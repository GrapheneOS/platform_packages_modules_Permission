/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.permissionmultidevice.cts

import android.os.SystemClock
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.google.common.truth.Truth

object UiAutomatorUtils {
    fun waitFindObject(selector: BySelector): UiObject2 {
        return findObjectWithRetry({ t -> UiAutomatorUtils2.waitFindObject(selector, t) })!!
    }

    fun waitFindObject(selector: BySelector, timeoutMillis: Long): UiObject2 {
        return findObjectWithRetry(
            { t -> UiAutomatorUtils2.waitFindObject(selector, t) },
            timeoutMillis
        )!!
    }

    fun click(selector: BySelector, timeoutMillis: Long = 20_000) {
        waitFindObject(selector, timeoutMillis).click()
    }

    fun findTextForView(selector: BySelector): String {
        val timeoutMs = 10000L

        var exception: Exception? = null
        var view: UiObject2? = null
        try {
            view = waitFindObject(selector, timeoutMs)
        } catch (e: Exception) {
            exception = e
        }
        Truth.assertThat(exception).isNull()
        Truth.assertThat(view).isNotNull()
        return view!!.text
    }

    private fun findObjectWithRetry(
        automatorMethod: (timeoutMillis: Long) -> UiObject2?,
        timeoutMillis: Long = 20_000L
    ): UiObject2? {
        val startTime = SystemClock.elapsedRealtime()
        return try {
            automatorMethod(timeoutMillis)
        } catch (e: StaleObjectException) {
            val remainingTime = timeoutMillis - (SystemClock.elapsedRealtime() - startTime)
            if (remainingTime <= 0) {
                throw e
            }
            automatorMethod(remainingTime)
        }
    }
}
