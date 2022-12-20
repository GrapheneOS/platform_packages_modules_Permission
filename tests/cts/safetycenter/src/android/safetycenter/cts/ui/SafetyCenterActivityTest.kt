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
import android.os.Bundle
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ID
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_SOURCE_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_SOURCE_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_SOURCE_3
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_SOURCE_GROUP_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_SOURCE_GROUP_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_SOURCE_GROUP_3
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCES_CONFIG_WITH_SOURCE_WITH_INVALID_INTENT
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCE_GROUPS_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_3
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_4
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_5
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.RECOMMENDATION_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Request
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Response
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.SettingsPackage.getSettingsPackageName
import android.safetycenter.cts.testing.UiTestHelper.RESCAN_BUTTON_LABEL
import android.safetycenter.cts.testing.UiTestHelper.expandMoreIssuesCard
import android.safetycenter.cts.testing.UiTestHelper.setAnimationsEnabled
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitButtonDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitSourceDataDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitSourceIssueDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitSourceIssueNotDisplayed
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.UiAutomatorUtils.getUiDevice
import java.time.Duration
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for the Safety Center Activity. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterActivityTest {

    @get:Rule val disableAnimationRule = DisableAnimationRule()

    @get:Rule val freezeRotationRule = FreezeRotationRule()

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
                context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_2.summaryResId)
            )
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
    fun launchActivity_displaysCollapsedGroupsOfMultipleSource() {
        with(safetyCenterCtsHelper) {
            setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
            setData(
                SOURCE_ID_1,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = false
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(DYNAMIC_SOURCE_GROUP_1.titleResId),
                context.getString(DYNAMIC_SOURCE_GROUP_1.summaryResId),
                context.getString(DYNAMIC_SOURCE_GROUP_3.titleResId),
                context.getString(DYNAMIC_SOURCE_GROUP_3.summaryResId)
            )
            waitAllTextNotDisplayed(
                SAFETY_SOURCE_1_TITLE,
                SAFETY_SOURCE_1_SUMMARY,
                SAFETY_SOURCE_2_TITLE,
                SAFETY_SOURCE_2_SUMMARY,
                SAFETY_SOURCE_4_TITLE,
                SAFETY_SOURCE_4_SUMMARY,
                SAFETY_SOURCE_5_TITLE,
                SAFETY_SOURCE_5_SUMMARY
            )
        }
    }

    @Test
    fun launchActivity_displaysPrioritizedGroupSummary() {
        with(safetyCenterCtsHelper) {
            setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
            setData(
                SOURCE_ID_1,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = true
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = true
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_CRITICAL_WARNING,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = true
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(DYNAMIC_SOURCE_GROUP_1.titleResId),
                SAFETY_SOURCE_2_SUMMARY,
                context.getString(DYNAMIC_SOURCE_GROUP_3.titleResId),
                SAFETY_SOURCE_5_SUMMARY
            )
            waitAllTextNotDisplayed(
                SAFETY_SOURCE_1_TITLE,
                SAFETY_SOURCE_2_TITLE,
                SAFETY_SOURCE_1_SUMMARY,
                SAFETY_SOURCE_4_TITLE,
                SAFETY_SOURCE_5_TITLE,
                SAFETY_SOURCE_4_SUMMARY
            )
        }
    }

    @Test
    fun launchActivity_displaysGroupsOfSingleSourceAsEntity() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(context.getString(DYNAMIC_SOURCE_3.titleResId))
            waitAllTextNotDisplayed(context.getString(DYNAMIC_SOURCE_GROUP_2.titleResId))
        }
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
            SOURCE_ID_1,
            safetySourceCtsData.recommendationWithGeneralIssue
        )
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.information)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            // Verify content description for the collapsed entry group, and click on it to expand.
            waitDisplayed(By.desc("List. OK. Actions needed. Recommendation summary")) {
                it.click()
            }

            // Verify content descriptions for the expanded group header and entry list items.
            waitAllTextDisplayed("OK")
            waitDisplayed(By.desc("List item. Recommendation title. Recommendation summary"))
            waitDisplayed(By.desc("List item. Ok title. Ok summary"))
        }
    }

    @Test
    fun entryListWithEntryGroup_clickingAnUnclickableDisabledEntry_doesNothing() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG_WITH_SOURCE_WITH_INVALID_INTENT)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.unspecified)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("OK")) { it.click() }
            waitDisplayed(By.text("Unspecified title")) { it.click() }
            // Confirm that clicking on the entry neither redirects to any other screen nor
            // collapses the group.
            waitAllTextDisplayed("Unspecified title")
        }
    }

    @Test
    fun entryListWithEntryGroup_unclickableDisabledEntry_hasContentDescription() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG_WITH_SOURCE_WITH_INVALID_INTENT)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.unspecified)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("List. OK. No info yet")) { it.click() }
            // Make sure that the content description is correctly set for the unclickable disabled
            // entries so that the talkback to works properly.
            waitDisplayed(By.desc("List item. Unspecified title. Unspecified summary"))
        }
    }

    @Test
    fun entryListWithEntryGroup_clickingAClickableDisabledEntry_redirectsToDifferentScreen() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1,
            safetySourceCtsData.unspecifiedDisabledWithTestActivityRedirect
        )

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("OK")) { it.click() }
            waitDisplayed(By.text("Clickable disabled title")) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
        }
    }

    @Test
    fun entryListWithEntryGroup_clickableDisabledEntry_hasContentDescription() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1,
            safetySourceCtsData.unspecifiedDisabledWithTestActivityRedirect
        )

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("List. OK. No info yet")) { it.click() }
            // Make sure that the content description is correctly set for the clickable disabled
            // entry so that the talkback to works properly.
            waitDisplayed(
                By.desc("List item. Clickable disabled title. Clickable disabled summary")
            )
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
    fun entryListWithSingleSource_clickingTheEntry_redirectsToDifferentScreen() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text("Ok title")) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
            waitDisplayed(By.text("Ok title"))
        }
    }

    @Test
    fun entryListWithSingleSource_clickingTheIconActionButton_redirectsToDifferentScreen() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.informationWithIconAction
        )

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Information")) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
            waitDisplayed(By.text("Ok title"))
        }
    }

    @Test
    fun staticSource_clickingTheEntry_redirectsToDifferentScreen() {
        safetyCenterCtsHelper.setConfig(STATIC_SOURCES_CONFIG)

        context.launchSafetyCenterActivity {
            waitDisplayed(
                By.text(context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_1.titleResId))
            ) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
            waitAllTextDisplayed(
                context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_1.titleResId)
            )
        }
    }

    @Test
    fun issueCard_criticalIssue_hasContentDescriptions() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )

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
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )

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
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )

        context.launchSafetyCenterActivity {
            waitDisplayed(By.desc("Dismiss")) { it.click() }
            waitAllTextDisplayed(
                "Dismiss this alert?",
                "Review your security and privacy settings any time to add more protection"
            )

            getUiDevice().rotate()
            getUiDevice()
                .waitForWindowUpdate(/* from any window*/ null, DIALOG_ROTATION_TIMEOUT.toMillis())

            waitAllTextDisplayed(
                "Dismiss this alert?",
                "Review your security and privacy settings any time to add more protection"
            )
            waitButtonDisplayed("Dismiss") { it.click() }

            waitSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitButtonDisplayed(RESCAN_BUTTON_LABEL)
        }
    }

    @Test
    fun issueCard_confirmsDismissal_cancels() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )

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
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )

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
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingIssueWithSuccessMessage
        )

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.ClearData
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceCtsData.criticalResolvingActionWithSuccessMessage
            waitButtonDisplayed(action.label) {
                // Re-enable animations for this test as this is needed to show the success message.
                setAnimationsEnabled(true)
                it.click()
            }

            // Success message should show up if issue marked as resolved
            val successMessage = action.successMessage
            waitAllTextDisplayed(successMessage)
        }
    }

    @Test
    fun issueCard_resolveIssue_issueDismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        // Set the initial data for the source
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingIssueWithSuccessMessage
        )

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.ClearData
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceCtsData.criticalResolvingActionWithSuccessMessage
            waitButtonDisplayed(action.label) { it.click() }

            // Wait for success message to go away, verify issue no longer displayed
            val successMessage = action.successMessage
            waitAllTextNotDisplayed(successMessage)
            waitSourceIssueNotDisplayed(
                safetySourceCtsData.criticalResolvingIssueWithSuccessMessage
            )
        }
    }

    @Test
    fun issueCard_resolveIssue_noSuccessMessage_noResolutionUiShown_issueDismisses() {
        SafetyCenterFlags.hideResolvedIssueUiTransitionDelay = TIMEOUT_LONG
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        // Set the initial data for the source
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )

        // Clear the data when action is triggered to simulate resolution.
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.ClearData
        )

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceCtsData.criticalResolvingAction
            waitButtonDisplayed(action.label) {
                // Re-enable animations for this test as this is needed to show the success message.
                setAnimationsEnabled(true)
                it.click()
            }

            waitSourceIssueNotDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_resolvingInflightIssueFailed_issueRemains() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        // Set the initial data for the source
        val data = safetySourceCtsData.criticalWithResolvingGeneralIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        // Respond with an error when the action is triggered
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), Response.Error)

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceCtsData.criticalResolvingAction
            waitButtonDisplayed(action.label) { it.click() }

            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
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

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            val action = safetySourceCtsData.criticalResolvingAction
            waitButtonDisplayed(action.label) { it.click() }

            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
        }
    }

    @Test
    fun issueCard_clickingNonResolvingActionButton_redirectsToDifferentScreen() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val data = safetySourceCtsData.criticalWithTestActivityRedirectIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        context.launchSafetyCenterActivity {
            val action = safetySourceCtsData.testActivityRedirectAction
            waitButtonDisplayed(action.label) { it.click() }
            waitButtonDisplayed("Exit test activity") { it.click() }
        }
    }

    @Test
    fun launchActivity_fromQuickSettings_issuesExpanded() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.recommendationWithGeneralIssue
        )
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
            SOURCE_ID_1,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.recommendationWithGeneralIssue
        )
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
            SOURCE_ID_1,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
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
            SOURCE_ID_1,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.recommendationWithGeneralIssue
        )
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
            SOURCE_ID_1,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.recommendationWithGeneralIssue
        )
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
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )

        context.launchSafetyCenterActivity {
            waitSourceIssueDisplayed(safetySourceCtsData.criticalResolvingGeneralIssue)
            waitAllTextNotDisplayed("See all alerts")
        }
    }

    @Test
    fun moreIssuesCard_moreIssuesCardShown_additionalIssueCardsCollapsed() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.recommendationWithGeneralIssue
        )
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
            SOURCE_ID_1,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.recommendationWithGeneralIssue
        )
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
            SOURCE_ID_1,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.recommendationWithGeneralIssue
        )
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
            SOURCE_ID_1,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.recommendationWithGeneralIssue
        )
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

    @Test
    fun collapsedEntryGroup_expandsWhenClicked() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
        with(safetyCenterCtsHelper) {
            setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
            setData(
                SOURCE_ID_1,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = false
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text(context.getString(DYNAMIC_SOURCE_GROUP_1.titleResId))) {
                it.click()
            }

            waitAllTextNotDisplayed(context.getString(DYNAMIC_SOURCE_GROUP_1.summaryResId))
            waitAllTextDisplayed(
                SAFETY_SOURCE_1_TITLE,
                SAFETY_SOURCE_1_SUMMARY,
                SAFETY_SOURCE_2_TITLE,
                SAFETY_SOURCE_2_SUMMARY
            )
        }
    }

    @Test
    fun expandedEntryGroup_collapsesWhenClicked() {
        with(safetyCenterCtsHelper) {
            setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
            setData(
                SOURCE_ID_1,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = false
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text(context.getString(DYNAMIC_SOURCE_GROUP_1.titleResId))) {
                it.click()
            }

            waitDisplayed(By.text(context.getString(DYNAMIC_SOURCE_GROUP_1.titleResId))) {
                it.click()
            }

            waitAllTextDisplayed(context.getString(DYNAMIC_SOURCE_GROUP_1.titleResId))
            waitAllTextNotDisplayed(SAFETY_SOURCE_1_TITLE, SAFETY_SOURCE_2_TITLE)
        }
    }

    @Test
    fun expandedEntryGroup_rotation_remainsExpanded() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text(context.getString(DYNAMIC_SOURCE_GROUP_1.titleResId))) {
                it.click()
            }

            getUiDevice().rotate()

            waitAllTextNotDisplayed(context.getString(DYNAMIC_SOURCE_GROUP_1.summaryResId))
            waitAllTextDisplayed(
                context.getString(DYNAMIC_SOURCE_1.titleResId),
                context.getString(DYNAMIC_SOURCE_2.titleResId)
            )
        }
    }

    @Test
    fun expandedEntryGroup_otherGroupRemainsCollapsed() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
        with(safetyCenterCtsHelper) {
            setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
            setData(
                SOURCE_ID_1,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_4,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_4_TITLE,
                    entrySummary = SAFETY_SOURCE_4_SUMMARY,
                    withIssue = false
                )
            )
            setData(
                SOURCE_ID_5,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_5_TITLE,
                    entrySummary = SAFETY_SOURCE_5_SUMMARY,
                    withIssue = false
                )
            )
        }

        context.launchSafetyCenterActivity {
            waitDisplayed(By.text(context.getString(DYNAMIC_SOURCE_GROUP_1.titleResId))) {
                it.click()
            }

            waitAllTextNotDisplayed(context.getString(DYNAMIC_SOURCE_GROUP_1.summaryResId))
            waitAllTextDisplayed(context.getString(DYNAMIC_SOURCE_GROUP_3.summaryResId))
        }
    }

    companion object {
        private const val EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY = "expand_issue_group_qs_fragment_key"

        private val DATA_UPDATE_TIMEOUT = Duration.ofSeconds(1)
        private val DIALOG_ROTATION_TIMEOUT = Duration.ofSeconds(1)

        private const val SAFETY_SOURCE_1_TITLE = "Safety Source 1 Title"
        private const val SAFETY_SOURCE_1_SUMMARY = "Safety Source 1 Summary"
        private const val SAFETY_SOURCE_2_TITLE = "Safety Source 2 Title"
        private const val SAFETY_SOURCE_2_SUMMARY = "Safety Source 2 Summary"
        private const val SAFETY_SOURCE_3_TITLE = "Safety Source 3 Title"
        private const val SAFETY_SOURCE_3_SUMMARY = "Safety Source 3 Summary"
        private const val SAFETY_SOURCE_4_TITLE = "Safety Source 4 Title"
        private const val SAFETY_SOURCE_4_SUMMARY = "Safety Source 4 Summary"
        private const val SAFETY_SOURCE_5_TITLE = "Safety Source 5 Title"
        private const val SAFETY_SOURCE_5_SUMMARY = "Safety Source 5 Summary"

        private fun UiDevice.rotate() {
            unfreezeRotation()
            if (isNaturalOrientation) {
                setOrientationLeft()
            } else {
                setOrientationNatural()
            }
            freezeRotation()
            waitForIdle()
        }

        private fun UiDevice.resetRotation() {
            if (!isNaturalOrientation) {
                unfreezeRotation()
                setOrientationNatural()
                freezeRotation()
                waitForIdle()
            }
        }
    }
}
