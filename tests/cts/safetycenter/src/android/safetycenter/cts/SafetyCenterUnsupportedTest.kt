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
import android.content.pm.PackageManager.FEATURE_AUTOMOTIVE
import android.content.pm.PackageManager.FEATURE_LEANBACK
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterManager
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
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
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterUnsupportedTest {
    private val context: Context = getApplicationContext()
    private val packageManager = context.packageManager
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = !context.deviceSupportsSafetyCenter()

    @Before
    fun assumeDeviceDoesntSupportSafetyCenterToRunTests() {
        assumeTrue(shouldRunTests)
    }

    @Before
    fun enableSafetyCenterBeforeTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.setEnabled(true)
    }

    @After
    fun clearDataAfterTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.reset()
    }

    @Test
    fun launchActivity_showsSecurityTitle() {
        // TODO(b/232284056): Check if we can remove these test restrictions
        assumeFalse(packageManager.hasSystemFeature(FEATURE_AUTOMOTIVE))
        assumeFalse(packageManager.hasSystemFeature(FEATURE_LEANBACK))

        context.launchSafetyCenterActivity { waitFindObject(By.text("Settings")) }
    }

    @Test
    fun isSafetyCenterEnabled_withFlagEnabled_returnsFalse() {
        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagDisabled_returnsFalse() {
        safetyCenterCtsHelper.setEnabled(false)

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun safetyCenterEnabledChanged_withImplicitReceiver_doesntCallReceiver() {
        val enabledChangedReceiver = safetyCenterCtsHelper.addEnabledChangedReceiver()

        assertFailsWith(TimeoutCancellationException::class) {
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false, TIMEOUT_SHORT)
        }
    }

    @Test
    fun safetyCenterEnabledChanged_withSourceReceiver_doesntCallReceiver() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false, TIMEOUT_SHORT)
        }
    }

    @Test
    fun getSafetyCenterConfig_isNull() {
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNull()
    }
}
