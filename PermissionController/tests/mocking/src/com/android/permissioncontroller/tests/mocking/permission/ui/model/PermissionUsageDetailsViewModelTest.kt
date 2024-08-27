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

package com.android.permissioncontroller.tests.mocking.permission.ui.model

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.permission.flags.Flags
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.appops.data.model.v31.DiscretePackageOpsModel
import com.android.permissioncontroller.appops.data.model.v31.DiscretePackageOpsModel.DiscreteOpModel
import com.android.permissioncontroller.permission.domain.usecase.v31.GetPermissionGroupUsageDetailsUseCase
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.PermissionUsageDetailsUiState
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModelV2
import com.android.permissioncontroller.permission.utils.StringUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.pm.data.model.v31.PackageInfoModel
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import com.android.permissioncontroller.tests.mocking.appops.data.repository.FakeAppOpRepository
import com.android.permissioncontroller.tests.mocking.coroutines.collectLastValue
import com.android.permissioncontroller.tests.mocking.permission.data.repository.FakePermissionRepository
import com.android.permissioncontroller.tests.mocking.pm.data.repository.FakePackageRepository
import com.android.permissioncontroller.tests.mocking.role.data.repository.FakeRoleRepository
import com.android.permissioncontroller.tests.mocking.user.data.repository.FakeUserRepository
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/**
 * These unit tests are for new permission timeline implementation, the new view model class is
 * [PermissionUsageDetailsViewModelV2]
 */
@RunWith(AndroidJUnit4::class)
class PermissionUsageDetailsViewModelTest {
    @Mock private lateinit var application: PermissionControllerApplication
    @Mock private lateinit var context: Context
    private var mockitoSession: MockitoSession? = null

    private lateinit var packageRepository: PackageRepository

    private val currentUser = android.os.Process.myUserHandle()
    private val testPackageName = "test.package"
    private val testPackageLabel = "Test Package Label"
    private val systemPackageName = "test.package.system"
    private lateinit var packageInfos: MutableMap<String, PackageInfoModel>

    @Before
    fun setup() {
        Assume.assumeTrue(SdkLevel.isAtLeastS())
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(Utils::class.java)
                .mockStatic(StringUtils::class.java)
                .mockStatic(Flags::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(application.applicationContext).thenReturn(context)
        whenever(Utils.getUserContext(application, currentUser)).thenReturn(context)
        whenever(
                StringUtils.getIcuPluralsString(
                    any(),
                    anyInt(),
                    anyInt(),
                    any(Array<String>::class.java)
                )
            )
            .thenReturn("Duration Summary")

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

        packageRepository =
            FakePackageRepository(
                packageInfos,
                packagesAndLabels = mapOf(testPackageName to testPackageLabel)
            )
    }

    @After
    fun finish() {
        mockitoSession?.finishMocking()
    }

    @Test
    fun verifyOnlyNonSystemAppsAreShown() = runTest {
        val accessTimeMillis = (getCurrentTime() - TimeUnit.HOURS.toMillis(5))
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_COARSE_LOCATION, accessTimeMillis, -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                    DiscretePackageOpsModel(systemPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getViewModel(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                savedStateMap = mapOf("show7Days" to false, "showSystem" to false),
            )
        val uiState = getPermissionUsageDetailsUiState(underTest)

        assertThat(uiState.containsSystemAppUsage).isEqualTo(true)
        assertThat(uiState.appPermissionAccessUiInfoList.size).isEqualTo(1)
        assertThat(uiState.appPermissionAccessUiInfoList.first().packageName)
            .isEqualTo(testPackageName)
        assertThat(uiState.appPermissionAccessUiInfoList.first().packageLabel)
            .isEqualTo(testPackageLabel)
    }

    @Test
    fun verifySystemAppsAreShown() = runTest {
        val accessTimeMillis = (getCurrentTime() - TimeUnit.HOURS.toMillis(5))
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    accessTimeMillis,
                    TimeUnit.MINUTES.toMillis(1)
                ),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                    DiscretePackageOpsModel(systemPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getViewModel(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                savedStateMap = mapOf("show7Days" to false, "showSystem" to true)
            )
        val uiState = getPermissionUsageDetailsUiState(underTest)

        assertThat(uiState.containsSystemAppUsage).isEqualTo(true)
        assertThat(uiState.appPermissionAccessUiInfoList.size).isEqualTo(2)
        assertThat(uiState.appPermissionAccessUiInfoList.map { it.packageName }.toSet())
            .isEqualTo(setOf(testPackageName, systemPackageName))
    }

    @Test
    fun verifyNoSystemAppsAvailable() = runTest {
        val accessTimeMillis = (getCurrentTime() - TimeUnit.HOURS.toMillis(5))
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    accessTimeMillis,
                    TimeUnit.MINUTES.toMillis(1)
                ),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getViewModel(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                savedStateMap = mapOf("show7Days" to false, "showSystem" to false)
            )
        val uiState = getPermissionUsageDetailsUiState(underTest)

        assertThat(uiState.containsSystemAppUsage).isEqualTo(false)
    }

    @Test
    fun verifyNoSystemAppsAvailableInLast24Hours() = runTest {
        val accessTimeMillis = (getCurrentTime() - TimeUnit.DAYS.toMillis(2))
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    accessTimeMillis,
                    TimeUnit.MINUTES.toMillis(1)
                ),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(systemPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getViewModel(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                savedStateMap = mapOf("show7Days" to false, "showSystem" to false)
            )
        val uiState = getPermissionUsageDetailsUiState(underTest)

        assertThat(uiState.containsSystemAppUsage).isEqualTo(false)
    }

    @Test
    fun verify24HoursDataIsShown() = runTest {
        val accessStartWithIn24Hours = (getCurrentTime() - TimeUnit.HOURS.toMillis(5))
        val accessStartBefore24Hours = (getCurrentTime() - TimeUnit.HOURS.toMillis(30))
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    accessStartWithIn24Hours,
                    TimeUnit.MINUTES.toMillis(5)
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    accessStartBefore24Hours,
                    TimeUnit.MINUTES.toMillis(7)
                ),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest = getViewModel(CAMERA_PERMISSION_GROUP, discretePackageOps)
        val uiState = getPermissionUsageDetailsUiState(underTest)

        assertThat(uiState.appPermissionAccessUiInfoList.size).isEqualTo(1)
        val timelineRow = uiState.appPermissionAccessUiInfoList.first()
        assertThat(timelineRow.accessStartTime).isEqualTo(accessStartWithIn24Hours)
    }

    @Test
    fun verify7DaysDataIsShown() = runTest {
        val accessTimeWithIn24Hours = (getCurrentTime() - TimeUnit.HOURS.toMillis(5))
        val accessTimeBefore24Hours = (getCurrentTime() - TimeUnit.DAYS.toMillis(3))
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    accessTimeWithIn24Hours,
                    TimeUnit.MINUTES.toMillis(5)
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    accessTimeBefore24Hours,
                    TimeUnit.MINUTES.toMillis(7)
                ),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getViewModel(
                CAMERA_PERMISSION_GROUP,
                discretePackageOps,
                savedStateMap = mapOf("show7Days" to true, "showSystem" to false),
                is7DayToggleEnabled = true
            )

        val uiState = getPermissionUsageDetailsUiState(underTest)
        assertThat(uiState.appPermissionAccessUiInfoList.size).isEqualTo(2)
        // assert rows are sorted by access timestamps i.e. most recent entry first
        assertThat(uiState.appPermissionAccessUiInfoList.map { it.accessStartTime })
            .isInOrder(Comparator.reverseOrder<Long>())
        val lastRow = uiState.appPermissionAccessUiInfoList.last()
        assertThat(lastRow.accessStartTime).isEqualTo(accessTimeBefore24Hours)
    }

    @Test
    fun verifyDurationLabelIsShown() = runTest {
        val accessTimeMillis = (getCurrentTime() - TimeUnit.HOURS.toMillis(5))
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    accessTimeMillis,
                    TimeUnit.MINUTES.toMillis(5)
                ),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest = getViewModel(CAMERA_PERMISSION_GROUP, discretePackageOps)
        val uiState = getPermissionUsageDetailsUiState(underTest)

        assertThat(uiState.appPermissionAccessUiInfoList.size).isEqualTo(1)
        val timelineRow = uiState.appPermissionAccessUiInfoList.first()
        assertThat(timelineRow.summaryText).isEqualTo("Duration Summary")
    }

    @Test
    fun verifyDurationLabelIsNotShown() = runTest {
        val accessTimeMillis = (getCurrentTime() - TimeUnit.HOURS.toMillis(5))
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    accessTimeMillis,
                    TimeUnit.MINUTES.toMillis(1)
                ),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest = getViewModel(CAMERA_PERMISSION_GROUP, discretePackageOps)
        val uiState = getPermissionUsageDetailsUiState(underTest)

        assertThat(uiState.appPermissionAccessUiInfoList.size).isEqualTo(1)
        val timelineRow = uiState.appPermissionAccessUiInfoList.first()
        assertThat(timelineRow.summaryText).isNull()
    }

    @Test
    fun verifyEmergencyLocationAccessWithAttribution() = runTest {
        Assume.assumeTrue(SdkLevel.isAtLeastV())
        whenever(Flags.locationBypassPrivacyDashboardEnabled()).thenReturn(true)
        whenever(application.getString(anyInt())).thenReturn("emergency attr label")
        val accessTimeMillis = (getCurrentTime() - TimeUnit.HOURS.toMillis(5))
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_EMERGENCY_LOCATION, accessTimeMillis, -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getViewModel(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
            )
        val uiState = getPermissionUsageDetailsUiState(underTest)
        assertThat(uiState.appPermissionAccessUiInfoList.size).isEqualTo(1)
        val timelineRow = uiState.appPermissionAccessUiInfoList.first()
        assertThat(timelineRow.isEmergencyLocationAccess).isTrue()
        assertThat(timelineRow.showingAttribution).isTrue()
        assertThat(timelineRow.summaryText).isEqualTo("emergency attr label")
    }

    @Test
    fun verifyNewEmittedEventsAreCollected() = runTest {
        val accessTimeMillis = (getCurrentTime() - TimeUnit.HOURS.toMillis(5))
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    accessTimeMillis,
                    TimeUnit.MINUTES.toMillis(1)
                ),
            )
        val actualData =
            listOf(
                DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
            )
        val discretePackageOps = MutableStateFlow(emptyList<DiscretePackageOpsModel>())
        discretePackageOps.emit(emptyList())

        val underTest =
            getViewModel(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
            )
        val result by collectLastValue(underTest.permissionUsageDetailsUiStateFlow)
        var uiState = result as PermissionUsageDetailsUiState.Success
        assertThat(uiState.appPermissionAccessUiInfoList).isEmpty()

        // verify that the emitted value is collected
        discretePackageOps.emit(actualData)
        val updatedResult by collectLastValue(underTest.permissionUsageDetailsUiStateFlow)
        uiState = updatedResult as PermissionUsageDetailsUiState.Success
        assertThat(uiState.appPermissionAccessUiInfoList.size).isEqualTo(1)
    }

    private fun getPermissionGroupUsageDetailsUseCase(
        permissionGroup: String,
        discreteUsageFlow: Flow<List<DiscretePackageOpsModel>>,
    ): GetPermissionGroupUsageDetailsUseCase {
        val userRepository = FakeUserRepository(listOf(currentUser.identifier))
        val permissionRepository = FakePermissionRepository()
        val appOpUsageRepository = FakeAppOpRepository(emptyFlow(), discreteUsageFlow)
        val roleRepository = FakeRoleRepository()
        return GetPermissionGroupUsageDetailsUseCase(
            permissionGroup,
            packageRepository,
            permissionRepository,
            appOpUsageRepository,
            roleRepository,
            userRepository
        )
    }

    /** Get current time rounded at minute level. */
    private fun getCurrentTime(): Long {
        return System.currentTimeMillis() / ONE_MINUTE_MILLIS * ONE_MINUTE_MILLIS
    }

    private fun TestScope.getViewModel(
        permissionGroup: String,
        discretePackageOps: Flow<List<DiscretePackageOpsModel>>,
        savedStateMap: Map<String, Boolean> = mapOf("show7Days" to false, "showSystem" to false),
        is7DayToggleEnabled: Boolean = false,
        pkgRepository: PackageRepository = packageRepository
    ) =
        PermissionUsageDetailsViewModelV2(
            application,
            getPermissionGroupUsageDetailsUseCase(permissionGroup, discretePackageOps),
            SavedStateHandle(savedStateMap),
            permissionGroup,
            scope = backgroundScope,
            StandardTestDispatcher(testScheduler),
            is7DayToggleEnabled = is7DayToggleEnabled,
            packageRepository = pkgRepository
        )

    private fun TestScope.getPermissionUsageDetailsUiState(
        viewModel: PermissionUsageDetailsViewModelV2
    ): PermissionUsageDetailsUiState.Success {
        val result by collectLastValue(viewModel.permissionUsageDetailsUiStateFlow)
        return result as PermissionUsageDetailsUiState.Success
    }

    private fun getPackageInfoModel(
        packageName: String,
        requestedPermissions: List<String> = listOf(CAMERA_PERMISSION, COARSE_LOCATION_PERMISSION),
        permissionsFlags: List<Int> =
            listOf(
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                PackageInfo.REQUESTED_PERMISSION_GRANTED
            ),
        applicationFlags: Int = 0,
    ) = PackageInfoModel(packageName, requestedPermissions, permissionsFlags, applicationFlags)

    companion object {
        private const val ONE_MINUTE_MILLIS = 60_000
        private val COARSE_LOCATION_PERMISSION = android.Manifest.permission.ACCESS_COARSE_LOCATION
        private val CAMERA_PERMISSION = android.Manifest.permission.CAMERA

        private val LOCATION_PERMISSION_GROUP = android.Manifest.permission_group.LOCATION
        private val CAMERA_PERMISSION_GROUP = android.Manifest.permission_group.CAMERA
    }
}
