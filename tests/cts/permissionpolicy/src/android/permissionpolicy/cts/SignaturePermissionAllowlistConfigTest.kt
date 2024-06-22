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

package android.permissionpolicy.cts

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.CddTest
import com.android.compatibility.common.util.PermissionUtils
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignaturePermissionAllowlistConfigTest {
    private val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
    private val packageManager = context.packageManager

    @CddTest(requirements = arrayOf("9.1/C-0-16"))
    @Test
    fun allPlatformSignedNonSystemPackagesHavePlatformSignaturePermissionsAllowlisted() {
        val allowlist = getSignaturePermissionAllowlist()
        val platformSignaturePermissionNames = getPlatformSignaturePermissionNames()
        val unallowlistedPackageAndPermissions = mutableMapOf<String, MutableList<String>>()
        for (packageInfo in getPlatformSignedNonSystemPackageInfos()) {
            val permissionNames = allowlist[packageInfo.packageName]
            packageInfo.requestedPermissions?.forEach { permissionName ->
                if (permissionName in platformSignaturePermissionNames) {
                    if (permissionNames?.contains(permissionName) != true) {
                        unallowlistedPackageAndPermissions.getOrPut(packageInfo.packageName) {
                            mutableListOf()
                        } += permissionName
                    }
                }
            }
        }
        assertTrue(
            "Some platform-signed non-system packages don't have their requested platform" +
                " signature permissions allowlisted. Suggested signature permission allowlist" +
                " additions:\n\n" +
                buildSiganturePermissionAllowlist(unallowlistedPackageAndPermissions),
            unallowlistedPackageAndPermissions.isEmpty()
        )
    }

    private fun getSignaturePermissionAllowlist(): Map<String, Set<String>> {
        val allowlist = mutableMapOf<String, MutableSet<String>>()
        val partitions = listOf("system", "system-ext", "vendor", "product", "apex")
        for (partition in partitions) {
            val output =
                SystemUtil.runShellCommandOrThrow(
                        "pm get-signature-permission-allowlist $partition"
                    )
                    .trim()
            lateinit var permissionNames: MutableSet<String>
            for (line in output.split("\n")) {
                val line = line.trim()
                when {
                    line.startsWith("Package: ") -> {
                        val packageName = line.substring("Package: ".length)
                        permissionNames = allowlist.getOrPut(packageName) { mutableSetOf() }
                    }
                    line.startsWith("Permission: ") -> {
                        val permissionName = line.substring("Permission: ".length)
                        permissionNames += permissionName
                    }
                    line.isEmpty() -> {}
                    else -> error("Unknown line in pm get-signature-permission-allowlist: $line")
                }
            }
        }
        return allowlist
    }

    @CddTest(requirements = arrayOf("9.1/C-0-16"))
    @Test
    fun allPlatformSignedNonSystemPackagesHavePlatformSignaturePermissionsGranted() {
        val platformSignaturePermissionNames = getPlatformSignaturePermissionNames()
        val deniedPackageAndPermissions = mutableMapOf<String, MutableList<String>>()
        for (packageInfo in getPlatformSignedNonSystemPackageInfos()) {
            packageInfo.requestedPermissions?.forEachIndexed { index, permissionName ->
                val permissionFlags = packageInfo.requestedPermissionsFlags!![index]
                if (permissionName in platformSignaturePermissionNames) {
                    if (permissionFlags and PackageInfo.REQUESTED_PERMISSION_GRANTED == 0) {
                        deniedPackageAndPermissions.getOrPut(packageInfo.packageName) {
                            mutableListOf()
                        } += permissionName
                    }
                }
            }
        }
        assertTrue(
            "Some platform-signed non-system packages don't have their requested platform" +
                " signature permissions granted. Suggested signature permission allowlist" +
                " additions:\n\n${buildSiganturePermissionAllowlist(deniedPackageAndPermissions)}",
            deniedPackageAndPermissions.isEmpty()
        )
    }

    private fun getPlatformSignaturePermissionNames(): List<String> =
        packageManager
            .getPackageInfo("android", PackageManager.GET_PERMISSIONS)
            .permissions!!
            .filter { it.protection == PermissionInfo.PROTECTION_SIGNATURE }
            .map { it.name }

    private fun getPlatformSignedNonSystemPackageInfos(): List<PackageInfo> =
        packageManager
            .getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter {
                it.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                    it.applicationInfo!!.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            }
            .filter { PermissionUtils.isPlatformSigned(it.packageName) }

    private fun buildSiganturePermissionAllowlist(
        packagesAndPermissions: Map<String, List<String>>
    ): String = buildString {
        for ((packageName, permissionNames) in packagesAndPermissions) {
            append("    <signature-permissions package=\"$packageName\">\n")
            for (permissionName in permissionNames) {
                append("        <permission name=\"$permissionName\" />\n")
            }
            append("    </signature-permissions>\n")
        }
    }
}
