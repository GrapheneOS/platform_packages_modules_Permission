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
import android.annotation.UserIdInt;
import android.content.Context;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Class responsible for posting, updating and dismissing Safety Center notifications each time
 * Safety Center's issues change.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterNotificationSender {

    private static final String TAG = "SafetyCenterNotificationSender";

    private static final int NOTIFICATION_BEHAVIOR_INTERNAL_NEVER = 100;
    private static final int NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED = 200;
    private static final int NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY = 300;

    /**
     * Internal notification behavior {@code @IntDef} which is related to the {@code
     * SafetySourceIssue.NotificationBehavior} type introduced in Android U.
     *
     * <p>This definition is available on T+.
     *
     * <p>Unlike the U+/external {@code @IntDef}, this one has no "unspecified behavior" value. Any
     * issues which have unspecified behavior are resolved to one of these specific behaviors based
     * on their other properties.
     */
    @IntDef(
            prefix = {"NOTIFICATION_BEHAVIOR_INTERNAL"},
            value = {
                NOTIFICATION_BEHAVIOR_INTERNAL_NEVER,
                NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED,
                NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY
            })
    @Retention(RetentionPolicy.SOURCE)
    private @interface NotificationBehaviorInternal {}

    // These dependencies are intentionally unused
    @NonNull private final Context mContext;

    @NonNull private final SafetyCenterNotificationFactory mNotificationFactory;

    @NonNull private final SafetyCenterIssueCache mIssueCache;

    @NonNull private final SafetyCenterRepository mSafetyCenterRepository;

    SafetyCenterNotificationSender(
            @NonNull Context context,
            @NonNull SafetyCenterNotificationFactory notificationFactory,
            @NonNull SafetyCenterIssueCache issueCache,
            @NonNull SafetyCenterRepository safetyCenterRepository) {
        mContext = context;
        mNotificationFactory = notificationFactory;
        mIssueCache = issueCache;
        mSafetyCenterRepository = safetyCenterRepository;
    }

    /**
     * Updates Safety Center notifications, usually in response to a change in the issues for the
     * given userId.
     */
    void updateNotifications(@UserIdInt int userId) {
        if (!SafetyCenterFlags.getNotificationsEnabled()) {
            return;
        }
        // TODO(b/255946874): Implement this
    }
}
