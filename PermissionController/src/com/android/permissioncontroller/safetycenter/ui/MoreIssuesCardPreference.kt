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
import android.safetycenter.SafetyCenterIssue
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R

/** A preference that displays a card linking to a list of more {@link SafetyCenterIssue}. */
class MoreIssuesCardPreference(
    context: Context,
    @DrawableRes val preferencWidgetIconResourceId: Int,
    val numberOfHiddenIssues: Int,
    val firstHiddenIssueSeverityLevel: Int,
    val onClickListener: OnPreferenceClickListener
) : Preference(context) {

    init {
        layoutResource = R.layout.preference_more_issues_card
        widgetLayoutResource = R.layout.preference_expand_more_issues_widget
        onPreferenceClickListener = onClickListener

        setIcon(selectIconResId(firstHiddenIssueSeverityLevel))
        setTitle(R.string.safety_center_more_issues_card_title)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val widgetIcon = holder.findViewById(R.id.widget_icon) as? ImageView
        widgetIcon?.setImageResource(preferencWidgetIconResourceId)
        val widgetTitle = holder.findViewById(R.id.widget_title) as? TextView
        widgetTitle?.text = numberOfHiddenIssues.toString()
    }

    @DrawableRes
    private fun selectIconResId(severityLevel: Int): Int {
        return when (severityLevel) {
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK -> R.drawable.ic_safety_info
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION ->
                R.drawable.ic_safety_recommendation
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING -> R.drawable.ic_safety_warn
            else -> {
                Log.e(
                    TAG,
                    String.format(
                        "Unexpected SafetyCenterIssue.IssueSeverityLevel: %d", severityLevel))
                R.drawable.ic_safety_null_state
            }
        }
    }

    companion object {
        val TAG: String = MoreIssuesCardPreference::class.java.simpleName
    }
}
