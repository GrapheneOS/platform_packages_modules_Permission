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

package android.permission.cts

import android.os.Build
import android.permission.flags.Flags.FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@AppModeFull(reason = "Instant apps cannot install packages")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
class RecordSensitiveContentPermissionTest {
    @Rule
    @JvmField
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @RequiresFlagsEnabled(FLAG_SENSITIVE_NOTIFICATION_APP_PROTECTION)
    fun testRecordSensitiveContentDuringProjection() {
        val packageManager = InstrumentationRegistry.getContext().getPackageManager()
        val packagesHoldingPermission =
            packageManager
                .getPackagesHoldingPermissions(
                    arrayOf(android.Manifest.permission.RECORD_SENSITIVE_CONTENT),
                    0
                )
                .map { it.packageName }

        if (packagesHoldingPermission.size > 1) {
            Assert.fail(
                "Only one system app on the device is allowed to hold the " +
                    "RECORD_SENSITIVE_CONTENT_DURING_PROJECTION permission, " +
                    "packages holding the permissions are: " +
                    packagesHoldingPermission
            )
        }
    }
}
