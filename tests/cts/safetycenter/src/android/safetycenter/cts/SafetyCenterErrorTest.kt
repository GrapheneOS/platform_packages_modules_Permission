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

import android.os.Parcel
import android.safetycenter.SafetyCenterError
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyCenterErrorTest {

    val error1 = SafetyCenterError("an error message")
    val error2 = SafetyCenterError("another error message")

    @Test
    fun getErrorMessage_returnsErrorMessage() {
        assertThat(error1.errorMessage).isEqualTo("an error message")
        assertThat(error2.errorMessage).isEqualTo("another error message")
    }

    @Test
    fun describeContents_returns0() {
        assertThat(error1.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel = Parcel.obtain()

        error1.writeToParcel(parcel, /* flags= */ 0)
        parcel.setDataPosition(0)

        val fromParcel = SafetyCenterError.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(error1)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(error1).isEqualTo(error1)
        assertThat(error1.hashCode()).isEqualTo(error1.hashCode())
        assertThat(error1.toString()).isEqualTo(error1.toString())
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val error = SafetyCenterError("an error message")
        val equivalentError = SafetyCenterError("an error message")

        assertThat(error).isEqualTo(equivalentError)
        assertThat(error.hashCode()).isEqualTo(equivalentError.hashCode())
        assertThat(error.toString()).isEqualTo(equivalentError.toString())
    }

    @Test
    fun equals_toString_withDifferentErrorMessages_areNotEqual() {
        val error = SafetyCenterError("an error message")
        val differentError = SafetyCenterError("a different error message")

        assertThat(error).isNotEqualTo(differentError)
        assertThat(error.toString()).isNotEqualTo(differentError.toString())
    }
}