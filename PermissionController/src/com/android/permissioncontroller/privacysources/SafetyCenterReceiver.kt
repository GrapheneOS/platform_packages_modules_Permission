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

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.pm.PackageManager
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.service.v33.SafetyCenterQsTileService
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.WorkPolicyInfo.Companion.WORK_POLICY_INFO_SOURCE_ID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.launch

private fun createMapOfSourceIdsToSources(context: Context): Map<String, PrivacySource> {
    if (!SdkLevel.isAtLeastT()) {
        return emptyMap()
    }
    return mapOf(
        SC_NLS_SOURCE_ID to NotificationListenerPrivacySource(),
        WORK_POLICY_INFO_SOURCE_ID to WorkPolicyInfo.create(context))
}

class SafetyCenterReceiver(
    private val getMapOfSourceIdsToSources: (Context) -> Map<String, PrivacySource> =
        ::createMapOfSourceIdsToSources,
    private val dispatcher: CoroutineDispatcher = Default
) : BroadcastReceiver() {

    enum class RefreshEvent {
        UNKNOWN,
        EVENT_DEVICE_REBOOTED,
        EVENT_REFRESH_REQUESTED
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!SdkLevel.isAtLeastT()) {
            return
        }
        val safetyCenterManager: SafetyCenterManager =
            Utils.getSystemServiceSafe(
                PermissionControllerApplication.get().applicationContext,
                SafetyCenterManager::class.java)

        if (!safetyCenterManager.isSafetyCenterEnabled &&
            intent.action != ACTION_SAFETY_CENTER_ENABLED_CHANGED) {
            return
        }

        val mapOfSourceIdsToSources = getMapOfSourceIdsToSources(context)

        when (intent.action) {
            ACTION_SAFETY_CENTER_ENABLED_CHANGED -> {
                safetyCenterEnabledChanged(
                    context,
                    safetyCenterManager.isSafetyCenterEnabled,
                    mapOfSourceIdsToSources.values)
            }
            ACTION_REFRESH_SAFETY_SOURCES -> {
                val sourceIdsExtra = intent.getStringArrayExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS)
                if (sourceIdsExtra != null && sourceIdsExtra.isNotEmpty()) {
                    refreshSafetySources(
                        context,
                        intent,
                        RefreshEvent.EVENT_REFRESH_REQUESTED,
                        mapOfSourceIdsToSources,
                        sourceIdsExtra.toList())
                }
            }
            ACTION_BOOT_COMPLETED -> {
                refreshSafetySources(
                    context,
                    intent,
                    RefreshEvent.EVENT_DEVICE_REBOOTED,
                    mapOfSourceIdsToSources,
                    mapOfSourceIdsToSources.keys.toList())
            }
        }
    }

    private fun safetyCenterEnabledChanged(
        context: Context,
        enabled: Boolean,
        privacySources: Collection<PrivacySource>
    ) {
        privacySources.forEach { source ->
            CoroutineScope(dispatcher).launch {
                source.safetyCenterEnabledChanged(context, enabled)
            }
        }
        updateTileVisibility(context, enabled)
    }

    private fun updateTileVisibility(context: Context, enabled: Boolean) {
        val tileComponent = ComponentName(context, SafetyCenterQsTileService::class.java)
        val wasEnabled =
            context.packageManager?.getComponentEnabledSetting(tileComponent) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (enabled && !wasEnabled) {
            context.packageManager.setComponentEnabledSetting(
                tileComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0)
        } else if (!enabled && wasEnabled) {
            context.packageManager.setComponentEnabledSetting(
                tileComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
        }
    }

    private fun refreshSafetySources(
        context: Context,
        intent: Intent,
        refreshEvent: RefreshEvent,
        mapOfSourceIdsToSources: Map<String, PrivacySource>,
        sourceIdsToRefresh: List<String>
    ) {
        for (sourceId in sourceIdsToRefresh) {
            CoroutineScope(dispatcher).launch {
                mapOfSourceIdsToSources[sourceId]?.rescanAndPushSafetyCenterData(
                    context, intent, refreshEvent)
            }
        }
    }
}
