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

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.annotation.Nullable;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.os.Binder;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.resources.SafetyCenterResourcesContext;

/** Class responsible for creating and updating Safety Center's notification channels. */
@RequiresApi(TIRAMISU)
final class SafetyCenterNotificationChannels {

    private static final String TAG = "SafetyCenterNC";

    private static final String CHANNEL_GROUP_ID = "safety_center_channels";
    private static final String CHANNEL_ID_INFORMATION = "safety_center_information";
    private static final String CHANNEL_ID_RECOMMENDATION = "safety_center_recommendation";
    private static final String CHANNEL_ID_CRITICAL_WARNING = "safety_center_critical_warning";

    private final SafetyCenterResourcesContext mResourcesContext;

    SafetyCenterNotificationChannels(SafetyCenterResourcesContext safetyCenterResourceContext) {
        mResourcesContext = safetyCenterResourceContext;
    }

    /**
     * Returns the ID of the appropriate {@link NotificationChannel} for a notification about the
     * given {@code issue} after ensuring that channel has been created.
     */
    @Nullable
    String createAndGetChannelId(NotificationManager notificationManager, SafetySourceIssue issue) {
        try {
            createAllChannelsWithoutCallingIdentity(notificationManager);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to create notification channels", e);
            return null;
        }
        return getChannelIdForIssue(issue);
    }

    @Nullable
    private String getChannelIdForIssue(SafetySourceIssue issue) {
        switch (issue.getSeverityLevel()) {
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return CHANNEL_ID_INFORMATION;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return CHANNEL_ID_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return CHANNEL_ID_CRITICAL_WARNING;
            default:
                Log.w(TAG, "No applicable notification channel for issue " + issue);
                return null;
        }
    }

    /**
     * Creates all Safety Center {@link NotificationChannel}s instances and their group using the
     * given {@link NotificationManager}, dropping any calling identity so those channels can be
     * unblockable. Throws a {@link RuntimeException} if any channel is malformed and could not be
     * created.
     */
    // TODO(b/265277413): Recreate/update these channels on locale changes by calling this method
    @Nullable
    private void createAllChannelsWithoutCallingIdentity(NotificationManager notificationManager) {
        // Clearing calling identity to be able to make unblockable system notification channels
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.createNotificationChannelGroup(getChannelGroupDefinition());
            notificationManager.createNotificationChannel(getGreenChannelDefinition());
            notificationManager.createNotificationChannel(getYellowChannelDefinition());
            notificationManager.createNotificationChannel(getRedChannelDefinition());
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private NotificationChannelGroup getChannelGroupDefinition() {
        return new NotificationChannelGroup(
                CHANNEL_GROUP_ID, getString("notification_channel_group_name"));
    }

    private NotificationChannel getGreenChannelDefinition() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID_INFORMATION,
                        getString("notification_channel_name_information"),
                        NotificationManager.IMPORTANCE_LOW);
        channel.setGroup(CHANNEL_GROUP_ID);
        channel.setBlockable(true);
        return channel;
    }

    private NotificationChannel getYellowChannelDefinition() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID_RECOMMENDATION,
                        getString("notification_channel_name_recommendation"),
                        NotificationManager.IMPORTANCE_DEFAULT);
        channel.setGroup(CHANNEL_GROUP_ID);
        channel.setBlockable(false);
        return channel;
    }

    private NotificationChannel getRedChannelDefinition() {
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID_CRITICAL_WARNING,
                        getString("notification_channel_name_critical_warning"),
                        NotificationManager.IMPORTANCE_HIGH);
        channel.setGroup(CHANNEL_GROUP_ID);
        channel.setBlockable(false);
        return channel;
    }

    private String getString(String name) {
        return mResourcesContext.getStringByName(name);
    }
}
