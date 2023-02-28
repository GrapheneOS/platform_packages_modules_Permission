/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.tests.mocking.safetycenter.ui.model

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStatus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class SafetyCenterUiDataTest {

    @Test
    fun getMatchingGroup_withValidMatchingGroup_returnsExpectedEntryGroup() {
        val matchingGroup = createSafetyCenterEntryGroup(MATCHING_GROUP_ID)
        val nonMatchingGroup = createSafetyCenterEntryGroup(NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(entryGroups = listOf(matchingGroup, nonMatchingGroup))

        val result = SafetyCenterUiData(safetyCenterData).getMatchingGroup(MATCHING_GROUP_ID)

        assertThat(result).isEqualTo(matchingGroup)
    }

    @Test
    fun getMatchingGroup_withNoMatchingGroup_returnsNull() {
        val nonMatchingGroup = createSafetyCenterEntryGroup(NON_MATCHING_GROUP_ID)
        val safetyCenterData = createSafetyCenterData(entryGroups = listOf(nonMatchingGroup))

        val result = SafetyCenterUiData(safetyCenterData).getMatchingGroup(MATCHING_GROUP_ID)

        assertThat(result).isNull()
    }

    @Test
    fun getMatchingIssues_withValidMatchingIssue_returnsListOfIssues() {
        val matchingIssue = createSafetyCenterIssue(MATCHING_GROUP_ID)
        val nonMatchingIssue = createSafetyCenterIssue(NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(issues = listOf(matchingIssue, nonMatchingIssue))

        val result = SafetyCenterUiData(safetyCenterData).getMatchingIssues(MATCHING_GROUP_ID)

        assertThat(result).containsExactly(matchingIssue)
    }

    @Test
    fun getMatchingIssues_withNoMatchingIssue_returnsEmptyList() {
        val nonMatchingIssue = createSafetyCenterIssue(NON_MATCHING_GROUP_ID)
        val dismissedIssue = createSafetyCenterIssue(MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                issues = listOf(nonMatchingIssue),
                dismissedIssues = listOf(dismissedIssue)
            )

        val result = SafetyCenterUiData(safetyCenterData).getMatchingIssues(MATCHING_GROUP_ID)

        assertThat(result).isEmpty()
    }

    @Test
    fun getMatchingDismissedIssues_withValidMatchingDismissedIssue_returnsListOfDismissedIssues() {
        val matchingDismissedIssue = createSafetyCenterIssue(MATCHING_GROUP_ID)
        val nonMatchingDismissedIssue = createSafetyCenterIssue(NON_MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                dismissedIssues = listOf(matchingDismissedIssue, nonMatchingDismissedIssue)
            )

        val result =
            SafetyCenterUiData(safetyCenterData).getMatchingDismissedIssues(MATCHING_GROUP_ID)

        assertThat(result).containsExactly(matchingDismissedIssue)
    }

    @Test
    fun getMatchingDismissedIssues_withNoMatchingDismissedIssue_returnsEmptyList() {
        val nonMatchingDismissedIssue = createSafetyCenterIssue(NON_MATCHING_GROUP_ID)
        val nonDismissedIssue = createSafetyCenterIssue(MATCHING_GROUP_ID)
        val safetyCenterData =
            createSafetyCenterData(
                issues = listOf(nonDismissedIssue),
                dismissedIssues = listOf(nonMatchingDismissedIssue)
            )

        val result =
            SafetyCenterUiData(safetyCenterData).getMatchingDismissedIssues(MATCHING_GROUP_ID)

        assertThat(result).isEmpty()
    }

    private companion object {
        const val MATCHING_GROUP_ID = "matching_group_id"
        const val NON_MATCHING_GROUP_ID = "non_matching_group_id"

        fun createSafetyCenterData(
            issues: List<SafetyCenterIssue> = listOf(),
            entryGroups: List<SafetyCenterEntryGroup> = listOf(),
            dismissedIssues: List<SafetyCenterIssue> = listOf()
        ): SafetyCenterData {
            val safetyCenterStatus =
                SafetyCenterStatus.Builder("status title", "status summary").build()
            val builder = SafetyCenterData.Builder(safetyCenterStatus)
            for (issue in issues) {
                builder.addIssue(issue)
            }
            for (group in entryGroups) {
                builder.addEntryOrGroup(SafetyCenterEntryOrGroup(group))
            }
            for (dismissedIssue in dismissedIssues) {
                builder.addDismissedIssue(dismissedIssue)
            }
            return builder.build()
        }

        fun createSafetyCenterEntryGroup(groupId: String) =
            SafetyCenterEntryGroup.Builder(groupId, "group title").build()

        fun createSafetyCenterIssue(groupId: String) =
            SafetyCenterIssue.Builder("issue id", "issue title", "issue summary")
                .setGroupId(groupId)
                .build()
    }
}
