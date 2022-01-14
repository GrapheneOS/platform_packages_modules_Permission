/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.auto

import android.Manifest
import android.app.Service.START_NOT_STICKY
import android.app.Service.START_STICKY
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_BASELINE
import android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness
import java.io.File

@RunWith(AndroidJUnit4::class)
class DrivingDecisionReminderServiceTest {

    companion object {
        val application = Mockito.mock(PermissionControllerApplication::class.java)
        const val TEST_PACKAGE_1 = "com.package.test1"
        const val TEST_PACKAGE_1_LABEL = "App1"
        const val TEST_PACKAGE_2 = "com.package.test2"
        const val TEST_PACKAGE_2_LABEL = "App2"
        const val TEST_PACKAGE_3 = "com.package.test3"
        const val TEST_PACKAGE_3_LABEL = "App3"
        const val LOCATION_PERMISSION_GROUP = Manifest.permission_group.LOCATION
        const val MICROPHONE_PERMISSION_GROUP = Manifest.permission_group.MICROPHONE
        const val CALENDAR_PERMISSION_GROUP = Manifest.permission_group.CALENDAR
    }

    @Mock
    lateinit var userHandle: UserHandle

    @Mock
    lateinit var car: Car

    @Mock
    lateinit var carUxRestrictionsManager: CarUxRestrictionsManager

    @Captor
    var uxRestrictionsChangedListener:
        ArgumentCaptor<CarUxRestrictionsManager.OnUxRestrictionsChangedListener>? = null

    @Captor
    var carServiceLifecycleListener: ArgumentCaptor<Car.CarServiceLifecycleListener>? = null

    private val noRestrictions = CarUxRestrictions.Builder(/* reqOpt= */ false,
        UX_RESTRICTIONS_BASELINE, System.currentTimeMillis()).build()
    private val fullRestrictions = CarUxRestrictions.Builder(/* reqOpt= */ true,
        UX_RESTRICTIONS_FULLY_RESTRICTED, System.currentTimeMillis()).build()

    private lateinit var mockitoSession: MockitoSession
    private lateinit var filesDir: File
    private lateinit var reminderService: DrivingDecisionReminderService
    private lateinit var testIntent: Intent

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession = ExtendedMockito.mockitoSession()
            .mockStatic(PermissionControllerApplication::class.java)
            .mockStatic(Car::class.java)
            .strictness(Strictness.LENIENT).startMocking()
        `when`(PermissionControllerApplication.get()).thenReturn(application)
        filesDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir()
        `when`(application.filesDir).thenReturn(filesDir)
        `when`(car.getCarManager(eq(Car.CAR_UX_RESTRICTION_SERVICE)))
            .thenReturn(carUxRestrictionsManager)
        testIntent = createIntent(TEST_PACKAGE_1, LOCATION_PERMISSION_GROUP)
        reminderService = Mockito.spy(DrivingDecisionReminderService())
        doNothing().`when`(reminderService).showRecentGrantDecisionsPostDriveNotification()
        doReturn(InstrumentationRegistry.getInstrumentation().getTargetContext().resources)
            .`when`(reminderService).resources
        doReturn(ApplicationProvider.getApplicationContext()).`when`(reminderService)
            .applicationContext

        doReturn(TEST_PACKAGE_1_LABEL).`when`(reminderService)
            .getLabelForPackage(TEST_PACKAGE_1, userHandle)
        doReturn(TEST_PACKAGE_2_LABEL).`when`(reminderService)
            .getLabelForPackage(TEST_PACKAGE_2, userHandle)
        doReturn(TEST_PACKAGE_3_LABEL).`when`(reminderService)
            .getLabelForPackage(TEST_PACKAGE_3, userHandle)

        // TODO(b/209026677) - move this to @BeforeClass
        // only run tests on Auto. Placed after all lateinit variables have been created
        assumeTrue(isAutomotiveDevice())
    }

    private fun createIntent(packageName: String, permissionGroup: String): Intent {
        return DrivingDecisionReminderService.createIntent(
            ApplicationProvider.getApplicationContext(),
            packageName,
            permissionGroup,
            userHandle)
    }

    private fun isAutomotiveDevice(): Boolean {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    @After
    fun finish() {
        mockitoSession.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()
    }

    @Test
    fun createIntent_success() {
        assertThat(testIntent.getStringExtra(DrivingDecisionReminderService.EXTRA_PACKAGE_NAME))
            .isEqualTo(TEST_PACKAGE_1)
        assertThat(testIntent.getStringExtra(DrivingDecisionReminderService.EXTRA_PERMISSION_GROUP))
            .isEqualTo(LOCATION_PERMISSION_GROUP)
        assertThat(testIntent
            .getParcelableExtra<UserHandle>(DrivingDecisionReminderService.EXTRA_USER))
            .isEqualTo(userHandle)
    }

    @Test
    fun onStartCommand_invalidIntent_startNotSticky() {
        val result = reminderService.onStartCommand(null, /* flags= */ 0, /* startId= */ 0)
        assertThat(result).isEqualTo(START_NOT_STICKY)
        ExtendedMockito.verify({
            Car.createCar(any(Context::class.java), any(),
                anyLong(), any(Car.CarServiceLifecycleListener::class.java))
        }, never())
    }

    @Test
    fun onStartCommand_validIntent_startSticky() {
        val result = reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        assertThat(result).isEqualTo(START_STICKY)
    }

    @Test
    fun onStartCommand_multipleStarts_onlyCreatesCarOnce() {
        reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        val result = reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        assertThat(result).isEqualTo(START_STICKY)

        captureCreateCarLifecycleListener()
    }

    @Test
    fun onStartCommand_carNotReady_stopsSelf() {
        reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        captureCreateCarLifecycleListener()

        carServiceLifecycleListener?.value?.onLifecycleChanged(car, /* ready= */ false)

        verify(reminderService).stopSelf()
    }

    @Test
    fun onCarReady_registersRestrictionListener_restrictionChangeToUnrestricted_showNotification() {
        reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        captureCreateCarLifecycleListener()
        carServiceLifecycleListener?.value?.onLifecycleChanged(car, /* ready= */ true)
        captureUxRestrictionsListener()

        uxRestrictionsChangedListener?.value?.onUxRestrictionsChanged(noRestrictions)

        verify(reminderService).showRecentGrantDecisionsPostDriveNotification()
    }

    @Test
    fun onCarReady_registersUxRestrictionListener_restrictionChangeToRestricted_doesNothing() {
        reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        captureCreateCarLifecycleListener()
        carServiceLifecycleListener?.value?.onLifecycleChanged(car, /* ready= */ true)
        captureUxRestrictionsListener()

        uxRestrictionsChangedListener?.value?.onUxRestrictionsChanged(fullRestrictions)

        verify(reminderService, never()).showRecentGrantDecisionsPostDriveNotification()
    }

    @Test
    fun createNotificationContent_1_app_1_permission() {
        reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)

        assertThat(reminderService.createNotificationContent())
            .isEqualTo("While driving, you gave App1 access to Location")
    }

    @Test
    fun createNotificationContent_1_app_2_permissions() {
        reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        reminderService.onStartCommand(createIntent(TEST_PACKAGE_1, MICROPHONE_PERMISSION_GROUP),
            /* flags= */ 0, /* startId= */ 0)

        assertThat(reminderService.createNotificationContent())
            .isEqualTo("While driving, you gave App1 access to Location & Microphone")
    }

    @Test
    fun createNotificationContent_1_app_3_permissions() {
        reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        reminderService.onStartCommand(createIntent(TEST_PACKAGE_1, MICROPHONE_PERMISSION_GROUP),
            /* flags= */ 0, /* startId= */ 0)
        reminderService.onStartCommand(createIntent(TEST_PACKAGE_1, CALENDAR_PERMISSION_GROUP),
            /* flags= */ 0, /* startId= */ 0)

        assertThat(reminderService.createNotificationContent())
            .isEqualTo("While driving, you granted 3 permissions to App1")
    }

    @Test
    fun createNotificationContent_2_apps() {
        reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        reminderService.onStartCommand(createIntent(TEST_PACKAGE_2, MICROPHONE_PERMISSION_GROUP),
            /* flags= */ 0, /* startId= */ 0)

        assertThat(reminderService.createNotificationContent())
            .isEqualTo("While driving, you gave App1 & 1 other app access")
    }

    @Test
    fun createNotificationContent_3_apps() {
        reminderService.onStartCommand(testIntent, /* flags= */ 0, /* startId= */ 0)
        reminderService.onStartCommand(createIntent(TEST_PACKAGE_2, MICROPHONE_PERMISSION_GROUP),
            /* flags= */ 0, /* startId= */ 0)
        reminderService.onStartCommand(createIntent(TEST_PACKAGE_3, MICROPHONE_PERMISSION_GROUP),
            /* flags= */ 0, /* startId= */ 0)

        assertThat(reminderService.createNotificationContent())
            .isEqualTo("While driving, you gave App1 & 2 other apps access")
    }

    private fun captureCreateCarLifecycleListener() {
        ExtendedMockito.verify {
            Car.createCar(any(Context::class.java), any(),
                anyLong(), carServiceLifecycleListener?.capture())
        }
    }

    private fun captureUxRestrictionsListener() {
        verify(carUxRestrictionsManager).registerListener(uxRestrictionsChangedListener?.capture())
    }
}