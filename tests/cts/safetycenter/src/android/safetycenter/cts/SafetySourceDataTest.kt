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
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT
import android.safetycenter.SafetySourceSeverity.LEVEL_INFORMATION
import android.safetycenter.SafetySourceSeverity.LEVEL_RECOMMENDATION
import android.safetycenter.SafetySourceSeverity.LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.IconAction.ICON_TYPE_GEAR
import android.safetycenter.cts.testing.EqualsHashCodeToStringTester
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceData]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetySourceDataTest {
    private val context: Context = getApplicationContext()

    private val status1 =
        SafetySourceStatus.Builder("Status title 1", "Status summary 1", LEVEL_UNSPECIFIED)
            .setEnabled(false)
            .build()
    private val status2 =
        SafetySourceStatus.Builder("Status title 2", "Status summary 2", LEVEL_RECOMMENDATION)
            .setIconAction(
                SafetySourceStatus.IconAction(
                    ICON_TYPE_GEAR,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent("IconAction PendingIntent 2"),
                        FLAG_IMMUTABLE
                    )
                )
            )
            .build()
    private val issue1 =
        SafetySourceIssue.Builder(
            "Issue id 1",
            "Issue summary 1",
            "Issue summary 1",
            LEVEL_INFORMATION,
            "issue_type_id"
        )
            .setSubtitle("Issue subtitle 1")
            .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
            .addAction(
                SafetySourceIssue.Action.Builder(
                    "action_id_1",
                    "Action label 1",
                    PendingIntent.getActivity(context, 0, Intent("Issue PendingIntent 1"),
                        FLAG_IMMUTABLE)
                )
                    .build()
            )
            .build()
    private val issue2 =
        SafetySourceIssue.Builder(
            "Issue id 2",
            "Issue title 2",
            "Issue summary 2",
            LEVEL_RECOMMENDATION,
            "issue_type_id"
        )
            .addAction(
                SafetySourceIssue.Action.Builder(
                    "action_id_2",
                    "Action label 2",
                    PendingIntent.getService(context, 0, Intent("Issue PendingIntent 2"),
                        FLAG_IMMUTABLE)
                )
                    .build()
            )
            .setOnDismissPendingIntent(
                PendingIntent.getService(
                    context,
                    0,
                    Intent("Issue OnDismissPendingIntent 2"),
                    FLAG_IMMUTABLE
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
        val safetySourceData = SafetySourceData.Builder().setStatus(status1).build()

        assertThat(safetySourceData.status).isEqualTo(status1)
    }

    @Test
    fun getIssues_withDefaultBuilder_returnsEmptyList() {
        val safetySourceData = SafetySourceData.Builder().build()

        assertThat(safetySourceData.issues).isEmpty()
    }

    @Test
    fun getIssues_whenSetExplicitly_returnsIssues() {
        val safetySourceData = SafetySourceData.Builder().addIssue(issue1).addIssue(issue2).build()

        assertThat(safetySourceData.issues).containsExactly(issue1, issue2).inOrder()
    }

    @Test
    fun clearIssues_removesAllIssues() {
        val safetySourceData =
            SafetySourceData.Builder().addIssue(issue1).addIssue(issue2).clearIssues().build()

        assertThat(safetySourceData.issues).isEmpty()
    }

    @Test
    fun describeContents_returns0() {
        val safetySourceData =
            SafetySourceData.Builder().setStatus(status1).addIssue(issue1).addIssue(issue2).build()

        assertThat(safetySourceData.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        val safetySourceData =
            SafetySourceData.Builder().setStatus(status1).addIssue(issue1).addIssue(issue2).build()

        assertThat(safetySourceData).recreatesEqual(SafetySourceData.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(SafetySourceData.Builder().build(),
                SafetySourceData.Builder().build())
            .addEqualityGroup(
                SafetySourceData.Builder().setStatus(status1).build(),
                SafetySourceData.Builder().setStatus(status1).build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder().addIssue(issue1).addIssue(issue2).build(),
                SafetySourceData.Builder().addIssue(issue1).addIssue(issue2).build()
            )
            .addEqualityGroup(SafetySourceData.Builder().setStatus(status2).build())
            .addEqualityGroup(SafetySourceData.Builder().addIssue(issue2).addIssue(issue1).build())
            .addEqualityGroup(SafetySourceData.Builder().addIssue(issue1).build())
            .addEqualityGroup(
                SafetySourceData.Builder().setStatus(status2).addIssue(issue1).addIssue(issue2)
                    .build(),
                SafetySourceData.Builder().setStatus(status2).addIssue(issue1).addIssue(issue2)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceData.Builder().setStatus(status1).addIssue(issue1).addIssue(issue2)
                    .build()
            )
            .test()
    }
}
