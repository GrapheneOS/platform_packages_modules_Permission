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
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetyCenterEntry;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.util.Arrays;
import java.util.List;

/** Helps build or retrieve {@link PendingIntent} instances. */
@RequiresApi(TIRAMISU)
final class PendingIntentFactory {

    private static final String TAG = "PendingIntentFactory";

    private static final int DEFAULT_REQUEST_CODE = 0;

    private static final String IS_SETTINGS_HOMEPAGE = "is_from_settings_homepage";

    private static final String ANDROID_LOCK_SCREEN_SOURCE_ID = "AndroidLockScreen";
    // Arbitrary values to construct PendingIntents that are guaranteed not to be equal due to
    // these request codes not being equal. The values match the ones in Settings QPR, in case we
    // ever end up using these request codes in QPR.
    private static final int ANDROID_LOCK_SCREEN_ENTRY_REQ_CODE = 1;
    private static final int ANDROID_LOCK_SCREEN_ICON_ACTION_REQ_CODE = 2;

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
     * <p>If the given {@code intentAction} resolves for the given {@code packageName}, the {@link
     * PendingIntent} will explicitly target the {@code packageName}. If the {@code intentAction}
     * resolves elsewhere, the {@link PendingIntent} will be implicit.
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
        Intent intent = createIntent(packageContext, sourceId, intentAction, isQuietModeEnabled);
        if (intent == null) {
            return null;
        }
        return getActivityPendingIntent(packageContext, DEFAULT_REQUEST_CODE, intent);
    }

    /**
     * Potentially overrides the Settings {@link PendingIntent}s for the AndroidLockScreen source.
     *
     * <p>This is done because of a bug in the Settings app where the {@link PendingIntent}s created
     * end up referencing either the {@link SafetyCenterEntry#getPendingIntent()} or the {@link
     * SafetyCenterEntry.IconAction#getPendingIntent()}. The reason for this is that {@link
     * PendingIntent} instances are cached and keyed by an object which does not take into account
     * the underlying {@link Intent} extras; and these two {@link Intent}s only differ by the extras
     * that they set.
     *
     * <p>We fix this issue by recreating the desired {@link PendingIntent} manually here, using
     * different request codes for the different {@link PendingIntent}s to ensure new instances are
     * created (the key does take into account the request code).
     */
    @Nullable
    PendingIntent maybeOverridePendingIntent(
            @NonNull String sourceId, @Nullable PendingIntent pendingIntent, boolean isIconAction) {
        if (!ANDROID_LOCK_SCREEN_SOURCE_ID.equals(sourceId) || pendingIntent == null) {
            return pendingIntent;
        }
        if (!SafetyCenterFlags.getReplaceLockScreenIconAction()) {
            return pendingIntent;
        }
        String settingsPackageName = pendingIntent.getCreatorPackage();
        int userId = pendingIntent.getCreatorUserHandle().getIdentifier();
        Context settingsPackageContext = createPackageContextAsUser(settingsPackageName, userId);
        if (settingsPackageContext == null) {
            return pendingIntent;
        }
        if (hasFixedSettingsIssue(settingsPackageContext)) {
            return pendingIntent;
        }
        PendingIntent suspectPendingIntent =
                getActivityPendingIntent(
                        settingsPackageContext,
                        DEFAULT_REQUEST_CODE,
                        newBaseLockScreenIntent(settingsPackageName),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (suspectPendingIntent == null) {
            // Nothing was cached.
            return pendingIntent;
        }
        if (!suspectPendingIntent.equals(pendingIntent)) {
            // The pending intent is not hitting this caching issue, so we should skip the override.
            return pendingIntent;
        }
        // We’re most likely hitting the caching issue described in this method’s documentation, so
        // we should ensure we create brand new pending intents where applicable by using different
        // request codes. We only perform this override for the applicable pending intents.
        // This is important because there are scenarios where the Settings app provides different
        // pending intents (e.g. in the work profile), and in this case we shouldn't override them.
        if (isIconAction) {
            Log.w(
                    TAG,
                    "Replacing " + ANDROID_LOCK_SCREEN_SOURCE_ID + " icon action pending intent");
            return getActivityPendingIntent(
                    settingsPackageContext,
                    ANDROID_LOCK_SCREEN_ICON_ACTION_REQ_CODE,
                    newLockScreenIconActionIntent(settingsPackageName));
        }
        Log.w(TAG, "Replacing " + ANDROID_LOCK_SCREEN_SOURCE_ID + " entry or issue pending intent");
        return getActivityPendingIntent(
                settingsPackageContext,
                ANDROID_LOCK_SCREEN_ENTRY_REQ_CODE,
                newLockScreenIntent(settingsPackageName));
    }

    private static boolean hasFixedSettingsIssue(@NonNull Context settingsPackageContext) {
        Resources settingsResources = settingsPackageContext.getResources();
        int hasSettingsFixedIssueResourceId =
                settingsResources.getIdentifier(
                        "config_isSafetyCenterLockScreenPendingIntentFixed",
                        "bool",
                        settingsPackageContext.getPackageName());
        if (hasSettingsFixedIssueResourceId != Resources.ID_NULL) {
            return settingsResources.getBoolean(hasSettingsFixedIssueResourceId);
        }
        return false;
    }

    @NonNull
    private static Intent newBaseLockScreenIntent(@NonNull String settingsPackageName) {
        return new Intent(Intent.ACTION_MAIN)
                .setComponent(
                        new ComponentName(
                                settingsPackageName, settingsPackageName + ".SubSettings"))
                .putExtra(":settings:source_metrics", 1917);
    }

    @NonNull
    private static Intent newLockScreenIntent(@NonNull String settingsPackageName) {
        String targetFragment =
                settingsPackageName + ".password.ChooseLockGeneric$ChooseLockGenericFragment";
        return newBaseLockScreenIntent(settingsPackageName)
                .putExtra(":settings:show_fragment", targetFragment)
                .putExtra("page_transition_type", 1);
    }

    @NonNull
    private static Intent newLockScreenIconActionIntent(@NonNull String settingsPackageName) {
        String targetFragment = settingsPackageName + ".security.screenlock.ScreenLockSettings";
        return newBaseLockScreenIntent(settingsPackageName)
                .putExtra(":settings:show_fragment", targetFragment)
                .putExtra("page_transition_type", 0);
    }

    @Nullable
    private Intent createIntent(
            @NonNull Context packageContext,
            @NonNull String sourceId,
            @NonNull String intentAction,
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

        // If the intent resolves for the package provided, then we make the assumption that it is
        // the desired app and make the intent explicit. This is to workaround implicit internal
        // intents that may not be exported which will stop working on Android U+.
        // This assumes that the source or the caller has the highest priority to resolve the intent
        // action.
        Intent explicitIntent = new Intent(intent).setPackage(packageContext.getPackageName());
        if (intentResolves(packageContext, explicitIntent)) {
            return explicitIntent;
        }

        if (intentResolves(packageContext, intent)) {
            // TODO(b/265954624): Write tests for this code path.
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

    private static boolean intentResolves(@NonNull Context packageContext, @NonNull Intent intent) {
        return !queryIntentActivities(packageContext, intent).isEmpty();
    }

    @NonNull
    private static List<ResolveInfo> queryIntentActivities(
            @NonNull Context packageContext, @NonNull Intent intent) {
        PackageManager packageManager = packageContext.getPackageManager();
        // This call requires the INTERACT_ACROSS_USERS permission as the `packageContext` could
        // belong to another user.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return packageManager.queryIntentActivities(intent, ResolveInfoFlags.of(0));
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @NonNull
    private static PendingIntent getActivityPendingIntent(
            @NonNull Context packageContext, int requestCode, @NonNull Intent intent) {
        return getActivityPendingIntent(
                packageContext, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Creates a {@link PendingIntent} to start an Activity from the given {@code packageContext}.
     *
     * <p>This function can only return {@code null} if the {@link PendingIntent#FLAG_NO_CREATE}
     * flag is passed in.
     */
    @Nullable
    static PendingIntent getActivityPendingIntent(
            @NonNull Context packageContext, int requestCode, @NonNull Intent intent, int flags) {
        // This call requires Binder identity to be cleared for getIntentSender() to be allowed to
        // send as another package.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return PendingIntent.getActivity(packageContext, requestCode, intent, flags);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Creates a non-protected broadcast {@link PendingIntent} which can only be received by the
     * system. Use this method to create PendingIntents to be received by Context-registered
     * receivers, for example for notification-related callbacks.
     *
     * <p>{@code flags} must include {@link PendingIntent#FLAG_IMMUTABLE}
     */
    @Nullable
    static PendingIntent getNonProtectedSystemOnlyBroadcastPendingIntent(
            @NonNull Context context, int requestCode, @NonNull Intent intent, int flags) {
        if ((flags & PendingIntent.FLAG_IMMUTABLE) == 0) {
            throw new IllegalArgumentException("flags must include FLAG_IMMUTABLE");
        }
        intent.setPackage("android");
        // This call is needed to be allowed to send the broadcast as the "android" package.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return PendingIntent.getBroadcast(context, requestCode, intent, flags);
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
