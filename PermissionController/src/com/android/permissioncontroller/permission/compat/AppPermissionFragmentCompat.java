/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.permissioncontroller.permission.compat;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_CALLER_NAME;

import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.android.modules.utils.build.SdkLevel;
import com.android.permission.flags.Flags;
import com.android.permissioncontroller.permission.ui.handheld.max35.LegacyAppPermissionFragment;
import com.android.permissioncontroller.permission.ui.handheld.v36.AppPermissionFragment;

/** Helper methods for AppPermissionFragment across SDKs for compatibility. */
public class AppPermissionFragmentCompat {
    public static final String GRANT_CATEGORY = "grant_category";
    public static final String PERSISTENT_DEVICE_ID = "persistent_device_id";

    /**
     * Create an instance of this fragment
     */
    @NonNull
    public static PreferenceFragmentCompat createFragment() {
        if (SdkLevel.isAtLeastV() && Flags.appPermissionFragmentUsesPreferences()) {
            return new AppPermissionFragment();
        } else {
            return new LegacyAppPermissionFragment();
        }
    }

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param packageName   The name of the package
     * @param permName      The name of the permission whose group this fragment is for (optional)
     * @param groupName     The name of the permission group (required if permName not specified)
     * @param userHandle    The user of the app permission group
     * @param caller        The name of the fragment we called from
     * @param sessionId     The current session ID
     * @param grantCategory The grant status of this app permission group. Used to initially set
     *                      the button state
     * @param persistentDeviceId A persistent device ID (optional)
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(@NonNull String packageName,
            @Nullable String permName, @Nullable String groupName,
            @NonNull UserHandle userHandle, @Nullable String caller, long sessionId,
            @Nullable String grantCategory, @Nullable String persistentDeviceId) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        if (groupName == null) {
            arguments.putString(Intent.EXTRA_PERMISSION_NAME, permName);
        } else {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putString(EXTRA_CALLER_NAME, caller);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        arguments.putString(GRANT_CATEGORY, grantCategory);
        arguments.putString(PERSISTENT_DEVICE_ID, persistentDeviceId);
        return arguments;
    }
}
