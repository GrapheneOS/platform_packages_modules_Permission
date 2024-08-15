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
import android.permission.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.appops.data.model.v31.DiscretePackageOpsModel
import com.android.permissioncontroller.appops.data.model.v31.DiscretePackageOpsModel.DiscreteOpModel
import com.android.permissioncontroller.permission.domain.model.v31.PermissionTimelineUsageModel
import com.android.permissioncontroller.permission.domain.model.v31.PermissionTimelineUsageModelWrapper
import com.android.permissioncontroller.permission.domain.usecase.v31.GetPermissionGroupUsageDetailsUseCase
import com.android.permissioncontroller.permission.domain.usecase.v31.TELECOM_PACKAGE
import com.android.permissioncontroller.pm.data.model.v31.PackageAttributionModel
import com.android.permissioncontroller.pm.data.model.v31.PackageInfoModel
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import com.android.permissioncontroller.tests.mocking.appops.data.repository.FakeAppOpRepository
import com.android.permissioncontroller.tests.mocking.coroutines.collectLastValue
import com.android.permissioncontroller.tests.mocking.permission.data.repository.FakePermissionRepository
import com.android.permissioncontroller.tests.mocking.pm.data.repository.FakePackageRepository
import com.android.permissioncontroller.tests.mocking.role.data.repository.FakeRoleRepository
import com.android.permissioncontroller.tests.mocking.user.data.repository.FakeUserRepository
import com.android.permissioncontroller.user.data.repository.v31.UserRepository
import com.google.common.truth.Truth
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
class GetPermissionGroupUsageDetailsUseCaseTest {
    @Mock private lateinit var application: PermissionControllerApplication
    @Mock private lateinit var context: Context

    private lateinit var mockitoSession: MockitoSession
    private lateinit var packageInfos: MutableMap<String, PackageInfoModel>

    private val currentUser = android.os.Process.myUserHandle()
    private val privateProfile = UserHandle.of(10)
    private val guestUser = UserHandle.of(20)

    private val testPackageName = "test.package"
    private val guestUserPkgName = "test.package.guest"
    private val exemptedPkgName = "test.exempted.package"
    private val systemPackageName = "test.package.system"

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

        packageInfos =
            mapOf(
                    testPackageName to getPackageInfoModel(testPackageName),
                    guestUserPkgName to getPackageInfoModel(guestUserPkgName),
                    exemptedPkgName to getPackageInfoModel(exemptedPkgName),
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
        mockitoSession.finishMocking()
    }

    @Test
    fun guestUserUsagesAreFiltered() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_COARSE_LOCATION, MINUTES.toMillis(1), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(5), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                    DiscretePackageOpsModel(guestUserPkgName, guestUser.identifier, appOpEvents)
                )
            )
        }

        val underTest =
            getPermissionGroupUsageDetailsUseCase(LOCATION_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(2)
        Truth.assertThat(permissionTimelineUsages.map { it.userId })
            .doesNotContain(guestUser.identifier)
    }

    @Test
    fun quiteProfileAndShowUsageInQuietMode() = runTest {
        Assume.assumeTrue(SdkLevel.isAtLeastV())
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(1), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(5), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                    DiscretePackageOpsModel(
                        testPackageName,
                        privateProfile.identifier,
                        appOpEvents
                    ),
                )
            )
        }

        val underTest =
            getPermissionGroupUsageDetailsUseCase(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                userRepository =
                    FakeUserRepository(
                        currentUserProfiles =
                            listOf(currentUser.identifier, privateProfile.identifier),
                        quietUserProfiles = listOf(privateProfile.identifier),
                        showInQuiteModeProfiles = listOf(privateProfile.identifier)
                    )
            )
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(4)
        Truth.assertThat(permissionTimelineUsages.map { it.userId }.toSet())
            .isEqualTo(setOf(currentUser.identifier, privateProfile.identifier))
    }

    @Test
    fun quietProfileAndDoNotShowInQuietMode() = runTest {
        Assume.assumeTrue(SdkLevel.isAtLeastV())
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(1), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(5), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                    DiscretePackageOpsModel(
                        testPackageName,
                        privateProfile.identifier,
                        appOpEvents
                    ),
                )
            )
        }

        val underTest =
            getPermissionGroupUsageDetailsUseCase(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                userRepository =
                    FakeUserRepository(
                        currentUserProfiles =
                            listOf(currentUser.identifier, privateProfile.identifier),
                        quietUserProfiles = listOf(privateProfile.identifier),
                    )
            )
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(2)
        Truth.assertThat(permissionTimelineUsages.map { it.userId })
            .contains(currentUser.identifier)
        Truth.assertThat(permissionTimelineUsages.map { it.userId })
            .doesNotContain(privateProfile.identifier)
    }

    @Test
    fun exemptedPackageIsFiltered() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(1), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(5), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                    DiscretePackageOpsModel(exemptedPkgName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getPermissionGroupUsageDetailsUseCase(LOCATION_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        val actualPackages = permissionTimelineUsages.map { it.packageName }
        Truth.assertThat(actualPackages).contains(testPackageName)
        Truth.assertThat(actualPackages).doesNotContain(exemptedPkgName)
    }

    @Test
    fun packageNoLongerRequestingPermissionIsFiltered() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(1), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(5), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }
        // package no more request location permission
        packageInfos[testPackageName] =
            getPackageInfoModel(testPackageName, requestedPermissions = listOf(CAMERA_PERMISSION))

        val underTest =
            getPermissionGroupUsageDetailsUseCase(LOCATION_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages).isEmpty()
    }

    @Test
    fun discreteAccessesAreClustered() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(1), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(2), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(3), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getPermissionGroupUsageDetailsUseCase(LOCATION_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        val timelineModel = permissionTimelineUsages.first()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(3))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(3))
    }

    @Test
    fun continuousAccessesAreClustered() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(1),
                    MINUTES.toMillis(1)
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(2),
                    MINUTES.toMillis(1)
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(3),
                    MINUTES.toMillis(2)
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
            getPermissionGroupUsageDetailsUseCase(CAMERA_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        val timelineModel = permissionTimelineUsages.first()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(4))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(4))
    }

    @Test
    fun accessesAreClusteredAcrossHours() = runTest {
        val hours3 = HOURS.toMillis(3)
        val hours4 = HOURS.toMillis(4)
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_FINE_LOCATION,
                    hours3 + MINUTES.toMillis(59),
                    -1
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_FINE_LOCATION,
                    hours4 + MINUTES.toMillis(0),
                    -1
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_FINE_LOCATION,
                    hours4 + MINUTES.toMillis(1),
                    -1
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
            getPermissionGroupUsageDetailsUseCase(LOCATION_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        val timelineModel = permissionTimelineUsages.first()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(hours3 + MINUTES.toMillis(59))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(hours4 + MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(3))
    }

    @Test
    fun overlappingDurationAreClustered() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(1),
                    MINUTES.toMillis(3)
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(3),
                    MINUTES.toMillis(2)
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
            getPermissionGroupUsageDetailsUseCase(CAMERA_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        val timelineModel = permissionTimelineUsages.first()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(4))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(4))
    }

    @Test
    fun discreteAccessesAreNotClustered() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(1), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(3), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getPermissionGroupUsageDetailsUseCase(LOCATION_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(2)
        var timelineModel = permissionTimelineUsages.first()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(1))

        timelineModel = permissionTimelineUsages.last()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(3))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(3))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(1))
    }

    @Test
    fun continuousAccessesAreNotClustered() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(1),
                    MINUTES.toMillis(3)
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(5),
                    MINUTES.toMillis(5)
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
            getPermissionGroupUsageDetailsUseCase(CAMERA_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(2)
        var timelineModel = permissionTimelineUsages.first()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(3))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(3))

        timelineModel = permissionTimelineUsages.last()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(5))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(9))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(5))
    }

    @Test
    fun singleDiscreteAccess() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(1), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getPermissionGroupUsageDetailsUseCase(LOCATION_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        val timelineModel = permissionTimelineUsages.first()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(1))
    }

    @Test
    fun singleContinuousAccess() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(1),
                    MINUTES.toMillis(3)
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
            getPermissionGroupUsageDetailsUseCase(CAMERA_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        val timelineModel = permissionTimelineUsages.first()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(3))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(3))
    }

    @Test
    fun unexpectedContinuousAccess() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(1),
                    MINUTES.toMillis(2)
                ),
                // This entry says the camera was accessed for 15 minutes starting at minute 3
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(3),
                    MINUTES.toMillis(15)
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(4),
                    MINUTES.toMillis(1)
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_CAMERA,
                    MINUTES.toMillis(6),
                    MINUTES.toMillis(1)
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
            getPermissionGroupUsageDetailsUseCase(CAMERA_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        val timelineModel = permissionTimelineUsages.first()
        Truth.assertThat(timelineModel.accessStartMillis).isEqualTo(MINUTES.toMillis(1))
        Truth.assertThat(timelineModel.accessEndMillis).isEqualTo(MINUTES.toMillis(17))
        Truth.assertThat(timelineModel.durationMillis).isEqualTo(MINUTES.toMillis(17))
    }

    @Test
    fun verifyUserSensitiveFlags() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_COARSE_LOCATION, MINUTES.toMillis(1), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                    DiscretePackageOpsModel(systemPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }
        val permissionFlags =
            mapOf<String, Int>(
                COARSE_LOCATION_PERMISSION to
                    PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
            )

        val underTest =
            getPermissionGroupUsageDetailsUseCase(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                permissionFlags = permissionFlags,
            )
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(2)
        val packageModelsMap = permissionTimelineUsages.groupBy { it.packageName }
        val systemPackageModels = packageModelsMap[systemPackageName]
        Truth.assertThat(systemPackageModels?.size).isEqualTo(1)
        Truth.assertThat(systemPackageModels?.first()?.isUserSensitive).isEqualTo(true)
        val testPackageModels = packageModelsMap[testPackageName]
        Truth.assertThat(testPackageModels?.size).isEqualTo(1)
        Truth.assertThat(testPackageModels?.first()?.isUserSensitive).isEqualTo(true)
    }

    @Test
    fun verifyNotUserSensitiveFlagsForSystemPackage() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_COARSE_LOCATION, MINUTES.toMillis(1), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(systemPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }
        val permissionFlags =
            mapOf<String, Int>(
                // 0 represents not user sensitive
                COARSE_LOCATION_PERMISSION to 0
            )

        val underTest =
            getPermissionGroupUsageDetailsUseCase(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                permissionFlags = permissionFlags,
            )
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        Truth.assertThat(permissionTimelineUsages.first().isUserSensitive).isEqualTo(false)
    }

    @Test
    fun verifyCameraUserSensitiveFlagsForTelecomPackage() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_CAMERA, MINUTES.toMillis(1), -1),
            )
        packageInfos[TELECOM_PACKAGE] = getPackageInfoModel(TELECOM_PACKAGE)
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(TELECOM_PACKAGE, currentUser.identifier, appOpEvents),
                )
            )
        }
        val permissionFlags =
            mapOf<String, Int>(
                CAMERA_PERMISSION to PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
            )

        val underTest =
            getPermissionGroupUsageDetailsUseCase(
                CAMERA_PERMISSION_GROUP,
                discretePackageOps,
                permissionFlags = permissionFlags,
            )
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        Truth.assertThat(permissionTimelineUsages.first().packageName).isEqualTo(TELECOM_PACKAGE)
        Truth.assertThat(permissionTimelineUsages.first().isUserSensitive).isEqualTo(false)
    }

    @Test
    fun verifyLocationUserSensitiveFlagsForTelecomPackage() = runTest {
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_COARSE_LOCATION, MINUTES.toMillis(1), -1),
            )
        packageInfos[TELECOM_PACKAGE] = getPackageInfoModel(TELECOM_PACKAGE)
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(TELECOM_PACKAGE, currentUser.identifier, appOpEvents),
                )
            )
        }
        val permissionFlags =
            mapOf<String, Int>(
                COARSE_LOCATION_PERMISSION to
                    PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
            )

        val underTest =
            getPermissionGroupUsageDetailsUseCase(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                permissionFlags = permissionFlags,
            )
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
        Truth.assertThat(permissionTimelineUsages.first().packageName).isEqualTo(TELECOM_PACKAGE)
        Truth.assertThat(permissionTimelineUsages.first().isUserSensitive).isEqualTo(true)
    }

    @Test
    fun verifyAttributionTagsAreGroupedAndClustered() = runTest {
        val appOpEvents =
            listOf(
                // These 3 entries should be grouped and clustered.
                DiscreteOpModel(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    MINUTES.toMillis(1),
                    -1,
                    "tag1"
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    MINUTES.toMillis(2),
                    -1,
                    "tag1"
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    MINUTES.toMillis(3),
                    -1,
                    "tag3"
                ),
                // The access at minute 4 should not be grouped or clustered.
                DiscreteOpModel(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    MINUTES.toMillis(4),
                    -1,
                    "tag2"
                ),
                DiscreteOpModel(
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    MINUTES.toMillis(8),
                    -1,
                    "tag2"
                ),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }
        val packageAttributions = mutableMapOf<String, PackageAttributionModel>()
        // tag1 and tag3 refers to same attribution label.
        val attributionTagToLabelRes = mapOf("tag1" to 100, "tag2" to 200, "tag3" to 100)
        val attributionsMap = mapOf(100 to "Tag1 Label", 200 to "Tag2 Label")
        packageAttributions[testPackageName] =
            PackageAttributionModel(
                testPackageName,
                true,
                attributionTagToLabelRes,
                attributionsMap
            )

        val underTest =
            getPermissionGroupUsageDetailsUseCase(
                LOCATION_PERMISSION_GROUP,
                discretePackageOps,
                packageRepository = FakePackageRepository(packageInfos, packageAttributions)
            )
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(3)
        val labelToTimelineModelsMap = permissionTimelineUsages.groupBy { it.attributionLabel }
        val tag1LabelModels = labelToTimelineModelsMap["Tag1 Label"]
        val tag2LabelModels = labelToTimelineModelsMap["Tag2 Label"]

        Truth.assertThat(tag1LabelModels?.size).isEqualTo(1)
        Truth.assertThat(tag1LabelModels?.first()?.attributionLabel).isEqualTo("Tag1 Label")
        Truth.assertThat(tag1LabelModels?.first()?.attributionTags).isEqualTo(setOf("tag1", "tag3"))

        Truth.assertThat(tag2LabelModels?.size).isEqualTo(2)
        Truth.assertThat(tag2LabelModels?.first()?.attributionLabel).isEqualTo("Tag2 Label")
        Truth.assertThat(tag2LabelModels?.first()?.attributionTags).isEqualTo(setOf("tag2"))
        Truth.assertThat(tag2LabelModels?.last()?.attributionLabel).isEqualTo("Tag2 Label")
        Truth.assertThat(tag2LabelModels?.last()?.attributionTags).isEqualTo(setOf("tag2"))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LOCATION_BYPASS_PRIVACY_DASHBOARD_ENABLED)
    fun emergencyAccessesAreNotClusteredWithRegularAccesses() = runTest {
        Assume.assumeTrue(SdkLevel.isAtLeastV())
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_EMERGENCY_LOCATION, MINUTES.toMillis(1), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_FINE_LOCATION, MINUTES.toMillis(2), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getPermissionGroupUsageDetailsUseCase(LOCATION_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(2)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LOCATION_BYPASS_PRIVACY_DASHBOARD_ENABLED)
    fun emergencyAccessesAreClustered() = runTest {
        Assume.assumeTrue(SdkLevel.isAtLeastV())
        val appOpEvents =
            listOf(
                DiscreteOpModel(AppOpsManager.OPSTR_EMERGENCY_LOCATION, MINUTES.toMillis(1), -1),
                DiscreteOpModel(AppOpsManager.OPSTR_EMERGENCY_LOCATION, MINUTES.toMillis(2), -1),
            )
        val discretePackageOps = flow {
            emit(
                listOf(
                    DiscretePackageOpsModel(testPackageName, currentUser.identifier, appOpEvents),
                )
            )
        }

        val underTest =
            getPermissionGroupUsageDetailsUseCase(LOCATION_PERMISSION_GROUP, discretePackageOps)
        val permissionTimelineUsages = getResult(underTest, this)

        Truth.assertThat(permissionTimelineUsages.size).isEqualTo(1)
    }

    private fun TestScope.getResult(
        useCase: GetPermissionGroupUsageDetailsUseCase,
        coroutineScope: CoroutineScope
    ): List<PermissionTimelineUsageModel> {
        val usages by collectLastValue(useCase(coroutineScope))
        return (usages as PermissionTimelineUsageModelWrapper.Success).timelineUsageModels
    }

    private fun getPermissionGroupUsageDetailsUseCase(
        permissionGroup: String,
        discreteUsageFlow: Flow<List<DiscretePackageOpsModel>>,
        permissionFlags: Map<String, Int> = emptyMap(),
        userRepository: UserRepository = FakeUserRepository(listOf(currentUser.identifier)),
        packageRepository: PackageRepository = FakePackageRepository(packageInfos)
    ): GetPermissionGroupUsageDetailsUseCase {
        val permissionRepository = FakePermissionRepository(permissionFlags)
        val appOpUsageRepository = FakeAppOpRepository(emptyFlow(), discreteUsageFlow)
        val roleRepository = FakeRoleRepository(setOf(exemptedPkgName))
        return GetPermissionGroupUsageDetailsUseCase(
            permissionGroup,
            packageRepository,
            permissionRepository,
            appOpUsageRepository,
            roleRepository,
            userRepository
        )
    }

    private fun getPackageInfoModel(
        packageName: String,
        requestedPermissions: List<String> =
            listOf(COARSE_LOCATION_PERMISSION, FINE_LOCATION_PERMISSION, CAMERA_PERMISSION),
        permissionsFlags: List<Int> =
            listOf(
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                PackageInfo.REQUESTED_PERMISSION_GRANTED
            ),
        applicationFlags: Int = 0,
    ) = PackageInfoModel(packageName, requestedPermissions, permissionsFlags, applicationFlags)

    companion object {
        private val FINE_LOCATION_PERMISSION = android.Manifest.permission.ACCESS_FINE_LOCATION
        private val COARSE_LOCATION_PERMISSION = android.Manifest.permission.ACCESS_COARSE_LOCATION
        private val CAMERA_PERMISSION = android.Manifest.permission.CAMERA

        private val LOCATION_PERMISSION_GROUP = android.Manifest.permission_group.LOCATION
        private val CAMERA_PERMISSION_GROUP = android.Manifest.permission_group.CAMERA
    }
}
