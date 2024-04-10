/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.viewmodel.v31

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.domain.model.v31.PermissionGroupUsageModel
import com.android.permissioncontroller.permission.domain.usecase.v31.GetPermissionGroupUsageUseCase
import com.android.permissioncontroller.permission.ui.viewmodel.BasePermissionUsageViewModel
import com.android.permissioncontroller.permission.utils.KotlinUtils
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/** Privacy dashboard's new implementation. */
class PermissionUsageViewModelV2(
    val app: Application,
    private val permissionRepository: PermissionRepository,
    private val getPermissionUsageUseCase: GetPermissionGroupUsageUseCase,
    scope: CoroutineScope? = null
) : BasePermissionUsageViewModel(app) {
    private var showSystemApps = false
    private var show7DaysData = false
    private val coroutineScope = scope ?: viewModelScope
    private val permissionGroupOpsFlow: StateFlow<List<PermissionGroupUsageModel>> by lazy {
        getPermissionUsageUseCase()
            .stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    @VisibleForTesting
    fun getPermissionUsagesUiDataFlow(): Flow<PermissionUsagesUiState> {
        return permissionGroupOpsFlow.map { permGroupUsages ->
            buildPermissionUsagesUiState(permGroupUsages)
        }
    }

    override fun getPermissionUsagesUiLiveData(): LiveData<PermissionUsagesUiState> {
        return getPermissionUsagesUiDataFlow()
            .asLiveData(context = coroutineScope.coroutineContext + Dispatchers.Default)
    }

    /** Get start time based on whether to show 24 hours or 7 days data. */
    private fun getStartTime(show7DaysData: Boolean): Long {
        val curTime = System.currentTimeMillis()
        val showPermissionUsagesDuration =
            if (KotlinUtils.is7DayToggleEnabled() && show7DaysData) {
                TIME_7_DAYS_DURATION
            } else {
                TIME_24_HOURS_DURATION
            }
        return max(curTime - showPermissionUsagesDuration, Instant.EPOCH.toEpochMilli())
    }

    /** Builds a [PermissionUsagesUiState] containing all data necessary to render the UI. */
    private fun buildPermissionUsagesUiState(
        permissionGroupOps: List<PermissionGroupUsageModel>
    ): PermissionUsagesUiState {
        val startTime = getStartTime(show7DaysData)
        val dashboardPermissionGroups =
            permissionRepository.getPermissionGroupsForPrivacyDashboard()
        val permissionUsageCountMap = HashMap<String, Int>(dashboardPermissionGroups.size)
        for (permissionGroup in dashboardPermissionGroups) {
            permissionUsageCountMap[permissionGroup] = 0
        }

        val permGroupOps = permissionGroupOps.filter { it.lastAccessTimestampMillis > startTime }
        permGroupOps
            .filter { showSystemApps || it.isUserSensitive }
            .forEach {
                permissionUsageCountMap[it.permissionGroup] =
                    permissionUsageCountMap.getOrDefault(it.permissionGroup, 0) + 1
            }
        return PermissionUsagesUiState(
            permGroupOps.any { !it.isUserSensitive },
            permissionUsageCountMap
        )
    }

    override fun getShowSystemApps(): Boolean {
        return showSystemApps
    }

    override fun getShow7DaysData(): Boolean {
        return show7DaysData
    }

    override fun updateShowSystem(showSystem: Boolean): PermissionUsagesUiState {
        showSystemApps = showSystem
        return buildPermissionUsagesUiState(permissionGroupOpsFlow.value)
    }

    override fun updateShow7Days(show7Days: Boolean): PermissionUsagesUiState {
        show7DaysData = show7Days
        return buildPermissionUsagesUiState(permissionGroupOpsFlow.value)
    }

    private val permissionGroupLabels = mutableMapOf<String, String>()

    override fun getPermissionGroupLabel(context: Context, permissionGroup: String): String {
        return runBlocking(coroutineScope.coroutineContext + Dispatchers.Default) {
            permissionGroupLabels.getOrDefault(
                permissionGroup,
                permissionRepository.getPermissionGroupLabel(context, permissionGroup).toString()
            )
        }
    }

    /** Companion class for [PermissionUsageViewModelV2]. */
    companion object {
        internal val LOG_TAG = PermissionUsageViewModelV2::class.java.simpleName

        private val TIME_7_DAYS_DURATION = TimeUnit.DAYS.toMillis(7)
        private val TIME_24_HOURS_DURATION = TimeUnit.DAYS.toMillis(1)
    }
}

/** Data class to hold all the information required to configure the UI. */
data class PermissionUsagesUiState(
    val shouldShowSystemToggle: Boolean,
    // Map instances for display in UI
    val permissionGroupUsageCount: Map<String, Int>,
)

/** Factory for [BasePermissionUsageViewModel]. */
@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageViewModelFactory(
    private val app: Application,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        val permissionRepository = PermissionRepository.getInstance(app)
        val permissionUsageUseCase = GetPermissionGroupUsageUseCase.create(app)
        return PermissionUsageViewModelV2(app, permissionRepository, permissionUsageUseCase) as T
    }
}
