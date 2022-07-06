/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.privacysources

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.DeviceConfig
import android.provider.Settings
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.service.notification.StatusBarNotification
import android.text.Html
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.util.Preconditions
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__ACTION__CARD_DISMISSED
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__ACTION__CLICKED_CTA1
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__PRIVACY_SOURCE__A11Y_SERVICE
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__DISMISSED
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_SHOWN
import com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__PRIVACY_SOURCE__A11Y_SERVICE
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.getSystemServiceSafe
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

@VisibleForTesting
const val PROPERTY_SC_ACCESSIBILITY_SOURCE_ENABLED = "sc_accessibility_source_enabled"
const val PROPERTY_SC_ACCESSIBILITY_LISTENER_ENABLED = "sc_accessibility_listener_enabled"
const val SC_ACCESSIBILITY_SOURCE_ID = "AndroidAccessibility"
const val SC_ACCESSIBILITY_REMOVE_ACCESS_ACTION_ID =
    "revoke_accessibility_app_access"
private const val DEBUG = false

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
private fun isAccessibilitySourceSupported(): Boolean {
    return SdkLevel.isAtLeastT()
}

fun isAccessibilitySourceEnabled(): Boolean {
    return DeviceConfig.getBoolean(
        DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_SC_ACCESSIBILITY_SOURCE_ENABLED,
        false
    )
}

/**
 * cts test needs to disable the listener.
 */
fun isAccessibilityListenerEnabled(): Boolean {
    return DeviceConfig.getBoolean(
        DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_SC_ACCESSIBILITY_LISTENER_ENABLED,
        true
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun isSafetyCenterEnabled(context: Context): Boolean {
    return getSystemServiceSafe(context, SafetyCenterManager::class.java)
        .isSafetyCenterEnabled
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilitySourceService(
    val context: Context,
    val random: Random = Random()
) : PrivacySource {

    private val parentUserContext = Utils.getParentUserContext(context)
    private val packageManager = parentUserContext.packageManager
    private val sharedPrefs: SharedPreferences = parentUserContext.getSharedPreferences(
        Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val notificationsManager = getSystemServiceSafe(parentUserContext,
        NotificationManager::class.java)
    private val safetyCenterManager = getSystemServiceSafe(parentUserContext,
        SafetyCenterManager::class.java)
    private val a11yDataFile = parentUserContext.getFileStreamPath(
        Constants.ACCESSIBILITY_SERVICES_ALREADY_NOTIFIED_FILE)

    private val storageService = TextStorageRepository(a11yDataFile)

    @WorkerThread
    internal suspend fun processAccessibilityJob(
        params: JobParameters?,
        jobService: AccessibilityJobService,
        cancel: BooleanSupplier?
    ) {
        lock.withLock {
            try {
                if (DEBUG) {
                    Log.v(LOG_TAG, "safety center accessibility privacy job started.")
                }
                interruptJobIfCanceled(cancel)
                val a11yServiceList = getEnabledAccessibilityServices()
                if (a11yServiceList.isEmpty()) {
                    if (DEBUG) {
                        Log.v(LOG_TAG, "accessibility services not enabled, job completed.")
                    }
                    jobService.jobFinished(params, false)
                    jobService.clearJob()
                    return
                }

                val lastShownNotification =
                    sharedPrefs.getLong(KEY_LAST_ACCESSIBILITY_NOTIFICATION_SHOWN, 0)
                val showNotification = ((System.currentTimeMillis() - lastShownNotification) >
                    getNotificationsIntervalMillis()) || getCurrentNotification() == null

                if (showNotification) {
                    val alreadyNotifiedServices = loadNotifiedComponentsLocked()
                        .filter { component ->
                            (System.currentTimeMillis() - component.notificationShownTime) <
                                getPackageNotificationIntervalMillis()
                        }
                        .map { it.componentName }

                    val toBeNotifiedServices = a11yServiceList.filter {
                        !alreadyNotifiedServices.contains(ComponentName.unflattenFromString(it.id))
                    }

                    if (toBeNotifiedServices.isNotEmpty()) {
                        if (DEBUG) {
                            Log.v(LOG_TAG, "sending an accessibility service notification")
                        }
                        val serviceToBeNotified: AccessibilityServiceInfo =
                            toBeNotifiedServices[random.nextInt(toBeNotifiedServices.size)]
                        createPermissionReminderChannel()
                        interruptJobIfCanceled(cancel)
                        sendNotification(serviceToBeNotified)
                    }
                }

                interruptJobIfCanceled(cancel)
                sendIssuesToSafetyCenter(a11yServiceList)
                jobService.jobFinished(params, false)
            } catch (ex: InterruptedException) {
                Log.w(LOG_TAG, "cancel request for safety center accessibility job received.")
                jobService.jobFinished(params, true)
            } catch (ex: Exception) {
                Log.w(LOG_TAG, "could not process safety center accessibility job", ex)
                jobService.jobFinished(params, false)
            } finally {
                jobService.clearJob()
            }
        }
    }

    /**
     * sends a notification for a given accessibility package
     */
    private suspend fun sendNotification(serviceToBeNotified: AccessibilityServiceInfo) {
        val pkgLabel = serviceToBeNotified.resolveInfo.loadLabel(packageManager)
        val componentName = ComponentName.unflattenFromString(serviceToBeNotified.id)!!
        val uid = serviceToBeNotified.resolveInfo.serviceInfo.applicationInfo.uid
        var sessionId = Constants.INVALID_SESSION_ID
        while (sessionId == Constants.INVALID_SESSION_ID) {
            sessionId = random.nextLong()
        }

        val notificationDeleteIntent =
            Intent(parentUserContext, AccessibilityNotificationDeleteHandler::class.java).apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                putExtra(Intent.EXTRA_UID, uid)
                flags = Intent.FLAG_RECEIVER_FOREGROUND
                identifier = componentName.flattenToString()
            }

        val title = parentUserContext.getString(
            R.string.accessibility_access_reminder_notification_title
        )
        val summary = parentUserContext.getString(
            R.string.accessibility_access_reminder_notification_content,
            pkgLabel
        )

        val notificationResource = getNotificationResource()
        val b: Notification.Builder =
            Notification.Builder(parentUserContext, Constants.PERMISSION_REMINDER_CHANNEL_ID)
                .setLocalOnly(true)
                .setContentTitle(title)
                .setContentText(summary)
                // Ensure entire text can be displayed, instead of being truncated to one line
                .setStyle(Notification.BigTextStyle().bigText(summary))
                .setSmallIcon(notificationResource.smallIconResId)
                .setColor(context.getColor(notificationResource.colorResId))
                .setAutoCancel(true)
                .setDeleteIntent(
                    PendingIntent.getBroadcast(
                        parentUserContext, 0, notificationDeleteIntent,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setContentIntent(getSafetyCenterActivityIntent(context, uid, sessionId))

        val appNameExtras = Bundle()
        appNameExtras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME,
            notificationResource.appLabel)
        b.addExtras(appNameExtras)

        notificationsManager.notify(
            componentName.toShortString(),
            Constants.ACCESSIBILITY_CHECK_NOTIFICATION_ID,
            b.build()
        )

        markAsNotifiedLocked(componentName)

        sharedPrefs.edit().putLong(
            KEY_LAST_ACCESSIBILITY_NOTIFICATION_SHOWN,
            System.currentTimeMillis()
        ).apply()

        if (DEBUG) {
            Log.v(LOG_TAG, "NOTIF_INTERACTION SEND metric, uid $uid session $sessionId")
        }
        PermissionControllerStatsLog.write(
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION,
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__PRIVACY_SOURCE__A11Y_SERVICE,
            uid,
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_SHOWN,
            sessionId
        )
    }

    class NotificationResource(val appLabel: String, val smallIconResId: Int, val colorResId: Int)

    private fun getNotificationResource(): NotificationResource {
        // Use PbA branding if available, otherwise default to more generic branding
        val pbaLabel = Html.fromHtml(parentUserContext.getString(
                android.R.string.safety_protection_display_text), 0)
        val appLabel: String
        val smallIconResId: Int
        val colorResId: Int
        if (pbaLabel != null && pbaLabel.isNotEmpty()) {
            appLabel = pbaLabel.toString()
            smallIconResId = android.R.drawable.ic_safety_protection
            colorResId = R.color.safety_center_info
        } else {
            appLabel = parentUserContext.getString(R.string.safety_center_notification_app_label)
            smallIconResId = R.drawable.ic_settings_notification
            colorResId = android.R.color.system_notification_accent_color
        }
        return NotificationResource(appLabel, smallIconResId, colorResId)
    }

    /** Create the channel for a11y notifications */
    private fun createPermissionReminderChannel() {
        val permissionReminderChannel = NotificationChannel(
            Constants.PERMISSION_REMINDER_CHANNEL_ID,
            context.getString(R.string.permission_reminders),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationsManager.createNotificationChannel(permissionReminderChannel)
    }

    /**
     * @param a11yService enabled 3rd party accessibility service
     * @return safety source issue, shown as the warning card in safety center
     */
    private fun createSafetySourceIssue(a11yService: AccessibilityServiceInfo): SafetySourceIssue {
        val componentName = ComponentName.unflattenFromString(a11yService.id)!!
        val safetySourceIssueId = "accessibility_${componentName.flattenToString()}"
        val pkgLabel = a11yService.resolveInfo.loadLabel(packageManager).toString()
        val uid = a11yService.resolveInfo.serviceInfo.applicationInfo.uid

        var sessionId = Constants.INVALID_SESSION_ID
        while (sessionId == Constants.INVALID_SESSION_ID) {
            sessionId = random.nextLong()
        }
        val removeAccessPendingIntent = getRemoveAccessPendingIntent(
            context,
            componentName,
            safetySourceIssueId,
            uid,
            sessionId
        )

        val removeAccessAction = SafetySourceIssue.Action.Builder(
            SC_ACCESSIBILITY_REMOVE_ACCESS_ACTION_ID,
            parentUserContext.getString(R.string.accessibility_remove_access_button_label),
            removeAccessPendingIntent
        )
            .setWillResolve(true)
            .setSuccessMessage(parentUserContext.getString(
                R.string.accessibility_remove_access_success_label))
            .build()

        val accessibilityActivityPendingIntent =
            getAccessibilityActivityPendingIntent(context, uid, sessionId)

        val accessibilityActivityAction = SafetySourceIssue.Action.Builder(
            SC_ACCESSIBILITY_SHOW_ACCESSIBILITY_ACTIVITY_ACTION_ID,
            parentUserContext.getString(R.string.accessibility_show_all_apps_button_label),
            accessibilityActivityPendingIntent
        ).build()

        val warningCardDismissIntent =
            Intent(parentUserContext, AccessibilityWarningCardDismissalReceiver::class.java).apply {
                flags = Intent.FLAG_RECEIVER_FOREGROUND
                identifier = componentName.flattenToString()
                putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                putExtra(Intent.EXTRA_UID, uid)
            }

        val warningCardDismissPendingIntent = PendingIntent.getBroadcast(
            parentUserContext, 0, warningCardDismissIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
        val title = parentUserContext.getString(
            R.string.accessibility_access_reminder_notification_title)
        val summary = parentUserContext.getString(
            R.string.accessibility_access_warning_card_content)

        return SafetySourceIssue
            .Builder(
                "accessibility_${componentName.flattenToString()}",
                title,
                summary,
                SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                SC_ACCESSIBILITY_ISSUE_TYPE_ID
            )
            .addAction(removeAccessAction)
            .addAction(accessibilityActivityAction)
            .setSubtitle(pkgLabel)
            .setOnDismissPendingIntent(warningCardDismissPendingIntent)
            .build()
    }

    /**
     * @return pending intent for remove access button on the warning card.
     */
    private fun getRemoveAccessPendingIntent(
        context: Context,
        serviceComponentName: ComponentName,
        safetySourceIssueId: String,
        uid: Int,
        sessionId: Long
    ): PendingIntent {
        val intent =
            Intent(parentUserContext, AccessibilityRemoveAccessHandler::class.java).apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponentName)
                putExtra(SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID, safetySourceIssueId)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                putExtra(Intent.EXTRA_UID, uid)
                flags = Intent.FLAG_RECEIVER_FOREGROUND
                identifier = serviceComponentName.flattenToString()
            }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * @return pending intent for redirecting user to the accessibility page
     */
    private fun getAccessibilityActivityPendingIntent(
        context: Context,
        uid: Int,
        sessionId: Long
    ): PendingIntent {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra(Constants.EXTRA_SESSION_ID, sessionId)
        intent.putExtra(Intent.EXTRA_UID, uid)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * @return pending intent to redirect the user to safety center on notification click
     */
    private fun getSafetyCenterActivityIntent(
        context: Context,
        uid: Int,
        sessionId: Long
    ): PendingIntent {
        val intent = Intent(Intent.ACTION_SAFETY_CENTER)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra(Constants.EXTRA_SESSION_ID, sessionId)
        intent.putExtra(Intent.EXTRA_UID, uid)
        intent.putExtra(
            Constants.EXTRA_PRIVACY_SOURCE,
            PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__PRIVACY_SOURCE__A11Y_SERVICE
        )
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun sendIssuesToSafetyCenter(
        a11yServiceList: List<AccessibilityServiceInfo>,
        safetyEvent: SafetyEvent = sourceStateChanged
    ) {
        val pendingIssues = a11yServiceList.map { createSafetySourceIssue(it) }
        val dataBuilder = SafetySourceData.Builder()
        pendingIssues.forEach { dataBuilder.addIssue(it) }
        val safetySourceData = dataBuilder.build()
        if (DEBUG) {
            Log.v(LOG_TAG, "sending ${pendingIssues.size} issue to sc, data: $safetySourceData")
        }
        safetyCenterManager.setSafetySourceData(
            SC_ACCESSIBILITY_SOURCE_ID,
            safetySourceData,
            safetyEvent
        )
    }

    fun sendIssuesToSafetyCenter(safetyEvent: SafetyEvent = sourceStateChanged) {
        val enabledServices = getEnabledAccessibilityServices()
        sendIssuesToSafetyCenter(enabledServices, safetyEvent)
    }

    /**
     * If [.cancel] throw an [InterruptedException].
     */
    @Throws(InterruptedException::class)
    private fun interruptJobIfCanceled(cancel: BooleanSupplier?) {
        if (cancel != null && cancel.asBoolean) {
            throw InterruptedException()
        }
    }

    private val accessibilityManager = getSystemServiceSafe(parentUserContext,
        AccessibilityManager::class.java)

    /**
     * @return enabled 3rd party accessibility services.
     */
    fun getEnabledAccessibilityServices(): List<AccessibilityServiceInfo> {
        return accessibilityManager.getEnabledAccessibilityServiceList(
            FEEDBACK_ALL_MASK
        ).filter { !it.isAccessibilityTool }
            .filter { ComponentName.unflattenFromString(it.id) != null }
    }

    /**
     * Get currently shown accessibility notification.
     *
     * @return The notification or `null` if no notification is currently shown
     */
    private fun getCurrentNotification(): StatusBarNotification? {
        val notifications = notificationsManager.activeNotifications
        return notifications.firstOrNull { it.id == Constants.ACCESSIBILITY_CHECK_NOTIFICATION_ID }
    }

    internal suspend fun markAsNotified(componentName: ComponentName) {
        lock.withLock {
            markAsNotifiedLocked(componentName)
        }
    }

    private suspend fun markAsNotifiedLocked(componentName: ComponentName) {
        val notifiedComponentsMap: MutableMap<ComponentName, AccessibilityComponent> =
            loadNotifiedComponentsLocked()
                .associateBy({ it.componentName }, { it })
                .toMutableMap()

        // don't compare timestamps, so remove existing Component if present and
        // then add again
        val currentComponent: AccessibilityComponent? =
            notifiedComponentsMap.remove(componentName)
        val componentToMarkNotified: AccessibilityComponent =
            currentComponent?.copy(notificationShownTime = System.currentTimeMillis())
                ?: AccessibilityComponent(
                    componentName,
                    notificationShownTime = System.currentTimeMillis()
                )
        notifiedComponentsMap[componentName] = componentToMarkNotified
        persistNotifiedComponentsLocked(notifiedComponentsMap.values.toList())
    }

    /**
     * Load the list of [components][AccessibilityComponent] we have already shown a notification for.
     *
     * @return The list of components we have already shown a notification for.
     */
    @WorkerThread
    @VisibleForTesting
    internal suspend fun loadNotifiedComponentsLocked(): List<AccessibilityComponent> {
        return withContext(Dispatchers.IO) {
            storageService.readData(a11yDataCreator)
        }
    }

    /**
     * Persist the list of [components][AccessibilityComponent] we have already shown a notification for.
     *
     * @param accessibilityComponents The list of packages we have already shown a notification for.
     */
    @WorkerThread
    private suspend fun persistNotifiedComponentsLocked(
        accessibilityComponents: List<AccessibilityComponent>
    ) {
        withContext(Dispatchers.IO) {
            storageService.persistData(accessibilityComponents)
        }
    }

    /**
     * Remove notification when safety center feature is turned off
     */
    private fun removeAccessibilityNotification() {
        val notification: StatusBarNotification = getCurrentNotification() ?: return
        cancelNotification(notification.tag)
    }

    /**
     * Remove notification (if needed) when an accessibility event occur.
     */
    fun removeAccessibilityNotification(a11yEnabledComponents: Set<String>) {
        val notification = getCurrentNotification() ?: return
        if (a11yEnabledComponents.contains(notification.tag)) {
            return
        }
        cancelNotification(notification.tag)
    }

    /**
     * Remove notification when a package is uninstalled.
     */
    private fun removeAccessibilityNotification(pkg: String) {
        val notification = getCurrentNotification() ?: return
        val component = ComponentName.unflattenFromString(notification.tag)
        if (component == null || component.packageName != pkg) {
            return
        }
        cancelNotification(notification.tag)
    }

    /**
     * Remove notification for a component, when warning card is dismissed.
     */
    fun removeAccessibilityNotification(component: ComponentName) {
        val notification = getCurrentNotification() ?: return
        if (component.toShortString() == notification.tag) {
            cancelNotification(notification.tag)
        }
    }

    private fun cancelNotification(notificationTag: String) {
        getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
            .cancel(notificationTag, Constants.ACCESSIBILITY_CHECK_NOTIFICATION_ID)
    }

    internal suspend fun removePackageState(pkg: String) {
        lock.withLock {
            removeAccessibilityNotification(pkg)
            // There can be multiple components per package
            // Remove all known components for the specified package
            val notifiedComponents = loadNotifiedComponentsLocked()
            val components = notifiedComponents.filterNot { it.componentName.packageName == pkg }

            if (components.size < notifiedComponents.size) { // Persist the resulting set
                persistNotifiedComponentsLocked(components)
            }
        }
    }

    companion object {
        private val LOG_TAG = AccessibilitySourceService::class.java.simpleName
        private const val SC_ACCESSIBILITY_ISSUE_TYPE_ID = "accessibility_privacy_issue"
        private const val KEY_LAST_ACCESSIBILITY_NOTIFICATION_SHOWN =
            "last_accessibility_notification_shown"
        private const val SC_ACCESSIBILITY_SHOW_ACCESSIBILITY_ACTIVITY_ACTION_ID =
            "show_accessibility_apps"
        private const val PROPERTY_SC_ACCESSIBILITY_JOB_INTERVAL_MILLIS =
            "sc_accessibility_job_interval_millis"
        private val DEFAULT_SC_ACCESSIBILITY_JOB_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1)
        private const val PROPERTY_SC_ACCESSIBILITY_PKG_NOTIFICATIONS_INTERVAL_MILLIS =
            "sc_accessibility_pkg_notifications_interval_millis"
        private val DEFAULT_SC_ACCESSIBILITY_PKG_NOTIFICATIONS_INTERVAL_MILLIS =
            TimeUnit.DAYS.toMillis(90)

        private val sourceStateChanged = SafetyEvent.Builder(
            SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        /** lock for processing a job */
        private val lock = Mutex()

        private val a11yDataCreator = object : PrivacySourceData.Creator<AccessibilityComponent> {
            override fun fromStorageData(data: String): AccessibilityComponent {
                val components = data.split(" ")
                val a11yService = ComponentName.unflattenFromString(components[0])!!
                val notificationShownTime: Long = components[1].toLong()
                return AccessibilityComponent(a11yService, notificationShownTime)
            }
        }

        /**
         * Get time in between two periodic checks.
         *
         * Default: 1 day
         *
         * @return The time in between check in milliseconds
         */
        fun getJobsIntervalMillis(): Long {
            return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SC_ACCESSIBILITY_JOB_INTERVAL_MILLIS,
                DEFAULT_SC_ACCESSIBILITY_JOB_INTERVAL_MILLIS
            )
        }

        /**
         * Flexibility of the periodic check.
         *
         *
         * 10% of [.getPeriodicCheckIntervalMillis]
         *
         * @return The flexibility of the periodic check in milliseconds
         */
        fun getFlexJobsIntervalMillis(): Long {
            return getJobsIntervalMillis() / 10
        }

        /**
         * Minimum time in between showing two notifications.
         *
         *
         * This is just small enough so that the periodic check can always show a notification.
         *
         * @return The minimum time in milliseconds
         */
        private fun getNotificationsIntervalMillis(): Long {
            return getJobsIntervalMillis() - (getFlexJobsIntervalMillis() * 2.1).toLong()
        }

        /**
         * Get time in between two notifications for a single package with enabled a11y services.
         *
         * Default: 90 days
         *
         * @return The time in between notifications for single package in milliseconds
         */
        private fun getPackageNotificationIntervalMillis(): Long {
            return DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SC_ACCESSIBILITY_PKG_NOTIFICATIONS_INTERVAL_MILLIS,
                DEFAULT_SC_ACCESSIBILITY_PKG_NOTIFICATIONS_INTERVAL_MILLIS
            )
        }
    }

    override val shouldProcessProfileRequest: Boolean = false

    override fun safetyCenterEnabledChanged(context: Context, enabled: Boolean) {
        if (!enabled) { // safety center disabled event
            removeAccessibilityNotification()
        }
    }

    override fun rescanAndPushSafetyCenterData(
        context: Context,
        intent: Intent,
        refreshEvent: RefreshEvent
    ) {
        if (DEBUG) {
            Log.v(LOG_TAG, "rescan and push event from safety center $refreshEvent")
        }
        val safetyCenterEvent = getSafetyCenterEvent(refreshEvent, intent)
        sendIssuesToSafetyCenter(safetyCenterEvent)
    }

    @VisibleForTesting
    internal data class AccessibilityComponent(
        val componentName: ComponentName,
        val notificationShownTime: Long = 0L
    ) : PrivacySourceData {
        override fun toStorageData(): String {
            return componentName.flattenToString() + " " + notificationShownTime
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilityPackageResetHandler : BroadcastReceiver() {
    private val LOG_TAG = AccessibilityPackageResetHandler::class.java.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_PACKAGE_DATA_CLEARED &&
            action != Intent.ACTION_PACKAGE_FULLY_REMOVED
        ) {
            return
        }

        if (!isAccessibilitySourceSupported() || isProfile(context)) {
            return
        }

        val data = Preconditions.checkNotNull(intent.data)
        val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        coroutineScope.launch(Dispatchers.Default) {
            if (DEBUG) {
                Log.v(LOG_TAG, "package reset event occurred for ${data.schemeSpecificPart}")
            }
            AccessibilitySourceService(context).run {
                removePackageState(data.schemeSpecificPart)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilityNotificationDeleteHandler : BroadcastReceiver() {
    private val LOG_TAG = AccessibilityNotificationDeleteHandler::class.java.simpleName
    override fun onReceive(context: Context, intent: Intent) {
        val componentName: ComponentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, Intent.EXTRA_COMPONENT_NAME)
        val sessionId =
            intent.getLongExtra(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID)
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        coroutineScope.launch(Dispatchers.Default) {
            if (DEBUG) {
                Log.v(LOG_TAG, "deleting notification for ${componentName.flattenToShortString()}")
            }
            AccessibilitySourceService(context).markAsNotified(componentName)

            if (DEBUG) {
                Log.v(LOG_TAG, "NOTIF_INTERACTION DISMISSED metric, uid $uid session $sessionId")
            }
            PermissionControllerStatsLog.write(
                PRIVACY_SIGNAL_NOTIFICATION_INTERACTION,
                PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__PRIVACY_SOURCE__A11Y_SERVICE,
                uid,
                PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__DISMISSED,
                sessionId
            )
        }
    }
}

/**
 * Handler for Remove access action (warning cards) in safety center dashboard
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilityRemoveAccessHandler : BroadcastReceiver() {
    private val LOG_TAG = AccessibilityRemoveAccessHandler::class.java.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        val a11yService: ComponentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, Intent.EXTRA_COMPONENT_NAME)
        val sessionId =
            intent.getLongExtra(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID)
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        coroutineScope.launch(Dispatchers.Default) {
            if (DEBUG) {
                Log.v(LOG_TAG, "disabling a11y service ${a11yService.flattenToShortString()}")
            }
            val accessibilityService = AccessibilitySourceService(context)
            val builder = try {
                AccessibilitySettingsUtil.disableAccessibilityService(context, a11yService)
                SafetyEvent.Builder(
                    SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
            } catch (ex: Exception) {
                Log.w(LOG_TAG, "error occurred in disabling a11y service.", ex)
                SafetyEvent.Builder(
                    SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
            }
            val safetySourceIssueId = intent.getStringExtra(
                SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID)
            val safetyEvent = builder.setSafetySourceIssueId(safetySourceIssueId)
                .setSafetySourceIssueActionId(SC_ACCESSIBILITY_REMOVE_ACCESS_ACTION_ID)
                .build()

            accessibilityService.sendIssuesToSafetyCenter(safetyEvent)

            if (DEBUG) {
                Log.v(LOG_TAG, "ISSUE_CARD_INTERACTION CTA1 metric, uid $uid session $sessionId")
            }
            PermissionControllerStatsLog.write(
                PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION,
                PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__PRIVACY_SOURCE__A11Y_SERVICE,
                uid,
                PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__ACTION__CLICKED_CTA1,
                sessionId
            )
        }
    }
}

/**
 * Handler for accessibility warning cards dismissal in safety center dashboard
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilityWarningCardDismissalReceiver : BroadcastReceiver() {
    private val LOG_TAG = AccessibilityWarningCardDismissalReceiver::class.java.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        val componentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, Intent.EXTRA_COMPONENT_NAME)
        val sessionId =
            intent.getLongExtra(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID)
        val uid = intent.getIntExtra(Intent.EXTRA_UID, -1)
        val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        coroutineScope.launch(Dispatchers.Default) {
            if (DEBUG) {
                Log.v(LOG_TAG, "removing notification for ${componentName.flattenToShortString()}")
            }
            val accessibilityService = AccessibilitySourceService(context)
            accessibilityService.removeAccessibilityNotification(componentName)
            accessibilityService.markAsNotified(componentName)
        }

        if (DEBUG) {
            Log.v(LOG_TAG, "ISSUE_CARD_INTERACTION DISMISSED metric, uid $uid session $sessionId")
        }
        PermissionControllerStatsLog.write(
            PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION,
            PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__PRIVACY_SOURCE__A11Y_SERVICE,
            uid,
            PRIVACY_SIGNAL_ISSUE_CARD_INTERACTION__ACTION__CARD_DISMISSED,
            sessionId
        )
    }
}

/**
 * Schedules periodic job to send notifications for third part accessibility services,
 * the job also sends this data to Safety Center.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilityOnBootReceiver : BroadcastReceiver() {
    private val LOG_TAG = AccessibilityOnBootReceiver::class.java.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        if (!isAccessibilitySourceSupported() || isProfile(context)) {
            Log.v(LOG_TAG, "accessibility privacy job not supported, can't schedule the job")
            return
        }
        if (DEBUG) {
            Log.v(LOG_TAG, "scheduling safety center accessibility privacy source job")
        }

        val jobScheduler = getSystemServiceSafe(context, JobScheduler::class.java)

        if (jobScheduler.getPendingJob(Constants.PERIODIC_ACCESSIBILITY_CHECK_JOB_ID) == null) {
            val jobInfo = JobInfo.Builder(
                Constants.PERIODIC_ACCESSIBILITY_CHECK_JOB_ID,
                ComponentName(context, AccessibilityJobService::class.java)
            )
                .setPeriodic(
                    AccessibilitySourceService.getJobsIntervalMillis(),
                    AccessibilitySourceService.getFlexJobsIntervalMillis()
                )
                .build()

            val status = jobScheduler.schedule(jobInfo)
            if (status != JobScheduler.RESULT_SUCCESS) {
                Log.w(LOG_TAG, "Could not schedule AccessibilityJobService: $status")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilityJobService : JobService() {
    private val LOG_TAG = AccessibilityJobService::class.java.simpleName

    private var mSourceService: AccessibilitySourceService? = null
    private val mLock = Object()

    @GuardedBy("mLock")
    private var mCurrentJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) {
            Log.v(LOG_TAG, "accessibility privacy source job created.")
        }
        mSourceService = AccessibilitySourceService(this)
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        synchronized(mLock) {
            if (mCurrentJob != null) {
                Log.v(LOG_TAG, "Accessibility privacy source job already running")
                return false
            }
            if (!isAccessibilitySourceEnabled() ||
                !isSafetyCenterEnabled(this@AccessibilityJobService)) {
                Log.v(LOG_TAG, "either privacy source or safety center is not enabled")
                jobFinished(params, false)
                mCurrentJob = null
                return false
            }
            val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            mCurrentJob = coroutineScope.launch(Dispatchers.Default) {
                mSourceService?.processAccessibilityJob(
                    params,
                    this@AccessibilityJobService,
                    BooleanSupplier {
                        val job = mCurrentJob
                        return@BooleanSupplier job?.isCancelled ?: false
                    }
                ) ?: jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        var job: Job?
        synchronized(mLock) {
            job = if (mCurrentJob == null) {
                return false
            } else {
                mCurrentJob
            }
        }
        job?.cancel()
        return false
    }

    fun clearJob() {
        synchronized(mLock) {
            mCurrentJob = null
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class SafetyCenterAccessibilityListener(val context: Context) :
    AccessibilityManager.AccessibilityServicesStateChangeListener {

    private val LOG_TAG = SafetyCenterAccessibilityListener::class.java.simpleName

    override fun onAccessibilityServicesStateChanged(manager: AccessibilityManager) {
        if (!isAccessibilityListenerEnabled()) {
            Log.v(LOG_TAG, "accessibility event occurred, listener not enabled.")
            return
        }

        if (!isAccessibilitySourceEnabled() || !isSafetyCenterEnabled(context) ||
            isProfile(context)) {
            Log.v(LOG_TAG, "accessibility event occurred, safety center feature not enabled.")
            return
        }

        val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        coroutineScope.launch(Dispatchers.Default) {
            if (DEBUG) {
                Log.v(LOG_TAG, "processing accessibility event")
            }
            val a11ySourceService = AccessibilitySourceService(context)
            val a11yEnabledServices = a11ySourceService.getEnabledAccessibilityServices()
            a11ySourceService.sendIssuesToSafetyCenter(a11yEnabledServices)
            val enabledComponents = a11yEnabledServices.map { a11yService ->
                ComponentName.unflattenFromString(a11yService.id)!!.toShortString()
            }.toSet()
            a11ySourceService.removeAccessibilityNotification(enabledComponents)
        }
    }
}