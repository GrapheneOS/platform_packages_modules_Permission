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
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Parcel
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyCenterStatus
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent = PendingIntent.getActivity(
            context,
            /* requestCode= */ 0,
            Intent("Fake Data"),
            PendingIntent.FLAG_IMMUTABLE)

    val status1 = SafetyCenterStatus.Builder()
            .setTitle("This is my title")
            .setSummary("This is my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()
    val status2 = SafetyCenterStatus.Builder()
            .setTitle("This is also my title")
            .setSummary("This is also my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
            .build()

    val issue1 = SafetyCenterIssue.Builder("iSsUe_iD_oNe")
            .setTitle("An issue title")
            .setSummary("An issue summary")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .build()
    val issue2 = SafetyCenterIssue.Builder("iSsUe_iD_tWo")
            .setTitle("Another issue title")
            .setSummary("Another issue summary")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    val entry1 = SafetyCenterEntry.Builder("eNtRy_iD_OnE")
            .setTitle("An entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
            .build()
    val entry2 = SafetyCenterEntry.Builder("eNtRy_iD_TwO")
            .setTitle("Another entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    val entryGroup1 = SafetyCenterEntryGroup.Builder("eNtRy_gRoUp_iD")
            .setTitle("An entry group title")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setEntries(listOf(entry2))
            .build()

    val entryOrGroup1 = SafetyCenterEntryOrGroup(entry1)
    val entryOrGroup2 = SafetyCenterEntryOrGroup(entryGroup1)

    val staticEntry1 = SafetyCenterStaticEntry(
            "A static entry title",
            "A static entry summary",
            pendingIntent)
    val staticEntry2 = SafetyCenterStaticEntry(
            "Another static entry title",
            "Another static entry summary",
            pendingIntent)

    val staticEntryGroup1 = SafetyCenterStaticEntryGroup(
            "A static entry group title", listOf(staticEntry1))
    val staticEntryGroup2 = SafetyCenterStaticEntryGroup(
            "Another static entry group title", listOf(staticEntry2))

    val data1 = SafetyCenterData(
            status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))
    val data2 = SafetyCenterData(
            status2, listOf(issue2), listOf(entryOrGroup2), listOf(staticEntryGroup2))

    @Test
    fun getStatus_returnsStatus() {
        assertThat(data1.status).isEqualTo(status1)
        assertThat(data2.status).isEqualTo(status2)
    }

    @Test
    fun getIssues_returnsIssues() {
        assertThat(data1.issues).containsExactly(issue1)
        assertThat(data2.issues).containsExactly(issue2)
    }

    @Test
    fun getIssues_mutationsAreNotReflected() {
        val mutatedIssues = data1.issues
        mutatedIssues.add(issue2)

        assertThat(mutatedIssues).containsExactly(issue1, issue2)
        assertThat(data1.issues).doesNotContain(issue2)
    }

    @Test
    fun getEntriesOrGroups_returnsEntriesOrGroups() {
        assertThat(data1.entriesOrGroups).containsExactly(entryOrGroup1)
        assertThat(data2.entriesOrGroups).containsExactly(entryOrGroup2)
    }

    @Test
    fun getEntriesOrGroups_mutationsAreNotReflected() {
        val mutatedEntriesOrGroups = data1.entriesOrGroups
        mutatedEntriesOrGroups.add(entryOrGroup2)

        assertThat(mutatedEntriesOrGroups).containsExactly(entryOrGroup1, entryOrGroup2)
        assertThat(data1.entriesOrGroups).doesNotContain(entryOrGroup2)
    }

    @Test
    fun getStaticEntryGroups_returnsStaticEntryGroups() {
        assertThat(data1.staticEntryGroups).containsExactly(staticEntryGroup1)
        assertThat(data2.staticEntryGroups).containsExactly(staticEntryGroup2)
    }

    @Test
    fun getStaticEntryGroups_mutationsAreNotReflected() {
        val mutatedStaticEntryGroups = data1.staticEntryGroups
        mutatedStaticEntryGroups.add(staticEntryGroup2)

        assertThat(mutatedStaticEntryGroups).containsExactly(staticEntryGroup1, staticEntryGroup2)
        assertThat(data1.staticEntryGroups).doesNotContain(staticEntryGroup2)
    }

    @Test
    fun describeContents_returns0() {
        assertThat(data1.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel: Parcel = Parcel.obtain()

        data1.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val fromParcel = SafetyCenterData.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(data1)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(data1).isEqualTo(data1)
        assertThat(data1.hashCode()).isEqualTo(data1.hashCode())
        assertThat(data1.toString()).isEqualTo(data1.toString())
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val data = SafetyCenterData(
                status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))
        val equivalentData = SafetyCenterData(
                status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))

        assertThat(data).isEqualTo(equivalentData)
        assertThat(data.hashCode()).isEqualTo(equivalentData.hashCode())
        assertThat(data.toString()).isEqualTo(equivalentData.toString())
    }

    @Test
    fun equals_hashCode_toString_withEmptyLists_equalByValue_areEqual() {
        val data = SafetyCenterData(status1, listOf(), listOf(), listOf())
        val equivalentData = SafetyCenterData(status1, listOf(), listOf(), listOf())

        assertThat(data).isEqualTo(equivalentData)
        assertThat(data.hashCode()).isEqualTo(equivalentData.hashCode())
        assertThat(data.toString()).isEqualTo(equivalentData.toString())
    }

    @Test
    fun equals_toString_withDifferentStatuses_areNotEqual() {
        val data = SafetyCenterData(
                status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))
        val differentData = SafetyCenterData(
                status2, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))

        assertThat(data).isNotEqualTo(differentData)
        assertThat(data.toString()).isNotEqualTo(differentData.toString())
    }

    @Test
    fun equals_toString_withDifferentIssues_areNotEqual() {
        val data = SafetyCenterData(
                status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))
        val differentData = SafetyCenterData(
                status1, listOf(issue2), listOf(entryOrGroup1), listOf(staticEntryGroup1))

        assertThat(data).isNotEqualTo(differentData)
        assertThat(data.toString()).isNotEqualTo(differentData.toString())
    }

    @Test
    fun equals_toString_withDifferentEntriesOrGroups_areNotEqual() {
        val data = SafetyCenterData(
                status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))
        val differentData = SafetyCenterData(
                status1, listOf(issue1), listOf(entryOrGroup2), listOf(staticEntryGroup1))

        assertThat(data).isNotEqualTo(differentData)
        assertThat(data.toString()).isNotEqualTo(differentData.toString())
    }

    @Test
    fun equals_toString_withDifferentStaticEntryGroups_areNotEqual() {
        val data = SafetyCenterData(
                status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))
        val differentData = SafetyCenterData(
                status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup2))

        assertThat(data).isNotEqualTo(differentData)
        assertThat(data.toString()).isNotEqualTo(differentData.toString())
    }
}