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

import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.annotation.WorkerThread;
import android.content.ApexEnvironment;
import android.os.Handler;
import android.safetycenter.SafetySourceData;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.BackgroundThread;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetyCenterFlags;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.persistence.PersistedSafetyCenterIssue;
import com.android.safetycenter.persistence.PersistenceException;
import com.android.safetycenter.persistence.SafetyCenterIssuesPersistence;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Repository to manage data about all issue dismissals in Safety Center.
 *
 * <p>It stores the state of this class automatically into a file. After the class is first
 * instantiated the user should call {@link
 * SafetyCenterIssueDismissalRepository#loadStateFromFile()} to initialize the state with what was
 * stored in the file.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 *
 * @hide
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
public final class SafetyCenterIssueDismissalRepository {

    private static final String TAG = "SafetyCenterIssueDis";

    /** The APEX name used to retrieve the APEX owned data directories. */
    private static final String APEX_MODULE_NAME = "com.android.permission";

    /** The name of the file used to persist the {@link SafetyCenterIssueDismissalRepository}. */
    private static final String ISSUE_DISMISSAL_REPOSITORY_FILE_NAME = "safety_center_issues.xml";

    /** The time delay used to throttle and aggregate writes to disk. */
    private static final Duration WRITE_DELAY = Duration.ofMillis(500);

    private final Handler mWriteHandler = BackgroundThread.getHandler();

    @NonNull private final Object mApiLock;

    @NonNull private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    private final ArrayMap<SafetyCenterIssueKey, IssueData> mIssues = new ArrayMap<>();
    private boolean mWriteStateToFileScheduled = false;

    public SafetyCenterIssueDismissalRepository(
            @NonNull Object apiLock, @NonNull SafetyCenterConfigReader safetyCenterConfigReader) {
        mApiLock = apiLock;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
    }

    /**
     * Returns {@code true} if the issue with the given key and severity level is currently
     * dismissed.
     *
     * <p>An issue which is dismissed at one time may become "un-dismissed" later, after the
     * resurface delay (which depends on severity level) has elapsed.
     *
     * <p>If the given issue key is not found in the repository this method returns {@code false}.
     */
    public boolean isIssueDismissed(
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
     * Marks the issue with the given key as dismissed.
     *
     * <p>That issue's notification (if any) is also marked as dismissed.
     */
    public void dismissIssue(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "dismissing");
        if (issueData == null) {
            return;
        }
        Instant now = Instant.now();
        issueData.setDismissedAt(now);
        issueData.setDismissCount(issueData.getDismissCount() + 1);
        issueData.setNotificationDismissedAt(now);
        scheduleWriteStateToFile();
    }

    /**
     * Copy dismissal data from one issue to the other.
     *
     * <p>This will align dismissal state of these issues, unless issues are of different
     * severities, in which case they can potentially differ in resurface times.
     */
    public void copyDismissalData(
            @NonNull SafetyCenterIssueKey keyFrom, @NonNull SafetyCenterIssueKey keyTo) {
        IssueData dataFrom = getOrWarn(keyFrom, "copying dismissed data");
        IssueData dataTo = getOrWarn(keyTo, "copying dismissed data");
        if (dataFrom == null || dataTo == null) {
            return;
        }

        dataTo.setDismissedAt(dataFrom.getDismissedAt());
        dataTo.setDismissCount(dataFrom.getDismissCount());
        scheduleWriteStateToFile();
    }

    /**
     * Marks the notification (if any) of the issue with the given key as dismissed.
     *
     * <p>The issue itself is <strong>not</strong> marked as dismissed and its warning card can
     * still appear in the Safety Center UI.
     */
    public void dismissNotification(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "dismissing notification");
        if (issueData == null) {
            return;
        }
        issueData.setNotificationDismissedAt(Instant.now());
        scheduleWriteStateToFile();
    }

    /**
     * Returns the {@link Instant} when the issue with the given key was first reported to Safety
     * Center.
     */
    @Nullable
    public Instant getIssueFirstSeenAt(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "getting first seen");
        if (issueData == null) {
            return null;
        }
        return issueData.getFirstSeenAt();
    }

    /**
     * Returns the {@link Instant} when the notification for the issue with the given key was last
     * dismissed.
     */
    // TODO(b/261429824): Handle mNotificationDismissedAt w.r.t. issue deduplication
    @Nullable
    public Instant getNotificationDismissedAt(@NonNull SafetyCenterIssueKey safetyCenterIssueKey) {
        IssueData issueData = getOrWarn(safetyCenterIssueKey, "getting notification dismissed");
        if (issueData == null) {
            return null;
        }
        return issueData.getNotificationDismissedAt();
    }

    /**
     * Updates the issue repository to contain exactly the given {@code safetySourceIssueIds} for
     * the supplied source and user.
     */
    void updateIssuesForSource(
            @NonNull ArraySet<String> safetySourceIssueIds,
            @NonNull String safetySourceId,
            @UserIdInt int userId) {
        boolean someDataChanged = false;

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
                someDataChanged = true;
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
                someDataChanged = true;
            }
        }
        if (someDataChanged) {
            scheduleWriteStateToFile();
        }
    }

    /** Takes a snapshot of the contents of the repository to be written to persistent storage. */
    @NonNull
    private List<PersistedSafetyCenterIssue> snapshot() {
        List<PersistedSafetyCenterIssue> persistedIssues = new ArrayList<>();
        for (int i = 0; i < mIssues.size(); i++) {
            String encodedKey = SafetyCenterIds.encodeToString(mIssues.keyAt(i));
            IssueData issueData = mIssues.valueAt(i);
            persistedIssues.add(issueData.toPersistedIssueBuilder().setKey(encodedKey).build());
        }
        return persistedIssues;
    }

    /**
     * Replaces the contents of the repository with the given issues read from persistent storage.
     */
    private void load(@NonNull List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues) {
        boolean someDataChanged = false;
        mIssues.clear();
        for (int i = 0; i < persistedSafetyCenterIssues.size(); i++) {
            PersistedSafetyCenterIssue persistedIssue = persistedSafetyCenterIssues.get(i);
            SafetyCenterIssueKey key = SafetyCenterIds.issueKeyFromString(persistedIssue.getKey());

            // Check the source associated with this issue still exists, it might have been removed
            // from the Safety Center config or the device might have rebooted with data persisted
            // from a temporary Safety Center config.
            if (!mSafetyCenterConfigReader.isExternalSafetySourceActive(key.getSafetySourceId())) {
                someDataChanged = true;
                continue;
            }

            IssueData issueData = IssueData.fromPersistedIssue(persistedIssue);
            mIssues.put(key, issueData);
        }
        if (someDataChanged) {
            scheduleWriteStateToFile();
        }
    }

    /** Clears all the data in the repository. */
    public void clear() {
        mIssues.clear();
        scheduleWriteStateToFile();
    }

    /** Clears all the data in the repository for the given user. */
    public void clearForUser(@UserIdInt int userId) {
        boolean someDataChanged = false;
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mIssues.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey issueKey = mIssues.keyAt(i);
            if (issueKey.getUserId() == userId) {
                mIssues.removeAt(i);
                someDataChanged = true;
            }
        }
        if (someDataChanged) {
            scheduleWriteStateToFile();
        }
    }

    /** Dumps state for debugging purposes. */
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter fout) {
        int issueRepositoryCount = mIssues.size();
        fout.println(
                "ISSUE DISMISSAL REPOSITORY ("
                        + issueRepositoryCount
                        + ", mWriteStateToFileScheduled="
                        + mWriteStateToFileScheduled
                        + ")");
        for (int i = 0; i < issueRepositoryCount; i++) {
            SafetyCenterIssueKey key = mIssues.keyAt(i);
            IssueData data = mIssues.valueAt(i);
            fout.println("\t[" + i + "] " + toUserFriendlyString(key) + " -> " + data);
        }
        fout.println();

        File issueDismissalRepositoryFile = getIssueDismissalRepositoryFile();
        fout.println(
                "ISSUE DISMISSAL REPOSITORY FILE ("
                        + issueDismissalRepositoryFile.getAbsolutePath()
                        + ")");
        fout.flush();
        try {
            Files.copy(issueDismissalRepositoryFile.toPath(), new FileOutputStream(fd));
        } catch (IOException e) {
            // TODO(b/266202404)
            e.printStackTrace(fout);
        }
        fout.println();
    }

    @Nullable
    private IssueData getOrWarn(@NonNull SafetyCenterIssueKey issueKey, @NonNull String reason) {
        IssueData issueData = mIssues.get(issueKey);
        if (issueData == null) {
            Log.w(
                    TAG,
                    "Issue missing when reading from dismissal repository for "
                            + reason
                            + ": "
                            + toUserFriendlyString(issueKey));
            return null;
        }
        return issueData;
    }

    /** Schedule writing the {@link SafetyCenterIssueDismissalRepository} to file. */
    private void scheduleWriteStateToFile() {
        if (!mWriteStateToFileScheduled) {
            mWriteHandler.postDelayed(this::writeStateToFile, WRITE_DELAY.toMillis());
            mWriteStateToFileScheduled = true;
        }
    }

    @WorkerThread
    private void writeStateToFile() {
        List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues;

        synchronized (mApiLock) {
            mWriteStateToFileScheduled = false;
            persistedSafetyCenterIssues = snapshot();
            // Since all write operations are scheduled in the same background thread, we can safely
            // release the lock after creating a snapshot and know that all snapshots will be
            // written in the correct order even if we are not holding the lock.
        }

        SafetyCenterIssuesPersistence.write(
                persistedSafetyCenterIssues, getIssueDismissalRepositoryFile());
    }

    /** Read the contents of the file and load them into this class. */
    public void loadStateFromFile() {
        List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues = new ArrayList<>();

        try {
            persistedSafetyCenterIssues =
                    SafetyCenterIssuesPersistence.read(getIssueDismissalRepositoryFile());
            Log.i(TAG, "Safety Center persisted issues read successfully");
        } catch (PersistenceException e) {
            Log.e(TAG, "Cannot read Safety Center persisted issues", e);
        }

        load(persistedSafetyCenterIssues);
        scheduleWriteStateToFile();
    }

    @NonNull
    private static File getIssueDismissalRepositoryFile() {
        ApexEnvironment apexEnvironment = ApexEnvironment.getApexEnvironment(APEX_MODULE_NAME);
        File dataDirectory = apexEnvironment.getDeviceProtectedDataDir();
        // It should resolve to /data/misc/apexdata/com.android.permission/safety_center_issues.xml
        return new File(dataDirectory, ISSUE_DISMISSAL_REPOSITORY_FILE_NAME);
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
            issueData.setNotificationDismissedAt(persistedIssue.getNotificationDismissedAt());
            return issueData;
        }

        @NonNull private final Instant mFirstSeenAt;

        @Nullable private Instant mDismissedAt;
        private int mDismissCount;

        @Nullable private Instant mNotificationDismissedAt;

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

        @Nullable
        private Instant getNotificationDismissedAt() {
            return mNotificationDismissedAt;
        }

        private void setNotificationDismissedAt(@Nullable Instant notificationDismissedAt) {
            mNotificationDismissedAt = notificationDismissedAt;
        }

        @NonNull
        private PersistedSafetyCenterIssue.Builder toPersistedIssueBuilder() {
            return new PersistedSafetyCenterIssue.Builder()
                    .setFirstSeenAt(mFirstSeenAt)
                    .setDismissedAt(mDismissedAt)
                    .setDismissCount(mDismissCount)
                    .setNotificationDismissedAt(mNotificationDismissedAt);
        }

        @Override
        public String toString() {
            return "SafetySourceIssueInfo{"
                    + "mFirstSeenAt="
                    + mFirstSeenAt
                    + ", mDismissedAt="
                    + mDismissedAt
                    + ", mDismissCount="
                    + mDismissCount
                    + ", mNotificationDismissedAt="
                    + mNotificationDismissedAt
                    + '}';
        }
    }
}
