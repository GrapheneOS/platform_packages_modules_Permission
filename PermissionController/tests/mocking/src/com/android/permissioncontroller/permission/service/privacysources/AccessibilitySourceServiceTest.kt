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

package com.android.permissioncontroller.permission.service.privacysources

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
import com.android.permissioncontroller.privacysources.AccessibilitySourceService
import com.android.permissioncontroller.privacysources.AccessibilitySourceService.AccessibilityComponent
import com.android.permissioncontroller.privacysources.AccessibilitySourceService.AccessibilityJobService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness
import org.mockito.Mockito.`when` as whenever

/**
 * Unit tests for internal [AccessibilitySourceService]
 *
 * <p> Does not test notification as there are conflicts with being able to mock NotificationManager
 * and PendingIntent.getBroadcast requiring a valid context. Notifications are tested in the CTS
 * integration tests
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class AccessibilitySourceServiceTest {

    @Mock
    lateinit var jobService: AccessibilityJobService
    private lateinit var context: Context
    private lateinit var mockitoSession: MockitoSession
    private lateinit var accessibilitySourceService: AccessibilitySourceService
    private var shouldCancel = false

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()

        mockitoSession = ExtendedMockito.mockitoSession()
            .mockStatic(DeviceConfig::class.java)
            .strictness(Strictness.LENIENT).startMocking()

        accessibilitySourceService = runWithShellPermissionIdentity {
            AccessibilitySourceService(context, { shouldCancel })
        }

        setAccessibilityFeatureFlag(true)
    }

    @After
    fun cleanup() {
        // cleanup ACCESSIBILITY_SERVICES_ALREADY_NOTIFIED_FILE
        context.deleteFile(Constants.ACCESSIBILITY_SERVICES_ALREADY_NOTIFIED_FILE)
        shouldCancel = false
        mockitoSession.finishMocking()
    }

    @Test
    fun processAccessibilityJobWithDisabledFlag() {
        setAccessibilityFeatureFlag(false)

        val jobParameters = mock(JobParameters::class.java)
        runWithShellPermissionIdentity {
            runBlocking {
                accessibilitySourceService.processAccessibilityJob(
                    jobParameters,
                    jobService
                )
            }
        }

        verify(jobService).jobFinished(jobParameters, false)
    }

    // @Test
    fun processAccessibilityJobWithCancellation() {
        shouldCancel = true
        val jobParameters = mock(JobParameters::class.java)

        runWithShellPermissionIdentity {
            runBlocking {
                accessibilitySourceService.processAccessibilityJob(
                    jobParameters,
                    jobService
                )
            }
        }
        verify(jobService).jobFinished(jobParameters, true)
    }

    @Test
    fun processAccessibilityJobWithEnabledFlag() {
        val jobParameters = mock(JobParameters::class.java)

        runWithShellPermissionIdentity {
            runBlocking {
                accessibilitySourceService.processAccessibilityJob(
                    jobParameters,
                    jobService
                )
            }
        }

        verify(jobService).jobFinished(jobParameters, false)
    }

    @Test
    fun markAsNotified() {
        var initialComponents: Set<AccessibilityComponent> = getNotifiedComponents()
        assertThat(initialComponents).isEmpty()

        val testComponent = ComponentName("com.test.package", "TestClass")
        val startTime = System.currentTimeMillis()

        // Mark as notified, and get the resulting list of Components
        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val component: AccessibilityComponent = runBlocking {
            accessibilitySourceService.markAsNotified(testComponent)
            getNotifiedComponents()
        }.filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }[0]

        // Verify notified time is not zero, and at least the test start time
        assertThat(component.notificationShownTime).isNotEqualTo(0L)
        assertThat(component.notificationShownTime).isAtLeast(startTime)
    }

    @Test
    fun markAsNotifiedWithSecondComponent() {
        var components: Set<AccessibilityComponent> = getNotifiedComponents()
        assertThat(components).isEmpty()

        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package2", "TestClass2")

        // Mark as notified, and get the resulting list of Components
        components = runBlocking {
            accessibilitySourceService.markAsNotified(testComponent)
            getNotifiedComponents()
        }
        // Expected # components is 1
        assertThat(components.size).isEqualTo(1)

        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val firstComponent = components
            .filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }[0]

        // Mark second component as notified, and get the resulting list of Components
        components = runBlocking {
            accessibilitySourceService.markAsNotified(testComponent2)
            getNotifiedComponents()
        }
        // Expected # components is 2
        assertThat(components.size).isEqualTo(2)

        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val secondComponent = components
            .filter { it.componentName == testComponent2 }
            .also { assertThat(it.size).isEqualTo(1) }[0]

        // Ensure second component marked notified after first component
        assertThat(secondComponent.notificationShownTime)
            .isGreaterThan(firstComponent.notificationShownTime)
    }

    @Test
    fun markAsNotifiedWithMultipleComponents() {
        var components: Set<AccessibilityComponent> = getNotifiedComponents()
        assertThat(components).isEmpty()

        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package2", "TestClass2")

        // Mark as notified, and get the resulting list of Components
        components = runBlocking {
            accessibilitySourceService.markAsNotified(testComponent)
            getNotifiedComponents()
        }
        // Expected # components is 1
        assertThat(components.size).isEqualTo(1)

        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val firstComponent = components
            .filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }[0]

        // Mark second component as notified, and get the resulting list of Components
        components = runBlocking {
            accessibilitySourceService.markAsNotified(testComponent2)
            getNotifiedComponents()
        }
        // Expected # components is 2
        assertThat(components.size).isEqualTo(2)

        // Verify first notified component still present
        assertThat(components.contains(firstComponent)).isTrue()
    }

    @Test
    fun markAsNotifiedForNotificationTimestamp() {
        val testComponent = ComponentName("com.test.package", "TestClass")

        // Mark as notified, and get the resulting list of Components
        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val initialComponent: AccessibilityComponent? = runBlocking {
            accessibilitySourceService.markAsNotified(testComponent)
            getNotifiedComponents()
        }.filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }
            .getOrNull(0)

        assertThat(initialComponent).isNotNull()

        // Mark as notified *again*, and get the resulting list of Components
        // Filter to the component that match the test component
        // Ensure size is equal to one (not empty)
        // Get the component
        val updatedComponent: AccessibilityComponent? = runBlocking {
            accessibilitySourceService.markAsNotified(testComponent)
            getNotifiedComponents()
        }.filter { it.componentName == testComponent }
            .also { assertThat(it.size).isEqualTo(1) }
            .getOrNull(0)

        assertThat(updatedComponent).isNotNull()

        // Verify updated Component has an updated notificationShownTime
        assertThat(updatedComponent!!.notificationShownTime)
            .isGreaterThan(initialComponent!!.notificationShownTime)
    }

    @Test
    fun removePackageState() {
        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package2", "TestClass2")
        val testComponents = listOf(testComponent, testComponent2)

        // Mark all components as notified, and get the resulting list of Components
        val initialComponents = runBlocking {
            testComponents.forEach {
                accessibilitySourceService.markAsNotified(it)
            }
            getNotifiedComponents().map { it.componentName }
        }

        // Verify expected components are present
        assertThat(initialComponents).isNotNull()
        assertThat(initialComponents.size).isEqualTo(testComponents.size)
        testComponents.forEach {
            assertThat(initialComponents.contains(it)).isTrue()
        }

        // Forget about test package, and get the resulting list of Components
        // Filter to the component that match the test component
        val updatedComponents = runWithShellPermissionIdentity {
            runBlocking {
                accessibilitySourceService.removePackageState(testComponent.packageName)
                getNotifiedComponents().map { it.componentName }
            }
        }

        // Verify expected components are present
        assertThat(updatedComponents).isNotNull()
        assertThat(updatedComponents.size).isEqualTo(testComponents.size - 1)
        assertThat(updatedComponents.contains(testComponent)).isFalse()
        assertThat(updatedComponents.contains(testComponent2)).isTrue()
    }

    @Test
    fun removePackageStateWithMultipleServicePerPackage() {
        val testComponent = ComponentName("com.test.package", "TestClass")
        val testComponent2 = ComponentName("com.test.package", "TestClass2")
        val testComponents = listOf(testComponent, testComponent2)

        // Mark all components as notified, and get the resulting list of Components
        val initialComponents = runBlocking {
            testComponents.forEach {
                accessibilitySourceService.markAsNotified(it)
            }
            getNotifiedComponents().map { it.componentName }
        }

        // Verify expected components are present
        assertThat(initialComponents).isNotNull()
        assertThat(initialComponents.size).isEqualTo(testComponents.size)
        testComponents.forEach {
            assertThat(initialComponents.contains(it)).isTrue()
        }

        // Forget about test package, and get the resulting list of Components
        // Filter to the component that match the test component
        val updatedComponents = runWithShellPermissionIdentity {
            runBlocking {
                accessibilitySourceService.removePackageState(testComponent.packageName)
                getNotifiedComponents().map { it.componentName }
            }
        }

        // Ensure empty
        assertThat(updatedComponents).isEmpty()
    }

    @Test
    fun removePackageState_noPreviouslyNotifiedPackage() {
        val testComponent = ComponentName("com.test.package", "TestClass")

        // Get the initial list of Components
        val initialComponents = getNotifiedComponents().map { it.componentName }

        // Verify no components are present
        assertThat(initialComponents).isEmpty()

        // Forget about test package, and get the resulting list of Components
        // Filter to the component that match the test component
        val updatedComponents = runWithShellPermissionIdentity {
            runBlocking {
                // Verify this should not fail!
                accessibilitySourceService.removePackageState(testComponent.packageName)
                getNotifiedComponents().map { it.componentName }
            }
        }

        // Verify no components are present
        assertThat(updatedComponents).isEmpty()
    }

    private fun setAccessibilityFeatureFlag(enabled: Boolean) {
        whenever(
            DeviceConfig.getBoolean(
                eq(DeviceConfig.NAMESPACE_PRIVACY),
                eq(AccessibilitySourceService.PROPERTY_SC_ACCESSIBILITY_SOURCE_ENABLED),
                anyBoolean()
            )
        ).thenReturn(enabled)
    }

    private fun getNotifiedComponents(): Set<AccessibilityComponent> = runBlocking {
        accessibilitySourceService.loadNotifiedComponentsLocked()
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
