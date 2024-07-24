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

package android.permissionui.cts

import android.Manifest.permission_group.SMS
import android.os.Build
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.modules.utils.build.SdkLevel
import com.android.permission.flags.Flags
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Enhanced Confirmation Backport UI tests.  */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class EnhancedConfirmationBackportTest : BaseUsePermissionTest() {

    @JvmField
    @Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setup() {
        assumeFalse(isAutomotive)
        assumeFalse(isTv)
        assumeFalse(isWatch)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_BACKPORT_ENABLED)
    @Test
    fun installDownloadedFile_clickAppPermissions_clickAllowRestrictedSettings_clickSMSPermGroup_clickAllowed() {
        installPackageWithInstallSourceAndNoMetadataFromDownloadedFile(APP_APK_NAME_LATEST)

        startManageAppPermissionsActivity()
        waitFindObject(By.descContains(MORE_OPTIONS)).clickAndWait(
            Until.newWindow(),
            BasePermissionTest.TIMEOUT_MILLIS
        )

        if (!SdkLevel.isAtLeastV()) {
            waitFindObject(By.text(ALLOW_RESTRICTED_SETTINGS)).click()

            pressBack()

            navigateToIndividualPermissionSetting(SMS)

            assertAllowButtonIsEnabledAndClickAndChecked()

            pressBack()
        } else {
            findView(By.text(ALLOW_RESTRICTED_SETTINGS), false)
        }

        pressBack()
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    @RequiresFlagsEnabled(Flags.FLAG_ENHANCED_CONFIRMATION_BACKPORT_ENABLED)
    @Test
    fun installFromLocalFile_clickAppPermissions_clickAllowRestrictedSettings_clickSMSPermGroup_clickAllowed() {
        installPackageWithInstallSourceAndNoMetadataFromLocalFile(APP_APK_NAME_LATEST)

        startManageAppPermissionsActivity()
        waitFindObject(By.descContains(MORE_OPTIONS)).click()

        if (!SdkLevel.isAtLeastV()) {
            waitFindObject(By.text(ALLOW_RESTRICTED_SETTINGS)).click()

            pressBack()

            navigateToIndividualPermissionSetting(SMS)

            assertAllowButtonIsEnabledAndClickAndChecked()

            pressBack()
        } else {
            findView(By.text(ALLOW_RESTRICTED_SETTINGS), false)
        }

        pressBack()
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    @RequiresFlagsDisabled(Flags.FLAG_ENHANCED_CONFIRMATION_BACKPORT_ENABLED)
    @Test
    fun installDownloadedFile_clickAppPermissions_noAllowRestrictedSettings() {
        installPackageWithInstallSourceAndNoMetadataFromDownloadedFile(APP_APK_NAME_LATEST)

        startManageAppPermissionsActivity()
        waitFindObject(By.descContains(MORE_OPTIONS)).clickAndWait(
            Until.newWindow(),
            BasePermissionTest.TIMEOUT_MILLIS
        )

        findView(By.text(ALLOW_RESTRICTED_SETTINGS), false)

        pressBack()
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
    @RequiresFlagsDisabled(Flags.FLAG_ENHANCED_CONFIRMATION_BACKPORT_ENABLED)
    @Test
    fun installFromLocalFile_clickAppPermissions_noAllowRestrictedSettings() {
        installPackageWithInstallSourceAndNoMetadataFromLocalFile(APP_APK_NAME_LATEST)

        startManageAppPermissionsActivity()
        waitFindObject(By.descContains(MORE_OPTIONS)).click()

        findView(By.text(ALLOW_RESTRICTED_SETTINGS), false)

        pressBack()
    }

    private fun assertAllowButtonIsEnabledAndClickAndChecked() {
        waitFindObject(By.res(ALLOW_RADIO_BUTTON).enabled(true).checked(false))
            .click()
        waitFindObject(By.res(ALLOW_RADIO_BUTTON).checked(true))
    }

    companion object {
        private const val MORE_OPTIONS = "More options"
        private const val ALLOW_RESTRICTED_SETTINGS = "Allow restricted settings"
    }
}
