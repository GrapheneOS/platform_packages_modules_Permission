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
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCES_GROUP_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCE_GROUPS_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.UiTestHelper.waitDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitNotDisplayed
import android.support.test.uiautomator.By
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
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
    fun launchSafetyCenter_withSubpagesFlagDisabled_showsHomepageTitle() {
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
}
