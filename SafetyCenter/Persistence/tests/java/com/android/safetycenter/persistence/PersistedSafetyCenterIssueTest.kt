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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.permission.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** CTS tests for [PersistedSafetyCenterIssue]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class PersistedSafetyCenterIssueTest {

    @Test
    fun getSourceId_returnsSourceId() {
        assertThat(ACTIVE_ISSUE.sourceId).isEqualTo(ACTIVE_ISSUE_SOURCE_ID)
        assertThat(DISMISSED_ISSUE.sourceId).isEqualTo(DISMISSED_ISSUE_SOURCE_ID)
    }

    @Test
    fun getIssueId_returnsIssueId() {
        assertThat(ACTIVE_ISSUE.issueId).isEqualTo(ACTIVE_ISSUE_ISSUE_ID)
        assertThat(DISMISSED_ISSUE.issueId).isEqualTo(DISMISSED_ISSUE_ISSUE_ID)
    }

    @Test
    fun getFirstSeenAt_returnsFirstSeenAt() {
        assertThat(ACTIVE_ISSUE.firstSeenAt).isEqualTo(INSTANT)
        assertThat(DISMISSED_ISSUE.firstSeenAt).isEqualTo(INSTANT)
    }

    @Test
    fun getDismissedAt_returnsDismissedAt() {
        assertThat(ACTIVE_ISSUE.dismissedAt).isEqualTo(null)
        assertThat(DISMISSED_ISSUE.dismissedAt).isEqualTo(INSTANT)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(
                ACTIVE_ISSUE,
                PersistedSafetyCenterIssue.Builder()
                    .setSourceId(ACTIVE_ISSUE_SOURCE_ID)
                    .setIssueId(ACTIVE_ISSUE_ISSUE_ID)
                    .setFirstSeenAt(INSTANT)
                    .build())
            .addEqualityGroup(DISMISSED_ISSUE)
            .addEqualityGroup(
                PersistedSafetyCenterIssue.Builder()
                    .setSourceId("other")
                    .setIssueId(DISMISSED_ISSUE_ISSUE_ID)
                    .setFirstSeenAt(INSTANT)
                    .setDismissedAt(INSTANT)
                    .build())
            .addEqualityGroup(
                PersistedSafetyCenterIssue.Builder()
                    .setSourceId(DISMISSED_ISSUE_SOURCE_ID)
                    .setIssueId("other")
                    .setFirstSeenAt(INSTANT)
                    .setDismissedAt(INSTANT)
                    .build())
            .addEqualityGroup(
                PersistedSafetyCenterIssue.Builder()
                    .setSourceId(DISMISSED_ISSUE_SOURCE_ID)
                    .setIssueId(DISMISSED_ISSUE_ISSUE_ID)
                    .setFirstSeenAt(Instant.ofEpochMilli(0))
                    .setDismissedAt(INSTANT)
                    .build())
            .addEqualityGroup(
                PersistedSafetyCenterIssue.Builder()
                    .setSourceId(DISMISSED_ISSUE_SOURCE_ID)
                    .setIssueId(DISMISSED_ISSUE_ISSUE_ID)
                    .setFirstSeenAt(INSTANT)
                    .setDismissedAt(Instant.ofEpochMilli(0))
                    .build())
            .test()
    }

    companion object {
        private const val ACTIVE_ISSUE_SOURCE_ID = "active_source"
        private const val ACTIVE_ISSUE_ISSUE_ID = "active_issue"
        private const val DISMISSED_ISSUE_SOURCE_ID = "dismissed_source"
        private const val DISMISSED_ISSUE_ISSUE_ID = "dismissed_issue"
        private val INSTANT = Instant.ofEpochMilli(1654041600000)

        // TODO(b/230078826): Consider extracting shared constants to a separate file.
        private val ACTIVE_ISSUE =
            PersistedSafetyCenterIssue.Builder()
                .setSourceId(ACTIVE_ISSUE_SOURCE_ID)
                .setIssueId(ACTIVE_ISSUE_ISSUE_ID)
                .setFirstSeenAt(INSTANT)
                .build()

        private val DISMISSED_ISSUE =
            PersistedSafetyCenterIssue.Builder()
                .setSourceId(DISMISSED_ISSUE_SOURCE_ID)
                .setIssueId(DISMISSED_ISSUE_ISSUE_ID)
                .setFirstSeenAt(INSTANT)
                .setDismissedAt(INSTANT)
                .build()
    }
}