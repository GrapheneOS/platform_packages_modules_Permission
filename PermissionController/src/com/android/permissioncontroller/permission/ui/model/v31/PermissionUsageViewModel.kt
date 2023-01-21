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

package com.android.permissioncontroller.permission.ui.model.v31

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import androidx.annotation.RequiresApi
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.android.permissioncontroller.permission.data.AppPermGroupUiInfoLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.v31.AllLightPackageOpsLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.v31.LightPackageOps
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * [ViewModel] for handheld Permissions Usage UI.
 *
 * Note that this class replaces [PermissionUsageViewModelLegacy] to rely on [LiveData] instead of
 * [PermissionUsages] loader.
 */
// TODO(b/257317510): Remove "new" suffix and deprecate PermissionUsageViewModel.
class PermissionUsageViewModel(
    private val state: SavedStateHandle,
    app: Application,
) : AndroidViewModel(app) {
    private val roleManager =
        Utils.getSystemServiceSafe(app.applicationContext, RoleManager::class.java)
    private val exemptedPackages: Set<String> = Utils.getExemptedPackages(roleManager)

    private val mAllLightPackageOpsLiveData = AllLightPackageOpsLiveData(app)
    private val appPermGroupUiInfoLiveDataList =
        mutableMapOf<AppPermissionId, AppPermGroupUiInfoLiveData>()

    val showSystemLiveData = state.getLiveData(SHOULD_SHOW_SYSTEM_KEY, false)
    val show7DaysLiveData = state.getLiveData(SHOULD_SHOW_7_DAYS_KEY, false)

    /** Updates whether system app permissions usage should be displayed in the UI. */
    fun updateShowSystem(showSystem: Boolean) {
        if (showSystem != state[SHOULD_SHOW_SYSTEM_KEY]) {
            state[SHOULD_SHOW_SYSTEM_KEY] = showSystem
        }
    }

    /** Updates whether 7 days usage or 1 day usage should be displayed in the UI. */
    fun updateShow7Days(show7Days: Boolean) {
        if (show7Days != state[SHOULD_SHOW_7_DAYS_KEY]) {
            state[SHOULD_SHOW_7_DAYS_KEY] = show7Days
        }
    }

    /** Builds a [PermissionUsagesUiData] containing all the data necessary to render the UI. */
    private fun buildPermissionUsagesUiData(): PermissionUsagesUiData {
        val curTime = System.currentTimeMillis()
        val showSystem: Boolean = state[SHOULD_SHOW_SYSTEM_KEY] ?: false
        val show7Days: Boolean = state[SHOULD_SHOW_7_DAYS_KEY] ?: false
        val showPermissionUsagesDuration =
            if (KotlinUtils.is7DayToggleEnabled() && show7Days) {
                TIME_7_DAYS_DURATION
            } else {
                TIME_24_HOURS_DURATION
            }
        val startTime = max(curTime - showPermissionUsagesDuration, Instant.EPOCH.toEpochMilli())
        return PermissionUsagesUiData(
            mAllLightPackageOpsLiveData.displayShowSystemToggle(startTime),
            showSystem,
            show7Days,
            mAllLightPackageOpsLiveData.buildPermissionGroupsWithUsageCounts(startTime, showSystem))
    }

    /** Builds a map of permission groups to the number of apps that recently accessed them. */
    private fun AllLightPackageOpsLiveData.buildPermissionGroupsWithUsageCounts(
        startTime: Long,
        showSystem: Boolean,
    ): Map<String, Int> {
        val permissionUsageCountMap: MutableMap<String, Int> = HashMap()
        for (permissionGroup: String in getAllEligiblePermissionGroups()) {
            permissionUsageCountMap[permissionGroup] = 0
        }

        val eligibleLightPackageOpsList: List<LightPackageOps> =
            getAllLightPackageOps()?.filterOutExemptedApps() ?: listOf()

        for (lightPackageOps: LightPackageOps in eligibleLightPackageOpsList) {
            val permGroupsToLastAccess: List<Map.Entry<String, Long>> =
                lightPackageOps.lastPermissionGroupAccessTimesMs.entries
                    .filterOutExemptedPermissionGroupsFromKeys()
                    .filterOutSystemAppPermissionsIfNecessary(
                        showSystem, lightPackageOps.packageName, lightPackageOps.userHandle)
                    .filterAccessTimeLaterThan(startTime)
            val recentlyUsedPermissions: List<String> = permGroupsToLastAccess.map { it.key }

            for (permissionGroup: String in recentlyUsedPermissions) {
                permissionUsageCountMap[permissionGroup] =
                    permissionUsageCountMap.getOrDefault(permissionGroup, 0) + 1
            }
        }
        return permissionUsageCountMap
    }

    /**
     * Determines whether there are any system app permissions with recent usage, in which case the
     * "show/hide system" toggle should be displayed in the UI.
     */
    private fun AllLightPackageOpsLiveData.displayShowSystemToggle(startTime: Long): Boolean {
        val eligibleLightPackageOpsList: List<LightPackageOps> =
            getAllLightPackageOps()?.filterOutExemptedApps() ?: listOf()

        for (lightPackageOps: LightPackageOps in eligibleLightPackageOpsList) {
            val recentlyUsedPermissions: Set<String> =
                lightPackageOps.lastPermissionGroupAccessTimesMs.entries
                    .filterAccessTimeLaterThan(startTime)
                    .map { it.key }
                    .toSet()
            if (recentlyUsedPermissions
                .filterOutExemptedPermissionGroups()
                .containsSystemAppPermission(
                    lightPackageOps.packageName, lightPackageOps.userHandle)) {
                return true
            }
        }

        return false
    }

    /**
     * Returns all permission groups tracked in the [AllLightPackageOpsLiveData] eligible for
     * display in the UI.
     */
    private fun AllLightPackageOpsLiveData.getAllEligiblePermissionGroups(): Set<String> {
        val eligibleLightPackageOpsList =
            getAllLightPackageOps()?.filterOutExemptedApps() ?: listOf()

        val allPermissionGroups: Set<String> =
            eligibleLightPackageOpsList.flatMap { it.lastPermissionGroupAccessTimesMs.keys }.toSet()

        return allPermissionGroups.filterOutExemptedPermissionGroups().toSet()
    }

    private fun isAppPermissionSystem(appPermissionId: AppPermissionId): Boolean {
        val appPermGroupUiInfo = appPermGroupUiInfoLiveDataList[appPermissionId]?.value

        if (appPermGroupUiInfo != null) {
            return appPermGroupUiInfo.isSystem
        } else
        // The AppPermGroupUiInfo may be null if it has either not loaded yet or if the app has not
        // requested any permissions from the permission group in question.
        // The Telecom doesn't request microphone or camera permissions. However, telecom app may
        // use these permissions and they are considered system app permissions, so we return true
        // even if the AppPermGroupUiInfo is unavailable.
        if (appPermissionId.packageName == TELECOM_PACKAGE &&
            (appPermissionId.permissionGroup == Manifest.permission_group.CAMERA ||
                appPermissionId.permissionGroup == Manifest.permission_group.MICROPHONE)) {
            return true
        }
        return false
    }

    private fun AllLightPackageOpsLiveData.getAllLightPackageOps() = value?.values

    /**
     * Filters out accesses earlier than the provided start time from a map of permission last
     * accesses.
     */
    private fun Collection<Map.Entry<String, Long>>.filterAccessTimeLaterThan(startTime: Long) =
        filter {
            it.value > startTime
        }

    /**
     * Filters out system app permissions from a map of permission last accesses, if showSystem is
     * false.
     */
    private fun Collection<Map.Entry<String, Long>>.filterOutSystemAppPermissionsIfNecessary(
        showSystem: Boolean,
        packageName: String,
        userHandle: UserHandle
    ) = filter {
        showSystem || !isAppPermissionSystem(AppPermissionId(packageName, userHandle, it.key))
    }

    /**
     * Filters out permission groups that are exempt from permission usage tracking from a map of
     * permission last accesses.
     */
    private fun Collection<Map.Entry<String, Long>>.filterOutExemptedPermissionGroupsFromKeys() =
        filter {
            !EXEMPTED_PERMISSION_GROUPS.contains(it.key)
        }

    /**
     * Filters out permission groups that are exempt from permission usage tracking from a map of
     * permission last accesses.
     */
    private fun Collection<String>.filterOutExemptedPermissionGroups() = filter {
        !EXEMPTED_PERMISSION_GROUPS.contains(it)
    }

    /** Filters out [LightPackageOps] for apps that are exempt from permission usage tracking. */
    private fun Collection<LightPackageOps>.filterOutExemptedApps() = filter {
        !exemptedPackages.contains(it.packageName)
    }

    /**
     * Returns from a list of permissions whether any permission along with the provided package
     * name and user handle are considered a system app permission.
     */
    private fun Collection<String>.containsSystemAppPermission(
        packageName: String,
        userHandle: UserHandle
    ) = any { isAppPermissionSystem(AppPermissionId(packageName, userHandle, it)) }

    /** Identifier for an app permission group combination. */
    data class AppPermissionId(
        val packageName: String,
        val userHandle: UserHandle,
        val permissionGroup: String
    )

    /** Data class to hold all the information required to configure the UI. */
    data class PermissionUsagesUiData(
        /** Whether to show the "show/hide system" toggle. */
        val displayShowSystemToggle: Boolean,
        /** Whether to show system app permissions in the UI. */
        val showSystemAppPermissions: Boolean,
        /** Whether to show usage data for 7 days or 1 day. */
        val show7DaysUsage: Boolean,
        /** Map instances for display in UI */
        val permissionGroupsWithUsageCount: Map<String, Int>,
    )

    /** LiveData object for [PermissionUsagesUiData]. */
    val permissionUsagesUiLiveData =
        object : SmartUpdateMediatorLiveData<@JvmSuppressWildcards PermissionUsagesUiData>() {

            private var appPermGroupListPopulated: Boolean = false
            private val getAppPermGroupUiInfoLiveData = { appPermissionId: AppPermissionId ->
                AppPermGroupUiInfoLiveData[
                    Triple(
                        appPermissionId.packageName,
                        appPermissionId.permissionGroup,
                        appPermissionId.userHandle,
                    )]
            }

            init {
                addSource(mAllLightPackageOpsLiveData) { update() }
                addSource(showSystemLiveData) { update() }
                addSource(show7DaysLiveData) { update() }
            }

            override fun onUpdate() {
                if (!mAllLightPackageOpsLiveData.isInitialized) {
                    return
                }

                if (appPermGroupUiInfoLiveDataList.isEmpty()) {
                    val appPermissionIds = mutableListOf<AppPermissionId>()
                    val allPackages = mAllLightPackageOpsLiveData.value?.keys ?: setOf()
                    for (packageWithUserHandle: Pair<String, UserHandle> in allPackages) {
                        val lastPermissionGroupAccessTimesMs =
                            mAllLightPackageOpsLiveData.value
                                ?.get(packageWithUserHandle)
                                ?.lastPermissionGroupAccessTimesMs
                                ?: mapOf()

                        for (permissionGroupToAccess in lastPermissionGroupAccessTimesMs) {
                            appPermissionIds.add(
                                AppPermissionId(
                                    packageWithUserHandle.first,
                                    packageWithUserHandle.second,
                                    permissionGroupToAccess.key,
                                ))
                        }
                    }

                    setSourcesToDifference(
                        appPermissionIds,
                        appPermGroupUiInfoLiveDataList,
                        getAppPermGroupUiInfoLiveData) {
                            update()
                        }
                    appPermGroupListPopulated = true

                    return
                }

                if (appPermGroupUiInfoLiveDataList.any { !it.value.isInitialized }) {
                    return
                }

                if (isInitialized && appPermGroupUiInfoLiveDataList.any { it.value.isStale }) {
                    return
                }

                val uiData = buildPermissionUsagesUiData()
                // We include this check as we don't want UX updates unless the data to be displayed
                // has changed. SmartUpdateMediatorLiveData sends updates if the data has changed OR
                // if the data has changed from stale to fresh.
                if (value != uiData) {
                    value = uiData
                }
            }
        }

    /** Companion class for [PermissionUsageViewModel]. */
    companion object {
        private val TIME_7_DAYS_DURATION = TimeUnit.DAYS.toMillis(7)
        private val TIME_24_HOURS_DURATION = TimeUnit.DAYS.toMillis(1)
        internal const val SHOULD_SHOW_SYSTEM_KEY = "showSystem"
        internal const val SHOULD_SHOW_7_DAYS_KEY = "show7Days"
        private const val TELECOM_PACKAGE = "com.android.server.telecom"

        /** Permission groups that should be hidden from the permissions usage UI. */
        private val EXEMPTED_PERMISSION_GROUPS = setOf(Manifest.permission_group.NOTIFICATIONS)
    }

    /** Factory for [PermissionUsageViewModel]. */
    @RequiresApi(Build.VERSION_CODES.S)
    class PermissionUsageViewModelFactory(
        val app: Application,
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle
    ) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
        override fun <T : ViewModel?> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T {
            @Suppress("UNCHECKED_CAST") return PermissionUsageViewModel(handle, app) as T
        }
    }
}
