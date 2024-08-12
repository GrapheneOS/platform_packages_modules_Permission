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
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceGroup
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.SafetyBrandChipPreference.Companion.closeSubpage
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData
import com.android.safetycenter.resources.SafetyCenterResourcesApk
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.IllustrationPreference
import com.android.settingslib.widget.SettingsThemeHelper

/** A fragment that represents a generic subpage in Safety Center. */
@RequiresApi(UPSIDE_DOWN_CAKE)
class SafetyCenterSubpageFragment : SafetyCenterFragment() {

    private lateinit var sourceGroupId: String
    private lateinit var subpageIssueGroup: PreferenceGroup
    private lateinit var subpageEntryGroup: PreferenceGroup
    private lateinit var subpageFooter: FooterPreference

    private var subpageIllustration: SafetyIllustrationPreference? = null
    private var expressiveIllustration: IllustrationPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.safety_center_subpage, rootKey)
        sourceGroupId = requireArguments().getString(SOURCE_GROUP_ID_KEY)!!

        val subpageBrandChip =
            preferenceScreen.findPreference<SafetyBrandChipPreference>(BRAND_CHIP_KEY)!!
        if (SettingsThemeHelper.isExpressiveTheme(requireContext())) {
            preferenceScreen.removePreference(subpageBrandChip)
        } else {
            subpageBrandChip.setupListener(requireActivity(), safetyCenterSessionId)
        }

        subpageIllustration = preferenceScreen.findPreference(ILLUSTRATION_KEY)!!
        expressiveIllustration = preferenceScreen.findPreference(EXPRESSIVE_ILLUSTRATION_KEY)!!
        if (SettingsThemeHelper.isExpressiveTheme(requireContext())) {
            subpageIllustration?.let { preferenceScreen.removePreference(it) }
            subpageIllustration = null
        } else {
            expressiveIllustration?.let { preferenceScreen.removePreference(it) }
            expressiveIllustration = null
        }

        subpageIssueGroup = preferenceScreen.findPreference(ISSUE_GROUP_KEY)!!
        subpageEntryGroup = preferenceScreen.findPreference(ENTRY_GROUP_KEY)!!
        subpageFooter = preferenceScreen.findPreference(FOOTER_KEY)!!

        setupIllustration()
        setupFooter()
        maybeRemoveSpacer()

        prerenderCurrentSafetyCenterData()
    }

    override fun configureInteractionLogger() {
        val logger = safetyCenterViewModel.interactionLogger
        logger.sessionId = safetyCenterSessionId
        logger.navigationSource = NavigationSource.fromIntent(requireActivity().getIntent())
        logger.viewType = ViewType.SUBPAGE
        logger.groupId = sourceGroupId
    }

    override fun onResume() {
        super.onResume()
        safetyCenterViewModel.pageOpen(sourceGroupId)
    }

    override fun renderSafetyCenterData(uiData: SafetyCenterUiData?) {
        Log.v(TAG, "renderSafetyCenterEntryGroup called with $uiData")
        val entryGroup = uiData?.getMatchingGroup(sourceGroupId)
        if (entryGroup == null) {
            Log.w(TAG, "$sourceGroupId doesn't match any of the existing SafetySourcesGroup IDs")
            closeSubpage(requireActivity(), requireContext(), safetyCenterSessionId)
            return
        }

        requireActivity().title = entryGroup.title
        updateSafetyCenterIssues(uiData)
        updateSafetyCenterEntries(entryGroup)
    }

    private fun setupIllustration() {
        val resPrefix =
            if (SettingsThemeHelper.isExpressiveTheme(requireContext())) {
                "illustration_expressive"
            } else {
                "illustration"
            }
        val resName = "${resPrefix}_${SnakeCaseConverter.fromCamelCase(sourceGroupId)}"
        val context = requireContext()
        val drawable = if (sourceGroupId == "AndroidLockScreenSources") {
            null
        } else {
            SafetyCenterResourcesApk(context).getDrawableByName(resName, context.theme)
        }
        if (drawable == null) {
            Log.w(TAG, "$sourceGroupId doesn't have any matching illustration")
            expressiveIllustration?.isVisible = false
            subpageIllustration?.isVisible = false
        }

        expressiveIllustration?.imageDrawable = drawable
        subpageIllustration?.illustrationDrawable = drawable
    }

    private fun setupFooter() {
        val resName = "${SnakeCaseConverter.fromCamelCase(sourceGroupId)}_footer"
        val footerText = SafetyCenterResourcesApk(requireContext()).getStringByName(resName)
        if (footerText.isEmpty()) {
            Log.w(TAG, "$sourceGroupId doesn't have any matching footer")
            subpageFooter.isVisible = false
        }
        // footer is ordered last by default
        // in order to keep a spacer after the footer, footer needs to be the second from last
        subpageFooter.order = Int.MAX_VALUE - 2
        subpageFooter.summary = footerText
    }

    private fun maybeRemoveSpacer() {
        if (SettingsThemeHelper.isExpressiveTheme(requireContext())) {
            val spacerPreference = preferenceScreen.findPreference<SpacerPreference>(SPACER_KEY)!!
            preferenceScreen.removePreference(spacerPreference)
        }
    }

    private fun updateSafetyCenterIssues(uiData: SafetyCenterUiData?) {
        subpageIssueGroup.removeAll()
        val subpageIssues = uiData?.getMatchingIssues(sourceGroupId)
        val subpageDismissedIssues = uiData?.getMatchingDismissedIssues(sourceGroupId)

        subpageIllustration?.isVisible =
            subpageIssues.isNullOrEmpty() && subpageIllustration?.illustrationDrawable != null
        expressiveIllustration?.isVisible =
            subpageIssues.isNullOrEmpty() && expressiveIllustration?.imageDrawable != null

        if (subpageIssues.isNullOrEmpty() && subpageDismissedIssues.isNullOrEmpty()) {
            Log.w(TAG, "$sourceGroupId doesn't have any matching SafetyCenterIssues")
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
            requireActivity().taskId,
        )
    }

    private fun updateSafetyCenterEntries(entryGroup: SafetyCenterEntryGroup) {
        Log.v(TAG, "updateSafetyCenterEntries called with $entryGroup")
        subpageEntryGroup.removeAll()
        for (entry in entryGroup.entries) {
            subpageEntryGroup.addPreference(
                SafetySubpageEntryPreference(
                    requireContext(),
                    PendingIntentSender.getTaskIdForEntry(
                        entry.id,
                        sameTaskSourceIds,
                        requireActivity(),
                    ),
                    entry,
                    safetyCenterViewModel,
                )
            )
        }
    }

    companion object {
        private val TAG = SafetyCenterSubpageFragment::class.java.simpleName
        private const val BRAND_CHIP_KEY = "subpage_brand_chip"
        private const val ILLUSTRATION_KEY = "subpage_illustration"
        private const val EXPRESSIVE_ILLUSTRATION_KEY = "subpage_expressive_illustration"
        private const val ISSUE_GROUP_KEY = "subpage_issue_group"
        private const val ENTRY_GROUP_KEY = "subpage_entry_group"
        private const val FOOTER_KEY = "subpage_footer"
        private const val SPACER_KEY = "subpage_spacer"
        private const val SOURCE_GROUP_ID_KEY = "source_group_id"

        /** Creates an instance of SafetyCenterSubpageFragment with the arguments set */
        @JvmStatic
        fun newInstance(sessionId: Long, groupId: String): SafetyCenterSubpageFragment {
            val args = Bundle()
            args.putLong(EXTRA_SESSION_ID, sessionId)
            args.putString(SOURCE_GROUP_ID_KEY, groupId)

            val subpageFragment = SafetyCenterSubpageFragment()
            subpageFragment.setArguments(args)
            return subpageFragment
        }
    }
}
