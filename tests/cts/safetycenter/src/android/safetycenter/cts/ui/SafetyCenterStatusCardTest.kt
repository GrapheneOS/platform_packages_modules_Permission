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
package android.safetycenter.cts.ui

import android.content.Context
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterCtsHelper.Companion.STATUS_CARD_RESCAN_BUTTON_LABEL
import android.safetycenter.cts.testing.SafetyCenterCtsHelper.Companion.STATUS_CARD_TITLE_CRITICAL_WARNING_REGEX
import android.safetycenter.cts.testing.SafetyCenterCtsHelper.Companion.STATUS_CARD_TITLE_INFO
import android.safetycenter.cts.testing.SafetyCenterCtsHelper.Companion.STATUS_CARD_TITLE_RECOMMENDATION_REGEX
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.UiTestHelper
import android.support.test.uiautomator.By
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for the Safety Center Status Card.  */
@RunWith(AndroidJUnit4::class)
class SafetyCenterStatusCardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)

    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = context.deviceSupportsSafetyCenter()

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        Assume.assumeTrue(shouldRunTests)
    }

    @Before
    fun enableSafetyCenterBeforeTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.setup()
    }

    @After
    fun clearDataAfterTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.reset()
    }

    @Test
    fun statusCard_displaysStatusOnLoad() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID,
            safetySourceCtsData.informationWithIconAction)
        context.launchSafetyCenterActivity {
            waitFindObject(By.text(STATUS_CARD_TITLE_INFO))
        }
    }

    @Test
    fun statusCard_withNoIssues_hasRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            UiTestHelper.findButton(STATUS_CARD_RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun statusCard_withInformationIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.text(STATUS_CARD_TITLE_INFO))
            UiTestHelper.waitButtonNotDisplayed(STATUS_CARD_RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun statusCard_withRecommendationIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData
            .recommendationWithGeneralIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.text(STATUS_CARD_TITLE_RECOMMENDATION_REGEX))
            UiTestHelper.waitButtonNotDisplayed(STATUS_CARD_RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun statusCard_withCriticalWarningIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.text(STATUS_CARD_TITLE_CRITICAL_WARNING_REGEX))
            UiTestHelper.waitButtonNotDisplayed(STATUS_CARD_RESCAN_BUTTON_LABEL)
        }
    }
}
