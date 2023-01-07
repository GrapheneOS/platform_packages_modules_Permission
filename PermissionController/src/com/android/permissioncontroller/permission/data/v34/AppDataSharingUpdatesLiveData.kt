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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job

/** LiveData for [AppDataSharingUpdate]s. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatesLiveData(val app: Application) :
    SmartAsyncMediatorLiveData<List<AppDataSharingUpdate>>() {

    override suspend fun loadDataAndPostValue(job: Job) {
        // TODO(b/261660881): This code serves to enable testing business logic in CTS. Remove when
        //  package add broadcasts can invoke writing of safety labels to persistence.
        writeTestSafetyLabelsToPersistence()

        val appSafetyLabelDiffsFromPersistence =
            AppsSafetyLabelHistoryPersistence.getAppSafetyLabelDiffs(
                Instant.now()
                    .minusSeconds(TimeUnit.DAYS.toSeconds(DATA_SHARING_UPDATE_PERIOD_DAYS)),
                AppsSafetyLabelHistoryPersistence.getSafetyLabelHistoryFile(app.applicationContext))
        val updatesFromPersistence =
            appSafetyLabelDiffsFromPersistence.mapNotNull { it.buildUpdateIfSignificantChange() }

        postValue(updatesFromPersistence)
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
        private const val PLACEHOLDER_PACKAGE_NAME_1 = "com.android.systemui"
        private const val PLACEHOLDER_PACKAGE_NAME_2 = "com.android.bluetooth"
        private const val TEST_PACKAGE_NAME = "android.permission3.cts.usepermission"
        private const val PLACEHOLDER_SAFETY_LABEL_UPDATES_FLAG =
            "placeholder_safety_label_updates_flag"
        private const val DATA_SHARING_UPDATE_PERIOD_DAYS: Long = 30

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
