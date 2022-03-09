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
import android.safetycenter.SafetyCenterIssue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterIssueTest {
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

    val action1 = SafetyCenterIssue.Action.Builder("action_id_1")
            .setLabel("an action")
            .setPendingIntent(pendingIntent1)
            .setResolving(true)
            .setInFlight(true)
            .setSuccessMessage("a success message")
            .build()
    val action2 = SafetyCenterIssue.Action.Builder("action_id_2")
            .setLabel("another action")
            .setPendingIntent(pendingIntent2)
            .setResolving(false)
            .setInFlight(false)
            .build()

    val issue1 = SafetyCenterIssue.Builder("issue_id")
            .setTitle("Everything's good")
            .setSubtitle("In the neighborhood")
            .setSummary("Please acknowledge this")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .setDismissible(true)
            .setShouldConfirmDismissal(true)
            .setActions(listOf(action1))
            .build()

    val issueWithRequiredFieldsOnly = SafetyCenterIssue.Builder("issue_id")
            .setTitle("Everything's good")
            .setSummary("Please acknowledge this")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .build()

    @Test
    fun getId_returnsId() {
        assertThat(SafetyCenterIssue.Builder(issue1).setId("an id").build().id)
                .isEqualTo("an id")
        assertThat(SafetyCenterIssue.Builder(issue1).setId("another id").build().id)
                .isEqualTo("another id")
    }

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterIssue.Builder(issue1).setTitle("a title").build().title)
                .isEqualTo("a title")
        assertThat(SafetyCenterIssue.Builder(issue1).setTitle("another title").build().title)
                .isEqualTo("another title")
    }

    @Test
    fun getSubtitle_returnsSubtitle() {
        assertThat(SafetyCenterIssue.Builder(issue1).setSubtitle("a subtitle").build().subtitle)
                .isEqualTo("a subtitle")
        assertThat(
                SafetyCenterIssue.Builder(issue1).setSubtitle("another subtitle").build().subtitle)
                .isEqualTo("another subtitle")
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(SafetyCenterIssue.Builder(issue1).setSummary("a summary").build().summary)
                .isEqualTo("a summary")
        assertThat(SafetyCenterIssue.Builder(issue1).setSummary("another summary").build().summary)
                .isEqualTo("another summary")
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(SafetyCenterIssue.Builder(issue1)
                .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
                .build()
                .severityLevel)
                .isEqualTo(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
        assertThat(SafetyCenterIssue.Builder(issue1)
                .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
                .build()
                .severityLevel)
                .isEqualTo(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun isDismissible_returnsIsDismissible() {
        assertThat(SafetyCenterIssue.Builder(issue1).setDismissible(true).build().isDismissible)
                .isTrue()
        assertThat(SafetyCenterIssue.Builder(issue1).setDismissible(false).build().isDismissible)
                .isFalse()
    }

    @Test
    fun isDismissible_defaultsToTrue() {
        assertThat(issueWithRequiredFieldsOnly.isDismissible).isTrue()
    }

    @Test
    fun shouldConfirmDismissal_returnsShouldConfirmDismissal() {
        assertThat(SafetyCenterIssue.Builder(issue1)
                .setShouldConfirmDismissal(true)
                .build()
                .shouldConfirmDismissal())
                .isTrue()
        assertThat(SafetyCenterIssue.Builder(issue1)
                .setShouldConfirmDismissal(false)
                .build()
                .shouldConfirmDismissal())
                .isFalse()
    }

    @Test
    fun shouldConfirmDismissal_defaultsToTrue() {
        assertThat(issueWithRequiredFieldsOnly.shouldConfirmDismissal()).isTrue()
    }

    @Test
    fun getActions_returnsActions() {
        assertThat(SafetyCenterIssue.Builder(issue1)
                .setActions(listOf(action1, action2))
                .build().actions)
                .containsExactly(action1, action2)
        assertThat(SafetyCenterIssue.Builder(issue1).setActions(listOf(action2)).build().actions)
                .containsExactly(action2)
        assertThat(SafetyCenterIssue.Builder(issue1).setActions(listOf()).build().actions)
                .isEmpty()
    }

    @Test
    fun getActions_mutationsAreNotReflected() {
        val mutatedActions = issue1.actions
        mutatedActions.add(action2)

        assertThat(mutatedActions).containsExactly(action1, action2)
        assertThat(issue1.actions).doesNotContain(action2)
    }

    @Test
    fun describeContents_returns0() {
        assertThat(issue1.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel = Parcel.obtain()

        issue1.writeToParcel(parcel, /* flags= */ 0)
        parcel.setDataPosition(0)

        val fromParcel = SafetyCenterIssue.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(issue1)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(issue1).isEqualTo(issue1)
        assertThat(issue1.hashCode()).isEqualTo(issue1.hashCode())
        assertThat(issue1.toString()).isEqualTo(issue1.toString())
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val issue = SafetyCenterIssue.Builder("an id")
                .setTitle("a title")
                .setSubtitle("In the neighborhood")
                .setSummary("Please acknowledge this")
                .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
                .setActions(listOf(action1))
                .build()
        val equivalentIssue = SafetyCenterIssue.Builder("an id")
                .setTitle("a title")
                .setSubtitle("In the neighborhood")
                .setSummary("Please acknowledge this")
                .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
                .setActions(listOf(action1))
                .build()

        assertThat(issue).isEqualTo(equivalentIssue)
        assertThat(issue.hashCode()).isEqualTo(equivalentIssue.hashCode())
        assertThat(issue.toString()).isEqualTo(equivalentIssue.toString())
    }

    @Test
    fun equals_hashCode_toString_fromCopyBuilder() {
        val copyOfIssue1 = SafetyCenterIssue.Builder(issue1).build()

        assertThat(copyOfIssue1).isEqualTo(issue1)
        assertThat(copyOfIssue1.hashCode()).isEqualTo(issue1.hashCode())
        assertThat(copyOfIssue1.toString()).isEqualTo(issue1.toString())
    }

    @Test
    fun equals_toString_differentIds_areNotEqual() {
        val differentFromIssue1 = SafetyCenterIssue.Builder(issue1)
                .setId("a different id")
                .build()

        assertThat(differentFromIssue1).isNotEqualTo(issue1)
        assertThat(differentFromIssue1.toString()).isNotEqualTo(issue1.toString())
    }

    @Test
    fun equals_toString_differentTitles_areNotEqual() {
        val differentFromIssue1 = SafetyCenterIssue.Builder(issue1)
                .setTitle("a different title")
                .build()

        assertThat(differentFromIssue1).isNotEqualTo(issue1)
        assertThat(differentFromIssue1.toString()).isNotEqualTo(issue1.toString())
    }

    @Test
    fun equals_toString_differentSubtitles_areNotEqual() {
        val differentFromIssue1 = SafetyCenterIssue.Builder(issue1)
                .setSubtitle("a different subtitle")
                .build()

        assertThat(differentFromIssue1).isNotEqualTo(issue1)
        assertThat(differentFromIssue1.toString()).isNotEqualTo(issue1.toString())
    }

    @Test
    fun equals_toString_differentSummaries_areNotEqual() {
        val differentFromIssue1 = SafetyCenterIssue.Builder(issue1)
                .setSummary("a different summary")
                .build()

        assertThat(differentFromIssue1).isNotEqualTo(issue1)
        assertThat(differentFromIssue1.toString()).isNotEqualTo(issue1.toString())
    }

    @Test
    fun equals_toString_differentSeverityLevels_areNotEqual() {
        val differentFromIssue1 = SafetyCenterIssue.Builder(issue1)
                .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
                .build()

        assertThat(differentFromIssue1).isNotEqualTo(issue1)
        assertThat(differentFromIssue1.toString()).isNotEqualTo(issue1.toString())
    }

    @Test
    fun equals_toString_differentIsDismissibleValues_areNotEqual() {
        val differentFromIssue1 = SafetyCenterIssue.Builder(issue1)
                .setDismissible(false)
                .build()

        assertThat(differentFromIssue1).isNotEqualTo(issue1)
        assertThat(differentFromIssue1.toString()).isNotEqualTo(issue1.toString())
    }

    @Test
    fun equals_toString_differentShouldConfirmDismissalValues_areNotEqual() {
        val differentFromIssue1 = SafetyCenterIssue.Builder(issue1)
                .setShouldConfirmDismissal(false)
                .build()

        assertThat(differentFromIssue1).isNotEqualTo(issue1)
        assertThat(differentFromIssue1.toString()).isNotEqualTo(issue1.toString())
    }

    @Test
    fun equals_toString_differentActions_areNotEqual() {
        val differentFromIssue1 = SafetyCenterIssue.Builder(issue1)
                .setActions(listOf(action2))
                .build()

        assertThat(differentFromIssue1).isNotEqualTo(issue1)
        assertThat(differentFromIssue1.toString()).isNotEqualTo(issue1.toString())
    }

    @Test
    fun action_getId_returnsId() {
        assertThat(action1.id).isEqualTo("action_id_1")
        assertThat(action2.id).isEqualTo("action_id_2")
    }

    @Test
    fun action_getLabel_returnsLabel() {
        assertThat(action1.label).isEqualTo("an action")
        assertThat(action2.label).isEqualTo("another action")
    }

    @Test
    fun action_getPendingIntent_returnsPendingIntent() {
        assertThat(action1.pendingIntent).isEqualTo(pendingIntent1)
        assertThat(action2.pendingIntent).isEqualTo(pendingIntent2)
    }

    @Test
    fun action_isResolving_returnsIsResolving() {
        assertThat(action1.isResolving).isTrue()
        assertThat(action2.isResolving).isFalse()
    }

    @Test
    fun action_isInFlight_returnsIsInFlight() {
        assertThat(action1.isInFlight).isTrue()
        assertThat(action2.isInFlight).isFalse()
    }

    @Test
    fun action_getSuccessMessage_returnsSuccessMessage() {
        assertThat(action1.successMessage).isEqualTo("a success message")
        assertThat(action2.successMessage).isNull()
    }

    @Test
    fun action_describeContents_returns0() {
        assertThat(action1.describeContents()).isEqualTo(0)
    }

    @Test
    fun action_createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel = Parcel.obtain()

        action1.writeToParcel(parcel, /* flags= */ 0)

        parcel.setDataPosition(0)
        val fromParcel = SafetyCenterIssue.Action.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(action1)
    }

    @Test
    fun action_equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(action1).isEqualTo(action1)
        assertThat(action1.hashCode()).isEqualTo(action1.hashCode())
        assertThat(action1.toString()).isEqualTo(action1.toString())
    }

    @Test
    fun action_equals_hashCode_toString_equalByValue_areEqual() {
        val action = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setResolving(true)
                .setInFlight(true)
                .setSuccessMessage("a success message")
                .build()
        val equivalentAction = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setResolving(true)
                .setInFlight(true)
                .setSuccessMessage("a success message")
                .build()

        assertThat(action).isEqualTo(equivalentAction)
        assertThat(action.toString()).isEqualTo(equivalentAction.toString())
    }

    @Test
    fun action_equals_toString_differentIds_areNotEqual() {
        val action = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setSuccessMessage("a success message")
                .build()
        val differentAction = SafetyCenterIssue.Action.Builder("a_different_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setSuccessMessage("a success message")
                .build()

        assertThat(action).isNotEqualTo(differentAction)
        assertThat(action.toString()).isNotEqualTo(differentAction.toString())
    }

    @Test
    fun action_equals_toString_differentLabels_areNotEqual() {
        val action = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setSuccessMessage("a success message")
                .build()
        val differentAction = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a different label")
                .setPendingIntent(pendingIntent1)
                .setSuccessMessage("a success message")
                .build()

        assertThat(action).isNotEqualTo(differentAction)
        assertThat(action.toString()).isNotEqualTo(differentAction.toString())
    }

    @Test
    fun action_equals_toString_differentPendingIntents_areNotEqual() {
        val action = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setSuccessMessage("a success message")
                .build()
        val differentAction = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent2)
                .setSuccessMessage("a success message")
                .build()

        assertThat(action).isNotEqualTo(differentAction)
        assertThat(action.toString()).isNotEqualTo(differentAction.toString())
    }

    @Test
    fun action_equals_toString_differentResovlingValues_areNotEqual() {
        val action = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setResolving(true)
                .setSuccessMessage("a success message")
                .build()
        val differentAction = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setResolving(false)
                .setSuccessMessage("a success message")
                .build()

        assertThat(action).isNotEqualTo(differentAction)
        assertThat(action.toString()).isNotEqualTo(differentAction.toString())
    }

    @Test
    fun action_equals_toString_differentInFlightValues_areNotEqual() {
        val action = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setResolving(true)
                .setInFlight(true)
                .setSuccessMessage("a success message")
                .build()
        val differentAction = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setResolving(true)
                .setInFlight(false)
                .setSuccessMessage("a success message")
                .build()

        assertThat(action).isNotEqualTo(differentAction)
        assertThat(action.toString()).isNotEqualTo(differentAction.toString())
    }

    @Test
    fun action_equals_toString_differentSuccessMessages_areNotEqual() {
        val action = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setSuccessMessage("a success message")
                .build()
        val differentAction = SafetyCenterIssue.Action.Builder("an_id")
                .setLabel("a label")
                .setPendingIntent(pendingIntent1)
                .setSuccessMessage("a different success message")
                .build()

        assertThat(action).isNotEqualTo(differentAction)
        assertThat(action.toString()).isNotEqualTo(differentAction.toString())
    }
}