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
import android.app.AppOpsManager.AttributedOpEntry
import android.app.AppOpsManager.HISTORY_FLAG_DISCRETE
import android.app.AppOpsManager.HISTORY_FLAG_GET_ATTRIBUTION_CHAINS
import android.app.AppOpsManager.HistoricalOps
import android.app.AppOpsManager.HistoricalOpsRequest
import android.app.AppOpsManager.OP_FLAG_SELF
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXY
import android.app.Application
import android.os.UserHandle
import android.permission.flags.Flags
import android.util.Log
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.appops.data.model.v31.DiscretePackageOpsModel
import com.android.permissioncontroller.appops.data.model.v31.DiscretePackageOpsModel.DiscreteOpModel
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel.AppOpUsageModel
import com.android.permissioncontroller.data.repository.v31.AppOpChangeListener
import com.android.permissioncontroller.data.repository.v31.PackageChangeListener
import com.android.permissioncontroller.data.repository.v31.PermissionChangeListener
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.utils.PermissionMapping
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

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

    /**
     * Returns a flow of discrete package ops.
     *
     * @param opNames A list of app op names.
     * @param coroutineScope the coroutine scope where we fetch the data asynchronously.
     */
    fun getDiscreteOps(
        opNames: List<String>,
        coroutineScope: CoroutineScope
    ): Flow<List<DiscretePackageOpsModel>>

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

    override fun getDiscreteOps(
        opNames: List<String>,
        coroutineScope: CoroutineScope
    ): Flow<List<DiscretePackageOpsModel>> {
        return callbackFlow {
                var job: Job? = null
                send(getDiscreteOps(opNames))

                fun sendUpdate() {
                    if (job == null || job?.isActive == false) {
                        job = coroutineScope.launch { trySend(getDiscreteOps(opNames)) }
                    }
                }

                val appOpListener =
                    AppOpChangeListener(opNames.toSet(), appOpsManager) { sendUpdate() }
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

    private suspend fun getDiscreteOps(opNames: List<String>): List<DiscretePackageOpsModel> {
        val duration =
            if (DeviceUtils.isHandheld()) TimeUnit.DAYS.toMillis(7) else TimeUnit.DAYS.toMillis(1)
        val currentTime = System.currentTimeMillis()
        val beginTimeMillis = currentTime - duration
        val request =
            HistoricalOpsRequest.Builder(beginTimeMillis, currentTime)
                .setFlags(OP_FLAG_SELF or OP_FLAG_TRUSTED_PROXIED)
                .setOpNames(opNames)
                .setHistoryFlags(HISTORY_FLAG_DISCRETE or HISTORY_FLAG_GET_ATTRIBUTION_CHAINS)
                .build()
        val historicalOps: HistoricalOps = suspendCoroutine {
            appOpsManager.getHistoricalOps(request, { it.run() }) { ops: HistoricalOps ->
                it.resumeWith(Result.success(ops))
            }
        }
        val discreteOpsResult = mutableListOf<DiscretePackageOpsModel>()
        // Read through nested (uid -> package name -> attribution tag -> op -> discrete events)
        // historical ops data structure
        for (uidIndex in 0 until historicalOps.uidCount) {
            val historicalUidOps = historicalOps.getUidOpsAt(uidIndex)
            val userId = UserHandle.getUserHandleForUid(historicalUidOps.uid).identifier
            for (packageIndex in 0 until historicalUidOps.packageCount) {
                val historicalPackageOps = historicalUidOps.getPackageOpsAt(packageIndex)
                val packageName = historicalPackageOps.packageName
                val appOpEvents = mutableListOf<DiscreteOpModel>()
                for (tagIndex in 0 until historicalPackageOps.attributedOpsCount) {
                    val attributedHistoricalOps = historicalPackageOps.getAttributedOpsAt(tagIndex)
                    for (opIndex in 0 until attributedHistoricalOps.opCount) {
                        val historicalOp = attributedHistoricalOps.getOpAt(opIndex)
                        for (index in 0 until historicalOp.discreteAccessCount) {
                            val attributedOpEntry: AttributedOpEntry =
                                historicalOp.getDiscreteAccessAt(index)
                            val proxy = attributedOpEntry.getLastProxyInfo(OPS_LAST_ACCESS_FLAGS)
                            val opEvent =
                                DiscreteOpModel(
                                    opName = historicalOp.opName,
                                    accessTimeMillis =
                                        attributedOpEntry.getLastAccessTime(OPS_LAST_ACCESS_FLAGS),
                                    durationMillis =
                                        attributedOpEntry.getLastDuration(OPS_LAST_ACCESS_FLAGS),
                                    attributionTag = attributedHistoricalOps.tag,
                                    proxyPackageName = proxy?.packageName,
                                    proxyUserId =
                                        proxy?.uid?.let {
                                            UserHandle.getUserHandleForUid(it).identifier
                                        },
                                )
                            appOpEvents.add(opEvent)
                        }
                    }
                }
                discreteOpsResult.add(DiscretePackageOpsModel(packageName, userId, appOpEvents))
            }
        }
        return discreteOpsResult
    }

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
            OP_FLAG_SELF or OP_FLAG_TRUSTED_PROXIED or OP_FLAG_TRUSTED_PROXY
    }
}
