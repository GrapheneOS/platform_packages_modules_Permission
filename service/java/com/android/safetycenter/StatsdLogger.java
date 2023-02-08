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

import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__COMPLETE_GET_NEW_DATA;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__COMPLETE_RESCAN;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__INLINE_ACTION;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__SINGLE_SOURCE_GET_NEW_DATA;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__SINGLE_SOURCE_RESCAN;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__ERROR;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__SUCCESS;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__COLLECTION_TYPE__AUTOMATIC;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_UNSPECIFIED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_STATE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__UPDATE_TYPE__UPDATE_TYPE_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyCenterManager.RefreshRequestType;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetySourceData;
import android.util.Log;
import android.util.StatsEvent;

import androidx.annotation.RequiresApi;

import com.android.permission.PermissionStatsLog;
import com.android.permission.util.UserUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Marshalls and writes statsd atoms. Contains implementation details of how atom parameters are
 * encoded and provides a better-typed interface for other classes to call.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class StatsdLogger {

    private static final String TAG = "StatsdLogger";
    private static final long UNSET_SOURCE_ID = 0;
    private static final long UNSET_ISSUE_TYPE_ID = 0;

    @IntDef(
            prefix = {"SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__"},
            value = {
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__SUCCESS,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__ERROR,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT
            })
    @Retention(RetentionPolicy.SOURCE)
    @interface SystemEventResult {}

    @NonNull private final Context mContext;
    @NonNull private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    StatsdLogger(
            @NonNull Context context, @NonNull SafetyCenterConfigReader safetyCenterConfigReader) {
        mContext = context;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
    }

    /**
     * Creates a {@link PermissionStatsLog#SAFETY_STATE} {@link StatsEvent} with the given
     * parameters.
     */
    @NonNull
    StatsEvent createSafetyStateEvent(
            @SafetyCenterStatus.OverallSeverityLevel int severityLevel,
            long openIssueCount,
            long dismissedIssueCount) {
        return PermissionStatsLog.buildStatsEvent(
                SAFETY_STATE,
                toSafetyStateOverallSeverityLevel(severityLevel),
                openIssueCount,
                dismissedIssueCount);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_SOURCE_STATE_COLLECTED} atom.
     *
     * @param sourceSeverityLevel is the {@link SafetySourceData.SeverityLevel} to log for this
     *     source, or {@code null} if none/unknown severity should be recorded.
     */
    void writeSafetySourceStateCollected(
            @NonNull String sourceId,
            boolean isManagedProfile,
            @Nullable @SafetySourceData.SeverityLevel Integer sourceSeverityLevel,
            long openIssuesCount,
            long dismissedIssuesCount) {
        if (!mSafetyCenterConfigReader.allowsStatsdLogging()) {
            return;
        }
        int profileType =
                isManagedProfile
                        ? SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED
                        : SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
        PermissionStatsLog.write(
                SAFETY_SOURCE_STATE_COLLECTED,
                idStringToLong(sourceId),
                profileType,
                toSafetySourceStateCollectedSeverityLevel(sourceSeverityLevel),
                openIssuesCount,
                dismissedIssuesCount,
                // TODO(b/268307189): Implement logging for dismissed issues.
                /* duplicateFilteredOutIssuesCount= */ 0L,
                // TODO(b/268309177): Implement source state logging
                SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_STATE_UNKNOWN,
                // TODO(b/268309211): Record this event when sources provide data
                SAFETY_SOURCE_STATE_COLLECTED__COLLECTION_TYPE__AUTOMATIC,
                // TODO(b/268309213): Log updateType, refreshReason, and dataChanged when sources
                // update their data.
                SAFETY_SOURCE_STATE_COLLECTED__UPDATE_TYPE__UPDATE_TYPE_UNKNOWN,
                /* refreshReason= */ 0L,
                /* dataChanged= */ false,
                // TODO(b/268311158): Implement last updated time logging
                /* lastUpdatedElapsedTimeMillis= */ 0L);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_SYSTEM_EVENT_REPORTED} atom of type {@code
     * SINGLE_SOURCE_RESCAN} or {@code SINGLE_SOURCE_GET_DATA}.
     */
    void writeSourceRefreshSystemEvent(
            @RefreshRequestType int refreshType,
            @NonNull String sourceId,
            @UserIdInt int userId,
            @NonNull Duration duration,
            @SystemEventResult int result) {
        if (!mSafetyCenterConfigReader.allowsStatsdLogging()) {
            return;
        }
        PermissionStatsLog.write(
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED,
                toSourceRefreshEventType(refreshType),
                idStringToLong(sourceId),
                toSystemEventProfileType(userId),
                UNSET_ISSUE_TYPE_ID,
                duration.toMillis(),
                result,
                // TODO(b/268328334): Track refreshReason and dataChanged for system events
                /* refreshReason= */ 0L,
                /* dataChanged= */ false);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_SYSTEM_EVENT_REPORTED} atom of type {@code
     * COMPLETE_RESCAN} or {@code COMPLETE_GET_DATA}.
     */
    void writeWholeRefreshSystemEvent(
            @RefreshRequestType int refreshType,
            @NonNull Duration duration,
            @SystemEventResult int result) {
        if (!mSafetyCenterConfigReader.allowsStatsdLogging()) {
            return;
        }
        PermissionStatsLog.write(
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED,
                toWholeRefreshEventType(refreshType),
                UNSET_SOURCE_ID,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_UNKNOWN,
                UNSET_ISSUE_TYPE_ID,
                duration.toMillis(),
                result,
                // TODO(b/268328334): Track refreshReason and dataChanged for system events
                /* refreshReason= */ 0L,
                /* dataChanged= */ false);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_SYSTEM_EVENT_REPORTED} atom of type {@code
     * INLINE_ACTION}.
     */
    void writeInlineActionSystemEvent(
            @NonNull String sourceId,
            @UserIdInt int userId,
            @Nullable String issueTypeId,
            @NonNull Duration duration,
            @SystemEventResult int result) {
        if (!mSafetyCenterConfigReader.allowsStatsdLogging()) {
            return;
        }
        PermissionStatsLog.write(
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__INLINE_ACTION,
                idStringToLong(sourceId),
                toSystemEventProfileType(userId),
                issueTypeId == null ? UNSET_ISSUE_TYPE_ID : idStringToLong(issueTypeId),
                duration.toMillis(),
                result,
                // Fields aren't relevant for inline action events, but must be written anyway due
                // to the statsd APIs:
                /* refreshReason= */ 0,
                /* dataChanged= */ false);
    }

    /**
     * Returns a {@link SystemEventResult} based on whether the given operation was {@code
     * successful}.
     */
    @SystemEventResult
    static int toSystemEventResult(boolean success) {
        return success
                ? SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__SUCCESS
                : SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__ERROR;
    }

    private static int toSourceRefreshEventType(@RefreshRequestType int refreshType) {
        switch (refreshType) {
            case SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__SINGLE_SOURCE_GET_NEW_DATA;
            case SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__SINGLE_SOURCE_RESCAN;
        }
        Log.w(TAG, "Unexpected SafetyCenterManager.RefreshRequestType: " + refreshType);
        return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_UNKNOWN;
    }

    private static int toWholeRefreshEventType(@RefreshRequestType int refreshType) {
        switch (refreshType) {
            case SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__COMPLETE_GET_NEW_DATA;
            case SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA:
                return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__COMPLETE_RESCAN;
        }
        Log.w(TAG, "Unexpected SafetyCenterManager.RefreshRequestType: " + refreshType);
        return SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_UNKNOWN;
    }

    private int toSystemEventProfileType(@UserIdInt int userId) {
        return UserUtils.isManagedProfile(userId, mContext)
                ? SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED
                : SAFETY_CENTER_SYSTEM_EVENT_REPORTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
    }

    /**
     * Converts a {@link String} ID (e.g. a Safety Source ID) to a {@code long} suitable for logging
     * to statsd.
     */
    private static long idStringToLong(@NonNull String id) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.w(TAG, "Couldn't encode safety source id: " + id, e);
            return 0;
        }
        messageDigest.update(id.getBytes());
        return new BigInteger(messageDigest.digest()).longValue();
    }

    private static int toSafetyStateOverallSeverityLevel(
            @SafetyCenterStatus.OverallSeverityLevel int severityLevel) {
        switch (severityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
                return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
        }
        Log.w(TAG, "Unexpected SafetyCenterStatus.OverallSeverityLevel: " + severityLevel);
        return SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
    }

    private static int toSafetySourceStateCollectedSeverityLevel(
            @Nullable @SafetySourceData.SeverityLevel Integer safetySourceSeverityLevel) {
        if (safetySourceSeverityLevel == null) {
            return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
        }
        switch (safetySourceSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_UNSPECIFIED;
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
        }
        Log.w(TAG, "Unexpected SafetySourceData.SeverityLevel: " + safetySourceSeverityLevel);
        return SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
    }
}
