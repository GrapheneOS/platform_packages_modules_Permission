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
import android.safetycenter.SafetyCenterStaticEntryGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterStaticEntryGroupTest {
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

    private val staticEntry1 =
            SafetyCenterStaticEntry("an entry title", "an entry summary", pendingIntent1)
    private val staticEntry2 =
            SafetyCenterStaticEntry("another entry title", "another entry summary", pendingIntent2)

    private val staticEntryGroup =
            SafetyCenterStaticEntryGroup("a title", listOf(staticEntry1, staticEntry2))

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterStaticEntryGroup("a title", listOf()).title).isEqualTo("a title")
        assertThat(SafetyCenterStaticEntryGroup("another title", listOf()).title)
                .isEqualTo("another title")
    }

    @Test
    fun getStaticEntries_returnsStaticEntries() {
        assertThat(SafetyCenterStaticEntryGroup("", listOf(staticEntry1)).staticEntries)
                .containsExactly(staticEntry1)
        assertThat(
                SafetyCenterStaticEntryGroup("", listOf(staticEntry1, staticEntry2)).staticEntries)
                .containsExactly(staticEntry1, staticEntry2)
        assertThat(SafetyCenterStaticEntryGroup("", listOf()).staticEntries).isEmpty()
    }

    @Test
    fun describeContents_returns0() {
        assertThat(staticEntryGroup.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel: Parcel = Parcel.obtain()

        staticEntryGroup.writeToParcel(parcel, 0 /* flags */)
        parcel.setDataPosition(0)
        val fromParcel = SafetyCenterStaticEntryGroup.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(staticEntryGroup)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(staticEntryGroup).isEqualTo(staticEntryGroup)
        assertThat(staticEntryGroup.hashCode()).isEqualTo(staticEntryGroup.hashCode())
        assertThat(staticEntryGroup.toString()).isEqualTo(staticEntryGroup.toString())
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val group = SafetyCenterStaticEntryGroup("a title", listOf(staticEntry1))
        val equivalentGroup = SafetyCenterStaticEntryGroup("a title", listOf(staticEntry1))

        assertThat(group).isEqualTo(equivalentGroup)
        assertThat(group.hashCode()).isEqualTo(equivalentGroup.hashCode())
        assertThat(group.toString()).isEqualTo(equivalentGroup.toString())
    }

    @Test
    fun equals_toString_withDifferentTitles_areNotEqual() {
        val group = SafetyCenterStaticEntryGroup("a title", listOf(staticEntry1))
        val differentGroup = SafetyCenterStaticEntryGroup("a different title", listOf(staticEntry1))

        assertThat(group).isNotEqualTo(differentGroup)
        assertThat(group.toString()).isNotEqualTo(differentGroup.toString())
    }

    @Test
    fun equals_toString_withDifferentStaticEntries_areNotEqual() {
        val group = SafetyCenterStaticEntryGroup("a title", listOf(staticEntry1))
        val differentGroup = SafetyCenterStaticEntryGroup("a different title", listOf(staticEntry2))

        assertThat(group).isNotEqualTo(differentGroup)
        assertThat(group.toString()).isNotEqualTo(differentGroup.toString())
    }
}