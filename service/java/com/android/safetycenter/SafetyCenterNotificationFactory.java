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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.safetycenter.SafetySourceIssue;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import java.util.List;

/**
 * Factory that builds {@link Notification} objects from {@link SafetySourceIssue} instances with
 * appropriate {@link PendingIntent}s for click and dismiss callbacks.
 */
@RequiresApi(TIRAMISU)
final class SafetyCenterNotificationFactory {

    private static final String TAG = "SafetyCenterNF";

    private static final int OPEN_SAFETY_CENTER_REQUEST_CODE = 1221;

    @NonNull private final Context mContext;

    SafetyCenterNotificationFactory(@NonNull Context context) {
        mContext = context;
    }

    /**
     * Creates and returns a new {@link Notification} instance corresponding to the given issue, or
     * {@code null} if none could be created.
     *
     * <p>The provided {@link NotificationManager} is used to create or update the {@link
     * NotificationChannel} for the notification.
     */
    @Nullable
    Notification newNotificationForIssue(
            @NonNull NotificationManager notificationManager,
            @NonNull SafetySourceIssue issue,
            @NonNull SafetyCenterIssueKey issueKey) {
        String channelId = createAndGetChannelId(notificationManager, issue);

        if (channelId == null) {
            return null;
        }

        CharSequence title = issue.getTitle();
        CharSequence text = issue.getSummary();
        List<SafetySourceIssue.Action> issueActions = issue.getActions();

        if (SdkLevel.isAtLeastU()) {
            SafetySourceIssue.Notification customNotification = issue.getCustomNotification();
            if (customNotification != null) {
                title = customNotification.getTitle();
                text = customNotification.getText();
                issueActions = customNotification.getActions();
            }
        }

        Notification.Builder builder =
                new Notification.Builder(mContext, channelId)
                        // TODO(b/259399024): Use correct icon here
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setExtras(getNotificationExtras())
                        .setContentTitle(title)
                        .setContentText(text)
                        .setContentIntent(newSafetyCenterPendingIntent(issue))
                        .setDeleteIntent(
                                SafetyCenterNotificationReceiver.newNotificationDismissedIntent(
                                        mContext, issueKey));

        for (int i = 0; i < issueActions.size(); i++) {
            Notification.Action notificationAction =
                    toNotificationAction(issueKey, issueActions.get(i));
            builder.addAction(notificationAction);
        }

        return builder.build();
    }

    @Nullable
    private String createAndGetChannelId(
            @NonNull NotificationManager notificationManager, @NonNull SafetySourceIssue issue) {
        // TODO(b/259398016): Different channels for different issues/severities
        NotificationChannel channel =
                new NotificationChannel(
                        "safety_center",
                        // TODO(b/259399024): Use suitable string here
                        "Safety Center notifications",
                        NotificationManager.IMPORTANCE_DEFAULT);
        return createNotificationChannelWithoutCallingIdentity(notificationManager, channel);
    }

    @NonNull
    private PendingIntent newSafetyCenterPendingIntent(@NonNull SafetySourceIssue targetIssue) {
        // TODO(b/259398419): Add target issue to intent so it's highlighted when SC opens
        Intent intent = new Intent(Intent.ACTION_SAFETY_CENTER);
        return PendingIntentFactory.getActivityPendingIntent(
                mContext, OPEN_SAFETY_CENTER_REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    @NonNull
    private Bundle getNotificationExtras() {
        Bundle extras = new Bundle();
        // TODO(b/259399024): Use suitable string resource here
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, "Safety Center");
        return extras;
    }

    @NonNull
    private Notification.Action toNotificationAction(
            @NonNull SafetyCenterIssueKey issueKey, @NonNull SafetySourceIssue.Action issueAction) {
        // We do not use the action's PendingIntent directly here instead we build a new PI which
        // will be handled by our SafetyCenterNotificationReceiver which will in turn dispatch
        // the source-provided action PI. This ensures that action execution is consistent across
        // between Safety Center UI and notifications, for example executing an action from a
        // notification will send an "action in-flight" update to any current listeners.
        SafetyCenterIssueActionId issueActionId =
                SafetyCenterIssueActionId.newBuilder()
                        .setSafetyCenterIssueKey(issueKey)
                        .setSafetySourceIssueActionId(issueAction.getId())
                        .build();
        PendingIntent receiverPendingIntent =
                SafetyCenterNotificationReceiver.newNotificationActionClickedIntent(
                        mContext, issueActionId);
        return new Notification.Action.Builder(null, issueAction.getLabel(), receiverPendingIntent)
                .build();
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
