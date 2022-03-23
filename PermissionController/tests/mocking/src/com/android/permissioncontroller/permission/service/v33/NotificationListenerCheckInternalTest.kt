/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.service.v33

import android.app.job.JobParameters
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.DeviceConfig
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.permission.service.v33.NotificationListenerCheckInternal.NlsComponent
import com.android.permissioncontroller.permission.service.v33.NotificationListenerCheck.NotificationListenerCheckJobService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/**
 * Unit tests for [NotificationListenerCheckInternal]
 *
 * <p> Does not test notification as there are conflicts with being able to mock NotifiationManager
 * and PendintIntent.getBroadcast requiring a valid context. Notifications are tested in the CTS
 * integration tests
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class NotificationListenerCheckInternalTest {

    @Mock
    lateinit var notificationListenerCheckJobService: NotificationListenerCheckJobService

    private lateinit var context: Context
    private lateinit var mockitoSession: MockitoSession
    private lateinit var notificationListenerCheck: NotificationListenerCheckInternal

    private var shouldCancel = false

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()

        mockitoSession = ExtendedMockito.mockitoSession()
            .mockStatic(DeviceConfig::class.java)
            .strictness(Strictness.LENIENT).startMocking()

        notificationListenerCheck = runWithShellPermissionIdentity {
            NotificationListenerCheckInternal(context) { shouldCancel }
        }

        enableNotificationListenerChecker(true)
    }

    @After
    fun cleanup() {
        // cleanup NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE
        context.deleteFile(Constants.NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE)

        shouldCancel = false
        mockitoSession.finishMocking()
    }

    @Test
    fun getEnabledNotificationListenersAndNotifyIfNeeded_featureDisabled_finishJob() {
        enableNotificationListenerChecker(false)

        val jobParameters = mock(JobParameters::class.java)
        runWithShellPermissionIdentity {
            runBlocking {
                notificationListenerCheck.getEnabledNotificationListenersAndNotifyIfNeeded(
                    jobParameters,
                    notificationListenerCheckJobService
                )
            }
        }

        verify(notificationListenerCheckJobService).jobFinished(jobParameters, false)
    }

    @Test
    fun getEnabledNotificationListenersAndNotifyIfNeeded_shouldCancel_finishJob_reschedule() {
        shouldCancel = true
        val jobParameters = mock(JobParameters::class.java)

        runWithShellPermissionIdentity {
            runBlocking {
                notificationListenerCheck.getEnabledNotificationListenersAndNotifyIfNeeded(
                    jobParameters,
                    notificationListenerCheckJobService
                )
            }
        }

        verify(notificationListenerCheckJobService).jobFinished(jobParameters, true)
    }

    @Test
    fun getEnabledNotificationListenersAndNotifyIfNeeded_finishJob() {
        val jobParameters = mock(JobParameters::class.java)

        runWithShellPermissionIdentity {
            runBlocking {
                notificationListenerCheck.getEnabledNotificationListenersAndNotifyIfNeeded(
                    jobParameters,
                    notificationListenerCheckJobService
                )
            }
        }

        verify(notificationListenerCheckJobService).jobFinished(jobParameters, false)
    }

    @Test
    fun markAsNotified() {
        var initialNlsComponents: Set<NlsComponent> = getNotifiedComponents()
        assertThat(initialNlsComponents).isEmpty()

        val testComponent = ComponentName("com.test.package", "TestClass")
        val startTime = System.currentTimeMillis()

        // Mark as notified, and get the resulting list of NlsComponents
        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val nlsComponent: NlsComponent = runBlocking {
            notificationListenerCheck.markAsNotified(testComponent)
            getNotifiedComponents()
        }.filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }[0]

        // Verify notified time is not zero, and at least the test start time
        assertThat(nlsComponent.notificationShownTime).isNotEqualTo(0L)
        assertThat(nlsComponent.notificationShownTime).isAtLeast(startTime)
    }

    @Test
    fun markAsNotified_notifySecondComponent() {
        var nlsComponents: Set<NlsComponent> = getNotifiedComponents()
        assertThat(nlsComponents).isEmpty()

        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package2", "TestClass2")

        // Mark as notified, and get the resulting list of NlsComponents
        nlsComponents = runBlocking {
            notificationListenerCheck.markAsNotified(testComponent)
            getNotifiedComponents()
        }
        // Expected # components is 1
        assertThat(nlsComponents.size).isEqualTo(1)

        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val firstComponent = nlsComponents
            .filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }[0]

        // Mark second component as notified, and get the resulting list of NlsComponents
        nlsComponents = runBlocking {
            notificationListenerCheck.markAsNotified(testComponent2)
            getNotifiedComponents()
        }
        // Expected # components is 2
        assertThat(nlsComponents.size).isEqualTo(2)

        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val secondComponent = nlsComponents
            .filter { it.componentName == testComponent2 }
            .also { assertThat(it.size).isEqualTo(1) }[0]

        // Ensure second component marked notified after first component
        assertThat(secondComponent.notificationShownTime)
            .isGreaterThan(firstComponent.notificationShownTime)
    }

    @Test
    fun markAsNotified_notifySecondComponent_ensureFirstComponentNotModified() {
        var nlsComponents: Set<NlsComponent> = getNotifiedComponents()
        assertThat(nlsComponents).isEmpty()

        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package2", "TestClass2")

        // Mark as notified, and get the resulting list of NlsComponents
        nlsComponents = runBlocking {
            notificationListenerCheck.markAsNotified(testComponent)
            getNotifiedComponents()
        }
        // Expected # components is 1
        assertThat(nlsComponents.size).isEqualTo(1)

        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val firstComponent = nlsComponents
            .filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }[0]

        // Mark second component as notified, and get the resulting list of NlsComponents
        nlsComponents = runBlocking {
            notificationListenerCheck.markAsNotified(testComponent2)
            getNotifiedComponents()
        }
        // Expected # components is 2
        assertThat(nlsComponents.size).isEqualTo(2)

        // Verify first notified component still present
        assertThat(nlsComponents.contains(firstComponent)).isTrue()
    }

    @Test
    fun markAsNotifiedTwice_updatedNotificationTime() {
        val testComponent = ComponentName("com.test.package", "TestClass")

        // Mark as notified, and get the resulting list of NlsComponents
        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val initialNlsComponent: NlsComponent? = runBlocking {
            notificationListenerCheck.markAsNotified(testComponent)
            getNotifiedComponents()
        }.filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }
            .getOrNull(0)

        assertThat(initialNlsComponent).isNotNull()

        // Mark as notified *again*, and get the resulting list of NlsComponents
        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val updatedNlsComponent: NlsComponent? = runBlocking {
            notificationListenerCheck.markAsNotified(testComponent)
            getNotifiedComponents()
        }.filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }
            .getOrNull(0)

        assertThat(updatedNlsComponent).isNotNull()

        // Verify updated NlsComponent has an updated notificationShownTime
        assertThat(updatedNlsComponent!!.notificationShownTime)
            .isGreaterThan(initialNlsComponent!!.notificationShownTime)
    }

    @Test
    fun removePackageState() {
        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package2", "TestClass2")
        val testComponents = listOf(testComponent, testComponent2)

        // Mark all components as notified, and get the resulting list of NlsComponents
        val initialNlsComponents = runBlocking {
            testComponents.forEach {
                notificationListenerCheck.markAsNotified(it)
            }
            getNotifiedComponents().map { it.componentName }
        }

        // Verify expected components are present
        assertThat(initialNlsComponents).isNotNull()
        assertThat(initialNlsComponents.size).isEqualTo(testComponents.size)
        testComponents.forEach {
            assertThat(initialNlsComponents.contains(it)).isTrue()
        }

        // Forget about test package, and get the resulting list of NlsComponents
        // Filter to the component that match the test component
        val updatedNlsComponents = runWithShellPermissionIdentity {
            runBlocking {
                notificationListenerCheck.removePackageState(testComponent.packageName)
                getNotifiedComponents().map { it.componentName }
            }
        }

        // Verify expected components are present
        assertThat(updatedNlsComponents).isNotNull()
        assertThat(updatedNlsComponents.size).isEqualTo(testComponents.size - 1)
        assertThat(updatedNlsComponents.contains(testComponent)).isFalse()
        assertThat(updatedNlsComponents.contains(testComponent2)).isTrue()
    }

    @Test
    fun removePackageState_multipleNlsPerPackage() {
        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package", "TestClass2")
        val testComponents = listOf(testComponent, testComponent2)

        // Mark all components as notified, and get the resulting list of NlsComponents
        val initialNlsComponents = runBlocking {
            testComponents.forEach {
                notificationListenerCheck.markAsNotified(it)
            }
            getNotifiedComponents().map { it.componentName }
        }

        // Verify expected components are present
        assertThat(initialNlsComponents).isNotNull()
        assertThat(initialNlsComponents.size).isEqualTo(testComponents.size)
        testComponents.forEach {
            assertThat(initialNlsComponents.contains(it)).isTrue()
        }

        // Forget about test package, and get the resulting list of NlsComponents
        // Filter to the component that match the test component
        val updatedNlsComponents = runWithShellPermissionIdentity {
            runBlocking {
                notificationListenerCheck.removePackageState(testComponent.packageName)
                getNotifiedComponents().map { it.componentName }
            }
        }

        // Ensure empty
        assertThat(updatedNlsComponents).isEmpty()
    }

    @Test
    fun removePackageState_noPreviouslyNotifiedPackage() {
        val testComponent = ComponentName("com.test.package", "TestClass")

        // Get the initial list of Nls Components
        val initialNlsComponents = getNotifiedComponents().map { it.componentName }

        // Verify no components are present
        assertThat(initialNlsComponents).isEmpty()

        // Forget about test package, and get the resulting list of NlsComponents
        // Filter to the component that match the test component
        val updatedNlsComponents = runWithShellPermissionIdentity {
            runBlocking {
                // Verify this should not fail!
                notificationListenerCheck.removePackageState(testComponent.packageName)
                getNotifiedComponents().map { it.componentName }
            }
        }

        // Verify no components are present
        assertThat(updatedNlsComponents).isEmpty()
    }

    private fun enableNotificationListenerChecker(enabled: Boolean) {
        whenever(
            DeviceConfig.getBoolean(
                eq(DeviceConfig.NAMESPACE_PRIVACY),
                eq(PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED),
                anyBoolean()
            )
        ).thenReturn(enabled)
    }

    private fun getNotifiedComponents(): Set<NlsComponent> = runBlocking {
        notificationListenerCheck.loadNotifiedComponentsLocked()
    }

    private fun <R> runWithShellPermissionIdentity(block: () -> R): R {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        uiAutomation.adoptShellPermissionIdentity()
        try {
            return block()
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }
}