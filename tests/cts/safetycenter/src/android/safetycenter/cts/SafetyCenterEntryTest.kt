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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterEntryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent1 = PendingIntent.getActivity(
            context,
            /* requestCode= */ 0,
            Intent("Fake Data"),
            PendingIntent.FLAG_IMMUTABLE)
    private val pendingIntent2 = PendingIntent.getActivity(
            context,
            /* requestCode= */ 0,
            Intent("Fake Different Data"),
            PendingIntent.FLAG_IMMUTABLE)

    private val iconAction1 = SafetyCenterEntry.IconAction(
            SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR, pendingIntent1)
    private val iconAction2 = SafetyCenterEntry.IconAction(
            SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO, pendingIntent2)

    private val entry1 = SafetyCenterEntry.Builder("eNtRy_iD")
            .setTitle("a title")
            .setSummary("a summary")
            .setPendingIntent(pendingIntent1)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN)
            .setIconAction(iconAction1)
            .build()

    @Test
    fun getId_returnsId() {
        assertThat(SafetyCenterEntry.Builder(entry1).setId("id_one").build().id)
                .isEqualTo("id_one")
        assertThat(SafetyCenterEntry.Builder(entry1).setId("id_two").build().id)
                .isEqualTo("id_two")
    }

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterEntry.Builder(entry1).setTitle("a title").build().title)
                .isEqualTo("a title")
        assertThat(SafetyCenterEntry.Builder(entry1).setTitle("another title").build().title)
                .isEqualTo("another title")
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(SafetyCenterEntry.Builder(entry1).setSummary("a summary").build().summary)
                .isEqualTo("a summary")
        assertThat(SafetyCenterEntry.Builder(entry1).setSummary("another summary").build().summary)
                .isEqualTo("another summary")
        assertThat(SafetyCenterEntry.Builder(entry1).setSummary(null).build().summary)
                .isNull()
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(SafetyCenterEntry.Builder(entry1)
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
                .build()
                .severityLevel)
                .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
        assertThat(SafetyCenterEntry.Builder(entry1)
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                .build()
                .severityLevel)
                .isEqualTo(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSeverityUnspecifiedIconType_returnsSeverityUnspecifiedIconType() {
        assertThat(entry1.severityUnspecifiedIconType).isEqualTo(
                SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)
        assertThat(SafetyCenterEntry.Builder(entry1)
                .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                .build()
                .severityUnspecifiedIconType)
                .isEqualTo(SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
    }

    @Test
    fun isEnabled_returnsIsEnabled() {
        assertThat(SafetyCenterEntry.Builder(entry1).setEnabled(true).build().isEnabled)
                .isTrue()
        assertThat(SafetyCenterEntry.Builder(entry1).setEnabled(false).build().isEnabled)
                .isFalse()
    }

    @Test
    fun isEnabled_defaultTrue() {
        assertThat(SafetyCenterEntry.Builder("eNtRy_iD")
                .setTitle("a title")
                .setPendingIntent(pendingIntent1)
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN)
                .build()
                .isEnabled)
                .isTrue()
    }

    @Test
    fun getPendingIntent_returnsPendingIntent() {
        assertThat(SafetyCenterEntry.Builder(entry1)
                .setPendingIntent(pendingIntent1)
                .build()
                .pendingIntent)
                .isEqualTo(pendingIntent1)
        assertThat(SafetyCenterEntry.Builder(entry1)
                .setPendingIntent(pendingIntent2)
                .build()
                .pendingIntent)
                .isEqualTo(pendingIntent2)
    }

    @Test
    fun getIconAction_returnsIconAction() {
        assertThat(SafetyCenterEntry.Builder(entry1).setIconAction(iconAction1).build().iconAction)
                .isEqualTo(iconAction1)
        assertThat(SafetyCenterEntry.Builder(entry1).setIconAction(iconAction2).build().iconAction)
                .isEqualTo(iconAction2)
        assertThat(SafetyCenterEntry.Builder(entry1).setIconAction(null).build().iconAction)
                .isNull()
    }

    @Test
    fun describeContents_returns0() {
        assertThat(entry1.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel = Parcel.obtain()

        entry1.writeToParcel(parcel, /* flags= */ 0)
        parcel.setDataPosition(0)

        val fromParcel = SafetyCenterEntry.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(entry1)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(entry1).isEqualTo(entry1)
        assertThat(entry1.hashCode()).isEqualTo(entry1.hashCode())
        assertThat(entry1.toString()).isEqualTo(entry1.toString())
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val entry = SafetyCenterEntry.Builder("id")
                .setTitle("a title")
                .setSummary("a summary")
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                .setPendingIntent(pendingIntent1)
                .setIconAction(SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO, pendingIntent2)
                .build()
        val equivalentEntry = SafetyCenterEntry.Builder("id")
                .setTitle("a title")
                .setSummary("a summary")
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                .setPendingIntent(pendingIntent1)
                .setIconAction(SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO, pendingIntent2)
                .build()

        assertThat(entry).isEqualTo(equivalentEntry)
        assertThat(entry.hashCode()).isEqualTo(equivalentEntry.hashCode())
        assertThat(entry.toString()).isEqualTo(equivalentEntry.toString())
    }

    @Test
    fun equals_hashCode_toString_fromCopyBuilder_areEqual() {
        val copyOfEntry1 = SafetyCenterEntry.Builder(entry1).build()

        assertThat(copyOfEntry1).isEqualTo(entry1)
        assertThat(copyOfEntry1.hashCode()).isEqualTo(entry1.hashCode())
        assertThat(copyOfEntry1.toString()).isEqualTo(entry1.toString())
    }

    @Test
    fun equals_toString_withDifferentIds_areNotEqual() {
        val differentFromEntry1 = SafetyCenterEntry.Builder(entry1)
                .setId("a different id")
                .build()

        assertThat(differentFromEntry1).isNotEqualTo(entry1)
        assertThat(differentFromEntry1.toString()).isNotEqualTo(entry1.toString())
    }

    @Test
    fun equals_toString_withDifferentTitles_areNotEqual() {
        val differentFromEntry1 = SafetyCenterEntry.Builder(entry1)
                .setTitle("a different title")
                .build()

        assertThat(differentFromEntry1).isNotEqualTo(entry1)
        assertThat(differentFromEntry1.toString()).isNotEqualTo(entry1.toString())
    }

    @Test
    fun equals_toString_withDifferentSummaries_areNotEqual() {
        val differentFromEntry1 = SafetyCenterEntry.Builder(entry1)
                .setSummary("a different summary")
                .build()

        assertThat(differentFromEntry1).isNotEqualTo(entry1)
        assertThat(differentFromEntry1.toString()).isNotEqualTo(entry1.toString())
    }

    @Test
    fun equals_toString_withDifferentSeverityLevels_areNotEqual() {
        val differentFromEntry1 = SafetyCenterEntry.Builder(entry1)
                .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                .build()

        assertThat(differentFromEntry1).isNotEqualTo(entry1)
        assertThat(differentFromEntry1.toString()).isNotEqualTo(entry1.toString())
    }

    @Test
    fun equals_toString_withDifferentSeverityUnspecifiedIconTypes_areNotEqual() {
        val differentFromEntry1 = SafetyCenterEntry.Builder(entry1)
                .setSeverityUnspecifiedIconType(
                        SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                .build()

        assertThat(differentFromEntry1).isNotEqualTo(entry1)
        assertThat(differentFromEntry1.toString()).isNotEqualTo(entry1.toString())
    }

    @Test
    fun equals_toString_withDifferentEnabledValues_areNotEqual() {
        val differentFromEntry1 = SafetyCenterEntry.Builder(entry1)
                .setEnabled(false)
                .build()

        assertThat(differentFromEntry1).isNotEqualTo(entry1)
        assertThat(differentFromEntry1.toString()).isNotEqualTo(entry1.toString())
    }

    @Test
    fun equals_toString_withDifferentPendingIntents_areNotEqual() {
        val differentFromEntry1 = SafetyCenterEntry.Builder(entry1)
                .setPendingIntent(pendingIntent2)
                .build()

        assertThat(differentFromEntry1).isNotEqualTo(entry1)
        assertThat(differentFromEntry1.toString()).isNotEqualTo(entry1.toString())
    }

    @Test
    fun equals_toString_withDifferentIconActions_areNotEqual() {
        val differentFromEntry1 = SafetyCenterEntry.Builder(entry1)
                .setIconAction(iconAction2)
                .build()

        assertThat(differentFromEntry1).isNotEqualTo(entry1)
        assertThat(differentFromEntry1.toString()).isNotEqualTo(entry1.toString())
    }

    @Test
    fun iconAction_getType_returnsType() {
        assertThat(SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR, pendingIntent1)
                .type)
                .isEqualTo(SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR)
        assertThat(SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO, pendingIntent1)
                .type)
                .isEqualTo(SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO)
    }

    @Test
    fun iconAction_getPendingIntent_returnsPendingIntent() {
        assertThat(SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR, pendingIntent1)
                .pendingIntent)
                .isEqualTo(pendingIntent1)
        assertThat(SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR, pendingIntent2)
                .pendingIntent)
                .isEqualTo(pendingIntent2)
    }

    @Test
    fun iconAction_describeContents_returns0() {
        assertThat(iconAction1.describeContents()).isEqualTo(0)
    }

    @Test
    fun iconAction_createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel = Parcel.obtain()

        iconAction1.writeToParcel(parcel, /* flags= */ 0)
        parcel.setDataPosition(0)

        val fromParcel = SafetyCenterEntry.IconAction.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(iconAction1)
    }

    @Test
    fun iconAction_equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(iconAction1).isEqualTo(iconAction1)
        assertThat(iconAction1.hashCode()).isEqualTo(iconAction1.hashCode())
        assertThat(iconAction1.toString()).isEqualTo(iconAction1.toString())
    }

    @Test
    fun iconAction_equals_hashCode_toString_equalByValue_areEqual() {
        val iconAction = SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR, pendingIntent1)
        val equivalentIconAction = SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR, pendingIntent1)

        assertThat(iconAction).isEqualTo(equivalentIconAction)
        assertThat(iconAction.hashCode()).isEqualTo(equivalentIconAction.hashCode())
        assertThat(iconAction.toString()).isEqualTo(equivalentIconAction.toString())
    }

    @Test
    fun iconAction_equals_toString_differentTypes_areNotEqual() {
        val iconAction = SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR, pendingIntent1)
        val differentIconAction = SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO, pendingIntent1)

        assertThat(iconAction).isNotEqualTo(differentIconAction)
        assertThat(iconAction.toString()).isNotEqualTo(differentIconAction.toString())
    }

    @Test
    fun intentAction_equals_toString_differentPendingIntents_areNotEqual() {
        val iconAction = SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR, pendingIntent1)
        val differentIconAction = SafetyCenterEntry.IconAction(
                SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR, pendingIntent2)

        assertThat(iconAction).isNotEqualTo(differentIconAction)
        assertThat(iconAction.toString()).isNotEqualTo(differentIconAction.toString())
    }
}