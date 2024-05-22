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
import android.companion.virtual.VirtualDeviceParams
import android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM
import android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.permission.PermissionManager
import android.permission.flags.Flags
import android.permissionmultidevice.cts.PermissionUtils.isCddCompliantScreenSize
import android.platform.test.annotations.RequiresFlagsEnabled
import android.virtualdevice.cts.common.VirtualDeviceRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.UiAutomatorUtils2
import com.android.modules.utils.build.SdkLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
class AppPermissionsTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val defaultDeviceContext = instrumentation.targetContext

    @get:Rule
    var virtualDeviceRule =
        VirtualDeviceRule.withAdditionalPermissions(
            Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
            Manifest.permission.MANAGE_ONE_TIME_PERMISSION_SESSIONS,
            Manifest.permission.REVOKE_RUNTIME_PERMISSIONS,
            Manifest.permission.CREATE_VIRTUAL_DEVICE
        )

    private lateinit var persistentDeviceId: String
    private lateinit var externalDeviceCameraText: String
    private lateinit var permissionMessage: String

    private val permissionManager =
        defaultDeviceContext.getSystemService(PermissionManager::class.java)!!

    private val TAG = AppPermissionsTest::class.java.simpleName

    @Before
    fun setup() {
        assumeTrue(SdkLevel.isAtLeastV())
        assumeFalse(PermissionUtils.isAutomotive(defaultDeviceContext))
        assumeFalse(PermissionUtils.isTv(defaultDeviceContext))
        assumeFalse(PermissionUtils.isWatch(defaultDeviceContext))
        assumeTrue(isCddCompliantScreenSize())

        PackageManagementUtils.installPackage(APP_APK_PATH_STREAMING)

        val virtualDeviceManager =
            defaultDeviceContext.getSystemService(VirtualDeviceManager::class.java)!!
        val virtualDevice =
            virtualDeviceRule.createManagedVirtualDevice(
                VirtualDeviceParams.Builder()
                    .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                    .build()
            )

        val mDeviceDisplayName =
            virtualDeviceManager.getVirtualDevice(virtualDevice.deviceId)!!.displayName.toString()

        persistentDeviceId = virtualDevice.persistentDeviceId!!
        externalDeviceCameraText = "Camera on $mDeviceDisplayName"
        permissionMessage = "Camera access for this app on $mDeviceDisplayName"
    }

    @After
    fun cleanup() {
        PackageManagementUtils.uninstallPackage(APP_PACKAGE_NAME, requireSuccess = false)
        UiAutomatorUtils2.getUiDevice().pressHome()
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun externalDevicePermissionGrantTest() {
        grantRunTimePermission()

        openAppPermissionsScreen()

        clickPermissionItem(externalDeviceCameraText)

        verifyPermissionMessage()

        verifyRadioButtonStates(
            allowForegroundChecked = true,
            askChecked = false,
            denyChecked = false
        )

        UiAutomatorUtils2.getUiDevice().pressBack()
        val expectedGrantInfoMap =
            mapOf(
                "Allowed" to listOf(externalDeviceCameraText),
                "Ask every time" to emptyList(),
                "Not allowed" to listOf("Camera")
            )
        assertEquals(expectedGrantInfoMap, getGrantInfoMap())
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun externalDevicePermissionChangeToAskTest() {
        grantRunTimePermission()
        openAppPermissionsScreen()

        clickPermissionItem(externalDeviceCameraText)
        clickAskButton()

        verifyAskSelection()
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun externalDevicePermissionChangeToDenyTest() {
        grantRunTimePermission()
        openAppPermissionsScreen()

        clickPermissionItem(externalDeviceCameraText)
        clickDenyButton()

        verifyDenySelection()
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun externalDevicePermissionChangeToAllowTest() {
        grantRunTimePermission()
        openAppPermissionsScreen()

        clickPermissionItem(externalDeviceCameraText)
        clickAskButton()
        verifyRadioButtonStates(
            allowForegroundChecked = false,
            askChecked = true,
            denyChecked = false
        )

        clickAllowForegroundButton()
        verifyAllowedSelection()
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun externalDevicePermissionNotDisplayedInitiallyTest() {
        openAppPermissionsScreen()

        // External device permission does not show initially (until requested)
        val expectedGrantInfoMap =
            mapOf(
                "Allowed" to emptyList(),
                "Ask every time" to emptyList(),
                "Not allowed" to listOf("Camera")
            )
        assertEquals(expectedGrantInfoMap, getGrantInfoMap())
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED
    )
    @Test
    fun externalDevicePermissionStickyOnGrantTest() {
        grantRunTimePermission()
        openAppPermissionsScreen()

        clickPermissionItem(externalDeviceCameraText)

        verifyRadioButtonStates(
            allowForegroundChecked = true,
            askChecked = false,
            denyChecked = false
        )

        clickDenyButton()

        UiAutomatorUtils2.getUiDevice().pressBack()

        // Verify the permission continue to show (sticky) after revoking, keeps option for users
        // to change in future
        val expectedGrantInfoMap =
            mapOf(
                "Allowed" to emptyList(),
                "Ask every time" to emptyList(),
                "Not allowed" to listOf("Camera", externalDeviceCameraText)
            )
        assertEquals(expectedGrantInfoMap, getGrantInfoMap())
    }

    private fun verifyAskSelection() {
        verifyPermissionMessage()

        verifyRadioButtonStates(
            allowForegroundChecked = false,
            askChecked = true,
            denyChecked = false
        )

        UiAutomatorUtils2.getUiDevice().pressBack()

        val expectedGrantInfoMap =
            mapOf(
                "Allowed" to emptyList(),
                "Ask every time" to listOf(externalDeviceCameraText),
                "Not allowed" to listOf("Camera")
            )
        assertEquals(expectedGrantInfoMap, getGrantInfoMap())

        val permState = getPermState()
        assertEquals(false, permState[DEVICE_AWARE_PERMISSION]!!.isGranted)
        assertTrue(
            permState[DEVICE_AWARE_PERMISSION]!!.flags and
                PackageManager.FLAG_PERMISSION_ONE_TIME != 0
        )
    }

    private fun verifyDenySelection() {
        verifyPermissionMessage()

        verifyRadioButtonStates(
            allowForegroundChecked = false,
            askChecked = false,
            denyChecked = true
        )

        UiAutomatorUtils2.getUiDevice().pressBack()

        val expectedGrantInfoMap =
            mapOf(
                "Allowed" to emptyList(),
                "Ask every time" to emptyList(),
                "Not allowed" to listOf("Camera", externalDeviceCameraText)
            )
        assertEquals(expectedGrantInfoMap, getGrantInfoMap())

        val permState = getPermState()
        assertEquals(false, permState[DEVICE_AWARE_PERMISSION]!!.isGranted)
        assertTrue(
            permState[DEVICE_AWARE_PERMISSION]!!.flags and
                PackageManager.FLAG_PERMISSION_USER_SET != 0
        )
    }

    private fun verifyAllowedSelection() {
        verifyPermissionMessage()

        verifyRadioButtonStates(
            allowForegroundChecked = true,
            askChecked = false,
            denyChecked = false
        )

        UiAutomatorUtils2.getUiDevice().pressBack()

        val expectedGrantInfoMap =
            mapOf(
                "Allowed" to listOf(externalDeviceCameraText),
                "Ask every time" to emptyList(),
                "Not allowed" to listOf("Camera")
            )
        assertEquals(expectedGrantInfoMap, getGrantInfoMap())

        val permState = getPermState()
        assertEquals(true, permState[DEVICE_AWARE_PERMISSION]!!.isGranted)
        assertTrue(
            permState[DEVICE_AWARE_PERMISSION]!!.flags and
                PackageManager.FLAG_PERMISSION_USER_SET != 0
        )
    }

    private fun verifyPermissionMessage() {
        val actualText = UiAutomatorUtils2.waitFindObject(By.res(PERMISSION_MESSAGE_ID)).text
        assertEquals(permissionMessage, actualText)
    }

    private fun getGrantInfoMap(): Map<String, List<String>> {
        val grantInfoMap =
            mapOf(
                "Allowed" to mutableListOf<String>(),
                "Ask every time" to mutableListOf(),
                "Not allowed" to mutableListOf()
            )
        val outOfScopeTitles = setOf("Unused app settings", "Manage app if unused")

        val titleSelector = UiSelector().resourceId(TITLE)
        var currentGrantText = ""

        val scrollable = getScrollableRecyclerView()

        // Scrolling to end inorder to have the scrollable object loaded with all child element data
        // ready to be read. If the scroll happens in the middle of the reading process, it has been
        // observed that child items will be skipped during the reading (could be a bug). Hence this
        // solution is to scroll to the bottom in the beginning and be more efficient as well.
        scrollable.scrollToEnd(1)

        for (i in 0..scrollable.childCount) {
            val child = scrollable.getChild(UiSelector().index(i))
            val titleText = child.getChild(titleSelector).text
            if (outOfScopeTitles.contains(titleText)) {
                break
            }
            if (grantInfoMap.contains(titleText)) {
                currentGrantText = titleText
            } else if (!titleText.startsWith("No permissions")) {
                grantInfoMap[currentGrantText]!!.add(titleText)
            }
        }
        return grantInfoMap
    }

    private fun verifyRadioButtonStates(
        allowForegroundChecked: Boolean,
        askChecked: Boolean,
        denyChecked: Boolean
    ) {
        eventually {
            assertEquals(
                allowForegroundChecked,
                UiAutomatorUtils2.waitFindObject(By.res(ALLOW_FOREGROUND_ONLY_RADIO_BUTTON))
                    .isChecked
            )
            assertEquals(
                askChecked,
                UiAutomatorUtils2.waitFindObject(By.res(ASK_RADIO_BUTTON)).isChecked
            )
            assertEquals(
                denyChecked,
                UiAutomatorUtils2.waitFindObject(By.res(DENY_RADIO_BUTTON)).isChecked
            )
        }
    }

    private fun openAppPermissionsScreen() {
        UiAutomatorUtils2.getUiDevice()
            .performActionAndWait(
                {
                    defaultDeviceContext.startActivity(
                        Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS).apply {
                            putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    )
                },
                Until.newWindow(),
                NEW_WINDOW_TIMEOUT_MILLIS
            )
    }

    private fun getScrollableRecyclerView(): UiScrollable {
        // Wait for object to load
        UiAutomatorUtils2.waitFindObject(By.res(RECYCLER_VIEW))
        return UiScrollable(UiSelector().resourceId(RECYCLER_VIEW))
    }

    private fun clickPermissionItem(permissionItemName: String) =
        UiAutomatorUtils2.waitFindObject(By.text(permissionItemName)).click()

    private fun clickAllowForegroundButton() =
        UiAutomatorUtils2.waitFindObject(By.res(ALLOW_FOREGROUND_ONLY_RADIO_BUTTON)).click()

    private fun clickAskButton() =
        UiAutomatorUtils2.waitFindObject(By.res(ASK_RADIO_BUTTON)).click()

    private fun clickDenyButton() =
        UiAutomatorUtils2.waitFindObject(By.res(DENY_RADIO_BUTTON)).click()

    private fun grantRunTimePermission() =
        permissionManager.grantRuntimePermission(
            APP_PACKAGE_NAME,
            DEVICE_AWARE_PERMISSION,
            persistentDeviceId
        )

    private fun getPermState(): Map<String, PermissionManager.PermissionState> =
        permissionManager.getAllPermissionStates(APP_PACKAGE_NAME, persistentDeviceId)

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
        private const val PERMISSION_MESSAGE_ID =
            "com.android.permissioncontroller:id/permission_message"
        private const val NEW_WINDOW_TIMEOUT_MILLIS: Long = 20_000
    }
}
