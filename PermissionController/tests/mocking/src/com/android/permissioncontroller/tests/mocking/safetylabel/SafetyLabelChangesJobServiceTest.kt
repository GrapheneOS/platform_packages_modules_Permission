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

package com.android.permissioncontroller.tests.mocking.safetylabel

import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.service.v34.SafetyLabelChangesJobService
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.Spy
import org.mockito.quality.Strictness

/** Tests for [SafetyLabelChangesJobService]. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class SafetyLabelChangesJobServiceTest {
    @Spy private val service = SafetyLabelChangesJobService()
    private val receiver = SafetyLabelChangesJobService.Receiver()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var mockitoSession: MockitoSession

    @Mock private lateinit var application: PermissionControllerApplication

    @Mock private lateinit var mockJobScheduler: JobScheduler

    @Mock private lateinit var mockNotificationManager: NotificationManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig::class.java)
                .mockStatic(PermissionControllerApplication::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()

        // Mock flags
        setSafetyLabelChangeNotificationsEnabled(true)
        setPermissionRationaleEnabled(true)

        // Mock application context
        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(application.resources).thenReturn(context.resources)
        whenever(application.applicationInfo).thenReturn(context.applicationInfo)
        whenever(application.applicationContext).thenReturn(application)

        // Mock services
        whenever(application.getSystemService(eq(NotificationManager::class.java)))
            .thenReturn(mockNotificationManager)
        whenever(application.getSystemService(eq(JobScheduler::class.java)))
            .thenReturn(mockJobScheduler)
        doNothing().`when`(service).jobFinished(any(), anyBoolean())
    }

    @After
    fun cleanup() {
        mockitoSession.finishMocking()
    }

    @Test
    fun flagsDisabled_onReceiveValidIntentAction_jobNotScheduled() {
        setSafetyLabelChangeNotificationsEnabled(false)

        receiver.onReceive(application, Intent(Intent.ACTION_BOOT_COMPLETED))

        verifyZeroInteractions(mockJobScheduler)
    }

    @Test
    fun flagsDisabled_onMainJobStart_notificationNotShown() {
        setSafetyLabelChangeNotificationsEnabled(false)

        val jobId = mockJobParamsForJobId(Constants.SAFETY_LABEL_CHANGES_JOB_ID)
        val jobStillRunning = service.onStartJob(jobId)

        assertThat(jobStillRunning).isEqualTo(false)

        verifyZeroInteractions(mockNotificationManager)
    }

    @Test
    fun onReceiveInvalidIntentAction_jobNotScheduled() {
        receiver.onReceive(application, Intent(Intent.ACTION_DEFAULT))

        verifyZeroInteractions(mockJobScheduler)
    }

    @Test
    fun onReceiveValidIntentAction_periodicJobScheduled() {
        receiver.onReceive(application, Intent(Intent.ACTION_BOOT_COMPLETED))

        val captor = ArgumentCaptor.forClass(JobInfo::class.java)
        verify(mockJobScheduler).schedule(captor.capture())
        assertThat(captor.value.id).isEqualTo(Constants.PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID)
    }

    @Test
    fun onStartPeriodicJob_scheduleJob() {
        val jobParams = mockJobParamsForJobId(Constants.PERIODIC_SAFETY_LABEL_CHANGES_JOB_ID)
        val jobStillRunning = service.onStartJob(jobParams)

        assertThat(jobStillRunning).isEqualTo(false)

        val captor = ArgumentCaptor.forClass(JobInfo::class.java)
        verify(mockJobScheduler, timeout(5000)).schedule(captor.capture())
        assertThat(captor.value.id).isEqualTo(Constants.SAFETY_LABEL_CHANGES_JOB_ID)
    }

    private fun waitForJobFinished() {
        verify(service, timeout(5000)).jobFinished(any(), anyBoolean())
    }

    private fun mockJobParamsForJobId(jobId: Int): JobParameters {
        val jobParameters = mock(JobParameters::class.java)
        whenever(jobParameters.jobId).thenReturn(jobId)
        return jobParameters
    }

    private fun setSafetyLabelChangeNotificationsEnabled(flagValue: Boolean) {
        whenever(
                DeviceConfig.getBoolean(
                    eq(DeviceConfig.NAMESPACE_PRIVACY),
                    eq(SafetyLabelConstants.SAFETY_LABEL_CHANGE_NOTIFICATIONS_ENABLED),
                    anyBoolean()))
            .thenReturn(flagValue)
    }

    private fun setPermissionRationaleEnabled(flagValue: Boolean) {
        whenever(
                DeviceConfig.getBoolean(
                    eq(DeviceConfig.NAMESPACE_PRIVACY),
                    eq(SafetyLabelConstants.PERMISSION_RATIONALE_ENABLED),
                    anyBoolean()))
            .thenReturn(flagValue)
    }
}