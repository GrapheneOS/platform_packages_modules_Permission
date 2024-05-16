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

package com.android.permissioncontroller.permission.service

import android.permission.PermissionManager
import com.android.permissioncontroller.permission.utils.PermissionMapping

/**
 * Takes a list of split permissions, and provides methods that return which split-permissions will
 * be active given an app's targetSdk.
 */
class SplitPermissionIndex() {
    private lateinit var permToGroupSplits: Set<SplitPermissionIndexEntry>
    private lateinit var groupToGroupSplits: Set<SplitPermissionIndexEntry>

    constructor(groupToGroupSplits: Set<SplitPermissionIndexEntry>) : this() {
        this.groupToGroupSplits = groupToGroupSplits
    }

    constructor(splitPermissionInfos: List<PermissionManager.SplitPermissionInfo>) : this() {
        val permToGroupSplits: MutableSet<SplitPermissionIndexEntry> = mutableSetOf()
        val groupToGroupSplits: MutableSet<SplitPermissionIndexEntry> = mutableSetOf()
        for (splitPermissionInfo in splitPermissionInfos) {
            val splitPermission = splitPermissionInfo.splitPermission
            for (newPerm in splitPermissionInfo.newPermissions) {
                val splitPermissionGroup =
                    PermissionMapping.getGroupOfPlatformPermission(splitPermission)
                val newPermGroup = PermissionMapping.getGroupOfPlatformPermission(newPerm)
                if (newPermGroup != null) {
                    permToGroupSplits.add(
                        SplitPermissionIndexEntry(
                            splitPermission,
                            splitPermissionInfo.targetSdk,
                            newPermGroup
                        )
                    )
                }
                if (splitPermissionGroup != null && newPermGroup != null) {
                    groupToGroupSplits.add(
                        SplitPermissionIndexEntry(
                            splitPermissionGroup,
                            splitPermissionInfo.targetSdk,
                            newPermGroup
                        )
                    )
                }
            }
        }
        this.permToGroupSplits = permToGroupSplits
        this.groupToGroupSplits = groupToGroupSplits
    }

    /**
     * Given a split permission, and a package targetSdkVersion, return permission groups of new
     * permissions. See <split-permission> tag.
     *
     * @param splitPermission the split permission (i.e. old permission)
     * @param appTargetSdk app target sdk
     * @return the permission groups calculated from new permissions
     */
    fun getPermissionGroupsFromSplitPermission(
        splitPermission: String,
        appTargetSdk: Int
    ): List<String> {
        return permToGroupSplits
            .filter { it.splitPermissionOrGroup == splitPermission && appTargetSdk < it.targetSdk }
            .map { it.newPermissionGroup }
            .toList()
    }

    /**
     * Given a split permission, and a package targetSdkVersion, return permission groups of new
     * permissions. See <split-permission> tag.
     *
     * @param splitPermissionGroup permission group of a split permission
     * @param appTargetSdk app target sdk
     * @return the permission groups calculated from new permissions
     */
    fun getPermissionGroupsFromSplitPermissionGroup(
        splitPermissionGroup: String,
        appTargetSdk: Int
    ): List<String> {
        return groupToGroupSplits
            .filter {
                it.splitPermissionOrGroup == splitPermissionGroup && appTargetSdk < it.targetSdk
            }
            .map { it.newPermissionGroup }
            .toList()
    }

    /**
     * Given a permission group, and package's target sdk find permission groups of the split
     * permissions, see <split-permission> tag.
     *
     * @param permissionGroup permission group mapped to new permissions in <split-permission> tag
     * @param appTargetSdk app target sdk
     * @return the permission group for the split permissions
     */
    fun getSplitPermissionGroups(permissionGroup: String, appTargetSdk: Int): List<String> {
        return groupToGroupSplits
            .filter { it.newPermissionGroup == permissionGroup && appTargetSdk < it.targetSdk }
            .map { it.splitPermissionOrGroup }
            .toList()
    }

    data class SplitPermissionIndexEntry(
        val splitPermissionOrGroup: String,
        /** The split only applies to app target sdk below this */
        val targetSdk: Int,
        val newPermissionGroup: String
    )
}
