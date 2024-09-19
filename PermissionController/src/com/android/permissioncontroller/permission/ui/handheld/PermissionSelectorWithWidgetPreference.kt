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
import androidx.annotation.IdRes
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.ResourceUtils
import com.android.settingslib.widget.SelectorWithWidgetPreference

/**
 * A `SelectorWithWidgetPreference` with additional features:
 * - Propagates the supplied `app:extraWidgetIcon` drawable to the extraWidget
 * - Propagates the supplied `app:extraWidgetId` id to the extraWidget (the icon on the right)
 * - Propagates the supplied `app:checkboxId` id to the checkbox (or radio button, on the left)
 * - Allows defining a "disabled click listener" handler that handles clicks when disabled
 */
class PermissionSelectorWithWidgetPreference : SelectorWithWidgetPreference {
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

    constructor(context: Context, isCheckbox: Boolean) : super(context, isCheckbox) {
        init(context, null)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        extraWidgetIconRes =
            ResourceUtils.getResourceIdByAttr(context, attrs, R.attr.extraWidgetIcon)
        extraWidgetIdRes = ResourceUtils.getResourceIdByAttr(context, attrs, R.attr.extraWidgetId)
        checkboxIdRes = ResourceUtils.getResourceIdByAttr(context, attrs, R.attr.checkboxId)
    }

    @DrawableRes private var extraWidgetIconRes = 0
    @IdRes private var extraWidgetIdRes = 0
    @IdRes private var checkboxIdRes = 0
    private var onDisabledClickListener: OnClickListener? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val extraWidget = holder.findViewById(
            com.android.settingslib.widget.preference.selector.R.id.selector_extra_widget
        ) as? ImageView
        val checkbox = holder.findViewById(android.R.id.checkbox)
        if (extraWidgetIconRes != 0) {
            extraWidget?.setImageResource(extraWidgetIconRes)
        }
        if (extraWidgetIdRes != 0) {
            extraWidget?.id = extraWidgetIdRes
        }
        if (checkboxIdRes != 0) {
            checkbox?.id = checkboxIdRes
        }
        if (onDisabledClickListener != null) {
            holder.itemView.isEnabled = true
            holder.itemView.setOnClickListener {
                onDisabledClickListener?.onRadioButtonClicked(this)
            }
        }
    }

    fun setOnDisabledClickListener(onClickListener: OnClickListener?) {
        onDisabledClickListener = onClickListener
        notifyChanged()
    }
}
