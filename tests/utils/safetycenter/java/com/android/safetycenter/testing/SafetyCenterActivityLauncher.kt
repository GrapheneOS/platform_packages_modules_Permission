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
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.RetryableException
import com.android.compatibility.common.util.UiAutomatorUtils2.getUiDevice
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import com.android.safetycenter.testing.UiTestHelper.waitDisplayed

/** A class that provides a way to launch the SafetyCenter activity in tests. */
@RequiresApi(TIRAMISU)
object SafetyCenterActivityLauncher {

    /**
     * Launches the SafetyCenter activity and exits it once [block] completes.
     *
     * @param withReceiverPermission whether we should hold the [SEND_SAFETY_CENTER_UPDATE]
     *   permission while the activity is on the screen (e.g. to ensure the CTS package can have its
     *   receiver called during refresh/rescan)
     * @param withRetry can be used paired with the `RetryRule` to support retrying launching the
     *   activity and the execution of the [block] by throwing a [RetryableException] on failure
     */
    fun Context.launchSafetyCenterActivity(
        intentExtras: Bundle? = null,
        intentAction: String = ACTION_SAFETY_CENTER,
        withReceiverPermission: Boolean = false,
        withRetry: Boolean = false,
        preventTrampolineToSettings: Boolean = true,
        block: () -> Unit
    ) {
        val launchSafetyCenterIntent =
            createIntent(
                intentAction,
                intentExtras,
                preventTrampolineToSettings = preventTrampolineToSettings
            )
        if (withReceiverPermission) {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                executeBlockAndExit(withRetry, block = block) {
                    startActivity(launchSafetyCenterIntent)
                }
            }
        } else {
            executeBlockAndExit(withRetry, block = block) {
                startActivity(launchSafetyCenterIntent)
            }
        }
    }

    /** Launches the SafetyCenter Quick Settings activity and exits it once [block] completes. */
    fun Context.launchSafetyCenterQsActivity(intentExtras: Bundle? = null, block: () -> Unit) {
        val launchSafetyCenterQsIntent = createIntent(ACTION_VIEW_SAFETY_CENTER_QS, intentExtras)
        executeBlockAndExit(withRetry = false, block = block) {
            callWithShellPermissionIdentity(REVOKE_RUNTIME_PERMISSIONS) {
                startActivity(launchSafetyCenterQsIntent)
            }
        }
    }

    /** Launches a page in Safety Center and exits it once [block] completes. */
    fun openPageAndExit(entryPoint: String, block: () -> Unit) {
        executeBlockAndExit(withRetry = false, block = block) {
            waitDisplayed(By.text(entryPoint)) { it.click() }
        }
    }

    private fun createIntent(
        intentAction: String,
        intentExtras: Bundle?,
        preventTrampolineToSettings: Boolean = false
    ): Intent {
        val launchIntent =
            Intent(intentAction).addFlags(FLAG_ACTIVITY_NEW_TASK).addFlags(FLAG_ACTIVITY_CLEAR_TASK)
        intentExtras?.let { launchIntent.putExtras(it) }
        if (preventTrampolineToSettings) {
            launchIntent.putExtra(EXTRA_PREVENT_TRAMPOLINE_TO_SETTINGS, true)
        }
        return launchIntent
    }

    /**
     * Executes the given [block] and presses the back button to exit.
     *
     * @param withRetry can be used paired with the `RetryRule` to support retrying the execution of
     *   the [block] by throwing a [RetryableException] on failure
     */
    fun executeBlockAndExit(
        withRetry: Boolean = false,
        block: () -> Unit,
        launchActivity: () -> Unit
    ) {
        maybeExecuteWithRetry(withRetry) {
            val uiDevice = getUiDevice()
            uiDevice.waitForIdle()
            launchActivity()
            uiDevice.waitForIdle()
            block()
            uiDevice.pressBack()
            uiDevice.waitForIdle()
        }
    }

    private fun maybeExecuteWithRetry(withRetry: Boolean = false, block: () -> Unit) {
        if (withRetry) {
            try {
                block()
            } catch (e: Throwable) {
                throw RetryableException(e, "Exception occurred when checking a Safety Center page")
            }
        } else {
            block()
        }
    }

    private const val EXTRA_PREVENT_TRAMPOLINE_TO_SETTINGS: String =
        "com.android.permissioncontroller.safetycenter.extra.PREVENT_TRAMPOLINE_TO_SETTINGS"
}
