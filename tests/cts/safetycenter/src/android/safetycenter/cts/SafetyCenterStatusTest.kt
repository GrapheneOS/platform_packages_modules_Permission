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

package android.safetycenter.cts

import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Parcel
import android.safetycenter.SafetyCenterStatus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterStatusTest {

    val baseStatus = SafetyCenterStatus.Builder()
            .setTitle("This is my title")
            .setSummary("This is my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .setRefreshStatus(SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS)
            .build()

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterStatus.Builder(baseStatus).setTitle("title").build().title)
                .isEqualTo("title")

        assertThat(SafetyCenterStatus.Builder(baseStatus).setTitle("different title").build().title)
                .isEqualTo("different title")
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(SafetyCenterStatus.Builder(baseStatus).setSummary("summary").build().summary)
                .isEqualTo("summary")

        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                        .setSummary("different summary")
                        .build()
                        .summary)
                .isEqualTo("different summary")
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                        .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                        .build()
                        .severityLevel)
                .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)

        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                        .setSeverityLevel(
                                SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
                        .build()
                        .severityLevel)
                .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun getSeverityLevel_defaultUnknown() {
        assertThat(
                SafetyCenterStatus.Builder()
                        .setTitle("This is my title")
                        .setSummary("This is my summary")
                        .build()
                        .severityLevel)
                .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getRefreshStatus_returnsRefreshStatus() {
        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                        .setRefreshStatus(SafetyCenterStatus.REFRESH_STATUS_NONE)
                        .build()
                        .refreshStatus)
                .isEqualTo(SafetyCenterStatus.REFRESH_STATUS_NONE)

        assertThat(
                SafetyCenterStatus.Builder(baseStatus)
                        .setRefreshStatus(SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
                        .build()
                        .refreshStatus)
                .isEqualTo(SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
    }

    @Test
    fun getRefreshStatus_defaultNone() {
        assertThat(
                SafetyCenterStatus.Builder()
                        .setTitle("This is my title")
                        .setSummary("This is my summary")
                        .build()
                        .refreshStatus)
                .isEqualTo(SafetyCenterStatus.REFRESH_STATUS_NONE)
    }

    @Test
    fun describeContents_returns0() {
        assertThat(baseStatus.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel: Parcel = Parcel.obtain()
        baseStatus.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val fromParcel = SafetyCenterStatus.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(baseStatus)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(baseStatus).isEqualTo(baseStatus)
        assertThat(baseStatus.hashCode()).isEqualTo(baseStatus.hashCode())
        assertThat(baseStatus.toString()).isEqualTo(baseStatus.toString())
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val status = SafetyCenterStatus.Builder()
                .setTitle("same title")
                .setSummary("same summary")
                .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                .build()
        val equivalentStatus = SafetyCenterStatus.Builder()
                .setTitle("same title")
                .setSummary("same summary")
                .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                .build()

        assertThat(status).isEqualTo(equivalentStatus)
        assertThat(status.hashCode()).isEqualTo(equivalentStatus.hashCode())
        assertThat(status.toString()).isEqualTo(equivalentStatus.toString())
    }

    @Test
    fun equals_hashCode_toString_fromCopyBuilder_areEqual() {
        val copyOfBaseStatus = SafetyCenterStatus.Builder(baseStatus).build()

        assertThat(copyOfBaseStatus).isEqualTo(baseStatus)
        assertThat(copyOfBaseStatus.hashCode()).isEqualTo(baseStatus.hashCode())
        assertThat(copyOfBaseStatus.toString()).isEqualTo(baseStatus.toString())
    }

    @Test
    fun equals_toString_withDifferentTitles_areNotEqual() {
        val unequalStatus = SafetyCenterStatus.Builder(baseStatus)
                .setTitle("that's discarsting")
                .build()

        assertThat(unequalStatus).isNotEqualTo(baseStatus)
        assertThat(unequalStatus.toString()).isNotEqualTo(baseStatus.toString())
    }

    @Test
    fun equals_toString_withDifferentSummaries_areNotEqual() {
        val unequalStatus = SafetyCenterStatus.Builder(baseStatus)
                .setSummary("discarsting sheet")
                .build()

        assertThat(unequalStatus).isNotEqualTo(baseStatus)
        assertThat(unequalStatus.toString()).isNotEqualTo(baseStatus.toString())
    }

    @Test
    fun equals_toString_withDifferentSeverityLevels_arNotEqual() {
        val unequalStatus = SafetyCenterStatus.Builder(baseStatus)
                .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                .build()

        assertThat(unequalStatus).isNotEqualTo(baseStatus)
        assertThat(unequalStatus.toString()).isNotEqualTo(baseStatus.toString())
    }

    @Test
    fun equals_toString_withDifferentRefreshStatuses_areNotEqual() {
        val unequalStatus = SafetyCenterStatus.Builder(baseStatus)
                .setRefreshStatus(SafetyCenterStatus.REFRESH_STATUS_NONE)
                .build()

        assertThat(unequalStatus).isNotEqualTo(baseStatus)
        assertThat(unequalStatus.toString()).isNotEqualTo(baseStatus.toString())
    }
}