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
import android.os.Build
import android.os.Bundle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyCenterStatus
import androidx.core.os.bundleOf
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.os.Parcelables.forceParcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.testing.EqualsHashCodeToStringTester
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withDismissedIssuesIfAtLeastU
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withExtrasIfAtLeastU
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterData]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent =
        PendingIntent.getActivity(context, 0, Intent("Fake Data"), PendingIntent.FLAG_IMMUTABLE)

    private val status1 =
        SafetyCenterStatus.Builder("This is my title", "This is my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()
    private val status2 =
        SafetyCenterStatus.Builder("This is also my title", "This is also my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val issue1 =
        SafetyCenterIssue.Builder("iSsUe_iD_oNe", "An issue title", "An issue summary")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .build()
    private val issue2 =
        SafetyCenterIssue.Builder("iSsUe_iD_tWo", "Another issue title", "Another issue summary")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val entry1 =
        SafetyCenterEntry.Builder("eNtRy_iD_OnE", "An entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
            .build()
    private val entry2 =
        SafetyCenterEntry.Builder("eNtRy_iD_TwO", "Another entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val entryGroup1 =
        SafetyCenterEntryGroup.Builder("eNtRy_gRoUp_iD", "An entry group title")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setEntries(listOf(entry2))
            .build()

    private val entryOrGroup1 = SafetyCenterEntryOrGroup(entry1)
    private val entryOrGroup2 = SafetyCenterEntryOrGroup(entryGroup1)

    private val staticEntry1 =
        SafetyCenterStaticEntry.Builder("A static entry title")
            .setSummary("A static entry summary")
            .setPendingIntent(pendingIntent)
            .build()
    private val staticEntry2 =
        SafetyCenterStaticEntry.Builder("Another static entry title")
            .setSummary("Another static entry summary")
            .setPendingIntent(pendingIntent)
            .build()

    private val staticEntryGroup1 =
        SafetyCenterStaticEntryGroup("A static entry group title", listOf(staticEntry1))
    private val staticEntryGroup2 =
        SafetyCenterStaticEntryGroup("Another static entry group title", listOf(staticEntry2))

    private val filledExtras =
        Bundle().apply {
            putBundle(SOURCE_EXTRA_KEY_1, bundleOf(EXTRA_KEY_1 to EXTRA_VALUE_1))
            putBundle(SOURCE_EXTRA_KEY_2, bundleOf(EXTRA_KEY_2 to EXTRA_VALUE_2))
        }

    private val data1 =
        SafetyCenterData(status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))
    private val data2 =
        SafetyCenterData(status2, listOf(issue2), listOf(entryOrGroup2), listOf(staticEntryGroup2))

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getStatus_withDefaultBuilder_returnsStatus() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.status).isEqualTo(status1)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getIssues_withDefaultBuilder_returnsEmptyList() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.issues).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getIssues_whenSetExplicitly_returnsIssues() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1).addIssue(issue1).addIssue(issue2).build()

        assertThat(safetyCenterData.issues).containsExactly(issue1, issue2).inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getEntriesOrGroups_withDefaultBuilder_returnsEmptyList() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.entriesOrGroups).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getEntriesOrGroups_whenSetExplicitly_returnsEntriesOrGroups() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addEntryOrGroup(entryOrGroup1)
                .addEntryOrGroup(entryOrGroup2)
                .build()

        assertThat(safetyCenterData.entriesOrGroups)
            .containsExactly(entryOrGroup1, entryOrGroup2)
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getStaticGroups_withDefaultBuilder_returnsEmptyList() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.staticEntryGroups).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getStaticEntryGroups_whenSetExplicitly_returnsStaticEntryGroups() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addStaticEntryGroup(staticEntryGroup1)
                .addStaticEntryGroup(staticEntryGroup2)
                .build()

        assertThat(safetyCenterData.staticEntryGroups)
            .containsExactly(staticEntryGroup1, staticEntryGroup2)
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getDismissedIssues_withDefaultBuilder_returnsEmptyList() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.dismissedIssues).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getDismissedIssues_whenSetExplicitly_returnsIssues() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addDismissedIssue(issue1)
                .addDismissedIssue(issue2)
                .build()

        assertThat(safetyCenterData.dismissedIssues).containsExactly(issue1, issue2).inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getExtras_withDefaultBuilder_returnsEmptyBundle() {
        val safetyCenterData = SafetyCenterData.Builder(status1).build()

        assertThat(safetyCenterData.extras.keySet()).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getExtras_whenSetExplicitly_returnsExtras() {
        val safetyCenterData = SafetyCenterData.Builder(status1).setExtras(filledExtras).build()

        assertContainsExtras(safetyCenterData)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getExtras_whenCleared_returnsEmptyBundle() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1).setExtras(filledExtras).clearExtras().build()

        assertThat(safetyCenterData.extras.keySet()).isEmpty()
    }

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
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getDismissedIssues_returnsDismissedIssues() {
        val data3 = data1.withDismissedIssuesIfAtLeastU(listOf(issue2))

        assertThat(data1.dismissedIssues).isEmpty()
        assertThat(data3.dismissedIssues).containsExactly(issue2)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getDismissedIssues_mutationsAreNotAllowed() {
        val mutatedDismissedIssues = data1.dismissedIssues

        assertFailsWith(UnsupportedOperationException::class) { mutatedDismissedIssues.add(issue2) }
    }

    @Test
    fun getIssues_mutationsAreNotAllowed() {
        val mutatedIssues = data1.issues

        assertFailsWith(UnsupportedOperationException::class) { mutatedIssues.add(issue2) }
    }

    @Test
    fun getEntriesOrGroups_returnsEntriesOrGroups() {
        assertThat(data1.entriesOrGroups).containsExactly(entryOrGroup1)
        assertThat(data2.entriesOrGroups).containsExactly(entryOrGroup2)
    }

    @Test
    fun getEntriesOrGroups_mutationsAreNotAllowed() {
        val mutatedEntriesOrGroups = data1.entriesOrGroups

        assertFailsWith(UnsupportedOperationException::class) {
            mutatedEntriesOrGroups.add(entryOrGroup2)
        }
    }

    @Test
    fun getStaticEntryGroups_returnsStaticEntryGroups() {
        assertThat(data1.staticEntryGroups).containsExactly(staticEntryGroup1)
        assertThat(data2.staticEntryGroups).containsExactly(staticEntryGroup2)
    }

    @Test
    fun getStaticEntryGroups_mutationsAreNotAllowed() {
        val mutatedStaticEntryGroups = data1.staticEntryGroups

        assertFailsWith(UnsupportedOperationException::class) {
            mutatedStaticEntryGroups.add(staticEntryGroup2)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun builder_addIssue_doesNotMutatePreviouslyBuiltInstance() {
        val safetyCenterDataBuilder = SafetyCenterData.Builder(status1).addIssue(issue1)
        val issues = safetyCenterDataBuilder.build().issues

        safetyCenterDataBuilder.addIssue(issue2)

        assertThat(issues).containsExactly(issue1)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun builder_clearIssues_removesAllIssues() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addIssue(issue1)
                .addIssue(issue2)
                .clearIssues()
                .build()

        assertThat(safetyCenterData.issues).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun builder_addEntryOrGroup_doesNotMutatePreviouslyBuiltInstance() {
        val safetyCenterDataBuilder =
            SafetyCenterData.Builder(status1).addEntryOrGroup(entryOrGroup1)
        val entriesOrGroups = safetyCenterDataBuilder.build().entriesOrGroups

        safetyCenterDataBuilder.addEntryOrGroup(entryOrGroup2)

        assertThat(entriesOrGroups).containsExactly(entryOrGroup1)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun builder_clearEntriesOrGroups_removesAllEntriesOrGroups() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addEntryOrGroup(entryOrGroup1)
                .addEntryOrGroup(entryOrGroup2)
                .clearEntriesOrGroups()
                .build()

        assertThat(safetyCenterData.entriesOrGroups).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun builder_addStaticEntryGroup_doesNotMutatePreviouslyBuiltInstance() {
        val safetyCenterDataBuilder =
            SafetyCenterData.Builder(status1).addStaticEntryGroup(staticEntryGroup1)
        val staticEntryGroups = safetyCenterDataBuilder.build().staticEntryGroups

        safetyCenterDataBuilder.addStaticEntryGroup(staticEntryGroup2)

        assertThat(staticEntryGroups).containsExactly(staticEntryGroup1)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun builder_clearStaticEntryGroups_removesAllStaticEntryGroups() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addStaticEntryGroup(staticEntryGroup1)
                .addStaticEntryGroup(staticEntryGroup2)
                .clearStaticEntryGroups()
                .build()

        assertThat(safetyCenterData.staticEntryGroups).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun builder_addDismissedIssue_doesNotMutatePreviouslyBuiltInstance() {
        val safetyCenterDataBuilder = SafetyCenterData.Builder(status1).addDismissedIssue(issue1)
        val dismissedIssues = safetyCenterDataBuilder.build().dismissedIssues

        safetyCenterDataBuilder.addDismissedIssue(issue2)

        assertThat(dismissedIssues).containsExactly(issue1)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun builder_clearDismissedIssues_removesAllDismissedIssues() {
        val safetyCenterData =
            SafetyCenterData.Builder(status1)
                .addDismissedIssue(issue1)
                .addDismissedIssue(issue2)
                .clearDismissedIssues()
                .build()

        assertThat(safetyCenterData.dismissedIssues).isEmpty()
    }

    @Test
    fun describeContents_returns0() {
        assertThat(data1.describeContents()).isEqualTo(0)
        assertThat(data2.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(data1).recreatesEqual(SafetyCenterData.CREATOR)
        assertThat(data2).recreatesEqual(SafetyCenterData.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun parcelRoundTrip_withDismissedIssues_recreatesEqual() {
        val data3 = data1.withDismissedIssuesIfAtLeastU(listOf(issue2))
        val data4 = data2.withDismissedIssuesIfAtLeastU(listOf(issue1))

        assertThat(data3).recreatesEqual(SafetyCenterData.CREATOR)
        assertThat(data4).recreatesEqual(SafetyCenterData.CREATOR)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun parcelRoundTrip_withExtras_recreatesEqual() {
        val safetyCenterDataWithExtras = data1.withExtrasIfAtLeastU(filledExtras)
        val safetyCenterDatafromParcel =
            forceParcel(safetyCenterDataWithExtras, SafetyCenterData.CREATOR)

        assertThat(safetyCenterDatafromParcel).isEqualTo(safetyCenterDataWithExtras)
        assertContainsExtras(safetyCenterDatafromParcel)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        val equalsHashCodeToStringTester =
            EqualsHashCodeToStringTester.ofParcelable(parcelableCreator = SafetyCenterData.CREATOR)
                .addEqualityGroup(
                    data1,
                    SafetyCenterData(
                        status1,
                        listOf(issue1),
                        listOf(entryOrGroup1),
                        listOf(staticEntryGroup1)
                    )
                )
                .addEqualityGroup(
                    data2,
                    SafetyCenterData(
                        status2,
                        listOf(issue2),
                        listOf(entryOrGroup2),
                        listOf(staticEntryGroup2)
                    )
                )
                .addEqualityGroup(
                    SafetyCenterData(status1, listOf(), listOf(), listOf()),
                    SafetyCenterData(status1, listOf(), listOf(), listOf())
                )
                .addEqualityGroup(
                    SafetyCenterData(
                        status2,
                        listOf(issue1),
                        listOf(entryOrGroup1),
                        listOf(staticEntryGroup1)
                    )
                )
                .addEqualityGroup(
                    SafetyCenterData(
                        status1,
                        listOf(issue2),
                        listOf(entryOrGroup1),
                        listOf(staticEntryGroup1)
                    )
                )
                .addEqualityGroup(
                    SafetyCenterData(
                        status1,
                        listOf(issue1),
                        listOf(entryOrGroup2),
                        listOf(staticEntryGroup1)
                    )
                )
                .addEqualityGroup(
                    SafetyCenterData(
                        status1,
                        listOf(issue1),
                        listOf(entryOrGroup1),
                        listOf(staticEntryGroup2)
                    )
                )

        equalsHashCodeToStringTester.test()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun equalsHashCode_atLeastU_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester.ofParcelable(
                parcelableCreator = SafetyCenterData.CREATOR,
                ignoreToString = true,
                createCopy = { SafetyCenterData.Builder(it).build() }
            )
            .addEqualityGroup(
                data1,
                SafetyCenterData(
                    status1,
                    listOf(issue1),
                    listOf(entryOrGroup1),
                    listOf(staticEntryGroup1)
                ),
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .build(),
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .setExtras(filledExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue1)
                    .build(),
                SafetyCenterData.Builder(status1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue1)
                    .setExtras(filledExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue2)
                    .build(),
                SafetyCenterData.Builder(status1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue2)
                    .setExtras(filledExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue2)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addIssue(issue1)
                    .setExtras(filledExtras)
                    .build(),
                SafetyCenterData.Builder(status1)
                    .addIssue(issue2)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addIssue(issue1)
                    .setExtras(filledExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue2)
                    .build(),
                SafetyCenterData.Builder(status1)
                    .addIssue(issue1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue2)
                    .setExtras(filledExtras)
                    .build()
            )
            .addEqualityGroup(
                SafetyCenterData.Builder(status1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue1)
                    .addDismissedIssue(issue2)
                    .build(),
                SafetyCenterData.Builder(status1)
                    .addEntryOrGroup(entryOrGroup1)
                    .addStaticEntryGroup(staticEntryGroup1)
                    .addDismissedIssue(issue1)
                    .addDismissedIssue(issue2)
                    .setExtras(filledExtras)
                    .build()
            )
            .test()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun toString_withExtras_containsHasExtras() {
        val safetyCenterDataWithExtras = data1.withExtrasIfAtLeastU(filledExtras)

        val stringRepresentation = safetyCenterDataWithExtras.toString()

        assertThat(stringRepresentation).contains("(has extras)")
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun toString_withoutExtras_doesNotContainHasExtras() {
        val safetyCenterDataWithoutExtras = data1

        val stringRepresentation = safetyCenterDataWithoutExtras.toString()

        assertThat(stringRepresentation).doesNotContain("(has extras)")
    }

    private fun assertContainsExtras(data: SafetyCenterData) {
        assertThat(data.extras.keySet()).containsExactly(SOURCE_EXTRA_KEY_1, SOURCE_EXTRA_KEY_2)
        val sourceExtra1 = data.extras.getBundle(SOURCE_EXTRA_KEY_1)!!
        val sourceExtra2 = data.extras.getBundle(SOURCE_EXTRA_KEY_2)!!
        assertThat(sourceExtra1.keySet()).containsExactly(EXTRA_KEY_1)
        assertThat(sourceExtra1.getString(EXTRA_KEY_1, "")).isEqualTo(EXTRA_VALUE_1)
        assertThat(sourceExtra2.keySet()).containsExactly(EXTRA_KEY_2)
        assertThat(sourceExtra2.getString(EXTRA_KEY_2, "")).isEqualTo(EXTRA_VALUE_2)
    }

    private companion object {
        /** Key of extra data in [Bundle]. */
        const val EXTRA_KEY_1 = "extra_key_1"

        /** Key of extra data in [Bundle]. */
        const val EXTRA_KEY_2 = "extra_key_2"

        /** Value of extra data in [Bundle]. */
        const val EXTRA_VALUE_1 = "extra_value_1"

        /** Value of extra data in [Bundle]. */
        const val EXTRA_VALUE_2 = "extra_value_2"

        /** Key of [SafetySourceData] extra data in combined [Bundle]. */
        const val SOURCE_EXTRA_KEY_1 = "source_extra_key_1"

        /** Key of [SafetySourceData] extra data in combined [Bundle]. */
        const val SOURCE_EXTRA_KEY_2 = "source_extra_key_2"
    }
}
