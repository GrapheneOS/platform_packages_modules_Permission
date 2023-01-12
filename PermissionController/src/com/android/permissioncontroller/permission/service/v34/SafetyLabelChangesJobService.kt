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

import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.android.permissioncontroller.Constants.PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID
import com.android.permissioncontroller.Constants.PERMISSION_REMINDER_CHANNEL_ID
import com.android.permissioncontroller.Constants.SAFETY_LABEL_CHANGES_JOB_ID
import com.android.permissioncontroller.Constants.SAFETY_LABEL_CHANGES_NOTIFICATION_ID
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils.getSystemServiceSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Runs a monthly job that performs Safety Labels-related tasks, e.g., data policy changes
 * notification, hygiene, etc.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class SafetyLabelChangesJobService : JobService() {
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled()) {
                return
            }
            if (intent.action != ACTION_BOOT_COMPLETED) {
                return
            }
            Log.i(LOG_TAG, "Received broadcast")
            schedulePeriodicJob(context)
        }
    }

    /**
     * Called twice each interval, first for the periodic job
     * [PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID], then for the main job [SAFETY_LABEL_CHANGES_JOB_ID].
     */
    override fun onStartJob(params: JobParameters): Boolean {
        if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled()) {
            return false
        }
        when (params.jobId) {
            PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID -> scheduleMainJobWithDelay()
            SAFETY_LABEL_CHANGES_JOB_ID -> {
                dispatchMainJobTask(params)
                return true
            }
            else -> Log.w(LOG_TAG, "Unexpected job ID")
        }
        return false
    }

    private fun dispatchMainJobTask(params: JobParameters) {
        GlobalScope.launch(Dispatchers.Default) {
            try {
                Log.i(LOG_TAG, "Job started")
                runMainJob()
                Log.i(LOG_TAG, "Job finished successfully")
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Job failed", e)
                throw e
            } finally {
                jobFinished(params, false)
            }
        }
    }

    private fun runMainJob() {
        postSafetyLabelChangedNotification()
    }

    private fun postSafetyLabelChangedNotification() {
        if (hasDataSharingChanged()) {
            Log.i(LOG_TAG, "Showing notification: data sharing has changed")
            showNotification()
        } else {
            Log.i(LOG_TAG, "Not showing notification: data sharing has not changed")
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    private fun hasDataSharingChanged(): Boolean {
        // TODO(b/261663886): Check whether data sharing has changed
        return true
    }

    private fun showNotification() {
        val context = PermissionControllerApplication.get() as Context
        val notificationManager = getSystemServiceSafe(context, NotificationManager::class.java)

        createNotificationChannel(context, notificationManager)

        val notification =
            NotificationCompat.Builder(context, PERMISSION_REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_info)
                .setContentTitle(
                    context.getString(R.string.safety_label_changes_notification_title))
                .setContentText(context.getString(R.string.safety_label_changes_notification_desc))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setSilent(true)
                .build()

        notificationManager.notify(SAFETY_LABEL_CHANGES_NOTIFICATION_ID, notification)
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

        private fun schedulePeriodicJob(context: Context) {
            try {
                val jobScheduler = getSystemServiceSafe(context, JobScheduler::class.java)

                if (jobScheduler.getPendingJob(PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID) != null) {
                    Log.i(LOG_TAG, "Not scheduling periodic job: already scheduled")
                    return
                }

                Log.i(LOG_TAG, "Scheduling periodic job")
                val job =
                    JobInfo.Builder(
                            PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID,
                            ComponentName(context, SafetyLabelChangesJobService::class.java))
                        .setPersisted(true)
                        .setRequiresDeviceIdle(
                            KotlinUtils.runSafetyLabelChangesJobOnlyWhenDeviceIdle())
                        .setPeriodic(KotlinUtils.getSafetyLabelChangesJobIntervalMillis())
                        .build()
                jobScheduler.schedule(job)
                Log.i(LOG_TAG, "Periodic job scheduled successfully")
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
                    Log.i(LOG_TAG, "Not scheduling job: already scheduled")
                    return
                }

                Log.i(LOG_TAG, "Scheduling job")
                val job =
                    JobInfo.Builder(
                            SAFETY_LABEL_CHANGES_JOB_ID,
                            ComponentName(context, SafetyLabelChangesJobService::class.java))
                        .setPersisted(true)
                        .setRequiresDeviceIdle(
                            KotlinUtils.runSafetyLabelChangesJobOnlyWhenDeviceIdle())
                        .setMinimumLatency(KotlinUtils.getSafetyLabelChangesJobDelayMillis())
                        .build()
                jobScheduler.schedule(job)
                Log.i(LOG_TAG, "Job scheduled successfully")
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Failed to schedule job", e)
                throw e
            }
        }
    }
}
