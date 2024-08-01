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

package com.android.permissioncontroller.appops.data.repository.v31

import android.app.AppOpsManager
import android.app.Application
import android.os.UserHandle
import android.permission.flags.Flags
import android.util.Log
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel.AppOpUsageModel
import com.android.permissioncontroller.data.repository.v31.AppOpChangeListener
import com.android.permissioncontroller.data.repository.v31.PackageChangeListener
import com.android.permissioncontroller.data.repository.v31.PermissionChangeListener
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.utils.PermissionMapping
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * This repository encapsulate app op data (i.e. app op usage, app op mode, historical ops etc.)
 * exposed by [AppOpsManager].
 */
interface AppOpRepository {
    /**
     * A flow/stream of package app ops, these app ops are processed to show the usage statistics in
     * the privacy dashboard.
     *
     * @see AppOpsManager.getPackagesForOps
     */
    val packageAppOpsUsages: Flow<List<PackageAppOpUsageModel>>

    companion object {
        @Volatile private var instance: AppOpRepository? = null

        fun getInstance(
            application: Application,
            permissionRepository: PermissionRepository
        ): AppOpRepository =
            instance
                ?: synchronized(this) {
                    AppOpRepositoryImpl(application, permissionRepository).also { instance = it }
                }
    }
}

class AppOpRepositoryImpl(
    application: Application,
    private val permissionRepository: PermissionRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AppOpRepository {
    private val appOpsManager =
        checkNotNull(application.getSystemService(AppOpsManager::class.java))
    private val packageManager = application.packageManager

    private val appOpNames = getPrivacyDashboardAppOpNames()

    override val packageAppOpsUsages by lazy {
        callbackFlow {
                send(getPackageOps())

                fun sendUpdate() {
                    trySend(getPackageOps())
                }

                val appOpListener = AppOpChangeListener(appOpNames, appOpsManager) { sendUpdate() }
                val packageListener = PackageChangeListener { sendUpdate() }
                val permissionListener = PermissionChangeListener(packageManager) { sendUpdate() }
                packageListener.register()
                appOpListener.register()
                permissionListener.register()
                awaitClose {
                    appOpListener.unregister()
                    packageListener.unregister()
                    permissionListener.unregister()
                }
            }
            .flowOn(dispatcher)
    }

    private fun getPackageOps(): List<PackageAppOpUsageModel> {
        return try {
                appOpsManager.getPackagesForOps(appOpNames.toTypedArray())
            } catch (e: NullPointerException) {
                Log.w(LOG_TAG, "App ops not recognized, app ops list: $appOpNames")
                // Older builds may not support all requested app ops.
                emptyList()
            }
            .map { packageOps ->
                PackageAppOpUsageModel(
                    packageOps.packageName,
                    packageOps.ops.map { opEntry ->
                        AppOpUsageModel(
                            opEntry.opStr,
                            opEntry.getLastAccessTime(OPS_LAST_ACCESS_FLAGS)
                        )
                    },
                    UserHandle.getUserHandleForUid(packageOps.uid).identifier
                )
            }
    }

    private fun getPrivacyDashboardAppOpNames(): Set<String> {
        val permissionGroups = permissionRepository.getPermissionGroupsForPrivacyDashboard()
        val opNames = mutableSetOf<String>()
        for (permissionGroup in permissionGroups) {
            val permissionNames =
                PermissionMapping.getPlatformPermissionNamesOfGroup(permissionGroup)
            for (permissionName in permissionNames) {
                val opName = AppOpsManager.permissionToOp(permissionName) ?: continue
                opNames.add(opName)
            }
        }

        opNames.add(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE)
        opNames.add(AppOpsManager.OPSTR_PHONE_CALL_CAMERA)
        if (SdkLevel.isAtLeastT()) {
            opNames.add(AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO)
        }
        if (SdkLevel.isAtLeastV() && Flags.locationBypassPrivacyDashboardEnabled()) {
            opNames.add(AppOpsManager.OPSTR_EMERGENCY_LOCATION)
        }
        return opNames
    }

    companion object {
        private const val LOG_TAG = "AppOpUsageRepository"

        private const val OPS_LAST_ACCESS_FLAGS =
            AppOpsManager.OP_FLAG_SELF or
                AppOpsManager.OP_FLAG_TRUSTED_PROXIED or
                AppOpsManager.OP_FLAG_TRUSTED_PROXY
    }
}
