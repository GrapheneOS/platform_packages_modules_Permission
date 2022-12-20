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

package android.safetycenter.cts

import android.content.Context
import android.safetycenter.SafetyCenterManager
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.CtsNotificationListener
import android.safetycenter.cts.testing.NotificationCharacteristics
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsData
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Notification-related tests for [SafetyCenterManager]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterNotificationTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)
    private val safetyCenterManager =
        requireNotNull(context.getSystemService(SafetyCenterManager::class.java)) {
            "Could not get system service"
        }

    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = context.deviceSupportsSafetyCenter()

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(shouldRunTests)
    }

    @Before
    fun setUp() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.setup()
        CtsNotificationListener.setup()
        SafetyCenterFlags.notificationsEnabled = true
        SafetyCenterFlags.notificationsAllowedSources = setOf(SINGLE_SOURCE_ID)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
    }

    @After
    fun tearDown() {
        if (!shouldRunTests) {
            return
        }
        // It is important to reset the notification listener last because it waits/ensures that
        // all notifications have been removed before returning.
        safetyCenterCtsHelper.reset()
        CtsNotificationListener.reset()
    }

    @Test
    fun setSafetySourceData_withNoIssue_noNotification() {
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        CtsNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withoutNotificationsAllowedSource_noNotification() {
        SafetyCenterFlags.notificationsAllowedSources = emptySet()

        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.recommendationWithAccountIssue
        )

        CtsNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withFlagDisabled_noNotification() {
        SafetyCenterFlags.notificationsEnabled = false

        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.recommendationWithAccountIssue
        )

        CtsNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withNotificationAllowedSource_sendsNotification() {
        val data = safetySourceCtsData.recommendationWithAccountIssue

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Recommendation issue title",
                text = "Recommendation issue summary"
            )
        )
    }

    @Test
    fun setSafetySourceData_twiceWithSameIssueId_updatesNotification() {
        val data1 =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData.defaultRecommendationIssueBuilder("Initial", "Blah").build()
                )
                .build()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data1)

        CtsNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(title = "Initial", text = "Blah")
        )

        val data2 =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .build()
                )
                .build()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data2)

        CtsNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(title = "Revised", text = "Different")
        )
    }

    @Test
    fun setSafetySourceData_twiceWithExactSameIssue_doNotNotifyTwice() {
        val data = safetySourceCtsData.recommendationWithAccountIssue

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForSingleNotification()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForZeroNotificationEvents()
    }

    @Test
    fun setSafetySourceData_twiceRemovingAnIssue_cancelsNotification() {
        val data1 = safetySourceCtsData.recommendationWithAccountIssue
        val data2 = safetySourceCtsData.information

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data1)

        CtsNotificationListener.waitForSingleNotification()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data2)

        CtsNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withDismissedIssueId_doesNotNotify() {
        val data1 =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData.defaultRecommendationIssueBuilder("Initial", "Blah").build()
                )
                .build()
        val data2 =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .build()
                )
                .build()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data1)

        val notification =
            CtsNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(title = "Initial", text = "Blah")
            )

        CtsNotificationListener.cancelAndWait(notification.key)

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data2)

        CtsNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun dismissSafetyCenterIssue_dismissesNotification() {
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.recommendationWithAccountIssue
        )

        CtsNotificationListener.waitForSingleNotification()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(
                SINGLE_SOURCE_ID,
                SafetySourceCtsData.RECOMMENDATION_ISSUE_ID
            )
        )

        CtsNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun dismissingNotification_doesNotUpdateSafetyCenterData() {
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )

        val notification = CtsNotificationListener.waitForSingleNotification()

        val listener = safetyCenterCtsHelper.addListener()

        CtsNotificationListener.cancelAndWait(notification.key)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun clearSafetySourceData_cancelsAllNotifications() {
        val data = safetySourceCtsData.recommendationWithAccountIssue

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForSingleNotification()

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        CtsNotificationListener.waitForZeroNotifications()
    }
}
