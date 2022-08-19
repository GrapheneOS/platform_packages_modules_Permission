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
import android.os.Bundle
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
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
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.RECOMMENDATION_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey.Reason.RESOLVE_ACTION
import android.safetycenter.cts.testing.SettingsPackage.getSettingsPackageName
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import android.safetycenter.cts.testing.UiTestHelper.STATUS_CARD_RESCAN_BUTTON_LABEL
import android.safetycenter.cts.testing.UiTestHelper.assertSourceDataDisplayed
import android.safetycenter.cts.testing.UiTestHelper.assertSourceIssueDisplayed
import android.safetycenter.cts.testing.UiTestHelper.assertSourceIssueNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.expandMoreIssuesCard
import android.safetycenter.cts.testing.UiTestHelper.findAllText
import android.safetycenter.cts.testing.UiTestHelper.findButton
import android.safetycenter.cts.testing.UiTestHelper.waitNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitTextNotDisplayed
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.UiAutomatorUtils.getUiDevice
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import java.time.Duration
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for the Safety Center Activity. */
@RunWith(AndroidJUnit4::class)
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
        safetyCenterCtsHelper.setup()
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
            waitFindObject(By.desc("Security & privacy"))
        }
    }

    @Test
    fun launchActivity_withFlagDisabled_opensSettings() {
        safetyCenterCtsHelper.setEnabled(false)

        context.launchSafetyCenterActivity {
            waitFindObject(By.pkg(context.getSettingsPackageName()))
        }
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
        val dataToDisplay = safetySourceCtsData.criticalWithResolvingGeneralIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToDisplay)

        context.launchSafetyCenterActivity { assertSourceDataDisplayed(dataToDisplay) }
    }

    @Test
    fun updatingSafetySourceData_updatesDisplayedSafetyData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            val dataToDisplay = safetySourceCtsData.recommendationWithGeneralIssue
            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToDisplay)

            assertSourceDataDisplayed(dataToDisplay)
        }
    }

    @Test
    fun updatingSafetySourceData_withoutSubtitle_newIssueWithSubtitle() {
        val initialDataToDisplay = safetySourceCtsData.informationWithIssue
        val updatedDataToDisplay = safetySourceCtsData.informationWithSubtitleIssue

        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, initialDataToDisplay)

        context.launchSafetyCenterActivity {
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)

            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, updatedDataToDisplay)
            getUiDevice()
                .waitForWindowUpdate(/* from any window*/ null, DATA_UPDATE_TIMEOUT.toMillis())

            assertSourceIssueDisplayed(safetySourceCtsData.informationIssueWithSubtitle)
        }
    }

    @Test
    fun updatingSafetySourceData_withSubtitle_newIssueWithoutSubtitle() {
        val initialDataToDisplay = safetySourceCtsData.informationWithSubtitleIssue
        val updatedDataToDisplay = safetySourceCtsData.informationWithIssue

        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, initialDataToDisplay)

        context.launchSafetyCenterActivity {
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssueWithSubtitle)

            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, updatedDataToDisplay)
            getUiDevice()
                .waitForWindowUpdate(/* from any window*/ null, DATA_UPDATE_TIMEOUT.toMillis())

            waitTextNotDisplayed(
                safetySourceCtsData.informationIssueWithSubtitle.subtitle.toString())
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun statusCard_withoutIssues_hasContentDescriptions() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            waitFindObject(By.descContains("Security and privacy status"))
            findButton("Scan device")
            waitNotDisplayed(By.desc("Protected by Android"))
        }
    }

    @Test
    fun issueCard_criticalIssue_hasContentDescriptions() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.desc("Alert. Critical issue title. Critical issue summary"))
            findButton("Solve issue")
            waitNotDisplayed(By.desc("Protected by Android"))
        }
    }

    @Test
    fun issueCard_informationIssueWithSubtitle_hasContentDescriptions() {
        val sourceIssue = safetySourceCtsData.informationWithSubtitleIssue
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, sourceIssue)
        val expectedString =
            "Alert. Information issue title. Information issue subtitle. Information issue summary"

        context.launchSafetyCenterActivity {
            waitFindObject(By.desc(expectedString))
            findButton("Review")
            waitNotDisplayed(By.desc("Protected by Android"))
        }
    }

    fun issueCard_greenIssue_noDismissalConfirmationAndDismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.desc("Dismiss")).click()

            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
            assertSourceDataDisplayed(safetySourceCtsData.information)
            findButton("Scan device")
        }
    }

    @Test
    fun issueCard_confirmsDismissal_dismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.desc("Dismiss")).click()
            waitFindObject(By.text("Dismiss this alert?"))
            findButton("Dismiss").click()

            assertSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            findButton(STATUS_CARD_RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_afterRotation_dismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.desc("Dismiss")).click()
            waitFindObject(By.text("Dismiss this alert?"))
            waitFindObject(
                By.text(
                    "Review your security and privacy settings any time to add more protection"))

            getUiDevice().rotate()
            getUiDevice()
                .waitForWindowUpdate(/* from any window*/ null, DIALOG_ROTATION_TIMEOUT.toMillis())

            waitFindObject(By.text("Dismiss this alert?"))
            waitFindObject(
                By.text(
                    "Review your security and privacy settings any time to add more protection"))
            findButton("Dismiss").click()

            assertSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            findButton(STATUS_CARD_RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_cancels() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.desc("Dismiss")).click()
            waitFindObject(By.text("Dismiss this alert?"))
            findButton("Cancel").click()

            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_afterRotation_cancels() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitFindObject(By.desc("Dismiss")).click()
            waitFindObject(By.text("Dismiss this alert?"))

            getUiDevice().rotate()
            getUiDevice()
                .waitForWindowUpdate(/* from any window*/ null, DIALOG_ROTATION_TIMEOUT.toMillis())

            waitFindObject(By.text("Dismiss this alert?"))
            findButton("Cancel").click()

            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_resolveIssue_successConfirmationShown_issueDismisses() {
        SafetyCenterFlags.hideResolvedIssueUiTransitionDelay = TIMEOUT_LONG
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        // Set the initial data for the source
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingIssueWithSuccessMessage)

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(RESOLVE_ACTION, SINGLE_SOURCE_ID)] = null

        context.launchSafetyCenterActivity {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                val action = safetySourceCtsData.criticalResolvingActionWithSuccessMessage
                waitFindObject(By.text(action.label.toString())).click()

                // Success message should show up if issue marked as resolved
                val successMessage = action.successMessage.toString()
                waitFindObject(By.text(successMessage))

                // Wait for success message to go away, verify issue no longer displayed
                waitTextNotDisplayed(successMessage)
                assertSourceIssueNotDisplayed(
                    safetySourceCtsData.criticalResolvingIssueWithSuccessMessage)
            }
        }
    }

    @Test
    fun issueCard_resolveIssue_noSuccessMessage_noResolutionUiShown_issueDismisses() {
        SafetyCenterFlags.hideResolvedIssueUiTransitionDelay = TIMEOUT_LONG
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        // Set the initial data for the source
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(RESOLVE_ACTION, SINGLE_SOURCE_ID)] = null

        context.launchSafetyCenterActivity {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                val action = safetySourceCtsData.criticalResolvingAction
                waitFindObject(By.text(action.label.toString())).click()

                // Wait for success message to go away, verify issue no longer displayed
                waitTextNotDisplayed(DEFAULT_SAFETY_CENTER_RESOLVED_ISSUE_SUCCESS_MESSAGE)
                assertSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            }
        }
    }

    @Test
    fun issueCard_resolvingInflightIssueFailed_issueRemains() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        // Set the initial data for the source
        val data = safetySourceCtsData.criticalWithResolvingGeneralIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        // Respond with an error when the action is triggered
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(RESOLVE_ACTION, SINGLE_SOURCE_ID)] = null
        SafetySourceReceiver.shouldReportSafetySourceError = true

        context.launchSafetyCenterActivity {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                val action = safetySourceCtsData.criticalResolvingAction
                waitFindObject(By.text(action.label.toString())).click()

                // criticalResolvingAction does not define a success message, check for default
                waitTextNotDisplayed(DEFAULT_SAFETY_CENTER_RESOLVED_ISSUE_SUCCESS_MESSAGE)
                assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            }
        }
    }

    @Test
    fun issueCard_resolvingInFlightIssueTimesOut_issueRemains() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        // Set the initial data for the source
        val data = safetySourceCtsData.criticalWithResolvingGeneralIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT

        // Set no data at all on the receiver, will ignore incoming call.

        context.launchSafetyCenterActivity {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                val action = safetySourceCtsData.criticalResolvingAction
                waitFindObject(By.text(action.label.toString())).click()

                // criticalResolvingAction does not define a success message, check for default
                waitTextNotDisplayed(DEFAULT_SAFETY_CENTER_RESOLVED_ISSUE_SUCCESS_MESSAGE)
                assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            }
        }
    }

    @Test
    fun launchActivity_fromQuickSettings_issuesExpanded() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putBoolean(EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY, true)
        context.launchSafetyCenterActivity(bundle) {
            // Verify cards expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun launchActivity_fromNotification_targetIssueAlreadyFirstIssue() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_1)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun launchActivity_fromNotification_targetIssueSamePriorityAsFirstIssue_reorderedFirstIssue() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.criticalWithRedirectingIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalRedirectingIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun launchActivity_fromNotification_targetLowerPriorityAsFirstIssue_reorderedSecondIssue() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, RECOMMENDATION_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun launchActivity_fromNotification_targetIssueNotFound() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_underMaxShownIssues_noMoreIssuesCard() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitTextNotDisplayed("See all alerts")
        }
    }

    @Test
    fun moreIssuesCard_moreIssuesCardShown_additionalIssueCardsCollapsed() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_expandAdditionalIssueCards() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)

            expandMoreIssuesCard()

            // Verify cards expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_rotation_cardsStillExpanded() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            expandMoreIssuesCard()

            val uiDevice = getUiDevice()
            uiDevice.waitForIdle()

            // Verify cards initially expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)

            // Device rotation to trigger usage of savedinstancestate via config update
            uiDevice.rotate()

            // Verify cards remain expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_twoIssuesAlreadyShown_expandAdditionalIssueCards() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        val bundle = Bundle()
        bundle.putString(EXTRA_SAFETY_SOURCE_ID, SOURCE_ID_2)
        bundle.putString(EXTRA_SAFETY_SOURCE_ISSUE_ID, RECOMMENDATION_ISSUE_ID)
        context.launchSafetyCenterActivity(bundle) {
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitFindObject(By.text("See all alerts"))
            assertSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)

            expandMoreIssuesCard()

            // Verify cards expanded
            waitTextNotDisplayed("See all alerts")
            assertSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            assertSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    companion object {
        private const val DEFAULT_SAFETY_CENTER_RESOLVED_ISSUE_SUCCESS_MESSAGE = "Action complete"
        private const val EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY = "expand_issue_group_qs_fragment_key"
        private val DIALOG_ROTATION_TIMEOUT = Duration.ofSeconds(1)
        private val DATA_UPDATE_TIMEOUT = Duration.ofSeconds(1)

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
