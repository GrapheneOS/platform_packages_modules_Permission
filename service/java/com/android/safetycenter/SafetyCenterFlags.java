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

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.safetycenter.SafetyCenterManager.RefreshReason;

import android.annotation.NonNull;
import android.os.Binder;
import android.provider.DeviceConfig;
import android.safetycenter.SafetySourceData;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.PrintWriter;
import java.time.Duration;

/** A class to access the Safety Center {@link DeviceConfig} flags. */
@RequiresApi(TIRAMISU)
final class SafetyCenterFlags {

    private static final String TAG = "SafetyCenterFlags";

    /** {@link DeviceConfig} property name for {@link #getSafetyCenterEnabled()}. */
    static final String PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled";

    private static final String PROPERTY_SHOW_ERROR_ENTRIES_ON_TIMEOUT =
            "show_error_entries_on_timeout";

    private static final String PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION =
            "safety_center_replace_lock_screen_icon_action";

    private static final String PROPERTY_RESOLVING_ACTION_TIMEOUT_MILLIS =
            "safety_center_resolve_action_timeout_millis";

    private static final String PROPERTY_FGS_ALLOWLIST_DURATION_MILLIS =
            "safety_center_refresh_fgs_allowlist_duration_millis";

    private static final String PROPERTY_RESURFACE_ISSUE_MAX_COUNTS =
            "safety_center_resurface_issue_max_counts";

    private static final String PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS =
            "safety_center_resurface_issue_delays_millis";

    private static final String PROPERTY_UNTRACKED_SOURCES = "safety_center_untracked_sources";

    private static final String PROPERTY_NO_BACKGROUND_REFRESH_SOURCES =
            "safety_center_no_background_refresh_sources";

    private static final String PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS =
            "safety_center_refresh_sources_timeouts_millis";

    private static final Duration REFRESH_SOURCES_TIMEOUT_DEFAULT_DURATION = Duration.ofSeconds(15);

    private static final Duration RESOLVING_ACTION_TIMEOUT_DEFAULT_DURATION =
            Duration.ofSeconds(10);

    private static final Duration FGS_ALLOWLIST_DEFAULT_DURATION = Duration.ofSeconds(20);

    private static final long RESURFACE_ISSUE_DEFAULT_MAX_COUNT = 0;

    private static final Duration RESURFACE_ISSUE_DEFAULT_DELAY = Duration.ofDays(180);

    /**
     * Dumps state for debugging purposes.
     *
     * @param fout {@link PrintWriter} to write to
     */
    static void dump(@NonNull PrintWriter fout) {
        fout.println("FLAGS");
        printFlag(fout, PROPERTY_SAFETY_CENTER_ENABLED, getSafetyCenterEnabled());
        printFlag(fout, PROPERTY_SHOW_ERROR_ENTRIES_ON_TIMEOUT, getShowErrorEntriesOnTimeout());
        printFlag(fout, PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION, getReplaceLockScreenIconAction());
        printFlag(fout, PROPERTY_RESOLVING_ACTION_TIMEOUT_MILLIS, getResolvingActionTimeout());
        printFlag(fout, PROPERTY_FGS_ALLOWLIST_DURATION_MILLIS, getFgsAllowlistDuration());
        printFlag(fout, PROPERTY_UNTRACKED_SOURCES, getUntrackedSourceIds());
        printFlag(fout, PROPERTY_RESURFACE_ISSUE_MAX_COUNTS, getResurfaceIssueMaxCounts());
        printFlag(fout, PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS, getResurfaceIssueDelaysMillis());
        printFlag(fout, PROPERTY_NO_BACKGROUND_REFRESH_SOURCES, getNoBackgroundRefreshSourceIds());
        printFlag(
                fout, PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS, getRefreshSourcesTimeoutsMillis());
        fout.println();
    }

    private static void printFlag(PrintWriter pw, String key, Duration duration) {
        printFlag(pw, key, duration.toMillis() + " (" + duration + ")");
    }

    private static void printFlag(PrintWriter pw, String key, Object value) {
        pw.println("\t" + key + "=" + value);
    }

    /** Returns whether Safety Center is enabled. */
    static boolean getSafetyCenterEnabled() {
        return getBoolean(PROPERTY_SAFETY_CENTER_ENABLED, false);
    }

    /**
     * Returns whether we should show error entries for sources that timeout when refreshing them.
     */
    static boolean getShowErrorEntriesOnTimeout() {
        return getBoolean(PROPERTY_SHOW_ERROR_ENTRIES_ON_TIMEOUT, true);
    }

    /**
     * Returns whether we should replace the lock screen source's {@link
     * android.safetycenter.SafetySourceStatus.IconAction}.
     */
    static boolean getReplaceLockScreenIconAction() {
        return getBoolean(PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION, true);
    }

    /**
     * Returns the time for which Safety Center will wait for a source to respond to a resolving
     * action before timing out.
     */
    static Duration getResolvingActionTimeout() {
        return getDuration(
                PROPERTY_RESOLVING_ACTION_TIMEOUT_MILLIS,
                RESOLVING_ACTION_TIMEOUT_DEFAULT_DURATION);
    }

    /**
     * Returns the time for which an app, upon receiving a Safety Center refresh broadcast, will be
     * placed on a temporary power allowlist allowing it to start a foreground service from the
     * background.
     */
    static Duration getFgsAllowlistDuration() {
        return getDuration(PROPERTY_FGS_ALLOWLIST_DURATION_MILLIS, FGS_ALLOWLIST_DEFAULT_DURATION);
    }

    /**
     * Returns the IDs of sources that should not be tracked, for example because they are
     * mid-rollout. Broadcasts are still sent to these sources.
     */
    @NonNull
    static ArraySet<String> getUntrackedSourceIds() {
        return getCommaSeparatedStrings(PROPERTY_UNTRACKED_SOURCES);
    }

    /**
     * Returns the IDs of sources that should only be refreshed when Safety Center is on screen. We
     * will refresh these sources only on page open and when the scan button is clicked.
     */
    @NonNull
    static ArraySet<String> getNoBackgroundRefreshSourceIds() {
        return getCommaSeparatedStrings(PROPERTY_NO_BACKGROUND_REFRESH_SOURCES);
    }

    /**
     * Returns the time for which a Safety Center refresh is allowed to wait for a source to respond
     * to a refresh request before timing out and marking the refresh as completed, based on the
     * reason for the refresh.
     */
    static Duration getRefreshSourcesTimeout(@RefreshReason int refreshReason) {
        Long timeout = getRefreshSourcesTimeoutsMillis().get(refreshReason);
        if (timeout != null) {
            return Duration.ofMillis(timeout);
        }
        return REFRESH_SOURCES_TIMEOUT_DEFAULT_DURATION;
    }

    /**
     * Returns a map where the key is a {@link RefreshReason} and the value is the timeout in millis
     * after which SafetyCenter would time out and mark the refresh as completed, despite not
     * getting a response from the source.
     */
    @NonNull
    private static ArrayMap<Integer, Long> getRefreshSourcesTimeoutsMillis() {
        String refreshSourcesTimeouts = getString(PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS, "");
        return convertStringConfigToMap(refreshSourcesTimeouts);
    }

    /**
     * Returns the number of times an issue of the given {@link SafetySourceData.SeverityLevel}
     * should be resurfaced.
     */
    static long getResurfaceIssueMaxCount(@SafetySourceData.SeverityLevel int severityLevel) {
        Long maxCount = getResurfaceIssueMaxCounts().get(severityLevel);
        if (maxCount != null) {
            return maxCount;
        }
        return RESURFACE_ISSUE_DEFAULT_MAX_COUNT;
    }

    /**
     * Returns a map where the key is an issue {@link SafetySourceData.SeverityLevel} and the value
     * is the number of times an issue of this {@link SafetySourceData.SeverityLevel} should be
     * resurfaced.
     */
    @NonNull
    private static ArrayMap<Integer, Long> getResurfaceIssueMaxCounts() {
        String maxCountsConfigString = getString(PROPERTY_RESURFACE_ISSUE_MAX_COUNTS, "");
        return convertStringConfigToMap(maxCountsConfigString);
    }

    /**
     * Returns the time after which a dismissed issue of the given {@link
     * SafetySourceData.SeverityLevel} will resurface if it has not reached the maximum count for
     * which a dismissed issue of the given {@link SafetySourceData.SeverityLevel} should be
     * resurfaced.
     */
    @NonNull
    static Duration getResurfaceIssueDelay(@SafetySourceData.SeverityLevel int severityLevel) {
        Long delayMillis = getResurfaceIssueDelaysMillis().get(severityLevel);
        if (delayMillis != null) {
            return Duration.ofMillis(delayMillis);
        }
        return RESURFACE_ISSUE_DEFAULT_DELAY;
    }

    /**
     * Returns a map where the key is an issue {@link SafetySourceData.SeverityLevel} and the value
     * is the time in milliseconds after which a dismissed issue of this {@link
     * SafetySourceData.SeverityLevel} will resurface if it has not reached the maximum count for
     * which a dismissed issue of this {@link SafetySourceData.SeverityLevel} should be resurfaced.
     */
    @NonNull
    private static ArrayMap<Integer, Long> getResurfaceIssueDelaysMillis() {
        String delaysConfigString = getString(PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS, "");
        return convertStringConfigToMap(delaysConfigString);
    }

    @NonNull
    private static Duration getDuration(@NonNull String property, @NonNull Duration defaultValue) {
        return Duration.ofMillis(getLong(property, defaultValue.toMillis()));
    }

    private static boolean getBoolean(@NonNull String property, boolean defaultValue) {
        // This call requires the READ_DEVICE_CONFIG permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY, property, defaultValue);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private static long getLong(@NonNull String property, long defaultValue) {
        // This call requires the READ_DEVICE_CONFIG permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return DeviceConfig.getLong(DeviceConfig.NAMESPACE_PRIVACY, property, defaultValue);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @NonNull
    private static ArraySet<String> getCommaSeparatedStrings(@NonNull String property) {
        return new ArraySet<>(getString(property, "").split(","));
    }

    @NonNull
    private static String getString(@NonNull String property, @NonNull String defaultValue) {
        // This call requires the READ_DEVICE_CONFIG permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return DeviceConfig.getString(DeviceConfig.NAMESPACE_PRIVACY, property, defaultValue);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /** Converts a comma separated list of colon separated pairs into an integer to long map. */
    @NonNull
    private static ArrayMap<Integer, Long> convertStringConfigToMap(@NonNull String input) {
        ArrayMap<Integer, Long> map = new ArrayMap<>();
        if (input.isEmpty()) {
            return map;
        }
        String[] pairsList = input.split(",");
        for (int i = 0; i < pairsList.length; i++) {
            String[] pair = pairsList[i].split(":");
            if (pair.length != 2) {
                Log.w(TAG, "Badly formatted string config: " + input);
                continue;
            }
            try {
                map.put(Integer.parseInt(pair[0]), Long.parseLong(pair[1]));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Badly formatted string config: " + input, e);
            }
        }
        return map;
    }

    private SafetyCenterFlags() {}
}
