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

package com.android.permissioncontroller.tests.mocking.safetylabel

import android.os.Build
import android.os.PersistableBundle
import androidx.test.filters.SdkSuppress
import com.android.permission.safetylabel.SafetyLabel.KEY_VERSION
import com.android.permission.safetylabel.SafetyLabel as AppMetadataSafetyLabel
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppInfo
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataCategory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataLabel
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel
import com.google.common.truth.Truth.assertThat
import java.time.ZonedDateTime
import org.junit.Test

/** Tests for [AppsSafetyLabelHistory]. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class AppsSafetyLabelHistoryTest {
    @Test
    fun fromAppMetadataSafetyLabel_whenNoSharing_returnsSafetyLabelForPersistence() {
        val metadataBundle = createMetadataWithDataShared(createDataSharedNoSharing())
        val appMetadataSafetyLabel =
            AppMetadataSafetyLabel.getSafetyLabelFromMetadata(metadataBundle)!!

        val safetyLabelForPersistence =
            SafetyLabel.fromAppMetadataSafetyLabel(
                PACKAGE_NAME_1, DATE_2022_09_01, appMetadataSafetyLabel)

        assertThat(safetyLabelForPersistence)
            .isEqualTo(SafetyLabel(AppInfo(PACKAGE_NAME_1), DATE_2022_09_01, DataLabel(mapOf())))
    }

    @Test
    fun fromAppMetadataSafetyLabel_whenLocationSharingNoAds_returnsSafetyLabelForPersistence() {
        val metadataBundle = createMetadataWithDataShared(createDataSharedWithLocationNoAds())
        val appMetadataSafetyLabel =
            AppMetadataSafetyLabel.getSafetyLabelFromMetadata(metadataBundle)!!

        val safetyLabelForPersistence =
            SafetyLabel.fromAppMetadataSafetyLabel(
                PACKAGE_NAME_1, DATE_2022_10_10, appMetadataSafetyLabel)

        assertThat(safetyLabelForPersistence)
            .isEqualTo(
                SafetyLabel(
                    AppInfo(PACKAGE_NAME_1),
                    DATE_2022_10_10,
                    DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(false)))))
    }

    @Test
    fun fromAppMetadataSafetyLabel_whenLocationSharingWithAds_returnsSafetyLabelForPersistence() {
        val metadataBundle = createMetadataWithDataShared(createDataSharedWithLocationWithAds())
        val appMetadataSafetyLabel =
            AppMetadataSafetyLabel.getSafetyLabelFromMetadata(metadataBundle)!!

        val safetyLabelForPersistence =
            SafetyLabel.fromAppMetadataSafetyLabel(
                PACKAGE_NAME_2, DATE_2022_10_10, appMetadataSafetyLabel)

        assertThat(safetyLabelForPersistence)
            .isEqualTo(
                SafetyLabel(
                    AppInfo(PACKAGE_NAME_2),
                    DATE_2022_10_10,
                    DataLabel(mapOf(LOCATION_CATEGORY to DataCategory(true)))))
    }

    /** Companion object for [AppsSafetyLabelHistoryTest]. */
    companion object {
        private const val PACKAGE_NAME_1 = "package_name_1"
        private const val PACKAGE_NAME_2 = "package_name_2"
        private const val LOCATION_CATEGORY = "location"
        private const val APPROX_LOCATION = "approx_location"
        private const val PURPOSE_FRAUD_PREVENTION_SECURITY = 4
        private const val PURPOSE_ADVERTISING = 5
        private const val SAFETY_LABEL_KEY = "safety_labels"
        private const val DATA_SHARED_KEY = "data_shared"
        private const val DATA_LABEL_KEY = "data_labels"
        private const val PURPOSES_KEY = "purposes"
        private const val TOP_LEVEL_VERSION = 1L
        private const val SAFETY_LABELS_VERSION = 1L
        private val DATE_2022_09_01 = ZonedDateTime.parse("2022-09-01T00:00:00.000Z").toInstant()
        private val DATE_2022_10_10 = ZonedDateTime.parse("2022-10-10T00:00:00.000Z").toInstant()

        fun createDataSharedNoSharing(): PersistableBundle {
            return PersistableBundle()
        }

        fun createDataSharedWithLocationNoAds(): PersistableBundle {
            val locationBundle =
                PersistableBundle().apply {
                    putPersistableBundle(
                        APPROX_LOCATION,
                        PersistableBundle().apply {
                            putIntArray(
                                PURPOSES_KEY,
                                listOf(PURPOSE_FRAUD_PREVENTION_SECURITY).toIntArray())
                        })
                }

            return PersistableBundle().apply {
                putPersistableBundle(LOCATION_CATEGORY, locationBundle)
            }
        }

        fun createDataSharedWithLocationWithAds(): PersistableBundle {
            val locationBundle =
                PersistableBundle().apply {
                    putPersistableBundle(
                        APPROX_LOCATION,
                        PersistableBundle().apply {
                            putIntArray(PURPOSES_KEY, listOf(PURPOSE_ADVERTISING).toIntArray())
                        })
                }

            return PersistableBundle().apply {
                putPersistableBundle(LOCATION_CATEGORY, locationBundle)
            }
        }

        fun createMetadataWithDataShared(dataSharedBundle: PersistableBundle): PersistableBundle {
            val dataLabelBundle =
                PersistableBundle().apply {
                    putPersistableBundle(DATA_SHARED_KEY, dataSharedBundle)
                }

            val safetyLabelBundle =
                PersistableBundle().apply {
                    putLong(KEY_VERSION, SAFETY_LABELS_VERSION)
                    putPersistableBundle(DATA_LABEL_KEY, dataLabelBundle) }

            return PersistableBundle().apply {
                putLong(KEY_VERSION, TOP_LEVEL_VERSION)
                putPersistableBundle(SAFETY_LABEL_KEY, safetyLabelBundle)
            }
        }
    }
}
