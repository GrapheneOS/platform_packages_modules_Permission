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

package com.android.permissioncontroller.tests.mocking.pm.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.Attribution
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepositoryImpl
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class PackageRepositoryTest {
    @Mock private lateinit var application: PermissionControllerApplication

    @Mock private lateinit var context: Context

    @Mock private lateinit var packageManager: PackageManager

    private lateinit var underTest: PackageRepository
    private var mockitoSession: MockitoSession? = null

    private val currentUser = android.os.Process.myUserHandle()
    private val testPackageName = "test.package"

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(Utils::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(Utils.getUserContext(application, currentUser)).thenReturn(context)
        whenever(context.packageManager).thenReturn(packageManager)

        underTest = PackageRepositoryImpl(application)
    }

    @After
    fun finish() {
        mockitoSession?.finishMocking()
    }

    @Test
    @Suppress("DEPRECATION")
    fun verifyMissingPackageAttributionInfo() = runTest {
        Assume.assumeTrue(SdkLevel.isAtLeastS())
        val mockData = getPackageInfoWithoutAttribution()
        whenever(
                packageManager.getPackageInfo(
                    eq(testPackageName),
                    eq(PackageManager.GET_ATTRIBUTIONS)
                )
            )
            .thenReturn(mockData)

        val attributionInfo = underTest.getPackageAttributionInfo(testPackageName, currentUser)
        assertThat(attributionInfo).isNotNull()
        assertThat(attributionInfo?.packageName).isEqualTo(testPackageName)
        assertThat(attributionInfo?.areUserVisible).isEqualTo(false)
        assertThat(attributionInfo?.tagResourceMap).isNull()
        assertThat(attributionInfo?.resourceLabelMap).isNull()
    }

    @Test
    @Suppress("DEPRECATION")
    fun verifyPackageAttributionInfo() = runTest {
        Assume.assumeTrue(SdkLevel.isAtLeastS())
        val mockData = getPackageInfoWithAttribution()
        whenever(application.createPackageContext(eq(testPackageName), eq(0))).thenReturn(context)
        whenever(context.getString(eq(100))).thenReturn("tag1 Label")
        whenever(
                packageManager.getPackageInfo(
                    eq(testPackageName),
                    eq(PackageManager.GET_ATTRIBUTIONS)
                )
            )
            .thenReturn(mockData)

        val expectedAttributionMap = mutableMapOf<Int, String>()
        expectedAttributionMap[100] = "tag1 Label"

        val expectedTagToLabelResMap = mutableMapOf<String, Int>()
        expectedTagToLabelResMap["tag1"] = 100
        expectedTagToLabelResMap["tag2"] = 200
        val attributionInfo = underTest.getPackageAttributionInfo(testPackageName, currentUser)
        assertThat(attributionInfo).isNotNull()
        assertThat(attributionInfo?.packageName).isEqualTo(testPackageName)
        assertThat(attributionInfo?.areUserVisible).isEqualTo(true)
        assertThat(attributionInfo?.tagResourceMap).isEqualTo(expectedTagToLabelResMap)
        assertThat(attributionInfo?.resourceLabelMap).isEqualTo(expectedAttributionMap)
    }

    private fun getPackageInfoWithoutAttribution(): PackageInfo {
        val info = mock(ApplicationInfo::class.java)
        whenever(info.areAttributionsUserVisible()).thenReturn(false)

        return PackageInfo().apply {
            packageName = testPackageName
            applicationInfo = info
            requestedPermissions = listOf<String>().toTypedArray()
            requestedPermissionsFlags = listOf<Int>().toIntArray()
        }
    }

    private fun getPackageInfoWithAttribution(): PackageInfo {
        val attribution = mock(Attribution::class.java)
        whenever(attribution.label).thenReturn(100)
        whenever(attribution.tag).thenReturn("tag1")
        val attribution2 = mock(Attribution::class.java)
        whenever(attribution2.label).thenReturn(200)
        whenever(attribution2.tag).thenReturn("tag2")

        val info = mock(ApplicationInfo::class.java)
        whenever(info.areAttributionsUserVisible()).thenReturn(true)

        return PackageInfo().apply {
            packageName = testPackageName
            applicationInfo = info
            requestedPermissions = listOf<String>().toTypedArray()
            requestedPermissionsFlags = listOf<Int>().toIntArray()
            attributions = listOf(attribution, attribution2).toTypedArray()
        }
    }
}
