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

package com.android.permissioncontroller.safetycenter.service;

import static android.app.job.JobScheduler.RESULT_SUCCESS;
import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PERIODIC;

import static com.android.permissioncontroller.Constants.SAFETY_CENTER_BACKGROUND_REFRESH_JOB_ID;

import static java.util.Objects.requireNonNull;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.safetycenter.SafetyCenterManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;

import java.time.Duration;

/**
 * Uses {@link android.app.job.JobScheduler} to schedule one-off calls to {@link
 * SafetyCenterManager#refreshSafetySources} after boot completed and after safety center is
 * enabled.
 *
 * <p>The job waits until the device is in idle maintenance mode to minimize impact on system
 * health.
 */
// TODO(b/243493200): Add tests
// TODO(b/243537828): Consider disabling this during other tests in case it makes them flakey
public final class SafetyCenterBackgroundRefreshJobService extends JobService {
    private static final String TAG = "SafetyCenterBackgroundR";
    private static final Duration DEFAULT_PERIODIC_BACKGROUND_REFRESH_INTERVAL = Duration.ofDays(1);
    /**
     * Schedules a one-off call to {@link SafetyCenterManager#refreshSafetySources} to be run when
     * the device is idle.
     */
    public static void schedulePeriodicBackgroundRefresh(
            @NonNull Context context, @Nullable String actionString) {

        if (!(ACTION_BOOT_COMPLETED.equals(actionString)
                || ACTION_SAFETY_CENTER_ENABLED_CHANGED.equals(actionString))) {
            return;
        }

        SafetyCenterManager safetyCenterManager =
                requireNonNull(context.getSystemService(SafetyCenterManager.class));
        if (!safetyCenterManager.isSafetyCenterEnabled()) {
            return;
        }

        Log.v(TAG, "Scheduling a periodic background refresh.");
        JobScheduler jobScheduler = requireNonNull(context.getSystemService(JobScheduler.class));
        // TODO(b/256610767): Consider adding setPriority(JobInfo.PRIORITY_LOW) and
        // setRequiresCharging(true)
        JobInfo.Builder builder =
                (new JobInfo.Builder(
                                SAFETY_CENTER_BACKGROUND_REFRESH_JOB_ID,
                                new ComponentName(
                                        context, SafetyCenterBackgroundRefreshJobService.class)))
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(getPeriodicBackgroundRefreshInterval().toMillis());
        int scheduleResult = jobScheduler.schedule(builder.build());
        if (scheduleResult != RESULT_SUCCESS) {
            Log.e(
                    TAG,
                    "Could not schedule the periodic background refresh, scheduleResult="
                            + scheduleResult);
        }
    }

    private static Duration getPeriodicBackgroundRefreshInterval() {
        // TODO(b/256610767): Add DeviceConfig/phenotype flag
        return DEFAULT_PERIODIC_BACKGROUND_REFRESH_INTERVAL;
    }

    @Override
    public boolean onStartJob(@NonNull JobParameters params) {
        // background thread not required, PC APK makes all API calls in main thread
        Log.v(TAG, "Background refresh job has started.");
        SafetyCenterManager safetyCenterManager =
                requireNonNull(this.getSystemService(SafetyCenterManager.class));
        if (safetyCenterManager.isSafetyCenterEnabled()) {
            safetyCenterManager.refreshSafetySources(getRefreshReason());
        }
        return false; // job is no longer running
    }

    private static int getRefreshReason() {
        if (SdkLevel.isAtLeastU()) {
            return REFRESH_REASON_PERIODIC;
        }
        return REFRESH_REASON_OTHER;
    }

    @Override
    public boolean onStopJob(@NonNull JobParameters params) {
        return false; // never want job to be rescheduled
    }

    /** Schedules a periodic background refresh. */
    public static final class SetupSafetyCenterBackgroundRefreshReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            schedulePeriodicBackgroundRefresh(context, intent.getAction());
        }
    }
}
