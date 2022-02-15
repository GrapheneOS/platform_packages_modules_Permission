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

import static android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.PowerExemptionManager.REASON_REFRESH_SAFETY_SOURCES;
import static android.os.PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED;
import static android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK;

import static com.android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_STATIC;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.config.SafetyCenterConfig;
import com.android.safetycenter.config.SafetySource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/** Class to manage and track refresh broadcasts sent by {@link SafetyCenterService}. */
@RequiresApi(TIRAMISU)
final class SafetyCenterRefreshManager {

    /**
     * Time for which an app, upon receiving a particular broadcast, will be placed on a temporary
     * power allowlist allowing it to start a foreground service from the background.
     */
    // TODO(b/219553295): Use a Device Config value instead, so that this duration can be
    //  easily adjusted.
    private static final Duration ALLOWLIST_DURATION = Duration.ofSeconds(20);

    @NonNull
    private final Context mContext;
    @Nullable
    private SafetyCenterConfig mSafetyCenterConfig;

    /** Creates a {@link SafetyCenterRefreshManager} using the given {@link Context}. */
    SafetyCenterRefreshManager(Context context) {
        mContext = context;
    }

    // TODO(b/218157907): Remove this method and use a SafetyCenterConfigReader field in
    //  SafetyCenterRefreshManager instead once ag/16834483 is submitted.
    /** Sets the {@link SafetyCenterConfig} to use to determine which sources to refresh. */
    void setSafetyCenterConfig(SafetyCenterConfig safetyCenterConfig) {
        mSafetyCenterConfig = safetyCenterConfig;
    }

    /**
     * Triggers a refresh of safety sources by sending them broadcasts with action
     * {@link android.safetycenter.SafetyCenterManager#ACTION_REFRESH_SAFETY_SOURCES}.
     */
    void refreshSafetySources(int refreshReason) {
        int requestType;
        switch (refreshReason) {
            case REFRESH_REASON_RESCAN_BUTTON_CLICK:
                requestType = EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
                break;
            case REFRESH_REASON_PAGE_OPEN:
                requestType = EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
                break;
            default:
                throw new IllegalArgumentException("Invalid refresh reason: " + refreshReason);
        }

        List<SafetySource> safetySourcesToRefresh =
                mSafetyCenterConfig.getSafetySourcesGroups()
                        .stream()
                        .flatMap(group -> group.getSafetySources().stream())
                        .filter(
                                // Only send broadcasts to dynamic safety sources that have
                                // specified a broadcast receiver component.
                                source -> source.getType() != SAFETY_SOURCE_TYPE_STATIC
                                        && source.getBroadcastReceiverClassName() != null)
                        .collect(Collectors.toList());

        sendRefreshBroadcastToSafetySources(safetySourcesToRefresh, requestType);

        // TODO(b/217944317): Remove this hardcoded broadcast to the cts app. Currently this is
        //  hardcoded as we don't have an API to add/remove safety sources at runtime in tests.
        //  We will add such an API as soon as we have API council feedback to determine the best
        //  approach.
        sendRefreshBroadcastToCtsAppForTest(requestType);
    }

    private void sendRefreshBroadcastToSafetySources(List<SafetySource> safetySources,
            int requestType) {
        Intent broadcastIntent = new Intent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, requestType)
                .setFlags(FLAG_RECEIVER_FOREGROUND);
        BroadcastOptions broadcastOptions = BroadcastOptions.makeBasic();
        // The following operation requires START_FOREGROUND_SERVICES_FROM_BACKGROUND
        // permission.
        broadcastOptions.setTemporaryAppAllowlist(ALLOWLIST_DURATION.toMillis(),
                TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                REASON_REFRESH_SAFETY_SOURCES,
                "Safety Center is requesting data from safety sources");

        for (SafetySource source : safetySources) {
            Intent broadcastIntentForSource = new Intent(broadcastIntent).setComponent(
                    new ComponentName(source.getPackageName(),
                            source.getBroadcastReceiverClassName()));
            // TODO(b/215144069): Add cross profile support for safety sources which support
            //  both personal and work profile. This implementation invokes
            //  `sendBroadcastAsUser` in order to invoke the permission.
            // The following operation requires INTERACT_ACROSS_USERS permission.
            mContext.sendBroadcastAsUser(broadcastIntentForSource,
                            UserHandle.CURRENT,
                            SEND_SAFETY_CENTER_UPDATE,
                            broadcastOptions.toBundle());
        }
    }

    private void sendRefreshBroadcastToCtsAppForTest(int requestType) {
        Intent broadcastIntent = new Intent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, requestType)
                .setFlags(FLAG_RECEIVER_FOREGROUND);
        BroadcastOptions broadcastOptions = BroadcastOptions.makeBasic();
        // The following operation requires START_FOREGROUND_SERVICES_FROM_BACKGROUND
        // permission.
        broadcastOptions.setTemporaryAppAllowlist(ALLOWLIST_DURATION.toMillis(),
                TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                REASON_REFRESH_SAFETY_SOURCES,
                "Safety Center is requesting data from safety sources");

        Intent broadcastIntentForSource = new Intent(broadcastIntent).setComponent(
                new ComponentName("android.safetycenter.cts",
                        "android.safetycenter.cts.SafetySourceBroadcastReceiver"));
        // The following operation requires INTERACT_ACROSS_USERS permission.
        mContext.sendBroadcastAsUser(broadcastIntentForSource,
                UserHandle.CURRENT,
                SEND_SAFETY_CENTER_UPDATE,
                broadcastOptions.toBundle());
    }
}
