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

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.companion.virtual.VirtualDeviceManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.permission.PermissionManager
import android.permission.PermissionManager.PermissionState
import androidx.annotation.RequiresApi
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.utils.PermissionMapping
import kotlinx.coroutines.Job

/**
 * LiveData that loads all the external device permissions per package. The permissions will be
 * loaded only if the package has requested the permission. This live data produces the list of
 * {@link ExternalDeviceGrantInfo} that has group name to which permission belongs to, grant state
 * and persistentDeviceId
 *
 * @param app The current Application
 * @param packageName The name of the package
 * @param user The user for whom the packageInfo will be defined
 */
class PackagePermissionsExternalDeviceLiveData
private constructor(private val app: Application, val packageName: String, val user: UserHandle) :
    SmartAsyncMediatorLiveData<
        List<PackagePermissionsExternalDeviceLiveData.ExternalDeviceGrantInfo>
    >() {
    private val permissionManager = app.getSystemService(PermissionManager::class.java)!!

    data class ExternalDeviceGrantInfo(
        val groupName: String,
        val permGrantState: AppPermGroupUiInfo.PermGrantState,
        val persistentDeviceId: String
    )

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override suspend fun loadDataAndPostValue(job: Job) {
        if (!SdkLevel.isAtLeastV()) {
            return
        }
        val virtualDeviceManager = app.getSystemService(VirtualDeviceManager::class.java)!!
        val externalDeviceGrantInfoList =
            virtualDeviceManager.allPersistentDeviceIds
                .map { getVirtualDeviceGrantInfoList(it) }
                .toList()
                .flatten()
        postValue(externalDeviceGrantInfoList)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun getVirtualDeviceGrantInfoList(
        persistentDeviceId: String
    ): List<ExternalDeviceGrantInfo> {
        val permissionState =
            permissionManager.getAllPermissionStates(packageName, persistentDeviceId)
        return permissionState.mapNotNull { (permissionName, permissionState) ->
            PermissionMapping.getGroupOfPlatformPermission(permissionName)?.let { groupName ->
                val grantState = getGrantState(permissionState)
                ExternalDeviceGrantInfo(groupName, grantState, persistentDeviceId)
            }
        }
    }

    /**
     * This method returns the GrantState for currently supported virtual device permissions
     * (Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).
     *
     * TODO: b/328841671 (Unite this with PermGroupUiInfoLiveData#getGrantedIncludingBackground)
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun getGrantState(permissionState: PermissionState): AppPermGroupUiInfo.PermGrantState =
        if (permissionState.isGranted) {
            AppPermGroupUiInfo.PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY
        } else if (permissionState.flags and PackageManager.FLAG_PERMISSION_ONE_TIME != 0) {
            AppPermGroupUiInfo.PermGrantState.PERMS_ASK
        } else {
            AppPermGroupUiInfo.PermGrantState.PERMS_DENIED
        }

    companion object :
        DataRepositoryForPackage<
            Pair<String, UserHandle>, PackagePermissionsExternalDeviceLiveData
        >() {
        override fun newValue(
            key: Pair<String, UserHandle>
        ): PackagePermissionsExternalDeviceLiveData {
            return PackagePermissionsExternalDeviceLiveData(
                PermissionControllerApplication.get(),
                key.first,
                key.second
            )
        }
    }
}
