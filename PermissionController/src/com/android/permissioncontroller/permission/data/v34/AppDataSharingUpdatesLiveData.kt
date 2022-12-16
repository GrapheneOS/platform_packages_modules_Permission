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

package com.android.permissioncontroller.permission.data.v34

import android.app.Application
import android.os.Build
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate.Companion.LOCATION_CATEGORY
import com.android.permissioncontroller.permission.model.v34.DataSharingUpdateType
import kotlinx.coroutines.Job

/** LiveData for [AppDataSharingUpdate]s. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatesLiveData(val app: Application) :
    SmartAsyncMediatorLiveData<List<AppDataSharingUpdate>>() {

    override suspend fun loadDataAndPostValue(job: Job) {
        // TODO(b/261665490): Read updates from AppSafetyLabelHistoryPersistence instead.
        if (!DeviceConfig.getBoolean(
            NAMESPACE_PRIVACY, PLACEHOLDER_SAFETY_LABEL_UPDATES_FLAG, false)) {
            postValue(listOf())
            return
        }

        val placeholderUpdates =
            listOf(
                AppDataSharingUpdate(
                    PLACEHOLDER_PACKAGE_NAME_1,
                    mapOf(
                        LOCATION_CATEGORY to
                            DataSharingUpdateType.ADDS_SHARING_WITHOUT_ADVERTISING_PURPOSE)),
                AppDataSharingUpdate(
                    PLACEHOLDER_PACKAGE_NAME_2,
                    mapOf(
                        LOCATION_CATEGORY to
                            DataSharingUpdateType.ADDS_SHARING_WITH_ADVERTISING_PURPOSE)))

        postValue(placeholderUpdates)
    }

    /** Companion object for [AppDataSharingUpdatesLiveData]. */
    companion object {
        private const val PLACEHOLDER_PACKAGE_NAME_1 = "com.android.systemui"
        private const val PLACEHOLDER_PACKAGE_NAME_2 = "com.android.bluetooth"
        private const val PLACEHOLDER_SAFETY_LABEL_UPDATES_FLAG =
            "placeholder_safety_label_updates_flag"
    }
}
