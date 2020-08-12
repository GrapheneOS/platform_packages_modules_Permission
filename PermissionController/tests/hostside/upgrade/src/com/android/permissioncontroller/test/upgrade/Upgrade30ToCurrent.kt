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

package com.android.permissioncontroller.test.upgrade

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(DeviceJUnit4ClassRunner::class)
class Upgrade30ToCurrent : BaseHostJUnit4Test() {

    companion object {
        private const val FULL_REBOOT = true
        private const val PERMISSION_SDK30_PATH = "raw/runtime-permissions-30.xml"
        private const val SDK30_DEVICE_PERMISSION_DB_PATH =
                "/data/misc_de/0/apexdata/com.android.permission/runtime-permissions.xml"
        private const val CURRENT_DEVICE_PERMISSION_DB_PATH =
                "/data/misc_de/0/apexdata/com.android.permission/runtime-permissions.xml"

        var hasSimulatedUpgrade = false
    }

    @Before
    fun simulateUpgrade() {
        if (hasSimulatedUpgrade) {
            return
        }
        hasSimulatedUpgrade = true
        runDeviceSideTest("verifyPackagesAreInstalled")
        device.executeShellCommand("stop")

        // Delete the current permissions file and write the R version
        device.deleteFile(CURRENT_DEVICE_PERMISSION_DB_PATH)
        val tmpFolder = TemporaryFolder()
        tmpFolder.create()
        val tmpFile = File(tmpFolder.root, "runtime-permissions.xml")
        val input = javaClass.classLoader!!.getResourceAsStream(PERMISSION_SDK30_PATH)
        Files.copy(input, tmpFile.toPath())
        device.pushFile(tmpFile, SDK30_DEVICE_PERMISSION_DB_PATH)

        if (FULL_REBOOT) {
            device.reboot()
        } else {
            device.executeShellCommand("start")
        }
        device.waitForDeviceAvailable()

        runDeviceSideTest("verifyPackagesAreInstalled")
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdk30CameraGranted() {
        runDeviceSideTest("testFgCameraPermissionUnchanged_sdk30CameraGranted")
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdk30CameraDenied() {
        runDeviceSideTest("testFgCameraPermissionUnchanged_sdk30CameraDenied")
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdk30CameraGrantedDeclaresBg() {
        runDeviceSideTest("testFgCameraPermissionUnchanged_sdk30CameraGrantedDeclaresBg")
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdk30CameraDeniedDeclaresBg() {
        runDeviceSideTest("testFgCameraPermissionUnchanged_sdk30CameraDeniedDeclaresBg")
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdkCurrentCameraGranted() {
        runDeviceSideTest("testFgCameraPermissionUnchanged_sdkCurrentCameraGranted")
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdkCurrentCameraDenied() {
        runDeviceSideTest("testFgCameraPermissionUnchanged_sdkCurrentCameraDenied")
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdkCurrentCameraGrantedDeclaresBg() {
        runDeviceSideTest("testFgCameraPermissionUnchanged_sdkCurrentCameraGrantedDeclaresBg")
    }

    @Test
    fun testFgCameraPermissionUnchanged_sdkCurrentCameraDeniedDeclaresBg() {
        runDeviceSideTest("testFgCameraPermissionUnchanged_sdkCurrentCameraDeniedDeclaresBg")
    }

    @Test
    fun testPackagesRequestBgCamera_sdk30CameraGranted() {
        runDeviceSideTest("testPackagesRequestBgCamera_sdk30CameraGranted")
    }

    @Test
    fun testPackagesRequestBgCamera_sdk30CameraDenied() {
        runDeviceSideTest("testPackagesRequestBgCamera_sdk30CameraDenied")
    }

    @Test
    fun testPackagesRequestBgCamera_sdk30CameraGrantedDeclaresBg() {
        runDeviceSideTest("testPackagesRequestBgCamera_sdk30CameraGrantedDeclaresBg")
    }

    @Test
    fun testPackagesRequestBgCamera_sdk30CameraDeniedDeclaresBg() {
        runDeviceSideTest("testPackagesRequestBgCamera_sdk30CameraDeniedDeclaresBg")
    }

    @Test
    fun testPackagesRequestBgCamera_sdkCurrentCameraGranted() {
        runDeviceSideTest("testPackagesRequestBgCamera_sdkCurrentCameraGranted")
    }

    @Test
    fun testPackagesRequestBgCamera_sdkCurrentCameraDenied() {
        runDeviceSideTest("testPackagesRequestBgCamera_sdkCurrentCameraDenied")
    }

    @Test
    fun testPackagesRequestBgCamera_sdkCurrentCameraGrantedDeclaresBg() {
        runDeviceSideTest("testPackagesRequestBgCamera_sdkCurrentCameraGrantedDeclaresBg")
    }

    @Test
    fun testPackagesRequestBgCamera_sdkCurrentCameraDeniedDeclaresBg() {
        runDeviceSideTest("testPackagesRequestBgCamera_sdkCurrentCameraDeniedDeclaresBg")
    }

    @Test
    fun testBgCameraRestrictionApplied_sdk30CameraGranted() {
        runDeviceSideTest("testBgCameraRestrictionApplied_sdk30CameraGranted")
    }

    @Test
    fun testBgCameraRestrictionApplied_sdk30CameraDenied() {
        runDeviceSideTest("testBgCameraRestrictionApplied_sdk30CameraDenied")
    }

    @Test
    fun testBgCameraRestrictionApplied_sdk30CameraGrantedDeclaresBg() {
        runDeviceSideTest("testBgCameraRestrictionApplied_sdk30CameraGrantedDeclaresBg")
    }

    @Test
    fun testBgCameraRestrictionApplied_sdk30CameraDeniedDeclaresBg() {
        runDeviceSideTest("testBgCameraRestrictionApplied_sdk30CameraDeniedDeclaresBg")
    }

    /* Removed packages targeting CURRENT only declaring fg */

    @Test
    fun testBgCameraRestrictionApplied_sdkCurrentCameraGrantedDeclaresBg() {
        runDeviceSideTest("testBgCameraRestrictionApplied_sdkCurrentCameraGrantedDeclaresBg")
    }

    @Test
    fun testBgCameraRestrictionApplied_sdkCurrentCameraDeniedDeclaresBg() {
        runDeviceSideTest("testBgCameraRestrictionApplied_sdkCurrentCameraDeniedDeclaresBg")
    }

    @Test
    fun testBgCameraIsNotExempt_sdk30CameraGranted() {
        runDeviceSideTest("testBgCameraIsNotExempt_sdk30CameraGranted")
    }

    @Test
    fun testBgCameraIsNotExempt_sdk30CameraDenied() {
        runDeviceSideTest("testBgCameraIsNotExempt_sdk30CameraDenied")
    }

    @Test
    fun testBgCameraIsNotExempt_sdk30CameraGrantedDeclaresBg() {
        runDeviceSideTest("testBgCameraIsNotExempt_sdk30CameraGrantedDeclaresBg")
    }

    @Test
    fun testBgCameraIsNotExempt_sdk30CameraDeniedDeclaresBg() {
        runDeviceSideTest("testBgCameraIsNotExempt_sdk30CameraDeniedDeclaresBg")
    }

    /* Removed packages targeting CURRENT only declaring fg */

    @Test
    fun testBgCameraIsNotExempt_sdkCurrentCameraGrantedDeclaresBg() {
        runDeviceSideTest("testBgCameraIsNotExempt_sdkCurrentCameraGrantedDeclaresBg")
    }

    @Test
    fun testBgCameraIsNotExempt_sdkCurrentCameraDeniedDeclaresBg() {
        runDeviceSideTest("testBgCameraIsNotExempt_sdkCurrentCameraDeniedDeclaresBg")
    }

    private fun runDeviceSideTest(method: String) {
        runDeviceTests("com.android.permissioncontroller.test.upgrade.deviceside",
                "com.android.permissioncontroller.test.upgrade.deviceside.DeviceSide", method)
    }
}