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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.SafetyCenterConfigReader.Broadcast;

import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class to store the state of a refresh of safety sources, if any is ongoing.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterRefreshTracker {
    private static final String TAG = "SafetyCenterRefreshTrac";

    @NonNull private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    @Nullable
    // TODO(b/229060064): Should we allow one refresh at a time per UserProfileGroup rather than
    //  one global refresh?
    private RefreshInProgress mRefreshInProgress = null;

    /**
     * Creates a {@link SafetyCenterRefreshTracker} using the given {@link
     * SafetyCenterConfigReader}.
     */
    SafetyCenterRefreshTracker(@NonNull SafetyCenterConfigReader safetyCenterConfigReader) {
        mSafetyCenterConfigReader = safetyCenterConfigReader;
    }

    private int mRefreshCounter = 0;

    /**
     * Reports that a new refresh is in progress and returns the broadcast id associated with this
     * refresh.
     */
    @NonNull
    String reportRefreshInProgress(
            @RefreshReason int refreshReason, @NonNull UserProfileGroup userProfileGroup) {
        if (mRefreshInProgress != null) {
            Log.w(TAG, "Replacing an ongoing refresh");
        }

        List<Broadcast> broadcasts = mSafetyCenterConfigReader.getBroadcasts();
        String refreshBroadcastId =
                Objects.hash(refreshReason, broadcasts, userProfileGroup) + "_" + mRefreshCounter++;
        mRefreshInProgress = new RefreshInProgress(refreshBroadcastId);

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);
            List<String> profileOwnerSourceIds =
                    broadcast.getSourceIdsForProfileOwner(refreshReason);
            for (int j = 0; j < profileOwnerSourceIds.size(); j++) {
                mRefreshInProgress.addSourceRefreshInFlight(
                        SafetySourceKey.of(
                                profileOwnerSourceIds.get(j),
                                userProfileGroup.getProfileOwnerUserId()));
            }
            List<String> managedProfilesSourceIds =
                    broadcast.getSourceIdsForManagedProfiles(refreshReason);
            for (int j = 0; j < managedProfilesSourceIds.size(); j++) {
                int[] managedProfilesUserIds = userProfileGroup.getManagedProfilesUserIds();
                for (int k = 0; k < managedProfilesUserIds.length; k++) {
                    mRefreshInProgress.addSourceRefreshInFlight(
                            SafetySourceKey.of(
                                    managedProfilesSourceIds.get(j), managedProfilesUserIds[k]));
                }
            }
        }

        return refreshBroadcastId;
    }

    /**
     * Reports that a source has completed its refresh, and returns whether this caused the refresh
     * to complete.
     *
     * <p>If a source calls {@code reportSafetySourceError}, then this method is also used to mark
     * the refresh as completed.
     */
    boolean reportSourceRefreshCompleted(
            @NonNull String sourceId, @NonNull String refreshBroadcastId, @UserIdInt int userId) {
        if (!checkMethodValid("reportSourceRefreshCompleted", refreshBroadcastId)) {
            return false;
        }

        mRefreshInProgress.markSourceRefreshAsComplete(SafetySourceKey.of(sourceId, userId));
        if (!mRefreshInProgress.isComplete()) {
            return false;
        }

        mRefreshInProgress = null;
        return true;
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
        if (mRefreshInProgress != null) {
            mRefreshInProgress = null;
        }
    }

    /**
     * Clears the refresh in progress with the given id, and returns whether it was ongoing.
     *
     * <p>Note that this method simply clears the tracking of a refresh, and does not prevent
     * scheduled broadcasts being sent by {@link
     * android.safetycenter.SafetyCenterManager#refreshSafetySources}.
     */
    // TODO(b/229188900): Should we stop any scheduled broadcasts from going out?
    boolean clearRefresh(@NonNull String refreshBroadcastId) {
        if (!checkMethodValid("clearRefresh", refreshBroadcastId)) {
            return false;
        }

        mRefreshInProgress = null;
        return true;
    }

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

    /** Class representing the state of a refresh in progress. */
    private static final class RefreshInProgress {
        @NonNull private final String mId;

        @NonNull private final ArraySet<SafetySourceKey> mSourceRefreshInFlight = new ArraySet<>();

        /** Creates a {@link RefreshInProgress}. */
        RefreshInProgress(@NonNull String id) {
            mId = id;
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

        private void addSourceRefreshInFlight(@NonNull SafetySourceKey safetySourceKey) {
            mSourceRefreshInFlight.add(safetySourceKey);
        }

        private void markSourceRefreshAsComplete(@NonNull SafetySourceKey safetySourceKey) {
            mSourceRefreshInFlight.remove(safetySourceKey);
        }

        private boolean isComplete() {
            return mSourceRefreshInFlight.isEmpty();
        }
    }
}
