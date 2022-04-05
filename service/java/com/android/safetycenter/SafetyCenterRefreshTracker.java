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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.safetycenter.SafetyCenterManager.RefreshRequestType;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/** A class to store the state of a refresh of safety sources, if any is ongoing. */
@RequiresApi(TIRAMISU)
final class SafetyCenterRefreshTracker {
    private static final String TAG = "SafetyCenterRefreshTrac";
    private final Object mRefreshStateLock = new Object();

    @GuardedBy("mRefreshStateLock")
    @Nullable
    // TODO(b/229060064): Should we allow one refresh at a time per UserProfileGroup?
    private RefreshInProgress mRefreshInProgress = null;

    /** Reports that a new refresh is in progress. */
    void reportRefreshInProgress(
            @NonNull String refreshBroadcastId,
            @RefreshRequestType int requestType,
            @NonNull UserProfileGroup userProfileGroup,
            @NonNull List<SafetyCenterConfigReader.Broadcast> broadcasts) {
        synchronized (mRefreshStateLock) {
            if (mRefreshInProgress != null) {
                Log.w(TAG, "Replacing an ongoing refresh");
            }

            mRefreshInProgress =
                    new RefreshInProgress(refreshBroadcastId, requestType, userProfileGroup);
            for (int i = 0; i < broadcasts.size(); i++) {
                SafetyCenterConfigReader.Broadcast b = broadcasts.get(i);
                for (int j = 0; j < b.getSourceIdsForProfileOwner().size(); j++) {
                    mRefreshInProgress.mSourceToStateMap.put(
                            SafetySourceKey.of(
                                    b.getSourceIdsForProfileOwner().get(j),
                                    userProfileGroup.getProfileOwnerUserId()),
                            STATE_IN_PROGRESS);
                }
                for (int j = 0; j < b.getSourceIdsForManagedProfiles().size(); j++) {
                    for (int k = 0; k < userProfileGroup.getManagedProfilesUserIds().length; k++) {
                        mRefreshInProgress.mSourceToStateMap.put(
                                SafetySourceKey.of(
                                        b.getSourceIdsForManagedProfiles().get(j),
                                        userProfileGroup.getManagedProfilesUserIds()[k]),
                                STATE_IN_PROGRESS);
                    }
                }
            }
        }
    }

    /** Reports that a source has successfully completed its refresh. */
    void reportSourceRefreshCompleted(
            @NonNull String sourceId, @UserIdInt int userId, @NonNull String refreshBroadcastId) {
        synchronized (mRefreshStateLock) {
            if (!checkMethodValid("reportSourceRefreshCompleted", refreshBroadcastId)) {
                return;
            }
            mRefreshInProgress.mSourceToStateMap.put(
                    SafetySourceKey.of(sourceId, userId), STATE_COMPLETED);
            checkIfRefreshFinished();
        }
    }

    /** Reports that a source has failed to complete its refresh. */
    void reportSourceRefreshFailed(
            @NonNull String sourceId, @UserIdInt int userId, @NonNull String refreshBroadcastId) {
        synchronized (mRefreshStateLock) {
            if (!checkMethodValid("reportSourceRefreshFailed", refreshBroadcastId)) {
                return;
            }
            mRefreshInProgress.mSourceToStateMap.put(
                    SafetySourceKey.of(sourceId, userId), STATE_FAILED);
            checkIfRefreshFinished();
        }
    }

    /**
     * Clears any ongoing refresh in progress.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    // TODO(b/229188900): Should we stop any scheduled broadcasts from going out?
    void clearRefresh() {
        synchronized (mRefreshStateLock) {
            if (mRefreshInProgress != null) {
                mRefreshInProgress = null;
            }
        }
    }

    /**
     * Returns a new {@link Runnable} to clear a refresh in progress with the given id, if any is
     * ongoing.
     */
    @NonNull
    Runnable createClearRefreshRunnable(@NonNull String refreshBroadcastId) {
        return new ClearRefreshRunnable(refreshBroadcastId);
    }

    /**
     * Clears the refresh in progress with the given id, if any is ongoing.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    // TODO(b/229188900): Should we stop any scheduled broadcasts from going out?
    private void clearRefresh(@NonNull String refreshBroadcastId) {
        synchronized (mRefreshStateLock) {
            if (!checkMethodValid("clearRefresh", refreshBroadcastId)) {
                return;
            }

            if (mRefreshInProgress != null) {
                mRefreshInProgress = null;
            }
        }
    }

    @GuardedBy("mRefreshStateLock")
    private void checkIfRefreshFinished() {
        for (int i = 0; i < mRefreshInProgress.mSourceToStateMap.size(); i++) {
            if (mRefreshInProgress.mSourceToStateMap.valueAt(i).equals(STATE_IN_PROGRESS)) {
                return;
            }
        }
        mRefreshInProgress = null;
    }

    @GuardedBy("mRefreshStateLock")
    private boolean checkMethodValid(
            @NonNull String methodName, @NonNull String refreshBroadcastId) {
        if (mRefreshInProgress == null || !mRefreshInProgress.getId().equals(refreshBroadcastId)) {
            Log.w(
                    TAG,
                    String.format(
                            "%s called for invalid refresh broadcast id: %s; no such refresh in"
                                    + " progress",
                            methodName, refreshBroadcastId));
            return false;
        }
        return true;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = "STATE_",
            value = {
                STATE_IN_PROGRESS,
                STATE_COMPLETED,
                STATE_FAILED,
            })
    /** State of refresh for a safety source. */
    @interface State {}

    /** A refresh for the source is in progress. */
    static final int STATE_IN_PROGRESS = 1;
    /** A refresh for the source has completed successfully. */
    static final int STATE_COMPLETED = 2;
    /** A refresh for the source has failed. */
    static final int STATE_FAILED = 3;

    /** Class representing the state of a refresh in progress. */
    private static final class RefreshInProgress {
        @NonNull private final String mId;
        @RefreshRequestType private final int mRequestType;
        @NonNull private final UserProfileGroup mUserProfileGroup;

        @NonNull
        private final ArrayMap<SafetySourceKey, Integer> mSourceToStateMap = new ArrayMap<>();

        /** Creates a {@link RefreshInProgress}. */
        RefreshInProgress(
                @NonNull String id,
                @RefreshRequestType int requestType,
                @NonNull UserProfileGroup userProfileGroup) {
            this.mId = id;
            this.mRequestType = requestType;
            this.mUserProfileGroup = userProfileGroup;
        }

        /**
         * Returns the id of the {@link RefreshInProgress}, which corresponds to the {@link
         * android.safetycenter.SafetyCenterManager#EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID} used
         * in the refresh.
         */
        @NonNull
        String getId() {
            return mId;
        }

        /**
         * Returns the request type of the {@link RefreshInProgress}, which corresponds to the
         * {@link
         * android.safetycenter.SafetyCenterManager#EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE} used
         * in the refresh.
         */
        @RefreshRequestType
        int getRequestType() {
            return mRequestType;
        }

        /** Returns the {@link UserProfileGroup} active for the refresh. */
        @NonNull
        UserProfileGroup getUserProfileGroup() {
            return mUserProfileGroup;
        }
    }

    /** A {@link Runnable} to run {@link #clearRefresh(String) . */
    private final class ClearRefreshRunnable implements Runnable {
        @NonNull private final String mRefreshBroadcastId;

        /** Creates a {@link ClearRefreshRunnable}. */
        private ClearRefreshRunnable(@NonNull String refreshBroadcastId) {
            this.mRefreshBroadcastId = refreshBroadcastId;
        }

        @Override
        public void run() {
            clearRefresh(mRefreshBroadcastId);
        }
    }
}
