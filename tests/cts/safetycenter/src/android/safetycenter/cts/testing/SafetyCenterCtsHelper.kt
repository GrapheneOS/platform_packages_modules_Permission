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
import android.content.IntentFilter
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_STATIC
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterFlags.isSafetyCenterEnabled
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import com.google.common.util.concurrent.MoreExecutors.directExecutor

/** A class that facilitates settings up Safety Center in tests. */
class SafetyCenterCtsHelper(private val context: Context) {

    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    private val safetyCenterFlagsSnapshot = SafetyCenterFlags.snapshot
    private val listeners = mutableListOf<SafetyCenterCtsListener>()
    private val enabledChangedReceivers = mutableListOf<SafetyCenterEnabledChangedReceiver>()

    private var currentConfigContainsCtsSource = false

    /** Resets the state of Safety Center. To be called after each test. */
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
        resetFlags()
        SafetySourceReceiver.reset()
        enabledChangedReceivers.forEach {
            context.unregisterReceiver(it)
            it.reset()
        }
        enabledChangedReceivers.clear()
    }

    /** Enables or disables SafetyCenter based on [value]. */
    fun setEnabled(value: Boolean) {
        val currentValue = SafetyCenterFlags.isEnabled
        if (currentValue == value) {
            return
        }
        setEnabledWaitingForBroadcastIdle(value)
    }

    /** Adds and returns a runtime-registered [SafetyCenterEnabledChangedReceiver]. */
    fun addEnabledChangedReceiver(): SafetyCenterEnabledChangedReceiver {
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver()
        context.registerReceiver(
            enabledChangedReceiver, IntentFilter(ACTION_SAFETY_CENTER_ENABLED_CHANGED))
        enabledChangedReceivers.add(enabledChangedReceiver)
        return enabledChangedReceiver
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

    private fun resetFlags() {
        setEnabled(safetyCenterFlagsSnapshot.isSafetyCenterEnabled())
        SafetyCenterFlags.reset(safetyCenterFlagsSnapshot)
    }

    private fun setEnabledWaitingForBroadcastIdle(value: Boolean) {
        // Wait for the CTS ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcast to be dispatched to avoid
        // it leaking onto other tests.
        if (currentConfigContainsCtsSource) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(value)
        } else {
            // This is still necessary because the config could be set in another test. Since
            // broadcasts are dispatched asynchronously, a wrong sequencing could still cause
            // failures (e.g: 1: flag switched, 2: test finishes, 3: new test starts, 4: a CTS
            // config is set, 5: broadcast from 1 dispatched).
            addEnabledChangedReceiver().setSafetyCenterEnabledWithReceiverPermissionAndWait(value)
        }
        // NOTE: We could be using ActivityManager#waitForBroadcastIdle() to achieve the same thing.
        // However:
        // 1. We can't solely rely on this call to wait for the
        // ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcasts to be dispatched, as the DeviceConfig
        // listener is called on a background thread (so waitForBroadcastIdle() could immediately
        // return prior to the listener being called)
        // 2. waitForBroadcastIdle() sleeps 1s in a loop when the broadcast queue is not empty,
        // which would slow down our CTS tests significantly
    }

    private fun isEnabled() = safetyCenterManager.isSafetyCenterEnabledWithPermission()
}
