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

package android.safetycenter.cts.config

import android.content.Intent
import android.content.pm.PackageManager.ResolveInfoFlags
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.config.SafetyCenterConfigParser
import com.android.safetycenter.resources.SafetyCenterResourcesContext
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class XmlConfigTest {
    private val safetyCenterContext = SafetyCenterResourcesContext(getApplicationContext())

    @Test
    fun safetyCenterConfigResource_validConfig() {
        // Assert that the parser validates the Safety Center config without throwing any exception
        assertThat(
                SafetyCenterConfigParser.parseXmlResource(
                    safetyCenterContext.safetyCenterConfig!!, safetyCenterContext.resources!!))
            .isNotNull()
    }

    @Test
    fun safetyCenterConfigResource_privacyControlsIntentResolvesIfInConfig() {
        assertThatIntentResolvesIfInConfig(ADVANCED_PRIVACY_INTENT_STRING)
    }

    @Test
    fun safetyCenterConfigResource_advancedPrivacyIntentResolvesIfInConfig() {
        assertThatIntentResolvesIfInConfig(PRIVACY_CONTROLS_INTENT_STRING)
    }

    private fun assertThatIntentResolvesIfInConfig(intentAction: String) {
        if (isIntentInConfig(intentAction)) {
            assertThatIntentResolves(intentAction)
        }
    }

    private fun assertThatIntentResolves(intentAction: String) {
        val pm = safetyCenterContext.packageManager
        assertWithMessage("Intent '%s' cannot be resolved.",
            intentAction)
            .that(
                pm.queryIntentActivities(
                    Intent(intentAction),
                    ResolveInfoFlags.of(0))
            )
            .isNotEmpty()
    }

    private fun isIntentInConfig(intentAction: String): Boolean {
        val safetyCenterConfig =
                SafetyCenterConfigParser.parseXmlResource(
                        safetyCenterContext.safetyCenterConfig!!, safetyCenterContext.resources!!)

        safetyCenterConfig.safetySourcesGroups.forEach { actualSafetySourceGroup ->
            actualSafetySourceGroup.safetySources.forEach {
                try {
                    if (it.intentAction == intentAction) {
                        return true
                    }
                } catch (_: UnsupportedOperationException) {}
            }
        }
        return false
    }

    companion object {
        private const val ADVANCED_PRIVACY_INTENT_STRING = "android.settings.PRIVACY_SETTINGS"
        private const val PRIVACY_CONTROLS_INTENT_STRING = "android.settings.PRIVACY_CONTROLS"
    }
}
