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

package com.android.permissioncontroller.permission.ui

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.android.permissioncontroller.R

/**
 * In some scenarios we want to prevent the permission grant dialog from streaming to a remote
 * device. If the streaming is blocked show a warning dialog rendered by this activity.
 */
class PermissionDialogStreamingBlockedActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this)
            .setTitle(R.string.permission_grant_dialog_streaming_blocked_title)
            .setMessage(R.string.permission_grant_dialog_streaming_blocked_description)
            .setPositiveButton(R.string.ongoing_usage_dialog_ok, null)
            .setOnDismissListener() {
                setResult(RESULT_OK)
                finish()
            }
            .create()
            .show()
    }
}
