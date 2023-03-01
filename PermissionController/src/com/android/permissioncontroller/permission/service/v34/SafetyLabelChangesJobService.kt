/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.service.v34

import android.Manifest
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
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.DeviceConfig
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.android.permission.safetylabel.DataCategoryConstants.CATEGORY_LOCATION
import com.android.permission.safetylabel.SafetyLabel as AppMetadataSafetyLabel
import com.android.permissioncontroller.Constants.PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID
import com.android.permissioncontroller.Constants.PERMISSION_REMINDER_CHANNEL_ID
import com.android.permissioncontroller.Constants.SAFETY_LABEL_CHANGES_JOB_ID
import com.android.permissioncontroller.Constants.SAFETY_LABEL_CHANGES_NOTIFICATION_ID
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.LightInstallSourceInfoLiveData
import com.android.permissioncontroller.permission.data.LightPackageInfoLiveData
import com.android.permissioncontroller.permission.data.SinglePermGroupPackagesUiInfoLiveData
import com.android.permissioncontroller.permission.data.v34.AppDataSharingUpdatesLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState.PERMS_ALLOWED_ALWAYS
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.getSystemServiceSafe
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppInfo
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel as SafetyLabelForPersistence
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistoryPersistence
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/**
 * Runs a monthly job that performs Safety Labels-related tasks. (E.g., data policy changes
 * notification, hygiene, etc.)
 */
// TODO(b/265202443): Review support for safe cancellation of this Job. Currently this is
//  implemented by implementing `onStopJob` method and including `yield()` calls in computation
//  loops.
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class SafetyLabelChangesJobService : JobService() {
    private var mainJobTask: Job? = null
    private val context = this@SafetyLabelChangesJobService

    class Receiver : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Received broadcast with intent action '${intent.action}'")
            }
            if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled()) {
                Log.i(LOG_TAG, "onReceive: Safety label change notifications are not enabled.")
                return
            }
            if (isContextInProfileUser(receiverContext)) {
                Log.i(
                    LOG_TAG,
                    "onReceive: Received broadcast in profile, not scheduling safety label" +
                        " change job")
                return
            }
            if (intent.action != ACTION_BOOT_COMPLETED &&
                intent.action != ACTION_SET_UP_SAFETY_LABEL_CHANGES_JOB) {
                return
            }
            schedulePeriodicJob(receiverContext)
        }

        private fun isContextInProfileUser(context: Context): Boolean {
            val userManager: UserManager = context.getSystemService(UserManager::class.java)!!
            return userManager.isProfile
        }
    }

    /**
     * Called twice each interval, first for the periodic job
     * [PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID], then for the main job [SAFETY_LABEL_CHANGES_JOB_ID].
     */
    override fun onStartJob(params: JobParameters): Boolean {
        if (DEBUG) {
            Log.d(LOG_TAG, "Starting safety label change job Id: ${params.jobId}")
        }
        if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled()) {
            Log.w(LOG_TAG, "onStartJob: Safety label change notifications are not enabled.")
            return false
        }
        when (params.jobId) {
            PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID -> scheduleMainJobWithDelay()
            SAFETY_LABEL_CHANGES_JOB_ID -> {
                dispatchMainJobTask(params)
                return true
            }
            else -> Log.w(LOG_TAG, "Unexpected job Id: ${params.jobId}")
        }
        if (DEBUG) {
            Log.d(LOG_TAG, "safety label change job Id: ${params.jobId} completed.")
        }
        return false
    }

    private fun dispatchMainJobTask(params: JobParameters) {
        mainJobTask =
            GlobalScope.launch(Dispatchers.Default) {
                try {
                    runMainJob()
                } catch (e: Throwable) {
                    Log.e(LOG_TAG, "Main Job failed", e)
                    throw e
                } finally {
                    jobFinished(params, false)
                }
            }
    }

    private suspend fun runMainJob() {
        performHygieneOnSafetyLabelHistoryPersistence()
        postSafetyLabelChangedNotification()
    }

    private suspend fun performHygieneOnSafetyLabelHistoryPersistence() {
        val historyFile = AppsSafetyLabelHistoryPersistence.getSafetyLabelHistoryFile(context)
        val safetyLabelsLastUpdatedTimes: Map<AppInfo, Instant> =
            AppsSafetyLabelHistoryPersistence.getSafetyLabelsLastUpdatedTimes(historyFile)
        // Retrieve all installed packages that are not pre-installed on the system and
        // that request the location permission; these are the packages that we care about for the
        // safety labels feature. The variable name does not specify all these filters for brevity.
        val packagesRequestingLocation: Set<Pair<String, UserHandle>> =
            getAllNonPreinstalledPackagesRequestingLocation()

        recordSafetyLabelsIfMissing(
            historyFile, packagesRequestingLocation, safetyLabelsLastUpdatedTimes)
        deleteSafetyLabelsNoLongerNeeded(
            historyFile, packagesRequestingLocation, safetyLabelsLastUpdatedTimes)
    }

    /**
     * Records safety labels for apps that may not have propagated their safety labels to
     * persistence through [SafetyLabelChangedBroadcastReceiver].
     *
     * This is done by:
     * 1. Initializing safety labels for apps that are relevant, but have no persisted safety labels
     *    yet.
     * 2. Update safety labels for apps that are relevant and have persisted safety labels, if we
     *    identify that we have missed an update for them.
     */
    private suspend fun recordSafetyLabelsIfMissing(
        historyFile: File,
        packagesRequestingLocation: Set<Pair<String, UserHandle>>,
        safetyLabelsLastUpdatedTimes: Map<AppInfo, Instant>
    ) {
        val safetyLabelsToRecord = mutableSetOf<SafetyLabelForPersistence>()
        val packageNamesWithPersistedSafetyLabels =
            safetyLabelsLastUpdatedTimes.keys.map { it.packageName }

        // Partition relevant apps by whether we already store safety labels for them.
        val (packagesToConsiderUpdate, packagesToInitialize) =
            packagesRequestingLocation.partition { (packageName, _) ->
                packageName in packageNamesWithPersistedSafetyLabels
            }
        if (DEBUG) {
            Log.d(
                LOG_TAG,
                "recording safety labels if missing:" +
                    " packagesRequestingLocation:" +
                    " $packagesRequestingLocation, packageNamesWithPersistedSafetyLabels:" +
                    " $packageNamesWithPersistedSafetyLabels")
        }
        safetyLabelsToRecord.addAll(getSafetyLabels(packagesToInitialize))
        safetyLabelsToRecord.addAll(
            getSafetyLabelsIfUpdatesMissed(packagesToConsiderUpdate, safetyLabelsLastUpdatedTimes))

        AppsSafetyLabelHistoryPersistence.recordSafetyLabels(safetyLabelsToRecord, historyFile)
    }

    private suspend fun getSafetyLabels(
        packages: List<Pair<String, UserHandle>>
    ): List<SafetyLabelForPersistence> {
        val safetyLabelsToPersist = mutableListOf<SafetyLabelForPersistence>()

        for ((packageName, user) in packages) {
            yield() // cancellation point
            val safetyLabelToPersist = getSafetyLabelToPersist(Pair(packageName, user))
            if (safetyLabelToPersist != null) {
                safetyLabelsToPersist.add(safetyLabelToPersist)
            }
        }
        return safetyLabelsToPersist
    }

    private suspend fun getSafetyLabelsIfUpdatesMissed(
        packages: List<Pair<String, UserHandle>>,
        safetyLabelsLastUpdatedTimes: Map<AppInfo, Instant>
    ): List<SafetyLabelForPersistence> {
        val safetyLabelsToPersist = mutableListOf<SafetyLabelForPersistence>()
        for ((packageName, user) in packages) {
            yield() // cancellation point

            // If safety labels are considered up-to-date, continue as there is no need to retrieve
            // the latest safety label; it was already captured.
            if (areSafetyLabelsUpToDate(Pair(packageName, user), safetyLabelsLastUpdatedTimes)) {
                continue
            }

            val safetyLabelToPersist = getSafetyLabelToPersist(Pair(packageName, user))
            if (safetyLabelToPersist != null) {
                safetyLabelsToPersist.add(safetyLabelToPersist)
            }
        }

        return safetyLabelsToPersist
    }

    /**
     * Returns whether the provided app's safety labels are up-to-date by checking that there have
     * been no app updates since the persisted safety label history was last updated.
     */
    private suspend fun areSafetyLabelsUpToDate(
        packageKey: Pair<String, UserHandle>,
        safetyLabelsLastUpdatedTimes: Map<AppInfo, Instant>
    ): Boolean {
        val lightPackageInfo = LightPackageInfoLiveData[packageKey].getInitializedValue()
        val lastAppUpdateTime: Instant =
            Instant.ofEpochMilli(lightPackageInfo?.lastUpdateTime ?: 0)
        val latestSafetyLabelUpdateTime: Instant? =
            safetyLabelsLastUpdatedTimes[AppInfo(packageKey.first)]
        return latestSafetyLabelUpdateTime != null &&
            !lastAppUpdateTime.isAfter(latestSafetyLabelUpdateTime)
    }

    private suspend fun getSafetyLabelToPersist(
        packageKey: Pair<String, UserHandle>
    ): SafetyLabelForPersistence? {
        val (packageName, user) = packageKey

        // Get the context for the user in which the app is installed.
        val userContext =
            if (user == Process.myUserHandle()) {
                context
            } else {
                context.createContextAsUser(user, 0)
            }
        val appMetadataBundle: PersistableBundle =
            userContext.packageManager.getAppMetadata(packageName)
        val appMetadataSafetyLabel: AppMetadataSafetyLabel =
            AppMetadataSafetyLabel.getSafetyLabelFromMetadata(appMetadataBundle) ?: return null
        val lastUpdateTime =
            Instant.ofEpochMilli(
                LightPackageInfoLiveData[packageKey].getInitializedValue()?.lastUpdateTime ?: 0)

        val safetyLabelForPersistence: SafetyLabelForPersistence =
            AppsSafetyLabelHistory.SafetyLabel.fromAppMetadataSafetyLabel(
                packageName, lastUpdateTime, appMetadataSafetyLabel)

        return safetyLabelForPersistence
    }

    /**
     * Deletes safety labels from persistence that are no longer necessary to persist.
     *
     * This is done by:
     * 1. Deleting safety labels for apps that are no longer relevant (e.g. app not installed or app
     *    not requesting location permission).
     * 2. Delete safety labels if there are multiple safety labels prior to the update period; at
     *    most one safety label is necessary to be persisted prior to the update period to determine
     *    updates to safety labels.
     */
    private fun deleteSafetyLabelsNoLongerNeeded(
        historyFile: File,
        packagesRequestingLocation: Set<Pair<String, UserHandle>>,
        safetyLabelsLastUpdatedTimes: Map<AppInfo, Instant>
    ) {
        val packageNamesWithPersistedSafetyLabels: List<String> =
            safetyLabelsLastUpdatedTimes.keys.map { appInfo -> appInfo.packageName }
        val packageNamesWithRelevantSafetyLabels: List<String> =
            packagesRequestingLocation.map { (packageName, _) -> packageName }

        val appInfosToDelete: Set<AppInfo> =
            packageNamesWithPersistedSafetyLabels
                .filter { packageName -> packageName !in packageNamesWithRelevantSafetyLabels }
                .map { packageName -> AppInfo(packageName) }
                .toSet()
        AppsSafetyLabelHistoryPersistence.deleteSafetyLabelsForApps(appInfosToDelete, historyFile)

        val updatePeriod =
            DeviceConfig.getLong(
                DeviceConfig.NAMESPACE_PRIVACY,
                DATA_SHARING_UPDATE_PERIOD_PROPERTY,
                Duration.ofDays(DEFAULT_DATA_SHARING_UPDATE_PERIOD_DAYS).toMillis())
        AppsSafetyLabelHistoryPersistence.deleteSafetyLabelsOlderThan(
            Instant.now().atZone(ZoneId.systemDefault()).toInstant().minusMillis(updatePeriod),
            historyFile)
    }

    // TODO(b/261607291): Modify this logic when we enable safety label change notifications for
    //  preinstalled apps.
    private suspend fun getAllNonPreinstalledPackagesRequestingLocation():
        Set<Pair<String, UserHandle>> =
        getAllPackagesRequestingLocation().filter { !isPreinstalledPackage(it) }.toSet()

    private suspend fun getAllPackagesRequestingLocation(): Set<Pair<String, UserHandle>> =
        SinglePermGroupPackagesUiInfoLiveData[Manifest.permission_group.LOCATION]
            .getInitializedValue(staleOk = false, forceUpdate = true)
            .keys

    private suspend fun getAllPackagesGrantedLocation(): Set<Pair<String, UserHandle>> =
        SinglePermGroupPackagesUiInfoLiveData[Manifest.permission_group.LOCATION]
            .getInitializedValue(staleOk = false, forceUpdate = true)
            .filter { (_, appPermGroupUiInfo) -> appPermGroupUiInfo.isPermissionGranted() }
            .keys

    private fun AppPermGroupUiInfo.isPermissionGranted() =
        permGrantState in setOf(PERMS_ALLOWED_ALWAYS, PERMS_ALLOWED_FOREGROUND_ONLY)

    private suspend fun isPreinstalledPackage(pkg: Pair<String, UserHandle>): Boolean =
        LightInstallSourceInfoLiveData[pkg].getInitializedValue().installingPackageName == null

    private suspend fun postSafetyLabelChangedNotification() {
        if (hasDataSharingChanged()) {
            Log.i(LOG_TAG, "Showing notification: data sharing has changed")
            showNotification()
        } else {
            Log.i(LOG_TAG, "Not showing notification: data sharing has not changed")
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        if (DEBUG) {
            Log.d(LOG_TAG, "onStopJob: stopping job ${params?.jobId}")
        }
        if (params?.jobId == SAFETY_LABEL_CHANGES_JOB_ID) {
            runBlocking {
                mainJobTask?.cancelAndJoin()
                mainJobTask = null
            }
        }
        return true
    }

    private suspend fun hasDataSharingChanged(): Boolean {
        val appDataSharingUpdates =
            AppDataSharingUpdatesLiveData(PermissionControllerApplication.get())
                .getInitializedValue()
        val packageNamesWithLocationDataSharingUpdates: List<String> =
            appDataSharingUpdates
                .filter { it.containsLocationCategoryUpdate() }
                .map { it.packageName }
        val packageNamesWithLocationGranted: List<String> =
            getAllPackagesGrantedLocation().map { (packageName, _) -> packageName }

        val packageNamesWithLocationGrantedAndUpdates =
            packageNamesWithLocationDataSharingUpdates.intersect(packageNamesWithLocationGranted)
        if (DEBUG) {
            Log.i(
                LOG_TAG,
                "Checking whether data sharing has changed. Packages with location" +
                    " updates: $packageNamesWithLocationDataSharingUpdates; Packages with" +
                    " location permission granted: $packageNamesWithLocationGranted")
        }

        return packageNamesWithLocationGrantedAndUpdates.isNotEmpty()
    }

    private fun AppDataSharingUpdate.containsLocationCategoryUpdate() =
        categorySharingUpdates[CATEGORY_LOCATION] != null

    private fun showNotification() {
        val context = PermissionControllerApplication.get() as Context
        val notificationManager = getSystemServiceSafe(context, NotificationManager::class.java)
        createNotificationChannel(context, notificationManager)

        val title = context.getString(R.string.safety_label_changes_notification_title)
        val text = context.getString(R.string.safety_label_changes_notification_desc)
        var notificationBuilder =
            NotificationCompat.Builder(context, PERMISSION_REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(createIntentToOpenAppDataSharingUpdates(context))

        val settingsAppLabel =
            Utils.getSettingsLabelForNotifications(applicationContext.packageManager)
        if (settingsAppLabel != null) {
            notificationBuilder =
                notificationBuilder
                    .setSmallIcon(R.drawable.ic_settings_24dp)
                    .addExtras(
                        Bundle().apply {
                            putString(
                                Notification.EXTRA_SUBSTITUTE_APP_NAME, settingsAppLabel.toString())
                        })
        }

        notificationManager.notify(
            SAFETY_LABEL_CHANGES_NOTIFICATION_ID, notificationBuilder.build())

        if (DEBUG) {
            Log.v(LOG_TAG, "Safety label change notification sent.")
        }
    }

    private fun createIntentToOpenAppDataSharingUpdates(context: Context): PendingIntent? {
        return PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel(
        context: Context,
        notificationManager: NotificationManager
    ) {
        val notificationChannel =
            NotificationChannel(
                PERMISSION_REMINDER_CHANNEL_ID,
                context.getString(R.string.permission_reminders),
                NotificationManager.IMPORTANCE_LOW)

        notificationManager.createNotificationChannel(notificationChannel)
    }

    companion object {
        private val LOG_TAG = SafetyLabelChangesJobService::class.java.simpleName
        private const val DEBUG = true

        private const val ACTION_SET_UP_SAFETY_LABEL_CHANGES_JOB =
            "com.android.permissioncontroller.action.SET_UP_SAFETY_LABEL_CHANGES_JOB"

        private const val DATA_SHARING_UPDATE_PERIOD_PROPERTY = "data_sharing_update_period_millis"
        private const val DEFAULT_DATA_SHARING_UPDATE_PERIOD_DAYS: Long = 30

        private fun schedulePeriodicJob(context: Context) {
            try {
                val jobScheduler = getSystemServiceSafe(context, JobScheduler::class.java)
                if (jobScheduler.getPendingJob(PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID) != null) {
                    Log.i(LOG_TAG, "Safety label change periodic job already scheduled.")
                    return
                }

                val job =
                    JobInfo.Builder(
                            PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID,
                            ComponentName(context, SafetyLabelChangesJobService::class.java))
                        .setPeriodic(KotlinUtils.getSafetyLabelChangesJobIntervalMillis())
                        .setPersisted(true)
                        .build()
                val result = jobScheduler.schedule(job)
                if (result != JobScheduler.RESULT_SUCCESS) {
                    Log.w(LOG_TAG, "Safety label job not scheduled, result code: $result")
                } else {
                    Log.d(LOG_TAG, "Safety label job scheduled.")
                }
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Failed to schedule periodic job", e)
                throw e
            }
        }

        private fun scheduleMainJobWithDelay() {
            try {
                val context = PermissionControllerApplication.get() as Context
                val jobScheduler = getSystemServiceSafe(context, JobScheduler::class.java)

                if (jobScheduler.getPendingJob(SAFETY_LABEL_CHANGES_JOB_ID) != null) {
                    Log.w(LOG_TAG, "safety label change job (one time) already scheduled")
                    return
                }

                val job =
                    JobInfo.Builder(
                            SAFETY_LABEL_CHANGES_JOB_ID,
                            ComponentName(context, SafetyLabelChangesJobService::class.java))
                        .setPersisted(true)
                        .setRequiresDeviceIdle(
                            KotlinUtils.runSafetyLabelChangesJobOnlyWhenDeviceIdle())
                        .setMinimumLatency(KotlinUtils.getSafetyLabelChangesJobDelayMillis())
                        .build()
                val result = jobScheduler.schedule(job)
                if (result == JobScheduler.RESULT_SUCCESS) {
                    Log.i(LOG_TAG, "main job scheduled successfully")
                } else {
                    Log.i(LOG_TAG, "main job not scheduled, result: $result")
                }
                Log.i(LOG_TAG, "periodic job running completes.")
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Failed to schedule job", e)
                throw e
            }
        }
    }
}
