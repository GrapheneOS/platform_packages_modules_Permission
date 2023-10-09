/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui.wear

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.preference.Preference
import com.android.permissioncontroller.role.ui.RolePreference
import com.android.permissioncontroller.role.ui.TwoTargetPreference.OnSecondTargetClickListener
import com.android.settingslib.widget.TwoTargetPreference

/** Role preference for Wear. The preference is only used to hand over the properties to Chip. */
class WearRolePreference(
    context: Context,
    val label: String,
    val onDefaultClicked: () -> Unit = {},
    private var restriction: String? = null
) : TwoTargetPreference(context), RolePreference {

    override fun setOnSecondTargetClickListener(listener: OnSecondTargetClickListener?) {
        // no-op
    }

    override fun setUserRestriction(userRestriction: String?) {
        restriction = userRestriction
        setEnabled(restriction == null)
    }

    override fun asPreference(): Preference {
        return this
    }

    fun getOnClicked(): () -> Unit =
        restriction?.let {
            return {
                context.startActivity(
                    Intent(Settings.ACTION_SHOW_ADMIN_SUPPORT_DETAILS)
                        .putExtra(DevicePolicyManager.EXTRA_RESTRICTION, restriction)
                )
            }
        }
            ?: onDefaultClicked
}
