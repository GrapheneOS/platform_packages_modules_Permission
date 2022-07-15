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

import android.content.Context
import android.os.Bundle
import android.permission.PermissionGroupUsage
import android.permission.PermissionManager
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterQsActivity
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
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
    private val shouldRunTests = context.deviceSupportsSafetyCenter()

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

    @Test
    fun launchActivity_fromQuickSettings_hasContentDescriptions() {
        val bundle = Bundle()
        bundle.putParcelableArrayList(
            PermissionManager.EXTRA_PERMISSION_USAGES, ArrayList<PermissionGroupUsage>())
        context.launchSafetyCenterQsActivity(bundle) {
            // Verify page landing descriptions
            waitFindObject(By.desc("Security and privacy quick settings"))
            waitFindObject(By.desc("Close"))

            // Verify privacy controls descriptions
            waitFindObject(By.desc("Switch. Camera access. Available"))
            waitFindObject(By.desc("Switch. Mic access. Available"))
            waitFindObject(By.desc("Switch. Location. Available"))
        }
    }
}
