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

package com.android.permissioncontroller.safetycenter.ui

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceGroup
import com.android.settingslib.widget.IllustrationPreference
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData
import com.android.safetycenter.resources.SafetyCenterResourcesContext

/** A fragment that represents a generic subpage in Safety Center. */
@RequiresApi(UPSIDE_DOWN_CAKE)
class SafetyCenterSubpageFragment : SafetyCenterFragment() {

    private lateinit var sourceGroupId: String
    private lateinit var subpageIllustration: IllustrationPreference
    private lateinit var subpageIssueGroup: PreferenceGroup
    private lateinit var subpageEntryGroup: PreferenceGroup

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.safety_center_subpage, rootKey)
        sourceGroupId = requireArguments().getString(SOURCE_GROUP_ID_KEY)!!

        subpageIllustration = getPreferenceScreen().findPreference(ILLUSTRATION_KEY)!!
        subpageIssueGroup = getPreferenceScreen().findPreference(ISSUE_GROUP_KEY)!!
        subpageEntryGroup = getPreferenceScreen().findPreference(ENTRY_GROUP_KEY)!!
        setupIllustration()
    }

    override fun onResume() {
        super.onResume()
        safetyCenterViewModel.pageOpen(sourceGroupId)
    }

    override fun renderSafetyCenterData(uiData: SafetyCenterUiData?) {
        Log.d(TAG, "renderSafetyCenterEntryGroup called with $uiData")
        val entryGroup = getMatchingGroup(uiData)
        if (entryGroup == null) {
            Log.w(TAG, "$sourceGroupId doesn't match any of the existing SafetySourcesGroup IDs")
            requireActivity().getSupportFragmentManager().popBackStack()
            return
        }

        requireActivity().setTitle(entryGroup.title)
        updateSafetyCenterIssues(uiData)
        updateSafetyCenterEntries(entryGroup)
    }

    private fun setupIllustration(){
        val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
        val groupIdSnakeCase = camelRegex.replace(sourceGroupId) { "_${it.value}" }.lowercase()
        val resName = "illustration_$groupIdSnakeCase"

        val context = requireContext()
        val drawable =
                SafetyCenterResourcesContext(context).getDrawableByName(resName, context.theme)
        if (drawable == null) {
            Log.w(TAG, "$sourceGroupId doesn't have any matching illustration")
            subpageIllustration.setVisible(false)
        }

        subpageIllustration.setImageDrawable(drawable)
    }

    private fun getMatchingGroup(uiData: SafetyCenterUiData?): SafetyCenterEntryGroup? {
        val entryOrGroups: List<SafetyCenterEntryOrGroup>? =
            uiData?.safetyCenterData?.entriesOrGroups
        val entryGroups = entryOrGroups?.mapNotNull { it.entryGroup }
        return entryGroups?.find { it.id == sourceGroupId }
    }

    private fun updateSafetyCenterIssues(uiData: SafetyCenterUiData?) {
        subpageIssueGroup.removeAll()
        val subpageIssues = uiData?.safetyCenterData?.issues?.filter { it.groupId == sourceGroupId }
        if (subpageIssues.isNullOrEmpty()) {
            Log.w(TAG, "$sourceGroupId doesn't have any matching SafetyCenterIssues")
            if (subpageIllustration.imageDrawable == null) return
            subpageIllustration.setVisible(true)
            return
        }

        subpageIllustration.setVisible(false)
        collapsableIssuesCardHelper.addIssues(
            requireContext(),
            safetyCenterViewModel,
            getChildFragmentManager(),
            subpageIssueGroup,
            subpageIssues,
            uiData.resolvedIssues,
            requireActivity().getTaskId()
        )
    }

    private fun updateSafetyCenterEntries(entryGroup: SafetyCenterEntryGroup) {
        Log.d(TAG, "updateSafetyCenterEntries called with $entryGroup")
        subpageEntryGroup.removeAll()
        for (entry in entryGroup.entries) {
            subpageEntryGroup.addPreference(
                SafetySubpageEntryPreference(
                    requireContext(),
                    PendingIntentSender.getTaskIdForEntry(
                        entry.id,
                        sameTaskSourceIds,
                        requireActivity()
                    ),
                    entry
                )
            )
        }
    }

    companion object {
        private val TAG: String = SafetyCenterSubpageFragment::class.java.simpleName
        private const val ILLUSTRATION_KEY: String = "subpage_illustration"
        private const val ISSUE_GROUP_KEY: String = "subpage_issue_group"
        private const val ENTRY_GROUP_KEY: String = "subpage_entry_group"
        private const val SOURCE_GROUP_ID_KEY: String = "source_group_id"

        /** Creates an instance of SafetyCenterSubpageFragment with the arguments set */
        @JvmStatic
        fun newInstance(groupId: String): SafetyCenterSubpageFragment {
            val args = Bundle()
            args.putString(SOURCE_GROUP_ID_KEY, groupId)

            val subpageFragment = SafetyCenterSubpageFragment()
            subpageFragment.setArguments(args)
            return subpageFragment
        }
    }
}
