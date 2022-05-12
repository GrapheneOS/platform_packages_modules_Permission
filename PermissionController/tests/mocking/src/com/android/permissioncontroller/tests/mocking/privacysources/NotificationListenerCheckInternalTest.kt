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

package com.android.permissioncontroller.tests.mocking.privacysources

import android.app.PendingIntent
import android.app.job.JobParameters
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.DisableNotificationListenerComponentHandler
import com.android.permissioncontroller.privacysources.NotificationListenerActionCardDismissalReceiver
import com.android.permissioncontroller.privacysources.NotificationListenerCheckJobService
import com.android.permissioncontroller.privacysources.NotificationListenerCheckInternal
import com.android.permissioncontroller.privacysources.NotificationListenerCheckInternal.NlsComponent
import com.android.permissioncontroller.privacysources.SC_NLS_DISABLE_ACTION_ID
import com.android.permissioncontroller.privacysources.SC_NLS_SOURCE_ID
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/**
 * Unit tests for [NotificationListenerCheckInternal]
 *
 * <p> Does not test notification as there are conflicts with being able to mock NotificationManager
 * and PendintIntent.getBroadcast requiring a valid context. Notifications are tested in the CTS
 * integration tests
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class NotificationListenerCheckInternalTest {

    @Mock
    lateinit var mockNotificationListenerCheckJobService: NotificationListenerCheckJobService
    @Mock
    lateinit var mockSafetyCenterManager: SafetyCenterManager

    private lateinit var context: Context
    private lateinit var mockitoSession: MockitoSession
    private lateinit var notificationListenerCheck: NotificationListenerCheckInternal

    private var shouldCancel = false

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = ApplicationProvider.getApplicationContext()

        mockitoSession = ExtendedMockito.mockitoSession()
            .spyStatic(Utils::class.java)
            .strictness(Strictness.LENIENT).startMocking()

        // Setup Safety Center
        doReturn(mockSafetyCenterManager).`when` {
            Utils.getSystemServiceSafe(
                any(ContextWrapper::class.java),
                eq(SafetyCenterManager::class.java)
            )
        }

        notificationListenerCheck = runWithShellPermissionIdentity {
            NotificationListenerCheckInternal(context) { shouldCancel }
        }
    }

    @After
    fun cleanup() {
        // cleanup NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE
        context.deleteFile(Constants.NOTIFICATION_LISTENER_CHECK_ALREADY_NOTIFIED_FILE)

        shouldCancel = false
        mockitoSession.finishMocking()
    }

    @Test
    fun getEnabledNotificationListenersAndNotifyIfNeeded_shouldCancel_finishJob_reschedule() {
        shouldCancel = true
        val jobParameters = mock(JobParameters::class.java)

        runWithShellPermissionIdentity {
            runBlocking {
                notificationListenerCheck.getEnabledNotificationListenersAndNotifyIfNeeded(
                    jobParameters,
                    mockNotificationListenerCheckJobService
                )
            }
        }

        verify(mockNotificationListenerCheckJobService).jobFinished(jobParameters, true)
    }

    @Test
    fun getEnabledNotificationListenersAndNotifyIfNeeded_finishJob() {
        val jobParameters = mock(JobParameters::class.java)

        runWithShellPermissionIdentity {
            runBlocking {
                notificationListenerCheck.getEnabledNotificationListenersAndNotifyIfNeeded(
                    jobParameters,
                    mockNotificationListenerCheckJobService
                )
            }
        }

        verify(mockNotificationListenerCheckJobService).jobFinished(jobParameters, false)
    }

    @Test
    fun getEnabledNotificationListenersAndNotifyIfNeeded_sendsDataToSafetyCenter() {
        val jobParameters = mock(JobParameters::class.java)

        runWithShellPermissionIdentity {
            runBlocking {
                notificationListenerCheck.getEnabledNotificationListenersAndNotifyIfNeeded(
                    jobParameters,
                    mockNotificationListenerCheckJobService
                )
            }
        }

        verify(mockSafetyCenterManager)
            .setSafetySourceData(
                eq(SC_NLS_SOURCE_ID),
                any(SafetySourceData::class.java),
                any(SafetyEvent::class.java))
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

    @Test
    fun createSafetySourceIssue() {
        val testComponent = ComponentName("com.test.package", "TestClass")
        val testAppLabel: CharSequence = "TestApp Label"
        doReturn(PackageInfo().apply {
            applicationInfo = ApplicationInfo()
        }).`when` {
            Utils.getPackageInfoForComponentName(
                any(Context::class.java),
                any(ComponentName::class.java)
            )
        }
        doReturn(testAppLabel).`when` {
            Utils.getApplicationLabel(
                any(Context::class.java),
                any(ApplicationInfo::class.java))
        }

        val safetySourceIssue = notificationListenerCheck.createSafetySourceIssue(testComponent)

        val expectedId = "notification_listener_${testComponent.flattenToString()}"
        val expectedTitle = context.getString(
                R.string.notification_listener_reminder_notification_title)
        val expectedSubtitle: String = testAppLabel.toString()
        val expectedSummary = context.getString(
            R.string.notification_listener_warning_card_content)
        val expectedSeverityLevel = SafetySourceData.SEVERITY_LEVEL_INFORMATION
        val expectedIssueTypeId = NotificationListenerCheckInternal.SC_NLS_ISSUE_TYPE_ID
        val expectedDismissIntent = Intent(context,
            NotificationListenerActionCardDismissalReceiver::class.java).apply {
            putExtra(Intent.EXTRA_COMPONENT_NAME, testComponent)
            flags = Intent.FLAG_RECEIVER_FOREGROUND
            identifier = testComponent.flattenToString()
        }
        val expectedDismissPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            expectedDismissIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val expectedAction1 = SafetySourceIssue.Action.Builder(
            SC_NLS_DISABLE_ACTION_ID,
            context.getString(R.string.notification_listener_remove_access_button_label),
            getDisableNlsPendingIntent(context, expectedId, testComponent)
        )
            .setWillResolve(true)
            .setSuccessMessage(context.getString(
                R.string.notification_listener_remove_access_success_label))
            .build()
        val expectedAction2 =
            SafetySourceIssue.Action.Builder(
                NotificationListenerCheckInternal.SC_SHOW_NLS_SETTINGS_ACTION_ID,
                context.getString(R.string.notification_listener_review_app_button_label),
                getNotificationListenerSettingsPendingIntent(context)
            ).build()

        assertThat(safetySourceIssue.id).isEqualTo(expectedId)
        assertThat(safetySourceIssue.title).isEqualTo(expectedTitle)
        assertThat(safetySourceIssue.subtitle).isEqualTo(expectedSubtitle)
        assertThat(safetySourceIssue.summary).isEqualTo(expectedSummary)
        assertThat(safetySourceIssue.severityLevel).isEqualTo(expectedSeverityLevel)
        assertThat(safetySourceIssue.issueTypeId).isEqualTo(expectedIssueTypeId)
        assertThat(safetySourceIssue.onDismissPendingIntent).isEqualTo(expectedDismissPendingIntent)
        assertThat(safetySourceIssue.actions.size).isEqualTo(2)
        assertThat(safetySourceIssue.actions).containsExactly(expectedAction2, expectedAction1)
    }

    private fun getNotifiedComponents(): Set<NlsComponent> = runBlocking {
        notificationListenerCheck.loadNotifiedComponentsLocked()
    }

    /**
     * @return [PendingIntent] for remove access button on the warning card.
     */
    private fun getDisableNlsPendingIntent(
        context: Context,
        safetySourceIssueId: String,
        componentName: ComponentName
    ): PendingIntent {
        val intent = Intent(context,
            DisableNotificationListenerComponentHandler::class.java).apply {
            putExtra(SafetyCenterManager.EXTRA_SAFETY_SOURCE_ISSUE_ID, safetySourceIssueId)
            putExtra(Intent.EXTRA_COMPONENT_NAME, componentName)
            flags = Intent.FLAG_RECEIVER_FOREGROUND
            identifier = componentName.flattenToString()
        }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** @return [PendingIntent] to Notification Listener Settings page */
    private fun getNotificationListenerSettingsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
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