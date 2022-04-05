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
import android.annotation.UserIdInt;
import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.safetycenter.SafetyCenterManager.RefreshRequestType;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.SafetyCenterConfigReader.Broadcast;
import com.android.safetycenter.SafetyCenterConfigReader.SafetyCenterConfigInternal;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Class to manage and track broadcasts sent by {@link SafetyCenterService}.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
final class SafetyCenterBroadcastManager {

    private static final String TAG = "SafetyCenterBroadcastMa";

    /**
     * Time for which an app, upon receiving a particular broadcast, will be placed on a temporary
     * power allowlist allowing it to start a foreground service from the background.
     */
    // TODO(b/219553295): Use a Device Config value instead, so that this duration can be
    //  easily adjusted.
    private static final Duration ALLOWLIST_DURATION = Duration.ofSeconds(20);

    @NonNull
    private final Context mContext;

    /** Creates a {@link SafetyCenterBroadcastManager} using the given {@link Context}. */
    SafetyCenterBroadcastManager(@NonNull Context context) {
        mContext = context;
    }

    /**
     * Triggers a refresh of safety sources by sending them broadcasts with action
     * {@link SafetyCenterManager#ACTION_REFRESH_SAFETY_SOURCES}.
     */
    void sendRefreshSafetySources(
            @NonNull SafetyCenterConfigInternal configInternal,
            @RefreshReason int refreshReason,
            @NonNull UserProfileGroup userProfileGroup) {
        List<Broadcast> broadcasts = configInternal.getBroadcasts();
        int requestType = toRefreshRequestType(refreshReason);
        BroadcastOptions broadcastOptions = BroadcastOptions.makeBasic();
        // The following operation requires START_FOREGROUND_SERVICES_FROM_BACKGROUND
        // permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            broadcastOptions.setTemporaryAppAllowlist(ALLOWLIST_DURATION.toMillis(),
                    TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                    REASON_REFRESH_SAFETY_SOURCES,
                    "Safety Center is requesting data from safety sources");
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);

            sendRefreshSafetySourcesBroadcast(broadcast, broadcastOptions, requestType,
                    userProfileGroup);
        }
    }

    /**
     * Triggers an {@link SafetyCenterManager#ACTION_SAFETY_CENTER_ENABLED_CHANGED} broadcast for
     * all safety sources.
     *
     * <p>This method also sends an implicit broadcast globally (which requires the {@link
     * android.Manifest.permission#READ_SAFETY_CENTER_STATUS} permission).
     */
    // TODO(b/227310195): Consider adding a boolean extra to the intent instead of having clients
    // rely on SafetyCenterManager#isSafetyCenterEnabled()?
    void sendEnabledChanged(@NonNull SafetyCenterConfigInternal configInternal) {
        List<Broadcast> broadcasts = configInternal.getBroadcasts();
        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);

            sendEnabledChangedBroadcast(
                    createEnabledChangedBroadcastIntent(broadcast.getPackageName()),
                    SEND_SAFETY_CENTER_UPDATE);
        }

        sendEnabledChangedBroadcast(createEnabledChangedBroadcastIntent(),
                READ_SAFETY_CENTER_STATUS);
    }

    private void sendRefreshSafetySourcesBroadcast(
            @NonNull Broadcast broadcast,
            @NonNull BroadcastOptions broadcastOptions,
            @RefreshRequestType int requestType,
            @NonNull UserProfileGroup userProfileGroup) {
        if (!broadcast.getSourceIdsForProfileOwner().isEmpty()) {
            int profileOwnerUserId = userProfileGroup.getProfileOwnerUserId();
            Intent broadcastIntent = createRefreshSafetySourcesBroadcastIntent(
                    requestType,
                    broadcast.getPackageName(),
                    broadcast.getSourceIdsForProfileOwner(),
                    profileOwnerUserId);

            sendRefreshSafetySourcesBroadcast(broadcastIntent, broadcastOptions,
                    UserHandle.of(profileOwnerUserId));
        }
        if (!broadcast.getSourceIdsForManagedProfiles().isEmpty()) {
            int[] managedProfileUserIds = userProfileGroup.getManagedProfilesUserIds();
            for (int i = 0; i < managedProfileUserIds.length; i++) {
                int managedProfileUserId = managedProfileUserIds[i];
                Intent broadcastIntent = createRefreshSafetySourcesBroadcastIntent(
                        requestType,
                        broadcast.getPackageName(),
                        broadcast.getSourceIdsForManagedProfiles(),
                        managedProfileUserId);

                sendRefreshSafetySourcesBroadcast(broadcastIntent, broadcastOptions,
                        UserHandle.of(managedProfileUserId));
            }
        }
    }

    private void sendRefreshSafetySourcesBroadcast(
            @NonNull Intent broadcastIntent,
            @NonNull BroadcastOptions broadcastOptions,
            @NonNull UserHandle userHandle) {
        // The following operation requires INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(broadcastIntent,
                    userHandle,
                    SEND_SAFETY_CENTER_UPDATE,
                    broadcastOptions.toBundle());
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private void sendEnabledChangedBroadcast(@NonNull Intent broadcastIntent,
            @NonNull String permission) {
        // The following operation requires INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(broadcastIntent,
                    UserHandle.ALL,
                    permission);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @NonNull
    private static Intent createEnabledChangedBroadcastIntent(@NonNull String packageName) {
        return createEnabledChangedBroadcastIntent().setPackage(packageName);
    }

    @NonNull
    private static Intent createEnabledChangedBroadcastIntent() {
        return createBroadcastIntent(ACTION_SAFETY_CENTER_ENABLED_CHANGED);
    }

    @NonNull
    private static Intent createRefreshSafetySourcesBroadcastIntent(
            @RefreshRequestType int requestType,
            @NonNull String packageName,
            @NonNull List<String> sourceIdsToRefresh,
            @UserIdInt int userId) {
        String refreshBroadcastId = String.valueOf(
                Objects.hash(sourceIdsToRefresh, userId, System.currentTimeMillis()));
        return createBroadcastIntent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, requestType)
                // TODO(b/220826153): Test source ids in refresh broadcasts.
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS,
                        sourceIdsToRefresh.toArray(new String[0]))
                // TODO(b/222677992): Test refresh broadcast id in refresh broadcasts.
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, refreshBroadcastId)
                .setPackage(packageName);
    }

    @NonNull
    private static Intent createBroadcastIntent(
            @NonNull String intentAction) {
        return new Intent(intentAction)
                .setFlags(FLAG_RECEIVER_FOREGROUND);
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
        throw new IllegalArgumentException("Invalid refresh reason: " + refreshReason);
    }
}
