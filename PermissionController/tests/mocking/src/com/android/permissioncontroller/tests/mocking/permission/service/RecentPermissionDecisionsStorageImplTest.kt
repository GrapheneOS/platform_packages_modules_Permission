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

package com.android.permissioncontroller.tests.mocking.permission.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.Context
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PermissionDecision
import com.android.permissioncontroller.permission.service.RecentPermissionDecisionsStorage
import com.android.permissioncontroller.permission.service.RecentPermissionDecisionsStorage.Companion.DEFAULT_MAX_DATA_AGE_MS
import com.android.permissioncontroller.permission.service.RecentPermissionDecisionsStorageImpl
import com.android.permissioncontroller.permission.service.RecentPermissionDecisionsStorageImpl.Companion.DEFAULT_CLEAR_OLD_DECISIONS_CHECK_FREQUENCY
import com.android.permissioncontroller.permission.utils.Utils.PROPERTY_PERMISSION_DECISIONS_CHECK_OLD_FREQUENCY_MILLIS
import com.android.permissioncontroller.permission.utils.Utils.PROPERTY_PERMISSION_DECISIONS_MAX_DATA_AGE_MILLIS
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RecentPermissionDecisionsStorageImplTest {

    companion object {
        val application = Mockito.mock(PermissionControllerApplication::class.java)
    }

    private val jan12020 = Date(2020, 0, 1).time
    private val jan22020 = Date(2020, 0, 2).time

    private val mapPackageName = "package.test.map"

    private val musicCalendarGrant = PermissionDecision(
        "package.test.music", "calendar", jan12020, false)
    private val mapLocationGrant = PermissionDecision(
        mapPackageName, "location", jan12020, true)
    private val mapLocationDenied = PermissionDecision(
        mapPackageName, "location", jan12020, false)
    private val mapMicrophoneGrant = PermissionDecision(
        mapPackageName, "microphone", jan12020, true)
    private val parkingLocationGrant = PermissionDecision(
        "package.test.parking", "location", jan22020, true)
    private val podcastMicrophoneGrant = PermissionDecision(
        "package.test.podcast", "microphone", jan22020, true)

    @Mock
    lateinit var jobScheduler: JobScheduler

    @Mock
    lateinit var existingJob: JobInfo

    private lateinit var context: Context
    private lateinit var storage: RecentPermissionDecisionsStorage
    private lateinit var mockitoSession: MockitoSession
    private lateinit var filesDir: File

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession = ExtendedMockito.mockitoSession()
            .mockStatic(PermissionControllerApplication::class.java)
            .mockStatic(DeviceConfig::class.java)
            .strictness(Strictness.LENIENT).startMocking()
        `when`(PermissionControllerApplication.get()).thenReturn(application)
        context = ApplicationProvider.getApplicationContext()
        filesDir = context.cacheDir
        `when`(application.filesDir).thenReturn(filesDir)
        `when`(jobScheduler.schedule(any())).thenReturn(JobScheduler.RESULT_SUCCESS)
        `when`(
            DeviceConfig.getLong(eq(DeviceConfig.NAMESPACE_PERMISSIONS),
                eq(PROPERTY_PERMISSION_DECISIONS_CHECK_OLD_FREQUENCY_MILLIS),
                eq(DEFAULT_CLEAR_OLD_DECISIONS_CHECK_FREQUENCY)))
            .thenReturn(DEFAULT_CLEAR_OLD_DECISIONS_CHECK_FREQUENCY)
        `when`(
            DeviceConfig.getLong(eq(DeviceConfig.NAMESPACE_PERMISSIONS),
                eq(PROPERTY_PERMISSION_DECISIONS_MAX_DATA_AGE_MILLIS),
                eq(DEFAULT_MAX_DATA_AGE_MS)))
            .thenReturn(DEFAULT_MAX_DATA_AGE_MS)
    }

    private fun init() {
        storage = RecentPermissionDecisionsStorageImpl(context, jobScheduler)
    }

    @After
    fun cleanup() = runBlocking {
        mockitoSession.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()

        storage.clearPermissionDecisions()
    }

    @Test
    fun init_noExistingJob_schedulesNewJob() {
        `when`(jobScheduler.getPendingJob(Constants.OLD_PERMISSION_DECISION_CLEANUP_JOB_ID))
            .thenReturn(null)
        init()

        Mockito.verify(jobScheduler).schedule(any())
    }

    @Test
    fun init_existingJob_doesNotScheduleNewJob() {
        `when`(existingJob.intervalMillis).thenReturn(DEFAULT_CLEAR_OLD_DECISIONS_CHECK_FREQUENCY)
        `when`(jobScheduler.getPendingJob(Constants.OLD_PERMISSION_DECISION_CLEANUP_JOB_ID))
            .thenReturn(existingJob)
        init()

        Mockito.verify(jobScheduler, Mockito.never()).schedule(any())
    }

    @Test
    fun init_existingJob_differentFrequency_schedulesNewJob() {
        `when`(existingJob.intervalMillis)
            .thenReturn(DEFAULT_CLEAR_OLD_DECISIONS_CHECK_FREQUENCY + 1)
        `when`(jobScheduler.getPendingJob(Constants.OLD_PERMISSION_DECISION_CLEANUP_JOB_ID))
            .thenReturn(existingJob)
        init()

        Mockito.verify(jobScheduler).schedule(any())
    }

    @Test
    fun loadPermissionDecisions_noData_returnsEmptyList() {
        init()
        runBlocking {
            assertThat(storage.loadPermissionDecisions()).isEmpty()
        }
    }

    @Test
    fun storePermissionDecision_singleDecision_writeSuccessAndReturnOnLoad() {
        init()
        runBlocking {
            assertThat(storage.storePermissionDecision(mapLocationGrant)).isTrue()
            assertThat(storage.loadPermissionDecisions()).containsExactly(mapLocationGrant)
        }
    }

    @Test
    fun storePermissionDecision_roundsTimeDownToDate() {
        init()
        runBlocking {
            val laterInTheDayGrant = musicCalendarGrant.copy(
                decisionTime = (musicCalendarGrant.decisionTime + (5 * 60 * 60 * 1000)))
            assertThat(storage.storePermissionDecision(laterInTheDayGrant)).isTrue()
            assertThat(storage.loadPermissionDecisions()).containsExactly(musicCalendarGrant)
        }
    }

    @Test
    fun storePermissionDecision_multipleDecisions_returnedOrderedByMostRecentlyAdded() {
        init()
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.storePermissionDecision(musicCalendarGrant)
            storage.storePermissionDecision(parkingLocationGrant)
            storage.storePermissionDecision(podcastMicrophoneGrant)
            assertThat(storage.loadPermissionDecisions())
                .containsExactly(musicCalendarGrant, mapLocationGrant, parkingLocationGrant,
                    podcastMicrophoneGrant)
        }
    }

    @Test
    fun storePermissionDecision_uniqueForPackagePermissionGroup() {
        init()
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.storePermissionDecision(mapLocationDenied)
            assertThat(storage.loadPermissionDecisions()).containsExactly(mapLocationDenied)
        }
    }

    @Test
    fun storePermissionDecision_ignoresExactDuplicates() {
        init()
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.storePermissionDecision(mapLocationGrant)
            assertThat(storage.loadPermissionDecisions()).containsExactly(mapLocationGrant)
        }
    }

    @Test
    fun clearPermissionDecisions_clearsExistingData() {
        init()
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.clearPermissionDecisions()
            assertThat(storage.loadPermissionDecisions()).isEmpty()
        }
    }

    @Test
    fun removePermissionDecisionsForPackage_removesDecisions() {
        init()
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.storePermissionDecision(musicCalendarGrant)
            storage.storePermissionDecision(mapMicrophoneGrant)
            storage.removePermissionDecisionsForPackage(mapPackageName)
            assertThat(storage.loadPermissionDecisions()).containsExactly(musicCalendarGrant)
        }
    }

    @Test
    fun removeOldData_removesOnlyOldData() {
        init()
        val todayDecision = parkingLocationGrant.copy(decisionTime = System.currentTimeMillis())
        val sixDaysAgoDecision = podcastMicrophoneGrant.copy(
            decisionTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6))
        val eightDaysAgoDecision = parkingLocationGrant.copy(
            decisionTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8))
        runBlocking {
            storage.storePermissionDecision(eightDaysAgoDecision)
            storage.storePermissionDecision(sixDaysAgoDecision)
            storage.storePermissionDecision(todayDecision)
            storage.removeOldData()

            // the times get rounded when persisting, so just check the package names
            val packageNames = storage.loadPermissionDecisions().map { it.packageName }
            assertThat(packageNames)
                .containsExactly(todayDecision.packageName, sixDaysAgoDecision.packageName)
        }
    }

    @Test
    fun updateDecisionsBySystemTimeDelta_lessThanOneDay_noChange() {
        init()
        runBlocking {
            storage.storePermissionDecision(musicCalendarGrant)
            storage.updateDecisionsBySystemTimeDelta(TimeUnit.HOURS.toMillis(12))

            assertThat(storage.loadPermissionDecisions()).containsExactly(musicCalendarGrant)
        }
    }

    @Test
    fun updateDecisionsBySystemTimeDelta_oneDayForward_shiftsData() {
        init()
        runBlocking {
            storage.storePermissionDecision(musicCalendarGrant)
            storage.updateDecisionsBySystemTimeDelta(TimeUnit.DAYS.toMillis(1))

            assertThat(storage.loadPermissionDecisions()).containsExactly(
                musicCalendarGrant.copy(
                    decisionTime = musicCalendarGrant.decisionTime + TimeUnit.DAYS.toMillis(1)
                )
            )
        }
    }

    @Test
    fun updateDecisionsBySystemTimeDelta_oneDayBackward_shiftsData() {
        init()
        runBlocking {
            storage.storePermissionDecision(musicCalendarGrant)
            storage.updateDecisionsBySystemTimeDelta(-TimeUnit.DAYS.toMillis(1))

            assertThat(storage.loadPermissionDecisions()).containsExactly(
                musicCalendarGrant.copy(
                    decisionTime = musicCalendarGrant.decisionTime - TimeUnit.DAYS.toMillis(1)
                )
            )
        }
    }
}