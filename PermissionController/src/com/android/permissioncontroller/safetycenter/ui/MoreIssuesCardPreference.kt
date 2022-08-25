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
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.StringUtils

/** A preference that displays a card linking to a list of more {@link SafetyCenterIssue}. */
class MoreIssuesCardPreference(
    context: Context,
    @DrawableRes val preferenceWidgetIconResourceId: Int,
    val previousMoreIssuesCardData: MoreIssuesCardData?,
    val newMoreIssuesCardData: MoreIssuesCardData,
    val onClickListener: OnPreferenceClickListener
) : Preference(context), ComparablePreference {

    private var moreIssuesCardAnimator = MoreIssuesCardAnimator()

    init {
        layoutResource = R.layout.preference_more_issues_card
        widgetLayoutResource = R.layout.preference_expand_more_issues_widget
        onPreferenceClickListener = onClickListener
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val statusIcon = holder.findViewById(R.id.status_icon) as ImageView
        setCurrentSeverityLevel(statusIcon)
        val widgetIcon = holder.findViewById(R.id.widget_icon) as ImageView
        widgetIcon.setImageResource(preferenceWidgetIconResourceId)
        val widgetTitle = holder.findViewById(R.id.widget_title) as TextView
        updateHiddenIssueCount(widgetTitle)

        val expansionString =
            StringUtils.getIcuPluralsString(
                context,
                R.string.safety_center_more_issues_card_expand_action,
                newMoreIssuesCardData.hiddenIssueCount)
        // Replacing the on-click label to indicate the number of hidden issues. The on-click
        // command is set to null so that it uses the existing expansion behaviour.
        ViewCompat.replaceAccessibilityAction(holder.itemView, ACTION_CLICK, expansionString, null)
    }

    private fun updateHiddenIssueCount(textView: TextView) {
        moreIssuesCardAnimator.cancelTextChangeAnimation(textView)

        val previousText = previousMoreIssuesCardData?.hiddenIssueCount.toString()
        val newText = newMoreIssuesCardData.hiddenIssueCount.toString()
        val animateTextChange = !previousText.isNullOrEmpty() && previousText != newText

        if (animateTextChange) {
            textView.text = previousText
            moreIssuesCardAnimator.animateChangeText(textView, newText)
        } else {
            textView.text = newText
        }
    }

    private fun setCurrentSeverityLevel(statusIcon: ImageView) {
        moreIssuesCardAnimator.cancelStatusAnimation(statusIcon)

        if (previousMoreIssuesCardData != null &&
            previousMoreIssuesCardData.severityLevel != newMoreIssuesCardData.severityLevel) {
            moreIssuesCardAnimator.animateStatusIconsChange(
                statusIcon,
                previousMoreIssuesCardData.severityLevel,
                newMoreIssuesCardData.severityLevel,
                selectIconResId(newMoreIssuesCardData.severityLevel))
        } else {
            statusIcon.setImageResource(selectIconResId(newMoreIssuesCardData.severityLevel))
        }
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

    override fun isSameItem(preference: Preference): Boolean {
        return preference is MoreIssuesCardPreference
    }

    override fun hasSameContents(preference: Preference): Boolean {
        return preference is MoreIssuesCardPreference &&
            previousMoreIssuesCardData == preference.previousMoreIssuesCardData &&
            newMoreIssuesCardData == preference.newMoreIssuesCardData &&
            preferenceWidgetIconResourceId == preference.preferenceWidgetIconResourceId
    }

    companion object {
        val TAG: String = MoreIssuesCardPreference::class.java.simpleName
    }
}

data class MoreIssuesCardData(val severityLevel: Int, val hiddenIssueCount: Int)
