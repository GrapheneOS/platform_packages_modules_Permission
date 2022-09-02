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

package android.safetycenter.cts.testing

import android.os.SystemClock
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.StaleObjectException
import android.support.test.uiautomator.UiObject2
import android.util.Log
import com.android.compatibility.common.util.UiAutomatorUtils.getUiDevice
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObjectOrNull
import java.lang.IllegalStateException
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

/** A class that helps with UI testing. */
object UiTestHelper {

    /** The label of the rescan button. */
    const val RESCAN_BUTTON_LABEL = "Scan device"

    private val WAIT_TIMEOUT = Duration.ofSeconds(25)
    private val NOT_DISPLAYED_TIMEOUT = Duration.ofMillis(500)

    private val TAG = UiTestHelper::class.java.simpleName

    /** Waits for the given [selector] to be displayed. */
    fun waitDisplayed(selector: BySelector): UiObject2 =
        waitFor("$selector to be displayed", WAIT_TIMEOUT) {
            Result.success(waitFindObject(selector, it.toMillis()))
        }

    /** Waits for all the given [textToFind] to be displayed. */
    fun waitAllTextDisplayed(vararg textToFind: CharSequence?) {
        for (text in textToFind) {
            if (text != null) waitDisplayed(By.text(text.toString()))
        }
    }

    /** Waits for a button with the given [label] to be displayed. */
    fun waitButtonDisplayed(label: CharSequence): UiObject2 = waitDisplayed(buttonSelector(label))

    /** Waits for the given [selector] not to be displayed. */
    fun waitNotDisplayed(selector: BySelector) {
        waitFor("$selector not to be displayed", NOT_DISPLAYED_TIMEOUT) {
            if (waitFindObjectOrNull(selector, it.toMillis()) == null) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException("Found $selector when expecting it not to be displayed"))
            }
        }
    }

    /** Waits for all the given [textToFind] not to be displayed. */
    fun waitAllTextNotDisplayed(vararg textToFind: CharSequence?) {
        for (text in textToFind) {
            if (text != null) waitNotDisplayed(By.text(text.toString()))
        }
    }

    /** Waits for a button with the given [label] not to be displayed. */
    fun waitButtonNotDisplayed(label: CharSequence) {
        waitNotDisplayed(buttonSelector(label))
    }

    /**
     * Waits for most of the [SafetySourceData] information to be displayed.
     *
     * This includes its UI entry and its issues.
     */
    fun waitSourceDataDisplayed(sourceData: SafetySourceData) {
        waitAllTextDisplayed(sourceData.status?.title, sourceData.status?.summary)

        for (sourceIssue in sourceData.issues) {
            waitSourceIssueDisplayed(sourceIssue)
        }
    }

    /** Waits for most of the [SafetySourceIssue] information to be displayed. */
    fun waitSourceIssueDisplayed(sourceIssue: SafetySourceIssue) {
        waitAllTextDisplayed(sourceIssue.title, sourceIssue.subtitle, sourceIssue.summary)

        for (action in sourceIssue.actions) {
            waitButtonDisplayed(action.label)
        }
    }

    /** Waits for most of the [SafetySourceIssue] information not to be displayed. */
    fun waitSourceIssueNotDisplayed(sourceIssue: SafetySourceIssue) {
        waitAllTextNotDisplayed(sourceIssue.title)
    }

    /** Expands the more issues card button. */
    fun expandMoreIssuesCard() {
        waitDisplayed(By.text("See all alerts")).click()
    }

    private fun buttonSelector(label: CharSequence): BySelector {
        return By.clickable(true).text(Pattern.compile("$label|${label.toString().uppercase()}"))
    }

    private fun <T> waitFor(
        message: String,
        uiAutomatorConditionTimeout: Duration,
        uiAutomatorCondition: (Duration) -> Result<T>
    ): T {
        val elapsedStartMillis = SystemClock.elapsedRealtime()
        while (true) {
            getUiDevice().waitForIdle()
            val durationSinceStart =
                Duration.ofMillis(SystemClock.elapsedRealtime() - elapsedStartMillis)
            if (durationSinceStart >= WAIT_TIMEOUT) {
                break
            }
            val remainingTime = WAIT_TIMEOUT - durationSinceStart
            val uiAutomatorTimeout = minOf(uiAutomatorConditionTimeout, remainingTime)
            try {
                val result = uiAutomatorCondition(uiAutomatorTimeout)
                if (result.isSuccess) {
                    return result.getOrThrow()
                } else {
                    Log.d(
                        TAG,
                        "Failed condition for $message, will retry if within timeout",
                        result.exceptionOrNull())
                }
            } catch (e: StaleObjectException) {
                Log.d(TAG, "StaleObjectException for $message, will retry if within timeout", e)
            }
        }

        throw TimeoutException("Timed out waiting for $message")
    }
}
