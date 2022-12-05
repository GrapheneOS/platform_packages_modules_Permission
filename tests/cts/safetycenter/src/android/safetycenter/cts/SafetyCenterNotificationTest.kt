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
import android.safetycenter.cts.testing.Coroutines
import android.safetycenter.cts.testing.CtsNotificationListener
import android.safetycenter.cts.testing.NotificationCharacteristics
import android.safetycenter.cts.testing.NotificationCharacteristics.Companion.assertNotificationMatches
import android.safetycenter.cts.testing.NotificationCharacteristics.Companion.assertNotificationsMatch
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
import com.android.compatibility.common.util.SystemUtil
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
        CtsNotificationListener.toggleListenerAccess(true)
        SafetyCenterFlags.notificationsEnabled = true
        SafetyCenterFlags.notificationsAllowedSources = setOf(SINGLE_SOURCE_ID)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
    }

    @After
    fun tearDown() {
        if (!shouldRunTests) {
            return
        }
        CtsNotificationListener.toggleListenerAccess(false)
        safetyCenterCtsHelper.reset()
    }

    @Test
    fun setSafetySourceData_withNoIssue_noNotification() {
        CtsNotificationListener.assertNoNotificationsPosted {
            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        }
    }

    @Test
    fun setSafetySourceData_withoutNotificationsAllowedSource_noNotification() {
        SafetyCenterFlags.notificationsAllowedSources = emptySet()

        CtsNotificationListener.assertNoNotificationsPosted {
            safetyCenterCtsHelper.setData(
                SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithAccountIssue)
        }
    }

    @Test
    fun setSafetySourceData_withFlagDisabled_noNotification() {
        SafetyCenterFlags.notificationsEnabled = false

        CtsNotificationListener.assertNoNotificationsPosted {
            safetyCenterCtsHelper.setData(
                SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithAccountIssue)
        }
    }

    @Test
    fun setSafetySourceData_withNotificationAllowedSource_sendsNotification() {
        val data = safetySourceCtsData.recommendationWithAccountIssue

        val notification =
            CtsNotificationListener.getNextNotificationPostedOrNull {
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)
            }

        assertThat(notification).isNotNull()
        assertNotificationMatches(
            notification!!,
            NotificationCharacteristics(
                title = "Recommendation issue title", text = "Recommendation issue summary"))
    }

    @Test
    fun setSafetySourceData_twiceWithSameIssueId_updatesNotification() {
        val data1 =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Initial", "Blah")
                        .build())
                .build()
        val data2 =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .build())
                .build()

        val notifications =
            CtsNotificationListener.getAllNotificationsPosted {
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data1)
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data2)
            }

        assertNotificationsMatch(
            notifications,
            NotificationCharacteristics(title = "Initial", text = "Blah"),
            NotificationCharacteristics(title = "Revised", text = "Different"))
        assertThat(notifications[0].key).isEqualTo(notifications[1].key)
    }

    @Test
    fun setSafetySourceData_twiceWithExactSameIssue_doNotNotifyTwice() {
        val data = safetySourceCtsData.recommendationWithAccountIssue

        val notifications =
            CtsNotificationListener.getAllNotificationsPosted {
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)
            }

        assertNotificationsMatch(
            notifications,
            NotificationCharacteristics(
                title = "Recommendation issue title", text = "Recommendation issue summary"))
    }

    @Test
    fun setSafetySourceData_twiceRemovingAnIssue_cancelsNotification() {
        val data1 = safetySourceCtsData.recommendationWithAccountIssue
        val data2 = safetySourceCtsData.information

        val posted =
            CtsNotificationListener.getNextNotificationPostedOrNull {
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data1)
            }
        val removed =
            CtsNotificationListener.getNextNotificationRemovedOrNull {
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data2)
            }

        assertThat(posted).isNotNull()
        assertThat(removed).isNotNull()
        assertThat(removed!!.key).isEqualTo(posted!!.key)
    }

    @Test
    fun setSafetySourceData_withDismissedIssueId_doesNotNotify() {
        val data1 =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Initial", "Blah")
                        .build())
                .build()
        val data2 =
            safetySourceCtsData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .build())
                .build()

        val posted =
            CtsNotificationListener.getNextNotificationPostedOrNull {
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data1)
            }

        assertThat(posted).isNotNull()
        assertNotificationMatches(
            posted!!, NotificationCharacteristics(title = "Initial", text = "Blah"))

        CtsNotificationListener.cancelAndWait(posted.key)

        // Here we wait for the issue (there is only one) to be recorded as dismissed according to
        // the dumpsys output. The cancelAndWait helper above "waits" for the notification to be
        // dismissed, but it does not wait for the notification's delete PendingIntent to be
        // handled. Without this additional wait there is a race condition between
        // SafetyCenterNotificationReceiver#onReceive and the setData below. That race makes the
        // test is flaky because the notification may not be recorded as dismissed before setData
        // is called again and the notification is able to be posted again, contradicting the
        // assertion.
        Coroutines.waitForWithTimeout {
            val dump = SystemUtil.runShellCommand("dumpsys safety_center")
            dump.contains(Regex("""mNotificationDismissedAt=\d+"""))
        }

        CtsNotificationListener.assertNoNotificationsPosted {
            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data2)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_dismissesNotification() {
        val posted =
            CtsNotificationListener.getNextNotificationPostedOrNull {
                safetyCenterCtsHelper.setData(
                    SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithAccountIssue)
            }
        val removed =
            CtsNotificationListener.getNextNotificationRemovedOrNull {
                safetyCenterManager.dismissSafetyCenterIssueWithPermission(
                    SafetyCenterCtsData.issueId(
                        SINGLE_SOURCE_ID, SafetySourceCtsData.RECOMMENDATION_ISSUE_ID))
            }

        assertThat(posted).isNotNull()
        assertThat(removed).isNotNull()
        assertThat(removed!!.key).isEqualTo(posted!!.key)
    }

    @Test
    fun dismissingNotification_doesntUpdateSafetyCenterData() {
        val posted =
            CtsNotificationListener.getNextNotificationPostedOrNull {
                safetyCenterCtsHelper.setData(
                    SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
            }
        assertThat(posted).isNotNull()
        val listener = safetyCenterCtsHelper.addListener()

        CtsNotificationListener.cancelAndWait(posted!!.key)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(Coroutines.TIMEOUT_SHORT)
        }
    }

    @Test
    fun clearSafetySourceData_cancelsAllNotifications() {
        val data = safetySourceCtsData.recommendationWithAccountIssue

        CtsNotificationListener.assertAnyNotificationPosted {
            safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)
        }
        CtsNotificationListener.assertAnyNotificationRemoved {
            safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()
        }
        val postedAfter =
            CtsNotificationListener.getNextNotificationPostedOrNull {
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, data)
            }

        assertThat(postedAfter).isNotNull()
        assertNotificationMatches(
            postedAfter!!,
            NotificationCharacteristics(
                title = "Recommendation issue title", text = "Recommendation issue summary"))
    }
}
