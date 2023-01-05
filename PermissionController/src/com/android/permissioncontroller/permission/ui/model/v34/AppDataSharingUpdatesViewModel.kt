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

package com.android.permissioncontroller.permission.ui.model.v34

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.Intent.ACTION_MANAGE_APP_PERMISSIONS
import android.content.Intent.ACTION_MANAGE_PERMISSIONS
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.content.Intent.EXTRA_USER
import android.os.Build
import android.os.Process
import android.os.UserHandle
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.data.v34.AppDataSharingUpdatesLiveData
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate.Companion.LOCATION_CATEGORY
import com.android.permissioncontroller.permission.model.v34.DataSharingUpdateType
import kotlinx.coroutines.Job

/** View model for data sharing updates UI. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatesViewModel(app: Application) {

    private val appDataSharingUpdatesLiveData = AppDataSharingUpdatesLiveData(app)

    /** Opens the Safety Label Help Center web page. */
    fun openSafetyLabelsHelpCenterPage(activity: Activity) {
        // TODO(b/263838996): Link to Safety Label Help Center.
        activity.startActivity(Intent(ACTION_MANAGE_PERMISSIONS))
    }

    /** Start the App Permissions fragment for the provided packageName and userHandle. */
    fun startAppPermissionsPage(activity: Activity, packageName: String, userHandle: UserHandle) {
        activity.startActivity(
            Intent(ACTION_MANAGE_APP_PERMISSIONS).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_USER, userHandle)
            })
    }

    /** All the information necessary to display an app's data sharing update in the UI. */
    data class AppLocationDataSharingUpdateUiInfo(
        val packageName: String,
        val userHandle: UserHandle,
        val dataSharingUpdateType: DataSharingUpdateType
    )

    /** LiveData for all data sharing updates to be displayed in the UI. */
    val appLocationDataSharingUpdateUiInfoLiveData =
        object : SmartAsyncMediatorLiveData<List<AppLocationDataSharingUpdateUiInfo>>() {

            init {
                addSource(appDataSharingUpdatesLiveData) { onUpdate() }
            }

            override suspend fun loadDataAndPostValue(job: Job) {
                if (appDataSharingUpdatesLiveData.isStale) {
                    return
                }

                postValue(
                    appDataSharingUpdatesLiveData.value?.mapNotNull {
                        // TODO(b/263838803): Filter to apps that are installed, have location
                        //  permission granted, and have an install source.
                        // TODO(b/263838456): Add an entry for all profiles in this.
                        it.categorySharingUpdates[LOCATION_CATEGORY]?.let { locationUpdate ->
                            AppLocationDataSharingUpdateUiInfo(
                                it.packageName, Process.myUserHandle(), locationUpdate)
                        }
                    })
            }
        }
}
