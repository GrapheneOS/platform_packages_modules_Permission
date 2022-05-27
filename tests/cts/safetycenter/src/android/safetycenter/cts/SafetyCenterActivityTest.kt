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
import android.os.SystemClock
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
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
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiObject2
import android.util.Log
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObjectOrNull
import java.time.Duration
import java.util.regex.Pattern
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

            assertIssueNotDisplayed(safetySourceCtsData.criticalIssue)
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

            assertIssueDisplayed(safetySourceCtsData.criticalIssue)
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

    // TODO(b/232104227): Add tests for issues dismissible without confirmation and non-dismissible
    // issues if and when the service supports them.

    @Test
    fun moreIssuesCard_underMaxShownIssues_noMoreIssuesCard() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)

        context.launchSafetyCenterActivity {
            assertIssueDisplayed(safetySourceCtsData.criticalIssue)
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
            assertIssueDisplayed(safetySourceCtsData.criticalIssue)
            waitFindObject(By.text("See all alerts"))
            assertIssueNotDisplayed(safetySourceCtsData.recommendationIssue)
            assertIssueNotDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    @Test
    fun moreIssuesCard_expandAdditionalIssueCards() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(SOURCE_ID_3, safetySourceCtsData.informationWithIssue)

        context.launchSafetyCenterActivity {
            assertIssueDisplayed(safetySourceCtsData.criticalIssue)
            waitFindObject(By.text("See all alerts")).click()

            waitTextNotDisplayed("See all alerts")
            assertIssueDisplayed(safetySourceCtsData.recommendationIssue)
            assertIssueDisplayed(safetySourceCtsData.informationIssue)
        }
    }

    private fun assertSourceDataDisplayed(sourceData: SafetySourceData) {
        findAllText(sourceData.status?.title, sourceData.status?.summary)

        for (sourceIssue in sourceData.issues) {
            assertIssueDisplayed(sourceIssue)
        }
    }

    private fun assertIssueDisplayed(sourceIssue: SafetySourceIssue) {
        findAllText(sourceIssue.title, sourceIssue.subtitle, sourceIssue.summary)

        for (action in sourceIssue.actions) {
            findButton(action.label)
        }
    }

    private fun assertIssueNotDisplayed(sourceIssue: SafetySourceIssue) {
        waitTextNotDisplayed(sourceIssue.title.toString())
    }

    private fun findButton(label: CharSequence): UiObject2 {
        return waitFindObject(buttonSelector(label))
    }

    private fun waitButtonNotDisplayed(label: CharSequence) {
        waitNotDisplayed(buttonSelector(label))
    }

    private fun buttonSelector(label: CharSequence): BySelector {
        return By.clickable(true).text(Pattern.compile("$label|${label.toString().uppercase()}"))
    }

    private fun findAllText(vararg textToFind: CharSequence?) {
        for (text in textToFind) if (text != null) waitFindObject(By.text(text.toString()))
    }

    private fun waitTextNotDisplayed(text: String) {
        waitNotDisplayed(By.text(text))
    }

    private fun waitNotDisplayed(selector: BySelector) {
        val startMillis = SystemClock.elapsedRealtime()
        while (true) {
            try {
                if (waitFindObjectOrNull(selector, NOT_DISPLAYED_CHECK_INTERVAL.toMillis()) ==
                    null) {
                    return
                }
            } catch (e: android.support.test.uiautomator.StaleObjectException) {
                Log.d(
                    TAG,
                    "StaleObjectException while calling waitTextNotDisplayed, will retry " +
                        "if within timeout.")
            }
            if (Duration.ofMillis(SystemClock.elapsedRealtime() - startMillis) >=
                NOT_DISPLAYED_TIMEOUT) {
                break
            }
            Thread.sleep(NOT_DISPLAYED_CHECK_INTERVAL.toMillis())
        }
        throw AssertionError(
            "View matching selector $selector is still displayed after waiting for at least" +
                "$NOT_DISPLAYED_TIMEOUT")
    }

    companion object {
        private val TAG = SafetyCenterActivityTest::class.java.simpleName
        private val NOT_DISPLAYED_TIMEOUT = Duration.ofSeconds(20)
        private val NOT_DISPLAYED_CHECK_INTERVAL = Duration.ofMillis(100)
    }
}
