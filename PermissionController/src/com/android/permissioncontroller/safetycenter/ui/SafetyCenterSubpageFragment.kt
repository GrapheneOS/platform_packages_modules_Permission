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
import android.safetycenter.SafetyCenterErrorDetails
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.model.LiveSafetyCenterViewModelFactory
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.resources.SafetyCenterResourcesContext

/** A fragment that represents a generic subpage in Safety Center. */
@RequiresApi(UPSIDE_DOWN_CAKE)
class SafetyCenterSubpageFragment : PreferenceFragmentCompat() {

    private lateinit var sourceGroupId: String
    private lateinit var viewModel: SafetyCenterViewModel
    private lateinit var sameTaskSourceIds: List<String>
    private var subpageEntryGroup: PreferenceGroup? = null

    override fun onCreateAdapter(
        preferenceScreen: PreferenceScreen?
    ): RecyclerView.Adapter<RecyclerView.ViewHolder> {
        /* By default, the PreferenceGroupAdapter does setHasStableIds(true). Since each Preference
         * is internally allocated with an auto-incremented ID, it does not allow us to gracefully
         * update only changed preferences based on SafetyPreferenceComparisonCallback. In order to
         * allow the list to track the changes, we need to ignore the Preference IDs. */
        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            super.onCreateAdapter(preferenceScreen)
        adapter.setHasStableIds(false)
        return adapter
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.safety_center_subpage, rootKey)
        sourceGroupId = requireArguments().getString(SOURCE_GROUP_ID_KEY)!!

        sameTaskSourceIds =
            SafetyCenterResourcesContext(requireContext())
                .getStringByName("config_same_task_safety_source_ids")
                .split(",")
        subpageEntryGroup = getPreferenceScreen().findPreference(ENTRY_GROUP_KEY)

        viewModel =
            ViewModelProvider(
                    requireActivity(),
                    LiveSafetyCenterViewModelFactory(requireActivity().getApplication())
                )
                .get(SafetyCenterViewModel::class.java)

        viewModel.safetyCenterUiLiveData.observe(this) { uiData: SafetyCenterUiData? ->
            renderSafetyCenterEntryGroup(uiData)
        }
        viewModel.errorLiveData.observe(this) { errorDetails: SafetyCenterErrorDetails? ->
            displayErrorDetails(errorDetails)
        }

        getPreferenceManager().setPreferenceComparisonCallback(SafetyPreferenceComparisonCallback())
    }

    override fun onResume() {
        super.onResume()
        viewModel.pageOpen(sourceGroupId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activity?.isChangingConfigurations == true) {
            viewModel.changingConfigurations()
        }
    }

    private fun displayErrorDetails(errorDetails: SafetyCenterErrorDetails?) {
        if (errorDetails == null) return
        Toast.makeText(requireContext(), errorDetails.errorMessage, Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    private fun renderSafetyCenterEntryGroup(uiData: SafetyCenterUiData?) {
        Log.d(TAG, "renderSafetyCenterEntryGroup called with $uiData")
        val entryGroup = getMatchingGroup(uiData)
        if (entryGroup == null) {
            Log.w(TAG, "$sourceGroupId doesn't match any of the existing SafetySourcesGroup IDs")
            requireActivity().getSupportFragmentManager().popBackStack()
            return
        }

        requireActivity().setTitle(entryGroup.title)
        updateSafetyCenterEntries(entryGroup)
    }

    private fun getMatchingGroup(uiData: SafetyCenterUiData?): SafetyCenterEntryGroup? {
        val entryOrGroups: List<SafetyCenterEntryOrGroup>? =
            uiData?.safetyCenterData?.entriesOrGroups
        val entryGroups = entryOrGroups?.mapNotNull { it.entryGroup }
        return entryGroups?.find { it.id == sourceGroupId }
    }

    private fun updateSafetyCenterEntries(entryGroup: SafetyCenterEntryGroup) {
        Log.d(TAG, "updateSafetyCenterEntries called with $entryGroup")
        subpageEntryGroup?.removeAll()
        for (entry in entryGroup.entries) {
            subpageEntryGroup?.addPreference(
                SafetySubpageEntryPreference(requireContext(), getTaskIdForEntry(entry.id), entry)
            )
        }
    }

    private fun getTaskIdForEntry(entryId: String): Int? {
        val sourceId: String = SafetyCenterIds.entryIdFromString(entryId).getSafetySourceId()
        return if (sameTaskSourceIds.contains(sourceId)) requireActivity().getTaskId() else null
    }

    companion object {
        private val TAG: String = SafetyCenterSubpageFragment::class.java.simpleName
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
