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

package android.permissionmultidevice.cts

import com.android.compatibility.common.util.SystemUtil
import com.android.modules.utils.build.SdkLevel
import org.junit.Assert

object PackageManagementUtils {
    fun installPackage(
        apkPath: String,
        reinstall: Boolean = false,
        grantRuntimePermissions: Boolean = false,
        expectSuccess: Boolean = true,
        installSource: String? = null
    ) {
        val output =
            SystemUtil.runShellCommandOrThrow(
                    "pm install${if (SdkLevel.isAtLeastU()) " --bypass-low-target-sdk-block" else ""} " +
                        "${if (reinstall) " -r" else ""}${
                        if (grantRuntimePermissions) " -g"
                        else ""
                    }${if (installSource != null) " -i $installSource" else ""} $apkPath"
                )
                .trim()

        if (expectSuccess) {
            Assert.assertEquals("Success", output)
        } else {
            Assert.assertNotEquals("Success", output)
        }
    }

    fun uninstallPackage(packageName: String, requireSuccess: Boolean = true) {
        val output = SystemUtil.runShellCommand("pm uninstall $packageName").trim()
        if (requireSuccess) {
            Assert.assertEquals("Success", output)
        }
    }
}
