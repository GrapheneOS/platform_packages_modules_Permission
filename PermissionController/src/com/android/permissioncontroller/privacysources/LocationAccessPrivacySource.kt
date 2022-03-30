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

package com.android.permissioncontroller.privacysources

import android.content.Context
import android.content.Intent
import android.os.Build
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.permission.service.LocationAccessCheck
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_DEVICE_REBOOTED
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_REFRESH_REQUESTED
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.UNKNOWN

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LocationAccessPrivacySource : PrivacySource {

    override fun safetyCenterEnabledChanged(context: Context, enabled: Boolean) {
        LocationAccessCheck(context, null).cancelBackgroundAccessWarningNotification()
    }

    override fun rescanAndPushSafetyCenterData(
        context: Context,
        intent: Intent,
        refreshEvent: RefreshEvent
    ) {
        val safetyRefreshEvent = when (refreshEvent) {
            UNKNOWN ->
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
            EVENT_DEVICE_REBOOTED ->
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build()
            EVENT_REFRESH_REQUESTED -> {
                val refreshBroadcastId = intent.getStringExtra(
                    SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID)
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                    .setRefreshBroadcastId(refreshBroadcastId).build()
            }
        }
        LocationAccessCheck(context, null).rescanAndPushSafetyCenterData(safetyRefreshEvent)
    }
}