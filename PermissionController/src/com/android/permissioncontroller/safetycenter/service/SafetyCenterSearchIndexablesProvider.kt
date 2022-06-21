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

package com.android.permissioncontroller.safetycenter.service

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Build
import android.os.UserManager
import android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS
import android.provider.SearchIndexablesContract.RawData.COLUMN_INTENT_ACTION
import android.provider.SearchIndexablesContract.RawData.COLUMN_KEY
import android.provider.SearchIndexablesContract.RawData.COLUMN_KEYWORDS
import android.provider.SearchIndexablesContract.RawData.COLUMN_RANK
import android.provider.SearchIndexablesContract.RawData.COLUMN_SCREEN_TITLE
import android.provider.SearchIndexablesContract.RawData.COLUMN_TITLE
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterManager
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.service.BaseSearchIndexablesProvider
import com.android.safetycenter.internaldata.SafetyCenterEntryId
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.resources.SafetyCenterResourcesContext

/**
 * {@link android.provider.SearchIndexablesProvider} for Safety Center.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class SafetyCenterSearchIndexablesProvider : BaseSearchIndexablesProvider() {

    private companion object {
        private const val BIOMETRIC_SOURCE_ID = "AndroidBiometrics"
        private const val PERSONAL_PROFILE_SUFFIX = "personal"
        private const val WORK_PROFILE_SUFFIX = "work"
    }

    override fun queryRawData(projection: Array<out String>?): Cursor {
        val context = requireContext()
        val safetyCenterManager: SafetyCenterManager? =
                context.getSystemService(SafetyCenterManager::class.java)
        val cursor = MatrixCursor(INDEXABLES_RAW_COLUMNS)
        if (safetyCenterManager?.isSafetyCenterEnabled == true) {
            val resourcesContext = SafetyCenterResourcesContext(context)

            val screenTitle = context.getString(R.string.safety_center_dashboard_page_title)

            safetyCenterManager.safetySources
                    ?.filter { it.type != SAFETY_SOURCE_TYPE_ISSUE_ONLY }
                    ?.forEach { safetySource ->
                        cursor.addSafetySourceRow(
                                context,
                                safetySource,
                                resourcesContext,
                                safetyCenterManager,
                                screenTitle
                        )
                    }
        }
        return cursor
    }

    private fun MatrixCursor.addSafetySourceRow(
        context: Context,
        safetySource: SafetySource,
        resourcesContext: SafetyCenterResourcesContext,
        safetyCenterManager: SafetyCenterManager,
        screenTitle: String
    ) {
        val searchTerms = resourcesContext.getNotEmptyStringOrNull(safetySource.searchTermsResId)
        var isPersonalEntryAdded = false
        var isWorkEntryAdded = false

        fun MatrixCursor.addIndexableRow(title: CharSequence, isWorkProfile: Boolean) =
                newRow()
                        .add(COLUMN_RANK, 0)
                        .add(COLUMN_TITLE, title)
                        .add(COLUMN_KEYWORDS, searchTerms?.let { "$title, $it" } ?: title)
                        .add(COLUMN_KEY, safetySource.id.addSuffix(isWorkProfile))
                        .add(COLUMN_INTENT_ACTION, Intent.ACTION_SAFETY_CENTER)
                        .add(COLUMN_SCREEN_TITLE, screenTitle)

        if (safetySource.id == BIOMETRIC_SOURCE_ID) {
            // correct Biometric Unlock title is only available when
            // Biometric SafetySource have sent the data to SafetyCenter
            context.getSystemService(UserManager::class.java)?.let { userManager ->
                safetyCenterManager.safetyEntries
                        .associateBy { it.entryId }
                        .filter { it.key.safetySourceId == BIOMETRIC_SOURCE_ID }
                        .forEach {
                            val isWorkProfile = userManager.isManagedProfile(it.key.userId)
                            if (isWorkProfile) {
                                isWorkEntryAdded = true
                            } else {
                                isPersonalEntryAdded = true
                            }
                            addIndexableRow(it.value.title, isWorkProfile)
                        }
            }
        }

        if (!isPersonalEntryAdded) {
            resourcesContext.getNotEmptyStringOrNull(safetySource.titleResId)?.let {
                addIndexableRow(title = it, isWorkProfile = false)
            }
        }

        if (!isWorkEntryAdded && safetySource.profile == SafetySource.PROFILE_ALL) {
            resourcesContext.getNotEmptyStringOrNull(safetySource.titleForWorkResId)?.let {
                addIndexableRow(title = it, isWorkProfile = true)
            }
        }
    }

    private fun Context.getNotEmptyStringOrNull(resId: Int): String? =
            if (resId != Resources.ID_NULL) {
                getString(resId).takeIf { it.isNotEmpty() }
            } else {
                null
            }

    private fun String.addSuffix(isWorkProfile: Boolean): String =
            "${this}_${if (isWorkProfile) WORK_PROFILE_SUFFIX else PERSONAL_PROFILE_SUFFIX}"

    private val SafetyCenterManager.safetySources: Sequence<SafetySource>?
        get() = safetyCenterConfig
                ?.safetySourcesGroups
                ?.asSequence()
                ?.flatMap { it.safetySources }

    private val SafetyCenterManager.safetyEntries: Sequence<SafetyCenterEntry>
        get() = safetyCenterData
                .entriesOrGroups
                .asSequence()
                .flatMap { groupOrEntry ->
                    groupOrEntry.entryGroup?.entries?.asSequence()
                            ?: groupOrEntry.entry?.let { sequenceOf(it) }
                            ?: emptySequence()
                }

    private val SafetyCenterEntry.entryId: SafetyCenterEntryId
        get() = SafetyCenterIds.entryIdFromString(id)
}
