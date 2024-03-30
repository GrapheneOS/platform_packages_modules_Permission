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

package com.android.permissioncontroller.tests.mocking.utils

import android.app.AppOpsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.mockito.Mockito
import org.mockito.Mockito.`when` as whenever

object MockUtil {
    fun createMockPackageOps(
        packageName: String,
        ops: List<AppOpsManager.OpEntry>,
        uid: Int
    ): AppOpsManager.PackageOps {
        val pkgOp = Mockito.mock(AppOpsManager.PackageOps::class.java)
        whenever(pkgOp.packageName).thenReturn(packageName)
        whenever(pkgOp.ops).thenReturn(ops)
        whenever(pkgOp.uid).thenReturn(uid)
        return pkgOp
    }

    fun createOpEntry(opStr: String, time: Long): AppOpsManager.OpEntry {
        val opEntry = Mockito.mock(AppOpsManager.OpEntry::class.java)
        whenever(opEntry.opStr).thenReturn(opStr)
        whenever(opEntry.getLastAccessTime(Mockito.anyInt())).thenReturn(time)
        return opEntry
    }

    fun createPackageInfo(
        testPackageName: String,
        requestedPerms: List<String>,
        requestedFlags: List<Int>,
        applicationInfoFlags: Int = 0
    ): PackageInfo {
        val appInfo = ApplicationInfo()
        appInfo.flags = applicationInfoFlags
        return PackageInfo().apply {
            packageName = testPackageName
            requestedPermissions = requestedPerms.toTypedArray()
            requestedPermissionsFlags = requestedFlags.toIntArray()
            applicationInfo = appInfo
        }
    }
}
