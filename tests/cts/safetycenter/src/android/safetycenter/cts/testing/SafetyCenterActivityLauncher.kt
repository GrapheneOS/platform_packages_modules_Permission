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

package android.safetycenter.cts.testing

import android.Manifest.permission.DUMP
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils

/** A class that provides a way to launch the SafetyCenter activity in tests. */
object SafetyCenterActivityLauncher {

    /** Launches the SafetyCenter activity and exits it once [block] completes. */
    fun Context.launchSafetyCenterActivity(block: () -> Unit) {
        val launchSafetyCenterIntent =
            Intent(ACTION_SAFETY_CENTER)
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                .addFlags(FLAG_ACTIVITY_CLEAR_TASK)
        // Wait for the PermissionController's SafetyCenterReceiver broadcast to be fully dispatched
        // prior to opening the SafetyCenterActivity. This shouldn't be necessary but there seems
        // to some racyness when enabling the SafetyCenter QS tile while opening the
        // SafetyCenterActivity which causes the window to be removed.
        val activityManager = getSystemService(ActivityManager::class.java)!!
        callWithShellPermissionIdentity({ activityManager.waitForBroadcastIdle() }, DUMP)
        val uiDevice = UiAutomatorUtils.getUiDevice()
        uiDevice.waitForIdle()
        startActivity(launchSafetyCenterIntent)
        uiDevice.waitForIdle()
        block()
        uiDevice.pressBack()
        uiDevice.waitForIdle()
    }
}
