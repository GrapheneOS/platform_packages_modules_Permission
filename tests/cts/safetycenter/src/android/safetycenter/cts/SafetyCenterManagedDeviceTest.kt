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

import android.Manifest.permission.INJECT_EVENTS
import android.content.Context
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.support.test.uiautomator.By
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasPermission
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Ignore
// TODO(b/234108780): enable when we figure a way to make sure these don't fail due to timeout error
@RunWith(BedsteadJUnit4::class)
class SafetyCenterManagedDeviceTest {

    companion object {
        @JvmField @ClassRule @Rule val deviceState: DeviceState = DeviceState()
    }
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = context.deviceSupportsSafetyCenter()

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
    @EnsureHasWorkProfile
    @EnsureHasPermission(INJECT_EVENTS)
    fun launchActivity_displayWorkProfile_profileOwner() {
        context.launchSafetyCenterActivity {
            // TODO(b/233188021): This test will fail if these strings are overridden by OEMS.
            findAllText("Your work policy info", "Settings managed by your IT admin")
        }
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasPermission(INJECT_EVENTS)
    fun launchActivity_displayWorkProfile_deviceOwner() {
        context.launchSafetyCenterActivity {
            // TODO(b/233188021): This test will fail if these strings are overridden by OEMS.
            findAllText("Your work policy info", "Settings managed by your IT admin")
        }
    }

    // TODO(b/228823159): Extract this method to reduce code duplication
    private fun findAllText(vararg textToFind: CharSequence?) {
        for (text in textToFind) if (text != null) waitFindObject(By.text(text.toString()))
    }
}
