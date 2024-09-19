/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.ResourceUtils
import com.android.settingslib.widget.TwoTargetPreference

/**
 * A `TwoTargetPreference` with additional features:
 * - Propagates the supplied `app:extraWidgetIcon` drawable to the second target
 * - Allows defining a click listener on the second target (the icon on the right)
 */
class PermissionTwoTargetPreference : TwoTargetPreference {
    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        extraWidgetIconRes =
            ResourceUtils.getResourceIdByAttr(context, attrs, R.attr.extraWidgetIcon)
    }

    @DrawableRes private var extraWidgetIconRes = 0
    private var secondTargetClickListener: OnSecondTargetClickListener? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val settingsButton = holder.findViewById(R.id.settings_button) as ImageView
        if (extraWidgetIconRes != 0) {
            settingsButton.setImageResource(extraWidgetIconRes)
        }
        if (secondTargetClickListener != null) {
            settingsButton.setOnClickListener {
                secondTargetClickListener!!.onSecondTargetClick(this)
            }
        } else {
            settingsButton.setOnClickListener(null)
        }
    }

    override fun getSecondTargetResId() = R.layout.settings_button_preference_widget

    override fun shouldHideSecondTarget() = secondTargetClickListener == null

    fun setOnSecondTargetClickListener(listener: OnSecondTargetClickListener?) {
        secondTargetClickListener = listener
        notifyChanged()
    }

    fun setExtraWidgetIconRes(@DrawableRes extraWidgetIconRes: Int) {
        this.extraWidgetIconRes = extraWidgetIconRes
        notifyChanged()
    }

    interface OnSecondTargetClickListener {
        fun onSecondTargetClick(preference: TwoTargetPreference)
    }
}
