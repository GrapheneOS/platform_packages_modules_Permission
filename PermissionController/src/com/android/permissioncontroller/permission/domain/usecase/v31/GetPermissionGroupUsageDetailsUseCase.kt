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

package com.android.permissioncontroller.permission.domain.usecase.v31

import android.Manifest
import android.app.AppOpsManager
import android.os.UserHandle
import android.permission.flags.Flags
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.appops.data.model.v31.DiscretePackageOpsModel
import com.android.permissioncontroller.appops.data.model.v31.DiscretePackageOpsModel.DiscreteOpModel
import com.android.permissioncontroller.appops.data.repository.v31.AppOpRepository
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.domain.model.v31.PermissionTimelineUsageModel
import com.android.permissioncontroller.permission.domain.model.v31.PermissionTimelineUsageModelWrapper
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.CLUSTER_SPACING_MINUTES
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.ONE_MINUTE_MS
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import com.android.permissioncontroller.role.data.repository.v31.RoleRepository
import com.android.permissioncontroller.user.data.repository.v31.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case reads discrete app ops data and transform it to show the private data access by
 * apps on permission timeline page.
 */
class GetPermissionGroupUsageDetailsUseCase(
    private val permissionGroup: String,
    private val packageRepository: PackageRepository,
    private val permissionRepository: PermissionRepository,
    private val appOpRepository: AppOpRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
) {
    operator fun invoke(coroutineScope: CoroutineScope): Flow<PermissionTimelineUsageModelWrapper> {
        val opNames = requireNotNull(permissionGroupToOpNames[permissionGroup])
        return appOpRepository.getDiscreteOps(opNames, coroutineScope).map { pkgOps ->
            val currentUsers =
                userRepository
                    .getUserProfilesIncludingCurrentUser()
                    .filterUsersToShowInQuietMode(userRepository)
            val exemptedPackages = roleRepository.getExemptedPackages()

            val filteredPackageOps: List<DiscretePackageOpsModel> =
                pkgOps
                    .filter { packageOps ->
                        packageOps.userId in currentUsers &&
                            packageOps.packageName !in exemptedPackages
                    }
                    .filterPackagesNotRequestingPermission(permissionGroup)
                    .map { packageOps ->
                        packageOps.isUserSensitive =
                            isPermissionGroupUserSensitive(
                                packageOps.packageName,
                                permissionGroup,
                                packageOps.userId,
                                permissionRepository,
                                packageRepository
                            )
                        packageOps
                    }
            val attributedPackageOps: List<DiscretePackageOpsModel> =
                filteredPackageOps.groupByAttributionLabelIfNeeded()
            val clusteredPackageOps: List<DiscretePackageOpsModel> =
                attributedPackageOps.createAccessClusters()
            val permissionTimelineUsageModels: List<PermissionTimelineUsageModel> =
                clusteredPackageOps.buildPermissionTimelineUsage()
            PermissionTimelineUsageModelWrapper.Success(permissionTimelineUsageModels)
        }
    }

    /** Group app op accesses by attribution label if it is available and user visible. */
    private suspend fun List<DiscretePackageOpsModel>.groupByAttributionLabelIfNeeded() =
        map { packageOps ->
                val attributionInfo =
                    packageRepository.getPackageAttributionInfo(
                        packageOps.packageName,
                        UserHandle.of(packageOps.userId)
                    )
                if (attributionInfo != null) {
                    if (attributionInfo.areUserVisible && attributionInfo.tagResourceMap != null) {
                        val attributionLabelOpsMap: Map<String?, List<DiscreteOpModel>> =
                            packageOps.appOpAccesses
                                .map { appOpEntry ->
                                    val resourceId =
                                        attributionInfo.tagResourceMap[appOpEntry.attributionTag]
                                    val label = attributionInfo.resourceLabelMap?.get(resourceId)
                                    label to appOpEntry
                                }
                                .groupBy { labelAppOpEntryPair -> labelAppOpEntryPair.first }
                                .mapValues { (_, values) ->
                                    values.map { labelAppOpEntryPair -> labelAppOpEntryPair.second }
                                }

                        attributionLabelOpsMap.map { labelAppOpsEntry ->
                            DiscretePackageOpsModel(
                                packageOps.packageName,
                                packageOps.userId,
                                appOpAccesses = labelAppOpsEntry.value,
                                attributionLabel = labelAppOpsEntry.key,
                                isUserSensitive = packageOps.isUserSensitive,
                            )
                        }
                    } else {
                        listOf(packageOps)
                    }
                } else {
                    listOf(packageOps)
                }
            }
            .flatten()

    /**
     * App op accesses are merged if the timestamp of the access is within the cluster spacing i.e.
     * one minute.
     */
    private fun List<DiscretePackageOpsModel>.createAccessClusters():
        List<DiscretePackageOpsModel> {
        return flatMap { packageOps ->
            val clusters = mutableListOf<DiscretePackageOpsModel>()
            val currentCluster = mutableListOf<DiscreteOpModel>()
            val sortedOps = packageOps.appOpAccesses.sortedBy { it.accessTimeMillis }
            for (discreteAccess in sortedOps) {
                if (currentCluster.isEmpty()) {
                    currentCluster.add(discreteAccess)
                } else if (!canAccessBeAddedToCluster(discreteAccess, currentCluster)) {
                    clusters.add(
                        DiscretePackageOpsModel(
                            packageOps.packageName,
                            packageOps.userId,
                            currentCluster.toMutableList(),
                            packageOps.attributionLabel,
                            packageOps.isUserSensitive
                        )
                    )
                    currentCluster.clear()
                    currentCluster.add(discreteAccess)
                } else {
                    currentCluster.add(discreteAccess)
                }
            }

            if (currentCluster.isNotEmpty()) {
                clusters.add(
                    DiscretePackageOpsModel(
                        packageOps.packageName,
                        packageOps.userId,
                        currentCluster.toMutableList(),
                        packageOps.attributionLabel,
                        packageOps.isUserSensitive
                    )
                )
            }
            clusters
        }
    }

    private fun List<DiscretePackageOpsModel>.buildPermissionTimelineUsage():
        List<PermissionTimelineUsageModel> {
        return this.map { packageOpsCluster ->
            val startTimeMillis = packageOpsCluster.appOpAccesses.minOf { it.accessTimeMillis }
            // The end minute is exclusive here in terms of access, i.e. [1..5) as the private data
            // was not accessed at minute 5, it helps calculate the duration correctly.
            val endTimeMillis =
                packageOpsCluster.appOpAccesses.maxOf { appOpEvent ->
                    if (appOpEvent.durationMillis > 0)
                        appOpEvent.accessTimeMillis + appOpEvent.durationMillis
                    else appOpEvent.accessTimeMillis + ONE_MINUTE_MS
                }
            val durationMillis = endTimeMillis - startTimeMillis
            val proxy = packageOpsCluster.appOpAccesses.firstOrNull { it.proxyPackageName != null }

            PermissionTimelineUsageModel(
                packageOpsCluster.packageName,
                packageOpsCluster.userId,
                packageOpsCluster.appOpAccesses.map { it.opName }.toSet(),
                startTimeMillis,
                // Make the end time inclusive i.e. [1..4]
                endTimeMillis - ONE_MINUTE_MS,
                durationMillis,
                packageOpsCluster.isUserSensitive,
                packageOpsCluster.attributionLabel,
                packageOpsCluster.appOpAccesses.mapNotNull { it.attributionTag }.toSet(),
                proxy?.proxyPackageName,
                proxy?.proxyUserId,
            )
        }
    }

    private fun isLocationByPassEnabled(): Boolean =
        SdkLevel.isAtLeastV() && Flags.locationBypassPrivacyDashboardEnabled()

    /**
     * Determine if an op should be in its own cluster and hence display as an individual entry in
     * the privacy timeline
     */
    private fun isOpClusteredByItself(opName: String): Boolean {
        if (isLocationByPassEnabled()) {
            return opName == AppOpsManager.OPSTR_EMERGENCY_LOCATION
        }
        return false
    }

    private fun canAccessBeAddedToCluster(
        currentAccess: DiscreteOpModel,
        clusteredAccesses: List<DiscreteOpModel>
    ): Boolean {
        val clusterOp = clusteredAccesses.last().opName
        if (
            (isOpClusteredByItself(currentAccess.opName) || isOpClusteredByItself(clusterOp)) &&
                currentAccess.opName != clusteredAccesses.last().opName
        ) {
            return false
        }
        val currentAccessMinute = currentAccess.accessTimeMillis / ONE_MINUTE_MS
        val prevMostRecentAccessMillis =
            clusteredAccesses.maxOf { discreteAccess ->
                if (discreteAccess.durationMillis > 0)
                // accessTimeMillis and durationMillis are rounded at minute level. if an entry
                // says mic was accessed for 3 minutes at minute 45, then the end time should
                // be minute 47, as the mic was accessed at minute 45, 46, and 47.
                // 45 + 3 - 1 = 47
                discreteAccess.accessTimeMillis + discreteAccess.durationMillis - ONE_MINUTE_MS
                else discreteAccess.accessTimeMillis
            }
        val prevMostRecentAccessMinute = prevMostRecentAccessMillis / ONE_MINUTE_MS
        return (currentAccessMinute - prevMostRecentAccessMinute) <= CLUSTER_SPACING_MINUTES
    }

    private fun getGroupOfPlatformPermission(permission: String): String? {
        if (permission == Manifest.permission.LOCATION_BYPASS) {
            return Manifest.permission_group.LOCATION
        }
        return PermissionMapping.getGroupOfPlatformPermission(permission)
    }

    /**
     * Filter out packages that are not requesting any permission from the permission group anymore.
     */
    private suspend fun List<DiscretePackageOpsModel>.filterPackagesNotRequestingPermission(
        permissionGroup: String
    ): List<DiscretePackageOpsModel> {
        return mapNotNull { packageOpsModel ->
            val userHandle = UserHandle.of(packageOpsModel.userId)
            val packageInfo =
                packageRepository.getPackageInfo(packageOpsModel.packageName, userHandle)
            val isAnyPermissionRequestedFromPermissionGroup =
                packageInfo?.requestedPermissions?.any { permission ->
                    permissionGroup == getGroupOfPlatformPermission(permission)
                } ?: false
            if (isAnyPermissionRequestedFromPermissionGroup) {
                packageOpsModel
            } else {
                null
            }
        }
    }

    companion object {
        val permissionGroupToOpNames: Map<String, List<String>> = permissionGroupToOpNamesMap()

        private fun permissionGroupToOpNamesMap(): Map<String, List<String>> {
            val permissionGroupOpNamesMap = mutableMapOf<String, MutableList<String>>()
            val permissionGroups =
                listOf(
                    Manifest.permission_group.CAMERA,
                    Manifest.permission_group.LOCATION,
                    Manifest.permission_group.MICROPHONE
                )
            permissionGroups.forEach { permissionGroup ->
                val opNames =
                    PermissionMapping.getPlatformPermissionNamesOfGroup(permissionGroup)
                        .mapNotNull { AppOpsManager.permissionToOp(it) }
                        .toMutableList()
                permissionGroupOpNamesMap[permissionGroup] = opNames
            }
            permissionGroupOpNamesMap[Manifest.permission_group.MICROPHONE]?.add(
                AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE
            )
            permissionGroupOpNamesMap[Manifest.permission_group.CAMERA]?.add(
                AppOpsManager.OPSTR_PHONE_CALL_CAMERA
            )
            if (SdkLevel.isAtLeastT()) {
                permissionGroupOpNamesMap[Manifest.permission_group.MICROPHONE]?.add(
                    AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO
                )
            }
            if (isLocationByPassEnabled()) {
                permissionGroupOpNamesMap[Manifest.permission_group.LOCATION]?.add(
                    AppOpsManager.OPSTR_EMERGENCY_LOCATION
                )
            }
            return permissionGroupOpNamesMap.toMap()
        }
    }
}
