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

package com.android.safetycenter.config

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.InputStream

abstract class ResourceBaseTest(
    val inputString: String,
    val message: String? = null
) {
    private val context: Context = getApplicationContext()

    @Test
    fun validate() {
        val inputStream: InputStream = inputString.byteInputStream()
        val resources: Resources = context.createPackageContext(RESOURCE_PKG, 0).resources
        if (message != null) {
            val thrown = assertThrows(Parser.ParseException::class.java) {
                Parser.parse(
                    inputStream,
                    RESOURCE_PKG,
                    resources
                )
            }
            assertThat(thrown).hasMessageThat().isEqualTo(message)
        } else {
            assertNotNull(Parser.parse(inputStream, RESOURCE_PKG, resources))
        }
    }

    companion object {
        const val RESOURCE_PKG =
            "com.android.safetycenter.tests.config.safetycenterconfigtestresources"
    }
}
