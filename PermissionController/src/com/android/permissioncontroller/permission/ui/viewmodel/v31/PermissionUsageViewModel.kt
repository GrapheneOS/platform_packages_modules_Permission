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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.domain.model.v31.PermissionGroupUsageModel
import com.android.permissioncontroller.permission.domain.model.v31.PermissionGroupUsageModelWrapper
import com.android.permissioncontroller.permission.domain.usecase.v31.GetPermissionGroupUsageUseCase
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.SHOULD_SHOW_7_DAYS_KEY
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.SHOULD_SHOW_SYSTEM_KEY
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max
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
import kotlinx.coroutines.runBlocking

/** Privacy dashboard's new implementation. */
class PermissionUsageViewModel(
    val app: Application,
    private val permissionRepository: PermissionRepository,
    private val getPermissionUsageUseCase: GetPermissionGroupUsageUseCase,
    scope: CoroutineScope? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val savedState: SavedStateHandle = SavedStateHandle(emptyMap()),
) : AndroidViewModel(app) {
    private val showSystemFlow = MutableStateFlow(savedState[SHOULD_SHOW_SYSTEM_KEY] ?: false)
    private val show7DaysFlow = MutableStateFlow(savedState[SHOULD_SHOW_7_DAYS_KEY] ?: false)
    private val coroutineScope = scope ?: viewModelScope

    private val permissionUsagesUiStateFlow: StateFlow<PermissionGroupUsageModelWrapper> by lazy {
        getPermissionUsageUseCase()
            .flowOn(defaultDispatcher)
            .stateIn(
                coroutineScope,
                SharingStarted.WhileSubscribed(5000),
                PermissionGroupUsageModelWrapper.Loading
            )
    }

    @VisibleForTesting
    val permissionUsagesUiDataFlow: Flow<PermissionUsagesUiState> by lazy {
        combine(permissionUsagesUiStateFlow, showSystemFlow, show7DaysFlow) {
                permGroupUsages,
                showSystemApps,
                show7Days ->
                buildPermissionUsagesUiState(permGroupUsages, showSystemApps, show7Days)
            }
            .flowOn(defaultDispatcher)
    }

    val permissionUsagesUiLiveData =
        permissionUsagesUiDataFlow.asLiveData(context = coroutineScope.coroutineContext)

    /** Get start time based on whether to show 24 hours or 7 days data. */
    private fun getStartTime(show7DaysData: Boolean): Long {
        val curTime = System.currentTimeMillis()
        val showPermissionUsagesDuration =
            if (show7DaysData && DeviceUtils.isHandheld()) {
                TIME_7_DAYS_DURATION
            } else {
                TIME_24_HOURS_DURATION
            }
        return max(curTime - showPermissionUsagesDuration, Instant.EPOCH.toEpochMilli())
    }

    /** Builds a [PermissionUsagesUiState] containing all data necessary to render the UI. */
    private fun buildPermissionUsagesUiState(
        usages: PermissionGroupUsageModelWrapper,
        showSystemApps: Boolean,
        show7DaysData: Boolean,
    ): PermissionUsagesUiState {
        if (usages is PermissionGroupUsageModelWrapper.Loading) {
            return PermissionUsagesUiState.Loading
        }

        val permissionGroupOps: List<PermissionGroupUsageModel> =
            (usages as PermissionGroupUsageModelWrapper.Success).permissionUsageModels

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
        return PermissionUsagesUiState.Success(
            permGroupOps.any { !it.isUserSensitive },
            permissionUsageCountMap,
            showSystemApps,
            show7DaysData
        )
    }

    fun getShowSystemApps(): Boolean = showSystemFlow.value

    fun getShow7DaysData(): Boolean = show7DaysFlow.value

    val showSystemAppsLiveData =
        showSystemFlow.asLiveData(context = coroutineScope.coroutineContext)

    fun updateShowSystem(showSystem: Boolean) {
        if (showSystem != savedState[SHOULD_SHOW_SYSTEM_KEY]) {
            savedState[SHOULD_SHOW_SYSTEM_KEY] = showSystem
        }
        showSystemFlow.compareAndSet(!showSystem, showSystem)
    }

    fun updateShow7Days(show7Days: Boolean) {
        if (show7Days != savedState[SHOULD_SHOW_7_DAYS_KEY]) {
            savedState[SHOULD_SHOW_7_DAYS_KEY] = show7Days
        }
        show7DaysFlow.compareAndSet(!show7Days, show7Days)
    }

    private val permissionGroupLabels = mutableMapOf<String, String>()

    fun getPermissionGroupLabel(context: Context, permissionGroup: String): String {
        return runBlocking(coroutineScope.coroutineContext + Dispatchers.Default) {
            permissionGroupLabels.getOrDefault(
                permissionGroup,
                permissionRepository.getPermissionGroupLabel(context, permissionGroup).toString()
            )
        }
    }

    /** Companion class for [PermissionUsageViewModel]. */
    companion object {
        private val TIME_7_DAYS_DURATION = TimeUnit.DAYS.toMillis(7)
        private val TIME_24_HOURS_DURATION = TimeUnit.DAYS.toMillis(1)
    }
}

/** Data class to hold all the information required to configure the UI. */
sealed class PermissionUsagesUiState {
    data object Loading : PermissionUsagesUiState()

    data class Success(
        val containsSystemAppUsage: Boolean,
        val permissionGroupUsageCount: Map<String, Int>,
        val showSystem: Boolean,
        val show7Days: Boolean,
    ) : PermissionUsagesUiState()
}

/** Factory for [PermissionUsageViewModel]. */
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
        return PermissionUsageViewModel(
            app,
            permissionRepository,
            permissionUsageUseCase,
            savedState = handle
        )
            as T
    }
}
