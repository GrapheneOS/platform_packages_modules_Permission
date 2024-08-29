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

package com.android.permissioncontroller.tests.mocking.permission.ui.model

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel.AppOpUsageModel
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.domain.usecase.v31.GetPermissionGroupUsageUseCase
import com.android.permissioncontroller.permission.ui.viewmodel.v31.PermissionUsageViewModel
import com.android.permissioncontroller.permission.ui.viewmodel.v31.PermissionUsagesUiState
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.pm.data.model.v31.PackageInfoModel
import com.android.permissioncontroller.tests.mocking.appops.data.repository.FakeAppOpRepository
import com.android.permissioncontroller.tests.mocking.coroutines.collectLastValue
import com.android.permissioncontroller.tests.mocking.permission.data.repository.FakePermissionRepository
import com.android.permissioncontroller.tests.mocking.pm.data.repository.FakePackageRepository
import com.android.permissioncontroller.tests.mocking.role.data.repository.FakeRoleRepository
import com.android.permissioncontroller.tests.mocking.user.data.repository.FakeUserRepository
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class PermissionUsageViewModelTest {
    @Mock private lateinit var application: PermissionControllerApplication
    @Mock private lateinit var context: Context
    private var mockitoSession: MockitoSession? = null

    private lateinit var permissionRepository: PermissionRepository
    private val currentUser = android.os.Process.myUserHandle()
    private val testPackageName = "test.package"
    private val systemPackageName = "test.package.system"
    private lateinit var packageInfos: MutableMap<String, PackageInfoModel>

    @Before
    fun setup() {
        Assume.assumeTrue(SdkLevel.isAtLeastS())
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(application.applicationContext).thenReturn(context)
        PermissionMapping.addHealthPermissionsToPlatform(setOf("health1"))

        val permissionFlags =
            mapOf<String, Int>(
                CAMERA_PERMISSION to PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED,
                RECORD_AUDIO_PERMISSION to 0, // not user sensitive
            )
        permissionRepository = FakePermissionRepository(permissionFlags)
        packageInfos =
            mapOf(
                    testPackageName to getPackageInfoModel(testPackageName),
                    systemPackageName to
                        getPackageInfoModel(
                            systemPackageName,
                            applicationFlags = ApplicationInfo.FLAG_SYSTEM
                        ),
                )
                .toMutableMap()
    }

    @After
    fun finish() {
        mockitoSession?.finishMocking()
    }

    @Test
    fun allPermissionGroupsAreShown() = runTest {
        val permissionUsageViewModel = getViewModel()
        val uiData = getPermissionUsageUiState(permissionUsageViewModel)

        val expectedPermissions = PermissionMapping.getPlatformPermissionGroups().toMutableSet()
        if (SdkLevel.isAtLeastT()) {
            expectedPermissions.remove(android.Manifest.permission_group.NOTIFICATIONS)
        }
        assertThat(uiData.permissionGroupUsageCount.keys).isEqualTo(expectedPermissions)
    }

    @Test
    fun onlyNonSystemAppsUsageIsCounted() = runTest {
        val timestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(5)
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, timestamp),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, timestamp),
            )
        val appOpsUsageModels =
            listOf(
                PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                PackageAppOpUsageModel(systemPackageName, appOpsUsage, currentUser.identifier),
            )
        val permissionUsageUseCase = getPermissionGroupUsageUseCase(appOpsUsageModels)
        val permissionUsageViewModel =
            getViewModel(
                useCase = permissionUsageUseCase,
                savedStateHandle = SavedStateHandle(mapOf("showSystem" to false))
            )
        val uiData = getPermissionUsageUiState(permissionUsageViewModel)

        val permissionGroupsCount = uiData.permissionGroupUsageCount
        assertThat(permissionGroupsCount[CAMERA_PERMISSION_GROUP]).isEqualTo(2)
        assertThat(permissionGroupsCount[MICROPHONE_PERMISSION_GROUP]).isEqualTo(1)
    }

    @Test
    fun systemAppsUsageIsCounted() = runTest {
        val timestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(5)
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, timestamp),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, timestamp),
            )
        val appOpsUsageModels =
            listOf(
                PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                PackageAppOpUsageModel(systemPackageName, appOpsUsage, currentUser.identifier),
            )
        val permissionUsageUseCase = getPermissionGroupUsageUseCase(appOpsUsageModels)
        val permissionUsageViewModel =
            getViewModel(
                useCase = permissionUsageUseCase,
                savedStateHandle = SavedStateHandle(mapOf("showSystem" to true))
            )
        val uiData = getPermissionUsageUiState(permissionUsageViewModel)

        val permissionGroupsCount = uiData.permissionGroupUsageCount
        assertThat(permissionGroupsCount[CAMERA_PERMISSION_GROUP]).isEqualTo(2)
        assertThat(permissionGroupsCount[MICROPHONE_PERMISSION_GROUP]).isEqualTo(2)
    }

    @Test
    fun noSystemAppsAvailableInLast24Hours() = runTest {
        val timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, timestamp),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, timestamp),
            )
        val appOpsUsageModels =
            listOf(
                PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
                PackageAppOpUsageModel(systemPackageName, appOpsUsage, currentUser.identifier),
            )
        val permissionUsageUseCase = getPermissionGroupUsageUseCase(appOpsUsageModels)
        val permissionUsageViewModel = getViewModel(useCase = permissionUsageUseCase)
        val uiData = getPermissionUsageUiState(permissionUsageViewModel)

        assertThat(uiData.containsSystemAppUsage).isFalse()
    }

    @Test
    fun appUsageIsCountedForLast7Days() = runTest {
        val timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, timestamp),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, timestamp),
            )
        val appOpsUsageModels =
            listOf(
                PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
            )
        val permissionUsageUseCase = getPermissionGroupUsageUseCase(appOpsUsageModels)
        val permissionUsageViewModel =
            getViewModel(
                useCase = permissionUsageUseCase,
                is7DayToggleEnabled = true,
                savedStateHandle = SavedStateHandle(mapOf("show7Days" to true))
            )
        val permissionGroupsCount =
            getPermissionUsageUiState(permissionUsageViewModel).permissionGroupUsageCount

        assertThat(permissionGroupsCount[CAMERA_PERMISSION_GROUP]).isEqualTo(1)
        assertThat(permissionGroupsCount[MICROPHONE_PERMISSION_GROUP]).isEqualTo(1)
    }

    @Test
    fun verifyObserverIsNotifiedOnUserActionWhenDataIsSame() = runTest {
        val timestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)
        val appOpsUsage =
            listOf(
                AppOpUsageModel(AppOpsManager.OPSTR_CAMERA, timestamp),
                AppOpUsageModel(AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE, timestamp),
            )
        val appOpsUsageModels =
            listOf(
                PackageAppOpUsageModel(testPackageName, appOpsUsage, currentUser.identifier),
            )
        val permissionUsageUseCase = getPermissionGroupUsageUseCase(appOpsUsageModels)
        val permissionUsageViewModel =
            getViewModel(
                useCase = permissionUsageUseCase,
                savedStateHandle = SavedStateHandle(mapOf("show7Days" to false))
            )

        val uiState = getPermissionUsageUiState(permissionUsageViewModel)
        assertThat(uiState.show7Days).isFalse()

        // perform user action
        permissionUsageViewModel.updateShow7Days(true)
        val uiState2 = getPermissionUsageUiState(permissionUsageViewModel)
        assertThat(uiState2.show7Days).isTrue()
    }

    private fun TestScope.getViewModel(
        useCase: GetPermissionGroupUsageUseCase = getPermissionGroupUsageUseCase(),
        is7DayToggleEnabled: Boolean = false,
        savedStateHandle: SavedStateHandle = SavedStateHandle(emptyMap())
    ): PermissionUsageViewModel {
        return PermissionUsageViewModel(
            application,
            permissionRepository,
            useCase,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            is7DayToggleEnabled = is7DayToggleEnabled,
            savedState = savedStateHandle
        )
    }

    private fun TestScope.getPermissionUsageUiState(
        viewModel: PermissionUsageViewModel
    ): PermissionUsagesUiState.Success {
        val result by collectLastValue(viewModel.permissionUsagesUiDataFlow)
        return result as PermissionUsagesUiState.Success
    }

    private fun getPermissionGroupUsageUseCase(
        packageAppOpsUsages: List<PackageAppOpUsageModel> = emptyList(),
    ): GetPermissionGroupUsageUseCase {
        val userRepository = FakeUserRepository(listOf(currentUser.identifier))
        val roleRepository = FakeRoleRepository()
        val packageRepository = FakePackageRepository(packageInfos)
        val appOpUsageRepository = FakeAppOpRepository(flowOf(packageAppOpsUsages))
        return GetPermissionGroupUsageUseCase(
            packageRepository,
            permissionRepository,
            appOpUsageRepository,
            roleRepository,
            userRepository
        )
    }

    private fun getPackageInfoModel(
        packageName: String,
        requestedPermissions: List<String> = listOf(CAMERA_PERMISSION, RECORD_AUDIO_PERMISSION),
        permissionsFlags: List<Int> =
            listOf(
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                PackageInfo.REQUESTED_PERMISSION_GRANTED
            ),
        applicationFlags: Int = 0,
    ) = PackageInfoModel(packageName, requestedPermissions, permissionsFlags, applicationFlags)

    companion object {
        private val CAMERA_PERMISSION = android.Manifest.permission.CAMERA
        private val RECORD_AUDIO_PERMISSION = android.Manifest.permission.RECORD_AUDIO
        private val CAMERA_PERMISSION_GROUP = android.Manifest.permission_group.CAMERA
        private val MICROPHONE_PERMISSION_GROUP = android.Manifest.permission_group.MICROPHONE
    }
}
