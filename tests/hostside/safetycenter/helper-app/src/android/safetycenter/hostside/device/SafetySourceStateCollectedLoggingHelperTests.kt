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
import com.android.compatibility.common.util.SystemUtil
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetySourceTestData
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetySourceStateCollectedLoggingHelperTests {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)

    @Before
    fun setUp() {
        safetyCenterTestHelper.setup()
        SafetyCenterFlags.allowStatsdLogging = true
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
    }

    @After
    fun tearDown() {
        safetyCenterTestHelper.reset()
    }

    @Test
    fun triggerStatsPull() {
        val label = 1 // Arbitrary label in [0, 16)
        val state = 3 // START
        val command = "cmd stats log-app-breadcrumb $label $state"
        SystemUtil.runShellCommandOrThrow(command)
    }

    @Test
    fun setSafetySourceData_source1() {
        safetyCenterTestHelper.setData(SOURCE_ID_1, safetySourceTestData.information)
    }
}
