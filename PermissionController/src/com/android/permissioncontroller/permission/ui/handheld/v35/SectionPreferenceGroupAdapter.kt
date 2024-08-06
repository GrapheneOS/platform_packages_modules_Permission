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

package com.android.permissioncontroller.permission.ui.handheld.v35

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.settingslib.widget.FooterPreference

/**
 * This is an extension over [PreferenceGroupAdapter] that allows creating visual sections for
 * preferences. It sets the following drawable states on item views when they are
 * [DrawableStateLinearLayout]:
 * - [android.R.attr.state_single] if the item is the only one in a section
 * - [android.R.attr.state_first] if the item is the first one in a section
 * - [android.R.attr.state_middle] if the item is neither the first one or the last one in a section
 * - [android.R.attr.state_last] if the item is the last one in a section
 * - [R.attr.state_has_icon_space] if the item has icon space
 *
 * Note that [androidx.preference.PreferenceManager.PreferenceComparisonCallback] isn't supported
 * (yet).
 */
class SectionPreferenceGroupAdapter(preferenceGroup: PreferenceGroup) :
    PreferenceGroupAdapter(preferenceGroup) {
    private var itemPositionStates = intArrayOf()

    private val handler = Handler(Looper.getMainLooper())

    private val syncRunnable = Runnable { buildItemPositionStates() }

    init {
        buildItemPositionStates()
    }

    override fun onPreferenceHierarchyChange(preference: Preference) {
        super.onPreferenceHierarchyChange(preference)

        // Post after super class has posted their sync runnable to update preferences.
        handler.removeCallbacks(syncRunnable)
        handler.post(syncRunnable)
    }

    private fun buildItemPositionStates() {
        val itemCount = itemCount
        if (itemPositionStates.size != itemCount) {
            itemPositionStates = IntArray(itemCount)
        }

        var lastItemIndex = -1
        for (i in 0..<itemCount) {
            val preference = getItem(i)!!

            if (preference.isSectionDivider) {
                itemPositionStates[i] = 0
                continue
            }

            val isFirstItemInFirstSection = lastItemIndex == -1
            val isFirstItemInNewSection = lastItemIndex != i - 1
            itemPositionStates[i] =
                if (isFirstItemInFirstSection || isFirstItemInNewSection) {
                    android.R.attr.state_first
                } else {
                    android.R.attr.state_middle
                }
            if (!isFirstItemInFirstSection && isFirstItemInNewSection) {
                itemPositionStates[lastItemIndex] =
                    if (itemPositionStates[lastItemIndex] == android.R.attr.state_first) {
                        android.R.attr.state_single
                    } else {
                        android.R.attr.state_last
                    }
            }

            lastItemIndex = i
        }

        if (lastItemIndex != -1) {
            itemPositionStates[lastItemIndex] =
                if (itemPositionStates[lastItemIndex] == android.R.attr.state_first) {
                    android.R.attr.state_single
                } else {
                    android.R.attr.state_last
                }
        }
    }

    private val Preference.isSectionDivider: Boolean
        get() = this is PreferenceCategory || this is FooterPreference

    override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)

        val drawableStateLinearLayout = holder.itemView as? DrawableStateLinearLayout ?: return
        val positionState = itemPositionStates.getOrElse(position) { 0 }
        if (positionState == 0) {
            return
        }
        val iconFrame =
            holder.findViewById(androidx.preference.R.id.icon_frame)
                ?: holder.findViewById(android.R.id.icon_frame)
        val hasIconSpace = iconFrame != null && iconFrame.visibility != View.GONE
        drawableStateLinearLayout.extraDrawableState =
            when (positionState) {
                android.R.attr.state_single ->
                    if (hasIconSpace) STATE_SET_SINGLE_HAS_ICON_SPACE else STATE_SET_SINGLE
                android.R.attr.state_first ->
                    if (hasIconSpace) STATE_SET_FIRST_HAS_ICON_SPACE else STATE_SET_FIRST
                android.R.attr.state_middle ->
                    if (hasIconSpace) STATE_SET_MIDDLE_HAS_ICON_SPACE else STATE_SET_MIDDLE
                android.R.attr.state_last ->
                    if (hasIconSpace) STATE_SET_LAST_HAS_ICON_SPACE else STATE_SET_LAST
                else -> error(positionState)
            }
    }

    companion object {
        private val STATE_SET_SINGLE = intArrayOf(android.R.attr.state_single)
        private val STATE_SET_FIRST = intArrayOf(android.R.attr.state_first)
        private val STATE_SET_MIDDLE = intArrayOf(android.R.attr.state_middle)
        private val STATE_SET_LAST = intArrayOf(android.R.attr.state_last)
        private val STATE_SET_SINGLE_HAS_ICON_SPACE =
            intArrayOf(android.R.attr.state_single, R.attr.state_has_icon_space)
        private val STATE_SET_FIRST_HAS_ICON_SPACE =
            intArrayOf(android.R.attr.state_first, R.attr.state_has_icon_space)
        private val STATE_SET_MIDDLE_HAS_ICON_SPACE =
            intArrayOf(android.R.attr.state_middle, R.attr.state_has_icon_space)
        private val STATE_SET_LAST_HAS_ICON_SPACE =
            intArrayOf(android.R.attr.state_last, R.attr.state_has_icon_space)
    }
}
