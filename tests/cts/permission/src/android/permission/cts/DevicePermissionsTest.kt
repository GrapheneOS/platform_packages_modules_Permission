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

package android.permission.cts

import android.Manifest
import android.app.Instrumentation
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceManager.VirtualDevice
import android.companion.virtual.VirtualDeviceParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.virtualdevice.cts.common.FakeAssociationRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
class DevicePermissionsTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private lateinit var virtualDeviceManager: VirtualDeviceManager
    private lateinit var virtualDevice: VirtualDevice
    private lateinit var deviceContext: Context

    @get:Rule var mFakeAssociationRule = FakeAssociationRule()

    @get:Rule
    val mAdoptShellPermissionsRule = AdoptShellPermissionsRule(
        instrumentation.uiAutomation, Manifest.permission.CREATE_VIRTUAL_DEVICE
    )

    @Before
    fun setup() {
        virtualDeviceManager = context.getSystemService(VirtualDeviceManager::class.java)!!
        virtualDevice = virtualDeviceManager.createVirtualDevice(
            mFakeAssociationRule.getAssociationInfo().getId(),
            VirtualDeviceParams.Builder().build()
        )
        assumeNotNull(virtualDevice)
        deviceContext = context.createDeviceContext(virtualDevice.deviceId)
        runShellCommand("pm install -r $TEST_APK")
    }

    @After
    fun cleanup() {
        runShellCommand("pm uninstall $TEST_PACKAGE_NAME")
    }

    @Test
    fun testPermissionGrant() {
        val packageManager = deviceContext.packageManager
        runWithShellPermissionIdentity {
            packageManager.grantRuntimePermission(
                TEST_PACKAGE_NAME, Manifest.permission.CAMERA, UserHandle.of(context.userId))
        }
        assertThat(packageManager.checkPermission(Manifest.permission.CAMERA, TEST_PACKAGE_NAME))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @Test
    fun testPermissionRevoke() {
        val packageManager = deviceContext.packageManager
        runWithShellPermissionIdentity {
            packageManager.grantRuntimePermission(
                TEST_PACKAGE_NAME, Manifest.permission.RECORD_AUDIO, UserHandle.of(context.userId))
        }
        assertThat(
                packageManager.checkPermission(Manifest.permission.RECORD_AUDIO, TEST_PACKAGE_NAME))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)

        runWithShellPermissionIdentity {
            packageManager.revokeRuntimePermission(
                TEST_PACKAGE_NAME, Manifest.permission.RECORD_AUDIO, UserHandle.of(context.userId))
        }
        assertThat(
                packageManager.checkPermission(Manifest.permission.RECORD_AUDIO, TEST_PACKAGE_NAME))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "android.permission.cts.appthatrequestpermission"
        private const val TEST_APK =
            "/data/local/tmp/cts/permissions/CtsAppThatRequestsDevicePermissions.apk"
    }
}
