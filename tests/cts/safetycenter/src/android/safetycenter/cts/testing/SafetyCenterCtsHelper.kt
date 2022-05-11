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

import android.content.Context
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_STATIC
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import com.google.common.util.concurrent.MoreExecutors.directExecutor

/** A class that facilitates settings up SafetyCenter in tests. */
class SafetyCenterCtsHelper(private val context: Context) {
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    private val listeners = mutableListOf<SafetyCenterCtsListener>()
    private var currentConfigContainsCtsSource = false

    /** Resets the state of SafetyCenter. To be called after each test. */
    fun reset() {
        setEnabled(true)
        listeners.forEach {
            safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(it)
            it.reset()
        }
        listeners.clear()
        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()
        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()
        currentConfigContainsCtsSource = false
        SafetySourceReceiver.reset()
    }

    /** Enables or disables SafetyCenter based on [value]. */
    fun setEnabled(value: Boolean) {
        val currentValue = SafetyCenterFlags.getSafetyCenterEnabled()
        if (currentValue == value) {
            return
        }
        if (currentConfigContainsCtsSource) {
            // Wait for the SafetySourceReceiver to receive the SAFETY_CENTER_ENABLED_CHANGED
            // broadcast to avoid it leaking onto other tests.
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(value)
        } else {
            SafetyCenterFlags.setSafetyCenterEnabled(value)
        }
    }

    /** Sets the given [SafetyCenterConfig]. */
    fun setConfig(config: SafetyCenterConfig) {
        require(isEnabled())
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(config)
        currentConfigContainsCtsSource =
            config.safetySourcesGroups
                .flatMap { it.safetySources }
                .filter { it.type != SAFETY_SOURCE_TYPE_STATIC }
                .any { it.packageName == context.packageName }
    }

    /**
     * Adds and returns a [SafetyCenterCtsListener] to SafetyCenter.
     *
     * @param skipInitialData whether the returned [SafetyCenterCtsListener] should receive the
     * initial SafetyCenter update
     */
    fun addListener(skipInitialData: Boolean = true): SafetyCenterCtsListener {
        require(isEnabled())
        val listener = SafetyCenterCtsListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        if (skipInitialData) {
            listener.receiveSafetyCenterData()
        }
        listeners.add(listener)
        return listener
    }

    /** Sets the [SafetySourceData] for the given CTS [safetySourceId]. */
    fun setData(
        safetySourceId: String,
        safetySourceData: SafetySourceData?,
        safetyEvent: SafetyEvent = EVENT_SOURCE_STATE_CHANGED
    ) {
        require(isEnabled())
        safetyCenterManager.setSafetySourceDataWithPermission(
            safetySourceId, safetySourceData, safetyEvent)
    }

    private fun isEnabled() =
        context.deviceSupportsSafetyCenter() && SafetyCenterFlags.getSafetyCenterEnabled()
}
