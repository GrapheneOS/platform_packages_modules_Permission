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

package com.android.permissioncontroller.safetycenter.ui.view

import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.ui.SeverityIconPicker

internal class SafetyEntryCommonViewsManager(rootEntryView: ViewGroup?) {

    val titleView: TextView? by lazy { rootEntryView?.findViewById(R.id.title) }
    val summaryView: TextView? by lazy { rootEntryView?.findViewById(R.id.summary) }
    private val iconView: ImageView? by lazy { rootEntryView?.findViewById(R.id.icon) }
    private val iconFrame: View? by lazy { rootEntryView?.findViewById(R.id.icon_frame) }
    private val emptySpace: View? by lazy { rootEntryView?.findViewById(R.id.empty_space) }

    fun showDetails(
        title: CharSequence,
        summary: CharSequence?,
        severityLevel: Int,
        severityUnspecifiedIconType: Int
    ) {
        titleView?.text = title
        summaryView?.showText(summary)

        iconView?.setImageResource(
            SeverityIconPicker.selectIconResId(severityLevel, severityUnspecifiedIconType)
        )

        val hideIcon =
            (severityLevel == ENTRY_SEVERITY_LEVEL_UNSPECIFIED &&
                severityUnspecifiedIconType == SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)
        iconFrame?.visibility = if (hideIcon) LinearLayout.GONE else LinearLayout.VISIBLE
        emptySpace?.visibility = if (hideIcon) LinearLayout.VISIBLE else LinearLayout.GONE
    }

    private fun TextView.showText(text: CharSequence?) {
        if (text != null && text.isNotEmpty()) {
            visibility = View.VISIBLE
            this.text = text
        } else {
            visibility = View.GONE
        }
    }

    companion object {

        /**
         * Change opacity to make some entries to look disabled but still be clickable
         *
         * @param isEnabled whether the [android.safetycenter.SafetyCenterEntry] is enabled
         * @param titleView view displaying the title text of the entry
         * @param summaryView view displaying the summary text of the entry
         */
        fun changeEnabledState(isEnabled: Boolean, titleView: TextView?, summaryView: TextView?) {
            if (isEnabled) {
                titleView?.alpha = 1f
                summaryView?.alpha = 1f
            } else {
                titleView?.alpha = 0.4f
                summaryView?.alpha = 0.4f
            }
        }
    }
}
