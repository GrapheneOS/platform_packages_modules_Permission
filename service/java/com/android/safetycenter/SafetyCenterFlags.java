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
import android.annotation.Nullable;
import android.os.Binder;
import android.provider.DeviceConfig;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
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
            "safety_center_show_error_entries_on_timeout";

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

    private static final String PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES =
            "safety_center_background_refresh_denied_sources";

    private static final String PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS =
            "safety_center_refresh_sources_timeouts_millis";

    private static final String PROPERTY_ISSUE_CATEGORY_ALLOWLISTS =
            "safety_center_issue_category_allowlists";

    private static final String PROPERTY_ALLOW_STATSD_LOGGING_IN_TESTS =
            "safety_center_allow_statsd_logging_in_tests";

    private static final Duration REFRESH_SOURCES_TIMEOUT_DEFAULT_DURATION = Duration.ofSeconds(15);

    private static final Duration RESOLVING_ACTION_TIMEOUT_DEFAULT_DURATION =
            Duration.ofSeconds(10);

    private static final Duration FGS_ALLOWLIST_DEFAULT_DURATION = Duration.ofSeconds(20);

    private static final long RESURFACE_ISSUE_DEFAULT_MAX_COUNT = 0;

    private static final Duration RESURFACE_ISSUE_DEFAULT_DELAY = Duration.ofDays(180);

    /** Dumps state for debugging purposes. */
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
        printFlag(
                fout,
                PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES,
                getBackgroundRefreshDeniedSourceIds());
        printFlag(
                fout, PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS, getRefreshSourcesTimeoutsMillis());
        printFlag(fout, PROPERTY_ISSUE_CATEGORY_ALLOWLISTS, getIssueCategoryAllowlists());
        printFlag(fout, PROPERTY_ALLOW_STATSD_LOGGING_IN_TESTS, getAllowStatsdLoggingInTests());
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
    static ArraySet<String> getBackgroundRefreshDeniedSourceIds() {
        return getCommaSeparatedStrings(PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES);
    }

    /**
     * Returns the time for which a Safety Center refresh is allowed to wait for a source to respond
     * to a refresh request before timing out and marking the refresh as completed, based on the
     * reason for the refresh.
     */
    static Duration getRefreshSourcesTimeout(@RefreshReason int refreshReason) {
        String refreshSourcesTimeouts = getRefreshSourcesTimeoutsMillis();
        Long timeout = getLongValueFromStringMapping(refreshSourcesTimeouts, refreshReason);
        if (timeout != null) {
            return Duration.ofMillis(timeout);
        }
        return REFRESH_SOURCES_TIMEOUT_DEFAULT_DURATION;
    }

    /**
     * Returns a comma-delimited list of colon-delimited pairs where the left value is a {@link
     * RefreshReason} and the right value is the refresh timeout applied for each source in case of
     * a refresh.
     */
    @NonNull
    private static String getRefreshSourcesTimeoutsMillis() {
        return getString(PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS, "");
    }

    /**
     * Returns the number of times an issue of the given {@link SafetySourceData.SeverityLevel}
     * should be resurfaced.
     */
    static long getResurfaceIssueMaxCount(@SafetySourceData.SeverityLevel int severityLevel) {
        String maxCountsConfigString = getResurfaceIssueMaxCounts();
        Long maxCount = getLongValueFromStringMapping(maxCountsConfigString, severityLevel);
        if (maxCount != null) {
            return maxCount;
        }
        return RESURFACE_ISSUE_DEFAULT_MAX_COUNT;
    }

    /**
     * Returns a comma-delimited list of colon-delimited pairs where the left value is an issue
     * {@link SafetySourceData.SeverityLevel} and the right value is the number of times an issue of
     * this {@link SafetySourceData.SeverityLevel} should be resurfaced.
     */
    @NonNull
    private static String getResurfaceIssueMaxCounts() {
        return getString(PROPERTY_RESURFACE_ISSUE_MAX_COUNTS, "");
    }

    /**
     * Returns the time after which a dismissed issue of the given {@link
     * SafetySourceData.SeverityLevel} will resurface if it has not reached the maximum count for
     * which a dismissed issue of the given {@link SafetySourceData.SeverityLevel} should be
     * resurfaced.
     */
    @NonNull
    static Duration getResurfaceIssueDelay(@SafetySourceData.SeverityLevel int severityLevel) {
        String delaysConfigString = getResurfaceIssueDelaysMillis();
        Long delayMillis = getLongValueFromStringMapping(delaysConfigString, severityLevel);
        if (delayMillis != null) {
            return Duration.ofMillis(delayMillis);
        }
        return RESURFACE_ISSUE_DEFAULT_DELAY;
    }

    /**
     * Returns a comma-delimited list of colon-delimited pairs where the left value is an issue
     * {@link SafetySourceData.SeverityLevel} and the right value is the time after which a
     * dismissed issue of this safety source severity level will resurface if it has not reached the
     * maximum count for which a dismissed issue of this {@link SafetySourceData.SeverityLevel}
     * should be resurfaced.
     */
    @NonNull
    private static String getResurfaceIssueDelaysMillis() {
        return getString(PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS, "");
    }

    /**
     * Returns whether a safety source is allowed to send issues for the given {@link
     * SafetySourceIssue.IssueCategory}.
     */
    @NonNull
    static boolean isIssueCategoryAllowedForSource(
            @SafetySourceIssue.IssueCategory int issueCategory, @NonNull String safetySourceId) {
        String issueCategoryAllowlists = getIssueCategoryAllowlists();
        String allowlistString =
                getStringValueFromStringMapping(issueCategoryAllowlists, issueCategory);
        if (allowlistString == null) {
            return true;
        }
        String[] allowlistArray = allowlistString.split("\\|");
        for (int i = 0; i < allowlistArray.length; i++) {
            if (allowlistArray[i].equals(safetySourceId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a comma-delimited list of colon-delimited pairs where the left value is an issue
     * {@link SafetySourceIssue.IssueCategory} and the right value is a vertical-bar-delimited list
     * of IDs of safety sources that are allowed to send issues with this category.
     */
    @NonNull
    private static String getIssueCategoryAllowlists() {
        return getString(PROPERTY_ISSUE_CATEGORY_ALLOWLISTS, "");
    }

    /** Returns whether we allow statsd logging in tests. */
    static boolean getAllowStatsdLoggingInTests() {
        return getBoolean(PROPERTY_ALLOW_STATSD_LOGGING_IN_TESTS, false);
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

    /**
     * Gets a long value for the provided integer key in a comma separated list of colon separated
     * pairs of integers and longs.
     */
    @Nullable
    private static Long getLongValueFromStringMapping(@NonNull String config, int key) {
        String valueString = getStringValueFromStringMapping(config, key);
        if (valueString == null) {
            return null;
        }
        try {
            return Long.parseLong(valueString);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Badly formatted string config: " + config, e);
            return null;
        }
    }

    /**
     * Gets a value for the provided integer key in a comma separated list of colon separated pairs
     * of integers and strings.
     */
    @Nullable
    private static String getStringValueFromStringMapping(@NonNull String config, int key) {
        return getStringValueFromStringMapping(config, Integer.toString(key));
    }

    /**
     * Gets a value for the provided key in a comma separated list of colon separated key-value
     * string pairs.
     */
    @Nullable
    private static String getStringValueFromStringMapping(
            @NonNull String config, @NonNull String key) {
        if (config.isEmpty()) {
            return null;
        }
        String[] pairsList = config.split(",");
        for (int i = 0; i < pairsList.length; i++) {
            String[] pair = pairsList[i].split(":", -1 /* allow trailing empty strings */);
            if (pair.length != 2) {
                Log.w(TAG, "Badly formatted string config: " + config);
                continue;
            }
            if (pair[0].equals(key)) {
                return pair[1];
            }
        }
        return null;
    }

    private SafetyCenterFlags() {}
}
