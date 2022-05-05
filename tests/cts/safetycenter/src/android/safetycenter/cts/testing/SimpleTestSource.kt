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

import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.CTS_SINGLE_SOURCE_CONFIG

class SimpleTestSource(private val safetyCenterManager: SafetyCenterManager) {
    fun configureTestSource() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
    }

    fun cleanupService() {
        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()
        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()
        SafetySourceReceiver.reset()
    }

    fun setSourceData(data: SafetySourceData, event: SafetyEvent = EVENT_SOURCE_STATE_CHANGED) {
        safetyCenterManager.setSafetySourceDataWithPermission(SOURCE_ID, data, event)
    }

    companion object {
        val EVENT_SOURCE_STATE_CHANGED =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
        private const val SOURCE_ID = SafetyCenterCtsConfigs.CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID
    }
}
