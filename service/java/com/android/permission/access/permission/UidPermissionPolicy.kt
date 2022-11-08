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

package com.android.permission.access.permission

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.util.Log
import com.android.permission.access.AccessState
import com.android.permission.access.AccessUri
import com.android.permission.access.PermissionUri
import com.android.permission.access.SchemePolicy
import com.android.permission.access.UidUri
import com.android.permission.access.UserState
import com.android.permission.access.collection.* // ktlint-disable no-wildcard-imports
import com.android.permission.access.data.Permission
import com.android.permission.access.external.PackageInfoUtils
import com.android.permission.access.external.PackageState

private val LOG_TAG = UidPermissionPolicy::class.java.simpleName

class UidPermissionPolicy : SchemePolicy() {
    override val subjectScheme: String
        get() = UidUri.SCHEME

    override val objectScheme: String
        get() = PermissionUri.SCHEME

    override fun getDecision(subject: AccessUri, `object`: AccessUri, state: AccessState): Int {
        subject as UidUri
        `object` as PermissionUri
        return state.userStates[subject.userId]?.permissionFlags?.get(subject.appId)
            ?.get(`object`.permissionName) ?: 0
    }

    override fun setDecision(
        subject: AccessUri,
        `object`: AccessUri,
        decision: Int,
        oldState: AccessState,
        newState: AccessState
    ) {
        subject as UidUri
        `object` as PermissionUri
        val uidFlags = newState.userStates.getOrPut(subject.userId) { UserState() }
            .permissionFlags.getOrPut(subject.appId) { IndexedMap() }
        uidFlags[`object`.permissionName] = decision
    }

    override fun onUserAdded(userId: Int, oldState: AccessState, newState: AccessState) {
        newState.systemState.packageStates.forEachValueIndexed {
            _, packageState -> // TODO: evaluatePermissionState()
        }
    }

    override fun onAppIdAdded(appId: Int, oldState: AccessState, newState: AccessState) {
        newState.userStates.forEachIndexed { _, _, userState ->
            userState.permissionFlags.getOrPut(appId) { IndexedMap() }
        }
    }

    override fun onAppIdRemoved(appId: Int, oldState: AccessState, newState: AccessState) {
        newState.userStates.forEachIndexed { _, _, userState -> userState.permissionFlags -= appId }
    }

    override fun onPackageAdded(
        packageState: PackageState,
        oldState: AccessState,
        newState: AccessState
    ) {
        adoptPermissions(packageState, newState)
        addPermissionGroups(packageState, newState)
        addPermissions(packageState, newState)
        // TODO: revokeStoragePermissionsIfScopeExpandedInternal()
        trimPermissions(packageState.packageName, newState)
        // TODO: evaluatePermissionState(packageState, oldState, newState, null)
    }

    private fun adoptPermissions(packageState: PackageState, newState: AccessState) {
        val `package` = packageState.androidPackage!!
        `package`.adoptPermissions.forEachIndexed { _, originalPackageName ->
            val packageName = `package`.packageName
            if (!canAdoptPermissions(packageName, originalPackageName, newState)) {
                return@forEachIndexed
            }
            newState.systemState.permissions.let { permissions ->
                permissions.forEachIndexed { i, permissionName, oldPermission ->
                    if (oldPermission.packageName != originalPackageName) {
                        return@forEachIndexed
                    }
                    @Suppress("DEPRECATION")
                    val newPermissionInfo = PermissionInfo().apply {
                        name = oldPermission.permissionInfo.name
                        this.packageName = packageName
                        protectionLevel = oldPermission.permissionInfo.protectionLevel
                    }
                    val newPermission = Permission(newPermissionInfo, false, oldPermission.type, 0)
                    permissions.setValueAt(i, newPermission)
                }
            }
        }
    }

    private fun canAdoptPermissions(
        packageName: String,
        originalPackageName: String,
        newState: AccessState
    ): Boolean {
        val originalPackageState = newState.systemState.packageStates[originalPackageName]
            ?: return false
        if (!originalPackageState.isSystem) {
            Log.w(
                LOG_TAG, "Unable to adopt permissions from $originalPackageName to $packageName:" +
                    " original package not in system partition"
            )
            return false
        }
        if (originalPackageState.androidPackage != null) {
            Log.w(
                LOG_TAG, "Unable to adopt permissions from $originalPackageName to $packageName:" +
                    " original package still exists"
            )
            return false
        }
        return true
    }

    private fun addPermissionGroups(packageState: PackageState, newState: AccessState) {
        // Different from the old implementation, which decides whether the app is an instant app by
        // the install flags, now for consistent behavior we allow adding permission groups if the
        // app is non-instant in at least one user.
        val isInstantApp = packageState.userStates.allIndexed { _, _, it -> it.isInstantApp }
        if (isInstantApp) {
            Log.w(
                LOG_TAG, "Ignoring permission groups declared in package" +
                    " ${packageState.packageName}: instant apps cannot declare permission groups"
            )
            return
        }
        packageState.androidPackage!!.permissionGroups.forEachIndexed { _, parsedPermissionGroup ->
            val newPermissionGroup = PackageInfoUtils.generatePermissionGroupInfo(
                parsedPermissionGroup, PackageManager.GET_META_DATA.toLong()
            )
            // TODO: Clear permission state on group take-over?
            val permissionGroupName = newPermissionGroup.name
            val oldPermissionGroup = newState.systemState.permissionGroups[permissionGroupName]
            if (oldPermissionGroup != null &&
                newPermissionGroup.packageName != oldPermissionGroup.packageName) {
                Log.w(
                    LOG_TAG, "Ignoring permission group $permissionGroupName declared in package" +
                        " ${newPermissionGroup.packageName}: already declared in another package" +
                        " ${oldPermissionGroup.packageName}"
                )
                return@forEachIndexed
            }
            newState.systemState.permissionGroups[permissionGroupName] = newPermissionGroup
        }
    }

    private fun addPermissions(packageState: PackageState, newState: AccessState) {
        packageState.androidPackage!!.permissions.forEachIndexed { _, parsedPermission ->
            // TODO:
            // parsedPermission.flags = parsedPermission.flags andInv PermissionInfo.FLAG_INSTALLED
            // TODO: This seems actually unused.
            // if (packageState.androidPackage.targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1) {
            //    parsedPermission.setParsedPermissionGroup(
            //        newState.systemState.permissionGroup[parsedPermission.group]
            //    )
            // }
            val newPermissionInfo = PackageInfoUtils.generatePermissionInfo(
                parsedPermission, PackageManager.GET_META_DATA.toLong()
            )
            // TODO: newPermissionInfo.flags |= PermissionInfo.FLAG_INSTALLED
            val permissionName = newPermissionInfo.name
            val oldPermission = if (parsedPermission.isTree) {
                newState.systemState.permissionTrees[permissionName]
            } else {
                newState.systemState.permissions[permissionName]
            }
            // Different from the old implementation, which may add an (incomplete) signature
            // permission inside another package's permission tree, we now consistently ignore such
            // permissions.
            val permissionTree = getPermissionTree(permissionName, newState)
            val newPackageName = newPermissionInfo.packageName
            if (permissionTree != null && newPackageName != permissionTree.packageName) {
                Log.w(
                    LOG_TAG, "Ignoring permission $permissionName declared in package" +
                        " $newPackageName: base permission tree ${permissionTree.name} is" +
                        " declared in another package ${permissionTree.packageName}"
                )
                return@forEachIndexed
            }
            val newPermission = if (oldPermission != null &&
                newPackageName != oldPermission.packageName) {
                val oldPackageName = oldPermission.packageName
                // Only allow system apps to redefine non-system permissions.
                if (!packageState.isSystem) {
                    Log.w(
                        LOG_TAG, "Ignoring permission $permissionName declared in package" +
                            " $newPackageName: already declared in another package" +
                            " $oldPackageName"
                    )
                    return@forEachIndexed
                }
                if (oldPermission.type == Permission.TYPE_CONFIG &&
                    !oldPermission.isReconciled) {
                    // It's a config permission and has no owner, take ownership now.
                    Permission(newPermissionInfo, true, Permission.TYPE_CONFIG, packageState.appId)
                } else if (newState.systemState.packageStates[oldPackageName]?.isSystem != true) {
                    Log.w(
                        LOG_TAG, "Overriding permission $permissionName with new declaration in" +
                            " system package $newPackageName: originally declared in another" +
                            " package $oldPackageName"
                    )
                    // Remove permission state on owner change.
                    newState.userStates.forEachValueIndexed { _, userState ->
                        userState.permissionFlags.forEachValueIndexed { _, permissionFlags ->
                            permissionFlags -= newPermissionInfo.name
                        }
                    }
                    // TODO: Notify re-evaluation of this permission.
                    Permission(
                        newPermissionInfo, true, Permission.TYPE_MANIFEST, packageState.appId
                    )
                } else {
                    Log.w(
                        LOG_TAG, "Ignoring permission $permissionName declared in system package" +
                            " $newPackageName: already declared in another system package" +
                            " $oldPackageName")
                    return@forEachIndexed
                }
            } else {
                // TODO: STOPSHIP: Clear permission state on type or group change.
                // Different from the old implementation, which doesn't update the permission
                // definition upon app update, but does update it on the next boot, we now
                // consistently update the permission definition upon app update.
                Permission(newPermissionInfo, true, Permission.TYPE_MANIFEST, packageState.appId)
            }

            if (parsedPermission.isTree) {
                newState.systemState.permissionTrees[permissionName] = newPermission
            } else {
                newState.systemState.permissions[permissionName] = newPermission
            }
        }
    }

    private fun trimPermissions(packageName: String, newState: AccessState) {
        val packageState = newState.systemState.packageStates[packageName]
        val androidPackage = packageState?.androidPackage
        if (packageState != null && androidPackage == null) {
            return
        }

        newState.systemState.permissionTrees.removeAllIndexed {
            _, permissionTreeName, permissionTree ->
            permissionTree.packageName == packageName && (
                packageState == null || androidPackage!!.permissions.noneIndexed { _, it ->
                    it.isTree && it.name == permissionTreeName
                }
            )
        }

        newState.systemState.permissions.removeAllIndexed { i, permissionName, permission ->
            val updatedPermission = updatePermissionIfDynamic(permission, newState)
            newState.systemState.permissions.setValueAt(i, updatedPermission)
            if (updatedPermission.packageName == packageName && (
                packageState == null || androidPackage!!.permissions.noneIndexed { _, it ->
                    !it.isTree && it.name == permissionName
                }
            )) {
                if (!isPermissionDeclaredByDisabledSystemPackage(permission, newState)) {
                    newState.userStates.forEachValueIndexed { _, userState ->
                        userState.permissionFlags.forEachValueIndexed { _, permissionFlags ->
                            permissionFlags.putWithDefault(
                                permissionName, permissionFlags.getWithDefault(
                                    permissionName, 0
                                ) and PermissionFlags.INSTALL_REVOKED, 0
                            )
                        }
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun isPermissionDeclaredByDisabledSystemPackage(
        permission: Permission,
        newState: AccessState
    ): Boolean {
        val disabledSystemPackage = newState.systemState
            .disabledSystemPackageStates[permission.packageName]?.androidPackage ?: return false
        return disabledSystemPackage.permissions.anyIndexed { _, it ->
            it.name == permission.name && it.protectionLevel == permission.protectionLevel
        }
    }

    private fun updatePermissionIfDynamic(
        permission: Permission,
        newState: AccessState
    ): Permission {
        if (!permission.isDynamic) {
            return permission
        }
        val permissionTree = getPermissionTree(permission.name, newState) ?: return permission
        @Suppress("DEPRECATION")
        return permission.copy(
            permissionInfo = PermissionInfo(permission.permissionInfo).apply {
                packageName = permissionTree.packageName
            }, appId = permissionTree.appId, isReconciled = true
        )
    }

    private fun getPermissionTree(permissionName: String, newState: AccessState): Permission? =
        newState.systemState.permissionTrees.firstNotNullOfOrNullIndexed {
            _, permissionTreeName, permissionTree ->
            if (permissionName.startsWith(permissionTreeName) &&
                permissionName.length > permissionTreeName.length &&
                permissionName[permissionTreeName.length] == '.') {
                permissionTree
            } else {
                null
            }
        }

    override fun onPackageRemoved(
        packageState: PackageState,
        oldState: AccessState,
        newState: AccessState
    ) {
        // TODO
    }
}
