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

package com.android.permissioncontroller.permission.domain.usecase.v31

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel
import com.android.permissioncontroller.appops.data.repository.v31.AppOpRepository
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.domain.model.v31.PackagePermissionGroupUsageModel
import com.android.permissioncontroller.permission.domain.model.v31.PermissionGroupUsageModel
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import com.android.permissioncontroller.role.data.repository.v31.RoleRepository
import com.android.permissioncontroller.user.data.repository.v31.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case read app ops data and transform that data to show the private data access by apps
 * in privacy dashboard.
 */
class GetPermissionGroupUsageUseCase(
    private val packageRepository: PackageRepository,
    private val permissionRepository: PermissionRepository,
    private val appOpRepository: AppOpRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
) {
    /**
     * Returns a flow (i.e. a stream) of permission group usages (i.e. the private data accesses)
     * for privacy dashboard page.
     */
    operator fun invoke(): Flow<List<PermissionGroupUsageModel>> {
        return appOpRepository.packageAppOpsUsages.map { packagesOps ->
            val exemptedPackages = roleRepository.getExemptedPackages()
            val currentUsers = userRepository.getUserProfilesIncludingCurrentUser()

            packagesOps
                .mapToPermissionGroups()
                .filter { it.userId in currentUsers }
                .filter { it.packageName !in exemptedPackages }
                .filterQuietProfilesIfNeeded(currentUsers)
                .filterNonRequestedOps()
                .buildPermissionGroupUsageModels()
        }
    }

    /** filter private space usages if needed. */
    private suspend fun List<PackagePermissionGroupUsageModel>.filterQuietProfilesIfNeeded(
        currentUsers: List<Int>
    ): List<PackagePermissionGroupUsageModel> {
        if (!SdkLevel.isAtLeastV()) {
            return this
        }
        val usersQuietModeEnabledMap =
            currentUsers.associateWith { userId -> userRepository.isQuietModeEnabled(userId) }
        val usersShouldShowInQuietModeMap =
            currentUsers.associateWith { userId -> userRepository.shouldShowInQuietMode(userId) }
        return filter {
            val isQuietModeEnabled = checkNotNull(usersQuietModeEnabledMap[it.userId])
            val shouldShowInQuietMode = checkNotNull(usersShouldShowInQuietModeMap[it.userId])
            !isQuietModeEnabled || shouldShowInQuietMode
        }
    }

    private fun List<PackageAppOpUsageModel>.mapToPermissionGroups():
        List<PackagePermissionGroupUsageModel> {
        return mapNotNull { packageOps ->
            val permissionGroupUsages =
                packageOps.usages
                    .mapNotNull {
                        val permissionGroup =
                            PermissionMapping.getPlatformPermissionGroupForOp(it.appOpName)
                        if (permissionGroup != null) {
                            Pair(permissionGroup, it.lastAccessTimestampMillis)
                        } else {
                            Log.w(LOG_TAG, "No permission group found for op: ${it.appOpName}")
                            null
                        }
                    }
                    .groupBy { it.first } // group by permission group name
                    .map { it -> // keep permission group and recent usage time
                        it.key to it.value.map { it.second }.maxOf { it }
                    }
                    .toMap()

            if (permissionGroupUsages.isNotEmpty()) {
                PackagePermissionGroupUsageModel(
                    packageOps.packageName,
                    permissionGroupUsages,
                    packageOps.userId
                )
            } else {
                null
            }
        }
    }

    /** Filter Ops where the corresponding permission group is no longer requested by the package */
    private suspend fun List<PackagePermissionGroupUsageModel>.filterNonRequestedOps():
        List<PackagePermissionGroupUsageModel> {
        return mapNotNull { pkgOps ->
            val userHandle = UserHandle.of(pkgOps.userId)
            val packageInfo = packageRepository.getPackageInfo(pkgOps.packageName, userHandle)
            val filteredOps =
                pkgOps.usages.filter { permissionGroupUsage ->
                    packageInfo?.requestedPermissions?.any { permission ->
                        permissionGroupUsage.key ==
                            PermissionMapping.getGroupOfPlatformPermission(permission)
                    }
                        ?: false
                }
            if (filteredOps.isNotEmpty()) {
                PackagePermissionGroupUsageModel(pkgOps.packageName, filteredOps, pkgOps.userId)
            } else {
                null
            }
        }
    }

    private suspend fun List<PackagePermissionGroupUsageModel>.buildPermissionGroupUsageModels():
        List<PermissionGroupUsageModel> {
        return flatMap { pkgOps ->
            pkgOps.usages.map { permGroupLastAccessTimeEntry ->
                PermissionGroupUsageModel(
                    permGroupLastAccessTimeEntry.key,
                    permGroupLastAccessTimeEntry.value,
                    isPermissionGroupUserSensitive(
                        pkgOps.packageName,
                        permGroupLastAccessTimeEntry.key,
                        pkgOps.userId
                    )
                )
            }
        }
    }

    /**
     * Determines if an app's permission group is user-sensitive. if the permission group is not
     * user sensitive then its only shown when user choose `Show system` option
     */
    private suspend fun isPermissionGroupUserSensitive(
        packageName: String,
        permissionGroup: String,
        userId: Int
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

    private fun isTelecomPackage(packageName: String, permissionGroup: String): Boolean {
        return packageName == TELECOM_PACKAGE &&
            (permissionGroup == Manifest.permission_group.CAMERA ||
                permissionGroup == Manifest.permission_group.MICROPHONE)
    }

    private fun isPermissionUserSensitive(
        isPermissionGranted: Boolean,
        permissionFlags: Int
    ): Boolean {
        return if (isPermissionGranted) {
            permissionFlags and PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED != 0
        } else {
            permissionFlags and PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED != 0
        }
    }

    companion object {
        private val LOG_TAG = GetPermissionGroupUsageUseCase::class.java.simpleName
        private const val TELECOM_PACKAGE = "com.android.server.telecom"

        fun create(app: Application): GetPermissionGroupUsageUseCase {
            val permissionRepository = PermissionRepository.getInstance(app)
            val userRepository = UserRepository.getInstance(app)
            val packageRepository = PackageRepository.getInstance(app)
            val roleRepository = RoleRepository.getInstance(app)
            val appOpRepository = AppOpRepository.getInstance(app, permissionRepository)

            return GetPermissionGroupUsageUseCase(
                packageRepository,
                permissionRepository,
                appOpRepository,
                roleRepository,
                userRepository
            )
        }
    }
}
