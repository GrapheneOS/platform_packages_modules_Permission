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

package com.android.permissioncontroller.tests.mocking.permission.domain.usecase

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.appops.data.model.PackageAppOpUsageModel
import com.android.permissioncontroller.appops.data.model.PackageAppOpUsageModel.AppOpUsageModel
import com.android.permissioncontroller.permission.domain.model.PermissionGroupUsageModel
import com.android.permissioncontroller.permission.domain.usecase.GetPermissionGroupUsageUseCase
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.pm.data.model.PackageInfoModel
import com.android.permissioncontroller.role.data.repository.RoleRepository
import com.android.permissioncontroller.tests.mocking.appops.data.repository.FakeAppOpRepository
import com.android.permissioncontroller.tests.mocking.coroutines.collectLastValue
import com.android.permissioncontroller.tests.mocking.permission.data.repository.FakePermissionRepository
import com.android.permissioncontroller.tests.mocking.pm.data.repository.FakePackageRepository
import com.android.permissioncontroller.tests.mocking.role.data.repository.FakeRoleRepository
import com.android.permissioncontroller.tests.mocking.user.data.repository.FakeUserRepository
import com.android.permissioncontroller.user.data.repository.UserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class GetPermissionUsageUseCaseTest {
    @Mock private lateinit var application: PermissionControllerApplication
    @Mock private lateinit var context: Context

    private lateinit var mockitoSession: MockitoSession
    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var packageInfos: MutableMap<String, PackageInfoModel>
    private val currentUser = android.os.Process.myUserHandle()
    private val testPackageName = "test.package"
    private val guestUserPkgName = "test.package.guest"
    private val exemptedPkgName = "test.exempted.package"
    private val guestUser = UserHandle.of(345)

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
        whenever(application.applicationContext).thenReturn(context)

        userRepository = FakeUserRepository(listOf(currentUser.identifier))
        roleRepository = FakeRoleRepository(setOf(exemptedPkgName))
        packageInfos =
            mapOf(
                    testPackageName to getPackageInfoModel(testPackageName),
                    guestUserPkgName to getPackageInfoModel(guestUserPkgName),
                    exemptedPkgName to getPackageInfoModel(exemptedPkgName),
                )
                .toMutableMap()
    }

    @After
    fun finish() {
        mockitoSession.finishMocking()
    }

    @Test
    fun invalidAppOpIsFiltered() = runTest {
        val appOpsUsageMillis =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 100),
                AppOpUsageModel("OPSTR_INVALID", 100)
            )
        val appOpsUsage =
            PackageAppOpUsageModel(testPackageName, appOpsUsageMillis, currentUser.identifier)
        val appOpsUsageModelFlow = flow { emit(listOf(appOpsUsage)) }
        val underTest = getPermissionGroupUsageUseCase(appOpsUsageModelFlow)

        val permissionGroupUsages by collectLastValue(underTest())
        assertThat(permissionGroupUsages)
            .isEqualTo(listOf(PermissionGroupUsageModel(CAMERA_PERMISSION_GROUP, 100, true)))
    }

    @Test
    fun guestUserUsageIsFiltered() = runTest {
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 100),
            )
        val guestAppOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 100),
            )
        val appOpsUsageModelFlow = flow {
            emit(
                listOf(
                    PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                    PackageAppOpUsageModel(guestUserPkgName, guestAppOpsUsage, guestUser.identifier)
                )
            )
        }
        val underTest = getPermissionGroupUsageUseCase(appOpsUsageModelFlow)

        val permissionGroupUsages by collectLastValue(underTest())
        assertThat(permissionGroupUsages)
            .isEqualTo(listOf(PermissionGroupUsageModel(CAMERA_PERMISSION_GROUP, 100, true)))
    }

    @Test
    fun quiteProfileShowUsageInQuietMode() = runTest {
        Assume.assumeTrue(SdkLevel.isAtLeastV())
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 100),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 150),
            )

        val appOpsUsageModelFlow = flow {
            emit(
                listOf(
                    PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                )
            )
        }
        val userRepository =
            FakeUserRepository(
                currentUserProfiles = listOf(currentUser.identifier),
                quietUserProfiles = listOf(currentUser.identifier),
                showInQuiteModeProfiles = listOf(currentUser.identifier)
            )
        val underTest =
            getPermissionGroupUsageUseCase(appOpsUsageModelFlow, userRepo = userRepository)

        val permissionGroupUsages by collectLastValue(underTest())
        assertThat(permissionGroupUsages)
            .isEqualTo(
                listOf(
                    PermissionGroupUsageModel(CAMERA_PERMISSION_GROUP, 100, true),
                    PermissionGroupUsageModel(MICROPHONE_PERMISSION_GROUP, 150, true)
                )
            )
    }

    @Test
    fun quietProfileAppOpsUsageIsFiltered() = runTest {
        Assume.assumeTrue(SdkLevel.isAtLeastV())
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 100),
            )
        val appOpsUsageModelFlow = flow {
            emit(
                listOf(
                    PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                )
            )
        }
        val userRepository =
            FakeUserRepository(
                currentUserProfiles = listOf(currentUser.identifier),
                quietUserProfiles = listOf(currentUser.identifier),
                showInQuiteModeProfiles = emptyList()
            )
        val underTest =
            getPermissionGroupUsageUseCase(appOpsUsageModelFlow, userRepo = userRepository)
        val permissionGroupUsages by collectLastValue(underTest())
        assertThat(permissionGroupUsages).isEmpty()
    }

    @Test
    fun exemptedPackageIsFiltered() = runTest {
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 100),
            )
        val exemptedOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 100),
            )
        val appOpsUsageModelFlow = flow {
            emit(
                listOf(
                    PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                    PackageAppOpUsageModel(
                        exemptedPkgName,
                        exemptedOpsUsage,
                        currentUser.identifier
                    )
                )
            )
        }
        val underTest = getPermissionGroupUsageUseCase(appOpsUsageModelFlow)

        val permissionGroupUsages by collectLastValue(underTest())
        assertThat(permissionGroupUsages)
            .isEqualTo(listOf(PermissionGroupUsageModel(CAMERA_PERMISSION_GROUP, 100, true)))
    }

    @Test
    fun permissionNoLongerRequestedOpsAreFiltered() = runTest {
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 100),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 100),
            )
        val appOpsUsageModelFlow = flow {
            emit(
                listOf(
                    PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                )
            )
        }
        packageInfos[testPackageName] =
            getPackageInfoModel(
                testPackageName,
                requestedPermissions = listOf(RECORD_AUDIO_PERMISSION)
            )
        val underTest = getPermissionGroupUsageUseCase(appOpsUsageModelFlow)

        val permissionGroupUsages by collectLastValue(underTest())
        assertThat(permissionGroupUsages)
            .isEqualTo(listOf(PermissionGroupUsageModel(MICROPHONE_PERMISSION_GROUP, 100, true)))
    }

    @Test
    fun mostRecentAccessedTimestampIsShown() = runTest {
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 100),
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 150),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 100),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 80),
            )
        val appOpsUsageModelFlow = flow {
            emit(
                listOf(
                    PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                )
            )
        }
        val underTest = getPermissionGroupUsageUseCase(appOpsUsageModelFlow)

        val permissionGroupUsages by collectLastValue(underTest())
        assertThat(permissionGroupUsages)
            .isEqualTo(
                listOf(
                    PermissionGroupUsageModel(CAMERA_PERMISSION_GROUP, 150, true),
                    PermissionGroupUsageModel(MICROPHONE_PERMISSION_GROUP, 100, true)
                )
            )
    }

    @Test
    fun nonSystemAppsUsageIsUserSensitive() = runTest {
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 100),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 100),
            )
        val appOpsUsageModelFlow = flow {
            emit(
                listOf(
                    PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                )
            )
        }
        // test package is not a system package
        val underTest = getPermissionGroupUsageUseCase(appOpsUsageModelFlow)

        val permissionGroupUsages by collectLastValue(underTest())
        assertThat(permissionGroupUsages)
            .isEqualTo(
                listOf(
                    PermissionGroupUsageModel(CAMERA_PERMISSION_GROUP, 100, true),
                    PermissionGroupUsageModel(MICROPHONE_PERMISSION_GROUP, 100, true)
                )
            )
    }

    @Test
    fun systemAppsUsageIsUserSensitive() = runTest {
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, 100),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, 100),
            )
        val appOpsUsageModelFlow = flow {
            emit(
                listOf(
                    PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                )
            )
        }
        val permissionFlags =
            mapOf<String, Int>(
                CAMERA_PERMISSION to PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED,
                RECORD_AUDIO_PERMISSION to 0, // not user sensitive when granted
            )
        packageInfos[testPackageName] =
            getPackageInfoModel(
                testPackageName,
                requestedPermissions = listOf(CAMERA_PERMISSION, RECORD_AUDIO_PERMISSION),
                permissionsFlags =
                    listOf(
                        PackageInfo.REQUESTED_PERMISSION_GRANTED,
                        PackageInfo.REQUESTED_PERMISSION_GRANTED
                    ),
                applicationFlags = ApplicationInfo.FLAG_SYSTEM
            )
        val underTest =
            getPermissionGroupUsageUseCase(appOpsUsageModelFlow, permissionFlags = permissionFlags)

        val permissionGroupUsages by collectLastValue(underTest())
        assertThat(permissionGroupUsages)
            .isEqualTo(
                listOf(
                    PermissionGroupUsageModel(CAMERA_PERMISSION_GROUP, 100, true),
                    PermissionGroupUsageModel(MICROPHONE_PERMISSION_GROUP, 100, false)
                )
            )
    }

    private fun getPackageInfoModel(
        packageName: String,
        requestedPermissions: List<String> = listOf(CAMERA_PERMISSION, RECORD_AUDIO_PERMISSION),
        permissionsFlags: List<Int> = listOf(0, 0),
        applicationFlags: Int = 0,
    ) = PackageInfoModel(packageName, requestedPermissions, permissionsFlags, applicationFlags)

    private fun getPermissionGroupUsageUseCase(
        packageAppOpsUsages: Flow<List<PackageAppOpUsageModel>>,
        permissionFlags: Map<String, Int> = emptyMap(),
        userRepo: UserRepository = userRepository
    ): GetPermissionGroupUsageUseCase {
        val permissionRepository = FakePermissionRepository(permissionFlags)
        val packageRepository = FakePackageRepository(packageInfos)
        val appOpUsageRepository = FakeAppOpRepository(packageAppOpsUsages)
        return GetPermissionGroupUsageUseCase(
            packageRepository,
            permissionRepository,
            appOpUsageRepository,
            roleRepository,
            userRepo
        )
    }

    companion object {
        private val CAMERA_PERMISSION = android.Manifest.permission.CAMERA
        private val RECORD_AUDIO_PERMISSION = android.Manifest.permission.RECORD_AUDIO
        private val CAMERA_PERMISSION_GROUP = android.Manifest.permission_group.CAMERA
        private val MICROPHONE_PERMISSION_GROUP = android.Manifest.permission_group.MICROPHONE
    }
}
