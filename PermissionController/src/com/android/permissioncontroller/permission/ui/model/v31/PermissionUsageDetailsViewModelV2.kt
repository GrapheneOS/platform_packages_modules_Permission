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

package com.android.permissioncontroller.permission.ui.model.v31

import android.app.AppOpsManager.OPSTR_EMERGENCY_LOCATION
import android.app.Application
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.android.permissioncontroller.appops.data.repository.v31.AppOpRepository
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.domain.model.v31.PermissionTimelineUsageModel
import com.android.permissioncontroller.permission.domain.model.v31.PermissionTimelineUsageModelWrapper
import com.android.permissioncontroller.permission.domain.usecase.v31.GetPermissionGroupUsageDetailsUseCase
import com.android.permissioncontroller.permission.domain.usecase.v31.isLocationByPassEnabled
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.SHOULD_SHOW_7_DAYS_KEY
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.SHOULD_SHOW_SYSTEM_KEY
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.TIME_24_HOURS_DURATION
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.TIME_7_DAYS_DURATION
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.PermissionUsageDetailsUiState
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import com.android.permissioncontroller.role.data.repository.v31.RoleRepository
import com.android.permissioncontroller.user.data.repository.v31.UserRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class PermissionUsageDetailsViewModelV2(
    app: Application,
    private val getPermissionUsageDetailsUseCase: GetPermissionGroupUsageDetailsUseCase,
    private val state: SavedStateHandle,
    private val permissionGroup: String,
    scope: CoroutineScope? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Inject the parameter to prevent READ_DEVICE_CONFIG permission error on T- platforms.
    private val is7DayToggleEnabled: Boolean = KotlinUtils.is7DayToggleEnabled(),
    private val packageRepository: PackageRepository = PackageRepository.getInstance(app)
) : BasePermissionUsageDetailsViewModel(app) {
    private val coroutineScope = scope ?: viewModelScope

    private val showSystemFlow = MutableStateFlow(state[SHOULD_SHOW_SYSTEM_KEY] ?: false)
    private val show7DaysFlow = MutableStateFlow(state[SHOULD_SHOW_7_DAYS_KEY] ?: false)

    private val permissionTimelineUsagesFlow:
        StateFlow<PermissionTimelineUsageModelWrapper> by lazy {
        getPermissionUsageDetailsUseCase(coroutineScope)
            .flowOn(defaultDispatcher)
            .stateIn(
                coroutineScope,
                SharingStarted.WhileSubscribed(5000),
                PermissionTimelineUsageModelWrapper.Loading
            )
    }

    @VisibleForTesting
    val permissionUsageDetailsUiStateFlow: Flow<PermissionUsageDetailsUiState> by lazy {
        combine(permissionTimelineUsagesFlow, showSystemFlow, show7DaysFlow) {
                permissionTimelineUsages,
                showSystem,
                show7Days ->
                permissionTimelineUsages.buildPermissionUsageDetailsUiInfo(showSystem, show7Days)
            }
            .flowOn(defaultDispatcher)
    }

    override fun getPermissionUsagesDetailsInfoUiLiveData():
        LiveData<PermissionUsageDetailsUiState> {
        return permissionUsageDetailsUiStateFlow.asLiveData(
            context = coroutineScope.coroutineContext
        )
    }

    private fun PermissionTimelineUsageModelWrapper.buildPermissionUsageDetailsUiInfo(
        showSystem: Boolean,
        show7Days: Boolean
    ): PermissionUsageDetailsUiState {
        if (this is PermissionTimelineUsageModelWrapper.Loading) {
            return PermissionUsageDetailsUiState.Loading
        }
        val permissionTimelineUsageModels =
            (this as PermissionTimelineUsageModelWrapper.Success).timelineUsageModels
        val startTime =
            (System.currentTimeMillis() - getUsageDuration(show7Days)).coerceAtLeast(
                Instant.EPOCH.toEpochMilli()
            )
        val containsSystemUsages = permissionTimelineUsageModels.any { !it.isUserSensitive }
        val result =
            permissionTimelineUsageModels
                .filter { it.accessEndMillis > startTime }
                .filter { showSystem || it.isUserSensitive }
                .map { clusterOps ->
                    val durationSummaryLabel =
                        if (clusterOps.durationMillis > 0) {
                            getDurationSummary(clusterOps.durationMillis)
                        } else {
                            null
                        }
                    val proxyLabel = getProxyPackageLabel(clusterOps)
                    val subAttributionLabel = clusterOps.attributionLabel
                    val showingSubAttribution = !subAttributionLabel.isNullOrEmpty()
                    val summary =
                        buildUsageSummary(subAttributionLabel, proxyLabel, durationSummaryLabel)
                    val isEmergencyLocationAccess =
                        isLocationByPassEnabled() &&
                            clusterOps.opNames.any { it == OPSTR_EMERGENCY_LOCATION }

                    PermissionUsageDetailsViewModel.AppPermissionAccessUiInfo(
                        UserHandle.of(clusterOps.userId),
                        clusterOps.packageName,
                        getPackageLabel(clusterOps.packageName, UserHandle.of(clusterOps.userId)),
                        permissionGroup,
                        clusterOps.accessStartMillis,
                        clusterOps.accessEndMillis,
                        summary,
                        showingSubAttribution,
                        clusterOps.attributionTags ?: emptySet(),
                        getBadgedPackageIcon(
                            clusterOps.packageName,
                            UserHandle.of(clusterOps.userId)
                        ),
                        isEmergencyLocationAccess
                    )
                }
                .sortedBy { -1 * it.accessStartTime }
        return PermissionUsageDetailsUiState.Success(result, containsSystemUsages)
    }

    override fun getShowSystem(): Boolean = showSystemFlow.value

    override fun getShow7Days(): Boolean = show7DaysFlow.value

    private fun getUsageDuration(show7Days: Boolean): Long {
        return if (is7DayToggleEnabled && show7Days) {
            TIME_7_DAYS_DURATION
        } else {
            TIME_24_HOURS_DURATION
        }
    }

    private fun getProxyPackageLabel(accessCluster: PermissionTimelineUsageModel): String? =
        accessCluster.proxyPackageName?.let { proxyPackageName ->
            if (accessCluster.proxyUserId != null) {
                getPackageLabel(proxyPackageName, UserHandle.of(accessCluster.proxyUserId))
            } else null
        }

    override fun updateShowSystemAppsToggle(showSystem: Boolean) {
        if (showSystem != state[SHOULD_SHOW_SYSTEM_KEY]) {
            state[SHOULD_SHOW_SYSTEM_KEY] = showSystem
        }
        showSystemFlow.compareAndSet(!showSystem, showSystem)
    }

    override fun updateShow7DaysToggle(show7Days: Boolean) {
        if (show7Days != state[SHOULD_SHOW_7_DAYS_KEY]) {
            state[SHOULD_SHOW_7_DAYS_KEY] = show7Days
        }
        show7DaysFlow.compareAndSet(!show7Days, show7Days)
    }

    // TODO review these methods when old impl is removed, suspend function??
    override fun getPackageLabel(app: Application, packageName: String, user: UserHandle): String {
        return packageRepository.getPackageLabel(packageName, user)
    }

    override fun getBadgedPackageIcon(
        app: Application,
        packageName: String,
        user: UserHandle
    ): Drawable? {
        return packageRepository.getBadgedPackageIcon(packageName, user)
    }

    companion object {
        fun create(
            app: Application,
            handle: SavedStateHandle,
            permissionGroup: String
        ): PermissionUsageDetailsViewModelV2 {
            val permissionRepository = PermissionRepository.getInstance(app)
            val packageRepository = PackageRepository.getInstance(app)
            val appOpRepository = AppOpRepository.getInstance(app, permissionRepository)
            val roleRepository = RoleRepository.getInstance(app)
            val userRepository = UserRepository.getInstance(app)
            val useCase =
                GetPermissionGroupUsageDetailsUseCase(
                    permissionGroup,
                    packageRepository,
                    permissionRepository,
                    appOpRepository,
                    roleRepository,
                    userRepository
                )
            return PermissionUsageDetailsViewModelV2(app, useCase, handle, permissionGroup)
        }
    }
}
