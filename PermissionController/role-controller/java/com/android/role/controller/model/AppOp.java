/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.role.controller.model;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.role.controller.util.PackageUtils;

import java.util.Objects;

/**
 * An app op to be granted or revoke by a {@link Role}.
 */
public class AppOp {

    /**
     * The name of this app op.
     */
    @NonNull
    private final String mName;

    /**
     * The maximum target SDK version for this app op to be granted, or {@code null} if none.
     */
    @Nullable
    private final Integer mMaxTargetSdkVersion;

    /**
     * The mode of this app op when granted.
     */
    private final int mMode;

    public AppOp(@NonNull String name, @Nullable Integer maxTargetSdkVersion, int mode) {
        mName = name;
        mMaxTargetSdkVersion = maxTargetSdkVersion;
        mMode = mode;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @Nullable
    public Integer getMaxTargetSdkVersion() {
        return mMaxTargetSdkVersion;
    }

    public int getMode() {
        return mMode;
    }

    /**
     * Grant this app op to an application.
     *
     * @param packageName the package name of the application
     * @param user the user of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether any app mode has changed
     */
    public boolean grantAsUser(@NonNull String packageName, @NonNull UserHandle user,
            @NonNull Context context) {
        if (!checkTargetSdkVersionAsUser(packageName, user, context)) {
            return false;
        }
        return Permissions.setAppOpUidModeAsUser(packageName, mName, mMode, user, context);
    }

    /**
     * Revoke this app op from an application.
     *
     * @param packageName the package name of the application
     * @param user the user of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether any app mode has changed
     */
    public boolean revokeAsUser(@NonNull String packageName, @NonNull UserHandle user,
            @NonNull Context context) {
        if (!checkTargetSdkVersionAsUser(packageName, user, context)) {
            return false;
        }
        int defaultMode = Permissions.getDefaultAppOpMode(mName);
        return Permissions.setAppOpUidModeAsUser(packageName, mName, defaultMode, user, context);
    }

    private boolean checkTargetSdkVersionAsUser(@NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        if (mMaxTargetSdkVersion == null) {
            return true;
        }
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfoAsUser(packageName, user,
                context);
        if (applicationInfo == null) {
            return false;
        }
        return applicationInfo.targetSdkVersion <= mMaxTargetSdkVersion;
    }

    @Override
    public String toString() {
        return "AppOp{"
                + "mName='" + mName + '\''
                + ", mMaxTargetSdkVersion=" + mMaxTargetSdkVersion
                + ", mMode=" + mMode
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        AppOp appOp = (AppOp) object;
        return mMode == appOp.mMode
                && Objects.equals(mName, appOp.mName)
                && Objects.equals(mMaxTargetSdkVersion, appOp.mMaxTargetSdkVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mMaxTargetSdkVersion, mMode);
    }
}
