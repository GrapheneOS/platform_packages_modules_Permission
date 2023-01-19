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
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate.Companion.LOCATION_CATEGORY
import com.android.permissioncontroller.permission.model.v34.AppDataSharingUpdate.Companion.buildUpdateIfSignificantChange
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataCategory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataLabel
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistoryPersistence
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.Job
import java.time.Duration

/** LiveData for [AppDataSharingUpdate]s. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatesLiveData(val app: Application) :
    SmartAsyncMediatorLiveData<List<AppDataSharingUpdate>>(),
    AppsSafetyLabelHistoryPersistence.ChangeListener {

    override suspend fun loadDataAndPostValue(job: Job) {
        // TODO(b/261660881): This code serves to enable testing business logic in CTS. Remove when
        //  we install apps with metadata in CTS tests.
        writeTestSafetyLabelsToPersistence()

        val updatePeriod =
                DeviceConfig.getLong(
                        DeviceConfig.NAMESPACE_PRIVACY,
                        PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS,
                        Duration.ofDays(DEFAULT_DATA_SHARING_UPDATE_PERIOD_DAYS).toMillis())
        val file =
                AppsSafetyLabelHistoryPersistence.getSafetyLabelHistoryFile(app.applicationContext)

        val appSafetyLabelDiffsFromPersistence =
            AppsSafetyLabelHistoryPersistence.getAppSafetyLabelDiffs(
                Instant.now().minusMillis(updatePeriod), file)
        val updatesFromPersistence =
            appSafetyLabelDiffsFromPersistence.mapNotNull { it.buildUpdateIfSignificantChange() }

        postValue(updatesFromPersistence)
    }

    override fun onActive() {
        super.onActive()
        AppsSafetyLabelHistoryPersistence.addListener(this)
    }

    override fun onInactive() {
        super.onInactive()
        AppsSafetyLabelHistoryPersistence.removeListener(this)
    }

    override fun onSafetyLabelHistoryChanged() {
        update()
    }

    /** Writes safety labels for a test app to safety label history. */
    private fun writeTestSafetyLabelsToPersistence() {
        val historyFile =
            AppsSafetyLabelHistoryPersistence.getSafetyLabelHistoryFile(app.applicationContext)
        AppsSafetyLabelHistoryPersistence.clear(historyFile)
        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(
            SAFETY_LABEL_TEST_PACKAGE_V1, historyFile)
        AppsSafetyLabelHistoryPersistence.recordSafetyLabel(
            SAFETY_LABEL_TEST_PACKAGE_V2, historyFile)
    }

    /** Companion object for [AppDataSharingUpdatesLiveData]. */
    companion object {
        private const val TEST_PACKAGE_NAME = "android.permission3.cts.usepermission"
        private const val PROPERTY_DATA_SHARING_UPDATE_PERIOD_MILLIS =
                "data_sharing_update_period_millis"
        private const val DEFAULT_DATA_SHARING_UPDATE_PERIOD_DAYS: Long = 30

        private val SAFETY_LABEL_TEST_PACKAGE_V1 =
            SafetyLabel(
                AppsSafetyLabelHistory.AppInfo(TEST_PACKAGE_NAME),
                ZonedDateTime.now().minusDays(5).toInstant(),
                DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(false))))

        private val SAFETY_LABEL_TEST_PACKAGE_V2 =
            SafetyLabel(
                AppsSafetyLabelHistory.AppInfo(TEST_PACKAGE_NAME),
                ZonedDateTime.now().minusDays(2).toInstant(),
                DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(true))))
    }
}
