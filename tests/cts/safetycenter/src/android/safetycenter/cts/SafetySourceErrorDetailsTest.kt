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
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.testing.AnyTester.assertThatRepresentationsAreEqual
import android.safetycenter.testing.AnyTester.assertThatRepresentationsAreNotEqual
import android.safetycenter.testing.ParcelableTester.assertThatRoundTripReturnsOriginal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceErrorDetails]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class SafetySourceErrorDetailsTest {
    @Test
    fun getSafetyEvent_returnsSafetyEvent() {
        val errorDetails = SafetySourceErrorDetails(SAFETY_EVENT)

        assertThat(errorDetails.safetyEvent).isEqualTo(SAFETY_EVENT)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val errorDetails = SafetySourceErrorDetails(SAFETY_EVENT)

        assertThatRoundTripReturnsOriginal(errorDetails, SafetySourceErrorDetails.CREATOR)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        val errorDetails = SafetySourceErrorDetails(SAFETY_EVENT)

        assertThatRepresentationsAreEqual(errorDetails, errorDetails)
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val errorDetails = SafetySourceErrorDetails(SAFETY_EVENT)
        val equivalentSafetySourceErrorDetails = SafetySourceErrorDetails(SAFETY_EVENT)

        assertThatRepresentationsAreEqual(errorDetails, equivalentSafetySourceErrorDetails)
    }

    @Test
    fun equals_toString_withDifferentSafetyEvents_areNotEqual() {
        val errorDetails = SafetySourceErrorDetails(
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build())
        val otherErrorDetails = SafetySourceErrorDetails(
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build())

        assertThatRepresentationsAreNotEqual(errorDetails, otherErrorDetails)
    }

    companion object {
        private val SAFETY_EVENT =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
    }
}