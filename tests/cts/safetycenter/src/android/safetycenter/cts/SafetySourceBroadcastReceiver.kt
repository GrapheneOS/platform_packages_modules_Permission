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

package android.safetycenter.cts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Duration

/** Broadcast receiver to be used for testing broadcasts sent to safety source apps. */
class SafetySourceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            throw IllegalArgumentException("Received null intent")
        }

        if (intent.action != ACTION_REFRESH_SAFETY_SOURCES) {
            throw IllegalArgumentException("Received intent with action: ${intent.action}")
        }

        val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

        when (intent.getIntExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, -1)) {
            EXTRA_REFRESH_REQUEST_TYPE_GET_DATA ->
                safetyCenterManager.setSafetySourceDataWithPermission(
                        safetySourceId!!,
                        safetySourceDataOnPageOpen!!,
                        EVENT_REFRESH_REQUESTED
                )
            EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA ->
                safetyCenterManager.setSafetySourceDataWithPermission(
                        safetySourceId!!,
                        safetySourceDataOnRescanClick!!,
                        EVENT_REFRESH_REQUESTED
                )
        }

        runBlocking {
            updateChannel.send(Unit)
        }
    }

    companion object {
        private var updateChannel = Channel<Unit>()
        private val EVENT_REFRESH_REQUESTED =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                        .setRefreshBroadcastId("refresh_id")
                        .build()
        var safetySourceId: String? = null
        var safetySourceDataOnPageOpen: SafetySourceData? = null
        var safetySourceDataOnRescanClick: SafetySourceData? = null

        fun reset() {
            safetySourceId = null
            safetySourceDataOnRescanClick = null
            safetySourceDataOnPageOpen = null
            updateChannel = Channel()
        }

        fun waitTillOnReceiveComplete(duration: Duration) {
            runBlocking {
                withTimeout(duration.toMillis()) {
                    updateChannel.receive()
                }
            }
        }
    }
}
