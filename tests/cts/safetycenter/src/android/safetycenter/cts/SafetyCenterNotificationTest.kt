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

import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.app.Notification
import android.content.Context
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetySourceIssue
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.CtsNotificationListener
import android.safetycenter.cts.testing.NotificationCharacteristics
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.dynamicSafetySourceBuilder
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.singleSourceConfig
import android.safetycenter.cts.testing.SafetyCenterCtsData
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Request
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Response
import android.safetycenter.cts.testing.SafetySourceReceiver
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
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
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withNotificationBehaviorNever_noNotification() {
        val data =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder()
                        .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_NEVER)
                        .build()
                )
                .build()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForZeroNotifications()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withNotificationBehaviorImmediately_sendsNotification() {
        val data =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Notify immediately", "This is urgent!")
                        .setNotificationBehavior(
                            SafetySourceIssue.NOTIFICATION_BEHAVIOR_IMMEDIATELY
                        )
                        .build()
                )
                .build()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Notify immediately",
                text = "This is urgent!",
                actions = listOf("See issue")
            )
        )
    }

    @Test
    fun setSafetySourceData_withNotificationsAllowedForSourceByFlag_sendsNotification() {
        SafetyCenterFlags.notificationsAllowedSources = setOf(SINGLE_SOURCE_ID)
        val data = safetySourceCtsData.recommendationWithAccountIssue

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Recommendation issue title",
                text = "Recommendation issue summary",
                actions = listOf("See issue")
            )
        )
    }

    @Test
    fun setSafetySourceData_issueWithTwoActions_notificationWithTwoActions() {
        val intent1 = safetySourceCtsData.testActivityRedirectPendingIntent(identifier = "1")
        val intent2 = safetySourceCtsData.testActivityRedirectPendingIntent(identifier = "2")

        val data =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder()
                        .clearActions()
                        .addAction(
                            SafetySourceIssue.Action.Builder("action1", "Action 1", intent1).build()
                        )
                        .addAction(
                            SafetySourceIssue.Action.Builder("action2", "Action 2", intent2).build()
                        )
                        .build()
                )
                .build()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Recommendation issue title",
                text = "Recommendation issue summary",
                actions = listOf("Action 1", "Action 2")
            )
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withNotificationsAllowedForSourceByConfig_sendsNotification() {
        safetyCenterCtsHelper.setConfig(
            singleSourceConfig(
                dynamicSafetySourceBuilder("MyNotifiableSource")
                    .setNotificationsAllowed(true)
                    .build()
            )
        )
        val data = safetySourceCtsData.recommendationWithAccountIssue

        safetyCenterCtsHelper.setData("MyNotifiableSource", data)

        CtsNotificationListener.waitForSingleNotification()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withCustomNotification_usesCustomValues() {
        val intent1 = safetySourceCtsData.testActivityRedirectPendingIntent(identifier = "1")
        val intent2 = safetySourceCtsData.testActivityRedirectPendingIntent(identifier = "2")

        val notification =
            SafetySourceIssue.Notification.Builder("Custom title", "Custom text")
                .addAction(
                    SafetySourceIssue.Action.Builder("action1", "Custom action 1", intent1).build()
                )
                .addAction(
                    SafetySourceIssue.Action.Builder("action2", "Custom action 2", intent1).build()
                )
                .build()

        val data =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Default title", "Default text")
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "default_action",
                                    "Default action",
                                    safetySourceCtsData.testActivityRedirectPendingIntent
                                )
                                .build()
                        )
                        .setCustomNotification(notification)
                        .build()
                )
                .build()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Custom title",
                text = "Custom text",
                actions = listOf("Custom action 1", "Custom action 2")
            )
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withEmptyCustomActions_notificationHasNoActions() {
        val notification =
            SafetySourceIssue.Notification.Builder("Custom title", "Custom text")
                .clearActions()
                .build()

        val data =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Default title", "Default text")
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "default_action",
                                    "Default action",
                                    safetySourceCtsData.testActivityRedirectPendingIntent
                                )
                                .build()
                        )
                        .setCustomNotification(notification)
                        .build()
                )
                .build()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)

        CtsNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Custom title",
                text = "Custom text",
                actions = emptyList()
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

        val initialNotification =
            CtsNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(
                    title = "Initial",
                    text = "Blah",
                    actions = listOf("See issue")
                )
            )

        val data2 =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "new_action",
                                    "New action",
                                    safetySourceCtsData.testActivityRedirectPendingIntent(
                                        identifier = "new_action"
                                    )
                                )
                                .build()
                        )
                        .build()
                )
                .build()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data2)

        val revisedNotification =
            CtsNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(
                    title = "Revised",
                    text = "Different",
                    actions = listOf("See issue", "New action")
                )
            )
        assertThat(initialNotification.key).isEqualTo(revisedNotification.key)
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
        // We use two different issues/data here to ensure that the reason the notification is not
        // posted the second time is specifically because of the dismissal. Notifications are not
        // re-posted/updated for unchanged issues but that functionality is different and tested
        // separately.
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
                NotificationCharacteristics(
                    title = "Initial",
                    text = "Blah",
                    actions = listOf("See issue")
                )
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
        // Add the listener after setting the initial data so that we don't need to consume/receive
        // an update for that
        val listener = safetyCenterCtsHelper.addListener()

        val notification = CtsNotificationListener.waitForSingleNotification()

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

    @Test
    fun sendActionPendingIntent_successful_updatesListenerRemovesNotification() {
        // Here we cause a notification with an action to be posted and prepare the fake receiver
        // to resolve that action successfully.
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        val notification = CtsNotificationListener.waitForSingleNotification()
        val action = notification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information)
        )
        val listener = safetyCenterCtsHelper.addListener()

        sendActionPendingIntentAndWaitWithPermission(action)

        val listenerData1 = listener.receiveSafetyCenterData()
        assertThat(listenerData1.inFlightActions).hasSize(1)
        val listenerData2 = listener.receiveSafetyCenterData()
        assertThat(listenerData2.issues).isEmpty()
        assertThat(listenerData2.status.severityLevel)
            .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
        CtsNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun sendActionPendingIntent_error_updatesListenerDoesNotRemoveNotification() {
        // Here we cause a notification with an action to be posted and prepare the fake receiver
        // to resolve that action successfully.
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue
        )
        val notification = CtsNotificationListener.waitForSingleNotification()
        val action = notification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), Response.Error)
        val listener = safetyCenterCtsHelper.addListener()

        sendActionPendingIntentAndWaitWithPermission(action)

        val listenerData1 = listener.receiveSafetyCenterData()
        assertThat(listenerData1.inFlightActions).hasSize(1)
        val listenerData2 = listener.receiveSafetyCenterData()
        assertThat(listenerData2.issues).hasSize(1)
        assertThat(listenerData2.inFlightActions).isEmpty()
        assertThat(listenerData2.status.severityLevel)
            .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        CtsNotificationListener.waitForSingleNotification()
    }

    companion object {
        private val SafetyCenterData.inFlightActions: List<SafetyCenterIssue.Action>
            get() = issues.flatMap { it.actions }.filter { it.isInFlight }

        private fun sendActionPendingIntentAndWaitWithPermission(action: Notification.Action) {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                action.actionIntent.send()
                // Sending the action's PendingIntent above is asynchronous and we need to wait for
                // it to be received by the fake receiver below.
                SafetySourceReceiver.receiveResolveAction()
            }
        }
    }
}
