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

import static com.android.safetycenter.StatsdLogger.toSystemEventResult;
import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.os.SystemClock;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceErrorDetails;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.SafetySourceStatus;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.permission.util.UserUtils;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Repository for {@link SafetySourceData} and other data managed by Safety Center including {@link
 * SafetySourceErrorDetails} and metadata about which issue actions are in-flight.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterRepository {

    private static final String TAG = "SafetyCenterRepository";

    private final ArrayMap<SafetySourceKey, SafetySourceData> mSafetySourceDataForKey =
            new ArrayMap<>();

    private final ArraySet<SafetySourceKey> mSafetySourceErrors = new ArraySet<>();

    private final ArrayMap<SafetyCenterIssueActionId, Long> mSafetyCenterIssueActionsInFlight =
            new ArrayMap<>();

    @NonNull private final Context mContext;
    @NonNull private final SafetyCenterConfigReader mSafetyCenterConfigReader;
    @NonNull private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;
    @NonNull private final StatsdLogger mStatsdLogger;
    @NonNull private final SafetyCenterIssueCache mSafetyCenterIssueCache;

    SafetyCenterRepository(
            @NonNull Context context,
            @NonNull SafetyCenterConfigReader safetyCenterConfigReader,
            @NonNull SafetyCenterRefreshTracker safetyCenterRefreshTracker,
            @NonNull StatsdLogger statsdLogger,
            @NonNull SafetyCenterIssueCache safetyCenterIssueCache) {
        mContext = context;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mSafetyCenterRefreshTracker = safetyCenterRefreshTracker;
        mStatsdLogger = statsdLogger;
        mSafetyCenterIssueCache = safetyCenterIssueCache;
    }

    /**
     * Sets the latest {@link SafetySourceData} for the given {@code safetySourceId}, {@link
     * SafetyEvent}, {@code packageName} and {@code userId}, and returns whether there was a change
     * to the underlying {@link SafetyCenterData}.
     *
     * <p>Throws if the request is invalid based on the {@link SafetyCenterConfig}: the given {@code
     * safetySourceId}, {@code packageName} and/or {@code userId} are unexpected; or the {@link
     * SafetySourceData} does not respect all constraints defined in the config.
     *
     * <p>Setting a {@code null} {@link SafetySourceData} evicts the current {@link
     * SafetySourceData} entry and clears the Safety Center issue cache for the source.
     *
     * <p>This method may modify the {@link SafetyCenterIssueCache}.
     */
    boolean setSafetySourceData(
            @Nullable SafetySourceData safetySourceData,
            @NonNull String safetySourceId,
            @NonNull SafetyEvent safetyEvent,
            @NonNull String packageName,
            @UserIdInt int userId) {
        if (!validateRequest(safetySourceData, safetySourceId, packageName, userId)) {
            return false;
        }
        boolean safetyEventChangedSafetyCenterData =
                processSafetyEvent(safetySourceId, safetyEvent, userId, false);

        SafetySourceKey key = SafetySourceKey.of(safetySourceId, userId);
        boolean removingSafetySourceErrorChangedSafetyCenterData = mSafetySourceErrors.remove(key);
        SafetySourceData existingSafetySourceData = mSafetySourceDataForKey.get(key);
        if (Objects.equals(safetySourceData, existingSafetySourceData)) {
            return safetyEventChangedSafetyCenterData
                    || removingSafetySourceErrorChangedSafetyCenterData;
        }

        ArraySet<String> issueIds = new ArraySet<>();
        if (safetySourceData == null) {
            mSafetySourceDataForKey.remove(key);
        } else {
            mSafetySourceDataForKey.put(key, safetySourceData);
            for (int i = 0; i < safetySourceData.getIssues().size(); i++) {
                issueIds.add(safetySourceData.getIssues().get(i).getId());
            }
        }
        mSafetyCenterIssueCache.updateIssuesForSource(issueIds, safetySourceId, userId);

        return true;
    }

    /**
     * Returns the latest {@link SafetySourceData} that was set by {@link #setSafetySourceData} for
     * the given {@code safetySourceId}, {@code packageName} and {@code userId}.
     *
     * <p>Throws if the request is invalid based on the {@link SafetyCenterConfig}: the given {@code
     * safetySourceId}, {@code packageName} and/or {@code userId} are unexpected.
     *
     * <p>Returns {@code null} if it was never set since boot, or if the entry was evicted using
     * {@link #setSafetySourceData} with a {@code null} value.
     */
    @Nullable
    SafetySourceData getSafetySourceData(
            @NonNull String safetySourceId, @NonNull String packageName, @UserIdInt int userId) {
        if (!validateRequest(null, safetySourceId, packageName, userId)) {
            return null;
        }
        return getSafetySourceData(SafetySourceKey.of(safetySourceId, userId));
    }

    /**
     * Returns the latest {@link SafetySourceData} that was set by {@link #setSafetySourceData} for
     * the given {@link SafetySourceKey}.
     *
     * <p>This method does not perform any validation, {@link #getSafetySourceData(String, String,
     * int)} should be called wherever validation is required.
     */
    @Nullable
    SafetySourceData getSafetySourceData(@NonNull SafetySourceKey safetySourceKey) {
        return mSafetySourceDataForKey.get(safetySourceKey);
    }

    /**
     * Reports the given {@link SafetySourceErrorDetails} for the given {@code safetySourceId} and
     * {@code userId}, and returns whether there was a change to the underlying {@link
     * SafetyCenterData}.
     *
     * <p>Throws if the request is invalid based on the {@link SafetyCenterConfig}: the given {@code
     * safetySourceId}, {@code packageName} and/or {@code userId} are unexpected.
     */
    boolean reportSafetySourceError(
            @NonNull SafetySourceErrorDetails safetySourceErrorDetails,
            @NonNull String safetySourceId,
            @NonNull String packageName,
            @UserIdInt int userId) {
        if (!validateRequest(null, safetySourceId, packageName, userId)) {
            return false;
        }
        SafetyEvent safetyEvent = safetySourceErrorDetails.getSafetyEvent();
        Log.w(TAG, "Error reported from source: " + safetySourceId + ", for event: " + safetyEvent);

        boolean safetyEventChangedSafetyCenterData =
                processSafetyEvent(safetySourceId, safetyEvent, userId, true);
        int safetyEventType = safetyEvent.getType();
        if (safetyEventType == SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED
                || safetyEventType == SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED) {
            return safetyEventChangedSafetyCenterData;
        }

        SafetySourceKey key = SafetySourceKey.of(safetySourceId, userId);
        boolean safetySourceErrorChangedSafetyCenterData = setSafetySourceError(key);
        return safetyEventChangedSafetyCenterData || safetySourceErrorChangedSafetyCenterData;
    }

    /** Marks the given {@link SafetySourceKey} as having errored-out. */
    boolean setSafetySourceError(@NonNull SafetySourceKey safetySourceKey) {
        boolean removingSafetySourceDataChangedSafetyCenterData =
                mSafetySourceDataForKey.remove(safetySourceKey) != null;
        boolean addingSafetySourceErrorChangedSafetyCenterData =
                mSafetySourceErrors.add(safetySourceKey);
        return removingSafetySourceDataChangedSafetyCenterData
                || addingSafetySourceErrorChangedSafetyCenterData;
    }

    /**
     * Clears all safety source errors received so far for the given {@link UserProfileGroup}, this
     * is useful e.g. when starting a new broadcast.
     */
    void clearSafetySourceErrors(@NonNull UserProfileGroup userProfileGroup) {
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetySourceErrors.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceErrors.valueAt(i);
            if (userProfileGroup.contains(sourceKey.getUserId())) {
                mSafetySourceErrors.removeAt(i);
            }
        }
    }

    /** Marks the given {@link SafetyCenterIssueActionId} as in-flight. */
    void markSafetyCenterIssueActionInFlight(
            @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId) {
        mSafetyCenterIssueActionsInFlight.put(
                safetyCenterIssueActionId, SystemClock.elapsedRealtime());
    }

    /**
     * Unmarks the given {@link SafetyCenterIssueActionId} as in-flight, logs that event to statsd
     * with the given {@code result} value, and returns {@code true} if the underlying {@link
     * SafetyCenterData} changed.
     */
    boolean unmarkSafetyCenterIssueActionInFlight(
            @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId,
            @StatsdLogger.SystemEventResult int result) {
        Long startElapsedMillis =
                mSafetyCenterIssueActionsInFlight.remove(safetyCenterIssueActionId);
        if (startElapsedMillis == null) {
            Log.w(
                    TAG,
                    "Attempt to unmark unknown in-flight action: "
                            + toUserFriendlyString(safetyCenterIssueActionId));
            return false;
        }

        SafetyCenterIssueKey issueKey = safetyCenterIssueActionId.getSafetyCenterIssueKey();
        SafetySourceIssue issue = getSafetySourceIssue(issueKey);
        String issueTypeId = issue == null ? null : issue.getIssueTypeId();
        Duration duration = Duration.ofMillis(SystemClock.elapsedRealtime() - startElapsedMillis);

        mStatsdLogger.writeInlineActionSystemEvent(
                issueKey.getSafetySourceId(), issueKey.getUserId(), issueTypeId, duration, result);

        if (issue == null || getSafetySourceIssueAction(safetyCenterIssueActionId) == null) {
            Log.w(
                    TAG,
                    "Attempt to unmark in-flight action for a non-existent issue or action: "
                            + toUserFriendlyString(safetyCenterIssueActionId));
            return false;
        }

        return true;
    }

    /**
     * Dismisses the given {@link SafetyCenterIssueKey}.
     *
     * <p>This method may modify the {@link SafetyCenterIssueCache}.
     */
    void dismissSafetyCenterIssue(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        mSafetyCenterIssueCache.dismissIssue(safetyCenterIssueKey);
    }

    /**
     * Returns the {@link SafetySourceIssue} associated with the given {@link SafetyCenterIssueKey}.
     *
     * <p>Returns {@code null} if there is no such {@link SafetySourceIssue}, or if it's been
     * dismissed.
     */
    @Nullable
    SafetySourceIssue getSafetySourceIssue(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        SafetySourceKey key =
                SafetySourceKey.of(
                        safetyCenterIssueKey.getSafetySourceId(), safetyCenterIssueKey.getUserId());
        SafetySourceData safetySourceData = mSafetySourceDataForKey.get(key);
        if (safetySourceData == null) {
            return null;
        }
        List<SafetySourceIssue> safetySourceIssues = safetySourceData.getIssues();

        SafetySourceIssue targetIssue = null;
        for (int i = 0; i < safetySourceIssues.size(); i++) {
            SafetySourceIssue safetySourceIssue = safetySourceIssues.get(i);

            if (safetyCenterIssueKey.getSafetySourceIssueId().equals(safetySourceIssue.getId())) {
                targetIssue = safetySourceIssue;
                break;
            }
        }
        if (targetIssue == null) {
            return null;
        }

        if (mSafetyCenterIssueCache.isIssueDismissed(
                safetyCenterIssueKey, targetIssue.getSeverityLevel())) {
            return null;
        }

        return targetIssue;
    }

    /**
     * Returns the {@link SafetySourceIssue.Action} associated with the given {@link
     * SafetyCenterIssueActionId}.
     *
     * <p>Returns {@code null} if there is no associated {@link SafetySourceIssue}, or if it's been
     * dismissed.
     *
     * <p>Returns {@code null} if the {@link SafetySourceIssue.Action} is currently in flight.
     */
    @Nullable
    SafetySourceIssue.Action getSafetySourceIssueAction(
            @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId) {
        SafetySourceIssue safetySourceIssue =
                getSafetySourceIssue(safetyCenterIssueActionId.getSafetyCenterIssueKey());

        if (safetySourceIssue == null) {
            return null;
        }

        if (actionIsInFlight(safetyCenterIssueActionId)) {
            return null;
        }

        List<SafetySourceIssue.Action> safetySourceIssueActions = safetySourceIssue.getActions();
        for (int i = 0; i < safetySourceIssueActions.size(); i++) {
            SafetySourceIssue.Action safetySourceIssueAction = safetySourceIssueActions.get(i);

            if (safetyCenterIssueActionId
                    .getSafetySourceIssueActionId()
                    .equals(safetySourceIssueAction.getId())) {
                return safetySourceIssueAction;
            }
        }

        return null;
    }

    /** Clears all {@link SafetySourceData}, errors, issues and in flight actions for all users. */
    void clear() {
        mSafetySourceDataForKey.clear();
        mSafetySourceErrors.clear();
        mSafetyCenterIssueActionsInFlight.clear();
    }

    /**
     * Clears all {@link SafetySourceData}, errors, issues and in flight actions, for the given
     * user.
     */
    void clearForUser(@UserIdInt int userId) {
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetySourceDataForKey.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceDataForKey.keyAt(i);
            if (sourceKey.getUserId() == userId) {
                mSafetySourceDataForKey.removeAt(i);
            }
        }
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetySourceErrors.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceErrors.valueAt(i);
            if (sourceKey.getUserId() == userId) {
                mSafetySourceErrors.removeAt(i);
            }
        }
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetyCenterIssueActionsInFlight.size() - 1; i >= 0; i--) {
            SafetyCenterIssueActionId issueActionId = mSafetyCenterIssueActionsInFlight.keyAt(i);
            if (issueActionId.getSafetyCenterIssueKey().getUserId() == userId) {
                mSafetyCenterIssueActionsInFlight.removeAt(i);
            }
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(@NonNull PrintWriter fout) {
        int dataCount = mSafetySourceDataForKey.size();
        fout.println("SOURCE DATA (" + dataCount + ")");
        for (int i = 0; i < dataCount; i++) {
            SafetySourceKey key = mSafetySourceDataForKey.keyAt(i);
            SafetySourceData data = mSafetySourceDataForKey.valueAt(i);
            fout.println("\t[" + i + "] " + key + " -> " + data);
        }
        fout.println();

        int errorCount = mSafetySourceErrors.size();
        fout.println("SOURCE ERRORS (" + errorCount + ")");
        for (int i = 0; i < errorCount; i++) {
            SafetySourceKey key = mSafetySourceErrors.valueAt(i);
            fout.println("\t[" + i + "] " + key);
        }
        fout.println();

        int actionInFlightCount = mSafetyCenterIssueActionsInFlight.size();
        fout.println("ACTIONS IN FLIGHT (" + actionInFlightCount + ")");
        for (int i = 0; i < actionInFlightCount; i++) {
            String printableId = toUserFriendlyString(mSafetyCenterIssueActionsInFlight.keyAt(i));
            long startElapsedMillis = mSafetyCenterIssueActionsInFlight.valueAt(i);
            long durationMillis = SystemClock.elapsedRealtime() - startElapsedMillis;
            fout.println("\t[" + i + "] " + printableId + "(duration=" + durationMillis + "ms)");
        }
        fout.println();
    }

    /** Returns {@code true} if the given issue action is in flight. */
    boolean actionIsInFlight(@NonNull SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return mSafetyCenterIssueActionsInFlight.containsKey(safetyCenterIssueActionId);
    }

    /** Returns {@code true} if the given source has an error. */
    boolean sourceHasError(@NonNull SafetySourceKey safetySourceKey) {
        return mSafetySourceErrors.contains(safetySourceKey);
    }

    /**
     * Checks if a request to the SafetyCenter is valid, and returns whether the request should be
     * processed.
     */
    private boolean validateRequest(
            @Nullable SafetySourceData safetySourceData,
            @NonNull String safetySourceId,
            @NonNull String packageName,
            @UserIdInt int userId) {
        SafetyCenterConfigReader.ExternalSafetySource externalSafetySource =
                mSafetyCenterConfigReader.getExternalSafetySource(safetySourceId);
        if (externalSafetySource == null) {
            throw new IllegalArgumentException("Unexpected safety source: " + safetySourceId);
        }

        SafetySource safetySource = externalSafetySource.getSafetySource();

        // TODO(b/222330089): Security: check certs?
        if (!packageName.equals(safetySource.getPackageName())) {
            throw new IllegalArgumentException(
                    "Unexpected package name: "
                            + packageName
                            + ", for safety source: "
                            + safetySourceId);
        }

        // TODO(b/222327845): Security: check package is installed for user?

        if (UserUtils.isManagedProfile(userId, mContext)
                && !SafetySources.supportsManagedProfiles(safetySource)) {
            throw new IllegalArgumentException(
                    "Unexpected managed profile request for safety source: " + safetySourceId);
        }

        boolean retrievingOrClearingData = safetySourceData == null;
        if (retrievingOrClearingData) {
            return mSafetyCenterConfigReader.isExternalSafetySourceActive(safetySourceId);
        }

        SafetySourceStatus safetySourceStatus = safetySourceData.getStatus();

        if (safetySource.getType() == SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY
                && safetySourceStatus != null) {
            throw new IllegalArgumentException(
                    "Unexpected status for issue only safety source: " + safetySourceId);
        }

        if (safetySource.getType() == SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC
                && safetySourceStatus == null) {
            throw new IllegalArgumentException(
                    "Missing status for dynamic safety source: " + safetySourceId);
        }

        if (safetySourceStatus != null) {
            int sourceSeverityLevel = safetySourceStatus.getSeverityLevel();

            if (externalSafetySource.hasEntryInRigidGroup()
                    && sourceSeverityLevel != SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED) {
                throw new IllegalArgumentException(
                        "Safety source: "
                                + safetySourceId
                                + " is in a rigid group but specified a severity level: "
                                + sourceSeverityLevel);
            }

            int maxSourceSeverityLevel =
                    Math.max(
                            SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                            safetySource.getMaxSeverityLevel());

            if (sourceSeverityLevel > maxSourceSeverityLevel) {
                throw new IllegalArgumentException(
                        "Unexpected severity level: "
                                + sourceSeverityLevel
                                + ", for safety source: "
                                + safetySourceId);
            }
        }

        List<SafetySourceIssue> safetySourceIssues = safetySourceData.getIssues();

        for (int i = 0; i < safetySourceIssues.size(); i++) {
            SafetySourceIssue safetySourceIssue = safetySourceIssues.get(i);
            int issueSeverityLevel = safetySourceIssue.getSeverityLevel();
            if (issueSeverityLevel > safetySource.getMaxSeverityLevel()) {
                throw new IllegalArgumentException(
                        "Unexpected severity level: "
                                + issueSeverityLevel
                                + ", for issue in safety source: "
                                + safetySourceId);
            }

            int issueCategory = safetySourceIssue.getIssueCategory();
            if (!SafetyCenterFlags.isIssueCategoryAllowedForSource(issueCategory, safetySourceId)) {
                throw new IllegalArgumentException(
                        "Unexpected issue category: "
                                + issueCategory
                                + ", for issue in safety source: "
                                + safetySourceId);
            }
        }

        return mSafetyCenterConfigReader.isExternalSafetySourceActive(safetySourceId);
    }

    private boolean processSafetyEvent(
            @NonNull String safetySourceId,
            @NonNull SafetyEvent safetyEvent,
            @UserIdInt int userId,
            boolean isError) {
        int type = safetyEvent.getType();
        switch (type) {
            case SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED:
                String refreshBroadcastId = safetyEvent.getRefreshBroadcastId();
                if (refreshBroadcastId == null) {
                    Log.w(
                            TAG,
                            "Received safety event of type "
                                    + safetyEvent.getType()
                                    + " without a refresh broadcast id");
                    return false;
                }
                return mSafetyCenterRefreshTracker.reportSourceRefreshCompleted(
                        refreshBroadcastId, safetySourceId, userId, !isError);
            case SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED:
            case SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED:
                String safetySourceIssueId = safetyEvent.getSafetySourceIssueId();
                if (safetySourceIssueId == null) {
                    Log.w(
                            TAG,
                            "Received safety event of type "
                                    + safetyEvent.getType()
                                    + " without a safety source issue id");
                    return false;
                }
                String safetySourceIssueActionId = safetyEvent.getSafetySourceIssueActionId();
                if (safetySourceIssueActionId == null) {
                    Log.w(
                            TAG,
                            "Received safety event of type "
                                    + safetyEvent.getType()
                                    + " without a safety source issue action id");
                    return false;
                }
                SafetyCenterIssueKey safetyCenterIssueKey =
                        SafetyCenterIssueKey.newBuilder()
                                .setSafetySourceId(safetySourceId)
                                .setSafetySourceIssueId(safetySourceIssueId)
                                .setUserId(userId)
                                .build();
                SafetyCenterIssueActionId safetyCenterIssueActionId =
                        SafetyCenterIssueActionId.newBuilder()
                                .setSafetyCenterIssueKey(safetyCenterIssueKey)
                                .setSafetySourceIssueActionId(safetySourceIssueActionId)
                                .build();
                boolean success = type == SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED;
                int result = toSystemEventResult(success);
                return unmarkSafetyCenterIssueActionInFlight(safetyCenterIssueActionId, result);
            case SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED:
            case SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_LOCALE_CHANGED:
            case SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED:
                return false;
        }
        Log.w(TAG, "Unexpected SafetyEvent.Type: " + type);
        return false;
    }
}
