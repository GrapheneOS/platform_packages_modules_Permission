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

import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.content.Context
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey.Reason
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import android.safetycenter.cts.testing.UiTestHelper.STATUS_CARD_RESCAN_BUTTON_LABEL
import android.safetycenter.cts.testing.UiTestHelper.findButton
import android.safetycenter.cts.testing.UiTestHelper.waitButtonNotDisplayed
import android.support.test.uiautomator.By
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import com.android.safetycenter.resources.SafetyCenterResourcesContext
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for the Safety Center Status Card. */
@RunWith(AndroidJUnit4::class)
@FlakyTest // TODO(b/244429821): Remove once bug is fixed.
// TODO(b/244582705): Add CTS tests for device & account titles, status when unspecified entries.
class SafetyCenterStatusCardTest {
    private val context: Context = getApplicationContext()

    private val safetyCenterResourcesContext = SafetyCenterResourcesContext.forTests(context)
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)

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
            waitFindObject(By.text(safetyCenterResourcesContext.getStringByName("scanning_title")))
            waitFindObject(By.text(safetyCenterResourcesContext.getStringByName("loading_summary")))
        }
    }

    @Test
    fun withKnownStatus_displaysStatusOnLoad() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIconAction)

        context.launchSafetyCenterActivity {
            waitFindObject(
                By.text(
                    safetyCenterResourcesContext.getStringByName(
                        "overall_severity_level_ok_title")))
            waitFindObject(By.text(safetyCenterResourcesContext.getStringByName("loading_summary")))
        }
    }

    @Test
    fun withUnknownStatusAndNoIssues_hasRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] = null
        SafetySourceReceiver.shouldReportSafetySourceError = true

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_review_title")))
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_review_summary")))
                findButton(STATUS_CARD_RESCAN_BUTTON_LABEL)
            }
        }
    }

    @Test
    fun withInformationAndNoIssues_hasRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_title")))
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_summary")))
                findButton(STATUS_CARD_RESCAN_BUTTON_LABEL)
            }
        }
    }

    @Test
    fun withInformationIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.informationWithIssue

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_title")))
                // TODO(b/244577363): Add test for N alerts string once we have a shared helper for
                // it.
                waitButtonNotDisplayed(STATUS_CARD_RESCAN_BUTTON_LABEL)
            }
        }
    }

    @Test
    fun withRecommendationIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.recommendationWithGeneralIssue

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_safety_recommendation_title")))
                // TODO(b/244577363): Add test for N alerts string once we have a shared helper for
                // it.
                waitButtonNotDisplayed(STATUS_CARD_RESCAN_BUTTON_LABEL)
            }
        }
    }

    @Test
    fun withCriticalWarningIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.criticalWithResolvingGeneralIssue

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_critical_safety_warning_title")))
                // TODO(b/244577363): Add test for N alerts string once we have a shared helper for
                // it.
                waitButtonNotDisplayed(STATUS_CARD_RESCAN_BUTTON_LABEL)
            }
        }
    }

    @Test
    @Ignore // TODO(b/244429821): Enable once bug is fixed.
    fun withKnownStatus_displaysScanningOnRescan() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_title")))
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_summary")))

                findButton(STATUS_CARD_RESCAN_BUTTON_LABEL).click()

                waitFindObject(
                    By.text(safetyCenterResourcesContext.getStringByName("scanning_title")))
                waitFindObject(
                    By.text(safetyCenterResourcesContext.getStringByName("loading_summary")))
            }
        }
    }

    @Test
    @Ignore // TODO(b/244429821): Enable once bug is fixed.
    fun rescan_updatesDataAfterScanCompletes() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.recommendationWithGeneralIssue

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_title")))
                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_summary")))

                findButton(STATUS_CARD_RESCAN_BUTTON_LABEL).click()

                waitFindObject(
                    By.text(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_safety_recommendation_title")))
                // TODO(b/244577363): Add test for N alerts string once we have a shared helper for
                // it.
            }
        }
    }
}
