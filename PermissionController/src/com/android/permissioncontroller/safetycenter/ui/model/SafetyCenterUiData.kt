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

package com.android.permissioncontroller.safetycenter.ui.model

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import androidx.annotation.RequiresApi

/** UI model representation of Safety Center Data */
data class SafetyCenterUiData(
    val safetyCenterData: SafetyCenterData,
    val resolvedIssues: Map<IssueId, ActionId> = emptyMap()
) {
    /** Returns the [SafetyCenterEntryGroup] corresponding to the provided ID */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun getMatchingGroup(groupId: String): SafetyCenterEntryGroup? {
        val entryOrGroups: List<SafetyCenterEntryOrGroup> = safetyCenterData.entriesOrGroups
        val entryGroups = entryOrGroups.mapNotNull { it.entryGroup }
        return entryGroups.find { it.id == groupId }
    }

    /**
     * Returns a list of [SafetyCenterIssue] corresponding to the provided ID. This will be
     * displayed as warning cards on a subpage in Safety Center.
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun getMatchingIssues(groupId: String): List<SafetyCenterIssue> =
        safetyCenterData.issues.filter { it.groupId == groupId }

    /**
     * Returns a list of dismissed [SafetyCenterIssue] corresponding to the provided ID. This will
     * be displayed as dismissed warning cards on a subpage in Safety Center.
     */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    fun getMatchingDismissedIssues(groupId: String): List<SafetyCenterIssue> =
        safetyCenterData.dismissedIssues.filter { it.groupId == groupId }
}
