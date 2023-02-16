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

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceErrorDetails;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.config.SafetyCenterConfig;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.safetycenter.ApiLock;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetyCenterRefreshTracker;
import com.android.safetycenter.SafetySourceIssueInfo;
import com.android.safetycenter.SafetySourceKey;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Manages updates and access to all data in the data subpackage.
 *
 * <p>Data entails what safety sources reported to safety center, including issues, entries,
 * dismissals, errors, in-flight actions, etc.
 *
 * @hide
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
public final class SafetyCenterDataManager {

    private final SafetySourceDataRepository mSafetySourceDataRepository;

    private final SafetyCenterIssueDismissalRepository mSafetyCenterIssueDismissalRepository;

    private final SafetyCenterIssueRepository mSafetyCenterIssueRepository;

    private final SafetyCenterInFlightIssueActionRepository
            mSafetyCenterInFlightIssueActionRepository;

    /** Creates an instance of {@link SafetyCenterDataManager}. */
    public SafetyCenterDataManager(
            Context context,
            SafetyCenterConfigReader safetyCenterConfigReader,
            SafetyCenterRefreshTracker safetyCenterRefreshTracker,
            SafetyCenterStatsdLogger safetyCenterStatsdLogger,
            ApiLock apiLock) {
        mSafetyCenterInFlightIssueActionRepository =
                new SafetyCenterInFlightIssueActionRepository(safetyCenterStatsdLogger);
        mSafetyCenterIssueDismissalRepository =
                new SafetyCenterIssueDismissalRepository(apiLock, safetyCenterConfigReader);
        mSafetySourceDataRepository =
                new SafetySourceDataRepository(
                        context,
                        safetyCenterConfigReader,
                        safetyCenterRefreshTracker,
                        mSafetyCenterInFlightIssueActionRepository,
                        mSafetyCenterIssueDismissalRepository);
        mSafetyCenterIssueRepository =
                new SafetyCenterIssueRepository(
                        context,
                        mSafetySourceDataRepository,
                        safetyCenterConfigReader,
                        SdkLevel.isAtLeastU()
                                ? new SafetyCenterIssueDeduplicator(
                                        mSafetyCenterIssueDismissalRepository)
                                : null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   STATE UPDATES ////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

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
     * SafetySourceData} entry and clears the {@link SafetyCenterIssueDismissalRepository} for the
     * source.
     *
     * <p>This method may modify the {@link SafetyCenterIssueDismissalRepository}.
     */
    public boolean setSafetySourceData(
            @Nullable SafetySourceData safetySourceData,
            String safetySourceId,
            SafetyEvent safetyEvent,
            String packageName,
            @UserIdInt int userId) {
        boolean dataUpdated =
                mSafetySourceDataRepository.setSafetySourceData(
                        safetySourceData, safetySourceId, safetyEvent, packageName, userId);
        if (dataUpdated) {
            mSafetyCenterIssueRepository.updateIssues(userId);
        }

        return dataUpdated;
    }

    /**
     * Marks the issue with the given key as dismissed.
     *
     * <p>That issue's notification (if any) is also marked as dismissed.
     */
    public void dismissSafetyCenterIssue(SafetyCenterIssueKey safetyCenterIssueKey) {
        mSafetyCenterIssueDismissalRepository.dismissIssue(safetyCenterIssueKey);
        mSafetyCenterIssueRepository.updateIssues(safetyCenterIssueKey.getUserId());
    }

    /**
     * Marks the notification (if any) of the issue with the given key as dismissed.
     *
     * <p>The issue itself is <strong>not</strong> marked as dismissed and its warning card can
     * still appear in the Safety Center UI.
     */
    public void dismissNotification(SafetyCenterIssueKey safetyCenterIssueKey) {
        mSafetyCenterIssueDismissalRepository.dismissNotification(safetyCenterIssueKey);
        mSafetyCenterIssueRepository.updateIssues(safetyCenterIssueKey.getUserId());
    }

    /**
     * Reports the given {@link SafetySourceErrorDetails} for the given {@code safetySourceId} and
     * {@code userId}, and returns whether there was a change to the underlying {@link
     * SafetyCenterData}.
     *
     * <p>Throws if the request is invalid based on the {@link SafetyCenterConfig}: the given {@code
     * safetySourceId}, {@code packageName} and/or {@code userId} are unexpected.
     */
    public boolean reportSafetySourceError(
            SafetySourceErrorDetails safetySourceErrorDetails,
            String safetySourceId,
            String packageName,
            @UserIdInt int userId) {
        boolean dataUpdated =
                mSafetySourceDataRepository.reportSafetySourceError(
                        safetySourceErrorDetails, safetySourceId, packageName, userId);
        if (dataUpdated) {
            mSafetyCenterIssueRepository.updateIssues(userId);
        }

        return dataUpdated;
    }

    /**
     * Marks the given {@link SafetySourceKey} as having errored-out and returns whether there was a
     * change to the underlying {@link SafetyCenterData}.
     */
    public boolean setSafetySourceError(SafetySourceKey safetySourceKey) {
        boolean dataUpdated = mSafetySourceDataRepository.setSafetySourceError(safetySourceKey);
        if (dataUpdated) {
            mSafetyCenterIssueRepository.updateIssues(safetySourceKey.getUserId());
        }

        return dataUpdated;
    }

    /**
     * Clears all safety source errors received so far for the given {@link UserProfileGroup}, this
     * is useful e.g. when starting a new broadcast.
     */
    public void clearSafetySourceErrors(UserProfileGroup userProfileGroup) {
        mSafetySourceDataRepository.clearSafetySourceErrors(userProfileGroup);
        mSafetyCenterIssueRepository.updateIssues(userProfileGroup);
    }

    /** Marks the given {@link SafetyCenterIssueActionId} as in-flight. */
    public void markSafetyCenterIssueActionInFlight(
            SafetyCenterIssueActionId safetyCenterIssueActionId) {
        mSafetyCenterInFlightIssueActionRepository.markSafetyCenterIssueActionInFlight(
                safetyCenterIssueActionId);
        mSafetyCenterIssueRepository.updateIssues(
                safetyCenterIssueActionId.getSafetyCenterIssueKey().getUserId());
    }

    /**
     * Unmarks the given {@link SafetyCenterIssueActionId} as in-flight, logs that event to statsd
     * with the given {@code result} value, and returns {@code true} if the underlying {@link
     * SafetyCenterData} changed.
     */
    public boolean unmarkSafetyCenterIssueActionInFlight(
            SafetyCenterIssueActionId safetyCenterIssueActionId,
            SafetySourceIssue safetySourceIssue,
            @SafetyCenterStatsdLogger.SystemEventResult int result) {
        boolean dataUpdated =
                mSafetyCenterInFlightIssueActionRepository.unmarkSafetyCenterIssueActionInFlight(
                        safetyCenterIssueActionId, safetySourceIssue, result);
        if (dataUpdated) {
            mSafetyCenterIssueRepository.updateIssues(
                    safetyCenterIssueActionId.getSafetyCenterIssueKey().getUserId());
        }

        return dataUpdated;
    }

    /** Clears all data related to the given {@code userId}. */
    public void clearForUser(@UserIdInt int userId) {
        mSafetySourceDataRepository.clearForUser(userId);
        mSafetyCenterInFlightIssueActionRepository.clearForUser(userId);
        mSafetyCenterIssueDismissalRepository.clearForUser(userId);
        mSafetyCenterIssueRepository.clearForUser(userId);
    }

    /** Clears all stored data. */
    public void clear() {
        mSafetySourceDataRepository.clear();
        mSafetyCenterIssueDismissalRepository.clear();
        mSafetyCenterInFlightIssueActionRepository.clear();
        mSafetyCenterIssueRepository.clear();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   SafetyCenterIssueDismissalRepository /////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

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
            SafetyCenterIssueKey safetyCenterIssueKey,
            @SafetySourceData.SeverityLevel int safetySourceIssueSeverityLevel) {
        return mSafetyCenterIssueDismissalRepository.isIssueDismissed(
                safetyCenterIssueKey, safetySourceIssueSeverityLevel);
    }

    /**
     * Returns the {@link Instant} when the notification for the issue with the given key was last
     * dismissed.
     */
    @Nullable
    public Instant getNotificationDismissedAt(SafetyCenterIssueKey safetyCenterIssueKey) {
        return mSafetyCenterIssueDismissalRepository.getNotificationDismissedAt(
                safetyCenterIssueKey);
    }

    /**
     * Load available persisted data state into memory.
     *
     * <p>Note: only some pieces of the data can be persisted, the rest won't be loaded.
     */
    public void loadPersistableDataStateFromFile() {
        mSafetyCenterIssueDismissalRepository.loadStateFromFile();
    }

    /**
     * Returns the {@link Instant} when the issue with the given key was first reported to Safety
     * Center.
     */
    @Nullable
    public Instant getIssueFirstSeenAt(SafetyCenterIssueKey safetyCenterIssueKey) {
        return mSafetyCenterIssueDismissalRepository.getIssueFirstSeenAt(safetyCenterIssueKey);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   SafetyCenterIssueRepository  /////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Fetches a list of issues related to the given {@link UserProfileGroup}.
     *
     * <p>Issues in the list are sorted in descending order and deduplicated (if applicable, only on
     * Android U+).
     *
     * <p>Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    public List<SafetySourceIssueInfo> getIssuesDedupedSortedDescFor(
            UserProfileGroup userProfileGroup) {
        return mSafetyCenterIssueRepository.getIssuesDedupedSortedDescFor(userProfileGroup);
    }

    /**
     * Counts the total number of issues from loggable sources, in the given {@link
     * UserProfileGroup}.
     *
     * <p>Only includes issues related to active/running {@code userId}s in the given {@link
     * UserProfileGroup}.
     */
    public int countLoggableIssuesFor(UserProfileGroup userProfileGroup) {
        return mSafetyCenterIssueRepository.countLoggableIssuesFor(userProfileGroup);
    }

    /** Gets an unmodifiable list of all issues for the given {@code userId}. */
    public List<SafetySourceIssueInfo> getIssuesForUser(@UserIdInt int userId) {
        return mSafetyCenterIssueRepository.getIssuesForUser(userId);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   SafetyCenterInFlightIssueActionRepository ////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Returns {@code true} if the given issue action is in flight. */
    public boolean actionIsInFlight(SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return mSafetyCenterInFlightIssueActionRepository.actionIsInFlight(
                safetyCenterIssueActionId);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////   SafetySourceDataRepository ///////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

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
    public SafetySourceData getSafetySourceData(
            String safetySourceId, String packageName, @UserIdInt int userId) {
        return mSafetySourceDataRepository.getSafetySourceData(safetySourceId, packageName, userId);
    }

    /**
     * Returns the latest {@link SafetySourceData} that was set by {@link #setSafetySourceData} for
     * the given {@link SafetySourceKey}.
     *
     * <p>This method does not perform any validation, {@link #getSafetySourceData(String, String,
     * int)} should be called wherever validation is required.
     *
     * <p>Returns {@code null} if it was never set since boot, or if the entry was evicted using
     * {@link #setSafetySourceData} with a {@code null} value.
     */
    @Nullable
    public SafetySourceData getSafetySourceDataInternal(SafetySourceKey safetySourceKey) {
        return mSafetySourceDataRepository.getSafetySourceDataInternal(safetySourceKey);
    }

    /** Returns {@code true} if the given source has an error. */
    public boolean sourceHasError(SafetySourceKey safetySourceKey) {
        return mSafetySourceDataRepository.sourceHasError(safetySourceKey);
    }

    /**
     * Returns the {@link SafetySourceIssue} associated with the given {@link SafetyCenterIssueKey}.
     *
     * <p>Returns {@code null} if there is no such {@link SafetySourceIssue}, or if it's been
     * dismissed.
     */
    @Nullable
    public SafetySourceIssue getSafetySourceIssue(SafetyCenterIssueKey safetyCenterIssueKey) {
        return mSafetySourceDataRepository.getSafetySourceIssue(safetyCenterIssueKey);
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
    public SafetySourceIssue.Action getSafetySourceIssueAction(
            SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return mSafetySourceDataRepository.getSafetySourceIssueAction(safetyCenterIssueActionId);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////  Other   /////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /** Dumps state for debugging purposes. */
    public void dump(FileDescriptor fd, PrintWriter fout) {
        mSafetySourceDataRepository.dump(fout);
        mSafetyCenterIssueDismissalRepository.dump(fd, fout);
        mSafetyCenterInFlightIssueActionRepository.dump(fout);
        mSafetyCenterIssueRepository.dump(fout);
    }
}
