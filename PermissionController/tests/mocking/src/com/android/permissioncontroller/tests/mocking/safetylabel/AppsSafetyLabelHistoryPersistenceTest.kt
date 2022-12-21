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

package com.android.permissioncontroller.tests.mocking.safetylabel

import android.content.Context
import android.os.Build
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataCategory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataLabel
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.PackageInfo
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistoryPersistence
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/** Tests for [AppsSafetyLabelHistoryPersistence]. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class AppsSafetyLabelHistoryPersistenceTest {
    private lateinit var context: Context
    private lateinit var dataFile: File
    private lateinit var mockitoSession: MockitoSession

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()
        dataFile = context.getFileStreamPath(TEST_FILE_NAME)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
    }

    @After
    fun cleanup() {
        context.deleteFile(TEST_FILE_NAME)
        mockitoSession.finishMocking()
    }

    @Test
    fun read_afterDeleted_returnsNull() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(),
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)
        AppsSafetyLabelHistoryPersistence.clear(dataFile)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile)).isEqualTo(null)
    }

    @Test
    fun read_afterWrite_noHistory_returnsIdenticalAppsSafetyLabelHistory() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(),
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile))
            .isEqualTo(appsSafetyLabelHistory)
    }

    @Test
    fun read_afterWrite_noSharing_returnsIdenticalAppsSafetyLabelHistory() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(PackageInfo("package_name_1"), listOf()),
                ),
            )
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile))
            .isEqualTo(appsSafetyLabelHistory)
    }

    @Test
    fun read_afterWrite_returnsIdenticalAppsSafetyLabelHistory() {
        val appsSafetyLabelHistory =
            AppsSafetyLabelHistory(
                listOf(
                    AppSafetyLabelHistory(
                        PackageInfo(PACKAGE_NAME_1),
                        listOf(
                            SafetyLabel(
                                PackageInfo(PACKAGE_NAME_1),
                                java.time.Instant.ofEpochMilli(500),
                                DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(true)))))),
                    AppSafetyLabelHistory(
                        PackageInfo(PACKAGE_NAME_2),
                        listOf(
                            SafetyLabel(
                                PackageInfo(PACKAGE_NAME_2),
                                java.time.Instant.ofEpochMilli(6000),
                                DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(true)))),
                            SafetyLabel(
                                PackageInfo(PACKAGE_NAME_2),
                                java.time.Instant.ofEpochMilli(800000),
                                DataLabel(
                                    mapOf(
                                        LOCATION_CATEGORY to DataCategory(true),
                                        FINANCIAL_CATEGORY to DataCategory(false))))))))
        AppsSafetyLabelHistoryPersistence.write(dataFile, appsSafetyLabelHistory)

        assertThat(AppsSafetyLabelHistoryPersistence.read(dataFile))
            .isEqualTo(appsSafetyLabelHistory)
    }

    companion object {
        private const val TEST_FILE_NAME = "test_safety_label_history_file"
        private const val PACKAGE_NAME_1 = "package_name_1"
        private const val PACKAGE_NAME_2 = "package_name_2"
        private const val LOCATION_CATEGORY = "location"
        private const val FINANCIAL_CATEGORY = "financial"
    }
}
