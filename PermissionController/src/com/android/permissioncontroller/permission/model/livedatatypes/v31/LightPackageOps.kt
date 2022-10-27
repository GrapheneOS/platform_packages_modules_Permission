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

package com.android.permissioncontroller.permission.model.livedatatypes.v31

import android.app.AppOpsManager.OP_FLAG_SELF
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXY
import android.app.AppOpsManager.OpEntry
import android.app.AppOpsManager.PackageOps
import android.app.AppOpsManager.opToPermission
import android.os.UserHandle
import com.android.permissioncontroller.permission.utils.PermissionMapping.getGroupOfPlatformPermission

/**
 * Light version of [PackageOps] class, tracking the last permission access for system permission
 * groups.
 */
data class LightPackageOps(
    /** Name of the package. */
    val packageName: String,
    /** [UserHandle] running the package. */
    val userHandle: UserHandle,
    /**
     * Mapping of permission group name to the last access time of any op backing a permission in
     * the group.
     */
    val lastPermissionGroupAccessTimesMs: Map<String, Long>
) {
    constructor(
        packageOps: PackageOps
    ) : this(
        packageOps.packageName,
        UserHandle.getUserHandleForUid(packageOps.uid),
        packageOps.ops
            .groupBy { getPermissionGroupForOp(it.opStr) }
            // Filter out ops that have no corresponding permission group
            .filterKeys { it != NO_PERM_GROUP }
            // Map each permission group to the latest access time across all its ops
            .mapValues { getLatestLastAccessTimeMs(it.value) })

    /** Companion object for [LightPackageOps]. */
    companion object {
        /** Key to represent the absence of a permission group in a map. */
        private const val NO_PERM_GROUP = "no_perm_group"
        /** Flags to use for querying an op's last access time. */
        private const val OPS_LAST_ACCESS_FLAGS =
            OP_FLAG_SELF or OP_FLAG_TRUSTED_PROXIED or OP_FLAG_TRUSTED_PROXY

        /**
         * Returns the permission group for the permission that the provided op backs, if any, else
         * returns [NO_PERM_GROUP].
         */
        private fun getPermissionGroupForOp(opName: String) =
            opToPermission(opName)?.let { getGroupOfPlatformPermission(it) } ?: NO_PERM_GROUP

        /** Returns the latest of all last access times of all the provided [OpEntry] instances. */
        private fun getLatestLastAccessTimeMs(opEntries: List<OpEntry>) =
            opEntries.maxOf { it.getLastAccessTime(OPS_LAST_ACCESS_FLAGS) }
    }
}
