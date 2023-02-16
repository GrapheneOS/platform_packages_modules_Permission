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

package com.android.safetycenter.data;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.permission.util.UserUtils;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetySourceIssueInfo;
import com.android.safetycenter.SafetySourceKey;
import com.android.safetycenter.SafetySources;
import com.android.safetycenter.UserProfileGroup;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Contains issue related data.
 *
 * <p>Responsible for generating lists of issues and deduplication of issues.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterIssueRepository {

    private static final SafetySourceIssuesInfoBySeverityDescending
            SAFETY_SOURCE_ISSUES_INFO_BY_SEVERITY_DESCENDING =
                    new SafetySourceIssuesInfoBySeverityDescending();

    private final Context mContext;
    private final SafetySourceDataRepository mSafetySourceDataRepository;
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    // Only available on Android U+.
    @Nullable private final SafetyCenterIssueDeduplicator mSafetyCenterIssueDeduplicator;

    // userId -> sorted and deduplicated list of issues
    private final SparseArray<List<SafetySourceIssueInfo>> mUserIdToIssuesInfo =
            new SparseArray<>();

    SafetyCenterIssueRepository(
            Context context,
            SafetySourceDataRepository safetySourceDataRepository,
            SafetyCenterConfigReader safetyCenterConfigReader,
            @Nullable SafetyCenterIssueDeduplicator safetyCenterIssueDeduplicator) {
        mContext = context;
        mSafetySourceDataRepository = safetySourceDataRepository;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mSafetyCenterIssueDeduplicator = safetyCenterIssueDeduplicator;
    }

    /**
     * Updates the class as per the current state of issues. Should be called after any state update
     * that can affect issues.
     */
    void updateIssues(UserProfileGroup userProfileGroup) {
        updateIssues(userProfileGroup.getProfileParentUserId(), /* isManagedProfile= */ false);

        int[] managedProfileUserIds = userProfileGroup.getManagedProfilesUserIds();
        for (int i = 0; i < managedProfileUserIds.length; i++) {
            updateIssues(managedProfileUserIds[i], /* isManagedProfile= */ true);
        }
    }

    /**
     * Updates the class as per the current state of issues. Should be called after any state update
     * that can affect issues.
     */
    void updateIssues(@UserIdInt int userId) {
        updateIssues(userId, UserUtils.isManagedProfile(userId, mContext));
    }

    private void updateIssues(@UserIdInt int userId, boolean isManagedProfile) {
        List<SafetySourceIssueInfo> issues =
                getAllStoredIssuesFromRawSourceData(userId, isManagedProfile);
        processIssues(issues);
        mUserIdToIssuesInfo.put(userId, unmodifiableList(issues));
    }

    /**
     * Fetches a list of issues related to the given {@link UserProfileGroup}.
     *
     * <p>Issues in the list are sorted in descending order and deduplicated (if applicable, only on
     * Android U+).
     *
     * <p>Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    List<SafetySourceIssueInfo> getIssuesDedupedSortedDescFor(UserProfileGroup userProfileGroup) {
        List<SafetySourceIssueInfo> issuesInfo = getIssuesFor(userProfileGroup);
        issuesInfo.sort(SAFETY_SOURCE_ISSUES_INFO_BY_SEVERITY_DESCENDING);
        return issuesInfo;
    }

    /**
     * Counts the total number of issues from loggable sources, in the given {@link
     * UserProfileGroup}.
     *
     * <p>Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    int countLoggableIssuesFor(UserProfileGroup userProfileGroup) {
        List<SafetySourceIssueInfo> relevantIssues = getIssuesFor(userProfileGroup);
        int issueCount = 0;
        for (int i = 0; i < relevantIssues.size(); i++) {
            SafetySourceIssueInfo safetySourceIssueInfo = relevantIssues.get(i);
            if (SafetySources.isLoggable(safetySourceIssueInfo.getSafetySource())) {
                issueCount++;
            }
        }
        return issueCount;
    }

    /** Gets an unmodifiable list of all issues for the given {@code userId}. */
    List<SafetySourceIssueInfo> getIssuesForUser(@UserIdInt int userId) {
        return mUserIdToIssuesInfo.get(userId, emptyList());
    }

    private void processIssues(List<SafetySourceIssueInfo> issuesInfo) {
        issuesInfo.sort(SAFETY_SOURCE_ISSUES_INFO_BY_SEVERITY_DESCENDING);

        if (SdkLevel.isAtLeastU() && mSafetyCenterIssueDeduplicator != null) {
            mSafetyCenterIssueDeduplicator.deduplicateIssues(issuesInfo);
        }
    }

    private List<SafetySourceIssueInfo> getAllStoredIssuesFromRawSourceData(
            @UserIdInt int userId, boolean isManagedProfile) {
        List<SafetySourceIssueInfo> allIssuesInfo = new ArrayList<>();

        List<SafetySourcesGroup> safetySourcesGroups =
                mSafetyCenterConfigReader.getSafetySourcesGroups();
        for (int j = 0; j < safetySourcesGroups.size(); j++) {
            addSafetySourceIssuesInfo(
                    allIssuesInfo, safetySourcesGroups.get(j), userId, isManagedProfile);
        }

        return allIssuesInfo;
    }

    private void addSafetySourceIssuesInfo(
            List<SafetySourceIssueInfo> issuesInfo,
            SafetySourcesGroup safetySourcesGroup,
            @UserIdInt int userId,
            boolean isManagedProfile) {
        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        for (int i = 0; i < safetySources.size(); i++) {
            SafetySource safetySource = safetySources.get(i);

            if (!SafetySources.isExternal(safetySource)) {
                continue;
            }
            if (isManagedProfile && !SafetySources.supportsManagedProfiles(safetySource)) {
                continue;
            }

            addSafetySourceIssuesInfo(issuesInfo, safetySource, safetySourcesGroup, userId);
        }
    }

    private void addSafetySourceIssuesInfo(
            List<SafetySourceIssueInfo> issuesInfo,
            SafetySource safetySource,
            SafetySourcesGroup safetySourcesGroup,
            @UserIdInt int userId) {
        SafetySourceKey key = SafetySourceKey.of(safetySource.getId(), userId);
        SafetySourceData safetySourceData =
                mSafetySourceDataRepository.getSafetySourceDataInternal(key);

        if (safetySourceData == null) {
            return;
        }

        List<SafetySourceIssue> safetySourceIssues = safetySourceData.getIssues();
        for (int i = 0; i < safetySourceIssues.size(); i++) {
            SafetySourceIssue safetySourceIssue = safetySourceIssues.get(i);

            SafetySourceIssueInfo safetySourceIssueInfo =
                    new SafetySourceIssueInfo(
                            safetySourceIssue, safetySource, safetySourcesGroup, userId);
            issuesInfo.add(safetySourceIssueInfo);
        }
    }

    /**
     * Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    private List<SafetySourceIssueInfo> getIssuesFor(UserProfileGroup userProfileGroup) {
        List<SafetySourceIssueInfo> issues =
                new ArrayList<>(getIssuesForUser(userProfileGroup.getProfileParentUserId()));

        int[] managedRunningProfileUserIds = userProfileGroup.getManagedRunningProfilesUserIds();
        for (int i = 0; i < managedRunningProfileUserIds.length; i++) {
            issues.addAll(getIssuesForUser(managedRunningProfileUserIds[i]));
        }

        return issues;
    }

    /** A comparator to order {@link SafetySourceIssueInfo} by severity level descending. */
    private static final class SafetySourceIssuesInfoBySeverityDescending
            implements Comparator<SafetySourceIssueInfo> {

        private SafetySourceIssuesInfoBySeverityDescending() {}

        @Override
        public int compare(SafetySourceIssueInfo left, SafetySourceIssueInfo right) {
            return Integer.compare(
                    right.getSafetySourceIssue().getSeverityLevel(),
                    left.getSafetySourceIssue().getSeverityLevel());
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(PrintWriter fout) {
        fout.println("ISSUE REPOSITORY");
        for (int i = 0; i < mUserIdToIssuesInfo.size(); i++) {
            List<SafetySourceIssueInfo> issues = mUserIdToIssuesInfo.valueAt(i);
            fout.println("\tUSER ID: " + mUserIdToIssuesInfo.keyAt(i));
            for (int j = 0; j < issues.size(); j++) {
                fout.println("\t\tSafetySourceIssueInfo = " + issues.get(j));
            }
        }
        fout.println();
    }

    /** Clears all the data from the repository. */
    void clear() {
        mUserIdToIssuesInfo.clear();
    }

    /** Clears all data related to the given {@code userId}. */
    void clearForUser(@UserIdInt int userId) {
        mUserIdToIssuesInfo.delete(userId);
    }
}
