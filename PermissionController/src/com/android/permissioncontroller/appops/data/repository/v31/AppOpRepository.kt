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
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel.AppOpUsageModel
import com.android.permissioncontroller.permission.data.PackageBroadcastReceiver
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

                // Suppress OnOpNotedListener lint error, startWatchingNoted is behind sdk check.
                @SuppressWarnings("NewApi")
                val callback =
                    object :
                        PackageManager.OnPermissionsChangedListener,
                        PackageBroadcastReceiver.PackageBroadcastListener,
                        AppOpsManager.OnOpActiveChangedListener,
                        AppOpsManager.OnOpNotedListener,
                        AppOpsManager.OnOpChangedListener {
                        override fun onPermissionsChanged(uid: Int) {
                            sendUpdate()
                        }

                        override fun onOpChanged(op: String?, packageName: String?) {
                            sendUpdate()
                        }

                        override fun onPackageUpdate(packageName: String) {
                            sendUpdate()
                        }

                        override fun onOpActiveChanged(
                            op: String,
                            uid: Int,
                            packageName: String,
                            active: Boolean
                        ) {
                            sendUpdate()
                        }

                        override fun onOpNoted(
                            op: String,
                            uid: Int,
                            packageName: String,
                            attributionTag: String?,
                            flags: Int,
                            result: Int
                        ) {
                            sendUpdate()
                        }

                        fun sendUpdate() {
                            trySend(getPackageOps())
                        }
                    }

                packageManager.addOnPermissionsChangeListener(callback)
                PackageBroadcastReceiver.addAllCallback(callback)
                appOpNames.forEach { opName ->
                    // TODO(b/262035952): We watch each active op individually as
                    //  startWatchingActive only registers the callback if all ops are valid.
                    //  Fix this behavior so if one op is invalid it doesn't affect the other ops.
                    try {
                        appOpsManager.startWatchingActive(arrayOf(opName), { it.run() }, callback)
                    } catch (ignored: IllegalArgumentException) {
                        // Older builds may not support all requested app ops.
                    }

                    try {
                        appOpsManager.startWatchingMode(opName, /* all packages */ null, callback)
                    } catch (ignored: IllegalArgumentException) {
                        // Older builds may not support all requested app ops.
                    }

                    if (SdkLevel.isAtLeastU()) {
                        try {
                            appOpsManager.startWatchingNoted(arrayOf(opName), callback)
                        } catch (ignored: IllegalArgumentException) {
                            // Older builds may not support all requested app ops.
                        }
                    }
                }

                awaitClose {
                    packageManager.removeOnPermissionsChangeListener(callback)
                    PackageBroadcastReceiver.removeAllCallback(callback)
                    appOpsManager.stopWatchingActive(callback)
                    appOpsManager.stopWatchingMode(callback)
                    if (SdkLevel.isAtLeastU()) {
                        appOpsManager.stopWatchingNoted(callback)
                    }
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
