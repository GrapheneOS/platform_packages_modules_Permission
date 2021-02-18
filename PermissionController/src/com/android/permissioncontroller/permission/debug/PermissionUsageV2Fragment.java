/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.debug;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;

/**
 * This is a V2 version of the permission usage page. WIP.
 */
public class PermissionUsageV2Fragment extends SettingsWithLargeHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback {

    /**
     * @return A new fragment
     */
    public static @NonNull PermissionUsageV2Fragment newInstance() {
        PermissionUsageV2Fragment fragment = new PermissionUsageV2Fragment();
        Bundle arguments = new Bundle();
        fragment.setArguments(arguments);

        return fragment;
    }

    @Override
    public void onPermissionUsagesChanged() {}
}
