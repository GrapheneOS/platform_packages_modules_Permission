/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.content.IntentFilter
import android.content.pm.PackageManager.FEATURE_AUTOMOTIVE
import android.content.pm.PackageManager.FEATURE_LEANBACK
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.CTS_SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterEnabledChangedReceiver
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.support.test.uiautomator.By
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterUnsupportedTest {
    private val context: Context = getApplicationContext()
    private val packageManager = context.packageManager
    private val safetyCenterEnabledChangedReceiver = SafetyCenterEnabledChangedReceiver()
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    @Before
    fun assumeDeviceDoesntSupportSafetyCenterToRunTests() {
        assumeFalse(context.deviceSupportsSafetyCenter())
    }

    @Test
    fun launchActivity_showsSecurityTitle() {
        // The security page redirects to the cars settings page on auto devices.
        assumeFalse(packageManager.hasSystemFeature(FEATURE_AUTOMOTIVE))
        // The security page says "Security & Restrictions" on TV.
        assumeFalse(packageManager.hasSystemFeature(FEATURE_LEANBACK))

        startSafetyCenterActivity()

        // CollapsingToolbar title can't be found by text, so using description instead.
        waitFindObject(By.desc("Security"))
    }

    @Test
    fun isSafetyCenterEnabled_withFlagEnabled_returnsFalse() {
        SafetyCenterFlags.setSafetyCenterEnabled(true)

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagDisabled_returnsFalse() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun safetyCenterEnabledChanged_withImplicitReceiver_doesntCallReceiver() {
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver()
        context.registerReceiver(
            enabledChangedReceiver, IntentFilter(ACTION_SAFETY_CENTER_ENABLED_CHANGED))

        assertFailsWith(TimeoutCancellationException::class) {
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false, TIMEOUT_SHORT)
        }
        context.unregisterReceiver(enabledChangedReceiver)
    }

    @Test
    fun safetyCenterEnabledChanged_withSourceReceiver_doesntCallReceiver() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false, TIMEOUT_SHORT)
        }
    }

    private fun startSafetyCenterActivity() {
        context.startActivity(
            Intent(ACTION_SAFETY_CENTER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
}
