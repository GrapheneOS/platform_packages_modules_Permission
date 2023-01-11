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
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsData
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Request
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Response
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.UiTestHelper.RESCAN_BUTTON_LABEL
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitButtonDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitButtonNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitNotDisplayed
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.safetycenter.resources.SafetyCenterResourcesContext
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for the Safety Center Status Card. */
@RunWith(AndroidJUnit4::class)
// TODO(b/244582705): Add CTS tests for device & account titles, status when unspecified entries.
class SafetyCenterStatusCardTest {

    @get:Rule val disableAnimationRule = DisableAnimationRule()

    @get:Rule val freezeRotationRule = FreezeRotationRule()

    private val context: Context = getApplicationContext()

    private val safetyCenterResourcesContext = SafetyCenterResourcesContext.forTests(context)
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)
    private val safetyCenterCtsData = SafetyCenterCtsData(context)

    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = context.deviceSupportsSafetyCenter()

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(shouldRunTests)
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
    fun withUnknownStatus_displaysScanningOnLoad() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName("scanning_title"),
                safetyCenterResourcesContext.getStringByName("loading_summary")
            )
        }
    }

    @Test
    fun withKnownStatus_displaysStatusOnLoad() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.informationWithIconAction
        )

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_title"),
                safetyCenterResourcesContext.getStringByName("loading_summary")
            )
        }
    }

    @Test
    fun withUnknownStatusAndNoIssues_hasRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(Request.Refresh(SINGLE_SOURCE_ID), Response.Error)

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_title"
                ),
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_summary"
                )
            )
            waitButtonDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun withInformationAndNoIssues_hasRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information)
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_title"),
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_summary")
            )
            waitButtonDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun withInformationAndNoIssues_hasContentDescriptions() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information)
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            waitDisplayed(By.descContains("Security and privacy status"))
            waitNotDisplayed(By.desc("Protected by Android"))
        }
    }

    @Test
    fun withInformationIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.informationWithIssue)
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_title"),
                safetyCenterCtsData.getAlertString(1)
            )
            waitButtonNotDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun withRecommendationIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.recommendationWithGeneralIssue)
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_safety_recommendation_title"
                ),
                safetyCenterCtsData.getAlertString(1)
            )
            waitButtonNotDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun withCriticalWarningIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.criticalWithResolvingGeneralIssue)
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_safety_warning_title"
                ),
                safetyCenterCtsData.getAlertString(1)
            )
            waitButtonNotDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun withKnownStatus_displaysScanningOnRescan() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information)
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_title"),
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_summary")
            )

            waitButtonDisplayed(RESCAN_BUTTON_LABEL) { it.click() }

            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName("scanning_title"),
                safetyCenterResourcesContext.getStringByName("loading_summary")
            )
        }
    }

    @Test
    fun rescan_updatesDataAfterScanCompletes() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information)
        )
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.recommendationWithGeneralIssue)
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_title"),
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_summary")
            )

            waitButtonDisplayed(RESCAN_BUTTON_LABEL) { it.click() }

            waitAllTextDisplayed(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_safety_recommendation_title"
                ),
                safetyCenterCtsData.getAlertString(1)
            )
        }
    }
}
