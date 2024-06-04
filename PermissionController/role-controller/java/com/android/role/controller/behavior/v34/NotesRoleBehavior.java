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

package com.android.role.controller.behavior.v34;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;
import com.android.role.controller.util.UserUtils;

import java.util.Objects;

/**
 * Class for behavior of the Notes role.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class NotesRoleBehavior implements RoleBehavior {

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        // Role should be enabled by OEMs.
        Resources resources = getResources(context);
        if (!resources.getBoolean(android.R.bool.config_enableDefaultNotes)) {
            return false;
        }

        // Cloned profile shouldn't have a separate role.
        if (UserUtils.isCloneProfile(user, context)) {
            return false;
        }

        if (UserUtils.isManagedProfile(user, context)) {
            // The role holder for work profile is separately controlled via config.
            return resources.getBoolean(android.R.bool.config_enableDefaultNotesForWorkProfile);
        }

        return true;
    }

    /**
     * Gets {@link Resources} for fetching notes role related resources.
     * <p>
     * When running within the system server process, this method retrieves system resource that
     * include Runtime Resource Overlay (RRO) values. These RRO values are not accessible through
     * the {@code context} object provided to this class during construction.
     *
     * @throws RuntimeException when no system package is found when this class runs in the system
     * server process.
     */
    @NonNull
    private static Resources getResources(@NonNull Context context) {
        if (Objects.equals(context.getPackageName(), "android")) {
            try {
                return context.getPackageManager().getResourcesForApplication("system");
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException("System package not found", e);
            }
        } else {
            return context.getResources();
        }
    }
}
