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

package com.android.permissioncontroller.permission.ui.wear.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.permission.ui.viewmodel.v31.PermissionUsageViewModel
import com.android.permissioncontroller.permission.ui.viewmodel.v31.PermissionUsagesUiState

class WearPermissionUsageViewModel(
    permissionUsagesUiState: PermissionUsagesUiState?,
    showSystemApps: Boolean,
    show7DaysData: Boolean
) : ViewModel() {
    val permissionUsagesUiStateLiveData = MutableLiveData(permissionUsagesUiState)
    /** A livedata which stores [BasePermissionUsageViewModel.getShowSystemApps()]. */
    val showSystemAppsLiveData = MutableLiveData(showSystemApps)

    /** A livedata which stores [BasePermissionUsageViewModel.getShow7DaysData()]. */
    val show7DaysLiveData = MutableLiveData(show7DaysData)

    fun updatePermissionUsagesUiStateLiveData(newUiData: PermissionUsagesUiState?) {
        permissionUsagesUiStateLiveData.value = newUiData
    }
}

/** Factory for a WearPermissionsUsageViewModel */
class WearPermissionUsageViewModelFactory(val viewModel: PermissionUsageViewModel) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return WearPermissionUsageViewModel(
            viewModel.getPermissionUsagesUiLiveData().value,
            viewModel.getShowSystemApps(),
            viewModel.getShow7DaysData()
        )
            as T
    }
}
