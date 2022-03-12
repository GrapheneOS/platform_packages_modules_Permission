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

package android.safetycenter.config.cts

import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.config.ParseException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [ParseException]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class ParseExceptionTest {
    @Test
    fun propagatesMessage() {
        val message = "error message"
        assertFailsWith(ParseException::class, message) {
            throwParseException(message)
        }
    }

    @Test
    fun propagatesCause() {
        val message = "error message"
        val cause = Exception("error message for cause")
        val exception = assertFailsWith(ParseException::class, message) {
            throwParseException(message, cause)
        }
        assertEquals(cause, exception.cause)
    }

    private fun throwParseException(message: String) {
        throw ParseException(message)
    }

    private fun throwParseException(message: String, cause: Throwable) {
        throw ParseException(message, cause)
    }
}