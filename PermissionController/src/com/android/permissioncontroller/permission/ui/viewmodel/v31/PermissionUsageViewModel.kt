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
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.domain.model.v31.PermissionGroupUsageModel
import com.android.permissioncontroller.permission.domain.usecase.v31.GetPermissionGroupUsageUseCase
import com.android.permissioncontroller.permission.utils.KotlinUtils
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/** Privacy dashboard's new implementation. */
class PermissionUsageViewModel(
    val app: Application,
    private val permissionRepository: PermissionRepository,
    private val getPermissionUsageUseCase: GetPermissionGroupUsageUseCase,
    scope: CoroutineScope? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Inject the parameter to prevent READ_DEVICE_CONFIG permission error on T- platforms.
    private val is7DayToggleEnabled: Boolean = KotlinUtils.is7DayToggleEnabled(),
) : AndroidViewModel(app) {
    private var showSystemApps = false
    private var show7DaysData = false
    private val coroutineScope = scope ?: viewModelScope

    // Cache permission usages to calculate ui state for "show system" and "show 7 days" toggle.
    @Volatile private var permissionGroupUsages = emptyList<PermissionGroupUsageModel>()

    private val permissionUsagesUiStateFlow: StateFlow<PermissionUsagesUiState> by lazy {
        getPermissionUsageUseCase()
            .map { permGroupUsages ->
                permissionGroupUsages = permGroupUsages
                buildPermissionUsagesUiState(permGroupUsages)
            }
            .flowOn(defaultDispatcher)
            .stateIn(
                coroutineScope,
                SharingStarted.WhileSubscribed(5000),
                PermissionUsagesUiState.Loading
            )
    }

    val permissionUsagesUiLiveData =
        permissionUsagesUiStateFlow.asLiveData(context = coroutineScope.coroutineContext)

    @VisibleForTesting
    fun getPermissionUsagesUiDataFlow(): Flow<PermissionUsagesUiState> {
        return permissionUsagesUiStateFlow
    }

    /** Get start time based on whether to show 24 hours or 7 days data. */
    private fun getStartTime(show7DaysData: Boolean): Long {
        val curTime = System.currentTimeMillis()
        val showPermissionUsagesDuration =
            if (is7DayToggleEnabled && show7DaysData) {
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
        return PermissionUsagesUiState.Success(
            permGroupOps.any { !it.isUserSensitive },
            permissionUsageCountMap
        )
    }

    fun getShowSystemApps(): Boolean {
        return showSystemApps
    }

    fun getShow7DaysData(): Boolean {
        return show7DaysData
    }

    fun updateShowSystem(showSystem: Boolean): PermissionUsagesUiState {
        showSystemApps = showSystem
        return buildPermissionUsagesUiState(permissionGroupUsages)
    }

    fun updateShow7Days(show7Days: Boolean): PermissionUsagesUiState {
        show7DaysData = show7Days
        return buildPermissionUsagesUiState(permissionGroupUsages)
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
        val shouldShowSystemToggle: Boolean,
        val permissionGroupUsageCount: Map<String, Int>
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
        return PermissionUsageViewModel(app, permissionRepository, permissionUsageUseCase) as T
    }
}
