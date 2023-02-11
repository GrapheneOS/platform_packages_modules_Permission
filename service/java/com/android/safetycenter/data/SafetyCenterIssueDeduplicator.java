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

import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.SafetySourceIssueInfo;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/** Deduplicates issues based on deduplication info provided by the source and the issue. */
@RequiresApi(UPSIDE_DOWN_CAKE)
@NotThreadSafe
final class SafetyCenterIssueDeduplicator {

    @NonNull
    private final SafetyCenterIssueDismissalRepository mSafetyCenterIssueDismissalRepository;

    SafetyCenterIssueDeduplicator(
            @NonNull SafetyCenterIssueDismissalRepository safetyCenterIssueDismissalRepository) {
        this.mSafetyCenterIssueDismissalRepository = safetyCenterIssueDismissalRepository;
    }

    /**
     * Accepts a list of issues sorted by priority and filters out duplicates.
     *
     * <p>Issues are considered duplicate if they have the same deduplication id and were sent by
     * sources which are part of the same deduplication group. All but the highest priority
     * duplicate issue will be filtered out.
     *
     * <p>In case any issue, in the bucket of duplicate issues, was dismissed, all issues of the
     * same or lower severity will be dismissed as well.
     *
     * <p>This method modifies the given argument.
     */
    void deduplicateIssues(@NonNull List<SafetySourceIssueInfo> sortedIssues) {
        // (dedup key) -> list(issues)
        ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets =
                createDedupBuckets(sortedIssues);

        dismissDuplicateIssuesOfDismissedIssue(dedupBuckets);

        ArraySet<SafetyCenterIssueKey> duplicatesToFilterOut =
                getDuplicatesToFilterOut(dedupBuckets);

        Iterator<SafetySourceIssueInfo> it = sortedIssues.iterator();
        while (it.hasNext()) {
            if (duplicatesToFilterOut.contains(it.next().getSafetyCenterIssueKey())) {
                it.remove();
            }
        }
    }

    /**
     * Handles dismissals logic: in each bucket, dismissal details of the top (highest priority)
     * dismissed issue will be copied to all other duplicate issues in that bucket, that are of
     * equal or lower severity (not priority).
     */
    private void dismissDuplicateIssuesOfDismissedIssue(
            @NonNull ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets) {
        for (int i = 0; i < dedupBuckets.size(); i++) {
            List<SafetySourceIssueInfo> duplicates = dedupBuckets.valueAt(i);
            SafetySourceIssueInfo topDismissed = getHighestPriorityDismissedIssue(duplicates);
            alignDismissalsDataWithinBucket(topDismissed, duplicates);
        }
    }

    /**
     * Dismisses all issues of lower or equal severity relative to the given top dismissed issue in
     * the bucket.
     */
    private void alignDismissalsDataWithinBucket(
            @Nullable SafetySourceIssueInfo topDismissed,
            @NonNull List<SafetySourceIssueInfo> duplicates) {
        if (topDismissed == null) {
            return;
        }
        SafetyCenterIssueKey topDismissedIssueKey = topDismissed.getSafetyCenterIssueKey();
        int topDismissedSeverityLevel = topDismissed.getSafetySourceIssue().getSeverityLevel();
        for (int i = 0; i < duplicates.size(); i++) {
            SafetySourceIssueInfo issueInfo = duplicates.get(i);
            SafetyCenterIssueKey issueKey = issueInfo.getSafetyCenterIssueKey();
            if (!issueKey.equals(topDismissedIssueKey)
                    && issueInfo.getSafetySourceIssue().getSeverityLevel()
                            <= topDismissedSeverityLevel) {
                // all duplicate issues should have same dismissals data as top dismissed issue
                mSafetyCenterIssueDismissalRepository.copyDismissalData(
                        topDismissedIssueKey, issueKey);
            }
        }
    }

    @Nullable
    private SafetySourceIssueInfo getHighestPriorityDismissedIssue(
            @NonNull List<SafetySourceIssueInfo> duplicates) {
        for (int i = 0; i < duplicates.size(); i++) {
            SafetySourceIssueInfo issueInfo = duplicates.get(i);
            if (mSafetyCenterIssueDismissalRepository.isIssueDismissed(
                    issueInfo.getSafetyCenterIssueKey(),
                    issueInfo.getSafetySourceIssue().getSeverityLevel())) {
                return issueInfo;
            }
        }

        return null;
    }

    /** Returns a set of duplicate issues that need to be filtered out. */
    @NonNull
    private static ArraySet<SafetyCenterIssueKey> getDuplicatesToFilterOut(
            @NonNull ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets) {
        ArraySet<SafetyCenterIssueKey> duplicatesToFilterOut = new ArraySet<>();

        for (int i = 0; i < dedupBuckets.size(); i++) {
            List<SafetySourceIssueInfo> duplicates = dedupBuckets.valueAt(i);
            // all but the top one in the bucket
            for (int j = 1; j < duplicates.size(); j++) {
                duplicatesToFilterOut.add(duplicates.get(j).getSafetyCenterIssueKey());
            }
        }

        return duplicatesToFilterOut;
    }

    /** Returns a mapping (dedup key) -> list(issues). */
    @NonNull
    private static ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> createDedupBuckets(
            @NonNull List<SafetySourceIssueInfo> sortedIssues) {
        ArrayMap<DeduplicationKey, List<SafetySourceIssueInfo>> dedupBuckets = new ArrayMap<>();

        for (int i = 0; i < sortedIssues.size(); i++) {
            SafetySourceIssueInfo issueInfo = sortedIssues.get(i);
            DeduplicationKey dedupKey = getDedupKey(issueInfo);
            if (dedupKey == null) {
                continue;
            }

            // each bucket will remain sorted
            List<SafetySourceIssueInfo> bucket =
                    dedupBuckets.getOrDefault(dedupKey, new ArrayList<>());
            bucket.add(issueInfo);

            dedupBuckets.put(dedupKey, bucket);
        }

        return dedupBuckets;
    }

    /** Returns deduplication key of the given {@code issueInfo}. */
    @Nullable
    private static DeduplicationKey getDedupKey(@NonNull SafetySourceIssueInfo issueInfo) {
        String deduplicationGroup = issueInfo.getSafetySource().getDeduplicationGroup();
        String deduplicationId = issueInfo.getSafetySourceIssue().getDeduplicationId();

        if (deduplicationGroup == null || deduplicationId == null) {
            return null;
        }
        return new DeduplicationKey(
                deduplicationGroup,
                deduplicationId,
                issueInfo.getSafetyCenterIssueKey().getUserId());
    }

    private static class DeduplicationKey {

        @NonNull private final String mDeduplicationGroup;
        @NonNull private final String mDeduplicationId;
        private final int mUserId;

        private DeduplicationKey(
                @NonNull String deduplicationGroup,
                @NonNull String deduplicationId,
                @UserIdInt int userId) {
            mDeduplicationGroup = deduplicationGroup;
            mDeduplicationId = deduplicationId;
            mUserId = userId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDeduplicationGroup, mDeduplicationId, mUserId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DeduplicationKey)) return false;
            DeduplicationKey dedupKey = (DeduplicationKey) o;
            return mDeduplicationGroup.equals(dedupKey.mDeduplicationGroup)
                    && mDeduplicationId.equals(dedupKey.mDeduplicationId)
                    && mUserId == dedupKey.mUserId;
        }
    }
}
