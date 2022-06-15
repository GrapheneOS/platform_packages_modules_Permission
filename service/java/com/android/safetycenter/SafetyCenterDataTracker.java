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

import static java.util.Collections.emptyList;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.annotation.UserIdInt;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.icu.text.ListFormatter;
import android.icu.util.ULocale;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterEntry;
import android.safetycenter.SafetyCenterEntryGroup;
import android.safetycenter.SafetyCenterEntryOrGroup;
import android.safetycenter.SafetyCenterErrorDetails;
import android.safetycenter.SafetyCenterIssue;
import android.safetycenter.SafetyCenterStaticEntry;
import android.safetycenter.SafetyCenterStaticEntryGroup;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceErrorDetails;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.SafetySourceStatus;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.permission.util.UserUtils;
import com.android.safetycenter.SafetyCenterConfigReader.ExternalSafetySource;
import com.android.safetycenter.internaldata.SafetyCenterEntryGroupId;
import com.android.safetycenter.internaldata.SafetyCenterEntryId;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class that keeps track of all the {@link SafetySourceData} set by safety sources, and
 * aggregates them into a {@link SafetyCenterData} object to be used by PermissionController.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterDataTracker {

    private static final String TAG = "SafetyCenterDataTracker";

    private static final String ANDROID_LOCK_SCREEN_SOURCES_ID = "AndroidLockScreenSources";

    private static final SafetyCenterIssuesBySeverityDescending
            SAFETY_CENTER_ISSUES_BY_SEVERITY_DESCENDING =
                    new SafetyCenterIssuesBySeverityDescending();

    private final ArrayMap<SafetySourceKey, SafetySourceData> mSafetySourceDataForKey =
            new ArrayMap<>();

    // TODO(b/221406600): Add persistent storage for dismissed issues.
    private final ArraySet<SafetyCenterIssueKey> mDismissedSafetyCenterIssueKeys = new ArraySet<>();

    private final ArraySet<SafetyCenterIssueActionId> mSafetyCenterIssueActionsInFlight =
            new ArraySet<>();

    @NonNull private final Context mContext;
    @NonNull private final SafetyCenterResourcesContext mSafetyCenterResourcesContext;
    @NonNull private final SafetyCenterConfigReader mSafetyCenterConfigReader;
    @NonNull private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;

    /**
     * Creates a {@link SafetyCenterDataTracker} using the given {@link Context}, {@link
     * SafetyCenterResourcesContext}, {@link SafetyCenterConfigReader} and {@link
     * SafetyCenterRefreshTracker}.
     */
    SafetyCenterDataTracker(
            @NonNull Context context,
            @NonNull SafetyCenterResourcesContext safetyCenterResourcesContext,
            @NonNull SafetyCenterConfigReader safetyCenterConfigReader,
            @NonNull SafetyCenterRefreshTracker safetyCenterRefreshTracker) {
        mContext = context;
        mSafetyCenterResourcesContext = safetyCenterResourcesContext;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mSafetyCenterRefreshTracker = safetyCenterRefreshTracker;
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
     * SafetySourceData} entry.
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
        boolean safetyCenterDataHasChanged =
                processSafetyEvent(safetySourceId, safetyEvent, userId);

        SafetySourceKey key = SafetySourceKey.of(safetySourceId, userId);
        SafetySourceData existingSafetySourceData = mSafetySourceDataForKey.get(key);
        if (Objects.equals(safetySourceData, existingSafetySourceData)) {
            return safetyCenterDataHasChanged;
        }

        if (safetySourceData == null) {
            mSafetySourceDataForKey.remove(key);
        } else {
            mSafetySourceDataForKey.put(key, safetySourceData);
        }

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
        return mSafetySourceDataForKey.get(SafetySourceKey.of(safetySourceId, userId));
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
        return processSafetyEvent(
                safetySourceId, safetySourceErrorDetails.getSafetyEvent(), userId);
    }

    /**
     * Returns a {@link SafetyCenterErrorDetails} based on a {@link SafetySourceErrorDetails}, if
     * any should be displayed.
     */
    @Nullable
    SafetyCenterErrorDetails getSafetyCenterErrorDetails(
            @NonNull String safetySourceId,
            @NonNull SafetySourceErrorDetails safetySourceErrorDetails) {
        if (!mSafetyCenterConfigReader.isExternalSafetySourceActive(safetySourceId)) {
            return null;
        }
        // TODO(b/229080761): Implement proper error message.
        return new SafetyCenterErrorDetails("Error reported from source: " + safetySourceId);
    }

    /**
     * Returns the current {@link SafetyCenterData} for the given {@link UserProfileGroup},
     * aggregated from all the {@link SafetySourceData} set so far.
     *
     * <p>If a {@link SafetySourceData} was not set, the default value from the {@link
     * SafetyCenterConfig} is used.
     */
    @NonNull
    SafetyCenterData getSafetyCenterData(@NonNull UserProfileGroup userProfileGroup) {
        return getSafetyCenterData(
                mSafetyCenterConfigReader.getSafetySourcesGroups(), userProfileGroup);
    }

    /** Marks the given {@link SafetyCenterIssueActionId} as in-flight. */
    void markSafetyCenterIssueActionAsInFlight(
            @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId) {
        mSafetyCenterIssueActionsInFlight.add(safetyCenterIssueActionId);
    }

    /**
     * Unmarks the given {@link SafetyCenterIssueActionId} as in-flight and returns whether it
     * caused the underlying {@link SafetyCenterData} to change.
     */
    boolean unmarkSafetyCenterIssueActionAsInFlight(
            @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return mSafetyCenterIssueActionsInFlight.remove(safetyCenterIssueActionId)
                && getSafetySourceIssueAction(safetyCenterIssueActionId) != null;
    }

    /** Dismisses the given {@link SafetyCenterIssueId}. */
    void dismissSafetyCenterIssue(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        mDismissedSafetyCenterIssueKeys.add(safetyCenterIssueKey);
    }

    /**
     * Clears all the {@link SafetySourceData}, dismissed {@link SafetyCenterIssueId}, in flight
     * {@link SafetyCenterIssueActionId} and any refresh in progress so far, for all users.
     */
    void clear() {
        mSafetySourceDataForKey.clear();
        mDismissedSafetyCenterIssueKeys.clear();
        mSafetyCenterIssueActionsInFlight.clear();
    }

    /**
     * Returns the {@link SafetySourceIssue} associated with the given {@link SafetyCenterIssueId}.
     *
     * <p>Returns {@code null} if there is no such {@link SafetySourceIssue}, or if it's been
     * dismissed.
     */
    @Nullable
    SafetySourceIssue getSafetySourceIssue(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        if (isDismissed(safetyCenterIssueKey)) {
            return null;
        }

        SafetySourceKey key =
                SafetySourceKey.of(
                        safetyCenterIssueKey.getSafetySourceId(), safetyCenterIssueKey.getUserId());
        SafetySourceData safetySourceData = mSafetySourceDataForKey.get(key);
        if (safetySourceData == null) {
            return null;
        }
        List<SafetySourceIssue> safetySourceIssues = safetySourceData.getIssues();

        for (int i = 0; i < safetySourceIssues.size(); i++) {
            SafetySourceIssue safetySourceIssue = safetySourceIssues.get(i);

            if (safetyCenterIssueKey.getSafetySourceIssueId().equals(safetySourceIssue.getId())) {
                return safetySourceIssue;
            }
        }

        return null;
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

        if (isInFlight(safetyCenterIssueActionId)) {
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

    /**
     * Returns a default {@link SafetyCenterData} object to be returned when the API is disabled.
     */
    @NonNull
    SafetyCenterData getDefaultSafetyCenterData() {
        return new SafetyCenterData(
                new SafetyCenterStatus.Builder(
                                getSafetyCenterStatusTitle(
                                        SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN, false),
                                getSafetyCenterStatusSummary(
                                        SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN, false))
                        .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                        .build(),
                emptyList(),
                emptyList(),
                emptyList());
    }

    private boolean isDismissed(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        return mDismissedSafetyCenterIssueKeys.contains(safetyCenterIssueKey);
    }

    private boolean isInFlight(@NonNull SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return mSafetyCenterIssueActionsInFlight.contains(safetyCenterIssueActionId);
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
        ExternalSafetySource externalSafetySource =
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
            int issueSeverityLevel = safetySourceIssues.get(i).getSeverityLevel();
            if (issueSeverityLevel > safetySource.getMaxSeverityLevel()) {
                throw new IllegalArgumentException(
                        "Unexpected severity level: "
                                + issueSeverityLevel
                                + ", for issue in safety source: "
                                + safetySourceId);
            }
        }

        return mSafetyCenterConfigReader.isExternalSafetySourceActive(safetySourceId);
    }

    private boolean processSafetyEvent(
            @NonNull String safetySourceId,
            @NonNull SafetyEvent safetyEvent,
            @UserIdInt int userId) {
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
                        safetySourceId, refreshBroadcastId, userId);
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
                return unmarkSafetyCenterIssueActionAsInFlight(safetyCenterIssueActionId);
            case SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED:
            case SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_LOCALE_CHANGED:
            case SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED:
                return false;
        }
        Log.w(TAG, "Unexpected SafetyEvent.Type: " + type);
        return false;
    }

    @NonNull
    private SafetyCenterData getSafetyCenterData(
            @NonNull List<SafetySourcesGroup> safetySourcesGroups,
            @NonNull UserProfileGroup userProfileGroup) {
        int safetyCenterOverallSeverityLevel = SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
        int safetyCenterEntriesSeverityLevel = SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
        List<SafetyCenterIssue> safetyCenterIssues = new ArrayList<>();
        List<SafetyCenterEntryOrGroup> safetyCenterEntryOrGroups = new ArrayList<>();
        List<SafetyCenterStaticEntryGroup> safetyCenterStaticEntryGroups = new ArrayList<>();

        for (int i = 0; i < safetySourcesGroups.size(); i++) {
            SafetySourcesGroup safetySourcesGroup = safetySourcesGroups.get(i);

            safetyCenterOverallSeverityLevel =
                    Math.max(
                            safetyCenterOverallSeverityLevel,
                            addSafetyCenterIssues(
                                    safetyCenterIssues, safetySourcesGroup, userProfileGroup));
            int safetySourcesGroupType = safetySourcesGroup.getType();
            switch (safetySourcesGroupType) {
                case SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE:
                    safetyCenterEntriesSeverityLevel =
                            Math.max(
                                    safetyCenterEntriesSeverityLevel,
                                    addSafetyCenterEntryGroup(
                                            safetyCenterEntryOrGroups,
                                            safetySourcesGroup,
                                            userProfileGroup));
                    break;
                case SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_RIGID:
                    addSafetyCenterStaticEntryGroup(
                            safetyCenterStaticEntryGroups, safetySourcesGroup, userProfileGroup);
                    break;
                case SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN:
                    break;
                default:
                    Log.w(TAG, "Unexpected SafetySourceGroupType: " + safetySourcesGroupType);
                    break;
            }
        }

        boolean hasSettingsToReview =
                safetyCenterEntriesSeverityLevel
                        > safetyCenterOverallSeverityLevel;
        safetyCenterIssues.sort(SAFETY_CENTER_ISSUES_BY_SEVERITY_DESCENDING);
        return new SafetyCenterData(
                new SafetyCenterStatus.Builder(
                                getSafetyCenterStatusTitle(
                                        safetyCenterOverallSeverityLevel, hasSettingsToReview),
                                getSafetyCenterStatusSummary(
                                        safetyCenterOverallSeverityLevel, hasSettingsToReview))
                        .setSeverityLevel(safetyCenterOverallSeverityLevel)
                        .setRefreshStatus(mSafetyCenterRefreshTracker.getRefreshStatus())
                        .build(),
                safetyCenterIssues,
                safetyCenterEntryOrGroups,
                safetyCenterStaticEntryGroups);
    }

    @SafetyCenterStatus.OverallSeverityLevel
    private int addSafetyCenterIssues(
            @NonNull List<SafetyCenterIssue> safetyCenterIssues,
            @NonNull SafetySourcesGroup safetySourcesGroup,
            @NonNull UserProfileGroup userProfileGroup) {
        int safetyCenterIssuesOverallSeverityLevel = SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        for (int i = 0; i < safetySources.size(); i++) {
            SafetySource safetySource = safetySources.get(i);

            if (!SafetySources.isExternal(safetySource)) {
                continue;
            }

            safetyCenterIssuesOverallSeverityLevel =
                    Math.max(
                            safetyCenterIssuesOverallSeverityLevel,
                            addSafetyCenterIssues(
                                    safetyCenterIssues,
                                    safetySource,
                                    userProfileGroup.getProfileParentUserId()));

            if (!SafetySources.supportsManagedProfiles(safetySource)) {
                continue;
            }

            int[] managedProfilesUserIds = userProfileGroup.getManagedProfilesUserIds();
            for (int j = 0; j < managedProfilesUserIds.length; j++) {
                int managedProfileUserId = managedProfilesUserIds[j];

                safetyCenterIssuesOverallSeverityLevel =
                        Math.max(
                                safetyCenterIssuesOverallSeverityLevel,
                                addSafetyCenterIssues(
                                        safetyCenterIssues, safetySource, managedProfileUserId));
            }
        }

        return safetyCenterIssuesOverallSeverityLevel;
    }

    @SafetyCenterStatus.OverallSeverityLevel
    private int addSafetyCenterIssues(
            @NonNull List<SafetyCenterIssue> safetyCenterIssues,
            @NonNull SafetySource safetySource,
            @UserIdInt int userId) {
        SafetySourceKey key = SafetySourceKey.of(safetySource.getId(), userId);
        SafetySourceData safetySourceData = mSafetySourceDataForKey.get(key);

        if (safetySourceData == null) {
            return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
        }

        int safetyCenterIssuesOverallSeverityLevel = SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;

        List<SafetySourceIssue> safetySourceIssues = safetySourceData.getIssues();
        for (int i = 0; i < safetySourceIssues.size(); i++) {
            SafetySourceIssue safetySourceIssue = safetySourceIssues.get(i);

            SafetyCenterIssue safetyCenterIssue =
                    toSafetyCenterIssue(safetySourceIssue, safetySource, userId);
            if (safetyCenterIssue == null) {
                continue;
            }
            safetyCenterIssuesOverallSeverityLevel =
                    Math.max(
                            safetyCenterIssuesOverallSeverityLevel,
                            toSafetyCenterStatusOverallSeverityLevel(
                                    safetySourceIssue.getSeverityLevel()));
            safetyCenterIssues.add(safetyCenterIssue);
        }

        return safetyCenterIssuesOverallSeverityLevel;
    }

    @Nullable
    private SafetyCenterIssue toSafetyCenterIssue(
            @NonNull SafetySourceIssue safetySourceIssue,
            @NonNull SafetySource safetySource,
            @UserIdInt int userId) {
        SafetyCenterIssueId safetyCenterIssueId =
                SafetyCenterIssueId.newBuilder()
                        .setSafetyCenterIssueKey(
                                SafetyCenterIssueKey.newBuilder()
                                        .setSafetySourceId(safetySource.getId())
                                        .setSafetySourceIssueId(safetySourceIssue.getId())
                                        .setUserId(userId)
                                        .build())
                        .setIssueTypeId(safetySourceIssue.getIssueTypeId())
                        .build();

        if (isDismissed(safetyCenterIssueId.getSafetyCenterIssueKey())) {
            return null;
        }

        List<SafetySourceIssue.Action> safetySourceIssueActions = safetySourceIssue.getActions();
        List<SafetyCenterIssue.Action> safetyCenterIssueActions =
                new ArrayList<>(safetySourceIssueActions.size());
        for (int i = 0; i < safetySourceIssueActions.size(); i++) {
            SafetySourceIssue.Action safetySourceIssueAction = safetySourceIssueActions.get(i);

            safetyCenterIssueActions.add(
                    toSafetyCenterIssueAction(
                            safetySourceIssueAction,
                            safetyCenterIssueId.getSafetyCenterIssueKey()));
        }

        return new SafetyCenterIssue.Builder(
                        SafetyCenterIds.encodeToString(safetyCenterIssueId),
                        safetySourceIssue.getTitle(),
                        safetySourceIssue.getSummary())
                .setSeverityLevel(
                        toSafetyCenterIssueSeverityLevel(safetySourceIssue.getSeverityLevel()))
                .setSubtitle(safetySourceIssue.getSubtitle())
                .setActions(safetyCenterIssueActions)
                .build();
    }

    @NonNull
    private SafetyCenterIssue.Action toSafetyCenterIssueAction(
            @NonNull SafetySourceIssue.Action safetySourceIssueAction,
            @NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        SafetyCenterIssueActionId safetyCenterIssueActionId =
                SafetyCenterIssueActionId.newBuilder()
                        .setSafetyCenterIssueKey(safetyCenterIssueKey)
                        .setSafetySourceIssueActionId(safetySourceIssueAction.getId())
                        .build();
        return new SafetyCenterIssue.Action.Builder(
                        SafetyCenterIds.encodeToString(safetyCenterIssueActionId),
                        safetySourceIssueAction.getLabel(),
                        safetySourceIssueAction.getPendingIntent())
                .setSuccessMessage(safetySourceIssueAction.getSuccessMessage())
                .setIsInFlight(isInFlight(safetyCenterIssueActionId))
                .setWillResolve(safetySourceIssueAction.willResolve())
                .build();
    }

    @SafetyCenterStatus.OverallSeverityLevel
    private int addSafetyCenterEntryGroup(
            @NonNull List<SafetyCenterEntryOrGroup> safetyCenterEntryOrGroups,
            @NonNull SafetySourcesGroup safetySourcesGroup,
            @NonNull UserProfileGroup userProfileGroup) {
        int groupSafetyCenterEntryLevel = SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;

        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        List<SafetyCenterEntry> entries = new ArrayList<>(safetySources.size());
        for (int i = 0; i < safetySources.size(); i++) {
            SafetySource safetySource = safetySources.get(i);

            groupSafetyCenterEntryLevel =
                    Math.max(
                            groupSafetyCenterEntryLevel,
                            addSafetyCenterEntry(
                                    entries,
                                    safetySource,
                                    false,
                                    userProfileGroup.getProfileParentUserId()));

            if (!SafetySources.supportsManagedProfiles(safetySource)) {
                continue;
            }

            int[] managedProfilesUserIds = userProfileGroup.getManagedProfilesUserIds();
            for (int j = 0; j < managedProfilesUserIds.length; j++) {
                int managedProfileUserId = managedProfilesUserIds[j];

                groupSafetyCenterEntryLevel =
                        Math.max(
                                groupSafetyCenterEntryLevel,
                                addSafetyCenterEntry(
                                        entries, safetySource, true, managedProfileUserId));
            }
        }

        if (entries.size() == 1) {
            safetyCenterEntryOrGroups.add(new SafetyCenterEntryOrGroup(entries.get(0)));
        } else if (entries.size() > 1) {
            SafetyCenterEntryGroupId safetyCenterEntryGroupId =
                    SafetyCenterEntryGroupId.newBuilder()
                            .setSafetySourcesGroupId(safetySourcesGroup.getId())
                            .build();
            CharSequence groupSummary = getOptionalString(safetySourcesGroup.getSummaryResId());
            if (groupSafetyCenterEntryLevel > SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK) {
                for (int i = 0; i < entries.size(); i++) {
                    SafetyCenterEntry entry = entries.get(i);

                    CharSequence entrySummary = entry.getSummary();
                    if (entry.getSeverityLevel() == groupSafetyCenterEntryLevel
                            && entrySummary != null) {
                        groupSummary = entrySummary;
                        break;
                    }
                }
            } else if (safetySourcesGroup.getId().equals(ANDROID_LOCK_SCREEN_SOURCES_ID)
                    && TextUtils.isEmpty(groupSummary)) {
                List<CharSequence> titles = new ArrayList<>();
                for (int i = 0; i < entries.size(); i++) {
                    titles.add(entries.get(i).getTitle());
                }
                groupSummary =
                        ListFormatter.getInstance(
                                        ULocale.getDefault(ULocale.Category.FORMAT),
                                        ListFormatter.Type.UNITS,
                                        ListFormatter.Width.WIDE)
                                .format(titles);
            }
            safetyCenterEntryOrGroups.add(
                    new SafetyCenterEntryOrGroup(
                            new SafetyCenterEntryGroup.Builder(
                                            SafetyCenterIds.encodeToString(
                                                    safetyCenterEntryGroupId),
                                            getString(safetySourcesGroup.getTitleResId()))
                                    .setSeverityLevel(groupSafetyCenterEntryLevel)
                                    .setSummary(groupSummary)
                                    .setEntries(entries)
                                    .setSeverityUnspecifiedIconType(
                                            toGroupSeverityUnspecifiedIconType(
                                                    safetySourcesGroup.getStatelessIconType()))
                                    .build()));
        }
        return entryToSafetyCenterStatusOverallSeverityLevel(groupSafetyCenterEntryLevel);
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private int addSafetyCenterEntry(
            @NonNull List<SafetyCenterEntry> entries,
            @NonNull SafetySource safetySource,
            boolean isUserManaged,
            @UserIdInt int userId) {
        SafetyCenterEntry safetyCenterEntry =
                toSafetyCenterEntry(safetySource, isUserManaged, userId);
        if (safetyCenterEntry == null) {
            return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
        }

        entries.add(safetyCenterEntry);

        return safetyCenterEntry.getSeverityLevel();
    }

    @Nullable
    private SafetyCenterEntry toSafetyCenterEntry(
            @NonNull SafetySource safetySource, boolean isUserManaged, @UserIdInt int userId) {
        switch (safetySource.getType()) {
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return null;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
                SafetySourceKey key = SafetySourceKey.of(safetySource.getId(), userId);
                SafetySourceStatus safetySourceStatus =
                        getSafetySourceStatus(mSafetySourceDataForKey.get(key));
                if (safetySourceStatus != null) {
                    PendingIntent pendingIntent = safetySourceStatus.getPendingIntent();
                    boolean enabled = safetySourceStatus.isEnabled();
                    if (pendingIntent == null) {
                        pendingIntent =
                                toPendingIntent(
                                        safetySource.getIntentAction(),
                                        safetySource.getPackageName(),
                                        userId);
                        enabled = enabled && pendingIntent != null;
                    }
                    SafetyCenterEntryId safetyCenterEntryId =
                            SafetyCenterEntryId.newBuilder()
                                    .setSafetySourceId(safetySource.getId())
                                    .setUserId(userId)
                                    .build();
                    int severityUnspecifiedIconType =
                            SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION;
                    SafetyCenterEntry.Builder builder =
                            new SafetyCenterEntry.Builder(
                                            SafetyCenterIds.encodeToString(safetyCenterEntryId),
                                            safetySourceStatus.getTitle())
                                    .setSeverityLevel(
                                            toSafetyCenterEntrySeverityLevel(
                                                    safetySourceStatus.getSeverityLevel()))
                                    .setSummary(safetySourceStatus.getSummary())
                                    .setEnabled(enabled)
                                    .setSeverityUnspecifiedIconType(severityUnspecifiedIconType)
                                    .setPendingIntent(pendingIntent);
                    SafetySourceStatus.IconAction iconAction = safetySourceStatus.getIconAction();
                    if (iconAction != null) {
                        builder.setIconAction(
                                new SafetyCenterEntry.IconAction(
                                        toSafetyCenterEntryIconActionType(iconAction.getIconType()),
                                        iconAction.getPendingIntent()));
                    }
                    return builder.build();
                }
                return toDefaultSafetyCenterEntry(
                        safetySource,
                        safetySource.getPackageName(),
                        SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN,
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION,
                        isUserManaged,
                        userId);
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
                return toDefaultSafetyCenterEntry(
                        safetySource,
                        null,
                        SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED,
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON,
                        isUserManaged,
                        userId);
        }
        Log.w(
                TAG,
                "Unknown safety source type found in collapsible group: " + safetySource.getType());
        return null;
    }

    @Nullable
    private SafetyCenterEntry toDefaultSafetyCenterEntry(
            @NonNull SafetySource safetySource,
            @Nullable String packageName,
            @SafetyCenterEntry.EntrySeverityLevel int entrySeverityLevel,
            @SafetyCenterEntry.SeverityUnspecifiedIconType int severityUnspecifiedIconType,
            boolean isUserManaged,
            @UserIdInt int userId) {
        if (SafetySources.isDefaultEntryHidden(safetySource)) {
            return null;
        }

        SafetyCenterEntryId safetyCenterEntryId =
                SafetyCenterEntryId.newBuilder()
                        .setSafetySourceId(safetySource.getId())
                        .setUserId(userId)
                        .build();
        PendingIntent pendingIntent =
                toPendingIntent(safetySource.getIntentAction(), packageName, userId);
        boolean enabled =
                pendingIntent != null && !SafetySources.isDefaultEntryDisabled(safetySource);
        CharSequence title =
                getString(
                        isUserManaged
                                ? safetySource.getTitleForWorkResId()
                                : safetySource.getTitleResId());
        return new SafetyCenterEntry.Builder(
                        SafetyCenterIds.encodeToString(safetyCenterEntryId), title)
                .setSeverityLevel(entrySeverityLevel)
                .setSummary(getOptionalString(safetySource.getSummaryResId()))
                .setEnabled(enabled)
                .setPendingIntent(pendingIntent)
                .setSeverityUnspecifiedIconType(severityUnspecifiedIconType)
                .build();
    }

    private void addSafetyCenterStaticEntryGroup(
            @NonNull List<SafetyCenterStaticEntryGroup> safetyCenterStaticEntryGroups,
            @NonNull SafetySourcesGroup safetySourcesGroup,
            @NonNull UserProfileGroup userProfileGroup) {
        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        List<SafetyCenterStaticEntry> staticEntries = new ArrayList<>(safetySources.size());
        for (int i = 0; i < safetySources.size(); i++) {
            SafetySource safetySource = safetySources.get(i);

            addSafetyCenterStaticEntry(
                    staticEntries, safetySource, false, userProfileGroup.getProfileParentUserId());

            if (!SafetySources.supportsManagedProfiles(safetySource)) {
                continue;
            }

            int[] managedProfilesUserIds = userProfileGroup.getManagedProfilesUserIds();
            for (int j = 0; j < managedProfilesUserIds.length; j++) {
                int managedProfileUserId = managedProfilesUserIds[j];

                addSafetyCenterStaticEntry(staticEntries, safetySource, true, managedProfileUserId);
            }
        }

        safetyCenterStaticEntryGroups.add(
                new SafetyCenterStaticEntryGroup(
                        getString(safetySourcesGroup.getTitleResId()), staticEntries));
    }

    private void addSafetyCenterStaticEntry(
            @NonNull List<SafetyCenterStaticEntry> staticEntries,
            @NonNull SafetySource safetySource,
            boolean isUserManaged,
            @UserIdInt int userId) {
        SafetyCenterStaticEntry staticEntry =
                toSafetyCenterStaticEntry(safetySource, isUserManaged, userId);
        if (staticEntry == null) {
            return;
        }
        staticEntries.add(staticEntry);
    }

    @Nullable
    private SafetyCenterStaticEntry toSafetyCenterStaticEntry(
            @NonNull SafetySource safetySource, boolean isUserManaged, @UserIdInt int userId) {
        switch (safetySource.getType()) {
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return null;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
                SafetySourceKey key = SafetySourceKey.of(safetySource.getId(), userId);
                SafetySourceStatus safetySourceStatus =
                        getSafetySourceStatus(mSafetySourceDataForKey.get(key));
                if (safetySourceStatus != null) {
                    PendingIntent pendingIntent = safetySourceStatus.getPendingIntent();
                    if (pendingIntent == null) {
                        // TODO(b/222838784): Decide strategy for static entries when the intent is
                        //  null.
                        return null;
                    }
                    return new SafetyCenterStaticEntry.Builder(safetySourceStatus.getTitle())
                            .setSummary(safetySourceStatus.getSummary())
                            .setPendingIntent(pendingIntent)
                            .build();
                }
                return toDefaultSafetyCenterStaticEntry(
                        safetySource, safetySource.getPackageName(), isUserManaged, userId);
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
                return toDefaultSafetyCenterStaticEntry(safetySource, null, isUserManaged, userId);
        }
        Log.w(TAG, "Unknown safety source type found in rigid group: " + safetySource.getType());
        return null;
    }

    @Nullable
    private SafetyCenterStaticEntry toDefaultSafetyCenterStaticEntry(
            @NonNull SafetySource safetySource,
            @Nullable String packageName,
            boolean isUserManaged,
            @UserIdInt int userId) {
        if (SafetySources.isDefaultEntryHidden(safetySource)) {
            return null;
        }

        PendingIntent pendingIntent =
                toPendingIntent(safetySource.getIntentAction(), packageName, userId);

        if (pendingIntent == null) {
            // TODO(b/222838784): Decide strategy for static entries when the intent is null.
            return null;
        }

        CharSequence title =
                getString(
                        isUserManaged
                                ? safetySource.getTitleForWorkResId()
                                : safetySource.getTitleResId());

        return new SafetyCenterStaticEntry.Builder(title)
                .setSummary(getOptionalString(safetySource.getSummaryResId()))
                .setPendingIntent(pendingIntent)
                .build();
    }

    @Nullable
    private PendingIntent toPendingIntent(
            @Nullable String intentAction, @Nullable String packageName, @UserIdInt int userId) {
        if (intentAction == null) {
            return null;
        }

        Context context = toPackageContextAsUser(packageName, userId);
        if (context == null) {
            return null;
        }

        // TODO(b/222838784): Validate that the intent action is available.

        // This call is required for getIntentSender() to be allowed to send as another package.
        final long identity = Binder.clearCallingIdentity();
        try {
            return PendingIntent.getActivity(
                    context, 0, new Intent(intentAction), PendingIntent.FLAG_IMMUTABLE);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Nullable
    private Context toPackageContextAsUser(@Nullable String packageName, @UserIdInt int userId) {
        String contextPackageName =
                packageName == null
                        // TODO(b/233047525): We should likely use the listener's or caller's
                        // package name here.
                        ? mContext.getPackageManager().getPermissionControllerPackageName()
                        : packageName;
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long identity = Binder.clearCallingIdentity();
        try {
            return mContext.createPackageContextAsUser(
                    contextPackageName, 0, UserHandle.of(userId));
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Package name " + contextPackageName + " not found", e);
            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns a {@link String} resource from the given {@code name}, using the {@link
     * SafetyCenterResourcesContext}.
     *
     * <p>Returns an empty string if the resource cannot be accessed.
     */
    @NonNull
    private String getStringByName(@NonNull String name) {
        String value = mSafetyCenterResourcesContext.getStringByName(name);
        if (value == null) {
            Log.w(TAG, "String resource \"" + name + "\" not found");
            return "";
        }
        return value;
    }

    /**
     * Returns a {@link String} resource from the given {@code stringId}, using the {@link
     * SafetyCenterResourcesContext}.
     *
     * <p>Throws a {@link Resources.NotFoundException} if the resource cannot be accessed.
     */
    @NonNull
    private String getString(@StringRes int stringId) {
        return mSafetyCenterResourcesContext.getString(stringId);
    }

    /**
     * Returns an optional {@link String} resource from the given {@code stringId}, using the {@link
     * SafetyCenterResourcesContext}.
     *
     * <p>Returns {@code null} if {@code stringId} is equal to {@link Resources#ID_NULL}. Otherwise,
     * throws a {@link Resources.NotFoundException} if the resource cannot be accessed.
     */
    @Nullable
    private String getOptionalString(@StringRes int stringId) {
        if (stringId == Resources.ID_NULL) {
            return null;
        }
        return getString(stringId);
    }

    @Nullable
    private static SafetySourceStatus getSafetySourceStatus(
            @Nullable SafetySourceData safetySourceData) {
        if (safetySourceData == null) {
            return null;
        }

        return safetySourceData.getStatus();
    }

    @SafetyCenterStatus.OverallSeverityLevel
    private static int toSafetyCenterStatusOverallSeverityLevel(
            @SafetySourceData.SeverityLevel int safetySourceSeverityLevel) {
        switch (safetySourceSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(TAG, "Unexpected SafetySourceData.SeverityLevel: " + safetySourceSeverityLevel);
        return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
    }

    @SafetyCenterStatus.OverallSeverityLevel
    private static int entryToSafetyCenterStatusOverallSeverityLevel(
            @SafetyCenterEntry.EntrySeverityLevel int safetyCenterEntrySeverityLevel) {
        switch (safetyCenterEntrySeverityLevel) {
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED:
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(
                TAG,
                "Unexpected SafetyCenterEntry.EntrySeverityLevel: "
                        + safetyCenterEntrySeverityLevel);
        return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private static int toSafetyCenterEntrySeverityLevel(
            @SafetySourceData.SeverityLevel int safetySourceSeverityLevel) {
        switch (safetySourceSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED;
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(
                TAG,
                "Unexpected SafetySourceData.SeverityLevel in SafetySourceStatus: "
                        + safetySourceSeverityLevel);
        return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
    }

    @SafetyCenterIssue.IssueSeverityLevel
    private static int toSafetyCenterIssueSeverityLevel(
            @SafetySourceData.SeverityLevel int safetySourceIssueSeverityLevel) {
        switch (safetySourceIssueSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                Log.w(
                        TAG,
                        "Unexpected use of SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED in "
                                + "SafetySourceIssue");
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(
                TAG,
                "Unexpected SafetySourceData.SeverityLevel in SafetySourceIssue: "
                        + safetySourceIssueSeverityLevel);
        return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK;
    }

    @SafetyCenterEntry.SeverityUnspecifiedIconType
    private static int toGroupSeverityUnspecifiedIconType(
            @SafetySourcesGroup.StatelessIconType int statelessIconType) {
        switch (statelessIconType) {
            case SafetySourcesGroup.STATELESS_ICON_TYPE_NONE:
                return SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON;
            case SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY:
                return SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY;
        }

        Log.w(TAG, "Unexpected SafetySourcesGroup.StatelessIconType: " + statelessIconType);
        return SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON;
    }

    @SafetyCenterEntry.IconAction.IconActionType
    private static int toSafetyCenterEntryIconActionType(
            @SafetySourceStatus.IconAction.IconType int safetySourceIconActionType) {
        switch (safetySourceIconActionType) {
            case SafetySourceStatus.IconAction.ICON_TYPE_GEAR:
                return SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR;
            case SafetySourceStatus.IconAction.ICON_TYPE_INFO:
                return SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO;
        }

        Log.w(
                TAG,
                "Unexpected SafetySourceStatus.IconAction.IconActionType: "
                        + safetySourceIconActionType);
        return SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO;
    }

    private String getSafetyCenterStatusTitle(
            @SafetyCenterStatus.OverallSeverityLevel int overallSeverityLevel,
            boolean hasSettingsToReview) {
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
                return getStringByName("overall_severity_level_unknown_title");
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                if (hasSettingsToReview) {
                    return getStringByName("overall_severity_level_ok_review_title");
                }
                return getStringByName("overall_severity_level_ok_title");
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return getStringByName("overall_severity_level_recommendation_title");
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return getStringByName("overall_severity_level_critical_warning_title");
        }

        Log.w(TAG, "Unexpected SafetyCenterStatus.OverallSeverityLevel: " + overallSeverityLevel);
        return "";
    }

    private String getSafetyCenterStatusSummary(
            @SafetyCenterStatus.OverallSeverityLevel int overallSeverityLevel,
            boolean hasSettingsToReview) {
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
                return getStringByName("overall_severity_level_unknown_summary");
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                if (hasSettingsToReview) {
                    return getStringByName("overall_severity_level_ok_review_summary");
                }
                return getStringByName("overall_severity_level_ok_summary");
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return getStringByName("overall_severity_level_recommendation_summary");
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return getStringByName("overall_severity_level_critical_warning_summary");
        }

        Log.w(TAG, "Unexpected SafetyCenterStatus.OverallSeverityLevel: " + overallSeverityLevel);
        return "";
    }

    /** A comparator to order {@link SafetyCenterIssue}s by severity level descending. */
    private static final class SafetyCenterIssuesBySeverityDescending
            implements Comparator<SafetyCenterIssue> {

        SafetyCenterIssuesBySeverityDescending() {}

        @Override
        public int compare(@NonNull SafetyCenterIssue left, @NonNull SafetyCenterIssue right) {
            return Integer.compare(right.getSeverityLevel(), left.getSeverityLevel());
        }
    }
}
