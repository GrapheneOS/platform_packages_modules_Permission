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

package com.android.permissioncontroller.permission.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

/**
 * Base class for [PermissionUsageViewModel] and [PermissionUsageViewModelV2], V2 is the new
 * implementation following app architecture and resolving live data framework shortcomings. Remove
 * [PermissionUsageViewModel] and this base class once V2 is stable.
 */
abstract class BasePermissionUsageViewModel(app: Application) : AndroidViewModel(app) {
    abstract fun getPermissionUsagesUiLiveData(): LiveData<PermissionUsagesUiState>
    abstract fun getShowSystemApps(): Boolean
    abstract fun getShow7DaysData(): Boolean
    abstract fun updateShowSystem(showSystem: Boolean): PermissionUsagesUiState
    abstract fun updateShow7Days(show7Days: Boolean): PermissionUsagesUiState
    abstract fun getPermissionGroupLabel(context: Context, permissionGroup: String): String
}
