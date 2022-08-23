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
import android.annotation.StringRes;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.content.Context;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.util.function.Supplier;

/** A class that handles dynamically updating enterprise-related resources. */
@RequiresApi(TIRAMISU)
final class DevicePolicyResources {

    private static final String SAFETY_CENTER_PREFIX = "SafetyCenter.";
    private static final String WORK_PROFILE_PAUSED_TITLE = "WORK_PROFILE_PAUSED";

    /**
     * Returns the updated string for the given {@code safetySourceId} by calling {@link
     * DevicePolicyResourcesManager#getString}.
     */
    @NonNull
    public static String getSafetySourceWorkString(
            @NonNull SafetyCenterResourcesContext safetyCenterResourcesContext,
            @NonNull String safetySourceId,
            @StringRes int workResId) {
        return getEnterpriseString(
                safetyCenterResourcesContext,
                safetySourceId,
                () -> safetyCenterResourcesContext.getString(workResId));
    }

    /**
     * Returns the updated string for the {@code work_profile_paused} string by calling {@link
     * DevicePolicyResourcesManager#getString}.
     */
    @NonNull
    public static String getWorkProfilePausedString(
            @NonNull SafetyCenterResourcesContext safetyCenterResourcesContext) {
        return getEnterpriseString(
                safetyCenterResourcesContext,
                WORK_PROFILE_PAUSED_TITLE,
                () -> safetyCenterResourcesContext.getStringByName("work_profile_paused"));
    }

    @NonNull
    private static String getEnterpriseString(
            @NonNull Context context,
            @NonNull String devicePolicyIdentifier,
            @NonNull Supplier<String> defaultValueLoader) {
        return context.getSystemService(DevicePolicyManager.class)
                .getResources()
                .getString(SAFETY_CENTER_PREFIX + devicePolicyIdentifier, defaultValueLoader);
    }
}
