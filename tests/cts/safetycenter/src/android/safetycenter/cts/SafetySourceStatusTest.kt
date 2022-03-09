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
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.IconAction
import android.safetycenter.SafetySourceStatus.IconAction.ICON_TYPE_GEAR
import android.safetycenter.SafetySourceStatus.IconAction.ICON_TYPE_INFO
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_OK
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceStatus]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetySourceStatusTest {
    private val context: Context = getApplicationContext()

    private val pendingIntent1: PendingIntent = PendingIntent.getActivity(
        context,
        0 /* requestCode= */, Intent("PendingIntent 1"), FLAG_IMMUTABLE
    )
    private val iconAction1 = IconAction(ICON_TYPE_INFO, pendingIntent1)
    private val pendingIntent2: PendingIntent = PendingIntent.getActivity(
        context,
        0 /* requestCode= */, Intent("PendingIntent 2"), FLAG_IMMUTABLE
    )
    private val iconAction2 = IconAction(ICON_TYPE_GEAR, pendingIntent2)

    @Test
    fun iconAction_getIconType_returnsIconType() {
        val iconAction = IconAction(ICON_TYPE_INFO, pendingIntent1)

        assertThat(iconAction.iconType).isEqualTo(ICON_TYPE_INFO)
    }

    @Test
    fun iconAction_getPendingIntent_returnsPendingIntent() {
        val iconAction = IconAction(ICON_TYPE_GEAR, pendingIntent1)

        assertThat(iconAction.pendingIntent).isEqualTo(pendingIntent1)
    }

    @Test
    fun iconAction_describeContents_returns0() {
        val iconAction = IconAction(ICON_TYPE_GEAR, pendingIntent1)

        assertThat(iconAction.describeContents()).isEqualTo(0)
    }

    @Test
    fun iconAction_createFromParcel_withWriteToParcel_returnsOriginalAction() {
        val iconAction = IconAction(ICON_TYPE_GEAR, pendingIntent1)

        val parcel: Parcel = Parcel.obtain()
        iconAction.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val iconActionFromParcel: IconAction = IconAction.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(iconActionFromParcel).isEqualTo(iconAction)
    }

    // TODO(b/208473675): Use `EqualsTester` for testing `hashcode` and `equals`.
    @Test
    fun iconAction_hashCode_equals_toString_withEqualByReferenceIconActions_areEqual() {
        val iconAction = IconAction(ICON_TYPE_GEAR, pendingIntent1)
        val otherIconAction = iconAction

        assertThat(iconAction.hashCode()).isEqualTo(otherIconAction.hashCode())
        assertThat(iconAction).isEqualTo(otherIconAction)
        assertThat(iconAction.toString()).isEqualTo(otherIconAction.toString())
    }

    @Test
    fun iconAction_hashCode_equals_toString_withAllFieldsEqual_areEqual() {
        val iconAction = IconAction(ICON_TYPE_GEAR, pendingIntent1)
        val otherIconAction = IconAction(ICON_TYPE_GEAR, pendingIntent1)

        assertThat(iconAction.hashCode()).isEqualTo(otherIconAction.hashCode())
        assertThat(iconAction).isEqualTo(otherIconAction)
        assertThat(iconAction.toString()).isEqualTo(otherIconAction.toString())
    }

    @Test
    fun iconAction_hashCode_equals_toString_withDifferentIconTypes_areNotEqual() {
        val iconAction = IconAction(ICON_TYPE_GEAR, pendingIntent1)
        val otherIconAction = IconAction(ICON_TYPE_INFO, pendingIntent1)

        assertThat(iconAction.hashCode()).isNotEqualTo(otherIconAction.hashCode())
        assertThat(iconAction).isNotEqualTo(otherIconAction)
        assertThat(iconAction.toString()).isNotEqualTo(otherIconAction.toString())
    }

    @Test
    fun iconAction_hashCode_equals_toString_withDifferentPendingIntents_areNotEqual() {
        val iconAction = IconAction(ICON_TYPE_GEAR, pendingIntent1)
        val otherIconAction = IconAction(ICON_TYPE_GEAR, pendingIntent2)

        assertThat(iconAction.hashCode()).isNotEqualTo(otherIconAction.hashCode())
        assertThat(iconAction).isNotEqualTo(otherIconAction)
        assertThat(iconAction.toString()).isNotEqualTo(otherIconAction.toString())
    }

    @Test
    fun getTitle_returnsTitle() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()

        assertThat(safetySourceStatus.title).isEqualTo("Status title")
    }

    @Test
    fun getSummary_returnsSummary() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()

        assertThat(safetySourceStatus.summary).isEqualTo("Status summary")
    }

    @Test
    fun getStatusLevel_returnsStatusLevel() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()

        assertThat(safetySourceStatus.statusLevel).isEqualTo(STATUS_LEVEL_OK)
    }

    @Test
    fun getPendingIntent_returnsPendingIntent() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()

        assertThat(safetySourceStatus.pendingIntent).isEqualTo(pendingIntent1)
    }

    @Test
    fun getIconAction_withDefaultBuilder_returnsNull() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()

        assertThat(safetySourceStatus.iconAction).isNull()
    }

    @Test
    fun getIconAction_whenSetExplicitly_returnsIconAction() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .setIconAction(iconAction1)
            .build()

        assertThat(safetySourceStatus.iconAction).isEqualTo(iconAction1)
    }

    @Test
    fun isEnabled_withDefaultBuilder_returnsTrue() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()

        assertThat(safetySourceStatus.isEnabled).isTrue()
    }

    @Test
    fun isEnabled_whenSetExplicitly_returnsEnabled() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .setEnabled(false)
            .build()

        assertThat(safetySourceStatus.isEnabled).isFalse()
    }

    @Test
    fun describeContents_returns0() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .setIconAction(iconAction1)
            .build()

        assertThat(safetySourceStatus.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsOriginalSafetySourceStatus() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .setIconAction(iconAction1)
            .setEnabled(true)
            .build()

        val parcel: Parcel = Parcel.obtain()
        safetySourceStatus.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val safetySourceStatusFromParcel: SafetySourceStatus =
            SafetySourceStatus.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(safetySourceStatusFromParcel).isEqualTo(safetySourceStatus)
    }

    // TODO(b/208473675): Use `EqualsTester` for testing `hashcode` and `equals`.
    @Test
    fun hashCode_equals_toString_withEqualByReferenceSafetySourceStatuses_areEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .setIconAction(iconAction1)
            .setEnabled(true)
            .build()
        val otherSafetySourceStatus = safetySourceStatus

        assertThat(safetySourceStatus.hashCode()).isEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withAllFieldsEqual_areEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .setIconAction(iconAction1)
            .setEnabled(true)
            .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .setIconAction(iconAction1)
            .setEnabled(true)
            .build()

        assertThat(safetySourceStatus.hashCode()).isEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentTitles_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
            "Other status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentSummaries_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Other status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentStatusLevels_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            pendingIntent1
        )
            .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_CRITICAL_WARNING,
            pendingIntent1
        )
            .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentPendingIntents_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_OK,
            PendingIntent.getActivity(
                context, 0 /* requestCode= */,
                Intent("Status PendingIntent"), FLAG_IMMUTABLE
            )
        )
            .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_CRITICAL_WARNING,
            PendingIntent.getActivity(
                context, 0 /* requestCode= */,
                Intent("Other status PendingIntent"), FLAG_IMMUTABLE
            )
        )
            .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentIconActions_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_CRITICAL_WARNING,
            pendingIntent1
        )
            .setIconAction(iconAction1)
            .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_CRITICAL_WARNING,
            pendingIntent1
        )
            .setIconAction(iconAction2)
            .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }

    @Test
    fun hashCode_equals_toString_withDifferentEnabled_areNotEqual() {
        val safetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_CRITICAL_WARNING,
            pendingIntent1
        )
            .setEnabled(true)
            .build()
        val otherSafetySourceStatus = SafetySourceStatus.Builder(
            "Status title",
            "Status summary",
            STATUS_LEVEL_CRITICAL_WARNING,
            pendingIntent1
        )
            .setEnabled(false)
            .build()

        assertThat(safetySourceStatus.hashCode()).isNotEqualTo(otherSafetySourceStatus.hashCode())
        assertThat(safetySourceStatus).isNotEqualTo(otherSafetySourceStatus)
        assertThat(safetySourceStatus.toString()).isNotEqualTo(otherSafetySourceStatus.toString())
    }
}