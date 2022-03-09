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

import android.os.Build
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED
import android.safetycenter.testers.AnyTester.assertThatRepresentationsAreEqual
import android.safetycenter.testers.AnyTester.assertThatRepresentationsAreNotEqual
import android.safetycenter.testers.ParcelableTester.assertThatRoundTripReturnsOriginal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyEvent]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class SafetyEventTest {
    @Test
    fun getSafetyEventType_returnsSafetyEventType() {
        val safetyEvent = SafetyEvent.Builder(SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        assertThat(safetyEvent.safetyEventType).isEqualTo(SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED)
    }

    @Test
    fun getRefreshBroadcastId_returnsRefreshBroadcastId() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                        .setRefreshBroadcastId(REFRESH_BROADCAST_ID)
                        .build()

        assertThat(safetyEvent.refreshBroadcastId).isEqualTo(REFRESH_BROADCAST_ID)
    }

    @Test
    fun getSafetySourceIssueId_returnsSafetySourceIssueId() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                        .build()

        assertThat(safetyEvent.safetySourceIssueId).isEqualTo(SAFETY_SOURCE_ISSUE_ID)
    }

    @Test
    fun getSafetySourceIssueActionId_returnsSafetySourceIssueActionId() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                        .build()

        assertThat(safetyEvent.safetySourceIssueActionId).isEqualTo(SAFETY_SOURCE_ISSUE_ACTION_ID)
    }

    @Test
    fun describeContents_returns0() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                        .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                        .build()

        assertThat(safetyEvent.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsOriginalSafetySourceData() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                        .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                        .build()

        assertThatRoundTripReturnsOriginal(safetyEvent, SafetyEvent.CREATOR)
    }

    // TODO(b/208473675): Use `EqualsTester` for testing `hashcode` and `equals`.
    @Test
    fun hashCode_equals_toString_withEqualByReference_areEqual() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                        .setRefreshBroadcastId(REFRESH_BROADCAST_ID)
                        .build()
        val otherSafetyEvent = safetyEvent

        assertThatRepresentationsAreEqual(safetyEvent, otherSafetyEvent)
    }

    @Test
    fun hashCode_equals_toString_withAllFieldsEqual_areEqual() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                        .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                        .build()
        val otherSafetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                        .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                        .build()

        assertThatRepresentationsAreEqual(safetyEvent, otherSafetyEvent)
    }

    @Test
    fun hashCode_equals_toString_withDifferentSafetyEventTypes_areNotEqual() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                        .build()
        val otherSafetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_DEVICE_REBOOTED)
                        .build()

        assertThatRepresentationsAreNotEqual(safetyEvent, otherSafetyEvent)
    }

    @Test
    fun hashCode_equals_toString_withDifferentRefreshBroadcastIds_areNotEqual() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                        .setRefreshBroadcastId(REFRESH_BROADCAST_ID)
                        .build()
        val otherSafetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                        .setRefreshBroadcastId(OTHER_REFRESH_BROADCAST_ID)
                        .build()

        assertThatRepresentationsAreNotEqual(safetyEvent, otherSafetyEvent)
    }

    @Test
    fun hashCode_equals_toString_withDifferentIssueIds_areNotEqual() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                        .build()
        val otherSafetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueId(OTHER_SAFETY_SOURCE_ISSUE_ID)
                        .build()

        assertThatRepresentationsAreNotEqual(safetyEvent, otherSafetyEvent)
    }

    @Test
    fun hashCode_equals_toString_withDifferentActionIds_areNotEqual() {
        val safetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                        .setSafetySourceIssueActionId(SAFETY_SOURCE_ISSUE_ACTION_ID)
                        .build()
        val otherSafetyEvent =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                        .setSafetySourceIssueId(SAFETY_SOURCE_ISSUE_ID)
                        .setSafetySourceIssueActionId(OTHER_SAFETY_SOURCE_ISSUE_ACTION_ID)
                        .build()

        assertThatRepresentationsAreNotEqual(safetyEvent, otherSafetyEvent)
    }

    companion object {
        const val REFRESH_BROADCAST_ID = "refresh_broadcast_id"
        const val OTHER_REFRESH_BROADCAST_ID = "other_refresh_broadcast_id"
        const val SAFETY_SOURCE_ISSUE_ID = "safety_source_issue_id"
        const val OTHER_SAFETY_SOURCE_ISSUE_ID = "other_safety_source_issue_id"
        const val SAFETY_SOURCE_ISSUE_ACTION_ID = "safety_source_issue_action_id"
        const val OTHER_SAFETY_SOURCE_ISSUE_ACTION_ID = "other_safety_source_issue_action_id"
    }
}