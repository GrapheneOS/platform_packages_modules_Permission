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
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCES_GROUP_ID
import android.safetycenter.SafetySourceData
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySourcesGroup
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCES_GROUP_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCE_GROUPS_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_INVALID_INTENT_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_3
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_4
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_5
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Request
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Response
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.UiTestHelper.resetRotation
import android.safetycenter.cts.testing.UiTestHelper.rotate
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextNotDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitButtonDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitNotDisplayed
import androidx.test.uiautomator.By
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.UiAutomatorUtils2
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for generic subpages in Safety Center. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class SafetyCenterSubpagesTest {

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
        SafetyCenterFlags.showSubpages = true
    }

    @After
    fun clearDataAfterTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.reset()
        UiAutomatorUtils2.getUiDevice().resetRotation()
    }

    @Test
    fun launchSafetyCenter_withSubpagesIntentExtra_showsSubpageTitle() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, MULTIPLE_SOURCES_GROUP_ID_1)

        context.launchSafetyCenterActivity(extras) {
            // CollapsingToolbar title can't be found by text, so using description instead.
            waitDisplayed(
                By.desc(
                    context.getString(
                        MULTIPLE_SOURCE_GROUPS_CONFIG.safetySourcesGroups.first()!!.titleResId
                    )
                )
            )
        }
    }

    @Test
    fun launchSafetyCenter_withSubpagesIntentExtraButFlagDisabled_showsHomepageTitle() {
        SafetyCenterFlags.showSubpages = false
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, MULTIPLE_SOURCES_GROUP_ID_1)

        context.launchSafetyCenterActivity(extras) {
            // CollapsingToolbar title can't be found by text, so using description instead.
            waitDisplayed(By.desc("Security & privacy"))
        }
    }

    @Test
    fun launchSafetyCenter_withNonExistingGroupID_displaysNothing() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
        val extras = Bundle()
        extras.putString(EXTRA_SAFETY_SOURCES_GROUP_ID, "non_existing_group_id")

        context.launchSafetyCenterActivity(extras) {
            waitNotDisplayed(
                By.desc(
                    context.getString(
                        MULTIPLE_SOURCE_GROUPS_CONFIG.safetySourcesGroups.first()!!.titleResId
                    )
                )
            )
        }
    }

    @Test
    fun launchSafetyCenter_withMultipleGroups_showsHomepageEntries() {
        val sourceCtsData = safetySourceCtsData.information
        with(safetyCenterCtsHelper) {
            setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
            setData(SOURCE_ID_1, sourceCtsData)
            setData(SOURCE_ID_2, sourceCtsData)
            setData(SOURCE_ID_3, sourceCtsData)
            setData(SOURCE_ID_4, sourceCtsData)
            setData(SOURCE_ID_5, sourceCtsData)
        }
        val firstGroup: SafetySourcesGroup =
            MULTIPLE_SOURCE_GROUPS_CONFIG.safetySourcesGroups.first()
        val lastGroup: SafetySourcesGroup = MULTIPLE_SOURCE_GROUPS_CONFIG.safetySourcesGroups.last()

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(firstGroup.titleResId),
                context.getString(firstGroup.summaryResId),
                context.getString(lastGroup.titleResId),
                context.getString(lastGroup.summaryResId)
            )

            openSubpageAndExit(lastGroup) {
                // Verifying that the subpage is opened with collapsing toolbar title
                waitDisplayed(By.desc(context.getString(lastGroup.titleResId)))
                waitAllTextNotDisplayed(context.getString(lastGroup.summaryResId))
            }
        }
    }

    @Test
    fun launchSafetyCenter_withMultipleGroupsButFlagDisabled_showsExpandAndCollapseEntries() {
        SafetyCenterFlags.showSubpages = false
        val sourceCtsData = safetySourceCtsData.information
        with(safetyCenterCtsHelper) {
            setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
            setData(SOURCE_ID_1, sourceCtsData)
            setData(SOURCE_ID_2, sourceCtsData)
            setData(SOURCE_ID_3, sourceCtsData)
            setData(SOURCE_ID_4, sourceCtsData)
            setData(SOURCE_ID_5, sourceCtsData)
        }
        val firstGroup: SafetySourcesGroup =
            MULTIPLE_SOURCE_GROUPS_CONFIG.safetySourcesGroups.first()
        val lastGroup: SafetySourcesGroup = MULTIPLE_SOURCE_GROUPS_CONFIG.safetySourcesGroups.last()

        context.launchSafetyCenterActivity {
            waitAllTextDisplayed(
                context.getString(firstGroup.titleResId),
                context.getString(firstGroup.summaryResId),
                context.getString(lastGroup.titleResId)
            )
            waitDisplayed(By.text(context.getString(lastGroup.summaryResId))) { it.click() }

            // Verifying that the group is expanded and sources are displayed
            waitAllTextDisplayed(sourceCtsData.status!!.title, sourceCtsData.status!!.summary)
            waitAllTextNotDisplayed(context.getString(lastGroup.summaryResId))
        }
    }

    @Test
    fun launchSafetyCenter_redirectBackFromSubpage_showsHomepageEntries() {
        with(safetyCenterCtsHelper) {
            setConfig(MULTIPLE_SOURCE_GROUPS_CONFIG)
            setData(SOURCE_ID_1, safetySourceCtsData.information)
            setData(SOURCE_ID_2, safetySourceCtsData.information)
        }
        val firstGroup: SafetySourcesGroup =
            MULTIPLE_SOURCE_GROUPS_CONFIG.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            // Verifying that both entry title and summary are displayed on homepage
            waitAllTextDisplayed(
                context.getString(firstGroup.titleResId),
                context.getString(firstGroup.summaryResId)
            )

            openSubpageAndExit(firstGroup) {
                // Verifying that only collapsing toolbar title is displayed for subpage
                waitDisplayed(By.desc(context.getString(firstGroup.titleResId)))
                waitAllTextNotDisplayed(context.getString(firstGroup.summaryResId))
            }

            // Verifying that the homepage is opened again
            waitAllTextDisplayed(
                context.getString(firstGroup.titleResId),
                context.getString(firstGroup.summaryResId)
            )
        }
    }

    @Test
    fun entryListWithMultipleSources_clickingOnHomepageEntry_showsSubpageEntries() {
        with(safetyCenterCtsHelper) {
            setConfig(MULTIPLE_SOURCES_CONFIG)
            setData(
                SOURCE_ID_1,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_1_TITLE,
                    entrySummary = SAFETY_SOURCE_1_SUMMARY
                )
            )
            setData(
                SOURCE_ID_2,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_2_TITLE,
                    entrySummary = SAFETY_SOURCE_2_SUMMARY
                )
            )
            setData(
                SOURCE_ID_3,
                safetySourceCtsData.buildSafetySourceDataWithSummary(
                    severityLevel = SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                    entryTitle = SAFETY_SOURCE_3_TITLE,
                    entrySummary = SAFETY_SOURCE_3_SUMMARY
                )
            )
        }
        val firstGroup: SafetySourcesGroup = MULTIPLE_SOURCES_CONFIG.safetySourcesGroups[0]
        val secondGroup: SafetySourcesGroup = MULTIPLE_SOURCES_CONFIG.safetySourcesGroups[1]

        context.launchSafetyCenterActivity {
            // Verifying that subpage entries of the first group are displayed
            openSubpageAndExit(firstGroup) {
                waitAllTextNotDisplayed(context.getString(firstGroup.summaryResId))
                waitAllTextDisplayed(
                    SAFETY_SOURCE_1_TITLE,
                    SAFETY_SOURCE_1_SUMMARY,
                    SAFETY_SOURCE_2_TITLE,
                    SAFETY_SOURCE_2_SUMMARY
                )
            }

            // Verifying that subpage entries of the second group are displayed
            openSubpageAndExit(secondGroup) {
                waitAllTextNotDisplayed(context.getString(secondGroup.summaryResId))
                waitAllTextDisplayed(SAFETY_SOURCE_3_TITLE, SAFETY_SOURCE_3_SUMMARY)
            }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingOnSubpageEntry_redirectsToDifferentScreen() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val sourcesGroup: SafetySourcesGroup = SINGLE_SOURCE_CONFIG.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()

        context.launchSafetyCenterActivity {
            openSubpageAndExit(sourcesGroup) {
                waitDisplayed(By.text(context.getString(source.titleResId))) { it.click() }
                waitButtonDisplayed("Exit test activity") { it.click() }
                waitAllTextDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )
            }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingTheInfoIcon_redirectsToDifferentScreen() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val sourceCtsData = safetySourceCtsData.informationWithIconAction
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, sourceCtsData)
        val sourcesGroup: SafetySourcesGroup = SINGLE_SOURCE_CONFIG.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            openSubpageAndExit(sourcesGroup) {
                waitDisplayed(By.desc("Information")) { it.click() }
                waitButtonDisplayed("Exit test activity") { it.click() }
                waitAllTextDisplayed(sourceCtsData.status!!.title, sourceCtsData.status!!.summary)
            }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingTheGearIcon_redirectsToDifferentScreen() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val sourceCtsData = safetySourceCtsData.informationWithGearIconAction
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, sourceCtsData)
        val sourcesGroup: SafetySourcesGroup = SINGLE_SOURCE_CONFIG.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            openSubpageAndExit(sourcesGroup) {
                waitDisplayed(By.desc("Settings")) { it.click() }
                waitButtonDisplayed("Exit test activity") { it.click() }
                waitAllTextDisplayed(sourceCtsData.status!!.title, sourceCtsData.status!!.summary)
            }
        }
    }

    @Test
    fun entryListWithSingleSource_clickingSourceWithNullPendingIntent_doesNothing() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_INVALID_INTENT_CONFIG)
        val sourceCtsData = safetySourceCtsData.informationWithNullIntent
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, sourceCtsData)
        val sourcesGroup: SafetySourcesGroup =
            SINGLE_SOURCE_INVALID_INTENT_CONFIG.safetySourcesGroups.first()

        context.launchSafetyCenterActivity {
            openSubpageAndExit(sourcesGroup) {
                waitDisplayed(By.text(sourceCtsData.status!!.title.toString())) { it.click() }

                // Verifying that clicking on the entry doesn't redirect to any other screen
                waitAllTextDisplayed(sourceCtsData.status!!.title, sourceCtsData.status!!.summary)
            }
        }
    }

    @Test
    fun entryListWithSingleSource_updateSafetySourceData_displayedDataIsUpdated() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val sourcesGroup: SafetySourcesGroup = SINGLE_SOURCE_CONFIG.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            openSubpageAndExit(sourcesGroup) {
                waitAllTextDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )
            }

            SafetySourceReceiver.setResponse(
                Request.Refresh(SINGLE_SOURCE_ID),
                Response.SetData(
                    safetySourceCtsData.buildSafetySourceDataWithSummary(
                        severityLevel = SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION,
                        entryTitle = "Updated title",
                        entrySummary = "Updated summary"
                    )
                )
            )

            openSubpageAndExit(sourcesGroup) {
                waitAllTextNotDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )
                waitAllTextDisplayed("Updated title", "Updated summary")
            }
        }
    }

    @Test
    fun entryListWithSingleSource_updateSafetySourceDataAndRotate_displayedDataIsNotUpdated() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val sourcesGroup: SafetySourcesGroup = SINGLE_SOURCE_CONFIG.safetySourcesGroups.first()
        val source: SafetySource = sourcesGroup.safetySources.first()

        context.launchSafetyCenterActivity(withReceiverPermission = true) {
            openSubpageAndExit(sourcesGroup) {
                waitAllTextDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )

                SafetySourceReceiver.setResponse(
                    Request.Refresh(SINGLE_SOURCE_ID),
                    Response.SetData(
                        safetySourceCtsData.buildSafetySourceDataWithSummary(
                            severityLevel = SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION,
                            entryTitle = "Updated title",
                            entrySummary = "Updated summary"
                        )
                    )
                )
                UiAutomatorUtils2.getUiDevice().rotate()

                waitAllTextDisplayed(
                    context.getString(source.titleResId),
                    context.getString(source.summaryResId)
                )
                waitAllTextNotDisplayed("Updated title", "Updated summary")
            }
        }
    }

    private fun openSubpageAndExit(group: SafetySourcesGroup, block: () -> Unit) {
        val uiDevice = UiAutomatorUtils2.getUiDevice()
        uiDevice.waitForIdle()

        // Opens subpage by clicking on the group title
        waitDisplayed(By.text(context.getString(group.titleResId))) { it.click() }
        uiDevice.waitForIdle()

        // Executes the required verifications
        block()
        uiDevice.waitForIdle()

        // Exits subpage by pressing the back button
        uiDevice.pressBack()
        uiDevice.waitForIdle()
    }

    companion object {
        private const val SAFETY_SOURCE_1_TITLE = "Safety Source 1 Title"
        private const val SAFETY_SOURCE_1_SUMMARY = "Safety Source 1 Summary"
        private const val SAFETY_SOURCE_2_TITLE = "Safety Source 2 Title"
        private const val SAFETY_SOURCE_2_SUMMARY = "Safety Source 2 Summary"
        private const val SAFETY_SOURCE_3_TITLE = "Safety Source 3 Title"
        private const val SAFETY_SOURCE_3_SUMMARY = "Safety Source 3 Summary"
    }
}
