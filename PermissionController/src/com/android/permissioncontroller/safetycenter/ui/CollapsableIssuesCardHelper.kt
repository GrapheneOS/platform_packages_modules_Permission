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

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.os.Bundle
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
import androidx.preference.PreferenceGroup
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY
import kotlin.math.max

/**
 * Helper class to hide issue cards if over a predefined limit and handle revealing hidden issue
 * cards when the more issues preference is clicked
 */
class CollapsableIssuesCardHelper {
    private var isQuickSettingsFragment: Boolean = false
    private var issueCardsExpanded: Boolean = false

    /**
     * Sets QuickSetting specific state for use to determine correct issue section expansion state
     * as well ass more issues card icon values
     *
     * <p> Note the issueCardsExpanded value set here may be overridden here by calls to
     * restoreState
     *
     * @param isQuickSettingsFragment {@code true} if CollapsableIssuesCardHelper is being used in
     * quick settings fragment
     * @param issueCardsExpanded Whether issue cards should be expanded or not when added to
     * preference screen
     */
    fun setQuickSettingsState(isQuickSettingsFragment: Boolean, issueCardsExpanded: Boolean) {
        this.isQuickSettingsFragment = isQuickSettingsFragment
        this.issueCardsExpanded = issueCardsExpanded
    }

    /** Restore previously saved state from [Bundle] */
    fun restoreState(state: Bundle?) {
        if (state == null) {
            return
        }
        // Apply the previously saved state
        issueCardsExpanded = state.getBoolean(EXPAND_ISSUE_GROUP_SAVED_INSTANCE_STATE_KEY, false)
    }

    /** Save current state to provided [Bundle] */
    fun saveState(outState: Bundle) =
        outState.putBoolean(EXPAND_ISSUE_GROUP_SAVED_INSTANCE_STATE_KEY, issueCardsExpanded)

    /**
     * Add the [IssueCardPreference] managed by this helper to the specified [PreferenceGroup]
     *
     * @param context Current context
     * @param issuesPreferenceGroup Preference group to add preference to
     * @param issueCardPreferences {@link List} of {@link IssueCardPreference} to add to the
     * preference fragment
     */
    fun addIssues(
        context: Context,
        issuesPreferenceGroup: PreferenceGroup,
        issueCardPreferences: List<IssueCardPreference>
    ) {
        val moreIssuesCardPreference =
            createMoreIssuesCardPreference(context, issuesPreferenceGroup, issueCardPreferences)
        addIssuesToPreferenceGroupAndSetVisibility(
            issuesPreferenceGroup,
            issueCardPreferences,
            moreIssuesCardPreference,
            issueCardsExpanded)
    }

    private fun createMoreIssuesCardPreference(
        context: Context,
        issuesPreferenceGroup: PreferenceGroup,
        issueCardPreferences: List<IssueCardPreference>
    ): MoreIssuesCardPreference {
        val prefIconResourceId =
            if (isQuickSettingsFragment) R.drawable.ic_chevron_right else R.drawable.ic_expand_more
        val numberOfHiddenIssue: Int = getNumberOfHiddenIssues(issueCardPreferences)
        val firstHiddenIssueSeverityLevel: Int =
            getFirstHiddenIssueSeverityLevel(issueCardPreferences)

        return MoreIssuesCardPreference(
            context, prefIconResourceId, numberOfHiddenIssue, firstHiddenIssueSeverityLevel) {
            if (isQuickSettingsFragment) {
                goToSafetyCenter(context)
            } else {
                expand(issuesPreferenceGroup)
            }
            true
        }
    }

    private fun expand(issuesPreferenceGroup: PreferenceGroup) {
        if (issueCardsExpanded) {
            return
        }

        val numberOfPreferences = issuesPreferenceGroup.preferenceCount
        for (i in 0 until numberOfPreferences) {
            val preference = issuesPreferenceGroup.getPreference(i)
            when {
                // preferences with index under MAX_SHOWN_ISSUES_COLLAPSED are already visible
                i < MAX_SHOWN_ISSUES_COLLAPSED -> continue
                // "more issues" preference has index of MAX_SHOWN_ISSUES_COLLAPSED and must be
                // hidden after expansion of issues
                i == MAX_SHOWN_ISSUES_COLLAPSED -> preference.isVisible = false
                // All further issue preferences must be shown
                else -> preference.isVisible = true
            }
        }
        issueCardsExpanded = true
    }

    private fun goToSafetyCenter(context: Context) {
        // Navigate to Safety center with issues expanded
        val safetyCenterIntent = Intent(ACTION_SAFETY_CENTER)
        safetyCenterIntent.putExtra(EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY, true)
        context.startActivity(safetyCenterIntent)
    }

    companion object {
        private const val EXPAND_ISSUE_GROUP_SAVED_INSTANCE_STATE_KEY =
            "expand_issue_group_saved_instance_state_key"
        private const val MAX_SHOWN_ISSUES_COLLAPSED = 1

        private fun getNumberOfHiddenIssues(issueCardPreferences: List<IssueCardPreference>): Int =
            max(0, issueCardPreferences.size - MAX_SHOWN_ISSUES_COLLAPSED)

        private fun getFirstHiddenIssueSeverityLevel(
            issueCardPreferences: List<IssueCardPreference>
        ): Int {
            val firstHiddenIssue: IssueCardPreference? =
                issueCardPreferences.getOrNull(MAX_SHOWN_ISSUES_COLLAPSED)
            // If no first hidden issue, default to ISSUE_SEVERITY_LEVEL_OK
            return firstHiddenIssue?.severityLevel ?: ISSUE_SEVERITY_LEVEL_OK
        }

        private fun addIssuesToPreferenceGroupAndSetVisibility(
            issuesPreferenceGroup: PreferenceGroup,
            issueCardPreferences: List<IssueCardPreference>,
            moreIssuesCardPreference: MoreIssuesCardPreference,
            issueCardsExpanded: Boolean
        ) {
            issueCardPreferences.forEachIndexed { index, issueCardPreference ->
                if (index == MAX_SHOWN_ISSUES_COLLAPSED && !issueCardsExpanded) {
                    issuesPreferenceGroup.addPreference(moreIssuesCardPreference)
                }
                issueCardPreference.isVisible =
                    index < MAX_SHOWN_ISSUES_COLLAPSED || issueCardsExpanded
                issuesPreferenceGroup.addPreference(issueCardPreference)
            }
        }
    }
}
