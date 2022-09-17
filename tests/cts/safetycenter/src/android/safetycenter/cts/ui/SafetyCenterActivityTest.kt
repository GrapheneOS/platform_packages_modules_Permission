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
import android.safetycenter.cts.testing.UiTestHelper.RESCAN_BUTTON_LABEL
import android.safetycenter.cts.testing.UiTestHelper.expandMoreIssuesCard
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitButtonDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitSourceDataDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitSourceIssueDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitSourceIssueNotDisplayed
import android.safetycenter.cts.ui.SafetyCenterActivityTest.Companion.rotate
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.UiAutomatorUtils.getUiDevice
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
            waitDisplayed(By.desc("Security & privacy"))
        }
    }

    @Test
    fun launchActivity_withFlagDisabled_opensSettings() {
        safetyCenterCtsHelper.setEnabled(false)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.pkg(context.getSettingsPackageName()))
        }
    }

    @Test
    fun launchActivity_displaysStaticSources() {
        safetyCenterCtsHelper.setConfig(STATIC_SOURCES_CONFIG)

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
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

        context.launchSafetyCenterActivity { waitSourceDataDisplayed(dataToDisplay) }
    }

    @Test
    fun updatingSafetySourceData_updatesDisplayedSafetyData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            val dataToDisplay = safetySourceCtsData.recommendationWithGeneralIssue
            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToDisplay)

            waitSourceDataDisplayed(dataToDisplay)
        }
    }

    @Test
    fun updatingSafetySourceData_withoutSubtitle_newIssueWithSubtitle() {
        val initialDataToDisplay = safetySourceCtsData.informationWithIssue
        val updatedDataToDisplay = safetySourceCtsData.informationWithSubtitleIssue

        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, initialDataToDisplay)

        context.launchSafetyCenterActivity {
            waitSourceIssueDisplayed(safetySourceCtsData.informationIssue)

            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, updatedDataToDisplay)
            getUiDevice()
                .waitForWindowUpdate(/* from any window*/ null, DATA_UPDATE_TIMEOUT.toMillis())

            waitSourceIssueDisplayed(safetySourceCtsData.informationIssueWithSubtitle)
        }
    }

    @Test
    fun updatingSafetySourceData_withSubtitle_newIssueWithoutSubtitle() {
        val initialDataToDisplay = safetySourceCtsData.informationWithSubtitleIssue
        val updatedDataToDisplay = safetySourceCtsData.informationWithIssue

        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, initialDataToDisplay)

        context.launchSafetyCenterActivity {
            waitSourceIssueDisplayed(safetySourceCtsData.informationIssueWithSubtitle)

            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, updatedDataToDisplay)
            getUiDevice()
                .waitForWindowUpdate(/* from any window*/ null, DATA_UPDATE_TIMEOUT.toMillis())

            waitAllTextNotDisplayed(safetySourceCtsData.informationIssueWithSubtitle.subtitle)
            waitSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun entryListWithEntryGroup_informationState_hasContentDescription() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.information)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.information)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            // Verify content description for the collapsed entry group, and click on it to expand
            waitDisplayed(By.desc("List. OK. OK")) { it.click() }

            // Verify content descriptions for the expanded group header and entry list item
            waitAllTextDisplayed("OK")
            waitDisplayed(By.desc("List item. Ok title. Ok summary"))
        }
    }

    @Test
    fun entryListWithEntryGroup_recommendationState_hasActionsNeededContentDescription() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.information)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            // Verify content description for the collapsed entry group, and click on it to expand
            waitDisplayed(By.desc("List. OK. Actions needed. Recommendation summary")) {
                it.click()
            }

            // Verify content descriptions for the expanded group header and entry list items
            waitAllTextDisplayed("OK")
            waitDisplayed(By.desc("List item. Recommendation title. Recommendation summary"))
            waitDisplayed(By.desc("List item. Ok title. Ok summary"))
        }
    }

    @Test
    fun entryListWithSingleSource_informationState_hasContentDescription() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            // Verify content description for the individual entry
            waitDisplayed(By.desc("Ok title. Ok summary"))
        }
    }

    @Test
    fun issueCard_criticalIssue_hasContentDescriptions() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Alert. Critical issue title. Critical issue summary"))
            waitButtonDisplayed("Solve issue")
            waitNotDisplayed(By.desc("Protected by Android"))

            // Since we already have a combined content description for the issue card, the below
            // tests ensure that we don't make the individual views visible to a11y technologies.
            waitNotDisplayed(By.desc("Critical issue title"))
            waitNotDisplayed(By.desc("Critical issue summary"))
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
            waitDisplayed(By.desc(expectedString))
            waitButtonDisplayed("Review")
            waitNotDisplayed(By.desc("Protected by Android"))

            // Since we already have a combined content description for the issue card, the below
            // tests ensure that we don't make the individual views visible to a11y technologies.
            waitNotDisplayed(By.desc("Information issue title"))
            waitNotDisplayed(By.desc("Information issue subtitle"))
            waitNotDisplayed(By.desc("Information issue summary"))
        }
    }

    @Test
    fun issueCard_greenIssue_noDismissalConfirmationAndDismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Dismiss")) { it.click() }

            waitSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
            waitSourceDataDisplayed(safetySourceCtsData.information)
            waitButtonDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_dismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Dismiss")) { it.click() }
            waitAllTextDisplayed("Dismiss this alert?")
            waitButtonDisplayed("Dismiss") { it.click() }

            waitSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitButtonDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_afterRotation_dismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Dismiss")) { it.click() }
            waitAllTextDisplayed(
                "Dismiss this alert?",
                "Review your security and privacy settings any time to add more protection")

            getUiDevice().rotate()
            getUiDevice()
                .waitForWindowUpdate(/* from any window*/ null, DIALOG_ROTATION_TIMEOUT.toMillis())

            waitAllTextDisplayed(
                "Dismiss this alert?",
                "Review your security and privacy settings any time to add more protection")
            waitButtonDisplayed("Dismiss") { it.click() }

            waitSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitButtonDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_cancels() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Dismiss")) { it.click() }
            waitAllTextDisplayed("Dismiss this alert?")
            waitButtonDisplayed("Cancel") { it.click() }

            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_afterRotation_cancels() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Dismiss")) { it.click() }
            waitAllTextDisplayed("Dismiss this alert?")

            getUiDevice().rotate()
            getUiDevice()
                .waitForWindowUpdate(/* from any window*/ null, DIALOG_ROTATION_TIMEOUT.toMillis())

            waitAllTextDisplayed("Dismiss this alert?")
            waitButtonDisplayed("Cancel") { it.click() }

            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_resolveIssue_successConfirmationShown() {
        SafetyCenterFlags.hideResolvedIssueUiTransitionDelay = TIMEOUT_LONG
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        // Set the initial data for the source
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingIssueWithSuccessMessage)

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(RESOLVE_ACTION, SINGLE_SOURCE_ID)] = null

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                val action = safetySourceCtsData.criticalResolvingActionWithSuccessMessage
                waitButtonDisplayed(action.label) { it.click() }

                // Success message should show up if issue marked as resolved
                val successMessage = action.successMessage
                waitAllTextDisplayed(successMessage)
            }
        }
    }

    @Test
    fun issueCard_resolveIssue_issueDismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        // Set the initial data for the source
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingIssueWithSuccessMessage)

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(RESOLVE_ACTION, SINGLE_SOURCE_ID)] = null

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                val action = safetySourceCtsData.criticalResolvingActionWithSuccessMessage
                waitButtonDisplayed(action.label) { it.click() }

                // Wait for success message to go away, verify issue no longer displayed
                val successMessage = action.successMessage
                waitAllTextNotDisplayed(successMessage)
                waitSourceIssueNotDisplayed(
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

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                val action = safetySourceCtsData.criticalResolvingAction
                waitButtonDisplayed(action.label) { it.click() }

                waitSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
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

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                val action = safetySourceCtsData.criticalResolvingAction
                waitButtonDisplayed(action.label) { it.click() }

                waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
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

        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
            context.launchSafetyCenterActivity {
                val action = safetySourceCtsData.criticalResolvingAction
                waitButtonDisplayed(action.label) { it.click() }

                waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
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
            waitAllTextNotDisplayed("See all alerts")
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.informationIssue)
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
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitAllTextDisplayed("See all alerts")
            waitSourceIssueNotDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
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
            waitSourceIssueDisplayed(safetySourceCtsData.criticalRedirectingIssue)
            waitAllTextDisplayed("See all alerts")
            waitSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
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
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitAllTextDisplayed("See all alerts")
            waitSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
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
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitAllTextDisplayed("See all alerts")
            waitSourceIssueNotDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_underMaxShownIssues_noMoreIssuesCard() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        context.launchSafetyCenterActivity {
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitAllTextNotDisplayed("See all alerts")
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
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitAllTextDisplayed("See all alerts")
            waitSourceIssueNotDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)
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
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)

            expandMoreIssuesCard()

            // Verify cards expanded
            waitAllTextNotDisplayed("See all alerts")
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.informationIssue)
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
            waitAllTextNotDisplayed("See all alerts")
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.informationIssue)

            // Device rotation to trigger usage of savedinstancestate via config update
            uiDevice.rotate()

            // Verify cards remain expanded
            waitAllTextNotDisplayed("See all alerts")
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.informationIssue)
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
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitAllTextDisplayed("See all alerts")
            waitSourceIssueNotDisplayed(safetySourceCtsData.informationIssue)

            expandMoreIssuesCard()

            // Verify cards expanded
            waitAllTextNotDisplayed("See all alerts")
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.recommendationGeneralIssue)
            waitSourceIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    companion object {
        private const val EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY = "expand_issue_group_qs_fragment_key"

        private val DATA_UPDATE_TIMEOUT = Duration.ofSeconds(1)
        private val DIALOG_ROTATION_TIMEOUT = Duration.ofSeconds(1)

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
