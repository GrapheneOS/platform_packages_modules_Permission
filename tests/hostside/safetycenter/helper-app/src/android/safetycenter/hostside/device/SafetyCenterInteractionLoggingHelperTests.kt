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

package android.safetycenter.hostside.device

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Contains "helper tests" that perform on-device setup and interaction code for Safety Center's
 * interaction logging host-side tests. These "helper tests" just perform arrange and act steps and
 * should not contain assertions. Assertions are performed in the host-side tests that run these
 * helper tests.
 *
 * Some context: host-side tests are unable to interact with the device UI in a detailed manner, and
 * must run "helper tests" on the device to perform in-depth interactions or setup that must be
 * performed by code running on the device.
 */
@RunWith(AndroidJUnit4::class)
class SafetyCenterInteractionLoggingHelperTests {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun openSafetyCenter() {
        context.launchSafetyCenterActivity {}
    }
}
