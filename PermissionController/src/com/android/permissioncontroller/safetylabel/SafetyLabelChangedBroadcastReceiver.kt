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

package com.android.permissioncontroller.safetylabel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.util.Preconditions
import com.android.permissioncontroller.permission.utils.KotlinUtils

/**
 * Listens for package additions, replacements, and removals.
 */
class SafetyLabelChangedBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!KotlinUtils.isSafetyLabelChangeNotificationsEnabled()) {
            return
        }

        val data = Preconditions.checkNotNull(intent.data)
        val action = Preconditions.checkNotNull(intent.action)
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            if (action != Intent.ACTION_PACKAGE_ADDED) {
                return
            }
            // TODO(b/261660881): Update Safety Label store when packages are added or updated.
            if (DEBUG) {
                Log.v(TAG, "Package ${data.schemeSpecificPart} replaced. Intent " +
                        "Action: ${action}\n")
            }
        } else if (action == Intent.ACTION_PACKAGE_ADDED) {
            // TODO(b/261660881): Update Safety Label store when packages are added or updated.
            if (DEBUG) {
                Log.v(TAG, "Package ${data.schemeSpecificPart} added.")
            }
        } else if (action == Intent.ACTION_PACKAGE_REMOVED) {
            // TODO(b/261661752): Update Safety Label store when packages are removed.
            if (DEBUG) {
                Log.v(TAG, "Package ${data.schemeSpecificPart} removed.")
            }
        }
    }
    companion object {
        private const val TAG = "SafetyLabelChangedBroadcastReceiver"
        private const val DEBUG = false
    }
}