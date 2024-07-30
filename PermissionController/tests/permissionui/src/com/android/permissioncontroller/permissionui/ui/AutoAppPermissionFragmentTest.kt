/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.permissioncontroller.permissionui.ui

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.os.UserHandle
import android.os.UserHandle.myUserId
import android.permission.cts.PermissionUtils
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.revokePermission
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObjectOrNull
import com.android.compatibility.common.util.UiAutomatorUtils2.waitUntilObjectGone
import com.android.modules.utils.build.SdkLevel
import com.android.permission.flags.Flags
import com.android.permissioncontroller.permissionui.wakeUpScreen
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val LOCATION = "Location"
private const val PERMISSION_REQUEST_APP = "PermissionRequestApp"
private const val SWITCH_VIEW = "Use precise location"
private const val LOG_TAG = "AppPermissionFragmentTest"
private const val DONT_ALLOW = "Don\u2019t allow"
private const val ALLOW_ONLY = "Allow only while using the app"

/**
 * Simple tests for {@link AutoAppPermissionFragment}
 *
 * Run with: atest AutoAppPermissionFragmentTest
 */
@RunWith(AndroidJUnit4::class)
class AutoAppPermissionFragmentTest : BasePermissionUiTest() {

    private val USER_PKG_V2 = "android.permission.cts.appthatrequestpermission"

    @JvmField @Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before fun assumeIsAuto() = assumeTrue(isAutomotive)

    @Before
    fun setup() {
        wakeUpScreen()
        assumeTrue("Location Accuracy is only available on S+", SdkLevel.isAtLeastS())
        installTestAppThatRequestsFineLocation()
        grantPermission(USER_PKG_V2, ACCESS_FINE_LOCATION)
        grantPermission(USER_PKG_V2, ACCESS_COARSE_LOCATION)
    }

    private fun startManagePermissionAppsActivity() {
        runWithShellPermissionIdentity {
            instrumentationContext.startActivity(
                Intent(Intent.ACTION_MANAGE_APP_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(Intent.EXTRA_PACKAGE_NAME, USER_PKG_V2)
                    putExtra(Intent.EXTRA_PERMISSION_NAME, ACCESS_FINE_LOCATION)
                    putExtra(Intent.EXTRA_USER, UserHandle.of(myUserId()))
                }
            )
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_COARSE_FINE_LOCATION_PROMPT_FOR_AAOS)
    fun whenSwitchViewOffCoarseLocationGranted() {
        // Pre: Assert switch view exists and fine location granted
        // Test: Find and toggle switch view
        // Post-Condition: Assert that coarse location granted

        startManagePermissionAppsActivity()

        // confirm switch view exists and is currently on
        assertNotNull(waitFindObjectOrNull(By.text(SWITCH_VIEW)))
        assertTrue(PermissionUtils.isPermissionGranted(USER_PKG_V2, ACCESS_FINE_LOCATION))

        // turn off switch view and confirm functionality
        waitFindObject(By.text(SWITCH_VIEW)).click()
        assertFalse(PermissionUtils.isPermissionGranted(USER_PKG_V2, ACCESS_FINE_LOCATION))
        assertTrue(PermissionUtils.isPermissionGranted(USER_PKG_V2, ACCESS_COARSE_LOCATION))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_COARSE_FINE_LOCATION_PROMPT_FOR_AAOS)
    fun whenSwitchViewOnFineLocationGranted() {
        // Pre: Assert switch view exists and fine location not granted
        // Test: Find and toggle switch view
        // Post-Condition: Assert that fine location granted

        revokePermission(USER_PKG_V2, ACCESS_FINE_LOCATION)
        startManagePermissionAppsActivity()

        // confirm switch view exists and is currently off
        assertNotNull(waitFindObjectOrNull(By.text(SWITCH_VIEW)))
        assertFalse(PermissionUtils.isPermissionGranted(USER_PKG_V2, ACCESS_FINE_LOCATION))

        // turn on switch view and confirm functionality
        waitFindObject(By.text(SWITCH_VIEW)).click()
        assertTrue(PermissionUtils.isPermissionGranted(USER_PKG_V2, ACCESS_FINE_LOCATION))
        assertTrue(PermissionUtils.isPermissionGranted(USER_PKG_V2, ACCESS_COARSE_LOCATION))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_COARSE_FINE_LOCATION_PROMPT_FOR_AAOS)
    fun whenLocationPermissionsDeniedSwitchViewGone() {
        // Pre: Assert switch view visible
        // Test: Click "Don't Allow"
        // Post-Condition: Assert switch view is not visible

        startManagePermissionAppsActivity()

        // confirm switch view exists
        assertNotNull(waitFindObjectOrNull(By.text(SWITCH_VIEW)))

        // turn off location permissions and confirm functionality
        waitFindObject(By.text(DONT_ALLOW)).click()
        assertNull(waitFindObjectOrNull(By.text(SWITCH_VIEW)))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_COARSE_FINE_LOCATION_PROMPT_FOR_AAOS)
    fun whenLocationPermissionsGrantedSwitchViewVisible() {
        // Test: Click "Allow always"
        // Post-Condition: Assert switch view is visible

        revokePermission(USER_PKG_V2, ACCESS_FINE_LOCATION)
        revokePermission(USER_PKG_V2, ACCESS_COARSE_LOCATION)
        startManagePermissionAppsActivity()

        // confirm switch view doesn't exist
        assertNull(waitFindObjectOrNull(By.text(SWITCH_VIEW)))

        // allow location permissions and confirm functionality
        waitFindObject(By.text(ALLOW_ONLY)).click()
        assertNotNull(waitFindObjectOrNull(By.text(SWITCH_VIEW)))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_COARSE_FINE_LOCATION_PROMPT_FOR_AAOS)
    fun whenFlaggedTurnedOnSwitchViewVisible() {
        startManagePermissionAppsActivity()
        assertNotNull(waitFindObjectOrNull(By.text(SWITCH_VIEW)))
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_COARSE_FINE_LOCATION_PROMPT_FOR_AAOS)
    fun whenFlaggedTurnedOffSwitchViewNotVisible() {
        startManagePermissionAppsActivity()
        waitUntilObjectGone(By.text(SWITCH_VIEW))
        assertNull(waitFindObjectOrNull(By.text(SWITCH_VIEW)))
    }

    @After
    fun uninstallTestApp() {
        uninstallTestApps()
    }
}
