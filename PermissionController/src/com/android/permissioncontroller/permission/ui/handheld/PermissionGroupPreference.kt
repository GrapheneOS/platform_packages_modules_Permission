/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.content.res.Resources
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.KotlinUtils

/**
 * A Preference for the permission group. Has icon, title and subtitle.
 */
class PermissionGroupPreference(
    context: Context,
    private val resources: Resources,
    private val permGroupName: String
) : Preference(context) {

    init {
        layoutResource = R.layout.permission_group_preference
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        (holder.findViewById(R.id.icon) as ImageView).setImageDrawable(
                KotlinUtils.getPermGroupIcon(context, permGroupName))
        (holder.findViewById(R.id.icon) as ImageView).contentDescription =
                KotlinUtils.getPermGroupLabel(context, permGroupName)
        (holder.findViewById(R.id.title) as TextView).text =
                resources.getString(R.string.permission_group_usage_title,
                        KotlinUtils.getPermGroupLabel(context, permGroupName))
        (holder.findViewById(R.id.subtitle) as TextView).text =
                resources.getString(R.string.permission_group_usage_subtitle,
                        KotlinUtils.getPermGroupLabel(context, permGroupName))
    }
}