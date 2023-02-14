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

package com.android.permissioncontroller.safetycenter.ui

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.safetycenter.SafetyCenterEntryGroup
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.SafetyBrandChipPreference.Companion.closeSubpage
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModel
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModel.Pref
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModel.PrefState
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModelFactory
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData
import com.android.safetycenter.internaldata.SafetyCenterIds

/** A fragment that represents the privacy subpage in Safety Center. */
@RequiresApi(UPSIDE_DOWN_CAKE)
class PrivacySubpageFragment : SafetyCenterFragment() {

    private lateinit var subpageBrandChip: SafetyBrandChipPreference
    private lateinit var subpageIssueGroup: PreferenceGroup
    private lateinit var subpageGenericEntryGroup: PreferenceGroup
    private lateinit var subpageDataEntryGroup: PreferenceGroup
    private lateinit var privacyControlsViewModel: PrivacyControlsViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.privacy_subpage, rootKey)

        subpageBrandChip = getPreferenceScreen().findPreference(BRAND_CHIP_KEY)!!
        subpageIssueGroup = getPreferenceScreen().findPreference(ISSUE_GROUP_KEY)!!
        subpageGenericEntryGroup = getPreferenceScreen().findPreference(GENERIC_ENTRY_GROUP_KEY)!!
        subpageDataEntryGroup = getPreferenceScreen().findPreference(DATA_ENTRY_GROUP_KEY)!!
        subpageBrandChip.setupListener(requireActivity(), requireContext())

        val factory = PrivacyControlsViewModelFactory(requireActivity().getApplication())
        privacyControlsViewModel =
            ViewModelProvider(this, factory).get(PrivacyControlsViewModel::class.java)
        privacyControlsViewModel.controlStateLiveData.observe(this) {
            prefStates: Map<Pref, PrefState> ->
            renderPrivacyControls(prefStates)
        }
    }

    override fun onResume() {
        super.onResume()
        safetyCenterViewModel.pageOpen(SOURCE_GROUP_ID)
    }

    override fun renderSafetyCenterData(uiData: SafetyCenterUiData?) {
        Log.d(TAG, "renderSafetyCenterEntryGroup called with $uiData")
        val entryGroup = uiData?.getMatchingGroup(SOURCE_GROUP_ID)
        if (entryGroup == null) {
            Log.w(TAG, "$SOURCE_GROUP_ID doesn't match any of the existing SafetySourcesGroup IDs")
            closeSubpage(requireActivity(), requireContext())
            return
        }

        requireActivity().setTitle(entryGroup.title)
        updateSafetyCenterIssues(uiData)
        updateSafetyCenterEntries(entryGroup)
    }

    private fun updateSafetyCenterIssues(uiData: SafetyCenterUiData?) {
        subpageIssueGroup.removeAll()
        val subpageIssues =
            uiData?.safetyCenterData?.issues?.filter { it.groupId == SOURCE_GROUP_ID }
        val subpageDismissedIssues =
            uiData?.safetyCenterData?.dismissedIssues?.filter { it.groupId == SOURCE_GROUP_ID }
        if (subpageIssues.isNullOrEmpty() && subpageDismissedIssues.isNullOrEmpty()) {
            Log.w(TAG, "$SOURCE_GROUP_ID doesn't have any matching SafetyCenterIssues")
            return
        }

        collapsableIssuesCardHelper.addIssues(
            requireContext(),
            safetyCenterViewModel,
            getChildFragmentManager(),
            subpageIssueGroup,
            subpageIssues,
            subpageDismissedIssues,
            uiData.resolvedIssues,
            requireActivity().getTaskId()
        )
    }

    private fun updateSafetyCenterEntries(entryGroup: SafetyCenterEntryGroup) {
        Log.d(TAG, "updateSafetyCenterEntries called with $entryGroup")
        subpageGenericEntryGroup.removeAll()
        subpageDataEntryGroup.removeAll()

        for (entry in entryGroup.entries) {
            val entryId = entry.id
            val sourceId = SafetyCenterIds.entryIdFromString(entryId).getSafetySourceId()

            val subpageEntry =
                SafetySubpageEntryPreference(
                    requireContext(),
                    PendingIntentSender.getTaskIdForEntry(
                        entryId,
                        sameTaskSourceIds,
                        requireActivity()
                    ),
                    entry
                )

            when (sourceId) {
                "AndroidPermissionUsage",
                "AndroidPermissionManager",
                "AndroidAdsPrivacy",
                "AndroidHealthConnect" -> {
                    subpageGenericEntryGroup.addPreference(subpageEntry)
                }
                "AndroidPrivacyControls" -> {
                    // No action required here because the privacy controls are rendered separately
                    // by this fragment as generic preferences.
                }
                else -> {
                    subpageDataEntryGroup.addPreference(subpageEntry)
                }
            }
        }
    }

    private fun renderPrivacyControls(prefStates: Map<Pref, PrefState>) {
        fun setSwitchPreference(prefType: Pref) {
            val switchPreference: ClickableDisabledSwitchPreference? = findPreference(prefType.key)
            switchPreference?.setupState(
                prefStates[prefType],
                prefType,
                privacyControlsViewModel,
                this
            )
        }

        setSwitchPreference(Pref.MIC)
        setSwitchPreference(Pref.CAMERA)
        setSwitchPreference(Pref.CLIPBOARD)
        setSwitchPreference(Pref.SHOW_PASSWORD)

        val locationEntry: Preference? = findPreference(Pref.LOCATION.key)
        locationEntry?.setOnPreferenceClickListener {
            privacyControlsViewModel.handlePrefClick(this, Pref.LOCATION, null)
            true
        }
    }

    companion object {
        private val TAG: String = PrivacySubpageFragment::class.java.simpleName
        private const val BRAND_CHIP_KEY: String = "subpage_brand_chip"
        private const val ISSUE_GROUP_KEY: String = "subpage_issue_group"
        private const val GENERIC_ENTRY_GROUP_KEY: String = "subpage_generic_entry_group"
        private const val DATA_ENTRY_GROUP_KEY: String = "subpage_data_entry_group"
        const val SOURCE_GROUP_ID: String = "AndroidPrivacySources"
    }
}
