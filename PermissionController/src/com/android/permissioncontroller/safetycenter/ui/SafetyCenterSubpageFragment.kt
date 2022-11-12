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
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceFragmentCompat
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.model.LiveSafetyCenterViewModelFactory
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel
import com.android.safetycenter.internaldata.SafetyCenterIds

/** A fragment that represents a generic subpage in Safety Center. */
@RequiresApi(UPSIDE_DOWN_CAKE)
class SafetyCenterSubpageFragment(private val sourceGroupId: String) : PreferenceFragmentCompat() {

    private lateinit var viewModel: SafetyCenterViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.safety_center_subpage, rootKey)

        viewModel =
            ViewModelProvider(
                    requireActivity(),
                    LiveSafetyCenterViewModelFactory(requireActivity().getApplication()))
                .get(SafetyCenterViewModel::class.java)
        viewModel.safetyCenterUiLiveData.observe(
            this,
            Observer { uiData: SafetyCenterUiData? -> this.renderSafetyCenterEntryGroup(uiData) })
    }

    private fun renderSafetyCenterEntryGroup(uiData: SafetyCenterUiData?) {
        val entryGroup = getMatchingGroup(uiData, sourceGroupId)
        if (entryGroup == null) {
            Log.w(TAG, "$sourceGroupId doesn't match any of the existing SafetySourcesGroup IDs")
            requireActivity().getSupportFragmentManager().popBackStack()
            return
        }
        requireActivity().setTitle(entryGroup.title)
    }

    private fun getMatchingGroup(
        uiData: SafetyCenterUiData?,
        sourceGroupId: String
    ): SafetyCenterEntryGroup? {
        val entryOrGroups: List<SafetyCenterEntryOrGroup>? =
            uiData?.safetyCenterData?.entriesOrGroups
        val entryGroups = entryOrGroups?.mapNotNull { it.entryGroup }
        return entryGroups?.find {
            SafetyCenterIds.entryGroupIdFromString(it.id).getSafetySourcesGroupId() == sourceGroupId
        }
    }

    companion object {
        private const val TAG: String = "SafetyCenterSubpageFragment"
    }
}
