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

package com.android.permissioncontroller.tests.mocking.appops.data.repository

import android.app.AppOpsManager
import android.app.AppOpsManager.PackageOps
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel.AppOpUsageModel
import com.android.permissioncontroller.appops.data.repository.v31.AppOpRepository
import com.android.permissioncontroller.appops.data.repository.v31.AppOpRepositoryImpl
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.tests.mocking.utils.MockUtil.createMockPackageOps
import com.android.permissioncontroller.tests.mocking.utils.MockUtil.createOpEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class AppOpRepositoryTest {
    @Mock private lateinit var application: PermissionControllerApplication
    @Mock private lateinit var context: Context
    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var appOpsManager: AppOpsManager

    private lateinit var underTest: AppOpRepository
    private lateinit var mockitoSession: MockitoSession

    private val currentUser = android.os.Process.myUserHandle()
    private val testPackageName = "test.package"
    private val testAppId = 100203

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(application.applicationContext).thenReturn(context)
        whenever(application.packageManager).thenReturn(packageManager)
        whenever(application.getSystemService(AppOpsManager::class.java)).thenReturn(appOpsManager)

        val permissionRepository = PermissionRepository.getInstance(application)
        underTest = AppOpRepositoryImpl(application, permissionRepository)
    }

    @After
    fun finish() {
        mockitoSession.finishMocking()
    }

    @Test
    fun verifyPackageAppOpsUsageData() = runTest {
        val packageOpsData = createPackageOpsMockData()
        whenever(appOpsManager.getPackagesForOps(any(Array<String>::class.java)))
            .thenReturn(listOf(packageOpsData))

        val packageOps = underTest.packageAppOpsUsages.take(1).toList().first()
        val expectedAppOpUsages =
            packageOpsData.ops.map {
                AppOpUsageModel(it.opStr, it.getLastAccessTime(OPS_LAST_ACCESS_FLAGS))
            }
        assertThat(packageOps.size).isEqualTo(1)
        assertThat(packageOps[0].packageName).isEqualTo(testPackageName)
        assertThat(packageOps[0].usages).isEqualTo(expectedAppOpUsages)
    }

    private fun createPackageOpsMockData(): PackageOps {
        val opEntries =
            listOf(
                createOpEntry(AppOpsManager.OPSTR_FINE_LOCATION, 100),
                createOpEntry(AppOpsManager.OPSTR_FINE_LOCATION, 200),
                createOpEntry(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 300),
                createOpEntry(AppOpsManager.OPSTR_CAMERA, 350),
            )
        return createMockPackageOps(testPackageName, opEntries, currentUser.getUid(testAppId))
    }

    companion object {
        private const val OPS_LAST_ACCESS_FLAGS =
            AppOpsManager.OP_FLAG_SELF or
                AppOpsManager.OP_FLAG_TRUSTED_PROXIED or
                AppOpsManager.OP_FLAG_TRUSTED_PROXY
    }
}
