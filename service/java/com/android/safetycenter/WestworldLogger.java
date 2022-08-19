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
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__INLINE_ACTION;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__ERROR;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__SUCCESS;
import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SEVERITY_LEVEL__SAFETY_SEVERITY_UNSPECIFIED;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_CRITICAL_WARNING;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_LEVEL_UNKNOWN;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_OK;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE__OVERALL_SEVERITY_LEVEL__SAFETY_SEVERITY_RECOMMENDATION;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetySourceData;
import android.util.Log;
import android.util.StatsEvent;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.permission.PermissionStatsLog;
import com.android.permission.util.UserUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * Marshalls and writes atoms to Westworld. Contains implementation details of how atom parameters
 * are encoded and provides a better-typed interface for other classes to call.
 */
@RequiresApi(TIRAMISU)
final class WestworldLogger {

    private static final String TAG = "WestworldLogger";

    @IntDef(
            prefix = {"SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__"},
            value = {
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__SUCCESS,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__ERROR,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SystemEventResult {}

    @NonNull private final Context mContext;

    WestworldLogger(@NonNull Context context) {
        mContext = context;
    }

    /** Constructs a new {@link PermissionStatsLog#SAFETY_STATE} {@link StatsEvent}. */
    public static StatsEvent newSafetyStateEvent(
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
    public void writeSafetySourceStateCollected(
            @NonNull String sourceId,
            boolean isManagedProfile,
            @Nullable @SafetySourceData.SeverityLevel Integer sourceSeverityLevel,
            long openIssuesCount,
            long dismissedIssuesCount) {
        PermissionStatsLog.write(
                SAFETY_SOURCE_STATE_COLLECTED,
                idStringToLong(sourceId),
                getProfileType(isManagedProfile),
                toSafetySourceStateCollectedSeverityLevel(sourceSeverityLevel),
                openIssuesCount,
                dismissedIssuesCount);
    }

    /**
     * Writes a {@link PermissionStatsLog#SAFETY_CENTER_SYSTEM_EVENT_REPORTED} atom of type {@code
     * INLINE_ACTION}.
     */
    public void writeInlineActionSystemEvent(
            @NonNull String sourceId,
            @UserIdInt int userId,
            @NonNull String issueTypeId,
            @NonNull Duration duration,
            @SystemEventResult int result) {
        PermissionStatsLog.write(
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED,
                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__EVENT_TYPE__INLINE_ACTION,
                idStringToLong(sourceId),
                userIdToProfileType(userId),
                idStringToLong(issueTypeId),
                duration.toMillis(),
                result);
    }

    private int userIdToProfileType(@UserIdInt int userId) {
        return getProfileType(UserUtils.isManagedProfile(userId, mContext));
    }

    private static int getProfileType(boolean isManaged) {
        return isManaged
                ? SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_MANAGED
                : SAFETY_SOURCE_STATE_COLLECTED__SAFETY_SOURCE_PROFILE_TYPE__PROFILE_TYPE_PERSONAL;
    }

    /**
     * Converts a {@link String} ID (e.g. a Safety Source ID) to a {@code long} suitable for logging
     * to Westworld.
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
