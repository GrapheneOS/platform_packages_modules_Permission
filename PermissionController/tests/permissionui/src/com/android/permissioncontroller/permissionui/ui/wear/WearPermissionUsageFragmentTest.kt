/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permissionui.ui.wear

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.permissioncontroller.permissionui.PermissionHub2Test
import com.android.permissioncontroller.permissionui.pressHome
import com.android.permissioncontroller.permissionui.wakeUpScreen
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [WearPermissionUsageFragment] */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class WearPermissionUsageFragmentTest : PermissionHub2Test() {
    private val SHOW_SYSTEM_LABEL = "Show system"
    private val HIDE_SYSTEM_LABEL = "Hide system"
    private val TIMEOUT = 30_000L

    @Before
    fun setup() {
        assumeTrue(isWear())
        wakeUpScreen()
    }

    @Test
    fun testShowSystem() {
        runWithShellPermissionIdentity {
            context.startActivity(
                Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        eventually {
            try {
                waitFindObject(By.textContains(SHOW_SYSTEM_LABEL), TIMEOUT).click()
            } catch (e: Exception) {
                throw e
            }
        }

        eventually {
            try {
                waitFindObject(By.textContains(HIDE_SYSTEM_LABEL), TIMEOUT).click()
            } catch (e: Exception) {
                throw e
            }
        }

        waitFindObject(By.textContains(SHOW_SYSTEM_LABEL), TIMEOUT)
    }

    @After
    fun tearDown() {
        pressHome()
    }
}
