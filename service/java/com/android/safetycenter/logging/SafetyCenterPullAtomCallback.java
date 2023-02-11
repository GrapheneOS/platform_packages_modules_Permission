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

package com.android.safetycenter.logging;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.permission.PermissionStatsLog.SAFETY_STATE;

import static java.util.Collections.emptyList;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.StatsManager;
import android.app.StatsManager.StatsPullAtomCallback;
import android.content.Context;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.SafetySourceStatus;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.Log;
import android.util.StatsEvent;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.build.SdkLevel;
import com.android.permission.PermissionStatsLog;
import com.android.safetycenter.ApiLock;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetyCenterDataFactory;
import com.android.safetycenter.SafetyCenterFlags;
import com.android.safetycenter.SafetySourceKey;
import com.android.safetycenter.SafetySources;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.data.SafetyCenterDataManager;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import java.util.List;

/**
 * A {@link StatsPullAtomCallback} that provides a {@link PermissionStatsLog#SAFETY_STATE} atom that
 * when requested by the {@link StatsManager}.
 *
 * <p>Whenever that atom, which describes the overall Safety Center, is pulled this class also
 * separately writes one {@code SAFETY_SOURCE_STATE_COLLECTED} atom for each active source (per
 * profile).
 *
 * @hide
 */
@RequiresApi(TIRAMISU)
public final class SafetyCenterPullAtomCallback implements StatsPullAtomCallback {

    private static final String TAG = "SafetyCenterPullAtom";

    @NonNull private final Context mContext;
    @NonNull private final ApiLock mApiLock;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterStatsdLogger mSafetyCenterStatsdLogger;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterDataFactory mSafetyCenterDataFactory;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterDataManager mSafetyCenterDataManager;

    public SafetyCenterPullAtomCallback(
            @NonNull Context context,
            @NonNull ApiLock apiLock,
            @NonNull SafetyCenterStatsdLogger safetyCenterStatsdLogger,
            @NonNull SafetyCenterConfigReader safetyCenterConfigReader,
            @NonNull SafetyCenterDataFactory safetyCenterDataFactory,
            @NonNull SafetyCenterDataManager safetyCenterDataManager) {
        mContext = context;
        mApiLock = apiLock;
        mSafetyCenterStatsdLogger = safetyCenterStatsdLogger;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mSafetyCenterDataFactory = safetyCenterDataFactory;
        mSafetyCenterDataManager = safetyCenterDataManager;
    }

    @Override
    public int onPullAtom(int atomTag, @NonNull List<StatsEvent> statsEvents) {
        if (atomTag != SAFETY_STATE) {
            Log.w(TAG, "Attempt to pull atom: " + atomTag + ", but only SAFETY_STATE is supported");
            return StatsManager.PULL_SKIP;
        }
        if (!SafetyCenterFlags.getSafetyCenterEnabled()) {
            Log.w(TAG, "Attempt to pull SAFETY_STATE, but Safety Center is disabled");
            return StatsManager.PULL_SKIP;
        }
        List<UserProfileGroup> userProfileGroups =
                UserProfileGroup.getAllUserProfileGroups(mContext);
        synchronized (mApiLock) {
            if (!mSafetyCenterConfigReader.allowsStatsdLogging()) {
                Log.w(TAG, "Skipping pulling and writing atoms due to a test config override");
                return StatsManager.PULL_SKIP;
            }
            Log.i(TAG, "Pulling and writing atoms…");
            for (int i = 0; i < userProfileGroups.size(); i++) {
                UserProfileGroup userProfileGroup = userProfileGroups.get(i);
                List<SafetySourcesGroup> loggableGroups =
                        mSafetyCenterConfigReader.getLoggableSafetySourcesGroups();
                statsEvents.add(
                        createOverallSafetyStateAtomLocked(userProfileGroup, loggableGroups));
                // The SAFETY_SOURCE_STATE_COLLECTED atoms are written instead of being pulled,
                // they do not support pull but we want to collect them at the same time as
                // the above pulled atom.
                writeSafetySourceStateCollectedAtomsLocked(userProfileGroup, loggableGroups);
            }
        }
        return StatsManager.PULL_SUCCESS;
    }

    @GuardedBy("mApiLock")
    @NonNull
    private StatsEvent createOverallSafetyStateAtomLocked(
            @NonNull UserProfileGroup userProfileGroup,
            @NonNull List<SafetySourcesGroup> loggableGroups) {
        SafetyCenterData loggableData =
                mSafetyCenterDataFactory.assembleSafetyCenterData(
                        "android", userProfileGroup, loggableGroups);
        long openIssuesCount = loggableData.getIssues().size();
        long dismissedIssuesCount = getDismissedIssuesCountLocked(loggableData, userProfileGroup);

        return mSafetyCenterStatsdLogger.createSafetyStateEvent(
                loggableData.getStatus().getSeverityLevel(), openIssuesCount, dismissedIssuesCount);
    }

    @GuardedBy("mApiLock")
    private long getDismissedIssuesCountLocked(
            @NonNull SafetyCenterData loggableData, @NonNull UserProfileGroup userProfileGroup) {
        if (SdkLevel.isAtLeastU()) {
            return loggableData.getDismissedIssues().size();
        }
        long openIssuesCount = loggableData.getIssues().size();
        return mSafetyCenterDataManager.countLoggableIssuesFor(userProfileGroup) - openIssuesCount;
    }

    @GuardedBy("mApiLock")
    private void writeSafetySourceStateCollectedAtomsLocked(
            @NonNull UserProfileGroup userProfileGroup,
            @NonNull List<SafetySourcesGroup> loggableGroups) {
        for (int i = 0; i < loggableGroups.size(); i++) {
            List<SafetySource> loggableSources = loggableGroups.get(i).getSafetySources();

            for (int j = 0; j < loggableSources.size(); j++) {
                SafetySource loggableSource = loggableSources.get(j);

                if (!SafetySources.isExternal(loggableSource)) {
                    continue;
                }

                writeSafetySourceStateCollectedAtomLocked(
                        loggableSource,
                        userProfileGroup.getProfileParentUserId(),
                        /* isUserManaged= */ false);

                if (!SafetySources.supportsManagedProfiles(loggableSource)) {
                    continue;
                }

                int[] managedRunningProfilesUserIds =
                        userProfileGroup.getManagedRunningProfilesUserIds();
                for (int k = 0; k < managedRunningProfilesUserIds.length; k++) {
                    writeSafetySourceStateCollectedAtomLocked(
                            loggableSource,
                            managedRunningProfilesUserIds[k],
                            /* isUserManaged= */ true);
                }
            }
        }
    }

    @GuardedBy("mApiLock")
    private void writeSafetySourceStateCollectedAtomLocked(
            @NonNull SafetySource safetySource, @UserIdInt int userId, boolean isUserManaged) {
        SafetySourceKey key = SafetySourceKey.of(safetySource.getId(), userId);
        SafetySourceData safetySourceData =
                mSafetyCenterDataManager.getSafetySourceDataInternal(key);
        SafetySourceStatus safetySourceStatus =
                safetySourceData == null ? null : safetySourceData.getStatus();
        List<SafetySourceIssue> safetySourceIssues =
                safetySourceData == null ? emptyList() : safetySourceData.getIssues();
        boolean isIssueOnlyAndHasData =
                SafetySources.isIssueOnly(safetySource) && safetySourceData != null;
        int maxSeverityLevel =
                isIssueOnlyAndHasData
                        ? SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
                        : Integer.MIN_VALUE;
        long openIssuesCount = 0;
        long dismissedIssuesCount = 0;
        for (int i = 0; i < safetySourceIssues.size(); i++) {
            SafetySourceIssue safetySourceIssue = safetySourceIssues.get(i);
            SafetyCenterIssueKey safetyCenterIssueKey =
                    SafetyCenterIssueKey.newBuilder()
                            .setSafetySourceId(safetySource.getId())
                            .setSafetySourceIssueId(safetySourceIssue.getId())
                            .setUserId(userId)
                            .build();

            if (mSafetyCenterDataManager.isIssueDismissed(
                    safetyCenterIssueKey, safetySourceIssue.getSeverityLevel())) {
                dismissedIssuesCount++;
            } else {
                openIssuesCount++;
                maxSeverityLevel = Math.max(maxSeverityLevel, safetySourceIssue.getSeverityLevel());
            }
        }
        if (safetySourceStatus != null) {
            maxSeverityLevel = Math.max(maxSeverityLevel, safetySourceStatus.getSeverityLevel());
        }
        Integer maxSeverityOrNull = maxSeverityLevel > Integer.MIN_VALUE ? maxSeverityLevel : null;

        mSafetyCenterStatsdLogger.writeSafetySourceStateCollected(
                safetySource.getId(),
                isUserManaged,
                maxSeverityOrNull,
                openIssuesCount,
                dismissedIssuesCount);
    }
}
