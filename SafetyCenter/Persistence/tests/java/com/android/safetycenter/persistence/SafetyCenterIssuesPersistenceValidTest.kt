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

package com.android.safetycenter.persistence

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.persistence.PersistenceConstants.PATH
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterIssuesPersistenceValidTest {
    data class Params(
        private val testName: String,
        val fileName: String,
        val expected: List<PersistedSafetyCenterIssue>
    ) {
        override fun toString() = testName
    }

    @Parameterized.Parameter lateinit var params: Params

    @Test
    fun validFile_matchesExpected() {
        val file = File(PATH + params.fileName)

        val actual = SafetyCenterIssuesPersistence.read(file)

        assertThat(actual).isEqualTo(params.expected)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() =
            arrayOf(
                Params("FileNotFound", "file_not_found.xml", emptyList()),
                Params(
                    "Valid",
                    "valid_file.xml",
                    listOf(
                        PersistedSafetyCenterIssue.Builder()
                            .setKey("key1")
                            .setFirstSeenAt(Instant.ofEpochMilli(1654041600000))
                            .build(),
                        PersistedSafetyCenterIssue.Builder()
                            .setKey("key2")
                            .setFirstSeenAt(Instant.ofEpochMilli(1654128000000))
                            .setDismissedAt(Instant.ofEpochMilli(1654214400000))
                            .build())))
    }
}
