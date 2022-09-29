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

import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.runBlockingWithTimeout
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.executeSafetyCenterIssueActionWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.refreshSafetySourcesWithPermission
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Request
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Response
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import android.safetycenter.cts.testing.WaitForBroadcastIdle.waitForBroadcastIdle
import androidx.test.core.app.ApplicationProvider
import java.time.Duration

/** Broadcast receiver used for testing broadcasts sent to safety sources. */
class SafetySourceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val userManager = context.getSystemService(UserManager::class.java)!!
        if (!userManager.isSystemUser) {
            // Ignore multi-users calls to this receiver for now, as we're not testing multi-users
            // broadcasts. When we do, we'll ensure that they don't leak between the tests.
            return
        }
        if (intent == null) {
            throw IllegalArgumentException("Received null intent")
        }

        runBlockingWithTimeout { safetySourceIntentHandler.handle(context, intent) }
    }

    companion object {
        @Volatile private var safetySourceIntentHandler = SafetySourceIntentHandler()

        /** Resets the state of the [SafetySourceReceiver] between tests. */
        fun reset() {
            safetySourceIntentHandler.cancel()
            safetySourceIntentHandler = SafetySourceIntentHandler()
        }

        /**
         * Sets the given [SafetySourceIntentHandler] [response] for the given [request] on this
         * receiver.
         */
        fun setResponse(request: Request, response: Response) {
            runBlockingWithTimeout { safetySourceIntentHandler.setResponse(request, response) }
        }

        fun SafetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            refreshReason: Int,
            timeout: Duration = TIMEOUT_LONG
        ) =
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                refreshSafetySourcesWithoutReceiverPermissionAndWait(refreshReason, timeout)
            }

        fun SafetyCenterManager.refreshSafetySourcesWithoutReceiverPermissionAndWait(
            refreshReason: Int,
            timeout: Duration
        ): String {
            refreshSafetySourcesWithPermission(refreshReason)
            if (timeout < TIMEOUT_LONG) {
                getApplicationContext().waitForBroadcastIdle()
            }
            return receiveRefreshSafetySources(timeout)
        }

        fun setSafetyCenterEnabledWithReceiverPermissionAndWait(
            value: Boolean,
            timeout: Duration = TIMEOUT_LONG
        ) =
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                setSafetyCenterEnabledWithoutReceiverPermissionAndWait(value, timeout)
            }

        fun setSafetyCenterEnabledWithoutReceiverPermissionAndWait(
            value: Boolean,
            timeout: Duration = TIMEOUT_LONG
        ): Boolean {
            SafetyCenterFlags.isEnabled = value
            if (timeout < TIMEOUT_LONG) {
                getApplicationContext().waitForBroadcastIdle()
            }
            return receiveSafetyCenterEnabledChanged(timeout)
        }

        fun SafetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            issueId: String,
            issueActionId: String,
            timeout: Duration = TIMEOUT_LONG
        ) {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                executeSafetyCenterIssueActionWithPermission(issueId, issueActionId)
                receiveResolveAction(timeout)
            }
        }

        fun SafetyCenterManager.dismissSafetyCenterIssueWithPermissionAndWait(
            issueId: String,
            timeout: Duration = TIMEOUT_LONG
        ) {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                dismissSafetyCenterIssueWithPermission(issueId)
                receiveDismissIssue(timeout)
            }
        }

        private fun receiveRefreshSafetySources(timeout: Duration = TIMEOUT_LONG): String =
            runBlockingWithTimeout(timeout) {
                safetySourceIntentHandler.receiveRefreshSafetySources()
            }

        /**
         * Waits for an [ACTION_SAFETY_CENTER_ENABLED_CHANGED] to be received by this receiver
         * within the given [timeout].
         */
        fun receiveSafetyCenterEnabledChanged(timeout: Duration = TIMEOUT_LONG): Boolean =
            runBlockingWithTimeout(timeout) {
                safetySourceIntentHandler.receiveSafetyCenterEnabledChanged()
            }

        private fun receiveResolveAction(timeout: Duration = TIMEOUT_LONG) {
            runBlockingWithTimeout(timeout) { safetySourceIntentHandler.receiveResolveAction() }
        }

        private fun receiveDismissIssue(timeout: Duration = TIMEOUT_LONG) {
            runBlockingWithTimeout(timeout) { safetySourceIntentHandler.receiveDimissIssue() }
        }

        private fun getApplicationContext(): Context = ApplicationProvider.getApplicationContext()
    }
}
