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

import static com.android.safetycenter.logging.SafetyCenterStatsdLogger.toSystemEventResult;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
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

    private final ArrayMap<SafetySourceKey, SafetySourceData> mSafetySourceDataForKey =
            new ArrayMap<>();

    private final ArraySet<SafetySourceKey> mSafetySourceErrors = new ArraySet<>();

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
    boolean setSafetySourceData(
            @Nullable SafetySourceData safetySourceData,
            String safetySourceId,
            SafetyEvent safetyEvent,
            String packageName,
            @UserIdInt int userId) {
        if (!validateRequest(safetySourceData, safetySourceId, packageName, userId)) {
            return false;
        }
        boolean safetyEventChangedSafetyCenterData =
                processSafetyEvent(safetySourceId, safetyEvent, userId, false);

        SafetySourceKey key = SafetySourceKey.of(safetySourceId, userId);
        boolean removingSafetySourceErrorChangedSafetyCenterData = mSafetySourceErrors.remove(key);
        SafetySourceData existingSafetySourceData = mSafetySourceDataForKey.get(key);
        SafetySourceData fixedSafetySourceData =
                AndroidLockScreenFix.maybeOverrideSafetySourceData(
                        mContext, safetySourceId, safetySourceData);
        if (Objects.equals(fixedSafetySourceData, existingSafetySourceData)) {
            return safetyEventChangedSafetyCenterData
                    || removingSafetySourceErrorChangedSafetyCenterData;
        }

        ArraySet<String> issueIds = new ArraySet<>();
        if (fixedSafetySourceData == null) {
            mSafetySourceDataForKey.remove(key);
        } else {
            mSafetySourceDataForKey.put(key, fixedSafetySourceData);
            for (int i = 0; i < fixedSafetySourceData.getIssues().size(); i++) {
                issueIds.add(fixedSafetySourceData.getIssues().get(i).getId());
            }
        }
        mSafetyCenterIssueDismissalRepository.updateIssuesForSource(
                issueIds, safetySourceId, userId);

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

    /**
     * Marks the given {@link SafetySourceKey} as having errored-out and returns whether there was a
     * change to the underlying {@link SafetyCenterData}.
     */
    boolean setSafetySourceError(SafetySourceKey safetySourceKey) {
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

    /** Clears all {@link SafetySourceData}, errors, issues and in flight actions for all users. */
    void clear() {
        mSafetySourceDataForKey.clear();
        mSafetySourceErrors.clear();
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
    }

    /** Dumps state for debugging purposes. */
    void dump(PrintWriter fout) {
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
    }

    /** Returns {@code true} if the given source has an error. */
    boolean sourceHasError(SafetySourceKey safetySourceKey) {
        return mSafetySourceErrors.contains(safetySourceKey);
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
                mSafetyCenterConfigReader.getExternalSafetySource(safetySourceId);
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

        return mSafetyCenterConfigReader.isExternalSafetySourceActive(safetySourceId);
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
}
