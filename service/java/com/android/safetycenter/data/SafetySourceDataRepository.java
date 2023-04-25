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

import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__DATA_PROVIDED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__NO_DATA_PROVIDED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__REFRESH_TIMEOUT;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_CLEARED;
import static com.android.permission.PermissionStatsLog.SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_ERROR;
import static com.android.safetycenter.logging.SafetyCenterStatsdLogger.toSystemEventResult;

import static java.util.Collections.emptyList;

import android.annotation.Nullable;
import android.annotation.UptimeMillisLong;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
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

import com.android.modules.utils.build.SdkLevel;
import com.android.permission.util.UserUtils;
import com.android.safetycenter.SafetyCenterConfigReader;
import com.android.safetycenter.SafetyCenterFlags;
import com.android.safetycenter.SafetyCenterRefreshTracker;
import com.android.safetycenter.SafetySourceKey;
import com.android.safetycenter.SafetySources;
import com.android.safetycenter.UserProfileGroup;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;

import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Repository for {@link SafetySourceData} and other data managed by Safety Center including {@link
 * SafetySourceErrorDetails}.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetySourceDataRepository {

    private static final String TAG = "SafetySourceDataRepo";

    private final ArrayMap<SafetySourceKey, SafetySourceData> mSafetySourceData = new ArrayMap<>();
    private final ArraySet<SafetySourceKey> mSafetySourceErrors = new ArraySet<>();
    private final ArrayMap<SafetySourceKey, Long> mSafetySourceLastUpdated = new ArrayMap<>();
    private final ArrayMap<SafetySourceKey, Integer> mSourceStates = new ArrayMap<>();

    private final Context mContext;
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;
    private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;
    private final SafetyCenterInFlightIssueActionRepository
            mSafetyCenterInFlightIssueActionRepository;
    private final SafetyCenterIssueDismissalRepository mSafetyCenterIssueDismissalRepository;
    private final PackageManager mPackageManager;

    SafetySourceDataRepository(
            Context context,
            SafetyCenterConfigReader safetyCenterConfigReader,
            SafetyCenterRefreshTracker safetyCenterRefreshTracker,
            SafetyCenterInFlightIssueActionRepository safetyCenterInFlightIssueActionRepository,
            SafetyCenterIssueDismissalRepository safetyCenterIssueDismissalRepository) {
        mContext = context;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mSafetyCenterRefreshTracker = safetyCenterRefreshTracker;
        mSafetyCenterInFlightIssueActionRepository = safetyCenterInFlightIssueActionRepository;
        mSafetyCenterIssueDismissalRepository = safetyCenterIssueDismissalRepository;
        mPackageManager = mContext.getPackageManager();
    }

    /**
     * Sets the latest {@link SafetySourceData} for the given {@code safetySourceId}, {@link
     * SafetyEvent}, {@code packageName} and {@code userId}, and returns {@code true} if this caused
     * any changes which would alter {@link SafetyCenterData}.
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
    boolean setSafetySourceData(
            @Nullable SafetySourceData safetySourceData,
            String safetySourceId,
            SafetyEvent safetyEvent,
            String packageName,
            @UserIdInt int userId) {
        if (!validateRequest(safetySourceData, safetySourceId, packageName, userId)) {
            return false;
        }
        SafetySourceKey key = SafetySourceKey.of(safetySourceId, userId);
        safetySourceData =
                AndroidLockScreenFix.maybeOverrideSafetySourceData(
                        mContext, safetySourceId, safetySourceData);

        // Must fetch refresh reason before calling processSafetyEvent because the latter may
        // complete and clear the current refresh.
        // TODO(b/277174417): Restructure this code to avoid this error-prone sequencing concern
        Integer refreshReason = null;
        if (safetyEvent.getType() == SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED) {
            refreshReason = mSafetyCenterRefreshTracker.getRefreshReason();
        }

        boolean sourceDataDiffers = !Objects.equals(safetySourceData, mSafetySourceData.get(key));
        boolean eventCausedChange =
                processSafetyEvent(safetySourceId, safetyEvent, userId, false, sourceDataDiffers);
        boolean removedSourceError = mSafetySourceErrors.remove(key);

        if (sourceDataDiffers) {
            setSafetySourceDataInternal(key, safetySourceData);
        }

        setLastUpdatedNow(key);
        logSafetySourceStateCollected(
                key, userId, safetySourceData, refreshReason, sourceDataDiffers);

        return sourceDataDiffers || eventCausedChange || removedSourceError;
    }

    private void setSafetySourceDataInternal(SafetySourceKey key, SafetySourceData data) {
        ArraySet<String> issueIds = new ArraySet<>();
        if (data == null) {
            mSafetySourceData.remove(key);
            mSourceStates.put(key, SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_CLEARED);
        } else {
            mSafetySourceData.put(key, data);
            for (int i = 0; i < data.getIssues().size(); i++) {
                issueIds.add(data.getIssues().get(i).getId());
            }
            mSourceStates.put(key, SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__DATA_PROVIDED);
        }
        mSafetyCenterIssueDismissalRepository.updateIssuesForSource(
                issueIds, key.getSourceId(), key.getUserId());
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
            String safetySourceId, String packageName, @UserIdInt int userId) {
        if (!validateRequest(null, safetySourceId, packageName, userId)) {
            return null;
        }
        return getSafetySourceDataInternal(SafetySourceKey.of(safetySourceId, userId));
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
    SafetySourceData getSafetySourceDataInternal(SafetySourceKey safetySourceKey) {
        return mSafetySourceData.get(safetySourceKey);
    }

    /** Returns {@code true} if the given source has an error. */
    boolean sourceHasError(SafetySourceKey safetySourceKey) {
        return mSafetySourceErrors.contains(safetySourceKey);
    }

    /**
     * Reports the given {@link SafetySourceErrorDetails} for the given {@code safetySourceId} and
     * {@code userId}, and returns {@code true} if this changed the repository's data.
     *
     * <p>Throws if the request is invalid based on the {@link SafetyCenterConfig}: the given {@code
     * safetySourceId}, {@code packageName} and/or {@code userId} are unexpected.
     */
    boolean reportSafetySourceError(
            SafetySourceErrorDetails safetySourceErrorDetails,
            String safetySourceId,
            String packageName,
            @UserIdInt int userId) {
        if (!validateRequest(null, safetySourceId, packageName, userId)) {
            return false;
        }
        SafetyEvent safetyEvent = safetySourceErrorDetails.getSafetyEvent();
        Log.w(TAG, "Error reported from source: " + safetySourceId + ", for event: " + safetyEvent);

        boolean safetyEventChangedSafetyCenterData =
                processSafetyEvent(safetySourceId, safetyEvent, userId, true, false);
        int safetyEventType = safetyEvent.getType();
        if (safetyEventType == SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED
                || safetyEventType == SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED) {
            return safetyEventChangedSafetyCenterData;
        }

        SafetySourceKey sourceKey = SafetySourceKey.of(safetySourceId, userId);
        mSourceStates.put(sourceKey, SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__SOURCE_ERROR);
        boolean safetySourceErrorChangedSafetyCenterData = setSafetySourceError(sourceKey);
        return safetyEventChangedSafetyCenterData || safetySourceErrorChangedSafetyCenterData;
    }

    /**
     * Marks the given {@link SafetySourceKey} as being in an error state due to a refresh timeout
     * and returns {@code true} if this changed the repository's data.
     */
    boolean markSafetySourceRefreshTimedOut(SafetySourceKey sourceKey) {
        mSourceStates.put(sourceKey, SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__REFRESH_TIMEOUT);
        return setSafetySourceError(sourceKey);
    }

    /**
     * Marks the given {@link SafetySourceKey} as being in an error state and returns {@code true}
     * if this changed the repository's data.
     */
    private boolean setSafetySourceError(SafetySourceKey safetySourceKey) {
        setLastUpdatedNow(safetySourceKey);
        boolean removingSafetySourceDataChangedSafetyCenterData =
                mSafetySourceData.remove(safetySourceKey) != null;
        boolean addingSafetySourceErrorChangedSafetyCenterData =
                mSafetySourceErrors.add(safetySourceKey);
        return removingSafetySourceDataChangedSafetyCenterData
                || addingSafetySourceErrorChangedSafetyCenterData;
    }

    /**
     * Clears all safety source errors received so far for the given {@link UserProfileGroup}, this
     * is useful e.g. when starting a new broadcast.
     */
    void clearSafetySourceErrors(UserProfileGroup userProfileGroup) {
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetySourceErrors.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceErrors.valueAt(i);
            if (userProfileGroup.contains(sourceKey.getUserId())) {
                mSafetySourceErrors.removeAt(i);
            }
        }
    }

    /**
     * Returns the {@link SafetySourceIssue} associated with the given {@link SafetyCenterIssueKey}.
     *
     * <p>Returns {@code null} if there is no such {@link SafetySourceIssue}.
     */
    @Nullable
    SafetySourceIssue getSafetySourceIssue(SafetyCenterIssueKey safetyCenterIssueKey) {
        SafetySourceKey key =
                SafetySourceKey.of(
                        safetyCenterIssueKey.getSafetySourceId(), safetyCenterIssueKey.getUserId());
        SafetySourceData safetySourceData = mSafetySourceData.get(key);
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

        return targetIssue;
    }

    /**
     * Returns the {@link SafetySourceIssue.Action} associated with the given {@link
     * SafetyCenterIssueActionId}.
     *
     * <p>Returns {@code null} if there is no associated {@link SafetySourceIssue}.
     *
     * <p>Returns {@code null} if the {@link SafetySourceIssue.Action} is currently in flight.
     */
    @Nullable
    SafetySourceIssue.Action getSafetySourceIssueAction(
            SafetyCenterIssueActionId safetyCenterIssueActionId) {
        SafetySourceIssue safetySourceIssue =
                getSafetySourceIssue(safetyCenterIssueActionId.getSafetyCenterIssueKey());

        if (safetySourceIssue == null) {
            return null;
        }

        return mSafetyCenterInFlightIssueActionRepository.getSafetySourceIssueAction(
                safetyCenterIssueActionId, safetySourceIssue);
    }

    /**
     * Returns the elapsed realtime millis of when the data of the given {@link SafetySourceKey} was
     * last updated, or {@code 0L} if no update has occurred.
     *
     * @see SystemClock#elapsedRealtime()
     */
    @UptimeMillisLong
    long getSafetySourceLastUpdated(SafetySourceKey sourceKey) {
        Long lastUpdated = mSafetySourceLastUpdated.get(sourceKey);
        if (lastUpdated != null) {
            return lastUpdated;
        } else {
            return 0L;
        }
    }

    private void setLastUpdatedNow(SafetySourceKey sourceKey) {
        mSafetySourceLastUpdated.put(sourceKey, SystemClock.elapsedRealtime());
    }

    /**
     * Returns the current {@link SafetyCenterStatsdLogger.SourceState} of the given {@link
     * SafetySourceKey}.
     */
    @SafetyCenterStatsdLogger.SourceState
    int getSourceState(SafetySourceKey sourceKey) {
        Integer sourceState = mSourceStates.get(sourceKey);
        if (sourceState != null) {
            return sourceState;
        } else {
            return SAFETY_SOURCE_STATE_COLLECTED__SOURCE_STATE__NO_DATA_PROVIDED;
        }
    }

    /** Clears all data for all users. */
    void clear() {
        mSafetySourceData.clear();
        mSafetySourceErrors.clear();
        mSafetySourceLastUpdated.clear();
        mSourceStates.clear();
    }

    /** Clears all data for the given user. */
    void clearForUser(@UserIdInt int userId) {
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetySourceData.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceData.keyAt(i);
            if (sourceKey.getUserId() == userId) {
                mSafetySourceData.removeAt(i);
            }
        }
        for (int i = mSafetySourceErrors.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceErrors.valueAt(i);
            if (sourceKey.getUserId() == userId) {
                mSafetySourceErrors.removeAt(i);
            }
        }
        for (int i = mSafetySourceLastUpdated.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSafetySourceLastUpdated.keyAt(i);
            if (sourceKey.getUserId() == userId) {
                mSafetySourceLastUpdated.removeAt(i);
            }
        }
        for (int i = mSourceStates.size() - 1; i >= 0; i--) {
            SafetySourceKey sourceKey = mSourceStates.keyAt(i);
            if (sourceKey.getUserId() == userId) {
                mSourceStates.removeAt(i);
            }
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(PrintWriter fout) {
        dumpArrayMap(fout, mSafetySourceData, "SOURCE DATA");
        int errorCount = mSafetySourceErrors.size();
        fout.println("SOURCE ERRORS (" + errorCount + ")");
        for (int i = 0; i < errorCount; i++) {
            SafetySourceKey key = mSafetySourceErrors.valueAt(i);
            fout.println("\t[" + i + "] " + key);
        }
        dumpArrayMap(fout, mSafetySourceLastUpdated, "LAST UPDATED");
        dumpArrayMap(fout, mSourceStates, "SOURCE STATES");
        fout.println();
    }

    private static <K, V> void dumpArrayMap(PrintWriter fout, ArrayMap<K, V> map, String label) {
        int count = map.size();
        fout.println(label + " (" + count + ")");
        for (int i = 0; i < count; i++) {
            fout.println("\t[" + i + "] " + map.keyAt(i) + " -> " + map.valueAt(i));
        }
    }

    /**
     * Checks if a request to the SafetyCenter is valid, and returns whether the request should be
     * processed.
     */
    private boolean validateRequest(
            @Nullable SafetySourceData safetySourceData,
            String safetySourceId,
            String packageName,
            @UserIdInt int userId) {
        SafetyCenterConfigReader.ExternalSafetySource externalSafetySource =
                mSafetyCenterConfigReader.getExternalSafetySource(safetySourceId, packageName);
        if (externalSafetySource == null) {
            throw new IllegalArgumentException("Unexpected safety source: " + safetySourceId);
        }

        SafetySource safetySource = externalSafetySource.getSafetySource();
        validateCallingPackage(safetySource, packageName, safetySourceId);

        if (UserUtils.isManagedProfile(userId, mContext)
                && !SafetySources.supportsManagedProfiles(safetySource)) {
            throw new IllegalArgumentException(
                    "Unexpected managed profile request for safety source: " + safetySourceId);
        }

        boolean retrievingOrClearingData = safetySourceData == null;
        if (retrievingOrClearingData) {
            return mSafetyCenterConfigReader.isExternalSafetySourceActive(
                    safetySourceId, packageName);
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

            if (externalSafetySource.hasEntryInStatelessGroup()
                    && sourceSeverityLevel != SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED) {
                throw new IllegalArgumentException(
                        "Safety source: "
                                + safetySourceId
                                + " is in a stateless group but specified a severity level: "
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

        return mSafetyCenterConfigReader.isExternalSafetySourceActive(safetySourceId, packageName);
    }

    private void validateCallingPackage(
            SafetySource safetySource, String packageName, String safetySourceId) {
        if (!packageName.equals(safetySource.getPackageName())) {
            throw new IllegalArgumentException(
                    "Unexpected package name: "
                            + packageName
                            + ", for safety source: "
                            + safetySourceId);
        }

        if (!SdkLevel.isAtLeastU()) {
            // No more validation checks possible on T devices
            return;
        }

        Set<String> certificateHashes = safetySource.getPackageCertificateHashes();
        if (certificateHashes.isEmpty()) {
            Log.d(TAG, "No cert check requested for package " + packageName);
            return;
        }

        if (!checkCerts(packageName, certificateHashes)
                && !checkCerts(
                        packageName,
                        SafetyCenterFlags.getAdditionalAllowedPackageCerts(packageName))) {
            Log.e(
                    TAG,
                    "Package "
                            + packageName
                            + " for source "
                            + safetySourceId
                            + " signed with invalid signature");
            throw new IllegalArgumentException("Invalid signature for package " + packageName);
        }
    }

    private boolean checkCerts(String packageName, Set<String> certificateHashes) {
        boolean hasMatchingCert = false;
        for (String certHash : certificateHashes) {
            try {
                byte[] certificate = new Signature(certHash).toByteArray();
                if (mPackageManager.hasSigningCertificate(
                        packageName, certificate, PackageManager.CERT_INPUT_SHA256)) {
                    Log.d(TAG, "Package " + packageName + " has expected signature");
                    hasMatchingCert = true;
                }
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Failed to parse signing certificate: " + certHash, e);
                throw new IllegalStateException(
                        "Failed to parse signing certificate: " + certHash, e);
            }
        }
        return hasMatchingCert;
    }

    private boolean processSafetyEvent(
            String safetySourceId,
            SafetyEvent safetyEvent,
            @UserIdInt int userId,
            boolean isError,
            boolean sourceDataChanged) {
        int type = safetyEvent.getType();
        switch (type) {
            case SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED:
                String refreshBroadcastId = safetyEvent.getRefreshBroadcastId();
                if (refreshBroadcastId == null) {
                    Log.w(TAG, "No refresh broadcast id in SafetyEvent of type " + type);
                    return false;
                }
                return mSafetyCenterRefreshTracker.reportSourceRefreshCompleted(
                        refreshBroadcastId, safetySourceId, userId, !isError, sourceDataChanged);
            case SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED:
            case SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED:
                String safetySourceIssueId = safetyEvent.getSafetySourceIssueId();
                if (safetySourceIssueId == null) {
                    Log.w(TAG, "No safety source issue id in SafetyEvent of type " + type);
                    return false;
                }
                String safetySourceIssueActionId = safetyEvent.getSafetySourceIssueActionId();
                if (safetySourceIssueActionId == null) {
                    Log.w(TAG, "No safety source issue action id in SafetyEvent of type " + type);
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
                return mSafetyCenterInFlightIssueActionRepository
                        .unmarkSafetyCenterIssueActionInFlight(
                                safetyCenterIssueActionId,
                                getSafetySourceIssue(safetyCenterIssueKey),
                                result);
            case SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED:
            case SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_LOCALE_CHANGED:
            case SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED:
                return false;
        }
        Log.w(TAG, "Unexpected SafetyEvent.Type: " + type);
        return false;
    }

    private void logSafetySourceStateCollected(
            SafetySourceKey sourceKey,
            @UserIdInt int userId,
            @Nullable SafetySourceData sourceData,
            @Nullable Integer refreshReason,
            boolean sourceDataDiffers) {
        SafetySourceStatus sourceStatus = sourceData == null ? null : sourceData.getStatus();
        List<SafetySourceIssue> sourceIssues =
                sourceData == null ? emptyList() : sourceData.getIssues();

        int maxSeverityLevel = Integer.MIN_VALUE;
        if (sourceStatus != null) {
            maxSeverityLevel = sourceStatus.getSeverityLevel();
        } else if (sourceData != null) {
            // In this case we know we have an issue-only source because of the checks carried out
            // in the validateRequest function.
            maxSeverityLevel = SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED;
        }

        long openIssuesCount = 0;
        long dismissedIssuesCount = 0;
        for (int i = 0; i < sourceIssues.size(); i++) {
            SafetySourceIssue issue = sourceIssues.get(i);
            if (isIssueDismissed(issue, sourceKey.getSourceId(), userId)) {
                dismissedIssuesCount++;
            } else {
                openIssuesCount++;
                maxSeverityLevel = Math.max(maxSeverityLevel, issue.getSeverityLevel());
            }
        }

        // TODO(b/268309211): Log duplicate filtered out issue counts when sources update
        int duplicateFilteredOutIssuesCount = 0;

        SafetyCenterStatsdLogger.writeSafetySourceStateCollectedSourceUpdated(
                sourceKey.getSourceId(),
                UserUtils.isManagedProfile(userId, mContext),
                maxSeverityLevel > Integer.MIN_VALUE ? maxSeverityLevel : null,
                openIssuesCount,
                dismissedIssuesCount,
                duplicateFilteredOutIssuesCount,
                getSourceState(sourceKey),
                refreshReason,
                sourceDataDiffers);
    }

    private boolean isIssueDismissed(
            SafetySourceIssue issue, String sourceId, @UserIdInt int userId) {
        SafetyCenterIssueKey issueKey =
                SafetyCenterIssueKey.newBuilder()
                        .setSafetySourceId(sourceId)
                        .setSafetySourceIssueId(issue.getId())
                        .setUserId(userId)
                        .build();
        return mSafetyCenterIssueDismissalRepository.isIssueDismissed(
                issueKey, issue.getSeverityLevel());
    }
}
