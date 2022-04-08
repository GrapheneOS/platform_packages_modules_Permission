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

package com.android.permissioncontroller.permission.ui.widget

import android.content.Context
import android.os.Build
import android.provider.DeviceConfig
import android.text.Html
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ChecksSdkIntAtLeast
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.R

class SafetyProtectionSectionView : LinearLayout {

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {}

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {}

    init {
        gravity = Gravity.CENTER
        orientation = HORIZONTAL
        visibility = if (isSafetyProtectionResourcesEnabled()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (isSafetyProtectionResourcesEnabled()) {
            LayoutInflater.from(context).inflate(R.layout.safety_protection_section, this)
            val safetyProtectionDisplayTextView =
                    requireViewById<TextView>(R.id.safety_protection_display_text)
            safetyProtectionDisplayTextView!!.setText(Html.fromHtml(
                    context.getString(android.R.string.safety_protection_display_text), 0))
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private fun isSafetyProtectionResourcesEnabled(): Boolean {
        return SdkLevel.isAtLeastT() && DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
                SAFETY_PROTECTION_RESOURCES_ENABLED, false)
    }

    companion object {
        private const val SAFETY_PROTECTION_RESOURCES_ENABLED = "safety_protection_enabled"
    }
}