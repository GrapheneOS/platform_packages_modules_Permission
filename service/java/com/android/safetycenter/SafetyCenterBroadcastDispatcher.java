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

import static android.Manifest.permission.READ_SAFETY_CENTER_STATUS;
import static android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.PowerExemptionManager.REASON_REFRESH_SAFETY_SOURCES;
import static android.os.PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED;
import static android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES;
import static android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_LOCALE_CHANGE;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_REBOOT;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_SAFETY_CENTER_ENABLED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.safetycenter.SafetyCenterManager.RefreshRequestType;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.SafetyCenterConfigReader.Broadcast;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class that dispatches SafetyCenter broadcasts.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterBroadcastDispatcher {
    private static final String TAG = "SafetyCenterBroadcastDi";

    @NonNull private final Context mContext;
    @NonNull private final SafetyCenterConfigReader mSafetyCenterConfigReader;
    @NonNull private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;

    /**
     * Creates a {@link SafetyCenterBroadcastDispatcher} using the given {@link Context}, {@link
     * SafetyCenterConfigReader} and {@link SafetyCenterRefreshTracker}.
     */
    SafetyCenterBroadcastDispatcher(
            @NonNull Context context,
            @NonNull SafetyCenterConfigReader safetyCenterConfigReader,
            @NonNull SafetyCenterRefreshTracker safetyCenterRefreshTracker) {
        mContext = context;
        mSafetyCenterConfigReader = safetyCenterConfigReader;
        mSafetyCenterRefreshTracker = safetyCenterRefreshTracker;
    }

    /**
     * Triggers a refresh of safety sources by sending them broadcasts with action {@link
     * SafetyCenterManager#ACTION_REFRESH_SAFETY_SOURCES}, and returns the associated broadcast id.
     *
     * <p>Returns {@code null} if no broadcast was sent.
     */
    @Nullable
    String sendRefreshSafetySources(
            @RefreshReason int refreshReason, @NonNull UserProfileGroup userProfileGroup) {
        List<Broadcast> broadcasts = mSafetyCenterConfigReader.getBroadcasts();
        BroadcastOptions broadcastOptions = createBroadcastOptions();

        String broadcastId =
                mSafetyCenterRefreshTracker.reportRefreshInProgress(
                        refreshReason, userProfileGroup);
        boolean hasSentAtLeastOneBroadcast = false;

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);

            hasSentAtLeastOneBroadcast |=
                    sendRefreshSafetySourcesBroadcast(
                            broadcast,
                            broadcastOptions,
                            refreshReason,
                            userProfileGroup,
                            broadcastId);
        }

        if (!hasSentAtLeastOneBroadcast) {
            mSafetyCenterRefreshTracker.clearRefresh(broadcastId);
            return null;
        }

        return broadcastId;
    }

    private boolean sendRefreshSafetySourcesBroadcast(
            @NonNull Broadcast broadcast,
            @NonNull BroadcastOptions broadcastOptions,
            @RefreshReason int refreshReason,
            @NonNull UserProfileGroup userProfileGroup,
            @NonNull String broadcastId) {
        boolean hasSentAtLeastOneBroadcast = false;
        int requestType = toRefreshRequestType(refreshReason);
        String packageName = broadcast.getPackageName();
        Set<String> deniedSourceIds = getRefreshDeniedSourceIds(refreshReason);
        SparseArray<List<String>> userIdsToSourceIds =
                getUserIdsToSourceIds(broadcast, userProfileGroup, refreshReason);

        for (int i = 0; i < userIdsToSourceIds.size(); i++) {
            int userId = userIdsToSourceIds.keyAt(i);
            List<String> sourceIds = userIdsToSourceIds.valueAt(i);

            if (!deniedSourceIds.isEmpty()) {
                sourceIds = new ArrayList<>(sourceIds);
                sourceIds.removeAll(deniedSourceIds);
            }

            if (sourceIds.isEmpty()) {
                continue;
            }

            Intent intent = createRefreshIntent(requestType, packageName, sourceIds, broadcastId);
            boolean broadcastWasSent =
                    sendBroadcastIfResolves(intent, UserHandle.of(userId), broadcastOptions);
            if (broadcastWasSent) {
                mSafetyCenterRefreshTracker.reportSourceRefreshesInFlight(
                        broadcastId, sourceIds, userId);
            }
            hasSentAtLeastOneBroadcast |= broadcastWasSent;
        }

        return hasSentAtLeastOneBroadcast;
    }

    /**
     * Triggers an {@link SafetyCenterManager#ACTION_SAFETY_CENTER_ENABLED_CHANGED} broadcast for
     * all safety sources.
     *
     * <p>This method also sends an implicit broadcast globally (which requires the {@link
     * android.Manifest.permission#READ_SAFETY_CENTER_STATUS} permission).
     */
    // TODO(b/227310195): Consider adding a boolean extra to the intent instead of having clients
    //  rely on SafetyCenterManager#isSafetyCenterEnabled()?
    void sendEnabledChanged() {
        List<Broadcast> broadcasts = mSafetyCenterConfigReader.getBroadcasts();
        BroadcastOptions broadcastOptions = createBroadcastOptions();
        List<UserProfileGroup> userProfileGroups =
                UserProfileGroup.getAllUserProfileGroups(mContext);

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);

            sendEnabledChangedBroadcast(broadcast, broadcastOptions, userProfileGroups);
        }

        Intent implicitIntent = createImplicitEnabledChangedIntent();
        sendBroadcast(implicitIntent, UserHandle.SYSTEM, READ_SAFETY_CENTER_STATUS, null);
    }

    private void sendEnabledChangedBroadcast(
            @NonNull Broadcast broadcast,
            @NonNull BroadcastOptions broadcastOptions,
            @NonNull List<UserProfileGroup> userProfileGroups) {
        Intent intent = createExplicitEnabledChangedIntent(broadcast.getPackageName());
        // The same ENABLED reason is used here for both enable and disable events. It is not sent
        // externally and is only used internally to filter safety sources in the methods of the
        // Broadcast class.
        int refreshReason = REFRESH_REASON_SAFETY_CENTER_ENABLED;

        for (int i = 0; i < userProfileGroups.size(); i++) {
            UserProfileGroup userProfileGroup = userProfileGroups.get(i);
            SparseArray<List<String>> userIdsToSourceIds =
                    getUserIdsToSourceIds(broadcast, userProfileGroup, refreshReason);

            for (int j = 0; j < userIdsToSourceIds.size(); j++) {
                int userId = userIdsToSourceIds.keyAt(j);

                sendBroadcastIfResolves(intent, UserHandle.of(userId), broadcastOptions);
            }
        }
    }

    private boolean sendBroadcastIfResolves(
            @NonNull Intent intent,
            @NonNull UserHandle userHandle,
            @Nullable BroadcastOptions broadcastOptions) {
        if (!doesBroadcastResolve(intent)) {
            Log.w(TAG, "No receiver for intent targeting " + intent.getPackage());
            return false;
        }
        Log.v(TAG, "Found receiver for intent targeting " + intent.getPackage());
        sendBroadcast(intent, userHandle, SEND_SAFETY_CENTER_UPDATE, broadcastOptions);
        return true;
    }

    private void sendBroadcast(
            @NonNull Intent intent,
            @NonNull UserHandle userHandle,
            @NonNull String permission,
            @Nullable BroadcastOptions broadcastOptions) {
        // The following operation requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(
                    intent,
                    userHandle,
                    permission,
                    broadcastOptions == null ? null : broadcastOptions.toBundle());
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private boolean doesBroadcastResolve(@NonNull Intent broadcastIntent) {
        return !mContext.getPackageManager().queryBroadcastReceivers(broadcastIntent, 0).isEmpty();
    }

    @NonNull
    private static Intent createExplicitEnabledChangedIntent(@NonNull String packageName) {
        return createImplicitEnabledChangedIntent().setPackage(packageName);
    }

    @NonNull
    private static Intent createImplicitEnabledChangedIntent() {
        return createBroadcastIntent(ACTION_SAFETY_CENTER_ENABLED_CHANGED);
    }

    @NonNull
    private static Intent createRefreshIntent(
            @RefreshRequestType int requestType,
            @NonNull String packageName,
            @NonNull List<String> sourceIdsToRefresh,
            @NonNull String broadcastId) {
        String[] sourceIdsArray = sourceIdsToRefresh.toArray(new String[0]);
        return createBroadcastIntent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, requestType)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS, sourceIdsArray)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, broadcastId)
                .setPackage(packageName);
    }

    @NonNull
    private static Intent createBroadcastIntent(@NonNull String intentAction) {
        return new Intent(intentAction).setFlags(FLAG_RECEIVER_FOREGROUND);
    }

    @NonNull
    private static BroadcastOptions createBroadcastOptions() {
        BroadcastOptions broadcastOptions = BroadcastOptions.makeBasic();
        Duration allowListDuration = SafetyCenterFlags.getFgsAllowlistDuration();
        // The following operation requires the START_FOREGROUND_SERVICES_FROM_BACKGROUND.
        final long callingId = Binder.clearCallingIdentity();
        try {
            broadcastOptions.setTemporaryAppAllowlist(
                    allowListDuration.toMillis(),
                    TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                    REASON_REFRESH_SAFETY_SOURCES,
                    "Safety Center is requesting data from safety sources");
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
        return broadcastOptions;
    }

    @RefreshRequestType
    private static int toRefreshRequestType(@RefreshReason int refreshReason) {
        switch (refreshReason) {
            case REFRESH_REASON_RESCAN_BUTTON_CLICK:
                return EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
            case REFRESH_REASON_PAGE_OPEN:
            case REFRESH_REASON_DEVICE_REBOOT:
            case REFRESH_REASON_DEVICE_LOCALE_CHANGE:
            case REFRESH_REASON_SAFETY_CENTER_ENABLED:
            case REFRESH_REASON_OTHER:
                return EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
        }
        throw new IllegalArgumentException("Unexpected refresh reason: " + refreshReason);
    }

    /** Returns {@code true} if {@code refreshReason} corresponds to a "background refresh". */
    private static boolean isBackgroundRefresh(@RefreshReason int refreshReason) {
        switch (refreshReason) {
            case REFRESH_REASON_DEVICE_REBOOT:
            case REFRESH_REASON_DEVICE_LOCALE_CHANGE:
            case REFRESH_REASON_SAFETY_CENTER_ENABLED:
            case REFRESH_REASON_OTHER:
                return true;
            case REFRESH_REASON_PAGE_OPEN:
            case REFRESH_REASON_RESCAN_BUTTON_CLICK:
                return false;
        }
        throw new IllegalArgumentException("Unexpected refresh reason: " + refreshReason);
    }

    /** Returns the list of source IDs for which refreshing is denied for the given reason. */
    @NonNull
    private static Set<String> getRefreshDeniedSourceIds(@RefreshReason int refreshReason) {
        if (isBackgroundRefresh(refreshReason)) {
            return SafetyCenterFlags.getBackgroundRefreshDeniedSourceIds();
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Returns a flattened mapping from user IDs to lists of source IDs for those users. The map is
     * in the form of a {@link SparseArray} where the int keys are user IDs and the values are the
     * lists of source IDs.
     *
     * <p>The set of user IDs (keys) is the profile parent user ID of {@code userProfileGroup} plus
     * the (possibly empty) set of running managed profile user IDs in that group.
     *
     * <p>Every value present is a non-empty list, but the overall result may be empty.
     */
    private static SparseArray<List<String>> getUserIdsToSourceIds(
            @NonNull Broadcast broadcast,
            @NonNull UserProfileGroup userProfileGroup,
            @RefreshReason int refreshReason) {
        int[] managedProfileIds = userProfileGroup.getManagedRunningProfilesUserIds();
        SparseArray<List<String>> result = new SparseArray<>(managedProfileIds.length + 1);

        List<String> profileParentSources = broadcast.getSourceIdsForProfileParent(refreshReason);
        if (!profileParentSources.isEmpty()) {
            result.put(userProfileGroup.getProfileParentUserId(), profileParentSources);
        }

        List<String> managedProfileSources =
                broadcast.getSourceIdsForManagedProfiles(refreshReason);
        if (!managedProfileSources.isEmpty()) {
            for (int i = 0; i < managedProfileIds.length; i++) {
                result.put(managedProfileIds[i], managedProfileSources);
            }
        }

        return result;
    }
}
