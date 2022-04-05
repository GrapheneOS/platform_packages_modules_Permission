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
import android.os.Handler;
import android.os.UserHandle;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.safetycenter.SafetyCenterManager.RefreshRequestType;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.BackgroundThread;
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
final class SafetyCenterBroadcastDispatcher {

    private static final String TAG = "SafetyCenterBroadcastDi";

    /**
     * Time for which a refresh is allowed to wait for sources to set data before timing out and
     * marking the refresh as finished.
     */
    // TODO(b/218285164): Decide final timeout and use a Device Config value instead so that this
    //  duration can be easily adjusted. Once done, add a test that overrides this Device Config
    //  value in CTS tests.
    private static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Time for which an app, upon receiving a particular broadcast, will be placed on a temporary
     * power allowlist allowing it to start a foreground service from the background.
     */
    // TODO(b/219553295): Use a Device Config value instead, so that this duration can be
    //  easily adjusted.
    private static final Duration ALLOWLIST_DURATION = Duration.ofSeconds(20);

    /** Counter to distinguish refreshes happening in quick succession. */
    private int mRefreshCounter = 0;

    @NonNull private final Context mContext;

    @NonNull private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;

    /**
     * Creates a {@link SafetyCenterBroadcastDispatcher} using the given {@link Context} and {@link
     * SafetyCenterRefreshTracker}.
     */
    SafetyCenterBroadcastDispatcher(
            @NonNull Context context,
            @NonNull SafetyCenterRefreshTracker safetyCenterRefreshTracker) {
        mContext = context;
        mSafetyCenterRefreshTracker = safetyCenterRefreshTracker;
    }

    /**
     * Triggers a refresh of safety sources by sending them broadcasts with action {@link
     * SafetyCenterManager#ACTION_REFRESH_SAFETY_SOURCES}.
     */
    void sendRefreshSafetySources(
            @NonNull SafetyCenterConfigInternal configInternal,
            @RefreshReason int refreshReason,
            @NonNull UserProfileGroup userProfileGroup) {
        List<Broadcast> broadcasts = configInternal.getBroadcasts();
        int requestType = toRefreshRequestType(refreshReason);
        BroadcastOptions broadcastOptions = createBroadcastOptions();
        String broadcastId =
                String.format(
                        "%s_%s",
                        Objects.hash(
                                requestType,
                                broadcasts,
                                userProfileGroup,
                                System.currentTimeMillis()),
                        incrementRefreshCounter());

        mSafetyCenterRefreshTracker.reportRefreshInProgress(
                broadcastId, requestType, userProfileGroup, broadcasts);
        // TODO(b/229060064): Determine best way to ensure one refresh at a time.

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);

            sendRefreshSafetySourcesBroadcast(
                    broadcast, broadcastOptions, requestType, userProfileGroup, broadcastId);
        }

        // TODO(b/229062879): Should we block here or not?
        Handler handler = BackgroundThread.getHandler();
        handler.postDelayed(
                mSafetyCenterRefreshTracker.createClearRefreshRunnable(broadcastId),
                REFRESH_TIMEOUT.toMillis());
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
    void sendEnabledChanged(@NonNull SafetyCenterConfigInternal configInternal) {
        List<Broadcast> broadcasts = configInternal.getBroadcasts();
        BroadcastOptions broadcastOptions = createBroadcastOptions();

        for (int i = 0; i < broadcasts.size(); i++) {
            Broadcast broadcast = broadcasts.get(i);

            sendBroadcast(
                    createEnabledChangedBroadcastIntent(broadcast.getPackageName()),
                    UserHandle.ALL,
                    SEND_SAFETY_CENTER_UPDATE,
                    broadcastOptions);
        }

        sendBroadcast(
                createEnabledChangedBroadcastIntent(),
                UserHandle.ALL,
                READ_SAFETY_CENTER_STATUS,
                null);
    }

    private void sendRefreshSafetySourcesBroadcast(
            @NonNull Broadcast broadcast,
            @NonNull BroadcastOptions broadcastOptions,
            @RefreshRequestType int requestType,
            @NonNull UserProfileGroup userProfileGroup,
            @NonNull String broadcastId) {
        if (!broadcast.getSourceIdsForProfileOwner().isEmpty()) {
            int profileOwnerUserId = userProfileGroup.getProfileOwnerUserId();
            Intent broadcastIntent =
                    createRefreshSafetySourcesBroadcastIntent(
                            requestType,
                            broadcast.getPackageName(),
                            broadcast.getSourceIdsForProfileOwner(),
                            broadcastId);
            sendBroadcast(
                    broadcastIntent,
                    UserHandle.of(profileOwnerUserId),
                    SEND_SAFETY_CENTER_UPDATE,
                    broadcastOptions);
        }
        if (!broadcast.getSourceIdsForManagedProfiles().isEmpty()) {
            int[] managedProfileUserIds = userProfileGroup.getManagedProfilesUserIds();
            for (int i = 0; i < managedProfileUserIds.length; i++) {
                int managedProfileUserId = managedProfileUserIds[i];
                Intent broadcastIntent =
                        createRefreshSafetySourcesBroadcastIntent(
                                requestType,
                                broadcast.getPackageName(),
                                broadcast.getSourceIdsForManagedProfiles(),
                                broadcastId);

                sendBroadcast(
                        broadcastIntent,
                        UserHandle.of(managedProfileUserId),
                        SEND_SAFETY_CENTER_UPDATE,
                        broadcastOptions);
            }
        }
    }

    private void sendBroadcast(
            @NonNull Intent broadcastIntent,
            @NonNull UserHandle userHandle,
            @NonNull String permission,
            @Nullable BroadcastOptions broadcastOptions) {
        // The following operation requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(
                    broadcastIntent,
                    userHandle,
                    permission,
                    broadcastOptions == null ? null : broadcastOptions.toBundle());
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
            @NonNull String broadcastId) {
        return createBroadcastIntent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, requestType)
                .putExtra(
                        EXTRA_REFRESH_SAFETY_SOURCE_IDS, sourceIdsToRefresh.toArray(new String[0]))
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
        // The following operation requires the START_FOREGROUND_SERVICES_FROM_BACKGROUND
        // permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            broadcastOptions.setTemporaryAppAllowlist(
                    ALLOWLIST_DURATION.toMillis(),
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
        throw new IllegalArgumentException("Invalid refresh reason: " + refreshReason);
    }

    /**
     * Increments {@link #mRefreshCounter} by 1 modulus 1000, to be used to distinguish refreshes
     * happening in quick succession.
     */
    private int incrementRefreshCounter() {
        return mRefreshCounter = (mRefreshCounter + 1) % 1000;
    }
}
