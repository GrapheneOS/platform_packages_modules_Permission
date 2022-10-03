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
import android.safetycenter.SafetyCenterIssue;
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
 * #loadSafetyCenterIssueCache(List)} and {@link #snapshotSafetyCenterIssueCache()} methods,
 * respectively. When {@link #isSafetyCenterIssueCacheDirty()} returns {@code true} that indicates
 * that the in-memory contents of the cache may have changed since the last load or snapshot
 * occurred.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterIssueCache {

    private static final String TAG = "SafetyCenterIssueCache";

    @NonNull private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    // TODO(b/249979479): Here and elsewhere check/edit variable and method names in this class
    private final ArrayMap<SafetyCenterIssueKey, SafetyCenterIssueData> mSafetyCenterIssueCache =
            new ArrayMap<>();

    private boolean mSafetyCenterIssueCacheDirty = false;

    SafetyCenterIssueCache(@NonNull SafetyCenterConfigReader safetyCenterConfigReader) {
        mSafetyCenterConfigReader = safetyCenterConfigReader;
    }

    /**
     * Counts the number of issues in the issue cache from currently-active sources in the given
     * {@link UserProfileGroup}.
     */
    int countActiveSourcesIssues(@NonNull UserProfileGroup userProfileGroup) {
        int issueCount = 0;
        for (int i = 0; i < mSafetyCenterIssueCache.size(); i++) {
            SafetyCenterIssueKey issueKey = mSafetyCenterIssueCache.keyAt(i);
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
    boolean isDismissed(
            @NonNull SafetyCenterIssueKey safetyCenterIssueKey,
            @SafetySourceData.SeverityLevel int safetySourceIssueSeverityLevel) {
        SafetyCenterIssueData safetyCenterIssueData =
                getSafetyCenterIssueData(safetyCenterIssueKey, "checking if dismissed");
        if (safetyCenterIssueData == null) {
            return false;
        }

        Instant dismissedAt = safetyCenterIssueData.getDismissedAt();
        boolean isNotCurrentlyDismissed = dismissedAt == null;
        if (isNotCurrentlyDismissed) {
            return false;
        }

        long maxCount = SafetyCenterFlags.getResurfaceIssueMaxCount(safetySourceIssueSeverityLevel);
        Duration delay = SafetyCenterFlags.getResurfaceIssueDelay(safetySourceIssueSeverityLevel);

        boolean hasAlreadyResurfacedTheMaxAllowedNumberOfTimes =
                safetyCenterIssueData.getDismissCount() > maxCount;
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
     * Dismisses the given {@link SafetyCenterIssueKey}.
     *
     * <p>This method may change the value reported by {@link #isSafetyCenterIssueCacheDirty} to
     * {@code true}.
     */
    void dismissSafetyCenterIssue(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        SafetyCenterIssueData safetyCenterIssueData =
                getSafetyCenterIssueData(safetyCenterIssueKey, "dismissing");
        if (safetyCenterIssueData == null) {
            return;
        }
        safetyCenterIssueData.setDismissedAt(Instant.now());
        safetyCenterIssueData.setDismissCount(safetyCenterIssueData.getDismissCount() + 1);
        mSafetyCenterIssueCacheDirty = true;
    }

    /**
     * Updates the issue cache to contain exactly the given {@code safetySourceIssueIds} for the
     * supplied source and user.
     */
    void updateSafetyCenterIssueCache(
            @NonNull ArraySet<String> safetySourceIssueIds,
            @NonNull String safetySourceId,
            @UserIdInt int userId) {
        // Remove issues no longer reported by the source.
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetyCenterIssueCache.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey issueKey = mSafetyCenterIssueCache.keyAt(i);
            boolean doesNotBelongToUserOrSource =
                    issueKey.getUserId() != userId
                            || !Objects.equals(issueKey.getSafetySourceId(), safetySourceId);
            if (doesNotBelongToUserOrSource) {
                continue;
            }
            boolean isIssueNoLongerReported =
                    !safetySourceIssueIds.contains(issueKey.getSafetySourceIssueId());
            if (isIssueNoLongerReported) {
                mSafetyCenterIssueCache.removeAt(i);
                mSafetyCenterIssueCacheDirty = true;
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
            boolean isIssueNewlyReported = !mSafetyCenterIssueCache.containsKey(issueKey);
            if (isIssueNewlyReported) {
                mSafetyCenterIssueCache.put(issueKey, new SafetyCenterIssueData(Instant.now()));
                mSafetyCenterIssueCacheDirty = true;
            }
        }
    }

    /**
     * Returns whether the Safety Center issue cache has been modified since the last time a
     * snapshot was taken.
     */
    boolean isSafetyCenterIssueCacheDirty() {
        return mSafetyCenterIssueCacheDirty;
    }

    /**
     * Takes a snapshot of the Safety Center issue cache that should be written to persistent
     * storage.
     *
     * <p>This method will reset the value reported by {@link #isSafetyCenterIssueCacheDirty} to
     * {@code false}.
     */
    @NonNull
    List<PersistedSafetyCenterIssue> snapshotSafetyCenterIssueCache() {
        mSafetyCenterIssueCacheDirty = false;

        List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues = new ArrayList<>();

        for (int i = 0; i < mSafetyCenterIssueCache.size(); i++) {
            String encodedKey = SafetyCenterIds.encodeToString(mSafetyCenterIssueCache.keyAt(i));
            SafetyCenterIssueData safetyCenterIssueData = mSafetyCenterIssueCache.valueAt(i);
            persistedSafetyCenterIssues.add(
                    new PersistedSafetyCenterIssue.Builder()
                            .setKey(encodedKey)
                            .setFirstSeenAt(safetyCenterIssueData.getFirstSeenAt())
                            .setDismissedAt(safetyCenterIssueData.getDismissedAt())
                            .setDismissCount(safetyCenterIssueData.getDismissCount())
                            .build());
        }

        return persistedSafetyCenterIssues;
    }

    /**
     * Replaces the Safety Center issue cache with the given list of issues.
     *
     * <p>This method may modify the Safety Center issue cache and change the value reported by
     * {@link #isSafetyCenterIssueCacheDirty} to {@code true}.
     */
    void loadSafetyCenterIssueCache(
            @NonNull List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues) {
        mSafetyCenterIssueCache.clear();
        for (int i = 0; i < persistedSafetyCenterIssues.size(); i++) {
            PersistedSafetyCenterIssue persistedSafetyCenterIssue =
                    persistedSafetyCenterIssues.get(i);

            SafetyCenterIssueKey key =
                    SafetyCenterIds.issueKeyFromString(persistedSafetyCenterIssue.getKey());
            // Check the source associated with this issue still exists, it might have been removed
            // from the Safety Center config or the device might have rebooted with data persisted
            // from a temporary Safety Center config.
            if (!mSafetyCenterConfigReader.isExternalSafetySourceActive(key.getSafetySourceId())) {
                mSafetyCenterIssueCacheDirty = true;
                continue;
            }

            SafetyCenterIssueData safetyCenterIssueData =
                    new SafetyCenterIssueData(persistedSafetyCenterIssue.getFirstSeenAt());
            safetyCenterIssueData.setDismissedAt(persistedSafetyCenterIssue.getDismissedAt());
            safetyCenterIssueData.setDismissCount(persistedSafetyCenterIssue.getDismissCount());
            mSafetyCenterIssueCache.put(key, safetyCenterIssueData);
        }
    }

    /**
     * Clears all the data in this cache.
     *
     * <p>This method will change the value reported by {@link #isSafetyCenterIssueCacheDirty} to
     * {@code true}.
     */
    void clear() {
        mSafetyCenterIssueCache.clear();
        mSafetyCenterIssueCacheDirty = true;
    }

    /**
     * Clears all the data in this cache for the given user.
     *
     * <p>This method may change the value reported by {@link #isSafetyCenterIssueCacheDirty} to
     * {@code true}.
     */
    void clearForUser(@UserIdInt int userId) {
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetyCenterIssueCache.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey issueKey = mSafetyCenterIssueCache.keyAt(i);
            if (issueKey.getUserId() == userId) {
                mSafetyCenterIssueCache.removeAt(i);
                mSafetyCenterIssueCacheDirty = true;
            }
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(@NonNull PrintWriter fout) {
        int issueCacheCount = mSafetyCenterIssueCache.size();
        fout.println(
                "ISSUE CACHE ("
                        + issueCacheCount
                        + ", dirty="
                        + mSafetyCenterIssueCacheDirty
                        + ")");
        for (int i = 0; i < issueCacheCount; i++) {
            SafetyCenterIssueKey key = mSafetyCenterIssueCache.keyAt(i);
            SafetyCenterIssueData data = mSafetyCenterIssueCache.valueAt(i);
            fout.println("\t[" + i + "] " + key + " -> " + data);
        }
        fout.println();
    }

    @Nullable
    private SafetyCenterIssueData getSafetyCenterIssueData(
            @NonNull SafetyCenterIssueKey safetyCenterIssueKey, @NonNull String reason) {
        SafetyCenterIssueData safetyCenterIssueData =
                mSafetyCenterIssueCache.get(safetyCenterIssueKey);
        if (safetyCenterIssueData == null) {
            Log.w(
                    TAG,
                    "Issue missing when reading from cache for "
                            + reason
                            + ": "
                            + toUserFriendlyString(safetyCenterIssueKey));
            return null;
        }
        return safetyCenterIssueData;
    }

    /**
     * An internal mutable data structure to track extra metadata associated with a {@link
     * SafetyCenterIssue}.
     */
    private static final class SafetyCenterIssueData {

        @NonNull private final Instant mFirstSeenAt;

        @Nullable private Instant mDismissedAt;
        private int mDismissCount;

        private SafetyCenterIssueData(@NonNull Instant firstSeenAt) {
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

        @Override
        public String toString() {
            return "SafetyCenterIssueData{"
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
