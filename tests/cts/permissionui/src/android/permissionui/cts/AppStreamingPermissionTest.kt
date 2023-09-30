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
import android.app.Instrumentation
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceManager.VirtualDevice
import android.companion.virtual.VirtualDeviceParams
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplayConfig
import android.media.ImageReader
import android.os.Build
import android.provider.Settings
import android.view.Display.DEFAULT_DISPLAY
import android.virtualdevice.cts.common.FakeAssociationRule
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** App Permission page UI tests. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
class AppStreamingPermissionTest : BaseUsePermissionTest() {
    companion object {
        private const val DISPLAY_HEIGHT = 1920
        private const val DISPLAY_WIDTH = 1080
    }

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val defaultDeviceContext = instrumentation.targetContext
    private val imageReader =
        ImageReader.newInstance(
            /* width= */ DISPLAY_WIDTH,
            /* height= */ DISPLAY_HEIGHT,
            PixelFormat.RGBA_8888,
            /* maxImages= */ 1
        )

    private lateinit var virtualDeviceManager: VirtualDeviceManager
    private lateinit var virtualDevice: VirtualDevice
    private var virtualDisplayId: Int = -1

    @get:Rule var mFakeAssociationRule = FakeAssociationRule()

    @get:Rule
    val mAdoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            instrumentation.uiAutomation,
            Manifest.permission.CREATE_VIRTUAL_DEVICE
        )

    @Before
    fun setup() {
        callWithShellPermissionIdentity {
            val virtualDeviceManager =
                defaultDeviceContext.getSystemService(VirtualDeviceManager::class.java)
            assumeNotNull(virtualDeviceManager)
            this.virtualDeviceManager = virtualDeviceManager!!
            virtualDevice =
                virtualDeviceManager.createVirtualDevice(
                    mFakeAssociationRule.associationInfo.id,
                    VirtualDeviceParams.Builder().build()
                )
            val display =
                virtualDevice.createVirtualDisplay(
                    VirtualDisplayConfig.Builder("testDisplay", DISPLAY_WIDTH, DISPLAY_HEIGHT, 240)
                        .setSurface(imageReader.surface)
                        .setFlags(
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED or
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        )
                        .build(),
                    Runnable::run,
                    null
                )
            assertThat(display).isNotNull()
            virtualDisplayId = display!!.display.displayId
        }
    }

    @After
    fun cleanup() {
        callWithShellPermissionIdentity { virtualDevice.close() }
        imageReader.close()
    }

    // TODO(b/291737919) Re-enable test once the flag is rolled out.
    // TODO(b/301272559) Make test dependent on flag value once fixed.
    @Test
    @Ignore("b/291737919")
    fun requestPermission_onVirtualDevice_showsAffectedDevice() {
        installPackage(APP_APK_PATH_STREAMING)
        requestPermissionOnDevice(virtualDisplayId)

        assertPermissionMessageContainsDeviceName(virtualDisplayId, getHostDeviceName())
    }

    @Test
    fun requestPermission_onHostDevice_doesNotShowAffectedDevice() {
        installPackage(APP_APK_PATH_STREAMING)

        requestPermissionOnDevice(DEFAULT_DISPLAY)

        assertPermissionMessageDoesNotContainDeviceName(DEFAULT_DISPLAY, getHostDeviceName())
    }

    private fun getHostDeviceName(): String {
        return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }

    private fun requestPermissionOnDevice(displayId: Int) {
        val options = VirtualDeviceTestUtils.createActivityOptions(displayId)

        val intent =
            Intent()
                .setComponent(
                    ComponentName(
                        "android.permissionui.cts.usepermission",
                        "android.permissionui.cts.usepermission.RequestPermissionActivity"
                    )
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        defaultDeviceContext.startActivity(intent, options)
    }

    private fun assertPermissionMessageContainsDeviceName(displayId: Int, deviceName: String) {
        waitFindObject(By.displayId(displayId).res(getPermissionMessageResource()))
        val text = findTextForView(By.displayId(displayId).res(getPermissionMessageResource()))
        assertThat(text).contains(deviceName)
    }

    private fun assertPermissionMessageDoesNotContainDeviceName(
        displayId: Int,
        deviceName: String
    ) {
        waitFindObject(By.displayId(displayId).res(getPermissionMessageResource()))
        val text = findTextForView(By.displayId(displayId).res(getPermissionMessageResource()))
        assertThat(text).doesNotContain(deviceName)
    }

    private fun getPermissionMessageResource(): String =
        when {
            isAutomotive -> PERMISSION_MESSAGE_ID_AUTOMOTIVE
            else -> PERMISSION_MESSAGE_ID
        }

    private fun findTextForView(selector: BySelector): String {
        val timeoutMs = 10000L

        var exception: Exception? = null
        var view: UiObject2? = null
        try {
            view = waitFindObject(selector, timeoutMs)
        } catch (e: Exception) {
            exception = e
        }
        assertThat(exception).isNull()
        assertThat(view).isNotNull()
        return view!!.text
    }
}
