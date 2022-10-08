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
import com.android.permission.access.AccessDecisions
import com.android.permission.access.AccessState
import com.android.permission.access.AccessUri
import com.android.permission.access.PermissionUri
import com.android.permission.access.SchemePolicy
import com.android.permission.access.UidUri
import com.android.permission.access.UserState
import com.android.permission.access.data.Permission
import com.android.permission.access.external.PackageInfoUtils
import com.android.permission.access.external.PackageState
import com.android.permission.access.util.* // ktlint-disable no-wildcard-imports

private val LOG_TAG = UidPermissionPolicy::class.java.simpleName

class UidPermissionPolicy : SchemePolicy() {
    override val subjectScheme: String
        get() = UidUri.SCHEME

    override val objectScheme: String
        get() = PermissionUri.SCHEME

    override fun getDecision(subject: AccessUri, `object`: AccessUri, state: AccessState): Int {
        subject as UidUri
        `object` as PermissionUri
        val flags = state.userStates[subject.userId]?.permissionFlags?.get(subject.appId)
            ?.get(`object`.permissionName) ?: return AccessDecisions.DENIED
        return when (flags) {
            // TODO
            0 -> AccessDecisions.DENIED
            else -> error(flags)
        }
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
        val flags = when (decision) {
            // TODO
            AccessDecisions.DENIED -> 0
            else -> error(decision)
        }
        uidFlags[`object`.permissionName] = flags
    }

    override fun onUserAdded(userId: Int, oldState: AccessState, newState: AccessState) {
        // NOTE: This adds UPDATE_PERMISSIONS_REPLACE_PKG
        //updateAllPermissions(StorageManager.UUID_PRIVATE_INTERNAL, true)
    }

    override fun onUserRemoved(userId: Int, oldState: AccessState, newState: AccessState) {}

    override fun onAppIdAdded(appId: Int, oldState: AccessState, newState: AccessState) {
        // TODO
    }

    override fun onAppIdRemoved(appId: Int, oldState: AccessState, newState: AccessState) {
        // TODO
    }

    override fun onPackageAdded(
        packageState: PackageState,
        oldState: AccessState,
        newState: AccessState
    ) {
        adoptPermissions(packageState, newState)
        addPermissionGroups(packageState, newState)
        // TODO: addPermissions(packageState, newState)
        // TODO: revokeStoragePermissionsIfScopeExpandedInternal()
        // TODO: Changed: updatePermissions() equivalent here to create permission state
        //  immediately.
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
                    val newPermission = Permission(newPermissionInfo, oldPermission.type, false)
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

    override fun onPackageRemoved(
        packageState: PackageState,
        oldState: AccessState,
        newState: AccessState
    ) {
        // TODO
    }
}
