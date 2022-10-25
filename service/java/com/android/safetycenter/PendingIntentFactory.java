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
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.permission.util.PackageUtils;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.util.Arrays;

/** Helps build or retrieve {@link PendingIntent} instances. */
@RequiresApi(TIRAMISU)
final class PendingIntentFactory {

    private static final String TAG = "PendingIntentFactory";
    private static final String ANDROID_LOCK_SCREEN_SOURCE_ID = "AndroidLockScreen";
    private static final int DEFAULT_REQUEST_CODE = 0;
    private static final int ANDROID_LOCK_SCREEN_ICON_ACTION_REQ_CODE = 86;
    private static final String IS_SETTINGS_HOMEPAGE = "is_from_settings_homepage";

    @NonNull private final Context mContext;
    @NonNull private final SafetyCenterResourcesContext mSafetyCenterResourcesContext;

    PendingIntentFactory(
            @NonNull Context context,
            @NonNull SafetyCenterResourcesContext safetyCenterResourcesContext) {
        mContext = context;
        mSafetyCenterResourcesContext = safetyCenterResourcesContext;
    }

    /**
     * Creates or retrieves a {@link PendingIntent} that will start a new {@code Activity} matching
     * the given {@code intentAction}.
     *
     * <p>If the given {@code intentAction} doesn't resolve implicitly, it will be matched
     * explicitly using the given {@code packageName}.
     *
     * <p>The {@code PendingIntent} is associated with a specific source given by {@code sourceId}.
     *
     * <p>Returns {@code null} if the required {@link PendingIntent} cannot be created or if there
     * is no valid target for the given {@code intentAction}.
     */
    @Nullable
    PendingIntent getPendingIntent(
            @NonNull String sourceId,
            @Nullable String intentAction,
            @NonNull String packageName,
            @UserIdInt int userId,
            boolean isQuietModeEnabled) {
        if (intentAction == null) {
            return null;
        }
        Context packageContext = createPackageContextAsUser(packageName, userId);
        if (packageContext == null) {
            return null;
        }
        Intent intent =
                createIntent(sourceId, intentAction, packageName, userId, isQuietModeEnabled);
        if (intent == null) {
            return null;
        }
        return getActivityPendingIntent(packageContext, DEFAULT_REQUEST_CODE, intent);
    }

    /**
     * Potentially overrides the Settings IconAction PendingIntent for the AndroidLockScreen source.
     *
     * <p>This is done because of a bug in the Settings app where the PendingIntent created ends up
     * referencing the one from the main entry. The reason for this is that PendingIntent instances
     * are cached and keyed by an object which does not take into account the underlying intent
     * extras; and these two intents only differ by the extras that they set. We fix this issue by
     * recreating the desired Intent and PendingIntent here, using a specific request code for the
     * PendingIntent to ensure a new instance is created (the key does take into account the request
     * code).
     */
    @NonNull
    PendingIntent getIconActionPendingIntent(
            @NonNull String sourceId, @NonNull PendingIntent pendingIntent) {
        if (!ANDROID_LOCK_SCREEN_SOURCE_ID.equals(sourceId)) {
            return pendingIntent;
        }
        if (!SafetyCenterFlags.getReplaceLockScreenIconAction()) {
            return pendingIntent;
        }
        String settingsPackageName = pendingIntent.getCreatorPackage();
        int userId = pendingIntent.getCreatorUserHandle().getIdentifier();
        Context packageContext = createPackageContextAsUser(settingsPackageName, userId);
        if (packageContext == null) {
            return pendingIntent;
        }
        Resources settingsResources = packageContext.getResources();
        int hasSettingsFixedIssueResourceId =
                settingsResources.getIdentifier(
                        "config_isSafetyCenterLockScreenPendingIntentFixed",
                        "bool",
                        settingsPackageName);
        if (hasSettingsFixedIssueResourceId != Resources.ID_NULL) {
            boolean hasSettingsFixedIssue =
                    settingsResources.getBoolean(hasSettingsFixedIssueResourceId);
            if (hasSettingsFixedIssue) {
                return pendingIntent;
            }
        }
        Intent intent =
                new Intent(Intent.ACTION_MAIN)
                        .setComponent(
                                new ComponentName(
                                        settingsPackageName, settingsPackageName + ".SubSettings"))
                        .putExtra(
                                ":settings:show_fragment",
                                settingsPackageName + ".security.screenlock.ScreenLockSettings")
                        .putExtra(":settings:source_metrics", 1917)
                        .putExtra("page_transition_type", 0);
        PendingIntent offendingPendingIntent =
                getActivityPendingIntent(packageContext, DEFAULT_REQUEST_CODE, intent);
        if (!offendingPendingIntent.equals(pendingIntent)) {
            return pendingIntent;
        }
        // If creating that PendingIntent with request code 0 returns the same value as the
        // PendingIntent that was sent to Safety Center, then we’re most likely hitting the caching
        // issue described in this method’s documentation.
        // i.e. the intent action and component of the cached PendingIntent are the same, but the
        // extras are actually different so we should ensure we create a brand new PendingIntent by
        // changing the request code.
        return getActivityPendingIntent(
                packageContext, ANDROID_LOCK_SCREEN_ICON_ACTION_REQ_CODE, intent);
    }

    @Nullable
    private Intent createIntent(
            @NonNull String sourceId,
            @NonNull String intentAction,
            @NonNull String packageName,
            @UserIdInt int userId,
            boolean isQuietModeEnabled) {
        Intent intent = new Intent(intentAction);

        if (shouldAddSettingsHomepageExtra(sourceId)) {
            // Identify this intent as coming from Settings. Because this intent is actually coming
            // from Safety Center, which is served by PermissionController, this is useful to
            // indicate that it is presented as part of the Settings app.
            //
            // In particular, the AOSP Settings app uses this to ensure that two-pane mode works
            // correctly.
            intent.putExtra(IS_SETTINGS_HOMEPAGE, true);
        }

        // queryIntentActivities does not return any activity when the work profile is in quiet
        // mode, even though it opens the quiet mode dialog. So, we assume that the intent will
        // resolve to this dialog.
        if (isQuietModeEnabled) {
            return intent;
        }
        if (intentResolves(intent, userId)) {
            return intent;
        }
        intent.setPackage(packageName);
        if (intentResolves(intent, userId)) {
            return intent;
        }
        return null;
    }

    private boolean shouldAddSettingsHomepageExtra(@NonNull String sourceId) {
        return Arrays.asList(
                        mSafetyCenterResourcesContext
                                .getStringByName("config_useSettingsHomepageIntentExtra")
                                .split(","))
                .contains(sourceId);
    }

    private boolean intentResolves(@NonNull Intent intent, @UserIdInt int userId) {
        return !PackageUtils.queryUnfilteredIntentActivitiesAsUser(intent, 0, userId, mContext)
                .isEmpty();
    }

    @NonNull
    private static PendingIntent getActivityPendingIntent(
            @NonNull Context packageContext, int requestCode, @NonNull Intent intent) {
        // This call requires Binder identity to be cleared for getIntentSender() to be allowed to
        // send as another package.
        final long callingId = Binder.clearCallingIdentity();

        try {
            return PendingIntent.getActivity(
                    packageContext, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Nullable
    private Context createPackageContextAsUser(@NonNull String packageName, @UserIdInt int userId) {
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return mContext.createPackageContextAsUser(packageName, 0, UserHandle.of(userId));
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package name " + packageName + " not found", e);
            return null;
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }
}
