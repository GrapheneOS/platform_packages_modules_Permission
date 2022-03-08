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

package com.android.permissioncontroller.permission.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.Intent.EXTRA_COMPONENT_NAME
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.provider.DeviceConfig
import android.service.notification.StatusBarNotification
import android.util.ArraySet
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.util.Preconditions
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.Constants.KEY_LAST_NOTIFICATION_LISTENER_NOTIFICATION_SHOWN
import com.android.permissioncontroller.Constants.NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE
import com.android.permissioncontroller.Constants.NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID
import com.android.permissioncontroller.Constants.PERIODIC_NOTIFICATION_LISTENER_CHECK_JOB_ID
import com.android.permissioncontroller.Constants.PREFERENCES_FILE
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
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
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.DAYS
import java.util.function.BooleanSupplier
import java.util.Random

private val TAG = NotificationListenerCheck::class.java.simpleName
private const val DEBUG = false

/**
 * Device config property for whether notification listener check is enabled on the device
 */
@VisibleForTesting
const val PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED = "notification_listener_check_enabled"

/**
 * Device config property for time period in milliseconds after which current enabled notification
 * listeners are queried
 */
private const val PROPERTY_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS =
    "notification_listener_check_interval_millis"

/**
 * Device config property for time period in milliseconds after which a followup notification can be
 * posted for an enabled notification listener
 */
private const val PROPERTY_NOTIFICATION_LISTENER_CHECK_PACKAGE_INTERVAL_MILLIS =
    "notification_listener_check_pkg_interval_millis"

private val DEFAULT_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS = DAYS.toMillis(1)

private val DEFAULT_NOTIFICATION_LISTENER_CHECK_PACKAGE_INTERVAL_MILLIS = DAYS.toMillis(90)

private fun checkNotificationListenerCheckEnabled(): Boolean {
    return DeviceConfig.getBoolean(
        DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED,
        false
    )
}

private fun checkNotificationListenerSupported(): Boolean {
    return SdkLevel.isAtLeastT()
}

/**
 * Get time in between two periodic checks.
 *
 * Default: 1 day
 *
 * @return The time in between check in milliseconds
 */
private fun getPeriodicCheckIntervalMillis(): Long {
    return DeviceConfig.getLong(
        DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS,
        DEFAULT_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS
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
private fun getFlexForPeriodicCheckMillis(): Long {
    return getPeriodicCheckIntervalMillis() / 10
}

/**
 * Minimum time in between showing two notifications.
 *
 *
 * This is just small enough so that the periodic check can always show a notification.
 *
 * @return The minimum time in milliseconds
 */
private fun getInBetweenNotificationsMillis(): Long {
    return getPeriodicCheckIntervalMillis() - (getFlexForPeriodicCheckMillis() * 2.1).toLong()
}

/**
 * Get time in between two notifications for a single package with enabled notification listener.
 *
 * Default: 90 days
 *
 * @return The time in between notifications for single package in milliseconds
 */
private fun getPackageNotificationIntervalMillis(): Long {
    return DeviceConfig.getLong(
        DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_NOTIFICATION_LISTENER_CHECK_PACKAGE_INTERVAL_MILLIS,
        DEFAULT_NOTIFICATION_LISTENER_CHECK_PACKAGE_INTERVAL_MILLIS
    )
}

/**
 * Show notification that double-guesses the user if they really wants to grant notification
 * listener permission to an app.
 *
 * <p>A notification is scheduled periodically, or on demand
 *
 * <p>We rate limit the number of notification we show and only ever show one notification at a
 * time.
 *
 * <p>As there are many cases why a notification should not been shown, we always schedule a
 * {@link #addNotificationListenerNotificationIfNeeded check} which then might add a notification.
 *
 * @param context Used to resolve managers
 * @param shouldCancel If supplied, can be used to interrupt long-running operations
 */
class NotificationListenerCheck(
    context: Context,
    private val shouldCancel: BooleanSupplier?
) {
    private val parentUserContext = Utils.getParentUserContext(context)
    private val packageManager: PackageManager = parentUserContext.packageManager
    private val random = Random()
    private val sharedPrefs: SharedPreferences =
        parentUserContext.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
    private val userManager: UserManager =
        Utils.getSystemServiceSafe(parentUserContext, UserManager::class.java)

    companion object {
        /** Lock required for all public methods */
        private val nlsLock = Mutex()
    }

    /**
     * Check for enabled notification listeners and notify user if needed.
     *
     * <p>Always run async inside a {@NotificationListenerCheckJobService} via coroutine.
     */
    @WorkerThread
    internal suspend fun getEnabledNotificationListenersAndNotifyIfNeeded(
        params: JobParameters,
        service: NotificationListenerCheckJobService
    ) {
        if (!checkNotificationListenerCheckEnabled()) {
            if (DEBUG) Log.d(TAG, "NotificationListenerCheck disabled, finishing job")
            service.jobFinished(params, false)
            return
        }

        if (!isRunningInParentProfile()) {
            // Profile parent handles child profiles too.
            if (DEBUG) Log.d(TAG, "NotificationListenerCheck only supported from parent profile")
            return
        }

        nlsLock.withLock {
            try {
                getEnabledNotificationListenersAndNotifyIfNeededLocked()
                service.jobFinished(params, false)
            } catch (e: Exception) {
                Log.e(TAG, "Could not check for notification listeners", e)
                service.jobFinished(params, true)
            } finally {
                service.clearJob()
            }
        }
    }

    @Throws(InterruptedException::class)
    private suspend fun getEnabledNotificationListenersAndNotifyIfNeededLocked() {
        val enabledComponents: List<ComponentName> = getEnabledNotificationListeners()

        // Load already notified components
        // Filter to only those that still have enabled listeners
        // Filter to those within notification interval (e.g. 90 days)
        val notifiedComponents = loadNotifiedComponentsLocked()
            .filterTo(ArraySet()) { componentHasBeenNotifiedWithinInterval(it) }
            .map { it.componentName }

        // Filter to unnotified components
        val unNotifiedComponents = enabledComponents.filter { it !in notifiedComponents }

        if (DEBUG) {
            Log.v(TAG, "Found ${enabledComponents.size} enabled notification listeners. " +
                "${notifiedComponents.size} already notified. ${unNotifiedComponents.size} " +
                "unnotified")
        }

        throwInterruptedExceptionIfTaskIsCanceled()

        postSystemNotificationIfNeeded(unNotifiedComponents)

        // TODO(b/217566029): send list of components with enabled notification listeners to
        //  Safety Center
    }

    /**
     * Get the [components][ComponentName] which have enabled notification listeners for the
     * parent/context user
     *
     * @throws InterruptedException If [.shouldCancel]
     */
    @Throws(InterruptedException::class)
    private fun getEnabledNotificationListeners(): List<ComponentName> {
        // Get all enabled NotificationListenerService components for primary user. NLS from managed
        // profiles are never bound.
        val enabledNotificationListeners =
            Utils.getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
                .enabledNotificationListeners

        if (DEBUG) {
            Log.d(TAG, "enabledNotificationListeners = " +
                "$enabledNotificationListeners")
        }

        throwInterruptedExceptionIfTaskIsCanceled()
        return enabledNotificationListeners
    }

    private fun componentHasBeenNotifiedWithinInterval(component: NlsComponent): Boolean {
        val interval = currentTimeMillis() - component.notificationShownTime
        if (DEBUG) {
            Log.v(TAG, "$interval ms since last notification of ${component.componentName}. " +
                "pkgInterval=${getPackageNotificationIntervalMillis()}")
        }
        return interval < getPackageNotificationIntervalMillis()
    }

    /**
     * Load the list of [components][NlsComponent] we have already shown a notification for.
     *
     * @return The list of components we have already shown a notification for.
     */
    @WorkerThread
    @VisibleForTesting
    internal suspend fun loadNotifiedComponentsLocked(): ArraySet<NlsComponent> {
        return withContext(Dispatchers.IO) {
            try {
                BufferedReader(
                    InputStreamReader(
                        parentUserContext.openFileInput(
                            NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE
                        )
                    )
                ).use { reader ->
                    val nlsComponents = ArraySet<NlsComponent>()

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
                            nlsComponents.add(
                                NlsComponent(
                                    componentName,
                                    notificationShownTime,
                                    signalResolvedTime
                                )
                            )
                        } else {
                            Log.i(
                                TAG,
                                "Not restoring state \"$line\" as component is unknown"
                            )
                        }
                    }
                    return@withContext nlsComponents
                }
            } catch (ignored: FileNotFoundException) {
                return@withContext ArraySet<NlsComponent>()
            } catch (e: Exception) {
                Log.w(TAG, "Could not read $NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE", e)
                return@withContext ArraySet<NlsComponent>()
            }
        }
    }

    /**
     * Persist the list of [components][NlsComponent] we have already shown a notification for.
     *
     * @param packages The list of packages we have already shown a notification for.
     */
    @WorkerThread
    private suspend fun persistNotifiedComponentsLocked(
        nlsComponents: Collection<NlsComponent>
    ) {
        withContext(Dispatchers.IO) {
            try {
                BufferedWriter(
                    OutputStreamWriter(
                        parentUserContext.openFileOutput(
                            NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE,
                            MODE_PRIVATE
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
                    for (nlsComponent in nlsComponents) {
                        writer.append(nlsComponent.componentName.flattenToString())
                            .append(' ')
                            .append(nlsComponent.notificationShownTime.toString())
                            .append(' ')
                            .append(nlsComponent.signalResolvedTime.toString())
                        writer.newLine()
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Could not write $NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE", e)
            }
        }
    }

    /**
     * Remove all persisted state for a package.
     *
     * @param pkg name of package
     */
    internal suspend fun removePackageState(pkg: String) {
        nlsLock.withLock {
            removeNotificationsForPackage(pkg)

            // There can be multiple NLS components per package
            // Remove all known NLS components for the specified package
            val notifiedComponents: ArraySet<NlsComponent> = loadNotifiedComponentsLocked()
                .filterNotTo(ArraySet()) { it.componentName.packageName == pkg }

            // Persist the resulting set
            persistNotifiedComponentsLocked(notifiedComponents)
        }
    }

    /**
     * Remember that we showed a notification for a [ComponentName]
     *
     * @param componentName The [ComponentName] we notified for
     */
    internal suspend fun markAsNotified(componentName: ComponentName) {
        nlsLock.withLock {
            val notifiedComponentsMap: MutableMap<ComponentName, NlsComponent> =
                loadNotifiedComponentsLocked()
                    .associateBy({ it.componentName }, { it })
                    .toMutableMap()

            // NlsComponent don't compare timestamps, so remove existing NlsComponent if present and
            // then add again
            val currentComponent: NlsComponent? = notifiedComponentsMap.remove(componentName)
            val componentToMarkNotified: NlsComponent =
                if (currentComponent != null) {
                    // Copy the current notified component and only update the notificationShownTime
                    currentComponent.copy(notificationShownTime = currentTimeMillis())
                } else {
                    // No previoulys notified component, create new one
                    NlsComponent(componentName, notificationShownTime = currentTimeMillis())
                }
            notifiedComponentsMap[componentName] = componentToMarkNotified
            persistNotifiedComponentsLocked(notifiedComponentsMap.values)
        }
    }

    @Throws(InterruptedException::class)
    private suspend fun postSystemNotificationIfNeeded(components: List<ComponentName>) {
        val componentsInternal = components.toMutableList()

        // Don't show too many notification within certain timespan
        if (currentTimeMillis() - sharedPrefs.getLong(
                KEY_LAST_NOTIFICATION_LISTENER_NOTIFICATION_SHOWN, 0
            )
            < getInBetweenNotificationsMillis()
        ) {
            Log.v(TAG, "Notification not posted, within " +
                "$DEFAULT_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS ms")
            return
        }

        // Check for existing notification first, exit if one already present
        if (getCurrentlyShownNotificationLocked() != null) {
            Log.v(TAG, "Notification not posted, previous notification has not been dismissed")
            return
        }

        // Get a random package and resolve package info
        var pkgInfo: PackageInfo? = null
        var componentToNotifyFor: ComponentName? = null
        while (pkgInfo == null || componentToNotifyFor == null) {
            throwInterruptedExceptionIfTaskIsCanceled()

            if (componentsInternal.isEmpty()) {
                Log.v(TAG, "Notification not posted, no unnotified enabled listeners")
                return
            }

            componentToNotifyFor = componentsInternal[random.nextInt(componentsInternal.size)]
            try {
                if (DEBUG) {
                    Log.v(TAG, "Attempting to get PackageInfo for " +
                        "${componentToNotifyFor.packageName}")
                }
                pkgInfo = getPackageInfoForComponentName(componentToNotifyFor)
            } catch (e: PackageManager.NameNotFoundException) {
                if (DEBUG) {
                    Log.w(TAG, "${componentToNotifyFor.packageName} not found")
                }
                componentsInternal.remove(componentToNotifyFor)
            }
        }

        createPermissionReminderChannel()
        createNotificationForNotificationListener(componentToNotifyFor, pkgInfo)
    }

    /**
     * Get [PackageInfo] for this ComponentName.
     *
     * @param component component to get package info for
     * @return The package info
     *
     * @throws PackageManager.NameNotFoundException if package does not exist
     */
    @Throws(PackageManager.NameNotFoundException::class)
    private fun getPackageInfoForComponentName(component: ComponentName): PackageInfo {
        return packageManager.getPackageInfo(component.packageName, 0)
    }

    /** Create the channel the notification listener notifications should be posted to. */
    private fun createPermissionReminderChannel() {
        val permissionReminderChannel = NotificationChannel(
            Constants.PERMISSION_REMINDER_CHANNEL_ID,
            parentUserContext.getString(R.string.permission_reminders),
            NotificationManager.IMPORTANCE_LOW
        )

        val notificationManager = Utils.getSystemServiceSafe(
            parentUserContext,
            NotificationManager::class.java
        )

        notificationManager.createNotificationChannel(permissionReminderChannel)
    }

    /**
     * Create a notification reminding the user that a package has an enabled notification listener.
     * From this notification the user can directly go to Safety Center to assess issue.
     *
     * @param component the [NlsComponent] of the Notification Listener
     * @param pkg The [PackageInfo] for the [ComponentName] package
     */
    private fun createNotificationForNotificationListener(
        componentName: ComponentName,
        pkg: PackageInfo
    ) {
        val pkgLabel: CharSequence = packageManager.getApplicationLabel(pkg.applicationInfo)
        val pkgName = pkg.packageName

        var sessionId = INVALID_SESSION_ID
        while (sessionId == INVALID_SESSION_ID) {
            sessionId = Random().nextLong()
        }

        val deleteIntent = Intent(parentUserContext, NotificationDeleteHandler::class.java).apply {
            putExtra(EXTRA_COMPONENT_NAME, componentName)
            putExtra(EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_RECEIVER_FOREGROUND
        }

        val clickIntent = Intent(parentUserContext, NotificationClickHandler::class.java).apply {
            putExtra(EXTRA_COMPONENT_NAME, componentName)
            putExtra(EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_RECEIVER_FOREGROUND
        }

        val title =
            parentUserContext.getString(R.string.notification_listener_reminder_notification_title)
        val text =
            parentUserContext.getString(
                R.string.notification_listener_reminder_notification_content,
                pkgLabel
            )

        val b: Notification.Builder =
            Notification.Builder(parentUserContext, Constants.PERMISSION_REMINDER_CHANNEL_ID)
                .setLocalOnly(true)
                .setContentTitle(title)
                .setContentText(text)
                // Ensure entire text can be displayed, instead of being truncated to one line
                .setStyle(Notification.BigTextStyle().bigText(text))
                // TODO(b/213357911): replace with Safety Center resource
                .setSmallIcon(R.drawable.ic_pin_drop)
                // TODO(b/213357911): replace with Safety Center resource
                .setColor(
                    parentUserContext.getColor(R.color.safety_center_info))
                .setAutoCancel(true)
                .setDeleteIntent(
                    PendingIntent.getBroadcast(
                        parentUserContext, 0, deleteIntent,
                        FLAG_ONE_SHOT or FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                    )
                )
                .setContentIntent(
                    PendingIntent.getBroadcast(
                        parentUserContext, 0, clickIntent,
                        FLAG_ONE_SHOT or FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                    )
                )

        // TODO(b/213357911): replace with Safety Center resource
        val appName = Utils.getSettingsLabelForNotifications(packageManager)
        if (appName != null) {
            val extras = Bundle()
            extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, appName.toString())
            b.addExtras(extras)
        }

        val notificationManager =
            Utils.getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
        notificationManager.notify(pkgName, NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID, b.build())

        Log.v(TAG, "Notification listener check notification shown with sessionId=" +
            "$sessionId component=${componentName.flattenToString()}"
        )

        sharedPrefs.edit().putLong(
            KEY_LAST_NOTIFICATION_LISTENER_NOTIFICATION_SHOWN,
            currentTimeMillis()
        ).apply()
    }

    /**
     * Get currently shown notification. We only ever show one notification per profile group. Also
     * only show notifications on the parent user/profile due to NotificationManager only binding
     * non-managed NLS.
     *
     * @return The notification or `null` if no notification is currently shown
     */
    private fun getCurrentlyShownNotificationLocked(): StatusBarNotification? {
        val notifications =
            Utils.getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
                .activeNotifications

        for (notification in notifications) {
            if (notification.id == NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID) {
                return notification
            }
        }
        return null
    }

    /**
     * Remove notification if present for a package
     *
     * @param pkg name of package
     */
    private suspend fun removeNotificationsForPackage(pkg: String) {
        val notification: StatusBarNotification? = getCurrentlyShownNotificationLocked()
        if (notification != null && notification.tag == pkg) {
            Utils.getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
                .cancel(pkg, NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID)
        }
    }

    /**
     * If [.shouldCancel] throw an [InterruptedException].
     */
    @Throws(InterruptedException::class)
    private fun throwInterruptedExceptionIfTaskIsCanceled() {
        if (shouldCancel != null && shouldCancel.asBoolean) {
            throw InterruptedException()
        }
    }

    /**
     * Check if the current user is the profile parent.
     *
     * @return `true` if the current user is the profile parent.
     */
    private fun isRunningInParentProfile(): Boolean {
        val user = UserHandle.of(UserHandle.myUserId())
        val parent: UserHandle? = userManager.getProfileParent(user)
        return parent == null || user == parent
    }

    /**
     * Checks if a new notification should be shown.
     */
    class NotificationListenerCheckJobService : JobService() {
        private var notificationListenerCheck: NotificationListenerCheck? = null
        private val jobLock = Object()

        /** We currently check if we should show a notification, the task executing the check  */
        @GuardedBy("jobLock")
        private var addNotificationListenerNotificationIfNeededJob: Job? = null

        override fun onCreate() {
            super.onCreate()
            notificationListenerCheck = NotificationListenerCheck(this, BooleanSupplier {
                synchronized(jobLock) {
                    val job = addNotificationListenerNotificationIfNeededJob
                    return@BooleanSupplier job?.isCancelled ?: false
                }
            })
        }

        /**
         * Starts an asynchronous check if a notification listener notification should be shown.
         *
         * @param params Not used other than for interacting with job scheduling
         *
         * @return `false` iff another check if already running
         */
        override fun onStartJob(params: JobParameters): Boolean {
            synchronized(jobLock) {
                if (addNotificationListenerNotificationIfNeededJob != null) {
                    if (DEBUG) Log.d(TAG, "Job already running")
                    return false
                }
                addNotificationListenerNotificationIfNeededJob = GlobalScope.launch(Default) {
                    notificationListenerCheck?.getEnabledNotificationListenersAndNotifyIfNeeded(
                        params,
                        this@NotificationListenerCheckJobService
                    ) ?: jobFinished(params, true)
                }
            }
            return true
        }

        /**
         * Abort the check if still running.
         *
         * @param params ignored
         *
         * @return false
         */
        override fun onStopJob(params: JobParameters): Boolean {
            var job: Job?
            synchronized(jobLock) {
                job = if (addNotificationListenerNotificationIfNeededJob == null) {
                    return false
                } else {
                    addNotificationListenerNotificationIfNeededJob
                }
            }
            job?.cancel()
            return false
        }

        fun clearJob() {
            synchronized(jobLock) {
                addNotificationListenerNotificationIfNeededJob = null
            }
        }
    }

    /**
     * On boot set up a periodic job that starts checks.
     */
    class SetupPeriodicNotificationListenerCheck : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!checkNotificationListenerSupported()) {
                return
            }

            val notificationListenerCheck = NotificationListenerCheck(context, null)
            val jobScheduler = Utils.getSystemServiceSafe(context, JobScheduler::class.java)

            if (!notificationListenerCheck.isRunningInParentProfile()) {
                // Profile parent handles child profiles too.
                return
            }

            if (jobScheduler.getPendingJob(PERIODIC_NOTIFICATION_LISTENER_CHECK_JOB_ID) == null) {
                val job =
                    JobInfo.Builder(
                        PERIODIC_NOTIFICATION_LISTENER_CHECK_JOB_ID,
                        ComponentName(context, NotificationListenerCheckJobService::class.java)
                    ).setPeriodic(
                        getPeriodicCheckIntervalMillis(),
                        getFlexForPeriodicCheckMillis()
                    ).build()
                val scheduleResult = jobScheduler.schedule(job)
                if (scheduleResult != JobScheduler.RESULT_SUCCESS) {
                    Log.e(
                        TAG,
                        "Could not schedule periodic notification listener check $scheduleResult"
                    )
                } else if (DEBUG) {
                    Log.i(TAG, "Scheduled periodic notification listener check")
                }
            }
        }
    }

    /**
     * Show the notification listener permission switch when the notification is clicked.
     */
    class NotificationClickHandler() : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val componentName =
                Utils.getParcelableExtraSafe<ComponentName>(intent, EXTRA_COMPONENT_NAME)
            val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, INVALID_SESSION_ID)
            GlobalScope.launch(Default) {
                NotificationListenerCheck(context, null).markAsNotified(componentName)
            }
            Log.v(
                TAG,
                "Notification listener check notification clicked with sessionId=$sessionId " +
                    "component=${componentName.flattenToString()}"
            )

            // TODO(b/216365468): send users to safety center
        }
    }

    /**
     * Handle the case where the notification is swiped away without further interaction.
     */
    class NotificationDeleteHandler() : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val componentName =
                Utils.getParcelableExtraSafe<ComponentName>(intent, EXTRA_COMPONENT_NAME)
            val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, INVALID_SESSION_ID)
            GlobalScope.launch(Default) {
                NotificationListenerCheck(context, null).markAsNotified(componentName)
            }
            Log.v(
                TAG,
                "Notification listener check notification declined with sessionId=$sessionId " +
                    "component=${componentName.flattenToString()}"
            )
        }
    }

    /**
     * If a package gets removed or the data of the package gets cleared, forget that we showed a
     * notification for it.
     */
    class PackageResetHandler : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != Intent.ACTION_PACKAGE_DATA_CLEARED &&
                action != Intent.ACTION_PACKAGE_FULLY_REMOVED
            ) {
                return
            }

            if (!checkNotificationListenerSupported()) {
                return
            }

            val data = Preconditions.checkNotNull(intent.data)

            if (DEBUG) Log.i(TAG, "Reset " + data.schemeSpecificPart)

            GlobalScope.launch(Default) {
                NotificationListenerCheck(context, null).run {
                    if (!this.isRunningInParentProfile()) {
                        if (DEBUG) {
                            Log.d(TAG, "NotificationListenerCheck only supports parent profile")
                        }
                        return@run
                    }

                    removePackageState(data.schemeSpecificPart)

                    // TODO(b/217566029): update Safety center action cards
                }
            }
        }
    }

    /**
     * An immutable data class containing a [ComponentName] and timestamps for notification and
     * signal resolved.
     *
     * @param componentName The component name of the notification listener
     * @param notificationShownTime optional named parameter to set time of notification shown
     * @param signalResolvedTime optional named parameter to set time of signal resolved
     */
    @VisibleForTesting
    internal data class NlsComponent(
        val componentName: ComponentName,
        val notificationShownTime: Long = 0L,
        val signalResolvedTime: Long = 0L
    )
}