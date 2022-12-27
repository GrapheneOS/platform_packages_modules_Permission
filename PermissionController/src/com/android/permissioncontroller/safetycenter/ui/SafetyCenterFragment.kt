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

import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.safetycenter.SafetyCenterErrorDetails
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT
import com.android.permissioncontroller.safetycenter.ui.ParsedSafetyCenterIntent.Companion.toSafetyCenterIntent
import com.android.permissioncontroller.safetycenter.ui.model.LiveSafetyCenterViewModelFactory
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel
import com.android.safetycenter.resources.SafetyCenterResourcesContext

/** A base fragment that represents a page in Safety Center. */
@RequiresApi(TIRAMISU)
abstract class SafetyCenterFragment : PreferenceFragmentCompat() {

    lateinit var safetyCenterViewModel: SafetyCenterViewModel
    lateinit var sameTaskSourceIds: List<String>
    lateinit var collapsableIssuesCardHelper: CollapsableIssuesCardHelper

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
        sameTaskSourceIds =
            SafetyCenterResourcesContext(requireContext())
                .getStringByName("config_same_task_safety_source_ids")
                .split(",")

        safetyCenterViewModel =
            ViewModelProvider(
                    requireActivity(),
                    LiveSafetyCenterViewModelFactory(requireActivity().getApplication())
                )
                .get(SafetyCenterViewModel::class.java)
        safetyCenterViewModel.safetyCenterUiLiveData.observe(this) { uiData: SafetyCenterUiData? ->
            renderSafetyCenterData(uiData)
        }
        safetyCenterViewModel.errorLiveData.observe(this) { errorDetails: SafetyCenterErrorDetails?
            ->
            displayErrorDetails(errorDetails)
        }

        val safetyCenterIntent: ParsedSafetyCenterIntent =
            requireActivity().getIntent().toSafetyCenterIntent()
        val isQsFragment =
            getArguments()?.getBoolean(QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT, false) ?: false
        collapsableIssuesCardHelper =
            CollapsableIssuesCardHelper(safetyCenterViewModel, sameTaskSourceIds)
        collapsableIssuesCardHelper.apply {
            setFocusedIssueKey(safetyCenterIntent.safetyCenterIssueKey)
            // Set quick settings state first and allow restored state to override if necessary
            setQuickSettingsState(isQsFragment, safetyCenterIntent.shouldExpandIssuesGroup)
            restoreState(savedInstanceState)
        }

        getPreferenceManager().setPreferenceComparisonCallback(SafetyPreferenceComparisonCallback())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        collapsableIssuesCardHelper.saveState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activity?.isChangingConfigurations == true) {
            safetyCenterViewModel.changingConfigurations()
        }
    }

    abstract fun renderSafetyCenterData(uiData: SafetyCenterUiData?)

    private fun displayErrorDetails(errorDetails: SafetyCenterErrorDetails?) {
        if (errorDetails == null) return
        Toast.makeText(requireContext(), errorDetails.errorMessage, Toast.LENGTH_LONG).show()
        safetyCenterViewModel.clearError()
    }
}
