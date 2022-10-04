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

import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.safetycenter.SafetySourceData;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.persistence.PersistedSafetyCenterIssue;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Cache to manage data about all the issues sent to Safety Center, in particular whether a given
 * issue should currently be considered dismissed.
 *
 * <p>The contents of this cache can be populated from and persisted to disk using the {@link
 * #load(List)} and {@link #snapshot()} methods. When {@link #isDirty()} returns {@code true} that
 * means that the contents of the cache may have changed since the last load or snapshot occurred.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterIssueCache {

    private static final String TAG = "SafetyCenterIssueCache";

    @NonNull private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    private final ArrayMap<SafetyCenterIssueKey, IssueData> mIssues = new ArrayMap<>();
    private boolean mIsDirty = false;

    SafetyCenterIssueCache(@NonNull SafetyCenterConfigReader safetyCenterConfigReader) {
        mSafetyCenterConfigReader = safetyCenterConfigReader;
    }

    /**
     * Counts the total number of issues in the issue cache, from currently-active sources, in the
     * given {@link UserProfileGroup}.
     */
    int countActiveIssues(@NonNull UserProfileGroup userProfileGroup) {
        int issueCount = 0;
        for (int i = 0; i < mIssues.size(); i++) {
            SafetyCenterIssueKey issueKey = mIssues.keyAt(i);
            if (mSafetyCenterConfigReader.isExternalSafetySourceActive(issueKey.getSafetySourceId())
                    && userProfileGroup.contains(issueKey.getUserId())) {
                issueCount++;
            }
        }
        return issueCount;
    }

    /**
     * Returns {@code true} if the issue with the given key and severity level is currently
     * dismissed.
     *
     * <p>An issue which is dismissed at one time may become "un-dismissed" later, after the
     * resurface delay (which depends on severity level) has elapsed.
     *
     * <p>If the given issue key is not found in the cache this method returns {@code false}.
     */
    boolean isIssueDismissed(
            @NonNull SafetyCenterIssueKey safetyCenterIssueKey,
            @SafetySourceData.SeverityLevel int safetySourceIssueSeverityLevel) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "checking if dismissed");
        if (issueData == null) {
            return false;
        }

        Instant dismissedAt = issueData.getDismissedAt();
        boolean isNotCurrentlyDismissed = dismissedAt == null;
        if (isNotCurrentlyDismissed) {
            return false;
        }

        long maxCount = SafetyCenterFlags.getResurfaceIssueMaxCount(safetySourceIssueSeverityLevel);
        Duration delay = SafetyCenterFlags.getResurfaceIssueDelay(safetySourceIssueSeverityLevel);

        boolean hasAlreadyResurfacedTheMaxAllowedNumberOfTimes =
                issueData.getDismissCount() > maxCount;
        if (hasAlreadyResurfacedTheMaxAllowedNumberOfTimes) {
            return true;
        }

        Duration timeSinceLastDismissal = Duration.between(dismissedAt, Instant.now());
        boolean isTimeToResurface = timeSinceLastDismissal.compareTo(delay) >= 0;
        if (isTimeToResurface) {
            return false;
        }

        return true;
    }

    /**
     * Dismisses the issue with the given key.
     *
     * <p>This method may change the value reported by {@link #isDirty} to {@code true}.
     */
    void dismissIssue(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "dismissing");
        if (issueData == null) {
            return;
        }
        issueData.setDismissedAt(Instant.now());
        issueData.setDismissCount(issueData.getDismissCount() + 1);
        mIsDirty = true;
    }

    /**
     * Updates the issue cache to contain exactly the given {@code safetySourceIssueIds} for the
     * supplied source and user.
     */
    void updateIssuesForSource(
            @NonNull ArraySet<String> safetySourceIssueIds,
            @NonNull String safetySourceId,
            @UserIdInt int userId) {
        // Remove issues no longer reported by the source.
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mIssues.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey issueKey = mIssues.keyAt(i);
            boolean doesNotBelongToUserOrSource =
                    issueKey.getUserId() != userId
                            || !Objects.equals(issueKey.getSafetySourceId(), safetySourceId);
            if (doesNotBelongToUserOrSource) {
                continue;
            }
            boolean isIssueNoLongerReported =
                    !safetySourceIssueIds.contains(issueKey.getSafetySourceIssueId());
            if (isIssueNoLongerReported) {
                mIssues.removeAt(i);
                mIsDirty = true;
            }
        }
        // Add newly reported issues.
        for (int i = 0; i < safetySourceIssueIds.size(); i++) {
            SafetyCenterIssueKey issueKey =
                    SafetyCenterIssueKey.newBuilder()
                            .setUserId(userId)
                            .setSafetySourceId(safetySourceId)
                            .setSafetySourceIssueId(safetySourceIssueIds.valueAt(i))
                            .build();
            boolean isIssueNewlyReported = !mIssues.containsKey(issueKey);
            if (isIssueNewlyReported) {
                mIssues.put(issueKey, new IssueData(Instant.now()));
                mIsDirty = true;
            }
        }
    }

    /**
     * Returns {@code true} if the contents of the cache may have changed since the last {@link
     * #load(List)} or {@link #snapshot()} occurred.
     */
    boolean isDirty() {
        return mIsDirty;
    }

    /**
     * Takes a snapshot of the contents of the cache to be written to persistent storage.
     *
     * <p>This method will reset the value reported by {@link #isDirty} to {@code false}.
     */
    @NonNull
    List<PersistedSafetyCenterIssue> snapshot() {
        mIsDirty = false;
        List<PersistedSafetyCenterIssue> persistedIssues = new ArrayList<>();
        for (int i = 0; i < mIssues.size(); i++) {
            String encodedKey = SafetyCenterIds.encodeToString(mIssues.keyAt(i));
            IssueData issueData = mIssues.valueAt(i);
            persistedIssues.add(issueData.toPersistedIssueBuilder().setKey(encodedKey).build());
        }
        return persistedIssues;
    }

    /**
     * Replaces the contents of the cache with the given issues read from persistent storage.
     *
     * <p>This method may change the value reported by {@link #isDirty} to {@code true}.
     */
    void load(@NonNull List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues) {
        mIssues.clear();
        for (int i = 0; i < persistedSafetyCenterIssues.size(); i++) {
            PersistedSafetyCenterIssue persistedIssue = persistedSafetyCenterIssues.get(i);
            SafetyCenterIssueKey key = SafetyCenterIds.issueKeyFromString(persistedIssue.getKey());

            // Check the source associated with this issue still exists, it might have been removed
            // from the Safety Center config or the device might have rebooted with data persisted
            // from a temporary Safety Center config.
            if (!mSafetyCenterConfigReader.isExternalSafetySourceActive(key.getSafetySourceId())) {
                mIsDirty = true;
                continue;
            }

            IssueData issueData = IssueData.fromPersistedIssue(persistedIssue);
            mIssues.put(key, issueData);
        }
    }

    /**
     * Clears all the data in the cache.
     *
     * <p>This method will change the value reported by {@link #isDirty} to {@code true}.
     */
    void clear() {
        mIssues.clear();
        mIsDirty = true;
    }

    /**
     * Clears all the data in the cache for the given user.
     *
     * <p>This method may change the value reported by {@link #isDirty} to {@code true}.
     */
    void clearForUser(@UserIdInt int userId) {
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mIssues.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey issueKey = mIssues.keyAt(i);
            if (issueKey.getUserId() == userId) {
                mIssues.removeAt(i);
                mIsDirty = true;
            }
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(@NonNull PrintWriter fout) {
        int issueCacheCount = mIssues.size();
        fout.println("ISSUE CACHE (" + issueCacheCount + ", dirty=" + mIsDirty + ")");
        for (int i = 0; i < issueCacheCount; i++) {
            SafetyCenterIssueKey key = mIssues.keyAt(i);
            IssueData data = mIssues.valueAt(i);
            fout.println("\t[" + i + "] " + toUserFriendlyString(key) + " -> " + data);
        }
        fout.println();
    }

    @Nullable
    private IssueData getOrWarn(@NonNull SafetyCenterIssueKey issueKey, @NonNull String reason) {
        IssueData issueData = mIssues.get(issueKey);
        if (issueData == null) {
            Log.w(
                    TAG,
                    "Issue missing when reading from cache for "
                            + reason
                            + ": "
                            + toUserFriendlyString(issueKey));
            return null;
        }
        return issueData;
    }

    /**
     * An internal mutable data structure containing issue metadata which is used to determine
     * whether an issue should be dismissed/hidden from the user.
     */
    private static final class IssueData {

        @NonNull
        private static IssueData fromPersistedIssue(
                @NonNull PersistedSafetyCenterIssue persistedIssue) {
            IssueData issueData = new IssueData(persistedIssue.getFirstSeenAt());
            issueData.setDismissedAt(persistedIssue.getDismissedAt());
            issueData.setDismissCount(persistedIssue.getDismissCount());
            return issueData;
        }

        @NonNull private final Instant mFirstSeenAt;

        @Nullable private Instant mDismissedAt;
        private int mDismissCount;

        private IssueData(@NonNull Instant firstSeenAt) {
            mFirstSeenAt = firstSeenAt;
        }

        @NonNull
        private Instant getFirstSeenAt() {
            return mFirstSeenAt;
        }

        @Nullable
        private Instant getDismissedAt() {
            return mDismissedAt;
        }

        private void setDismissedAt(@Nullable Instant dismissedAt) {
            mDismissedAt = dismissedAt;
        }

        private int getDismissCount() {
            return mDismissCount;
        }

        private void setDismissCount(int dismissCount) {
            mDismissCount = dismissCount;
        }

        @NonNull
        private PersistedSafetyCenterIssue.Builder toPersistedIssueBuilder() {
            return new PersistedSafetyCenterIssue.Builder()
                    .setFirstSeenAt(mFirstSeenAt)
                    .setDismissedAt(mDismissedAt)
                    .setDismissCount(mDismissCount);
        }

        @Override
        public String toString() {
            return "IssueData{"
                    + "mFirstSeenAt="
                    + mFirstSeenAt
                    + ", mDismissedAt="
                    + mDismissedAt
                    + ", mDismissCount="
                    + mDismissCount
                    + '}';
        }
    }
}
