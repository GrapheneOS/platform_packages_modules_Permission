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
import android.safetycenter.SafetySourceSeverity.LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceSeverity.LEVEL_INFORMATION
import android.safetycenter.SafetySourceSeverity.LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.IconAction
import android.safetycenter.SafetySourceStatus.IconAction.ICON_TYPE_GEAR
import android.safetycenter.SafetySourceStatus.IconAction.ICON_TYPE_INFO
import android.safetycenter.cts.testing.EqualsHashCodeToStringTester
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceStatus]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetySourceStatusTest {
    private val context: Context = getApplicationContext()

    private val pendingIntent1: PendingIntent =
        PendingIntent.getActivity(context, 0, Intent("PendingIntent 1"), FLAG_IMMUTABLE)
    private val iconAction1 = IconAction(ICON_TYPE_INFO, pendingIntent1)
    private val pendingIntent2: PendingIntent =
        PendingIntent.getActivity(context, 0, Intent("PendingIntent 2"), FLAG_IMMUTABLE)
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
    fun iconAction_parcelRoundTrip_recreatesEqual() {
        val iconAction = IconAction(ICON_TYPE_GEAR, pendingIntent1)

        assertThat(iconAction).recreatesEqual(IconAction.CREATOR)
    }

    @Test
    fun iconAction_equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(
                IconAction(ICON_TYPE_GEAR, pendingIntent1),
                IconAction(ICON_TYPE_GEAR, pendingIntent1)
            )
            .addEqualityGroup(IconAction(ICON_TYPE_INFO, pendingIntent1))
            .addEqualityGroup(IconAction(ICON_TYPE_GEAR, pendingIntent2))
            .test()
    }

    @Test
    fun getTitle_returnsTitle() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION).build()

        assertThat(safetySourceStatus.title).isEqualTo("Status title")
    }

    @Test
    fun getSummary_returnsSummary() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION).build()

        assertThat(safetySourceStatus.summary).isEqualTo("Status summary")
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION).build()

        assertThat(safetySourceStatus.severityLevel).isEqualTo(LEVEL_INFORMATION)
    }

    @Test
    fun getPendingIntent_withDefaultBuilder_returnsNull() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION).build()

        assertThat(safetySourceStatus.pendingIntent).isNull()
    }

    @Test
    fun getPendingIntent_whenSetExplicitly_returnsPendingIntent() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION)
                .setPendingIntent(pendingIntent1)
                .build()

        assertThat(safetySourceStatus.pendingIntent).isEqualTo(pendingIntent1)
    }

    @Test
    fun getIconAction_withDefaultBuilder_returnsNull() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION).build()

        assertThat(safetySourceStatus.iconAction).isNull()
    }

    @Test
    fun getIconAction_whenSetExplicitly_returnsIconAction() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION)
                .setIconAction(iconAction1)
                .build()

        assertThat(safetySourceStatus.iconAction).isEqualTo(iconAction1)
    }

    @Test
    fun isEnabled_withDefaultBuilder_returnsTrue() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_UNSPECIFIED).build()

        assertThat(safetySourceStatus.isEnabled).isTrue()
    }

    @Test
    fun isEnabled_whenSetExplicitly_returnsEnabled() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_UNSPECIFIED)
                .setEnabled(false)
                .build()

        assertThat(safetySourceStatus.isEnabled).isFalse()
    }

    @Test
    fun describeContents_returns0() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION)
                .setPendingIntent(pendingIntent1)
                .setIconAction(iconAction1)
                .build()

        assertThat(safetySourceStatus.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        val safetySourceStatus =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION)
                .setPendingIntent(pendingIntent1)
                .setIconAction(iconAction1)
                .setEnabled(true)
                .build()
        val safetySourceStatusWithMinimalFields =
            SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION).build()

        assertThat(safetySourceStatus).recreatesEqual(SafetySourceStatus.CREATOR)
        assertThat(safetySourceStatusWithMinimalFields).recreatesEqual(SafetySourceStatus.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(
                SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION)
                    .setPendingIntent(pendingIntent1)
                    .setIconAction(iconAction1)
                    .setEnabled(true)
                    .build(),
                SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION)
                    .setPendingIntent(pendingIntent1)
                    .setIconAction(iconAction1)
                    .setEnabled(true)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_INFORMATION)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceStatus.Builder("Other status title", "Status summary",
                    LEVEL_INFORMATION)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceStatus.Builder("Status title", "Other status summary",
                    LEVEL_INFORMATION)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_CRITICAL_WARNING)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_CRITICAL_WARNING)
                    .setPendingIntent(pendingIntent2)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_CRITICAL_WARNING)
                    .setIconAction(iconAction2)
                    .build()
            )
            .addEqualityGroup(
                SafetySourceStatus.Builder("Status title", "Status summary", LEVEL_UNSPECIFIED)
                    .setEnabled(false)
                    .build()
            )
            .test()
    }
}
