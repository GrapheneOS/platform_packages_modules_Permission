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
import android.safetycenter.config.SafetySourcesGroup
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCES_GROUP_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCE_GROUPS_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_3
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_4
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_5
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitNotDisplayed
import android.support.test.uiautomator.By
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.UiAutomatorUtils
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
                        MULTIPLE_SOURCE_GROUPS_CONFIG.safetySourcesGroups.first()!!.titleResId)))
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
                        MULTIPLE_SOURCE_GROUPS_CONFIG.safetySourcesGroups.first()!!.titleResId)))
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
            waitDisplayed(By.text(context.getString(firstGroup.titleResId)))
            waitDisplayed(By.text(context.getString(firstGroup.summaryResId)))
            waitDisplayed(By.text(context.getString(lastGroup.titleResId)))
            waitDisplayed(By.text(context.getString(lastGroup.summaryResId))) { it.click() }

            // Verifying that the subpage is opened with collapsing toolbar title
            waitDisplayed(By.desc(context.getString(lastGroup.titleResId)))
            waitNotDisplayed(By.text(context.getString(lastGroup.summaryResId)))
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
            waitDisplayed(By.text(context.getString(firstGroup.titleResId)))
            waitDisplayed(By.text(context.getString(firstGroup.summaryResId)))
            waitDisplayed(By.text(context.getString(lastGroup.titleResId)))
            waitDisplayed(By.text(context.getString(lastGroup.summaryResId))) { it.click() }

            // Verifying that the group is expanded and sources are displayed
            waitAllTextDisplayed(sourceCtsData.status!!.title, sourceCtsData.status!!.summary)
            waitNotDisplayed(By.text(context.getString(lastGroup.summaryResId)))
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
            waitDisplayed(By.text(context.getString(firstGroup.titleResId)))
            waitDisplayed(By.text(context.getString(firstGroup.summaryResId))) { it.click() }

            // Verifying that only collapsing toolbar title is displayed for subpage
            waitDisplayed(By.desc(context.getString(firstGroup.titleResId)))
            waitNotDisplayed(By.text(context.getString(firstGroup.summaryResId)))

            // Verifying that clicking on the back button opens homepage again
            UiAutomatorUtils.getUiDevice().pressBack()
            waitDisplayed(By.text(context.getString(firstGroup.titleResId)))
            waitDisplayed(By.text(context.getString(firstGroup.summaryResId)))
        }
    }
}
