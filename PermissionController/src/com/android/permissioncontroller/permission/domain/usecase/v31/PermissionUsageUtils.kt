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

package com.android.permissioncontroller.permission.domain.usecase.v31

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.permission.flags.Flags
import androidx.annotation.VisibleForTesting
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import com.android.permissioncontroller.user.data.repository.v31.UserRepository

/** Returns a list of users which can be shown in the Settings surfaces. */
suspend fun List<Int>.filterUsersToShowInQuietMode(userRepository: UserRepository): List<Int> {
    if (!SdkLevel.isAtLeastV()) {
        return this
    }
    val usersQuietModeEnabledMap =
        this.associateWith { userId -> userRepository.isQuietModeEnabled(userId) }
    val usersShouldShowInQuietModeMap =
        this.associateWith { userId -> userRepository.shouldShowInQuietMode(userId) }

    return filter {
        val isQuietModeEnabled = checkNotNull(usersQuietModeEnabledMap[it])
        val shouldShowInQuietMode = checkNotNull(usersShouldShowInQuietModeMap[it])
        !isQuietModeEnabled || shouldShowInQuietMode
    }
}

/**
 * Determines if an app's permission group is user-sensitive. if the permission group is not user
 * sensitive then its only shown when user choose `Show system` option
 */
suspend fun isPermissionGroupUserSensitive(
    packageName: String,
    permissionGroup: String,
    userId: Int,
    permissionRepository: PermissionRepository,
    packageRepository: PackageRepository,
): Boolean {
    if (isTelecomPackage(packageName, permissionGroup)) {
        return false
    }
    val userHandle = UserHandle.of(userId)
    val packageInfo = packageRepository.getPackageInfo(packageName, userHandle) ?: return false
    // if not a system app, the permission group must be user sensitive
    if (packageInfo.applicationFlags and ApplicationInfo.FLAG_SYSTEM == 0) {
        return true
    }

    packageInfo.requestedPermissions.forEachIndexed { index, permissionName ->
        if (PermissionMapping.getGroupOfPlatformPermission(permissionName) == permissionGroup) {
            val permFlags =
                permissionRepository.getPermissionFlags(permissionName, packageName, userHandle)
            val packageFlags = packageInfo.requestedPermissionsFlags[index]
            val isPermissionGranted =
                packageFlags and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 &&
                    permFlags and PackageManager.FLAG_PERMISSION_REVOKED_COMPAT == 0
            if (isPermissionUserSensitive(isPermissionGranted, permFlags)) {
                return true
            }
        }
    }
    return false
}

private fun isPermissionUserSensitive(isPermissionGranted: Boolean, permissionFlags: Int): Boolean {
    return if (isPermissionGranted) {
        permissionFlags and PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED != 0
    } else {
        permissionFlags and PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED != 0
    }
}

@VisibleForTesting const val TELECOM_PACKAGE = "com.android.server.telecom"

private fun isTelecomPackage(packageName: String, permissionGroup: String): Boolean {
    return packageName == TELECOM_PACKAGE &&
        (permissionGroup == Manifest.permission_group.CAMERA ||
            permissionGroup == Manifest.permission_group.MICROPHONE)
}

fun isLocationByPassEnabled(): Boolean =
    SdkLevel.isAtLeastV() && Flags.locationBypassPrivacyDashboardEnabled()
