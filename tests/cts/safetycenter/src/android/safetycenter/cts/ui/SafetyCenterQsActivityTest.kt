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

package android.safetycenter.cts.ui

import android.Manifest.permission.MANAGE_SENSOR_PRIVACY
import android.Manifest.permission.OBSERVE_SENSOR_PRIVACY
import android.content.Context
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.SensorPrivacyManager.Sensors.MICROPHONE
import android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterQsActivity
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import android.support.test.uiautomator.By
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for the Safety Center Quick Settings Activity. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterQsActivityTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val sensorPrivacyManager = context.getSystemService(SensorPrivacyManager::class.java)!!
    private var shouldRunTests =
        context.deviceSupportsSafetyCenter() &&
            deviceSupportsSensorToggle(CAMERA) &&
            deviceSupportsSensorToggle(MICROPHONE)
    private var oldCameraState: Boolean = false
    private var oldMicrophoneState: Boolean = false

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(shouldRunTests)
    }

    @Before
    fun enableSafetyCenterBeforeTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.setup()
    }

    @After
    fun clearDataAfterTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.reset()
    }

    @Before
    fun enablePrivacyControlsBeforeTest() {
        if (!shouldRunTests) {
            return
        }
        oldCameraState = isSensorEnabled(CAMERA)
        setSensorState(CAMERA, true)

        oldMicrophoneState = isSensorEnabled(MICROPHONE)
        setSensorState(MICROPHONE, true)
    }

    @After
    fun restorePrivacyControlsAfterTest() {
        if (!shouldRunTests) {
            return
        }
        setSensorState(CAMERA, oldCameraState)
        setSensorState(MICROPHONE, oldMicrophoneState)
    }

    @Test
    fun launchActivity_fromQuickSettings_hasContentDescriptions() {
        context.launchSafetyCenterQsActivity() {
            // Verify page landing descriptions
            waitFindObject(By.desc("Security and privacy quick settings"))
            waitFindObject(By.text("Your privacy controls"))
            waitFindObject(By.desc("Close"))

            // Verify privacy controls descriptions
            waitFindObject(By.desc("Switch. Camera access. Available"))
            waitFindObject(By.desc("Switch. Mic access. Available"))
        }
    }

    @Test
    fun launchActivity_togglePrivacyControls_hasUpdatedDescriptions() {
        context.launchSafetyCenterQsActivity() {
            // Toggle privacy controls
            waitFindObject(By.desc("Switch. Camera access. Available")).click()
            waitFindObject(By.desc("Switch. Mic access. Available")).click()

            // Verify updated state of privacy controls
            waitFindObject(By.desc("Switch. Camera access. Blocked"))
            waitFindObject(By.desc("Switch. Mic access. Blocked"))
        }
    }

    private fun deviceSupportsSensorToggle(sensor: Int): Boolean {
        return sensorPrivacyManager.supportsSensorToggle(sensor) &&
            sensorPrivacyManager.supportsSensorToggle(TOGGLE_TYPE_SOFTWARE, sensor)
    }

    private fun isSensorEnabled(sensor: Int): Boolean {
        val isSensorDisabled = callWithShellPermissionIdentity(
            { sensorPrivacyManager.isSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE, sensor) },
            OBSERVE_SENSOR_PRIVACY)
        return !isSensorDisabled
    }

    private fun setSensorState(sensor: Int, enabled: Boolean) {
        val disableSensor = !enabled
        // The sensor is enabled iff the privacy control is disabled.
        callWithShellPermissionIdentity(
            { sensorPrivacyManager.setSensorPrivacy(sensor, disableSensor) },
            MANAGE_SENSOR_PRIVACY,
            OBSERVE_SENSOR_PRIVACY)
    }
}
