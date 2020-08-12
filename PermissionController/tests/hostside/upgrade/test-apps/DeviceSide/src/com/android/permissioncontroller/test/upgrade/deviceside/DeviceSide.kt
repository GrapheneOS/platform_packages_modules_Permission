/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.test.upgrade.deviceside

import android.Manifest.permission.BACKGROUND_CAMERA
import android.Manifest.permission.CAMERA
import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_APPLY_RESTRICTION
import android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT
import android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT
import android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Process
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSide {
    companion object {
        private const val PKG_30_CAMERA_GRANTED =
                "com.android.permission.test.upgrade.sdk_30.camera_granted"
        private const val PKG_30_CAMERA_DENIED =
                "com.android.permission.test.upgrade.sdk_30.camera_denied"

        private const val PKG_30_CAMERA_GRANTED_DECLARES_BG =
                "com.android.permission.test.upgrade.sdk_30.camera_granted_declares_bg"
        private const val PKG_30_CAMERA_DENIED_DECLARES_BG =
                "com.android.permission.test.upgrade.sdk_30.camera_denied_declares_bg"

        private const val PKG_CURRENT_CAMERA_GRANTED =
                "com.android.permission.test.upgrade.sdk_current.camera_granted"
        private const val PKG_CURRENT_CAMERA_DENIED =
                "com.android.permission.test.upgrade.sdk_current.camera_denied"

        private const val PKG_CURRENT_CAMERA_GRANTED_DECLARES_BG =
                "com.android.permission.test.upgrade.sdk_current.camera_granted_declares_bg"
        private const val PKG_CURRENT_CAMERA_DENIED_DECLARES_BG =
                "com.android.permission.test.upgrade.sdk_current.camera_denied_declares_bg"

        private const val ALL_PERMISSION_EXEMPT_FLAGS =
                FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT or
                        FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT or
                        FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT
    }

    lateinit var uiautomation: UiAutomation
    lateinit var context: Context
    lateinit var pm: PackageManager
    lateinit var user: UserHandle

    @Before
    fun init() {
        uiautomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        context = InstrumentationRegistry.getInstrumentation().context
        pm = context.packageManager
        user = Process.myUserHandle()
    }

    /**
     * Assert all packages are installed
     */
    @Test
    fun verifyPackagesAreInstalled() {
        // Don't catch any NameNotFound exceptions
        pm.getPackageInfo(PKG_30_CAMERA_GRANTED, 0)
        pm.getPackageInfo(PKG_30_CAMERA_DENIED, 0)

        pm.getPackageInfo(PKG_30_CAMERA_GRANTED_DECLARES_BG, 0)
        pm.getPackageInfo(PKG_30_CAMERA_DENIED_DECLARES_BG, 0)

        pm.getPackageInfo(PKG_CURRENT_CAMERA_GRANTED, 0)
        pm.getPackageInfo(PKG_CURRENT_CAMERA_DENIED, 0)

        pm.getPackageInfo(PKG_CURRENT_CAMERA_GRANTED_DECLARES_BG, 0)
        pm.getPackageInfo(PKG_CURRENT_CAMERA_DENIED_DECLARES_BG, 0)
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdk30CameraGranted() {
        assertThat(pm.checkPermission(CAMERA, PKG_30_CAMERA_GRANTED))
                .isEqualTo(PERMISSION_GRANTED)
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdk30CameraDenied() {
        assertThat(pm.checkPermission(CAMERA, PKG_30_CAMERA_DENIED))
                .isEqualTo(PERMISSION_DENIED)
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdk30CameraGrantedDeclaresBg() {
        assertThat(pm.checkPermission(CAMERA, PKG_30_CAMERA_GRANTED_DECLARES_BG))
                .isEqualTo(PERMISSION_GRANTED)
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdk30CameraDeniedDeclaresBg() {
        assertThat(pm.checkPermission(CAMERA, PKG_30_CAMERA_DENIED_DECLARES_BG))
                .isEqualTo(PERMISSION_DENIED)
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdkCurrentCameraGranted() {
        assertThat(pm.checkPermission(CAMERA, PKG_CURRENT_CAMERA_GRANTED))
                .isEqualTo(PERMISSION_GRANTED)
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdkCurrentCameraDenied() {
        assertThat(pm.checkPermission(CAMERA, PKG_CURRENT_CAMERA_DENIED))
                .isEqualTo(PERMISSION_DENIED)
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdkCurrentCameraGrantedDeclaresBg() {
        assertThat(pm.checkPermission(CAMERA, PKG_CURRENT_CAMERA_GRANTED_DECLARES_BG))
                .isEqualTo(PERMISSION_GRANTED)
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdkCurrentCameraDeniedDeclaresBg() {
        assertThat(pm.checkPermission(CAMERA, PKG_CURRENT_CAMERA_DENIED_DECLARES_BG))
                .isEqualTo(PERMISSION_DENIED)
    }

    @Test
    fun testPackagesRequestBgCamera_sdk30CameraGranted() {
        assertThat(pm.getPackageInfo(PKG_30_CAMERA_GRANTED, GET_PERMISSIONS)
                .requestedPermissions.contains(BACKGROUND_CAMERA)).isEqualTo(true)
    }

    @Test
    fun testPackagesRequestBgCamera_sdk30CameraDenied() {
        assertThat(pm.getPackageInfo(PKG_30_CAMERA_DENIED, GET_PERMISSIONS)
                .requestedPermissions.contains(BACKGROUND_CAMERA)).isEqualTo(true)
    }

    @Test
    fun testPackagesRequestBgCamera_sdk30CameraGrantedDeclaresBg() {
        assertThat(pm.getPackageInfo(PKG_30_CAMERA_GRANTED_DECLARES_BG, GET_PERMISSIONS)
                .requestedPermissions.contains(BACKGROUND_CAMERA)).isEqualTo(true)
    }

    @Test
    fun testPackagesRequestBgCamera_sdk30CameraDeniedDeclaresBg() {
        assertThat(pm.getPackageInfo(PKG_30_CAMERA_DENIED_DECLARES_BG, GET_PERMISSIONS)
                .requestedPermissions.contains(BACKGROUND_CAMERA)).isEqualTo(true)
    }

    @Test
    fun testPackagesRequestBgCamera_sdkCurrentCameraGranted() {
        assertThat(pm.getPackageInfo(PKG_CURRENT_CAMERA_GRANTED, GET_PERMISSIONS)
                .requestedPermissions.contains(BACKGROUND_CAMERA)).isEqualTo(false)
    }

    @Test
    fun testPackagesRequestBgCamera_sdkCurrentCameraDenied() {
        assertThat(pm.getPackageInfo(PKG_CURRENT_CAMERA_DENIED, GET_PERMISSIONS)
                .requestedPermissions.contains(BACKGROUND_CAMERA)).isEqualTo(false)
    }

    @Test
    fun testPackagesRequestBgCamera_sdkCurrentCameraGrantedDeclaresBg() {
        assertThat(pm.getPackageInfo(PKG_CURRENT_CAMERA_GRANTED_DECLARES_BG, GET_PERMISSIONS)
                .requestedPermissions.contains(BACKGROUND_CAMERA)).isEqualTo(true)
    }

    @Test
    fun testPackagesRequestBgCamera_sdkCurrentCameraDeniedDeclaresBg() {
        assertThat(pm.getPackageInfo(PKG_CURRENT_CAMERA_DENIED_DECLARES_BG, GET_PERMISSIONS)
                .requestedPermissions.contains(BACKGROUND_CAMERA)).isEqualTo(true)
    }

    @Test
    fun testBgCameraRestrictionApplied_sdk30CameraGranted() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_30_CAMERA_GRANTED, user) and
                    FLAG_PERMISSION_APPLY_RESTRICTION).isNotEqualTo(0)
        }
    }

    @Test
    fun testBgCameraRestrictionApplied_sdk30CameraDenied() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_30_CAMERA_DENIED, user) and
                    FLAG_PERMISSION_APPLY_RESTRICTION).isNotEqualTo(0)
        }
    }

    @Test
    fun testBgCameraRestrictionApplied_sdk30CameraGrantedDeclaresBg() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_30_CAMERA_GRANTED_DECLARES_BG, user) and
                    FLAG_PERMISSION_APPLY_RESTRICTION).isNotEqualTo(0)
        }
    }

    @Test
    fun testBgCameraRestrictionApplied_sdk30CameraDeniedDeclaresBg() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_30_CAMERA_DENIED_DECLARES_BG, user) and
                    FLAG_PERMISSION_APPLY_RESTRICTION).isNotEqualTo(0)
        }
    }

    /* Removed packages targeting CURRENT only declaring fg */

    @Test
    fun testBgCameraRestrictionApplied_sdkCurrentCameraGrantedDeclaresBg() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_CURRENT_CAMERA_GRANTED_DECLARES_BG, user) and
                    FLAG_PERMISSION_APPLY_RESTRICTION).isNotEqualTo(0)
        }
    }

    @Test
    fun testBgCameraRestrictionApplied_sdkCurrentCameraDeniedDeclaresBg() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_CURRENT_CAMERA_DENIED_DECLARES_BG, user) and
                    FLAG_PERMISSION_APPLY_RESTRICTION).isNotEqualTo(0)
        }
    }

    @Test
    fun testBgCameraIsNotExempt_sdk30CameraGranted() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_30_CAMERA_GRANTED, user) and
                    ALL_PERMISSION_EXEMPT_FLAGS).isEqualTo(0)
        }
    }

    @Test
    fun testBgCameraIsNotExempt_sdk30CameraDenied() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_30_CAMERA_DENIED, user) and
                    ALL_PERMISSION_EXEMPT_FLAGS).isEqualTo(0)
        }
    }

    @Test
    fun testBgCameraIsNotExempt_sdk30CameraGrantedDeclaresBg() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_30_CAMERA_GRANTED_DECLARES_BG, user) and
                    ALL_PERMISSION_EXEMPT_FLAGS).isEqualTo(0)
        }
    }

    @Test
    fun testBgCameraIsNotExempt_sdk30CameraDeniedDeclaresBg() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_30_CAMERA_DENIED_DECLARES_BG, user) and
                    ALL_PERMISSION_EXEMPT_FLAGS).isEqualTo(0)
        }
    }

    /* Removed packages targeting CURRENT only declaring fg */

    @Test
    fun testBgCameraIsNotExempt_sdkCurrentCameraGrantedDeclaresBg() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_CURRENT_CAMERA_GRANTED_DECLARES_BG, user) and
                    ALL_PERMISSION_EXEMPT_FLAGS).isEqualTo(0)
        }
    }

    @Test
    fun testBgCameraIsNotExempt_sdkCurrentCameraDeniedDeclaresBg() {
        runWithShellPermissionIdentity {
            assertThat(pm.getPermissionFlags(BACKGROUND_CAMERA,
                    PKG_CURRENT_CAMERA_DENIED_DECLARES_BG, user) and
                    ALL_PERMISSION_EXEMPT_FLAGS).isEqualTo(0)
        }
    }

    fun runWithShellPermissionIdentity(r: () -> Unit) {
        try {
            uiautomation.adoptShellPermissionIdentity()
            r()
        } finally {
            uiautomation.dropShellPermissionIdentity()
        }
    }
}
