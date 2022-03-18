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
import android.os.UserHandle
import android.os.UserHandle.myUserId
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AccessibilitySourceService(
    val context: Context,
    val cancel: BooleanSupplier?,
    val random: Random = Random()
) {
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
        jobService: AccessibilityJobService
    ) {
        if (!isAccessibilitySourceEnabled() || !safetyCenterManager.isSafetyCenterEnabled) {
            Log.v(LOG_TAG, "accessibility source (device config) not enabled.")
            jobService.jobFinished(params, false)
            jobService.clearJob()
            return
        }

        lock.withLock {
            try {
                interruptJobIfCanceled()
                val a11yServiceList = getEnabledAccessibilityServices()
                if (a11yServiceList.isEmpty()) {
                    jobService.jobFinished(params, false)
                    jobService.clearJob()
                    return
                }

                val lastShownNotification =
                    sharedPrefs.getLong(KEY_LAST_ACCESSIBILITY_NOTIFICATION_SHOWN, 0)
                val showNotification = ((System.currentTimeMillis() - lastShownNotification) <
                    getNotificationsIntervalMillis()) || getCurrentNotification() != null

                if (showNotification) {
                    val alreadyNotifiedServices = loadNotifiedComponentsLocked()
                        .filterTo(ArraySet()) { componentHasBeenNotifiedWithinInterval(it) }
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
                        sendNotification(pkgLabel, component)
                    }
                }

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

        if (DEBUG) {
            Log.v(LOG_TAG, "processAccessibilityJob method done")
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

        val pkgName = componentName.packageName
        val notificationDeleteIntent =
            Intent(parentUserContext, NotificationDeleteHandler::class.java).apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                flags = Intent.FLAG_RECEIVER_FOREGROUND
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
            pkgName,
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
     * @param a11yService enabled 3rds party accessibility service
     * @return safety source issue, shown as the warning card in safety center
     */
    private fun createSafetySourceIssue(a11yService: AccessibilityServiceInfo): SafetySourceIssue {
        val componentName = ComponentName.unflattenFromString(a11yService.id)!!
        val pkgLabel = a11yService.resolveInfo.loadLabel(packageManager).toString()
        val revokeAccessPendingIntent = getRemoveAccessPendingIntent(
            context,
            componentName
        )

        val revokeAction = SafetySourceIssue.Action.Builder(
            SC_ACCESSIBILITY_REMOVE_ACCESS_ACTION_ID,
            parentUserContext.getString(R.string.accessibility_remove_access_button_label),
            revokeAccessPendingIntent
        )
            .setWillResolve(true)
            .setSuccessMessage(parentUserContext.getString(
                R.string.accessibility_remove_access_success_label))
            .build()

        val accessibilityActivityPendingIntent = getAccessibilityActivityPendingIntent(context)

        val listA11yServicesAction = SafetySourceIssue.Action.Builder(
            SC_ACCESSIBILITY_SHOW_ACCESSIBILITY_ACTIVITY_ACTION_ID,
            parentUserContext.getString(R.string.accessibility_show_all_apps_button_label),
            accessibilityActivityPendingIntent
        ).build()

        val warningCardDismissIntent =
            Intent(parentUserContext, WarningCardDismissalReceiver::class.java).apply {
                flags = Intent.FLAG_RECEIVER_FOREGROUND
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
            .addAction(revokeAction)
            .addAction(listA11yServicesAction)
            .setSubtitle(pkgLabel)
            .setOnDismissPendingIntent(warningCardDismissPendingIntent)
            .build()
    }

    /**
     * @return pending intent for remove access button on the warning card.
     */
    private fun getRemoveAccessPendingIntent(
        context: Context,
        serviceComponentName: ComponentName
    ): PendingIntent {
        val intent =
            Intent(parentUserContext, RemoveAccessHandler::class.java).apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponentName)
                flags = Intent.FLAG_RECEIVER_FOREGROUND
            }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
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

    private fun sendIssuesToSafetyCenter(a11yServiceList: List<AccessibilityServiceInfo>) {
        val pendingIssues = a11yServiceList.map { createSafetySourceIssue(it) }
        val safetyEvent = SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED)
            .build()
        val dataBuilder = SafetySourceData.Builder()
        pendingIssues.forEach { dataBuilder.addIssue(it) }
        val safetySourceData = dataBuilder.build()
        if (DEBUG) {
            Log.v(LOG_TAG, "sending data to safety center : $safetySourceData")
        }
        safetyCenterManager.setSafetySourceData(
            SC_ACCESSIBILITY_SOURCE_ID,
            safetySourceData,
            safetyEvent
        )
    }

    private fun sendIssuesToSafetyCenter() {
        val pendingServices = getEnabledAccessibilityServices()
        sendIssuesToSafetyCenter(pendingServices)
    }

    private val userManager = getSystemServiceSafe(parentUserContext, UserManager::class.java)

    /**
     * Check if the current user is the profile parent.
     *
     * @return `true` if the current user is the profile parent.
     */
    fun isRunningInParentProfile(): Boolean {
        val user = UserHandle.of(myUserId())
        val parent: UserHandle? = userManager.getProfileParent(user)
        return parent == null || user == parent
    }

    /**
     * If [.cancel] throw an [InterruptedException].
     */
    @Throws(InterruptedException::class)
    private fun interruptJobIfCanceled() {
        if (cancel != null && cancel.asBoolean) {
            throw InterruptedException()
        }
    }

    private val accessibilityManager = getSystemServiceSafe(parentUserContext,
        AccessibilityManager::class.java)

    private fun getEnabledAccessibilityServices(): List<AccessibilityServiceInfo> {
        val a11yServiceList = accessibilityManager.getEnabledAccessibilityServiceList(
            FEEDBACK_ALL_MASK
        ).filter { !it.isAccessibilityTool }
            .filter { ComponentName.unflattenFromString(it.id) != null }

        if (DEBUG) {
            Log.v(LOG_TAG, "enabled accessibility services $a11yServiceList")
        }
        return a11yServiceList
    }

    private fun componentHasBeenNotifiedWithinInterval(component: AccessibilityComponent): Boolean {
        val interval = System.currentTimeMillis() - component.notificationShownTime
        if (DEBUG) {
            Log.v(
                LOG_TAG, "$interval ms since last notification of ${component.componentName}. " +
                    "pkgInterval=${getPackageNotificationIntervalMillis()}"
            )
        }
        return interval < getPackageNotificationIntervalMillis()
    }

    /**
     * Get currently shown notification. We only ever show one notification per profile group. Also
     * only show notifications on the parent user/profile due to NotificationManager only binding
     * non-managed NLS.
     *
     * @return The notification or `null` if no notification is currently shown
     */
    private fun getCurrentNotification(): StatusBarNotification? {
        val notifications = notificationsManager.activeNotifications

        for (notification in notifications) {
            if (notification.id == Constants.ACCESSIBILITY_CHECK_NOTIFICATION_ID) {
                return notification
            }
        }
        return null
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

        if (DEBUG) {
            Log.v(LOG_TAG, "markAsNotified done")
        }
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
                            Log.i(LOG_TAG, "Not restoring state \"$line\" as component is unknown")
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
     * Remove notification if present for a package
     *
     * @param pkg name of package
     */
    private fun removeNotificationsForPackage(pkg: String) {
        val notification: StatusBarNotification? = getCurrentNotification()
        if (notification != null && notification.tag == pkg) {
            getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
                .cancel(pkg, Constants.ACCESSIBILITY_CHECK_NOTIFICATION_ID)
        }
    }

    internal suspend fun removePackageState(pkg: String) {
        lock.withLock {
            removeNotificationsForPackage(pkg)
            // There can be multiple components per package
            // Remove all known components for the specified package
            val notifiedComponents: ArraySet<AccessibilityComponent> =
                loadNotifiedComponentsLocked()
                    .filterNotTo(ArraySet()) { it.componentName.packageName == pkg }

            // Persist the resulting set
            persistNotifiedComponentsLocked(notifiedComponents)
        }
        if (DEBUG) {
            Log.v(LOG_TAG, "removePackageState done")
        }
    }

    companion object {
        private val LOG_TAG = AccessibilitySourceService::class.java.simpleName
        private const val DEBUG = false // TODO (b/227383312): review logs later
        private const val SC_ACCESSIBILITY_ISSUE_TYPE_ID = "accessibility_privacy_issue"
        private const val KEY_LAST_ACCESSIBILITY_NOTIFICATION_SHOWN =
            "last_accessibility_notification_shown"
        private const val SC_ACCESSIBILITY_SOURCE_ID = "AccessibilitySource"
        private const val SC_ACCESSIBILITY_REMOVE_ACCESS_ACTION_ID =
            "revoke_accessibility_app_access"
        private const val SC_ACCESSIBILITY_SHOW_ACCESSIBILITY_ACTIVITY_ACTION_ID =
            "show_accessibility_apps"

        @VisibleForTesting
        const val PROPERTY_SC_ACCESSIBILITY_SOURCE_ENABLED = "sc_accessibility_source_enabled"
        private const val PROPERTY_SC_ACCESSIBILITY_JOB_INTERVAL_MILLIS =
            "sc_accessibility_job_interval_millis"
        private val DEFAULT_SC_ACCESSIBILITY_JOB_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1)
        private const val PROPERTY_SC_ACCESSIBILITY_PKG_NOTIFICATIONS_INTERVAL_MILLIS =
            "sc_accessibility_pkg_notifications_interval_millis"
        private val DEFAULT_SC_ACCESSIBILITY_PKG_NOTIFICATIONS_INTERVAL_MILLIS =
            TimeUnit.DAYS.toMillis(90)

        /** lock for processing a job */
        private val lock = Mutex()

        fun isAccessibilitySourceEnabled(): Boolean {
            return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SC_ACCESSIBILITY_SOURCE_ENABLED,
                false
            )
        }

        private fun isAccessibilitySourceSupported(): Boolean {
            return SdkLevel.isAtLeastT()
        }

        /**
         * Get time in between two periodic checks.
         *
         * Default: 1 day
         *
         * @return The time in between check in milliseconds
         */
        private fun getJobsIntervalMillis(): Long {
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
        private fun getFlexJobsIntervalMillis(): Long {
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

    /**
     * Job service contains logic for pulling a11y data (I guess)
     */
    class AccessibilityJobService : JobService() {
        private var mSourceService: AccessibilitySourceService? = null
        private val mLock = Object()

        @GuardedBy("mLock")
        private var mCurrentJob: Job? = null

        override fun onCreate() {
            super.onCreate()
            mSourceService = AccessibilitySourceService(this, BooleanSupplier {
                synchronized(mLock) {
                    val job = mCurrentJob
                    return@BooleanSupplier job?.isCancelled ?: false
                }
            })
        }

        override fun onStartJob(params: JobParameters?): Boolean {
            synchronized(mLock) {
                if (mCurrentJob != null) {
                    if (DEBUG) Log.v(LOG_TAG, "Accessibility (safety center) Job already running")
                    return false
                }
                mCurrentJob = GlobalScope.launch(Dispatchers.Default) {
                    mSourceService?.processAccessibilityJob(
                        params,
                        this@AccessibilityJobService
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

    /**
     * Remove access or turn off an accessibility service.
     */
    class RemoveAccessHandler : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val a11yService: ComponentName =
                Utils.getParcelableExtraSafe<ComponentName>(intent, Intent.EXTRA_COMPONENT_NAME)
            GlobalScope.launch(Dispatchers.Default) {
                if (DEBUG) {
                    Log.v(LOG_TAG, "RemoveAccessHandler: disabling a11y service $a11yService")
                }
                AccessibilitySettingsUtil.disableAccessibilityService(
                    context,
                    a11yService
                )
                val accessibilityService = AccessibilitySourceService(context, null)
                accessibilityService.removeNotificationsForPackage(a11yService.packageName)
            }
        }
    }

    class WarningCardDismissalReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val componentName =
                Utils.getParcelableExtraSafe<ComponentName>(intent, Intent.EXTRA_COMPONENT_NAME)
            GlobalScope.launch(Dispatchers.Default) {
                val accessibilityService = AccessibilitySourceService(context, null)
                accessibilityService.removeNotificationsForPackage(componentName.packageName)
                accessibilityService.markAsNotified(componentName)
            }
        }
    }

    class NotificationDeleteHandler : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val componentName =
                Utils.getParcelableExtraSafe<ComponentName>(intent, Intent.EXTRA_COMPONENT_NAME)
            GlobalScope.launch(Dispatchers.Default) {
                AccessibilitySourceService(context, null).markAsNotified(componentName)
            }
        }
    }

    /**
     * Schedules periodic job to send notifications for third part accessibility services,
     * the job also sends this data to Safety Center.
     */
    class AccessibilityOnBootReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_BOOT_COMPLETED != intent.action) {
                return
            }
            val a11ySourceService = AccessibilitySourceService(context, null)
            if (!isAccessibilitySourceSupported() || !isAccessibilitySourceEnabled() ||
                !a11ySourceService.safetyCenterManager.isSafetyCenterEnabled ||
                !a11ySourceService.isRunningInParentProfile()
            ) {
                Log.v(LOG_TAG, "safety center/a11y feature is not enabled.")
                return
            }

            val jobScheduler = getSystemServiceSafe(context, JobScheduler::class.java)

            if (jobScheduler.getPendingJob(Constants.PERIODIC_ACCESSIBILITY_CHECK_JOB_ID) == null) {
                val jobInfo = JobInfo.Builder(
                    Constants.PERIODIC_ACCESSIBILITY_CHECK_JOB_ID,
                    ComponentName(context, AccessibilityJobService::class.java)
                )
                    .setPeriodic(getJobsIntervalMillis(), getFlexJobsIntervalMillis())
                    .build()

                val status = jobScheduler.schedule(jobInfo)
                if (status != JobScheduler.RESULT_SUCCESS) {
                    Log.w(LOG_TAG, "Could not schedule AccessibilityJobService: $status")
                }
            }
        }
    }

    class PackageResetHandler : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != Intent.ACTION_PACKAGE_DATA_CLEARED &&
                action != Intent.ACTION_PACKAGE_FULLY_REMOVED
            ) {
                return
            }

            if (!isAccessibilitySourceSupported() || !isAccessibilitySourceEnabled()) {
                Log.v(LOG_TAG, "safety center a11y package reset handler not enabled.")
                return
            }

            val data = Preconditions.checkNotNull(intent.data)
            GlobalScope.launch(Dispatchers.Default) {
                AccessibilitySourceService(context, null).run {
                    if (!this.isRunningInParentProfile()) {
                        if (DEBUG) {
                            Log.v(
                                LOG_TAG,
                                "AccessibilitySourceService only supports parent profile"
                            )
                        }
                        return@run
                    }
                    removePackageState(data.schemeSpecificPart)
                    sendIssuesToSafetyCenter()
                }
            }
        }
    }

    class SafetyCenterAccessibilityListener(val context: Context) :
        AccessibilityManager.AccessibilityServicesStateChangeListener {

        override fun onAccessibilityServicesStateChanged(manager: AccessibilityManager) {
            GlobalScope.launch(Dispatchers.Default) {
                val a11ySourceService = AccessibilitySourceService(context, null)
                a11ySourceService.sendIssuesToSafetyCenter()
                // TODO (b/227383312): remove notifications if needed, also add feature flag check
            }
            if (DEBUG)
            Log.v(LOG_TAG, "accessibility changes happened.")
        }
    }

    @VisibleForTesting
    internal data class AccessibilityComponent(
        val componentName: ComponentName,
        val notificationShownTime: Long = 0L,
        val signalResolvedTime: Long = 0L
    )
}
