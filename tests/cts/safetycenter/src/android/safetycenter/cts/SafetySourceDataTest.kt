/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Parcel
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.IconAction.ICON_TYPE_GEAR
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceData]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetySourceDataTest {
    private val context: Context = getApplicationContext()

    private val status1 = SafetySourceStatus.Builder(
            "Status title 1",
            "Status summary 1",
            SafetySourceStatus.STATUS_LEVEL_NONE,
            PendingIntent.getActivity(context, 0 /* requestCode= */,
                    Intent("Status PendingIntent 1"), FLAG_IMMUTABLE))
            .setEnabled(false)
            .build()
    private val status2 = SafetySourceStatus.Builder(
            "Status title 2",
            "Status summary 2",
            SafetySourceStatus.STATUS_LEVEL_RECOMMENDATION,
            PendingIntent.getActivity(context, 0 /* requestCode= */,
                    Intent("Status PendingIntent 2"), FLAG_IMMUTABLE))
            .setIconAction(SafetySourceStatus.IconAction(ICON_TYPE_GEAR,
                    PendingIntent.getActivity(context, 0 /* requestCode= */,
                            Intent("IconAction PendingIntent 2"), FLAG_IMMUTABLE)))
            .build()
    private val issue1 = SafetySourceIssue.Builder(
        "Issue id 1",
        "Issue summary 1",
        "Issue summary 1",
        SafetySourceIssue.SEVERITY_LEVEL_INFORMATION, "issue_type_id"
    )
        .setSubtitle("Issue subtitle 1")
        .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
        .addAction(
            SafetySourceIssue.Action.Builder(
                "action_id_1",
                "Action label 1",
                PendingIntent.getActivity(
                    context, 0 /* requestCode= */,
                    Intent("Issue PendingIntent 1"), FLAG_IMMUTABLE
                )
            )
                .build()
        )
        .build()
    private val issue2 = SafetySourceIssue.Builder(
        "Issue id 2",
        "Issue title 2",
        "Issue summary 2",
        SafetySourceIssue.SEVERITY_LEVEL_RECOMMENDATION, "issue_type_id"
    )
        .addAction(
            SafetySourceIssue.Action.Builder(
                "action_id_2",
                "Action label 2",
                PendingIntent.getService(
                    context, 0 /* requestCode= */,
                    Intent("Issue PendingIntent 2"), FLAG_IMMUTABLE
                )
            ).build()
        )
        .setOnDismissPendingIntent(
            PendingIntent.getService(
                context,
                0 /* requestCode= */,
                Intent("Issue OnDismissPendingIntent 2"), FLAG_IMMUTABLE
            )
        )
        .build()

    @Test
    fun getStatus_withDefaultBuilder_returnsNull() {
        val safetySourceData = SafetySourceData.Builder().build()

        assertThat(safetySourceData.status).isNull()
    }

    @Test
    fun getStatus_whenSetExplicitly_returnsStatus() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .build()

        assertThat(safetySourceData.status).isEqualTo(status1)
    }

    @Test
    fun getIssues_withDefaultBuilder_returnsEmptyList() {
        val safetySourceData = SafetySourceData.Builder().build()

        assertThat(safetySourceData.issues).isEmpty()
    }

    @Test
    fun getIssues_whenSetExplicitly_returnsIssues() {
        val safetySourceData = SafetySourceData.Builder()
                .addIssue(issue1)
                .addIssue(issue2)
                .build()

        assertThat(safetySourceData.issues).containsExactly(issue1, issue2).inOrder()
    }

    @Test
    fun clearIssues_removesAllIssues() {
        val safetySourceData = SafetySourceData.Builder()
            .addIssue(issue1)
            .addIssue(issue2)
            .clearIssues()
            .build()

        assertThat(safetySourceData.issues).isEmpty()
    }

    @Test
    fun describeContents_returns0() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .build()

        assertThat(safetySourceData.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsOriginalSafetySourceData() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .build()

        val parcel: Parcel = Parcel.obtain()
        safetySourceData.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val safetySourceDataFromParcel: SafetySourceData =
                SafetySourceData.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(safetySourceDataFromParcel).isEqualTo(safetySourceData)
    }

    // TODO(b/208473675): Use `EqualsTester` for testing `hashcode` and `equals`.
    @Test
    fun hashCode_equals_toString_withEqualByReference_withoutStatusAndIssues_areEqual() {
        val safetySourceData = SafetySourceData.Builder().build()
        val otherSafetySourceData = safetySourceData

        assertThat(safetySourceData.hashCode()).isEqualTo(otherSafetySourceData.hashCode())
        assertThat(safetySourceData).isEqualTo(otherSafetySourceData)
        assertThat(safetySourceData.toString()).isEqualTo(otherSafetySourceData.toString())
    }

    @Test
    fun hashCode_equals_toString_withEqualByReference_withoutIssues_areEqual() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .build()
        val otherSafetySourceData = safetySourceData

        assertThat(safetySourceData.hashCode()).isEqualTo(otherSafetySourceData.hashCode())
        assertThat(safetySourceData).isEqualTo(otherSafetySourceData)
        assertThat(safetySourceData.toString()).isEqualTo(otherSafetySourceData.toString())
    }

    @Test
    fun hashCode_equals_toString_withEqualByReference_areEqual() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .build()
        val otherSafetySourceData = safetySourceData

        assertThat(safetySourceData.hashCode()).isEqualTo(otherSafetySourceData.hashCode())
        assertThat(safetySourceData).isEqualTo(otherSafetySourceData)
        assertThat(safetySourceData.toString()).isEqualTo(otherSafetySourceData.toString())
    }

    @Test
    fun hashCode_equals_toString_withAllFieldsEqual_areEqual() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .build()
        val otherSafetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .build()

        assertThat(safetySourceData.hashCode()).isEqualTo(otherSafetySourceData.hashCode())
        assertThat(safetySourceData).isEqualTo(otherSafetySourceData)
        assertThat(safetySourceData.toString()).isEqualTo(otherSafetySourceData.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentIssues_areNotEqual() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .build()
        val otherSafetySourceData = SafetySourceData.Builder()
                .setStatus(status2)
                .addIssue(issue1)
                .addIssue(issue2)
                .build()

        assertThat(safetySourceData.hashCode()).isNotEqualTo(otherSafetySourceData.hashCode())
        assertThat(safetySourceData).isNotEqualTo(otherSafetySourceData)
        assertThat(safetySourceData.toString()).isNotEqualTo(otherSafetySourceData.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentStatuses_areNotEqual() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .build()
        val otherSafetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .addIssue(issue1)
                .build()

        assertThat(safetySourceData.hashCode()).isNotEqualTo(otherSafetySourceData.hashCode())
        assertThat(safetySourceData).isNotEqualTo(otherSafetySourceData)
        assertThat(safetySourceData.toString()).isNotEqualTo(otherSafetySourceData.toString())
    }

    @Test
    fun hashCode_equals_toString_withStatusSetInOneAndNotOther_areNotEqual() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .build()
        val otherSafetySourceData = SafetySourceData.Builder().build()

        assertThat(safetySourceData.hashCode()).isNotEqualTo(otherSafetySourceData.hashCode())
        assertThat(safetySourceData).isNotEqualTo(otherSafetySourceData)
        assertThat(safetySourceData.toString()).isNotEqualTo(otherSafetySourceData.toString())
    }

    @Test
    fun hashCode_equals_toString_withIssuesSetInOneAndNotOther_areNotEqual() {
        val safetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .build()
        val otherSafetySourceData = SafetySourceData.Builder()
                .setStatus(status1)
                .build()

        assertThat(safetySourceData.hashCode()).isNotEqualTo(otherSafetySourceData.hashCode())
        assertThat(safetySourceData).isNotEqualTo(otherSafetySourceData)
        assertThat(safetySourceData.toString()).isNotEqualTo(otherSafetySourceData.toString())
    }
}