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
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_RECEIVER_FOREGROUND
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.DeviceConfig
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.service.notification.StatusBarNotification
import android.text.Html
import android.util.ArraySet
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.util.Preconditions
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.Constants.KEY_LAST_NOTIFICATION_LISTENER_NOTIFICATION_SHOWN
import com.android.permissioncontroller.Constants.NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE
import com.android.permissioncontroller.Constants.NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID
import com.android.permissioncontroller.Constants.PERIODIC_NOTIFICATION_LISTENER_CHECK_JOB_ID
import com.android.permissioncontroller.Constants.PREFERENCES_FILE
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.getSystemServiceSafe
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_DEVICE_REBOOTED
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_REFRESH_REQUESTED
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.UNKNOWN
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

private val TAG = "NotificationListenerCheck"
private const val DEBUG = false

const val SC_NLS_SOURCE_ID = "AndroidNotificationListener"
@VisibleForTesting
const val SC_NLS_DISABLE_ACTION_ID = "disable_nls_component"

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

private fun isNotificationListenerCheckFlagEnabled(): Boolean {
    return DeviceConfig.getBoolean(
        DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED,
        false)
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

/** Notification Listener Check requires Android T or later*/
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
private fun checkNotificationListenerCheckSupported(): Boolean {
    return SdkLevel.isAtLeastT()
}

/** Returns {@code true} when Notification listener check is supported, feature flag enabled and
 *  Safety Center enabled */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
private fun checkNotificationListenerCheckEnabled(context: Context): Boolean {
    return checkNotificationListenerCheckSupported() &&
        isNotificationListenerCheckFlagEnabled() &&
        getSystemServiceSafe(context, SafetyCenterManager::class.java).isSafetyCenterEnabled
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun isProfile(context: Context): Boolean {
    val userManager = getSystemServiceSafe(context, UserManager::class.java)
    return userManager.isProfile
}

private fun getSafetySourceIssueIdFromComponentName(componentName: ComponentName): String {
    return "notification_listener_${componentName.flattenToString()}"
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
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@VisibleForTesting
internal class NotificationListenerCheckInternal(
    context: Context,
    private val shouldCancel: BooleanSupplier?
) {
    private val parentUserContext = Utils.getParentUserContext(context)
    private val random = Random()

    companion object {
        @VisibleForTesting
        const val SC_NLS_ISSUE_TYPE_ID = "notification_listener_privacy_issue"
        @VisibleForTesting
        const val SC_SHOW_NLS_SETTINGS_ACTION_ID =
            "show_notification_listener_settings"

        /** Lock required for all public methods */
        private val nlsLock = Mutex()

        private val sourceStateChangedSafetyEvent =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
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
        sendIssuesToSafetyCenter(enabledComponents)
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
            getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
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
    suspend fun loadNotifiedComponentsLocked(): ArraySet<NlsComponent> {
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
     * @param nlsComponents The list of packages we have already shown a notification for.
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
            markAsNotifiedLocked(componentName)
        }
    }

    private suspend fun markAsNotifiedLocked(componentName: ComponentName) {
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

    @Throws(InterruptedException::class)
    private suspend fun postSystemNotificationIfNeeded(components: List<ComponentName>) {
        val componentsInternal = components.toMutableList()

        // Don't show too many notification within certain timespan
        val sharedPrefs: SharedPreferences =
            parentUserContext.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
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
                        componentToNotifyFor.packageName
                    )
                }
                pkgInfo =
                    Utils.getPackageInfoForComponentName(parentUserContext, componentToNotifyFor)
            } catch (e: PackageManager.NameNotFoundException) {
                if (DEBUG) {
                    Log.w(TAG, "${componentToNotifyFor.packageName} not found")
                }
                componentsInternal.remove(componentToNotifyFor)
            }
        }

        createPermissionReminderChannel()
        createNotificationForNotificationListener(componentToNotifyFor, pkgInfo)
        markAsNotifiedLocked(componentToNotifyFor)
    }

    /** Create the channel the notification listener notifications should be posted to. */
    private fun createPermissionReminderChannel() {
        val permissionReminderChannel = NotificationChannel(
            Constants.PERMISSION_REMINDER_CHANNEL_ID,
            parentUserContext.getString(R.string.permission_reminders),
            NotificationManager.IMPORTANCE_LOW
        )

        val notificationManager = getSystemServiceSafe(
            parentUserContext,
            NotificationManager::class.java
        )

        notificationManager.createNotificationChannel(permissionReminderChannel)
    }

    /**
     * Create a notification reminding the user that a package has an enabled notification listener.
     * From this notification the user can directly go to Safety Center to assess issue.
     *
     * @param componentName the [NlsComponent] of the Notification Listener
     * @param pkg The [PackageInfo] for the [ComponentName] package
     */
    private fun createNotificationForNotificationListener(
        componentName: ComponentName,
        pkg: PackageInfo
    ) {
        val pkgLabel: CharSequence =
            Utils.getApplicationLabel(parentUserContext, pkg.applicationInfo)

        val deletePendingIntent =
            getNotificationDeleteBroadcastPendingIntent(parentUserContext, componentName)
        val clickPendingIntent =
            getSafetyCenterActivityPendingIntent(parentUserContext, componentName)

        val title =
            parentUserContext.getString(R.string.notification_listener_reminder_notification_title)
        val text =
            parentUserContext.getString(
                R.string.notification_listener_reminder_notification_content,
                pkgLabel
            )

        // Use PbA branding if available, otherwise default to more generic branding
        val pbaLabel = Html.fromHtml(
            parentUserContext.getString(android.R.string.safety_protection_display_text), 0)
            .toString()
        val appLabel: CharSequence?
        val smallIconResId: Int
        val colorResId: Int
        if (pbaLabel != null && pbaLabel.isNotEmpty()) {
            // PbA branding and colors
            appLabel = pbaLabel
            smallIconResId = android.R.drawable.ic_safety_protection
            colorResId = R.color.safety_center_info
        } else {
            // Generic branding. Settings label, gear icon, and system accent color
            appLabel = Utils.getSettingsLabelForNotifications(parentUserContext.packageManager)
            smallIconResId = R.drawable.ic_settings_24dp
            colorResId = android.R.color.system_notification_accent_color
        }

        val b: Notification.Builder =
            Notification.Builder(parentUserContext, Constants.PERMISSION_REMINDER_CHANNEL_ID)
                .setLocalOnly(true)
                .setContentTitle(title)
                .setContentText(text)
                // Ensure entire text can be displayed, instead of being truncated to one line
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setSmallIcon(smallIconResId)
                .setColor(parentUserContext.getColor(colorResId))
                .setAutoCancel(true)
                .setDeleteIntent(deletePendingIntent)
                .setContentIntent(clickPendingIntent)

        if (appLabel != null && appLabel.isNotEmpty()) {
            val appNameExtras = Bundle()
            appNameExtras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, appLabel.toString())
            b.addExtras(appNameExtras)
        }

        val notificationManager =
            getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
        notificationManager.notify(
            componentName.flattenToString(),
            NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID,
            b.build())

        Log.v(TAG, "Notification listener check notification shown with component=" +
            "${componentName.flattenToString()}")

        val sharedPrefs: SharedPreferences =
            parentUserContext.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        sharedPrefs.edit().putLong(
            KEY_LAST_NOTIFICATION_LISTENER_NOTIFICATION_SHOWN,
            currentTimeMillis()
        ).apply()
    }

    /**
     * @return [PendingIntent] to safety center
     */
    private fun getNotificationDeleteBroadcastPendingIntent(
        context: Context,
        componentName: ComponentName
    ): PendingIntent {
        val intent = Intent(parentUserContext,
            NotificationListenerCheckNotificationDeleteHandler::class.java).apply {
            putExtra(EXTRA_COMPONENT_NAME, componentName)
            flags = FLAG_RECEIVER_FOREGROUND
            identifier = componentName.flattenToString()
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            FLAG_ONE_SHOT or FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )
    }

    /**
     * @return [PendingIntent] to safety center
     */
    private fun getSafetyCenterActivityPendingIntent(
        context: Context,
        componentName: ComponentName
    ): PendingIntent {
        val intent = Intent(Intent.ACTION_SAFETY_CENTER).apply {
            putExtra(EXTRA_SAFETY_SOURCE_ID, SC_NLS_SOURCE_ID)
            putExtra(EXTRA_SAFETY_SOURCE_ISSUE_ID,
                getSafetySourceIssueIdFromComponentName(componentName))
            putExtra(EXTRA_COMPONENT_NAME, componentName)
            flags = FLAG_ACTIVITY_NEW_TASK
            identifier = componentName.flattenToString()
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            FLAG_ONE_SHOT or FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )
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
            getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
                .activeNotifications

        return notifications.firstOrNull { it.id == NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID }
    }

    /** Remove any posted notifications for this feature */
    internal fun removeAnyNotification() {
        cancelNotification()
    }

    /** Remove notification if present for a package */
    internal fun removeNotificationsForPackage(pkg: String) {
        val notification: StatusBarNotification = getCurrentlyShownNotificationLocked() ?: return
        val notificationComponent = ComponentName.unflattenFromString(notification.tag)
        if (notificationComponent == null || notificationComponent.packageName != pkg) {
            return
        }
        cancelNotification(notification.tag)
    }

    /** Remove notification if present for a [ComponentName] */
    internal fun removeNotificationsForComponent(component: ComponentName) {
        val notification: StatusBarNotification = getCurrentlyShownNotificationLocked() ?: return
        val notificationComponent = ComponentName.unflattenFromString(notification.tag)
        if (notificationComponent == null || notificationComponent != component) {
            return
        }
        cancelNotification(notification.tag)
    }

    private fun cancelNotification(notificationTag: String) {
        getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
            .cancel(notificationTag, NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID)
    }

    private fun cancelNotification() {
        getSystemServiceSafe(parentUserContext, NotificationManager::class.java)
            .cancel(NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID)
    }

    internal fun sendIssuesToSafetyCenter(
        safetyEvent: SafetyEvent = sourceStateChangedSafetyEvent
    ) {
        val enabledComponents = getEnabledNotificationListeners()
        sendIssuesToSafetyCenter(enabledComponents, safetyEvent)
    }

    private fun sendIssuesToSafetyCenter(
        enabledComponents: List<ComponentName>,
        safetyEvent: SafetyEvent = sourceStateChangedSafetyEvent
    ) {
        val pendingIssues = enabledComponents.map { createSafetySourceIssue(it) }
        val dataBuilder = SafetySourceData.Builder()
        pendingIssues.forEach { dataBuilder.addIssue(it) }
        val safetySourceData = dataBuilder.build()
        val safetyCenterManager =
            getSystemServiceSafe(parentUserContext, SafetyCenterManager::class.java)
        safetyCenterManager.setSafetySourceData(
            SC_NLS_SOURCE_ID,
            safetySourceData,
            safetyEvent
        )
    }

    /**
     * @param componentName enabled [NotificationListenerService]
     * @return safety source issue, shown as the warning card in safety center
     */
    @VisibleForTesting
    fun createSafetySourceIssue(componentName: ComponentName): SafetySourceIssue {
        val pkgInfo = Utils.getPackageInfoForComponentName(parentUserContext, componentName)
        val pkgLabel: CharSequence =
            Utils.getApplicationLabel(parentUserContext, pkgInfo.applicationInfo)
        val safetySourceIssueId = getSafetySourceIssueIdFromComponentName(componentName)

        val disableNlsPendingIntent =
            getDisableNlsPendingIntent(parentUserContext, safetySourceIssueId, componentName)

        val disableNlsAction = SafetySourceIssue.Action.Builder(
            SC_NLS_DISABLE_ACTION_ID,
            parentUserContext.getString(R.string.notification_listener_remove_access_button_label),
            disableNlsPendingIntent
        )
            .setWillResolve(true)
            .setSuccessMessage(parentUserContext.getString(
                R.string.notification_listener_remove_access_success_label))
            .build()

        val notificationListenerSettingsPendingIntent =
            getNotificationListenerSettingsPendingIntent(parentUserContext)

        val showNotificationListenerSettingsAction =
            SafetySourceIssue.Action.Builder(
                SC_SHOW_NLS_SETTINGS_ACTION_ID,
                parentUserContext.getString(R.string.notification_listener_review_app_button_label),
                notificationListenerSettingsPendingIntent
        ).build()

        val actionCardDismissPendingIntent =
            getActionCardDismissalPendingIntent(parentUserContext, componentName)

        val title = parentUserContext.getString(
            R.string.notification_listener_reminder_notification_title)
        val summary = parentUserContext.getString(
            R.string.notification_listener_warning_card_content)

        return SafetySourceIssue
            .Builder(
                safetySourceIssueId,
                title,
                summary,
                SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                SC_NLS_ISSUE_TYPE_ID
            )
            .setSubtitle(pkgLabel)
            .addAction(disableNlsAction)
            .addAction(showNotificationListenerSettingsAction)
            .setOnDismissPendingIntent(actionCardDismissPendingIntent)
            .build()
    }

    /**
     * @return [PendingIntent] for remove access button on the warning card.
     */
    private fun getDisableNlsPendingIntent(
        context: Context,
        safetySourceIssueId: String,
        componentName: ComponentName
    ): PendingIntent {
        val intent = Intent(context,
                DisableNotificationListenerComponentHandler::class.java).apply {
                putExtra(EXTRA_SAFETY_SOURCE_ISSUE_ID, safetySourceIssueId)
                putExtra(EXTRA_COMPONENT_NAME, componentName)
                flags = FLAG_RECEIVER_FOREGROUND
                identifier = componentName.flattenToString()
            }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            FLAG_IMMUTABLE
        )
    }

    /** @return [PendingIntent] to Notification Listener Settings page */
    private fun getNotificationListenerSettingsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            FLAG_IMMUTABLE
        )
    }

    private fun getActionCardDismissalPendingIntent(
        context: Context,
        componentName: ComponentName
    ): PendingIntent {
        val intent = Intent(context,
            NotificationListenerActionCardDismissalReceiver::class.java).apply {
                putExtra(EXTRA_COMPONENT_NAME, componentName)
                flags = FLAG_RECEIVER_FOREGROUND
                identifier = componentName.flattenToString()
            }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            FLAG_IMMUTABLE
        )
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
     * An immutable data class containing a [ComponentName] and timestamps for notification and
     * signal resolved.
     *
     * @param componentName The component name of the notification listener
     * @param notificationShownTime optional named parameter to set time of notification shown
     * @param signalResolvedTime optional named parameter to set time of signal resolved
     */
    @VisibleForTesting
    data class NlsComponent(
        val componentName: ComponentName,
        val notificationShownTime: Long = 0L,
        val signalResolvedTime: Long = 0L
    )
}

/**
 * Checks if a new notification should be shown.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerCheckJobService : JobService() {
    private var notificationListenerCheckInternal: NotificationListenerCheckInternal? = null
    private val jobLock = Object()

    /** We currently check if we should show a notification, the task executing the check  */
    @GuardedBy("jobLock")
    private var addNotificationListenerNotificationIfNeededJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        if (!checkNotificationListenerCheckEnabled(this)) {
            // NotificationListenerCheck not enabled. End job.
            return
        }

        notificationListenerCheckInternal =
            NotificationListenerCheckInternal(this, BooleanSupplier {
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
     * @return `false` if another check is already running, or if SDK Check fails (below T)
     */
    override fun onStartJob(params: JobParameters): Boolean {
        if (!checkNotificationListenerCheckEnabled(this)) {
            // NotificationListenerCheck not enabled. End job.
            return false
        }

        synchronized(jobLock) {
            if (addNotificationListenerNotificationIfNeededJob != null) {
                if (DEBUG) Log.d(TAG, "Job already running")
                return false
            }
            addNotificationListenerNotificationIfNeededJob = GlobalScope.launch(Default) {
                notificationListenerCheckInternal
                    ?.getEnabledNotificationListenersAndNotifyIfNeeded(
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
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class SetupPeriodicNotificationListenerCheck : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!checkNotificationListenerCheckSupported()) {
            // Notification Listener Check not supported. Exit.
            return
        }

        if (isProfile(context)) {
            // Profile parent handles child profiles too.
            return
        }

        val jobScheduler = getSystemServiceSafe(context, JobScheduler::class.java)
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
 * Handle the case where the notification is swiped away without further interaction.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerCheckNotificationDeleteHandler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!checkNotificationListenerCheckSupported()) {
            return
        }

        val componentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, EXTRA_COMPONENT_NAME)
        GlobalScope.launch(Default) {
            NotificationListenerCheckInternal(context, null).markAsNotified(componentName)
        }
        Log.v(
            TAG,
            "Notification listener check notification declined with component=" +
                "${componentName.flattenToString()}"
        )
    }
}

/** Disable a specified Notification Listener Service component */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class DisableNotificationListenerComponentHandler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.d(TAG, "DisableComponentHandler.onReceive $intent")
        val componentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, EXTRA_COMPONENT_NAME)

        GlobalScope.launch(Default) {
            if (DEBUG) {
                Log.v(TAG, "DisableComponentHandler: disabling $componentName")
            }

            val safetyEventBuilder = try {
                val notificationManager = getSystemServiceSafe(
                    context,
                    NotificationManager::class.java
                )
                notificationManager.setNotificationListenerAccessGranted(
                    componentName,
                    /* granted= */ false,
                    /* userSet= */ true)

                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
            } catch (e: Exception) {
                Log.w(TAG, "error occurred in disabling notification listener service.", e)
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
            }

            val safetySourceIssueId: String? =
                intent.getStringExtra(EXTRA_SAFETY_SOURCE_ISSUE_ID)
            val safetyEvent = safetyEventBuilder
                .setSafetySourceIssueId(safetySourceIssueId)
                .setSafetySourceIssueActionId(SC_NLS_DISABLE_ACTION_ID)
                .build()

            NotificationListenerCheckInternal(context, null).run {
                removeNotificationsForComponent(componentName)
                sendIssuesToSafetyCenter(safetyEvent)
            }
        }
    }
}

/* A Safety Center action card for a specified component was dismissed */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerActionCardDismissalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.d(TAG, "ActionCardDismissalReceiver.onReceive $intent")
        val componentName =
            Utils.getParcelableExtraSafe<ComponentName>(intent, EXTRA_COMPONENT_NAME)
        GlobalScope.launch(Default) {
            if (DEBUG) {
                Log.v(TAG, "ActionCardDismissalReceiver: $componentName dismissed")
            }
            NotificationListenerCheckInternal(context, null).run {
                removeNotificationsForComponent(componentName)
                markAsNotified(componentName)
                // TODO(b/217566029): update Safety center action cards
            }
        }
    }
}

/**
 * If a package gets removed or the data of the package gets cleared, forget that we showed a
 * notification for it.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerPackageResetHandler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_PACKAGE_DATA_CLEARED &&
            action != Intent.ACTION_PACKAGE_FULLY_REMOVED
        ) {
            return
        }

        if (!checkNotificationListenerCheckEnabled(context)) {
            return
        }

        if (isProfile(context)) {
            if (DEBUG) {
                Log.d(TAG, "NotificationListenerCheck only supports parent profile")
            }
            return
        }

        val data = Preconditions.checkNotNull(intent.data)
        val pkg = data.schemeSpecificPart

        if (DEBUG) Log.i(TAG, "Reset $pkg")

        GlobalScope.launch(Default) {
            NotificationListenerCheckInternal(context, null).run {
                removeNotificationsForPackage(pkg)
                removePackageState(pkg)
                sendIssuesToSafetyCenter()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationListenerPrivacySource : PrivacySource {
    override fun safetyCenterEnabledChanged(context: Context, enabled: Boolean) {
        NotificationListenerCheckInternal(context, null).run {
            removeAnyNotification()
        }
    }

    override fun rescanAndPushSafetyCenterData(
        context: Context,
        intent: Intent,
        refreshEvent: RefreshEvent
    ) {
        val safetyRefreshEvent = when (refreshEvent) {
            UNKNOWN ->
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
            EVENT_DEVICE_REBOOTED ->
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build()
            EVENT_REFRESH_REQUESTED -> {
                val refreshBroadcastId = intent.getStringExtra(
                    SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID)
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                    .setRefreshBroadcastId(refreshBroadcastId).build()
            }
        }

        NotificationListenerCheckInternal(context, null).run {
            sendIssuesToSafetyCenter(safetyRefreshEvent)
        }
    }
}