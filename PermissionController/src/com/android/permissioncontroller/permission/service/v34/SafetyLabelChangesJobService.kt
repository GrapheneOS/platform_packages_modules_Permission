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
import android.os.UserHandle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.android.permission.safetylabel.SafetyLabel as AppMetadataSafetyLabel
import com.android.permissioncontroller.Constants.PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID
import com.android.permissioncontroller.Constants.PERMISSION_REMINDER_CHANNEL_ID
import com.android.permissioncontroller.Constants.SAFETY_LABEL_CHANGES_JOB_ID
import com.android.permissioncontroller.Constants.SAFETY_LABEL_CHANGES_NOTIFICATION_ID
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.LightInstallSourceInfoLiveData
import com.android.permissioncontroller.permission.data.SinglePermGroupPackagesUiInfoLiveData
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils.getSystemServiceSafe
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel as SafetyLabelForPersistence
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistoryPersistence
import java.time.Instant
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
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class SafetyLabelChangesJobService : JobService() {
    private var mainJobTask: Job? = null

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Received broadcast with intent action '${intent.action}'")
            }
            if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled()) {
                Log.w(LOG_TAG, "onReceive: Safety label change notifications are not enabled.")
                return
            }
            if (intent.action != ACTION_BOOT_COMPLETED &&
                intent.action != ACTION_SET_UP_SAFETY_LABEL_CHANGES_JOB) {
                return
            }
            schedulePeriodicJob(context)
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
        initializeSafetyLabels()
        postSafetyLabelChangedNotification()
    }

    private suspend fun initializeSafetyLabels() {
        val context = PermissionControllerApplication.get() as Context
        val historyFile = AppsSafetyLabelHistoryPersistence.getSafetyLabelHistoryFile(context)

        val packageNamesWithPersistedSafetyLabels: Set<String> =
            AppsSafetyLabelHistoryPersistence.getAppsWithSafetyLabels(historyFile)
                .map { it.packageName }
                .toSet()
        val packageNamesWithRelevantSafetyLabels: Set<String> =
            getAllNonPreinstalledPackageNamesRequestingLocation()
        val packageNamesToInitialize: Set<String> =
            packageNamesWithRelevantSafetyLabels - packageNamesWithPersistedSafetyLabels

        val safetyLabelsToPersist = mutableSetOf<SafetyLabelForPersistence>()

        for (packageName in packageNamesToInitialize) {
            yield() // cancellation point

            val appMetadataBundle = context.packageManager.getAppMetadata(packageName)
            val appMetadataSafetyLabel: AppMetadataSafetyLabel =
                AppMetadataSafetyLabel.getSafetyLabelFromMetadata(appMetadataBundle) ?: continue
            // TODO(b/264884404): Use install time or last update time for an app for the time a
            //  safety label is received instead of current time.
            val safetyLabelForPersistence: SafetyLabelForPersistence =
                AppsSafetyLabelHistory.SafetyLabel.fromAppMetadataSafetyLabel(
                    packageName, receivedAt = Instant.now(), appMetadataSafetyLabel)

            safetyLabelsToPersist.add(safetyLabelForPersistence)
        }

        AppsSafetyLabelHistoryPersistence.recordSafetyLabels(safetyLabelsToPersist, historyFile)
    }

    // TODO(b/261607291): Modify this logic when we enable safety label change notifications for
    //  preinstalled apps.
    private suspend fun getAllNonPreinstalledPackageNamesRequestingLocation(): Set<String> =
        getAllPackagesRequestingLocation()
            .filter { !isPreinstalledPackage(it) }
            .map { (packageName, _) -> packageName }
            .toSet()

    private suspend fun getAllPackagesRequestingLocation(): Set<Pair<String, UserHandle>> =
        SinglePermGroupPackagesUiInfoLiveData[Manifest.permission_group.LOCATION]
            .getInitializedValue()
            .keys

    private suspend fun isPreinstalledPackage(pkg: Pair<String, UserHandle>): Boolean =
        LightInstallSourceInfoLiveData[pkg].getInitializedValue().installingPackageName == null

    private fun postSafetyLabelChangedNotification() {
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

    private fun hasDataSharingChanged(): Boolean {
        // TODO(b/261663886): Check whether data sharing has changed
        return true
    }

    private fun showNotification() {
        val context = PermissionControllerApplication.get() as Context
        val notificationManager = getSystemServiceSafe(context, NotificationManager::class.java)
        createNotificationChannel(context, notificationManager)

        val title = context.getString(R.string.safety_label_changes_notification_title)
        val text = context.getString(R.string.safety_label_changes_notification_desc)
        val notification =
            NotificationCompat.Builder(context, PERMISSION_REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(createIntentToOpenAppDataSharingUpdates(context))
                .build()

        notificationManager.notify(SAFETY_LABEL_CHANGES_NOTIFICATION_ID, notification)
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
