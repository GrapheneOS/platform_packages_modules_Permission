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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Parcel
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntryGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterEntryGroupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent = PendingIntent.getActivity(
            context,
            /* requestCode= */ 0,
            Intent("Fake Data"),
            PendingIntent.FLAG_IMMUTABLE)

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

    val groupId1 = "gRoUp_iD_oNe"
    val groupId2 = "gRoUp_iD_tWo"

    val entryGroup1 = SafetyCenterEntryGroup.Builder(groupId1)
            .setTitle("A group title")
            .setSummary("A group summary")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
            .setEntries(listOf(entry1))
            .build()
    val entryGroup2 = SafetyCenterEntryGroup.Builder(groupId2)
            .setTitle("Another group title")
            .setSummary("Another group summary")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setEntries(listOf(entry2))
            .build()

    @Test
    fun getId_returnsId() {
        assertThat(entryGroup1.id).isEqualTo(groupId1)
        assertThat(entryGroup2.id).isEqualTo(groupId2)
    }

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setTitle("title one").build().title)
                .isEqualTo("title one")
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setTitle("title two").build().title)
                .isEqualTo("title two")
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setSummary("one").build().summary)
                .isEqualTo("one")
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setSummary("two").build().summary)
                .isEqualTo("two")
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1).setSummary(null).build().summary)
                .isNull()
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1)
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
                .build()
                .severityLevel)
                .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1)
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_NONE)
                .build()
                .severityLevel)
                .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_NONE)
    }

    @Test
    fun getSeverityNoneIconType_returnsSeverityNoneIconType() {
        assertThat(entryGroup1.severityNoneIconType)
                .isEqualTo(SafetyCenterEntry.SEVERITY_NONE_ICON_TYPE_NO_ICON)
        assertThat(SafetyCenterEntryGroup.Builder(entryGroup1)
                .setSeverityNoneIconType(SafetyCenterEntry.SEVERITY_NONE_ICON_TYPE_PRIVACY)
                .build()
                .severityNoneIconType)
                .isEqualTo(SafetyCenterEntry.SEVERITY_NONE_ICON_TYPE_PRIVACY)
    }

    @Test
    fun getEntries_returnsEntries() {
        assertThat(entryGroup1.entries).containsExactly(entry1)
        assertThat(entryGroup2.entries).containsExactly(entry2)
    }

    @Test
    fun getEntries_mutationsAreNotReflected() {
        val mutatedEntries = entryGroup1.entries
        mutatedEntries.add(entry2)

        assertThat(mutatedEntries).containsExactly(entry1, entry2)
        assertThat(entryGroup1.entries).doesNotContain(entry2)
    }

    @Test
    fun describeContents_returns0() {
        assertThat(entryGroup1.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel = Parcel.obtain()

        entryGroup1.writeToParcel(parcel, /* flags= */ 0)
        parcel.setDataPosition(0)

        val fromParcel = SafetyCenterEntryGroup.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(entryGroup1)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(entryGroup1).isEqualTo(entryGroup1)
        assertThat(entryGroup1.hashCode()).isEqualTo(entryGroup1.hashCode())
        assertThat(entryGroup1.toString()).isEqualTo(entryGroup1.toString())
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val entry = SafetyCenterEntryGroup.Builder(groupId1)
                .setTitle("A group title")
                .setSummary("A group summary")
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                .setEntries(listOf(entry1))
                .build()
        val equivalentEntry = SafetyCenterEntryGroup.Builder(groupId1)
                .setTitle("A group title")
                .setSummary("A group summary")
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                .setEntries(listOf(entry1))
                .build()

        assertThat(entry).isEqualTo(equivalentEntry)
        assertThat(entry.hashCode()).isEqualTo(equivalentEntry.hashCode())
        assertThat(entry.toString()).isEqualTo(equivalentEntry.toString())
    }

    @Test
    fun equals_hashCode_toString_fromCopyBuilder_areEqual() {
        val equivalentToEntryGroup1 = SafetyCenterEntryGroup.Builder(entryGroup1).build()

        assertThat(equivalentToEntryGroup1).isEqualTo(entryGroup1)
        assertThat(equivalentToEntryGroup1.hashCode()).isEqualTo(entryGroup1.hashCode())
        assertThat(equivalentToEntryGroup1.toString()).isEqualTo(entryGroup1.toString())
    }

    @Test
    fun equals_toString_withDifferentIds_areNotEqual() {
        val differentFromEntryGroup1 = SafetyCenterEntryGroup.Builder(entryGroup1)
                .setId("different!")
                .build()

        assertThat(differentFromEntryGroup1).isNotEqualTo(entryGroup1)
        assertThat(differentFromEntryGroup1.toString()).isNotEqualTo(entryGroup1.toString())
    }

    @Test
    fun equals_toString_withDifferentTitles_areNotEqual() {
        val differentFromEntryGroup1 = SafetyCenterEntryGroup.Builder(entryGroup1)
                .setTitle("different!")
                .build()

        assertThat(differentFromEntryGroup1).isNotEqualTo(entryGroup1)
        assertThat(differentFromEntryGroup1.toString()).isNotEqualTo(entryGroup1.toString())
    }

    @Test
    fun equals_toString_withDifferentSummaries_areNotEqual() {
        val differentFromEntryGroup1 = SafetyCenterEntryGroup.Builder(entryGroup1)
                .setSummary("different!")
                .build()

        assertThat(differentFromEntryGroup1).isNotEqualTo(entryGroup1)
        assertThat(differentFromEntryGroup1.toString()).isNotEqualTo(entryGroup1.toString())
    }

    @Test
    fun equals_toString_withDifferentSeverityLevels_areNotEqual() {
        val differentFromEntryGroup1 = SafetyCenterEntryGroup.Builder(entryGroup1)
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN)
                .build()

        assertThat(differentFromEntryGroup1).isNotEqualTo(entryGroup1)
        assertThat(differentFromEntryGroup1.toString()).isNotEqualTo(entryGroup1.toString())
    }

    @Test
    fun equals_toString_withDifferentSeverityNoneIconTypes_areNotEqual() {
        val differentFromEntryGroup1 = SafetyCenterEntryGroup.Builder(entryGroup1)
                .setSeverityNoneIconType(SafetyCenterEntry.SEVERITY_NONE_ICON_TYPE_PRIVACY)
                .build()

        assertThat(differentFromEntryGroup1).isNotEqualTo(entryGroup1)
        assertThat(differentFromEntryGroup1.toString()).isNotEqualTo(entryGroup1.toString())
    }

    @Test
    fun equals_toString_withDifferentEntries_areNotEqual() {
        val differentFromEntryGroup1 = SafetyCenterEntryGroup.Builder(entryGroup1)
                .setEntries(listOf(entry2))
                .build()

        assertThat(differentFromEntryGroup1).isNotEqualTo(entryGroup1)
        assertThat(differentFromEntryGroup1.toString()).isNotEqualTo(entryGroup1.toString())
    }

    @Test
    fun equals_toString_withDifferentEntries_emptyList_areNotEqual() {
        val differentFromEntryGroup1 = SafetyCenterEntryGroup.Builder(entryGroup1)
                .setEntries(listOf())
                .build()

        assertThat(differentFromEntryGroup1).isNotEqualTo(entryGroup1)
        assertThat(differentFromEntryGroup1.toString()).isNotEqualTo(entryGroup1.toString())
    }
}