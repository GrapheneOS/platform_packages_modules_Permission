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

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.Utils

class WearLocationProviderInterceptDialogViewModel : ViewModel() {
    private val showDialogLiveData = MutableLiveData<Boolean>()
    val dialogVisibilityLiveData: LiveData<Boolean> = showDialogLiveData
    var locationProviderInterceptDialogArgs: LocationProviderInterceptDialogArgs? = null

    init {
        showDialogLiveData.value = false
    }

    private fun applicationInfo(context: Context, packageName: String): ApplicationInfo? {
        val packageInfo: PackageInfo? =
            try {
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        return packageInfo?.applicationInfo
    }

    fun showDialog(context: Context, packageName: String) {
        val applicationInfo = applicationInfo(context, packageName) ?: return
        val appLabel = Utils.getAppLabel(applicationInfo, context)
        locationProviderInterceptDialogArgs =
            LocationProviderInterceptDialogArgs(
                iconId = R.drawable.ic_dialog_alert_material,
                titleId = android.R.string.dialog_alert_title,
                message = context.getString(R.string.location_warning, appLabel),
                okButtonTitleId = R.string.ok,
                locationSettingsId = R.string.location_settings,
                onOkButtonClick = { dismissDialog() },
                onLocationSettingsClick = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            )
        showDialogLiveData.value = true
    }

    fun dismissDialog() {
        locationProviderInterceptDialogArgs = null
        showDialogLiveData.value = false
    }
}

/** Factory for an AppPermissionGroupsRevokeDialogViewModel */
class WearLocationProviderInterceptDialogViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return WearLocationProviderInterceptDialogViewModel() as T
    }
}

data class LocationProviderInterceptDialogArgs(
    val iconId: Int,
    val titleId: Int,
    val message: String,
    val okButtonTitleId: Int,
    val locationSettingsId: Int,
    val onOkButtonClick: () -> Unit,
    val onLocationSettingsClick: () -> Unit
)
