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

package android.safetycenter.cts.config

import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.cts.testing.EqualsHashCodeToStringTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterConfig]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterConfigTest {

    @Test
    fun getSafetySourcesGroups_returnsSafetySourcesGroups() {
        assertThat(BASE.safetySourcesGroups)
            .containsExactly(SafetySourcesGroupTest.RIGID, SafetySourcesGroupTest.HIDDEN)
            .inOrder()
    }

    @Test
    fun getSafetySourcesGroups_mutationsAreNotAllowed() {
        val sourcesGroups = BASE.safetySourcesGroups

        assertFailsWith(UnsupportedOperationException::class) {
            sourcesGroups.add(SafetySourcesGroupTest.COLLAPSIBLE_WITH_SUMMARY)
        }
    }

    @Test
    fun builder_addSafetySourcesGroup_doesNotMutatePreviouslyBuiltInstance() {
        val safetyCenterConfigBuilder =
            SafetyCenterConfig.Builder()
                .addSafetySourcesGroup(SafetySourcesGroupTest.RIGID)
                .addSafetySourcesGroup(SafetySourcesGroupTest.HIDDEN)
        val sourceGroups = safetyCenterConfigBuilder.build().safetySourcesGroups

        safetyCenterConfigBuilder.addSafetySourcesGroup(
            SafetySourcesGroupTest.COLLAPSIBLE_WITH_SUMMARY)

        assertThat(sourceGroups)
            .containsExactly(SafetySourcesGroupTest.RIGID, SafetySourcesGroupTest.HIDDEN)
            .inOrder()
    }

    @Test
    fun describeContents_returns0() {
        assertThat(BASE.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(BASE).recreatesEqual(SafetyCenterConfig.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(
                BASE,
                SafetyCenterConfig.Builder()
                    .addSafetySourcesGroup(SafetySourcesGroupTest.RIGID)
                    .addSafetySourcesGroup(SafetySourcesGroupTest.HIDDEN)
                    .build())
            .addEqualityGroup(
                SafetyCenterConfig.Builder()
                    .addSafetySourcesGroup(SafetySourcesGroupTest.HIDDEN)
                    .addSafetySourcesGroup(SafetySourcesGroupTest.RIGID)
                    .build())
            .test()
    }

    companion object {
        private val BASE =
            SafetyCenterConfig.Builder()
                .addSafetySourcesGroup(SafetySourcesGroupTest.RIGID)
                .addSafetySourcesGroup(SafetySourcesGroupTest.HIDDEN)
                .build()
    }
}
