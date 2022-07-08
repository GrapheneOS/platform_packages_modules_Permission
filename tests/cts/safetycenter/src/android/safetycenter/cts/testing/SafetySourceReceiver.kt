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
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.runBlockingWithTimeout
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.executeSafetyCenterIssueActionWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.refreshSafetySourcesWithPermission
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey.Reason
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import android.safetycenter.cts.testing.WaitForBroadcastIdle.waitForBroadcastIdle
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED

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

        val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

        when (val action = intent.action) {
            ACTION_REFRESH_SAFETY_SOURCES -> {
                val broadcastId = intent.getStringExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID)
                if (broadcastId.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received refresh intent with no broadcast id specified")
                }
                val sourceIds = intent.getStringArrayExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS)
                if (sourceIds.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received refresh intent with no source ids specified")
                }
                val requestType = intent.getIntExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, -1)
                if (requestType != EXTRA_REFRESH_REQUEST_TYPE_GET_DATA &&
                    requestType != EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA) {
                    throw IllegalArgumentException(
                        "Received refresh intent with invalid request type: $requestType")
                }
                for (id: String in sourceIds) {
                    safetyCenterManager.setRefreshDataForSource(id, requestType, broadcastId)
                }
                runBlockingWithTimeout { refreshSafetySourcesChannel.send(broadcastId) }
            }
            ACTION_SAFETY_CENTER_ENABLED_CHANGED ->
                runBlockingWithTimeout {
                    safetyCenterEnabledChangedChannel.send(
                        safetyCenterManager.isSafetyCenterEnabled)
                }
            ACTION_HANDLE_INLINE_ACTION -> {
                val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
                if (sourceId.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received inline action intent with no source id specified")
                }
                val sourceIssueId = intent.getStringExtra(EXTRA_SOURCE_ISSUE_ID)
                if (sourceIssueId.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received inline action intent with no source issue id specified")
                }
                val sourceIssueActionId = intent.getStringExtra(EXTRA_SOURCE_ISSUE_ACTION_ID)
                if (sourceIssueActionId.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received inline action intent with no source issue action id specified")
                }
                safetyCenterManager.setInlineActionDataForSource(
                    sourceId, sourceIssueId, sourceIssueActionId)
                runBlockingWithTimeout { inlineActionChannel.send(Unit) }
            }
            ACTION_HANDLE_DISMISSED_ISSUE -> {
                val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
                if (sourceId.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received dismissed issue intent with no source id specified")
                }
                safetyCenterManager.setDismissedIssueDataForSource(sourceId)
                runBlockingWithTimeout { dismissedIssueChannel.send(Unit) }
            }
            else -> throw IllegalArgumentException("Received intent with action: $action")
        }
    }

    companion object {
        @Volatile private var refreshSafetySourcesChannel = Channel<String>(UNLIMITED)

        @Volatile private var safetyCenterEnabledChangedChannel = Channel<Boolean>(UNLIMITED)

        @Volatile private var inlineActionChannel = Channel<Unit>(UNLIMITED)

        @Volatile private var dismissedIssueChannel = Channel<Unit>(UNLIMITED)

        /**
         * Whether we should call [SafetyCenterManager.reportSafetySourceError] instead of
         * [SafetyCenterManager.setSafetySourceData].
         */
        @Volatile var shouldReportSafetySourceError = false

        /** An intent action to handle an inline action in this receiver. */
        const val ACTION_HANDLE_INLINE_ACTION =
            "android.safetycenter.cts.testing.action.HANDLE_INLINE_ACTION"

        /** An intent action to handle an issue dismissed by Safety Center in this receiver. */
        const val ACTION_HANDLE_DISMISSED_ISSUE =
            "android.safetycenter.cts.testing.action.HANDLE_DISMISSED_ISSUE"

        /**
         * An extra to be used with [ACTION_HANDLE_INLINE_ACTION] or [ACTION_HANDLE_DISMISSED_ISSUE]
         * to specify the target safety source id.
         */
        const val EXTRA_SOURCE_ID = "android.safetycenter.cts.testing.extra.SOURCE_ID"

        /**
         * An extra to be used with [ACTION_HANDLE_INLINE_ACTION] to specify the target safety
         * source issue id of the inline action.
         */
        const val EXTRA_SOURCE_ISSUE_ID = "android.safetycenter.cts.testing.extra.SOURCE_ISSUE_ID"

        /**
         * An extra to be used with [ACTION_HANDLE_INLINE_ACTION] to specify the target safety
         * source issue action id of the inline action.
         */
        const val EXTRA_SOURCE_ISSUE_ACTION_ID =
            "android.safetycenter.cts.testing.extra.SOURCE_ISSUE_ACTION_ID"

        /**
         * The [SafetySourceData] to return with [SafetyCenterManager.setSafetySourceData] in case
         * of refreshes or inline actions.
         */
        val safetySourceData = mutableMapOf<SafetySourceDataKey, SafetySourceData?>()

        fun reset() {
            shouldReportSafetySourceError = false
            safetySourceData.clear()
            refreshSafetySourcesChannel.cancel()
            refreshSafetySourcesChannel = Channel(UNLIMITED)
            safetyCenterEnabledChangedChannel.cancel()
            safetyCenterEnabledChangedChannel = Channel(UNLIMITED)
            inlineActionChannel.cancel()
            inlineActionChannel = Channel(UNLIMITED)
            dismissedIssueChannel.cancel()
            dismissedIssueChannel = Channel(UNLIMITED)
        }

        fun SafetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            refreshReason: Int,
            timeout: Duration = TIMEOUT_LONG
        ) =
            callWithShellPermissionIdentity(
                { refreshSafetySourcesWithoutReceiverPermissionAndWait(refreshReason, timeout) },
                SEND_SAFETY_CENTER_UPDATE)

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
            callWithShellPermissionIdentity(
                { setSafetyCenterEnabledWithoutReceiverPermissionAndWait(value, timeout) },
                SEND_SAFETY_CENTER_UPDATE)

        fun setSafetyCenterEnabledWithoutReceiverPermissionAndWait(
            value: Boolean,
            timeout: Duration
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
            callWithShellPermissionIdentity(
                {
                    executeSafetyCenterIssueActionWithPermission(issueId, issueActionId)
                    receiveInlineAction(timeout)
                },
                SEND_SAFETY_CENTER_UPDATE)
        }

        fun SafetyCenterManager.dismissSafetyCenterIssueWithPermissionAndWait(
            issueId: String,
            timeout: Duration = TIMEOUT_LONG
        ) {
            callWithShellPermissionIdentity(
                {
                    dismissSafetyCenterIssueWithPermission(issueId)
                    receiveDismissIssue(timeout)
                },
                SEND_SAFETY_CENTER_UPDATE)
        }

        private fun createRefreshEvent(broadcastId: String) =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(broadcastId)
                .build()

        private fun createInlineActionSuccessEvent(
            sourceIssueId: String,
            sourceIssueActionId: String
        ) =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
                .setSafetySourceIssueId(sourceIssueId)
                .setSafetySourceIssueActionId(sourceIssueActionId)
                .build()

        private fun createInlineActionErrorEvent(
            sourceIssueId: String,
            sourceIssueActionId: String
        ) =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
                .setSafetySourceIssueId(sourceIssueId)
                .setSafetySourceIssueActionId(sourceIssueActionId)
                .build()

        private fun SafetyCenterManager.setRefreshDataForSource(
            sourceId: String,
            requestType: Int,
            broadcastId: String
        ) {
            val reason = requestTypeToReason(requestType)
            setReceiverDataForSource(reason, sourceId, createRefreshEvent(broadcastId))
        }

        private fun requestTypeToReason(requestType: Int) =
            when (requestType) {
                EXTRA_REFRESH_REQUEST_TYPE_GET_DATA -> Reason.REFRESH_GET_DATA
                EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA -> Reason.REFRESH_FETCH_FRESH_DATA
                else -> throw IllegalStateException("Unexpected request type: $requestType")
            }

        private fun SafetyCenterManager.setInlineActionDataForSource(
            sourceId: String,
            sourceIssueId: String,
            sourceIssueActionId: String
        ) {
            setReceiverDataForSource(
                Reason.RESOLVE_ACTION,
                sourceId,
                createInlineActionSuccessEvent(sourceIssueId, sourceIssueActionId),
                createInlineActionErrorEvent(sourceIssueId, sourceIssueActionId))
        }

        private fun SafetyCenterManager.setDismissedIssueDataForSource(sourceId: String) {
            setReceiverDataForSource(Reason.DISMISS_ISSUE, sourceId, EVENT_SOURCE_STATE_CHANGED)
        }

        private fun SafetyCenterManager.setReceiverDataForSource(
            reason: Reason,
            sourceId: String,
            successSafetyEvent: SafetyEvent,
            errorSafetyEvent: SafetyEvent = successSafetyEvent
        ) {
            val key = SafetySourceDataKey(reason, sourceId)
            if (!safetySourceData.containsKey(key)) {
                return
            }
            if (shouldReportSafetySourceError) {
                reportSafetySourceError(sourceId, SafetySourceErrorDetails(errorSafetyEvent))
            } else {
                setSafetySourceData(sourceId, safetySourceData[key], successSafetyEvent)
            }
        }

        private fun receiveRefreshSafetySources(timeout: Duration = TIMEOUT_LONG): String =
            runBlockingWithTimeout(timeout) { refreshSafetySourcesChannel.receive() }

        private fun receiveSafetyCenterEnabledChanged(timeout: Duration = TIMEOUT_LONG): Boolean =
            runBlockingWithTimeout(timeout) { safetyCenterEnabledChangedChannel.receive() }

        private fun receiveInlineAction(timeout: Duration = TIMEOUT_LONG) {
            runBlockingWithTimeout(timeout) { inlineActionChannel.receive() }
        }

        private fun receiveDismissIssue(timeout: Duration = TIMEOUT_LONG) {
            runBlockingWithTimeout(timeout) { dismissedIssueChannel.receive() }
        }

        private fun getApplicationContext(): Context = ApplicationProvider.getApplicationContext()

        /**
         * A key to provide different [SafetySourceData] to this receiver depending on what [Intent]
         * it receives.
         */
        data class SafetySourceDataKey(val reason: Reason, val sourceId: String) {
            /** The reasons why this receiver could provide [SafetySourceData]. */
            enum class Reason {
                REFRESH_GET_DATA,
                REFRESH_FETCH_FRESH_DATA,
                RESOLVE_ACTION,
                DISMISS_ISSUE
            }
        }
    }
}
