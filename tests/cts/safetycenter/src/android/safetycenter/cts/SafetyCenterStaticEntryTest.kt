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
import android.safetycenter.SafetyCenterStaticEntry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterStaticEntryTest {
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

    private val title1 = "a title"
    private val title2 = "another title"

    private val summary1 = "a summary"
    private val summary2 = "another summary"

    private val staticEntry1 = SafetyCenterStaticEntry(title1, summary1, pendingIntent1)
    private val staticEntry2 = SafetyCenterStaticEntry(title2, summary2, pendingIntent2)

    @Test
    fun getTitle_returnsTitle() {
        assertThat(staticEntry1.title).isEqualTo(title1)
        assertThat(staticEntry2.title).isEqualTo(title2)
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(staticEntry1.summary).isEqualTo(summary1)
        assertThat(staticEntry2.summary).isEqualTo(summary2)
        assertThat(SafetyCenterStaticEntry("", null, pendingIntent1).summary).isNull()
    }

    @Test
    fun getPendingIntent_returnsPendingIntent() {
        assertThat(staticEntry1.pendingIntent).isEqualTo(pendingIntent1)
        assertThat(staticEntry2.pendingIntent).isEqualTo(pendingIntent2)
    }

    @Test
    fun describeContents_returns0() {
        assertThat(staticEntry1.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel: Parcel = Parcel.obtain()

        staticEntry1.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val fromParcel = SafetyCenterStaticEntry.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(staticEntry1)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(staticEntry1).isEqualTo(staticEntry1)
        assertThat(staticEntry1.hashCode()).isEqualTo(staticEntry1.hashCode())
        assertThat(staticEntry1.toString()).isEqualTo(staticEntry1.toString())
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val staticEntry = SafetyCenterStaticEntry("titlee", "sumaree", pendingIntent1)
        val equivalentStaticEntry = SafetyCenterStaticEntry("titlee", "sumaree", pendingIntent1)

        assertThat(staticEntry).isEqualTo(equivalentStaticEntry)
        assertThat(staticEntry.hashCode()).isEqualTo(equivalentStaticEntry.hashCode())
        assertThat(staticEntry.toString()).isEqualTo(equivalentStaticEntry.toString())
    }

    @Test
    fun equals_toString_withDifferentTitles_areNotEqual() {
        val staticEntry = SafetyCenterStaticEntry("a title", "a summary", pendingIntent1)
        val differentStaticEntry =
                SafetyCenterStaticEntry("a different title", "a summary", pendingIntent1)

        assertThat(staticEntry).isNotEqualTo(differentStaticEntry)
        assertThat(staticEntry.toString()).isNotEqualTo(differentStaticEntry.toString())
    }

    @Test
    fun equals_toString_withDifferentSummaries_areNotEqual() {
        val staticEntry = SafetyCenterStaticEntry("a title", "a summary", pendingIntent1)
        val differentStaticEntry =
                SafetyCenterStaticEntry("a title", "a different summary", pendingIntent1)

        assertThat(staticEntry).isNotEqualTo(differentStaticEntry)
        assertThat(staticEntry.toString()).isNotEqualTo(differentStaticEntry.toString())
    }

    @Test
    fun equals_toString_withDifferentPendingIntents_areNotEqual() {
        val staticEntry = SafetyCenterStaticEntry("a title", "a summary", pendingIntent1)
        val differentStaticEntry = SafetyCenterStaticEntry("a title", "a summary", pendingIntent2)

        assertThat(staticEntry).isNotEqualTo(differentStaticEntry)
        assertThat(staticEntry.toString()).isNotEqualTo(differentStaticEntry.toString())
    }
}