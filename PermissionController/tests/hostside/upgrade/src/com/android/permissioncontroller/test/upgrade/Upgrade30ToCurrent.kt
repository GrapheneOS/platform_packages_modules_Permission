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
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(DeviceJUnit4ClassRunner::class)
class Upgrade30ToCurrent : BaseHostJUnit4Test() {

    companion object {
        private const val FULL_REBOOT = false
        private const val PERMISSION_SDK30_ASSISTANT_MIC_GRANTED_PATH =
                "raw/runtime-permissions-30_AssistantMicGranted.xml"
        private const val PERMISSION_SDK30_ASSISTANT_MIC_DENIED_PATH =
                "raw/runtime-permissions-30_AssistantMicDenied.xml"
        private const val PERMISSION_SDK30_ROLES_PATH = "raw/roles-30.xml"

        private const val SDK30_DEVICE_PERMISSION_DB_PATH =
                "/data/misc_de/0/apexdata/com.android.permission/runtime-permissions.xml"
        private const val CURRENT_DEVICE_PERMISSION_DB_PATH =
                "/data/misc_de/0/apexdata/com.android.permission/runtime-permissions.xml"

        private const val SDK30_DEVICE_ROLES_DB_PATH =
                "/data/misc_de/0/apexdata/com.android.permission/roles.xml"
        private const val CURRENT_DEVICE_ROLES_DB_PATH =
                "/data/misc_de/0/apexdata/com.android.permission/roles.xml"
    }

    @Test
    fun testUpgrade1() {
        simulateUpgrade(PERMISSION_SDK30_ASSISTANT_MIC_GRANTED_PATH, PERMISSION_SDK30_ROLES_PATH)

        runDeviceSideTest("testUpgrade1")
    }

    @Test
    fun testUpgrade2() {
        simulateUpgrade(PERMISSION_SDK30_ASSISTANT_MIC_DENIED_PATH, PERMISSION_SDK30_ROLES_PATH)

        runDeviceSideTest("testUpgrade2")
    }

    fun simulateUpgrade(permissionsPath: String, rolesPath: String) {
        runDeviceSideTest("verifyPackagesAreInstalled")
        device.executeShellCommand("stop")

        // Delete the current permissions file and write the R version
        device.deleteFile(CURRENT_DEVICE_PERMISSION_DB_PATH)
        device.deleteFile(CURRENT_DEVICE_ROLES_DB_PATH)
        val tmpFolder = TemporaryFolder()
        tmpFolder.create()

        var tmpFile = File(tmpFolder.root, "runtime-permissions.xml")
        var input = javaClass.classLoader!!.getResourceAsStream(permissionsPath)
        Files.copy(input, tmpFile.toPath())
        device.pushFile(tmpFile, SDK30_DEVICE_PERMISSION_DB_PATH)

        tmpFile = File(tmpFolder.root, "roles.xml")
        input = javaClass.classLoader!!.getResourceAsStream(rolesPath)
        Files.copy(input, tmpFile.toPath())
        device.pushFile(tmpFile, SDK30_DEVICE_ROLES_DB_PATH)

        if (FULL_REBOOT) {
            device.reboot()
        } else {
            device.executeShellCommand("start")
        }
        device.waitForDeviceAvailable()

        runDeviceSideTest("verifyPackagesAreInstalled")
    }

    private fun runDeviceSideTest(method: String) {
        runDeviceTests("com.android.permissioncontroller.test.upgrade.deviceside",
                "com.android.permissioncontroller.test.upgrade.deviceside.DeviceSide", method)
    }
}