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

package android.safetycenter.hostside

import android.cts.statsdatom.lib.DeviceUtils
import com.android.tradefed.device.ITestDevice
import com.android.tradefed.util.CommandStatus
import java.io.IOException

/** Runs a test in the Safety Center hostside test helper app */
internal fun ITestDevice.runTest(testClassName: String, testMethodName: String) {
    DeviceUtils.runDeviceTests(this, HelperApp.PACKAGE_NAME, testClassName, testMethodName)
}

/** Checks whether this [ITestDevice] supports Safety Center. */
internal fun ITestDevice.supportsSafetyCenter(): Boolean =
    executeShellCommandOrThrow("cmd safety_center supported").toBoolean()

/** Checks whether Safety Center is currently enabled on this [ITestDevice]. */
internal fun ITestDevice.isSafetyCenterEnabled(): Boolean =
    executeShellCommandOrThrow("cmd safety_center enabled").toBoolean()

/** Returns the package name of Safety Center on this [ITestDevice]. */
internal fun ITestDevice.getSafetyCenterPackageName(): String =
    executeShellCommandOrThrow("cmd safety_center package-name")

private fun ITestDevice.executeShellCommandOrThrow(command: String): String {
    val result = executeShellV2Command(command)
    if (result.status != CommandStatus.SUCCESS) {
        throw IOException("$command exited with status ${result.exitCode}")
    }
    return result.stdout.trim()
}
