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

import android.app.job.JobScheduler
import android.content.Context
import android.provider.DeviceConfig
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PermissionDecision
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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
     * Remove permission data older than [DEFAULT_MAX_DATA_AGE_MS] milliseconds ago.
     *
     * @return whether the storage was successful
     */
    suspend fun removeOldData(): Boolean

    /**
     * Remove all the permission decisions for a particular package.
     *
     * @param packageName of the package to remove
     * @return whether the storage was successful
     */
    suspend fun removePermissionDecisionsForPackage(packageName: String): Boolean

    /**
     * Update decision timestamps based on the delta in system time. Since
     * [storePermissionDecision] rounds timestamps down to day-level granularity, we only update
     * the date if [diffSystemTimeMillis] is greater than 1 day.
     *
     * @param diffSystemTimeMillis the difference between the current and old system times. Positive
     * values mean that the time has changed in the future and negative means the time was changed
     * into the past.
     * @return whether the storage was successful
     */
    suspend fun updateDecisionsBySystemTimeDelta(diffSystemTimeMillis: Long): Boolean

    companion object {

        val DEFAULT_MAX_DATA_AGE_MS = TimeUnit.DAYS.toMillis(7)

        @Volatile
        private var INSTANCE: RecentPermissionDecisionsStorage? = null

        fun getInstance(): RecentPermissionDecisionsStorage =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance().also { INSTANCE = it }
            }

        private fun createInstance(): RecentPermissionDecisionsStorage {
            return RecentPermissionDecisionsStorageImpl(PermissionControllerApplication.get(),
                PermissionControllerApplication.get().getSystemService(JobScheduler::class.java)!!)
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

        fun getMaxDataAgeMs() =
            DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
                Utils.PROPERTY_PERMISSION_DECISIONS_MAX_DATA_AGE_MILLIS,
                DEFAULT_MAX_DATA_AGE_MS)
    }
}