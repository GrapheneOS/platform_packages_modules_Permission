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

package com.android.permissioncontroller.permission.service

import android.content.Context
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PermissionDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Persistent storage for retrieving recent permission decisions.
 *
 * Constraints on this database:
 * <li> Rows are unique with (package_name, permission_group) index
 */
interface RecentPermissionDecisionsStorage {
    /**
     * Persist a permission decision for retrieval later. Overwrites an existing row based on
     * (package_name, permission_group) uniqueness.
     *
     * [PermissionDecision.decisionTime] is rounded down to the day so that this isn't a granular
     * record of app usage and we preserve user privacy.
     *
     * @param decision the decision to store
     * @return whether the storage was successful
     */
    suspend fun storePermissionDecision(decision: PermissionDecision): Boolean

    /**
     * Returns all recent permission decisions, sorted from newest to oldest. This returns directly
     * what is in storage and does not do any additional validation checking.
     */
    suspend fun loadPermissionDecisions(): List<PermissionDecision>

    /**
     * Clear all recent decisions.
     */
    suspend fun clearPermissionDecisions()

    /**
     * Remove all the permission decisions for a particular package.
     *
     * @param packageName of the package to remove
     * @return whether the storage was successful
     */
    suspend fun removePermissionDecisionsForPackage(packageName: String): Boolean

    companion object {

        @Volatile
        private var INSTANCE: RecentPermissionDecisionsStorage? = null

        fun getInstance(): RecentPermissionDecisionsStorage =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance().also { INSTANCE = it }
            }

        private fun createInstance(): RecentPermissionDecisionsStorage {
            return RecentPermissionDecisionsStorageImpl(PermissionControllerApplication.get())
        }

        fun recordPermissionDecision(
            context: Context,
            packageName: String,
            permGroupName: String,
            isGranted: Boolean
        ) {
            if (isRecordPermissionsSupported(context)) {
                GlobalScope.launch(Dispatchers.IO) {
                    getInstance().storePermissionDecision(
                        PermissionDecision(packageName, permGroupName, System.currentTimeMillis(),
                            isGranted))
                }
            }
        }

        fun isRecordPermissionsSupported(context: Context): Boolean {
            return DeviceUtils.isAuto(context)
        }
    }
}