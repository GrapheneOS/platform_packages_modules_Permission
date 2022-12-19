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

import android.Manifest.permission.READ_SAFETY_CENTER_STATUS
import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.content.Context
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_STATIC
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterFlags.isSafetyCenterEnabled
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import com.google.common.util.concurrent.MoreExecutors.directExecutor

/** A class that facilitates settings up Safety Center in tests. */
class SafetyCenterCtsHelper(private val context: Context) {

    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    private val listeners = mutableListOf<SafetyCenterCtsListener>()

    /**
     * Sets up the state of Safety Center by enabling it on the device and setting default flag
     * values. To be called before each test.
     */
    fun setup() {
        SafetyCenterFlags.setup()
        setEnabled(true)
    }

    /** Resets the state of Safety Center. To be called after each test. */
    fun reset() {
        setEnabled(true)
        listeners.forEach {
            safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(it)
            it.cancel()
        }
        listeners.clear()
        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()
        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()
        resetFlags()
        SafetySourceReceiver.reset()
    }

    /** Enables or disables SafetyCenter based on [value]. */
    fun setEnabled(value: Boolean) {
        val currentValue = SafetyCenterFlags.isEnabled
        if (currentValue == value) {
            return
        }
        val safetyCenterConfig = safetyCenterManager.getSafetyCenterConfigWithPermission()
        if (safetyCenterConfig == null) {
            // No broadcasts are dispatched when toggling the flag when SafetyCenter is not
            // supported by the device.
            SafetyCenterFlags.isEnabled = value
        } else {
            setEnabledWaitingForSafetyCenterBroadcastIdle(value, safetyCenterConfig)
        }
    }

    /** Sets the given [SafetyCenterConfig]. */
    fun setConfig(config: SafetyCenterConfig) {
        require(isEnabled())
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(config)
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
            directExecutor(),
            listener
        )
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
            safetySourceId,
            safetySourceData,
            safetyEvent
        )
    }

    private fun resetFlags() {
        setEnabled(SafetyCenterFlags.snapshot.isSafetyCenterEnabled())
        SafetyCenterFlags.reset()
    }

    private fun setEnabledWaitingForSafetyCenterBroadcastIdle(
        value: Boolean,
        safetyCenterConfig: SafetyCenterConfig
    ) =
        callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE, READ_SAFETY_CENTER_STATUS) {
            val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver(context)
            SafetyCenterFlags.isEnabled = value
            // Wait for all CTS ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcasts to be dispatched to
            // avoid them leaking onto other tests.
            if (safetyCenterConfig.containsCtsSource()) {
                SafetySourceReceiver.receiveSafetyCenterEnabledChanged()
                // The explicit ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcast is also sent to the
                // dynamically registered receivers.
                enabledChangedReceiver.receiveSafetyCenterEnabledChanged()
            }
            // Wait for the implicit ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcast. This is because
            // the CTS config could later be set in another test. Since broadcasts are dispatched
            // asynchronously, a wrong sequencing could still cause failures (e.g: 1: flag switched,
            // 2: test finishes, 3: new test starts, 4: a CTS config is set, 5: broadcast from 1
            // dispatched).
            enabledChangedReceiver.receiveSafetyCenterEnabledChanged()
            enabledChangedReceiver.unregister()
            // NOTE: We could be using ActivityManager#waitForBroadcastIdle() to achieve the same
            // thing.
            // However:
            // 1. We can't solely rely on this call to wait for the
            // ACTION_SAFETY_CENTER_ENABLED_CHANGED broadcasts to be dispatched, as the DeviceConfig
            // listener is called on a background thread (so waitForBroadcastIdle() could
            // immediately return prior to the listener being called)
            // 2. waitForBroadcastIdle() sleeps 1s in a loop when the broadcast queue is not empty,
            // which would slow down our CTS tests significantly
        }

    private fun SafetyCenterConfig.containsCtsSource(): Boolean =
        safetySourcesGroups
            .flatMap { it.safetySources }
            .filter { it.type != SAFETY_SOURCE_TYPE_STATIC }
            .any { it.packageName == context.packageName }

    private fun isEnabled() = safetyCenterManager.isSafetyCenterEnabledWithPermission()
}
