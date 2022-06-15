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
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterIssuesPersistenceWriteTest {
    data class Params(
        private val testName: String,
        val fileName: String,
        val original: List<PersistedSafetyCenterIssue>
    ) {
        override fun toString() = testName
    }

    @Parameterized.Parameter lateinit var params: Params

    @Test
    fun writeRoundTrip_recreatesEqual() {
        val file = File.createTempFile(params.fileName, "xml")
        file.deleteOnExit()

        SafetyCenterIssuesPersistence.writeForUser(params.original, file)
        val read = SafetyCenterIssuesPersistence.readForUser(file)

        assertThat(read).isEqualTo(params.original)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() =
            arrayOf(
                Params("WithoutIssues", "valid_file_without_issues_written.xml", emptyList()),
                Params(
                    "WithIssues",
                    "valid_file_with_issues_written.xml",
                    listOf(
                        PersistedSafetyCenterIssue.Builder()
                            .setSourceId("source_id")
                            .setIssueId("issue_id")
                            .setFirstSeenAt(Instant.ofEpochMilli(1654041600000))
                            .build(),
                        PersistedSafetyCenterIssue.Builder()
                            .setSourceId("source_id")
                            .setIssueId("issue_id")
                            .setFirstSeenAt(Instant.ofEpochMilli(1654041600000))
                            .setDismissedAt(Instant.ofEpochMilli(1654214400000))
                            .build())))
    }
}
