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

import android.Manifest
import android.app.Instrumentation
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.permission.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.UiAutomatorUtils2
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
class AppPermissionsTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val defaultDeviceContext = instrumentation.targetContext

    private lateinit var virtualDevice: VirtualDeviceManager.VirtualDevice
    private lateinit var virtualDeviceContext: Context
    private lateinit var persistentDeviceId: String
    private lateinit var deviceName: String

    @get:Rule(order = 0) var mFakeVirtualDeviceRule = FakeVirtualDeviceRule()

    @get:Rule(order = 1)
    val mAdoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            instrumentation.uiAutomation,
            Manifest.permission.CREATE_VIRTUAL_DEVICE,
            Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
            Manifest.permission.MANAGE_ONE_TIME_PERMISSION_SESSIONS,
            Manifest.permission.REVOKE_RUNTIME_PERMISSIONS
        )

    @Rule @JvmField val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setup() {
        virtualDevice = mFakeVirtualDeviceRule.virtualDevice
        virtualDeviceContext = defaultDeviceContext.createDeviceContext(virtualDevice.deviceId)
        persistentDeviceId = virtualDevice.persistentDeviceId!!
        deviceName = mFakeVirtualDeviceRule.deviceDisplayName

        PackageManagementUtils.installPackage(APP_APK_PATH_STREAMING)
    }

    @After
    fun cleanup() {
        PackageManagementUtils.uninstallPackage(APP_PACKAGE_NAME, requireSuccess = false)
        virtualDevice.close()
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun virtualDevicePermissionGrantTest() {
        grantRunTimePermission()

        openAppPermissionsScreen()

        val grantInfoMap = getGrantInfoMap()

        val virtualDeviceCameraText = "Camera on $deviceName"

        assertEquals(1, grantInfoMap["Allowed"]!!.size)
        assertEquals(true, grantInfoMap["Allowed"]!!.contains(virtualDeviceCameraText))

        assertEquals(1, grantInfoMap["Not allowed"]!!.size)
        assertEquals(true, grantInfoMap["Not allowed"]!!.contains("Camera"))

        clickPermissionItem(virtualDeviceCameraText)

        val foregroundOnlyRadioButton =
            UiAutomatorUtils2.waitFindObject(By.res(ALLOW_FOREGROUND_ONLY_RADIO_BUTTON))
        val askRadioButton = UiAutomatorUtils2.waitFindObject(By.res(ASK_RADIO_BUTTON))
        val denyRadioButton = UiAutomatorUtils2.waitFindObject(By.res(DENY_RADIO_BUTTON))
        assertEquals(foregroundOnlyRadioButton.isChecked, true)
        assertEquals(askRadioButton.isChecked, false)
        assertEquals(denyRadioButton.isChecked, false)
    }

    private fun getGrantInfoMap(): Map<String, List<String>> {
        val recyclerView = getAppPermissionsRecyclerView()

        val grantInfoMap =
            mapOf(
                "Allowed" to mutableListOf<String>(),
                "Ask every time" to mutableListOf(),
                "Not allowed" to mutableListOf(),
                "Unused app settings" to mutableListOf(),
                "Manage app if unused" to mutableListOf()
            )

        val childItemSelector = UiSelector().resourceId(TITLE)
        var grantText = ""

        for (i in 0..recyclerView.childCount) {
            val child = recyclerView.getChild(UiSelector().index(i)).getChild(childItemSelector)
            if (!child.exists()) {
                break
            }
            if (child.text in grantInfoMap) {
                grantText = child.text
            } else if (!child.text.startsWith("No permissions")) {
                grantInfoMap[grantText]!!.add(child.text)
            }
        }
        return grantInfoMap
    }

    private fun getAppPermissionsRecyclerView(): UiObject {
        val uiObject =
            UiAutomatorUtils2.getUiDevice().findObject(UiSelector().resourceId(RECYCLER_VIEW))
        uiObject.waitForExists(5000)
        return uiObject
    }

    private fun openAppPermissionsScreen() {
        instrumentation.context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", APP_PACKAGE_NAME, null)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        SystemUtil.eventually { UiAutomatorUtils.click(By.text("Permissions")) }
    }

    private fun clickPermissionItem(permissionItemName: String) {
        val childItemSelector = UiSelector().resourceId(TITLE)
        getAppPermissionsRecyclerView().getChild(childItemSelector.text(permissionItemName)).let {
            it.waitForExists(5000)
            it.click()
        }
    }

    private fun grantRunTimePermission() {
        virtualDeviceContext.packageManager.grantRuntimePermission(
            APP_PACKAGE_NAME,
            DEVICE_AWARE_PERMISSION,
            UserHandle.of(virtualDeviceContext.userId)
        )
    }

    companion object {
        private const val APK_DIRECTORY = "/data/local/tmp/cts-permissionmultidevice"
        private const val APP_APK_PATH_STREAMING =
            "${APK_DIRECTORY}/CtsAccessRemoteDeviceCamera.apk"
        private const val APP_PACKAGE_NAME =
            "android.permissionmultidevice.cts.accessremotedevicecamera"
        private const val DEVICE_AWARE_PERMISSION = Manifest.permission.CAMERA

        private const val ALLOW_FOREGROUND_ONLY_RADIO_BUTTON =
            "com.android.permissioncontroller:id/allow_foreground_only_radio_button"
        private const val ASK_RADIO_BUTTON = "com.android.permissioncontroller:id/ask_radio_button"
        private const val DENY_RADIO_BUTTON =
            "com.android.permissioncontroller:id/deny_radio_button"
        private const val TITLE = "android:id/title"
        private const val RECYCLER_VIEW = "com.android.permissioncontroller:id/recycler_view"
    }
}
