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
import android.safetycenter.SafetySourceError
import android.safetycenter.testers.AnyTester.assertThatRepresentationsAreEqual
import android.safetycenter.testers.AnyTester.assertThatRepresentationsAreNotEqual
import android.safetycenter.testers.ParcelableTester.assertThatRoundTripReturnsOriginal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceError]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class SafetySourceErrorTest {
    @Test
    fun getSafetyEvent_returnsSafetyEvent() {
        val safetySourceError = SafetySourceError(SAFETY_EVENT)

        assertThat(safetySourceError.safetyEvent).isEqualTo(SAFETY_EVENT)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val safetySourceError = SafetySourceError(SAFETY_EVENT)

        assertThatRoundTripReturnsOriginal(safetySourceError, SafetySourceError.CREATOR)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        val safetySourceError = SafetySourceError(SAFETY_EVENT)

        assertThatRepresentationsAreEqual(safetySourceError, safetySourceError)
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val safetySourceError = SafetySourceError(SAFETY_EVENT)
        val equivalentSafetySourceError = SafetySourceError(SAFETY_EVENT)

        assertThatRepresentationsAreEqual(safetySourceError, equivalentSafetySourceError)
    }

    @Test
    fun equals_toString_withDifferentSafetyEvents_areNotEqual() {
        val safetySourceError = SafetySourceError(
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build())
        val otherSafetySourceError = SafetySourceError(
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build())

        assertThatRepresentationsAreNotEqual(safetySourceError, otherSafetySourceError)
    }

    companion object {
        private val SAFETY_EVENT =
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
    }
}