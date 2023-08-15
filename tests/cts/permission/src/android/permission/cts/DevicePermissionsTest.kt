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
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
import android.virtualdevice.cts.common.FakeAssociationRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = " cannot be accessed by instant apps")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
class DevicePermissionsTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val defaultDeviceContext = instrumentation.targetContext

    private lateinit var virtualDevice: VirtualDevice
    private lateinit var virtualDeviceContext: Context

    @get:Rule var mFakeAssociationRule = FakeAssociationRule()

    @get:Rule
    val mAdoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            instrumentation.uiAutomation,
            Manifest.permission.CREATE_VIRTUAL_DEVICE,
            Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
            Manifest.permission.REVOKE_RUNTIME_PERMISSIONS
        )

    @Before
    fun setup() {
        val virtualDeviceManager =
            defaultDeviceContext.getSystemService(VirtualDeviceManager::class.java)
        assumeNotNull(virtualDeviceManager)
        virtualDevice =
            virtualDeviceManager!!.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                VirtualDeviceParams.Builder().build()
            )
        virtualDeviceContext = defaultDeviceContext.createDeviceContext(virtualDevice.deviceId)
        runShellCommand("pm install -r $TEST_APK")
    }

    @After
    fun cleanup() {
        runShellCommand("pm uninstall $TEST_PACKAGE_NAME")
        virtualDevice.close()
    }

    @Test
    fun testDeviceAwareRuntimePermissionIsGranted() {
        grantPermissionAndAssertGranted(Manifest.permission.CAMERA, virtualDeviceContext)
    }

    @Test
    fun testDeviceAwareRuntimePermissionGrantIsInherited() {
        grantPermissionAndAssertGranted(Manifest.permission.CAMERA, defaultDeviceContext)

        assertPermission(
            Manifest.permission.CAMERA, PERMISSION_GRANTED, virtualDeviceContext
        )
    }

    @Test
    fun testNonDeviceAwareRuntimePermissionGrantIsInherited() {
        grantPermissionAndAssertGranted(Manifest.permission.READ_CONTACTS, defaultDeviceContext)

        assertPermission(
            Manifest.permission.READ_CONTACTS, PERMISSION_GRANTED, virtualDeviceContext
        )
    }

    @Test
    fun testDeviceAwareRuntimePermissionIsRevoked() {
        grantPermissionAndAssertGranted(Manifest.permission.RECORD_AUDIO, virtualDeviceContext)

        revokePermissionAndAssertDenied(Manifest.permission.RECORD_AUDIO, virtualDeviceContext)
    }

    @Test
    fun testNonDeviceAwareRuntimePermissionIsRevokedForDefaultDevice() {
        grantPermissionAndAssertGranted(Manifest.permission.READ_CONTACTS, defaultDeviceContext)
        assertPermission(
            Manifest.permission.READ_CONTACTS, PERMISSION_GRANTED, virtualDeviceContext
        )
        // Revoke call from virtualDeviceContext should revoke for default device as well.
        revokePermissionAndAssertDenied(Manifest.permission.READ_CONTACTS, virtualDeviceContext)
        assertPermission(Manifest.permission.READ_CONTACTS, PERMISSION_DENIED, defaultDeviceContext)
    }

    @Test
    fun testNormalPermissionGrantIsInherited() {
        assertPermission(Manifest.permission.INTERNET, PERMISSION_GRANTED, virtualDeviceContext)
    }

    @Test
    fun testSignaturePermissionGrantIsInherited() {
        assertPermission(CUSTOM_SIGNATURE_PERMISSION, PERMISSION_GRANTED, virtualDeviceContext)
    }

    private fun grantPermissionAndAssertGranted(permissionName: String, context: Context) {
        context.packageManager.grantRuntimePermission(
            TEST_PACKAGE_NAME, permissionName, UserHandle.of(context.userId)
        )
        assertPermission(permissionName, PERMISSION_GRANTED, context)
    }

    private fun revokePermissionAndAssertDenied(permissionName: String, context: Context) {
        context.packageManager.revokeRuntimePermission(
            TEST_PACKAGE_NAME, permissionName, UserHandle.of(context.userId)
        )
        eventually {
            assertPermission(permissionName, PERMISSION_DENIED, context)
        }
    }

    private fun assertPermission(
        permissionName: String,
        permissionState: Int,
        context: Context,
    ) {
        assertThat(context.packageManager.checkPermission(permissionName, TEST_PACKAGE_NAME))
            .isEqualTo(permissionState)
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "android.permission.cts.appthatrequestpermission"
        private const val TEST_APK =
            "/data/local/tmp/cts-permission/CtsAppThatRequestsDevicePermissions.apk"

        private const val CUSTOM_SIGNATURE_PERMISSION =
            "android.permission.cts.CUSTOM_SIGNATURE_PERMISSION"
    }
}
