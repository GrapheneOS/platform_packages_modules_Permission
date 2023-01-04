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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Binder;
import android.safetycenter.SafetySourceIssue;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.resources.SafetyCenterResourcesContext;

/** Class responsible for creating and updating Safety Center's notification channels. */
@RequiresApi(TIRAMISU)
final class SafetyCenterNotificationChannels {

    private static final String TAG = "SafetyCenterNC";

    @NonNull private final SafetyCenterResourcesContext mResourcesContext;

    SafetyCenterNotificationChannels(
            @NonNull SafetyCenterResourcesContext safetyCenterResourceContext) {
        mResourcesContext = safetyCenterResourceContext;
    }

    /**
     * Returns the ID of the appropriate {@link NotificationChannel} for a notification about the
     * given {@code issue} after ensuring that channel has been created.
     */
    @Nullable
    String createAndGetChannelId(
            @NonNull NotificationManager notificationManager, @NonNull SafetySourceIssue issue) {
        // TODO(b/259398016): Different channels for different issues/severities
        NotificationChannel channel =
                new NotificationChannel(
                        "safety_center",
                        // TODO(b/259399024): Use suitable string here
                        mResourcesContext.getStringByName("notification_channel_name"),
                        NotificationManager.IMPORTANCE_DEFAULT);
        return createNotificationChannelWithoutCallingIdentity(notificationManager, channel);
    }

    /**
     * Creates a {@link NotificationChannel} using the given {@link NotificationManager}, dropping
     * any calling identity so that it can be unblockable. Returns the new channel's ID if it was
     * created successfully or {@code null} otherwise.
     */
    @Nullable
    private static String createNotificationChannelWithoutCallingIdentity(
            @NonNull NotificationManager notificationManager,
            @NonNull NotificationChannel channel) {
        // Clearing calling identity to be able to make an unblockable system notification channel
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.createNotificationChannel(channel);
            return channel.getId();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to create notification channel", e);
            return null;
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }
}
