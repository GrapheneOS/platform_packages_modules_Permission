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

package android.permissionui.cts

import android.Manifest
import android.os.Build
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * This test validates multi device permission APIs.
 *
 * TODO(mrulhania): will update the test once all iris permission API changes are merged.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@FlakyTest
class MultiDevicePermissionTest : BaseUsePermissionTest() {
    @Before
    fun setup() {
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)
    }

    @Test
    fun testPermissionGrantForDefaultDevice() {
        installPackage(APP_APK_PATH_LATEST)
        assertAppHasPermission(Manifest.permission.CAMERA, false)
        assertAppHasPermission(Manifest.permission.RECORD_AUDIO, false)

        requestAppPermissionsAndAssertResult(Manifest.permission.CAMERA to true) {
            clickPermissionRequestAllowForegroundButton()
        }
        requestAppPermissionsAndAssertResult(Manifest.permission.RECORD_AUDIO to true) {
            clickPermissionRequestAllowForegroundButton()
        }
    }
}
