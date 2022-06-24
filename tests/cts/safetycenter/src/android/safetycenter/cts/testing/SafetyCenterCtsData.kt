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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
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

/**
 * A class that provides [SafetyCenterData] objects and associated constants to facilitate asserting
 * on specific Safety Center states in SafetyCenter for testing.
 */
object SafetyCenterCtsData {

    /** The default [SafetyCenterData] returned by the Safety Center APIs. */
    val DEFAULT =
        SafetyCenterData(
            SafetyCenterStatus.Builder("Looks good", "This device is protected")
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
                .build(),
            emptyList(),
            emptyList(),
            emptyList())

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

    /**
     * A stub [PendingIntent] used to replace [PendingIntent]s that are not created as the CTS
     * package.
     */
    val stubPendingIntent
        get() =
            PendingIntent.getActivity(
                getApplicationContext(),
                0 /* requestCode */,
                Intent("Stub"),
                PendingIntent.FLAG_IMMUTABLE)

    /**
     * Helper function used to replace the [PendingIntent]s in [SafetyCenterData].
     *
     * This is needed because the [PendingIntent] of the external packages cannot be created in CTS
     * test.
     */
    fun SafetyCenterData.normalize() =
        SafetyCenterData(
            status,
            issues,
            entriesOrGroups.map {
                if (it.entry != null) SafetyCenterEntryOrGroup(it.entry!!.normalize())
                else SafetyCenterEntryOrGroup(it.entryGroup!!.normalize())
            },
            staticEntryGroups.map { it.normalize() })

    private fun SafetyCenterEntryGroup.normalize() =
        SafetyCenterEntryGroup.Builder(this).setEntries(entries.map { it.normalize() }).build()

    private fun SafetyCenterEntry.normalize() =
        SafetyCenterEntry.Builder(this).setPendingIntent(pendingIntent.normalize()).build()

    private fun SafetyCenterStaticEntryGroup.normalize() =
        SafetyCenterStaticEntryGroup(title, staticEntries.map { it.normalize() })

    private fun SafetyCenterStaticEntry.normalize() =
        SafetyCenterStaticEntry.Builder(this).setPendingIntent(pendingIntent.normalize()).build()

    private fun PendingIntent?.normalize() =
        if (this != null && creatorPackage != (getApplicationContext() as Context).packageName)
            stubPendingIntent
        else this
}
