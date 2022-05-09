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

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.SystemClock
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceStatus
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SimpleTestSource
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiObject2
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

    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    private val simpleTestSource = SimpleTestSource(safetyCenterManager)
    private val somePendingIntent =
        PendingIntent.getActivity(
            context, 0 /* requestCode */, Intent(ACTION_SAFETY_CENTER), FLAG_IMMUTABLE)

    private val criticalIssue =
        SafetySourceIssue.Builder(
                "critical_issue_id",
                "Critical issue title",
                "Critical issue summary",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                "issue_type_id")
            .addAction(
                SafetySourceIssue.Action.Builder(
                        "critical_action_id", "Solve issue", somePendingIntent)
                    .build())
            .build()
    private val safetySourceDataCritical =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title", "Critical summary", SEVERITY_LEVEL_CRITICAL_WARNING)
                    .setPendingIntent(somePendingIntent)
                    .build())
            .addIssue(criticalIssue)
            .build()
    private val safetySourceDataRecommendation =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Recommendation title",
                        "Recommendation summary",
                        SEVERITY_LEVEL_RECOMMENDATION)
                    .setPendingIntent(somePendingIntent)
                    .build())
            .addIssue(
                SafetySourceIssue.Builder(
                        "recommendation_issue_id",
                        "Recommendation issue title",
                        "Recommendation issue summary",
                        SEVERITY_LEVEL_RECOMMENDATION,
                        "issue_type_id")
                    .addAction(
                        SafetySourceIssue.Action.Builder(
                                "recommendation_action_id", "See issue", somePendingIntent)
                            .build())
                    .build())
            .build()

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(context.deviceSupportsSafetyCenter())
    }

    @Before
    @After
    fun clearDataBetweenTest() {
        SafetyCenterFlags.setSafetyCenterEnabled(true)
        simpleTestSource.cleanupService()
    }

    @Test
    fun launchActivity_withFlagEnabled_showsSecurityAndPrivacyTitle() {
        startSafetyCenterActivity()

        // CollapsingToolbar title can't be found by text, so using description instead.
        waitFindObject(By.desc("Security & Privacy"))
    }

    @Test
    fun launchActivity_withFlagDisabled_showsSecurityTitle() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)

        startSafetyCenterActivity()

        // CollapsingToolbar title can't be found by text, so using description instead.
        waitFindObject(By.desc("Security"))
    }

    @Test
    fun launchActivity_displaysSafetyData() {
        simpleTestSource.configureTestSource()
        simpleTestSource.setSourceData(safetySourceDataCritical)

        startSafetyCenterActivity()

        assertSourceDataDisplayed(safetySourceDataCritical)
    }

    @Test
    fun updatingSafetySourceData_updatesDisplayedSafetyData() {
        simpleTestSource.configureTestSource()
        simpleTestSource.setSourceData(safetySourceDataCritical)
        startSafetyCenterActivity()

        simpleTestSource.setSourceData(safetySourceDataRecommendation)

        assertSourceDataDisplayed(safetySourceDataRecommendation)
    }

    @Test
    fun launchActivity_displaysStaticSources() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            SafetyCenterCtsConfigs.STATIC_SOURCES_CONFIG)

        startSafetyCenterActivity()

        findAllText(
            context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_GROUP_1.titleResId),
            context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_1.titleResId),
            context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_1.summaryResId),
            context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_GROUP_2.titleResId),
            context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_2.titleResId),
            context.getString(SafetyCenterCtsConfigs.STATIC_SOURCE_2.summaryResId))
    }

    @Test
    fun issueCard_confirmsDismissal_dismisses() {
        simpleTestSource.configureTestSource()
        simpleTestSource.setSourceData(safetySourceDataCritical)
        startSafetyCenterActivity()

        waitFindObject(By.desc("Dismiss")).click()
        waitFindObject(By.text("Dismiss this alert?"))
        findButton("Dismiss").click()

        assertIssueNotDisplayed(criticalIssue)
    }

    @Test
    fun issueCard_confirmsDismissal_cancels() {
        simpleTestSource.configureTestSource()
        simpleTestSource.setSourceData(safetySourceDataCritical)
        startSafetyCenterActivity()

        waitFindObject(By.desc("Dismiss")).click()
        waitFindObject(By.text("Dismiss this alert?"))
        findButton("Cancel").click()

        assertIssueDisplayed(criticalIssue)
    }

    // TODO: Add tests for issues dismissible without confirmation and non-dismissible issues if &
    // when the service supports them.

    private fun startSafetyCenterActivity() {
        context.startActivity(
            Intent(ACTION_SAFETY_CENTER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
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
        return waitFindObject(
            By.clickable(true).text(Pattern.compile("$label|${label.toString().uppercase()}")))
    }

    private fun findAllText(vararg textToFind: CharSequence?) {
        for (text in textToFind) if (text != null) waitFindObject(By.text(text.toString()))
    }

    private fun waitTextNotDisplayed(text: String) {
        val startMillis = SystemClock.elapsedRealtime()
        while (true) {
            if (waitFindObjectOrNull(By.text(text), NOT_DISPLAYED_CHECK_INTERVAL.toMillis()) ==
                null) {
                return
            }
            if (Duration.ofMillis(SystemClock.elapsedRealtime() - startMillis) >=
                NOT_DISPLAYED_TIMEOUT) {
                break
            }
            Thread.sleep(NOT_DISPLAYED_CHECK_INTERVAL.toMillis())
        }
        throw AssertionError(
            "View with text $text is still displayed after waiting for at least" +
                "$NOT_DISPLAYED_TIMEOUT")
    }

    companion object {
        private val NOT_DISPLAYED_TIMEOUT = Duration.ofSeconds(20)
        private val NOT_DISPLAYED_CHECK_INTERVAL = Duration.ofMillis(100)
    }
}
