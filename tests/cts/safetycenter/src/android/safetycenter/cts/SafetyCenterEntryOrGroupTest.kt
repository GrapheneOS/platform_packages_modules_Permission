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
import android.safetycenter.SafetyCenterEntryOrGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterEntryOrGroupTest {
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

    val entryGroup1 = SafetyCenterEntryGroup.Builder("gRoUp_iD_oNe")
            .setTitle("A group title")
            .setSummary("A group summary")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
            .setEntries(listOf(entry1))
            .build()
    val entryGroup2 = SafetyCenterEntryGroup.Builder("gRoUp_iD_tWo")
            .setTitle("Another group title")
            .setSummary("Another group summary")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setEntries(listOf(entry2))
            .build()

    val entryOrGroupWithEntry = SafetyCenterEntryOrGroup(entry1)
    val entryOrGroupWithGroup = SafetyCenterEntryOrGroup(entryGroup1)

    @Test
    fun getEntry_returnsEntry() {
        assertThat(entryOrGroupWithEntry.entry).isEqualTo(entry1)
    }

    @Test
    fun getEntry_returnsEntry_whenNull() {
        assertThat(entryOrGroupWithGroup.entry).isNull()
    }

    @Test
    fun getEntryGroup_returnsEntryGroup() {
        assertThat(entryOrGroupWithGroup.entryGroup).isEqualTo(entryGroup1)
    }

    @Test
    fun getEntryGroup_returnsEntryGroup_whenNull() {
        assertThat(entryOrGroupWithEntry.entryGroup).isNull()
    }

    @Test
    fun describeContents_returns0() {
        assertThat(entryOrGroupWithEntry.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_withEntry_returnsEquivalentObject() {
        runCreateFromParcel_withWriteToParcel_withGroup_returnsEquivalentObjectTest(
                entryOrGroupWithEntry)
    }

    @Test
    fun createFromParcel_withWriteToParcel_withGroup_returnsEquivalentObject() {
        runCreateFromParcel_withWriteToParcel_withGroup_returnsEquivalentObjectTest(
                entryOrGroupWithGroup)
    }

    fun runCreateFromParcel_withWriteToParcel_withGroup_returnsEquivalentObjectTest(
        entryOrGroup: SafetyCenterEntryOrGroup
    ) {
        val parcel: Parcel = Parcel.obtain()

        entryOrGroup.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val fromParcel = SafetyCenterEntryOrGroup.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(entryOrGroup)
    }

    @Test
    fun equals_hashCode_toString_whenEqualByReference_areEqual() {
        assertThat(entryOrGroupWithEntry).isEqualTo(entryOrGroupWithEntry)
        assertThat(entryOrGroupWithEntry.hashCode()).isEqualTo(entryOrGroupWithEntry.hashCode())
        assertThat(entryOrGroupWithEntry.toString()).isEqualTo(entryOrGroupWithEntry.toString())
    }

    @Test
    fun equals_hashCode_toString_whenEqualByValue_withEntry_areEqual() {
        val entryOrGroup = SafetyCenterEntryOrGroup(entry1)
        val equivalentEntryOrGroup = SafetyCenterEntryOrGroup(entry1)

        assertThat(entryOrGroup).isEqualTo(equivalentEntryOrGroup)
        assertThat(entryOrGroup.hashCode()).isEqualTo(equivalentEntryOrGroup.hashCode())
        assertThat(entryOrGroup.toString()).isEqualTo(equivalentEntryOrGroup.toString())
    }

    @Test
    fun equals_hashCode_toString_whenEqualByValue_withGroup_areEqual() {
        val entryOrGroup = SafetyCenterEntryOrGroup(entryGroup1)
        val equivalentEntryOrGroup = SafetyCenterEntryOrGroup(entryGroup1)

        assertThat(entryOrGroup).isEqualTo(equivalentEntryOrGroup)
        assertThat(entryOrGroup.hashCode()).isEqualTo(equivalentEntryOrGroup.hashCode())
        assertThat(entryOrGroup.toString()).isEqualTo(equivalentEntryOrGroup.toString())
    }

    @Test
    fun equals_toString_withDifferentEntryAndGroup_areNotEqual() {
        assertThat(entryOrGroupWithEntry).isNotEqualTo(entryOrGroupWithGroup)
        assertThat(entryOrGroupWithEntry.toString()).isNotEqualTo(entryOrGroupWithGroup.toString())
    }

    @Test
    fun equals_toString_withDifferentEntries_areNotEqual() {
        val entryOrGroup = SafetyCenterEntryOrGroup(entry1)
        val differentEntryOrGroup = SafetyCenterEntryOrGroup(entry2)

        assertThat(entryOrGroup).isNotEqualTo(differentEntryOrGroup)
        assertThat(entryOrGroup.toString()).isNotEqualTo(differentEntryOrGroup.toString())
    }

    @Test
    fun equals_toString_withDifferentGroups_areNotEqual() {
        val entryOrGroup = SafetyCenterEntryOrGroup(entryGroup1)
        val differentEntryOrGroup = SafetyCenterEntryOrGroup(entryGroup2)

        assertThat(entryOrGroup).isNotEqualTo(differentEntryOrGroup)
        assertThat(entryOrGroup.toString()).isNotEqualTo(differentEntryOrGroup.toString())
    }
}