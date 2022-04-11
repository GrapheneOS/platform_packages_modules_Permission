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
import android.safetycenter.SafetySourceData
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.runBlockingWithTimeout
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
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
                        "Received refresh intent with no broadcast id specified"
                    )
                }
                val sourceIds = intent.getStringArrayExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS)
                if (sourceIds.isNullOrEmpty()) {
                    throw IllegalArgumentException(
                        "Received refresh intent with no source ids specified"
                    )
                }
                val requestType = intent.getIntExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, -1)
                if (requestType != EXTRA_REFRESH_REQUEST_TYPE_GET_DATA &&
                        requestType != EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA
                ) {
                    throw IllegalArgumentException(
                        "Received refresh intent with invalid request type"
                    )
                }
                for (id: String in sourceIds) {
                    safetyCenterManager.setDataForSource(id, requestType, broadcastId)
                }
                runBlockingWithTimeout { refreshSafetySourcesChannel.send(broadcastId) }
            }
            ACTION_SAFETY_CENTER_ENABLED_CHANGED ->
                runBlockingWithTimeout {
                    safetyCenterEnabledChangedChannel.send(
                        safetyCenterManager.isSafetyCenterEnabled
                    )
                }
            else -> throw IllegalArgumentException("Received intent with action: $action")
        }
    }

    companion object {
        @Volatile private var refreshSafetySourcesChannel = Channel<String>(UNLIMITED)

        @Volatile private var safetyCenterEnabledChangedChannel = Channel<Boolean>(UNLIMITED)

        var safetySourceIds = mutableListOf<String>()

        var safetySourceDataOnPageOpen = mutableMapOf<String, SafetySourceData>()

        var safetySourceDataOnRescanClick = mutableMapOf<String, SafetySourceData>()

        fun reset() {
            safetySourceIds.clear()
            safetySourceDataOnRescanClick.clear()
            safetySourceDataOnPageOpen.clear()
            refreshSafetySourcesChannel.cancel()
            refreshSafetySourcesChannel = Channel()
            safetyCenterEnabledChangedChannel.cancel()
            safetyCenterEnabledChangedChannel = Channel()
        }

        fun receiveRefreshSafetySources(timeout: Duration = TIMEOUT_LONG): String =
            runBlockingWithTimeout(timeout) { refreshSafetySourcesChannel.receive() }

        fun receiveSafetyCenterEnabledChanged(timeout: Duration = TIMEOUT_LONG) =
            runBlockingWithTimeout(timeout) { safetyCenterEnabledChangedChannel.receive() }

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
                MANAGE_SAFETY_CENTER
            )

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
                WRITE_DEVICE_CONFIG
            )

        private fun createRefreshEvent(broadcastId: String) =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(broadcastId)
                .build()

        private fun SafetyCenterManager.setDataForSource(
            sourceId: String,
            requestType: Int,
            broadcastId: String
        ) {
            if (!safetySourceIds.contains(sourceId)) {
                return
            }
            when (requestType) {
                EXTRA_REFRESH_REQUEST_TYPE_GET_DATA ->
                    setSafetySourceDataWithPermission(
                        sourceId,
                        safetySourceDataOnPageOpen[sourceId],
                        createRefreshEvent(broadcastId)
                    )
                EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA ->
                    setSafetySourceDataWithPermission(
                        sourceId,
                        safetySourceDataOnRescanClick[sourceId],
                        createRefreshEvent(broadcastId)
                    )
            }
        }
    }
}
