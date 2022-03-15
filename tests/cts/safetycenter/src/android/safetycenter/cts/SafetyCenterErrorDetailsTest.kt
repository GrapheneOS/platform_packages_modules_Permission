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

import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Parcel
import android.safetycenter.SafetyCenterErrorDetails
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterErrorDetailsTest {

    val errorDetails1 = SafetyCenterErrorDetails("an error message")
    val errorDetails2 = SafetyCenterErrorDetails("another error message")

    @Test
    fun getErrorMessage_returnsErrorMessage() {
        assertThat(errorDetails1.errorMessage).isEqualTo("an error message")
        assertThat(errorDetails2.errorMessage).isEqualTo("another error message")
    }

    @Test
    fun describeContents_returns0() {
        assertThat(errorDetails1.describeContents()).isEqualTo(0)
    }

    @Test
    fun createFromParcel_withWriteToParcel_returnsEquivalentObject() {
        val parcel = Parcel.obtain()

        errorDetails1.writeToParcel(parcel, /* flags= */ 0)
        parcel.setDataPosition(0)

        val fromParcel = SafetyCenterErrorDetails.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        assertThat(fromParcel).isEqualTo(errorDetails1)
    }

    @Test
    fun equals_hashCode_toString_equalByReference_areEqual() {
        assertThat(errorDetails1).isEqualTo(errorDetails1)
        assertThat(errorDetails1.hashCode()).isEqualTo(errorDetails1.hashCode())
        assertThat(errorDetails1.toString()).isEqualTo(errorDetails1.toString())
    }

    @Test
    fun equals_hashCode_toString_equalByValue_areEqual() {
        val errorDetails = SafetyCenterErrorDetails("an error message")
        val equivalentErrorDetails = SafetyCenterErrorDetails("an error message")

        assertThat(errorDetails).isEqualTo(equivalentErrorDetails)
        assertThat(errorDetails.hashCode()).isEqualTo(equivalentErrorDetails.hashCode())
        assertThat(errorDetails.toString()).isEqualTo(equivalentErrorDetails.toString())
    }

    @Test
    fun equals_toString_withDifferentErrorMessages_areNotEqual() {
        val errorDetails = SafetyCenterErrorDetails("an error message")
        val differentErrorDetails = SafetyCenterErrorDetails("a different error message")

        assertThat(errorDetails).isNotEqualTo(differentErrorDetails)
        assertThat(errorDetails.toString()).isNotEqualTo(differentErrorDetails.toString())
    }
}