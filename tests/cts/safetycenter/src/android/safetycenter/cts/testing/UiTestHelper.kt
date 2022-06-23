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
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObjectOrNull
import java.time.Duration
import java.util.regex.Pattern

/** A class that helps with UI testing. */
object UiTestHelper {

    private val NOT_DISPLAYED_TIMEOUT = Duration.ofSeconds(20)
    private val NOT_DISPLAYED_CHECK_INTERVAL = Duration.ofMillis(100)
    private val FIND_TEXT_TIMEOUT = Duration.ofSeconds(25)
    private val TAG = UiTestHelper::class.java.simpleName

    fun findAllText(vararg textToFind: CharSequence?) {
        for (text in textToFind) {
            if (text != null) waitFindObject(By.text(text.toString()), FIND_TEXT_TIMEOUT.toMillis())
        }
    }

    fun findButton(label: CharSequence): UiObject2 {
        return waitFindObject(buttonSelector(label))
    }

    private fun buttonSelector(label: CharSequence): BySelector {
        return By.clickable(true).text(Pattern.compile("$label|${label.toString().uppercase()}"))
    }

    fun waitNotDisplayed(selector: BySelector) {
        val startMillis = SystemClock.elapsedRealtime()
        while (true) {
            try {
                if (waitFindObjectOrNull(selector, NOT_DISPLAYED_CHECK_INTERVAL.toMillis()) ==
                    null) {
                    return
                }
            } catch (e: StaleObjectException) {
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

    fun waitButtonNotDisplayed(label: CharSequence) {
        waitNotDisplayed(buttonSelector(label))
    }

    fun waitTextNotDisplayed(text: String) {
        waitNotDisplayed(By.text(text))
    }

    fun assertSourceDataDisplayed(sourceData: SafetySourceData) {
        findAllText(sourceData.status?.title, sourceData.status?.summary)

        for (sourceIssue in sourceData.issues) {
            assertSourceIssueDisplayed(sourceIssue)
        }
    }

    fun assertSourceIssueDisplayed(sourceIssue: SafetySourceIssue) {
        findAllText(sourceIssue.title, sourceIssue.subtitle, sourceIssue.summary)

        for (action in sourceIssue.actions) {
            findButton(action.label)
        }
    }

    fun assertSourceIssueNotDisplayed(sourceIssue: SafetySourceIssue) {
        waitTextNotDisplayed(sourceIssue.title.toString())
    }

    fun expandMoreIssuesCard() {
        waitFindObject(By.text("See all alerts")).click()
    }
}
