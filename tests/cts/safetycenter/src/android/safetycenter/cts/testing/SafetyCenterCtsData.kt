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

import android.content.Context
import android.os.UserHandle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.ISSUE_TYPE_ID
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.android.safetycenter.internaldata.SafetyCenterEntryGroupId
import com.android.safetycenter.internaldata.SafetyCenterEntryId
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId
import com.android.safetycenter.internaldata.SafetyCenterIssueId
import com.android.safetycenter.internaldata.SafetyCenterIssueKey
import com.android.safetycenter.resources.SafetyCenterResourcesContext

/**
 * A class that provides [SafetyCenterData] objects and associated constants to facilitate asserting
 * on specific Safety Center states in SafetyCenter for testing.
 */
object SafetyCenterCtsData {

    /** The default [SafetyCenterData] returned by the Safety Center APIs. */
    val DEFAULT: SafetyCenterData
        get() {
            val context: Context = getApplicationContext()
            val safetyCenterResourcesContext = SafetyCenterResourcesContext.forTests(context)
            return SafetyCenterData(
                SafetyCenterStatus.Builder(
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_title"),
                        safetyCenterResourcesContext.getStringByName(
                            "overall_severity_level_ok_summary"))
                    .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
                    .build(),
                emptyList(),
                emptyList(),
                emptyList())
        }

    /** Creates an ID for a Safety Center entry group. */
    fun entryGroupId(sourcesGroupId: String) =
        SafetyCenterIds.encodeToString(
            SafetyCenterEntryGroupId.newBuilder().setSafetySourcesGroupId(sourcesGroupId).build())

    /** Creates an ID for a Safety Center entry. */
    fun entryId(sourceId: String, userId: Int = UserHandle.myUserId()) =
        SafetyCenterIds.encodeToString(
            SafetyCenterEntryId.newBuilder().setSafetySourceId(sourceId).setUserId(userId).build())

    /** Creates an ID for a Safety Center issue. */
    fun issueId(
        sourceId: String,
        sourceIssueId: String,
        issueTypeId: String = ISSUE_TYPE_ID,
        userId: Int = UserHandle.myUserId()
    ) =
        SafetyCenterIds.encodeToString(
            SafetyCenterIssueId.newBuilder()
                .setSafetyCenterIssueKey(
                    SafetyCenterIssueKey.newBuilder()
                        .setSafetySourceId(sourceId)
                        .setSafetySourceIssueId(sourceIssueId)
                        .setUserId(userId)
                        .build())
                .setIssueTypeId(issueTypeId)
                .build())

    /** Creates an ID for a Safety Center issue action. */
    fun issueActionId(
        sourceId: String,
        sourceIssueId: String,
        sourceIssueActionId: String,
        userId: Int = UserHandle.myUserId()
    ) =
        SafetyCenterIds.encodeToString(
            SafetyCenterIssueActionId.newBuilder()
                .setSafetyCenterIssueKey(
                    SafetyCenterIssueKey.newBuilder()
                        .setSafetySourceId(sourceId)
                        .setSafetySourceIssueId(sourceIssueId)
                        .setUserId(userId)
                        .build())
                .setSafetySourceIssueActionId(sourceIssueActionId)
                .build())
}
