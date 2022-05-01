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
import android.os.UserManager
import android.provider.DeviceConfig
import android.provider.Settings
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.service.notification.StatusBarNotification
import android.util.ArraySet
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.util.Preconditions
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.getSystemServiceSafe
import com.android.permissioncontroller.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

@VisibleForTesting
const val PROPERTY_SC_ACCESSIBILITY_SOURCE_ENABLED = "sc_accessibility_source_enabled"
const val SC_ACCESSIBILITY_SOURCE_ID = "AndroidAccessibility"
const val SC_ACCESSIBILITY_REMOVE_ACCESS_ACTION_ID =
    "revoke_accessibility_app_access"

private fun isAccessibilitySourceSupported(): Boolean {
    return SdkLevel.isAtLeastT()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun isProfile(context: Context): Boolean {
    val userManager = getSystemServiceSafe(context, UserManager::class.java)
    return userManager.isProfile
}

fun isAccessibilitySourceEnabled(): Boolean {
    return DeviceConfig.getBoolean(
        DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_SC_ACCESSIBILITY_SOURCE_ENABLED,
        false
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

    @WorkerThread
    internal suspend fun processAccessibilityJob(
        params: JobParameters?,
        jobService: AccessibilityJobService,
        cancel: BooleanSupplier?
    ) {
        lock.withLock {
            try {
                interruptJobIfCanceled(cancel)
                val a11yServiceList = getEnabledAccessibilityServices()
                if (a11yServiceList.isEmpty()) {
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
                        val serviceToBeNotified =
                            toBeNotifiedServices[random.nextInt(toBeNotifiedServices.size)]
                        val pkgLabel = serviceToBeNotified.resolveInfo.loadLabel(packageManager)
                        val component = ComponentName.unflattenFromString(serviceToBeNotified.id)!!
                        createPermissionReminderChannel()
                        interruptJobIfCanceled(cancel)
                        sendNotification(pkgLabel, component)
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
    private suspend fun sendNotification(
        pkgLabel: CharSequence,
        componentName: ComponentName
    ) {
        var sessionId = Constants.INVALID_SESSION_ID
        while (sessionId == Constants.INVALID_SESSION_ID) {
            sessionId = random.nextLong()
        }

        val notificationDeleteIntent =
            Intent(parentUserContext, AccessibilityNotificationDeleteHandler::class.java).apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
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

        val b: Notification.Builder =
            Notification.Builder(parentUserContext, Constants.PERMISSION_REMINDER_CHANNEL_ID)
                .setLocalOnly(true)
                .setContentTitle(title)
                .setContentText(summary)
                // Ensure entire text can be displayed, instead of being truncated to one line
                .setStyle(Notification.BigTextStyle().bigText(summary))
                .setSmallIcon(android.R.drawable.ic_safety_protection)
                .setColor(parentUserContext.getColor(R.color.safety_center_info))
                .setAutoCancel(true)
                .setDeleteIntent(
                    PendingIntent.getBroadcast(
                        parentUserContext, 0, notificationDeleteIntent,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setContentIntent(getSafetyCenterActivityIntent(context))

        val appName = parentUserContext.getString(android.R.string.safety_protection_display_text)
        val extras = Bundle()
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, appName)
        b.addExtras(extras)

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
        val removeAccessPendingIntent = getRemoveAccessPendingIntent(
            context,
            componentName,
            safetySourceIssueId
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

        val accessibilityActivityPendingIntent = getAccessibilityActivityPendingIntent(context)

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
        safetySourceIssueId: String
    ): PendingIntent {
        val intent =
            Intent(parentUserContext, AccessibilityRemoveAccessHandler::class.java).apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponentName)
                putExtra(SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID, safetySourceIssueId)
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
    private fun getAccessibilityActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * @return pending intent to redirect the user to safety center on notification click
     */
    private fun getSafetyCenterActivityIntent(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_SAFETY_CENTER)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
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
        persistNotifiedComponentsLocked(notifiedComponentsMap.values)
    }

    /**
     * Load the list of [components][AccessibilityComponent] we have already shown a notification for.
     *
     * @return The list of components we have already shown a notification for.
     */
    @WorkerThread
    @VisibleForTesting
    internal suspend fun loadNotifiedComponentsLocked(): ArraySet<AccessibilityComponent> {
        return withContext(Dispatchers.IO) {
            try {
                BufferedReader(
                    InputStreamReader(
                        parentUserContext.openFileInput(
                            Constants.ACCESSIBILITY_SERVICES_ALREADY_NOTIFIED_FILE
                        )
                    )
                ).use { reader ->
                    val accessibilityComponents = ArraySet<AccessibilityComponent>()

                    /*
                     * The format of the file is
                     * <flattened component> <time of notification> <time resolved>
                     * e.g.
                     *
                     * com.one.package/Class 1234567890 1234567890
                     * com.two.package/Class 1234567890 1234567890
                     * com.three.package/Class 1234567890 1234567890
                     */
                    while (true) {
                        val line = reader.readLine() ?: break
                        val lineComponents = line.split(" ".toRegex()).toTypedArray()
                        val componentName = ComponentName.unflattenFromString(lineComponents[0])
                        val notificationShownTime: Long = lineComponents[1].toLong()
                        val signalResolvedTime: Long = lineComponents[2].toLong()
                        if (componentName != null) {
                            accessibilityComponents.add(
                                AccessibilityComponent(
                                    componentName,
                                    notificationShownTime,
                                    signalResolvedTime
                                )
                            )
                        } else {
                            Log.w(LOG_TAG, "Not restoring state \"$line\" as component is unknown")
                        }
                    }
                    return@withContext accessibilityComponents
                }
            } catch (ignored: FileNotFoundException) {
                return@withContext ArraySet<AccessibilityComponent>()
            } catch (e: Exception) {
                Log.w(
                    LOG_TAG,
                    "Could not read ${Constants.ACCESSIBILITY_SERVICES_ALREADY_NOTIFIED_FILE}",
                    e
                )
                return@withContext ArraySet<AccessibilityComponent>()
            }
        }
    }

    /**
     * Persist the list of [components][AccessibilityComponent] we have already shown a notification for.
     *
     * @param accessibilityComponents The list of packages we have already shown a notification for.
     */
    @WorkerThread
    private suspend fun persistNotifiedComponentsLocked(
        accessibilityComponents: Collection<AccessibilityComponent>
    ) {
        withContext(Dispatchers.IO) {
            try {
                BufferedWriter(
                    OutputStreamWriter(
                        parentUserContext.openFileOutput(
                            Constants.ACCESSIBILITY_SERVICES_ALREADY_NOTIFIED_FILE,
                            Context.MODE_PRIVATE
                        )
                    )
                ).use { writer ->
                    /*
                     * The format of the file is
                     * <flattened component> <time of notification> <time resolved>
                     * e.g.
                     *
                     * com.one.package/Class 1234567890 1234567890
                     * com.two.package/Class 1234567890 1234567890
                     * com.three.package/Class 1234567890 1234567890
                     */
                    for (component in accessibilityComponents) {
                        writer.append(component.componentName.flattenToString())
                            .append(' ')
                            .append(component.notificationShownTime.toString())
                            .append(' ')
                            .append(component.signalResolvedTime.toString())
                        writer.newLine()
                    }
                }
            } catch (e: IOException) {
                Log.e(
                    LOG_TAG,
                    "Could not write to ${Constants.ACCESSIBILITY_SERVICES_ALREADY_NOTIFIED_FILE}",
                    e
                )
            }
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

    override fun safetyCenterEnabledChanged(context: Context, enabled: Boolean) {
        if (!enabled) { // safety center disabled event
            removeAccessibilityNotification()
        }
    }

    override fun rescanAndPushSafetyCenterData(
        context: Context,
        intent: Intent,
        refreshEvent: SafetyCenterReceiver.RefreshEvent
    ) {
        val refreshBroadcastId = intent.getStringExtra(
            SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID)
        val safetyRefreshEvent = SafetyEvent.Builder(
            SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
            .setRefreshBroadcastId(refreshBroadcastId)
            .build()
        sendIssuesToSafetyCenter(safetyRefreshEvent)
    }

    @VisibleForTesting
    internal data class AccessibilityComponent(
        val componentName: ComponentName,
        val notificationShownTime: Long = 0L,
        val signalResolvedTime: Long = 0L
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilityPackageResetHandler : BroadcastReceiver() {

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
        GlobalScope.launch(Dispatchers.Default) {
            AccessibilitySourceService(context).run {
                removePackageState(data.schemeSpecificPart)
                sendIssuesToSafetyCenter()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilityNotificationDeleteHandler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val componentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, Intent.EXTRA_COMPONENT_NAME)
        GlobalScope.launch(Dispatchers.Default) {
            AccessibilitySourceService(context).markAsNotified(componentName)
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
        GlobalScope.launch(Dispatchers.Default) {
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
        }
    }
}

/**
 * Handler for accessibility warning cards dismissal in safety center dashboard
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilityWarningCardDismissalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val componentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, Intent.EXTRA_COMPONENT_NAME)
        GlobalScope.launch(Dispatchers.Default) {
            val accessibilityService = AccessibilitySourceService(context)
            accessibilityService.removeAccessibilityNotification(componentName)
            accessibilityService.markAsNotified(componentName)
        }
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
        if (Intent.ACTION_BOOT_COMPLETED != intent.action ||
            !isAccessibilitySourceSupported() || isProfile(context)) {
            Log.v(LOG_TAG, "accessibility privacy job not supported, can't schedule the job")
            return
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
        mSourceService = AccessibilitySourceService(this)
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        synchronized(mLock) {
            if (mCurrentJob != null) {
                Log.w(LOG_TAG, "Accessibility privacy source job already running")
                return false
            }
            if (!isAccessibilitySourceEnabled() ||
                !isSafetyCenterEnabled(this@AccessibilityJobService)) {
                jobFinished(params, false)
                mCurrentJob = null
                return false
            }
            mCurrentJob = GlobalScope.launch(Dispatchers.Default) {
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
        if (!isAccessibilitySourceEnabled() || !isSafetyCenterEnabled(context) ||
            isProfile(context)) {
            Log.v(LOG_TAG, "accessibility event occurred, safety center feature not enabled.")
            return
        }

        GlobalScope.launch(Dispatchers.Default) {
            val a11ySourceService = AccessibilitySourceService(context)
            val a11yEnabledServices = a11ySourceService.getEnabledAccessibilityServices()
            a11ySourceService.sendIssuesToSafetyCenter(a11yEnabledServices)
            val enabledComponents = a11yEnabledServices.map { a11yService ->
                ComponentName.unflattenFromString(a11yService.id)!!.toShortString()
            }.toSet()
            a11ySourceService.removeAccessibilityNotification(enabledComponents)
        }
        Log.v(LOG_TAG, "accessibility changes processed.")
    }
}