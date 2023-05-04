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

package com.android.permissioncontroller.permission.service.v33

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.SensorPrivacyManager.Sensors.MICROPHONE
import android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.annotation.RequiresApi
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants

@RequiresApi(TIRAMISU)
class CorrectSensorPrivacyJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        correctStateIfNeeded(this)
        cancelJobIfStateCorrect()
        jobFinished(params, false)
        return false
    }

    private fun cancelJobIfStateCorrect() {
        if (isStateCorrect(this)) {
            getSystemService(JobScheduler::class.java)!!.cancel(
                Constants.FIX_SENSOR_PRIVACY_STATE_PERIODIC_JOB_ID)
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        cancelJobIfStateCorrect()
        return false
    }

    companion object {
        private const val WAIT_TIME_MS = 5 * 60 * 1000L
        fun scheduleJobIfNeeded(context: Context) {
            if (!SdkLevel.isAtLeastT()) {
                return
            }

            // Try to fix the state now
            correctStateIfNeeded(context)
            val jobScheduler = context.getSystemService(JobScheduler::class.java)!!
            if (jobScheduler.getPendingJob(Constants.FIX_SENSOR_PRIVACY_STATE_PERIODIC_JOB_ID) !=
                null || isStateCorrect(context)) {
                return
            }
            val jobInfo = JobInfo.Builder(
                Constants.FIX_SENSOR_PRIVACY_STATE_PERIODIC_JOB_ID,
                ComponentName(context, CorrectSensorPrivacyJobService::class.java))
                .setPeriodic(WAIT_TIME_MS)
                .build()
            context.getSystemService(JobScheduler::class.java)!!.schedule(jobInfo)
        }

        private fun isStateCorrect(context: Context): Boolean {
            val spManager = context.getSystemService(SensorPrivacyManager::class.java)!!
            val cameraCorrect = spManager.supportsSensorToggle(CAMERA) ||
                !spManager.isSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE, CAMERA)
            val micCorrect = spManager.supportsSensorToggle(MICROPHONE) ||
                !spManager.isSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE, MICROPHONE)
            return cameraCorrect && micCorrect
        }

        private fun correctStateIfNeeded(context: Context) {
            if (isStateCorrect(context)) {
                return
            }

            val spManager = context.getSystemService(SensorPrivacyManager::class.java)!!
            if (spManager.isSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE, CAMERA) &&
                !spManager.supportsSensorToggle(CAMERA)) {
                spManager.setSensorPrivacy(CAMERA, false)
            }
            if (spManager.isSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE, MICROPHONE) &&
                !spManager.supportsSensorToggle(MICROPHONE)) {
                spManager.setSensorPrivacy(MICROPHONE, false)
            }
        }
    }
}