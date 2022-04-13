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

import android.Manifest.permission.MANAGE_SAFETY_CENTER
import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.Manifest.permission.WRITE_DEVICE_CONFIG
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED

/** Broadcast receiver used for testing broadcasts sent to safety sources. */
class SafetySourceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
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
                val sourceId = intent.getStringExtra(EXTRA_INLINE_ACTION_SOURCE_ID)
                if (sourceId.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received inline action intent with no source id specified")
                }
                val sourceIssueId = intent.getStringExtra(EXTRA_INLINE_ACTION_SOURCE_ISSUE_ID)
                if (sourceIssueId.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received inline action intent with no source issue id specified")
                }
                val sourceIssueActionId =
                    intent.getStringExtra(EXTRA_INLINE_ACTION_SOURCE_ISSUE_ACTION_ID)
                if (sourceIssueActionId.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received inline action intent with no source issue action id specified")
                }
                safetyCenterManager.setInlineActionDataForSource(
                    sourceId, sourceIssueId, sourceIssueActionId)
                runBlockingWithTimeout { inlineActionChannel.send(Unit) }
            }
            else -> throw IllegalArgumentException("Received intent with action: $action")
        }
    }

    companion object {
        @Volatile private var refreshSafetySourcesChannel = Channel<String>(UNLIMITED)

        @Volatile private var safetyCenterEnabledChangedChannel = Channel<Boolean>(UNLIMITED)

        @Volatile private var inlineActionChannel = Channel<Unit>(UNLIMITED)

        /**
         * Whether we should call [SafetyCenterManager.reportSafetySourceError] instead of
         * [SafetyCenterManager.setSafetySourceData].
         */
        @Volatile var shouldReportSafetySourceError = false

        /** An intent action to handle an inline action in this receiver. */
        const val ACTION_HANDLE_INLINE_ACTION =
            "android.safetycenter.cts.testing.action.HANDLE_INLINE_ACTION"

        /**
         * An extra to be used with [ACTION_HANDLE_INLINE_ACTION] to specify the target safety
         * source id of the inline action.
         */
        const val EXTRA_INLINE_ACTION_SOURCE_ID =
            "android.safetycenter.cts.testing.extra.INLINE_ACTION_SOURCE_ID"

        /**
         * An extra to be used with [ACTION_HANDLE_INLINE_ACTION] to specify the target safety
         * source issue id of the inline action.
         */
        const val EXTRA_INLINE_ACTION_SOURCE_ISSUE_ID =
            "android.safetycenter.cts.testing.extra.INLINE_ACTION_SOURCE_ISSUE_ID"

        /**
         * An extra to be used with [ACTION_HANDLE_INLINE_ACTION] to specify the target safety
         * source issue action id of the inline action.
         */
        const val EXTRA_INLINE_ACTION_SOURCE_ISSUE_ACTION_ID =
            "android.safetycenter.cts.testing.extra.INLINE_ACTION_SOURCE_ISSUE_ACTION_ID"

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
        }

        fun receiveRefreshSafetySources(timeout: Duration = TIMEOUT_LONG): String =
            runBlockingWithTimeout(timeout) { refreshSafetySourcesChannel.receive() }

        fun receiveSafetyCenterEnabledChanged(timeout: Duration = TIMEOUT_LONG) =
            runBlockingWithTimeout(timeout) { safetyCenterEnabledChangedChannel.receive() }

        fun receiveInlineAction(timeout: Duration = TIMEOUT_LONG) =
            runBlockingWithTimeout(timeout) { inlineActionChannel.receive() }

        fun SafetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            refreshReason: Int,
            timeout: Duration = TIMEOUT_LONG
        ) =
            callWithShellPermissionIdentity(
                {
                    refreshSafetySources(refreshReason)
                    receiveRefreshSafetySources(timeout)
                },
                SEND_SAFETY_CENTER_UPDATE,
                MANAGE_SAFETY_CENTER)

        fun setSafetyCenterEnabledWithReceiverPermissionAndWait(
            value: Boolean,
            timeout: Duration = TIMEOUT_LONG
        ) =
            callWithShellPermissionIdentity(
                {
                    SafetyCenterFlags.setSafetyCenterEnabledWithoutPermission(value)
                    receiveSafetyCenterEnabledChanged(timeout)
                },
                SEND_SAFETY_CENTER_UPDATE,
                WRITE_DEVICE_CONFIG)

        fun SafetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            issueId: String,
            issueActionId: String,
            timeout: Duration = TIMEOUT_LONG
        ) =
            callWithShellPermissionIdentity(
                {
                    executeSafetyCenterIssueAction(issueId, issueActionId)
                    receiveInlineAction(timeout)
                },
                SEND_SAFETY_CENTER_UPDATE,
                MANAGE_SAFETY_CENTER)

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
            val key = SafetySourceDataKey(reason, sourceId)
            if (!safetySourceData.containsKey(key)) {
                return
            }
            // TODO(b/224455303): Write CTS tests for refresh errors.
            if (shouldReportSafetySourceError) {
                reportSafetySourceError(
                    sourceId, SafetySourceErrorDetails(createRefreshEvent(broadcastId)))
            } else {
                setSafetySourceData(
                    sourceId, safetySourceData[key], createRefreshEvent(broadcastId))
            }
        }

        private fun requestTypeToReason(requestType: Int) =
            when (requestType) {
                EXTRA_REFRESH_REQUEST_TYPE_GET_DATA -> SafetySourceDataKey.Reason.REFRESH_PAGE_OPEN
                EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA ->
                    SafetySourceDataKey.Reason.REFRESH_RESCAN
                else -> throw IllegalStateException("Unexpected request type: $requestType")
            }

        private fun SafetyCenterManager.setInlineActionDataForSource(
            sourceId: String,
            sourceIssueId: String,
            sourceIssueActionId: String
        ) {
            val inlineActionKey =
                SafetySourceDataKey(SafetySourceDataKey.Reason.INLINE_ACTION, sourceId)
            if (!safetySourceData.containsKey(inlineActionKey)) {
                throw IllegalStateException(
                    "Attempt to reply to an inline action when no data provided for it")
            }
            if (shouldReportSafetySourceError) {
                reportSafetySourceError(
                    sourceId,
                    SafetySourceErrorDetails(
                        createInlineActionErrorEvent(sourceIssueId, sourceIssueActionId)))
            } else {
                setSafetySourceData(
                    sourceId,
                    safetySourceData[inlineActionKey],
                    createInlineActionSuccessEvent(sourceIssueId, sourceIssueActionId))
            }
        }

        /**
         * A key to provide different [SafetySourceData] to this receiver depending on what [Intent]
         * it receives.
         */
        data class SafetySourceDataKey(val reason: Reason, val sourceId: String) {
            /** The reasons why this receiver could provide [SafetySourceData]. */
            enum class Reason {
                REFRESH_PAGE_OPEN,
                REFRESH_RESCAN,
                INLINE_ACTION
            }
        }
    }
}
