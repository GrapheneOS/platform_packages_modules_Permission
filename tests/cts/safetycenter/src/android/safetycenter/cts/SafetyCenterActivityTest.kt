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

package android.safetycenter.cts

import android.content.Context
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_3
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ID_2
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.RECOMMENDATION_ISSUE_ID
import android.safetycenter.cts.testing.UiTestHelper.assertSourceIssueDisplayed
import android.safetycenter.cts.testing.UiTestHelper.assertSourceIssueNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.assertSourceDataDisplayed
import android.safetycenter.cts.testing.UiTestHelper.expandMoreIssuesCard
import android.safetycenter.cts.testing.UiTestHelper.findAllText
import android.safetycenter.cts.testing.UiTestHelper.findButton
import android.safetycenter.cts.testing.UiTestHelper.waitButtonNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitTextNotDisplayed
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.UiAutomatorUtils.getUiDevice
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterActivityTest {
    private val context: Context = getApplicationContext()

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
        safetyCenterCtsHelper.setEnabled(true)
    }

    @After
    fun clearDataAfterTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.reset()
        getUiDevice().resetRotation()
    }

    @Test
    fun launchActivity_withFlagEnabled_showsSecurityAndPrivacyTitle() {
        context.launchSafetyCenterActivity {
            // CollapsingToolbar title can't be found by text, so using description instead.
            waitFindObject(By.desc("Security & Privacy"))
        }
    }

    @Test
    fun launchActivity_withFlagDisabled_showsSettingsTitle() {
        safetyCenterCtsHelper.setEnabled(false)

        context.launchSafetyCenterActivity { waitFindObject(By.text("Settings")) }
    }

    @Test
    fun launchActivity_displaysStaticSources() {
        safetyCenterCtsHelper.setConfig(STATIC_SOURCES_CONFIG)

        context.launchSafetyCenterActivity {
            findAllText(
                context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_GROUP_1.titleResId),
                context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_1.titleResId),
                context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_1.summaryResId),
                context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_GROUP_2.titleResId),
                context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_2.titleResId),
                context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_2.summaryResId))
        }
    }

    @Test
    fun launchActivity_displaysSafetyData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val dataToDisplay = safetySourceCtsData.criticalWithIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToDisplay)

        context.launchSafetyCenterActivity { assertSourceDataDisplayed(dataToDisplay) }
    }

    @Test
    fun updatingSafetySourceData_updatesDisplayedSafetyData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            val dataToDisplay = safetySourceCtsData.recommendationWithIssue
            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToDisplay)

            assertSourceDataDisplayed(dataToDisplay)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_dismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.desc("Dismiss")).click()
            waitFindObject(By.text("Dismiss this alert?"))
            findButton("Dismiss").click()

            assertSourceIssueNotDisplayed(safetySourceCtsData.criticalIssue)
            findButton("Scan")
        }
    }

    @Test
    fun issueCard_confirmsDismissal_cancels() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.desc("Dismiss")).click()
            waitFindObject(By.text("Dismiss this alert?"))
            findButton("Cancel").click()

            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
        }
    }

    @Test
    fun launchActivity_withNoIssues_hasRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        context.launchSafetyCenterActivity { findButton("Scan") }
    }

    @Test
    fun launchActivity_withIssue_doesNotHaveRescanButton() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity { waitButtonNotDisplayed("Scan") }
    }

    @Test
    fun launchActivity_fromQuickSettings_issuesExpanded() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putBoolean(EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY, true)
        context.launchSafetyCenterActivity(bundle) {
            // Verify cards expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun launchActivity_fromNotification_targetIssueAlreadyFirstIssue() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_1)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.recommendationIssue)
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun launchActivity_fromNotification_targetIssueSamePriorityAsFirstIssue_reorderedFirstIssue() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.criticalWithIssue2)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID_2)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue2)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.criticalIssue)
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun launchActivity_fromNotification_targetLowerPriorityAsFirstIssue_reorderedSecondIssue() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, RECOMMENDATION_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun launchActivity_fromNotification_targetIssueNotFound() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID_2)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.recommendationIssue)
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_underMaxShownIssues_noMoreIssuesCard() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)

        context.launchSafetyCenterActivity {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            waitTextNotDisplayed("See all alerts")
        }
    }

    @Test
    fun moreIssuesCard_moreIssuesCardShown_additionalIssueCardsCollapsed() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.recommendationIssue)
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_expandAdditionalIssueCards() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)

            expandMoreIssuesCard()

            // Verify cards expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_rotation_cardsStillExpanded() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            expandMoreIssuesCard()

            val uiDevice = getUiDevice()
            uiDevice.waitForIdle()

            // Verify cards initially expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)

            // Device rotation to trigger usage of savedinstancestate via config update
            uiDevice.rotate()

            // Verify cards remain expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_twoIssuesAlreadyShown_expandAdditionalIssueCards() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, RECOMMENDATION_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)

            expandMoreIssuesCard()

            // Verify cards expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    companion object {
        private const val EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY = "expand_issue_group_qs_fragment_key"

        private fun UiDevice.rotate() {
            if (isNaturalOrientation) {
                setOrientationLeft()
            } else {
                setOrientationNatural()
            }
            waitForIdle()
        }

        private fun UiDevice.resetRotation() {
            if (!isNaturalOrientation) {
                setOrientationNatural()
            }
            unfreezeRotation()
            waitForIdle()
        }
    }
}
