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

package com.android.safetycenter.testing

import android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS
import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.content.Intent.ACTION_VIEW_SAFETY_CENTER_QS
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import com.android.compatibility.common.util.UiAutomatorUtils2.getUiDevice
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity

/** A class that provides a way to launch the SafetyCenter activity in tests. */
object SafetyCenterActivityLauncher {

    /**
     * Launches the SafetyCenter activity and exits it once [block] completes.
     *
     * @param withReceiverPermission whether we should hold the [SEND_SAFETY_CENTER_UPDATE]
     *   permission while the activity is on the screen (e.g. to ensure the CTS package can have its
     *   receiver called during refresh/rescan)
     */
    fun Context.launchSafetyCenterActivity(
        intentExtras: Bundle? = null,
        withReceiverPermission: Boolean = false,
        block: () -> Unit
    ) {
        val launchSafetyCenterIntent = createIntent(ACTION_SAFETY_CENTER, intentExtras)
        if (withReceiverPermission) {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                executeBlockAndExit(block) { startActivity(launchSafetyCenterIntent) }
            }
        } else {
            executeBlockAndExit(block) { startActivity(launchSafetyCenterIntent) }
        }
    }

    /** Launches the SafetyCenter Quick Settings activity and exits it once [block] completes. */
    fun Context.launchSafetyCenterQsActivity(intentExtras: Bundle? = null, block: () -> Unit) {
        val launchSafetyCenterQsIntent = createIntent(ACTION_VIEW_SAFETY_CENTER_QS, intentExtras)
        executeBlockAndExit(block) {
            callWithShellPermissionIdentity(REVOKE_RUNTIME_PERMISSIONS) {
                startActivity(launchSafetyCenterQsIntent)
            }
        }
    }

    private fun createIntent(intentAction: String, intentExtras: Bundle?): Intent {
        val launchIntent =
            Intent(intentAction).addFlags(FLAG_ACTIVITY_NEW_TASK).addFlags(FLAG_ACTIVITY_CLEAR_TASK)
        intentExtras?.let { launchIntent.putExtras(it) }
        return launchIntent
    }

    private fun executeBlockAndExit(block: () -> Unit, launchActivity: () -> Unit) {
        val uiDevice = getUiDevice()
        uiDevice.waitForIdle()
        launchActivity()
        uiDevice.waitForIdle()
        block()
        uiDevice.pressBack()
        uiDevice.waitForIdle()
    }
}
