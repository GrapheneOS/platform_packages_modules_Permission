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

package com.android.role.controller.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;

/**
 * Utility methods for dealing with {@link Context}s.
 */
public final class ContextUtils {

    private ContextUtils() {}

    /**
     * Create a Context for the PermissionController app for the given user.
     *
     * @param user the user of the application
     * @param context the context to clone
     *
     * @return The PermissionController context for the given user
     */
    @NonNull
    public static Context getPermissionControllerContext(@NonNull UserHandle user,
            @NonNull Context context) {
        if (!SdkLevel.isAtLeastV()) {
            // We don't have the getPermissionControllerPackageName() API below V,
            // but role controller always runs in PermissionController and in its own user below V.
            return context;
        }
        String packageName = context.getPackageManager().getPermissionControllerPackageName();
        try {
            return context.createPackageContextAsUser(packageName, 0, user);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Cannot create PermissionController context", e);
        }
    }
}
