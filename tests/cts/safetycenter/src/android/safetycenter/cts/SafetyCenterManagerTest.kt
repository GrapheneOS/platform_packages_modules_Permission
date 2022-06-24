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

package android.safetycenter.cts

import android.app.PendingIntent
import android.content.Context
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.UserHandle.USER_NULL
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_NONE
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.FakeExecutor
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.executeSafetyCenterIssueActionWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.refreshSafetySourcesWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.reportSafetySourceErrorWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.COMPLEX_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_ALL_OPTIONAL_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_DISABLED_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_GROUP_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_HIDDEN_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_HIDDEN_WITH_SEARCH_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_IN_COLLAPSIBLE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_IN_RIGID_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_OTHER_PACKAGE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_ALL_OPTIONAL_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_IN_RIGID_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MIXED_COLLAPSIBLE_GROUP_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.NO_PAGE_OPEN_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SEVERITY_ZERO_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_3
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_IN_COLLAPSIBLE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsData
import android.safetycenter.cts.testing.SafetyCenterCtsData.normalize
import android.safetycenter.cts.testing.SafetyCenterCtsData.stubPendingIntent
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterCtsListener
import android.safetycenter.cts.testing.SafetyCenterEnabledChangedReceiver
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.INFORMATION_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.INFORMATION_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.RECOMMENDATION_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.RECOMMENDATION_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey.Reason.REFRESH_FETCH_FRESH_DATA
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey.Reason.REFRESH_GET_DATA
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey.Reason.RESOLVING_ACTION
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.executeSafetyCenterIssueActionWithReceiverPermissionAndWait
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithReceiverPermissionAndWait
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithoutReceiverPermissionAndWait
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    private val safetyCenterStatusOk =
        SafetyCenterStatus.Builder("Looks good", "This device is protected")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusOkOneAlert =
        SafetyCenterStatus.Builder("Looks good", "1 alert")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusOkReviewOneAlert =
        SafetyCenterStatus.Builder("Add more protection", "1 alert")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusOkReview =
        SafetyCenterStatus.Builder("Add more protection", "Review your settings")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusRecommendationOneAlert =
        SafetyCenterStatus.Builder("Device may be at risk", "1 alert")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val safetyCenterStatusCriticalOneAlert =
        SafetyCenterStatus.Builder("Device is at risk", "1 alert")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusCriticalSixAlerts =
        SafetyCenterStatus.Builder("Device is at risk", "6 alerts")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterEntryOrGroupCritical =
        SafetyCenterEntryOrGroup(safetyCenterEntryCritical(SINGLE_SOURCE_ID))

    private val safetyCenterEntryGroupMixedFromComplexConfig =
        SafetyCenterEntryOrGroup(
            SafetyCenterEntryGroup.Builder(
                    SafetyCenterCtsData.entryGroupId(MIXED_COLLAPSIBLE_GROUP_ID), "OK")
                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                .setSummary("OK")
                .setEntries(
                    listOf(
                        safetyCenterEntryDefaultBuilder(DYNAMIC_IN_COLLAPSIBLE_ID).build(),
                        SafetyCenterEntry.Builder(
                                SafetyCenterCtsData.entryId(STATIC_IN_COLLAPSIBLE_ID), "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                            .setSummary("OK")
                            .setPendingIntent(stubPendingIntent)
                            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)
                            .build()))
                .build())

    private val safetyCenterStaticEntryGroupFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("OK").setPendingIntent(stubPendingIntent).build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(stubPendingIntent)
                    .build()))

    private val safetyCenterStaticEntryGroupMixedFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
                    .build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(stubPendingIntent)
                    .build()))

    private val safetyCenterStaticEntryGroupMixedUpdatedFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("Unspecified title")
                    .setSummary("Unspecified summary")
                    .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
                    .build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(stubPendingIntent)
                    .build()))

    private val safetyCenterDataFromConfig =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterEntryDefaultBuilder(SINGLE_SOURCE_ID).build())),
            emptyList())

    private val safetyCenterDataUnspecified =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(SafetyCenterEntryOrGroup(safetyCenterEntryUnspecified(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataOk =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(SafetyCenterEntryOrGroup(safetyCenterEntryOk(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataOkOneAlert =
        SafetyCenterData(
            safetyCenterStatusOkOneAlert,
            listOf(safetyCenterIssueInformation(SINGLE_SOURCE_ID)),
            listOf(SafetyCenterEntryOrGroup(safetyCenterEntryOk(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataOkReview =
        SafetyCenterData(
            safetyCenterStatusOkReview,
            emptyList(),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataOkReviewOneAlert =
        SafetyCenterData(
            safetyCenterStatusOkReviewOneAlert,
            listOf(safetyCenterIssueInformation(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataRecommendationOneAlert =
        SafetyCenterData(
            safetyCenterStatusRecommendationOneAlert,
            listOf(safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
            listOf(SafetyCenterEntryOrGroup(safetyCenterEntryRecommendation(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataCriticalOneAlert =
        SafetyCenterData(
            safetyCenterStatusCriticalOneAlert,
            listOf(safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataCriticalOneAlertInFlight =
        SafetyCenterData(
            safetyCenterStatusCriticalOneAlert,
            listOf(safetyCenterIssueCritical(SINGLE_SOURCE_ID, isActionInFlight = true)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataFromComplexConfig =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntryGroup.Builder(
                            SafetyCenterCtsData.entryGroupId(DYNAMIC_GROUP_ID), "OK")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                        .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                        .setSummary("OK")
                        .setEntries(
                            listOf(
                                safetyCenterEntryDefaultBuilder(DYNAMIC_BAREBONE_ID).build(),
                                safetyCenterEntryDefaultBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                                    .setEnabled(false)
                                    .build(),
                                safetyCenterEntryDefaultBuilder(DYNAMIC_DISABLED_ID)
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build(),
                                safetyCenterEntryDefaultBuilder(DYNAMIC_OTHER_PACKAGE_ID)
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build()))
                        .build()),
                safetyCenterEntryGroupMixedFromComplexConfig),
            listOf(
                safetyCenterStaticEntryGroupFromComplexConfig,
                safetyCenterStaticEntryGroupMixedFromComplexConfig))

    private val safetyCenterDataFromComplexConfigUpdated =
        SafetyCenterData(
            safetyCenterStatusCriticalSixAlerts,
            listOf(
                safetyCenterIssueCritical(DYNAMIC_BAREBONE_ID),
                safetyCenterIssueCritical(ISSUE_ONLY_BAREBONE_ID),
                safetyCenterIssueRecommendation(DYNAMIC_DISABLED_ID),
                safetyCenterIssueRecommendation(ISSUE_ONLY_ALL_OPTIONAL_ID),
                safetyCenterIssueInformation(DYNAMIC_IN_RIGID_ID),
                safetyCenterIssueInformation(ISSUE_ONLY_IN_RIGID_ID)),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntryGroup.Builder(
                            SafetyCenterCtsData.entryGroupId(DYNAMIC_GROUP_ID), "OK")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                        .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                        // TODO(b/229076771): Check that the first summary of the most critical
                        // entries is used.
                        .setSummary("Critical summary")
                        .setEntries(
                            listOf(
                                safetyCenterEntryCritical(DYNAMIC_BAREBONE_ID),
                                safetyCenterEntryDefaultBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                                    .setEnabled(false)
                                    .build(),
                                safetyCenterEntryRecommendation(DYNAMIC_DISABLED_ID),
                                safetyCenterEntryUnspecified(
                                    DYNAMIC_HIDDEN_ID, pendingIntent = null),
                                safetyCenterEntryOk(DYNAMIC_HIDDEN_WITH_SEARCH_ID),
                                safetyCenterEntryDefaultBuilder(DYNAMIC_OTHER_PACKAGE_ID)
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build()))
                        .build()),
                safetyCenterEntryGroupMixedFromComplexConfig),
            listOf(
                safetyCenterStaticEntryGroupFromComplexConfig,
                safetyCenterStaticEntryGroupMixedUpdatedFromComplexConfig))

    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = context.deviceSupportsSafetyCenter()

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(shouldRunTests)
    }

    @Before
    fun enableSafetyCenterBeforeTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.setEnabled(true)
    }

    @After
    fun clearDataAfterTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.reset()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagEnabled_returnsTrue() {
        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isTrue()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagDisabled_returnsFalse() {
        safetyCenterCtsHelper.setEnabled(false)

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.isSafetyCenterEnabled }
    }

    @Test
    fun setSafetySourceData_validId_setsValue() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val dataToSet = safetySourceCtsData.unspecified
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_twice_replacesValue() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.unspecified)

        val dataToSet = safetySourceCtsData.criticalWithIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_null_clearsValue() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.unspecified)

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_sourceInRigidGroupUnspecified_setsValue() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val dataToSet = safetySourceCtsData.unspecified
        safetyCenterCtsHelper.setData(DYNAMIC_IN_RIGID_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_IN_RIGID_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_unknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.unspecified)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun setSafetySourceData_staticId_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(STATIC_BAREBONE_ID, safetySourceCtsData.unspecified)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_differentPackage_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(
                    DYNAMIC_OTHER_PACKAGE_ID, safetySourceCtsData.unspecified)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID)
    }

    @Test
    fun setSafetySourceData_sourceInRigidGroupNotUnspecified_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(DYNAMIC_IN_RIGID_ID, safetySourceCtsData.information)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Safety source: $DYNAMIC_IN_RIGID_ID is in a rigid group but specified a severity" +
                    " level: ${SafetySourceData.SEVERITY_LEVEL_INFORMATION}")
    }

    @Test
    fun setSafetySourceData_nullUnknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun setSafetySourceData_nullStaticId_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(STATIC_BAREBONE_ID, safetySourceData = null)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_nullDifferentPackage_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(DYNAMIC_OTHER_PACKAGE_ID, safetySourceData = null)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID)
    }

    @Test
    fun setSafetySourceData_issueOnlyWithStatus_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(
                    ISSUE_ONLY_BAREBONE_ID, safetySourceCtsData.unspecified)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected status for issue only safety source: $ISSUE_ONLY_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_dynamicWithIssueOnly_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(
                    DYNAMIC_BAREBONE_ID,
                    SafetySourceCtsData.issuesOnly(safetySourceCtsData.informationIssue))
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Missing status for dynamic safety source: $DYNAMIC_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevUnspecified_setsValue() {
        safetyCenterCtsHelper.setConfig(SEVERITY_ZERO_CONFIG)

        val dataToSet = safetySourceCtsData.unspecified
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevInformation_setsValue() {
        safetyCenterCtsHelper.setConfig(SEVERITY_ZERO_CONFIG)

        val dataToSet = safetySourceCtsData.information
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevInformationWithIssue_throwsException() {
        safetyCenterCtsHelper.setConfig(SEVERITY_ZERO_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(
                    SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIssue)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                    SafetySourceData.SEVERITY_LEVEL_INFORMATION
                }, for issue in safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevCritical_throwsException() {
        safetyCenterCtsHelper.setConfig(SEVERITY_ZERO_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(
                    SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                    SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                }, for safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun setSafetySourceData_withMaxSevRecommendation_met() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val dataToSet = safetySourceCtsData.recommendationWithIssue
        safetyCenterCtsHelper.setData(DYNAMIC_ALL_OPTIONAL_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_ALL_OPTIONAL_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMaxSevRecommendation_notMet() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(
                    DYNAMIC_ALL_OPTIONAL_ID, safetySourceCtsData.criticalWithIssue)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                    SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                }, for safety source: $DYNAMIC_ALL_OPTIONAL_ID")
    }

    @Test
    fun setSafetySourceData_issueOnlyWithMaxSevRecommendation_met() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val dataToSet = SafetySourceCtsData.issuesOnly(safetySourceCtsData.recommendationIssue)
        safetyCenterCtsHelper.setData(ISSUE_ONLY_ALL_OPTIONAL_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(ISSUE_ONLY_ALL_OPTIONAL_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_issueOnlyWithMaxSevRecommendation_notMet() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(
                    ISSUE_ONLY_ALL_OPTIONAL_ID,
                    SafetySourceCtsData.issuesOnly(safetySourceCtsData.criticalIssue))
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                    SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                }, for issue in safety source: $ISSUE_ONLY_ALL_OPTIONAL_ID")
    }

    @Test
    fun setSafetySourceData_withFlagDisabled_doesntSetData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setEnabled(false)

        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID, safetySourceCtsData.unspecified, EVENT_SOURCE_STATE_CHANGED)

        safetyCenterCtsHelper.setEnabled(true)
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetySourceData(
                SINGLE_SOURCE_ID, safetySourceCtsData.unspecified, EVENT_SOURCE_STATE_CHANGED)
        }
    }

    @Test
    fun getSafetySourceData_validId_noData_returnsNull() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun getSafetySourceData_unknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun getSafetySourceData_staticId_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.getSafetySourceDataWithPermission(STATIC_BAREBONE_ID)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun getSafetySourceData_differentPackage_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_OTHER_PACKAGE_ID)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID)
    }

    @Test
    fun getSafetySourceData_withFlagDisabled_returnsNull() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.unspecified)
        safetyCenterCtsHelper.setEnabled(false)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun getSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.getSafetySourceData(SINGLE_SOURCE_ID)
        }
    }

    @Test
    fun reportSafetySourceError_callsErrorListener() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        val safetyCenterErrorDetailsFromListener = listener.receiveSafetyCenterErrorDetails()

        assertThat(safetyCenterErrorDetailsFromListener)
            .isEqualTo(SafetyCenterErrorDetails("Error reported from source: $SINGLE_SOURCE_ID"))
    }

    @Test
    // This test runs the default no-op implementation of OnSafetyCenterDataChangedListener#onError
    // for code coverage purposes.
    fun reportSafetySourceError_withDefaultErrorListener() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val fakeExecutor = FakeExecutor()
        val listener =
            object : OnSafetyCenterDataChangedListener {
                override fun onSafetyCenterDataChanged(safetyCenterData: SafetyCenterData) {}
            }
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            fakeExecutor, listener)
        fakeExecutor.getNextTask().run()

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        fakeExecutor.getNextTask().run()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
    }

    @Test
    fun reportSafetySourceError_unknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.reportSafetySourceErrorWithPermission(
                    SINGLE_SOURCE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun reportSafetySourceError_staticId_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.reportSafetySourceErrorWithPermission(
                    STATIC_BAREBONE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun reportSafetySourceError_differentPackage_throwsIllegalArgumentException() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.reportSafetySourceErrorWithPermission(
                    DYNAMIC_OTHER_PACKAGE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID)
    }

    @Test
    fun reportSafetySourceError_withFlagDisabled_doesntCallErrorListener() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterCtsHelper.setEnabled(false)

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun reportSafetySourceError_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.reportSafetySourceError(
                SINGLE_SOURCE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenImplicitReceiverHasPermission_receiverCalled() {
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver(context)

        val receiverValue =
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(false)

        assertThat(receiverValue).isFalse()

        val toggledReceiverValue =
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(true)

        assertThat(toggledReceiverValue).isTrue()
        enabledChangedReceiver.unregister()
    }

    @Test
    fun safetyCenterEnabledChanged_whenImplicitReceiverDoesntHavePermission_receiverNotCalled() {
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver(context)

        assertFailsWith(TimeoutCancellationException::class) {
            enabledChangedReceiver.setSafetyCenterEnabledWithoutReceiverPermissionAndWait(
                false, TIMEOUT_SHORT)
        }
        enabledChangedReceiver.unregister()
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverHasPermission_receiverCalled() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val receiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(false)

        assertThat(receiverValue).isFalse()

        val toggledReceiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(true)

        assertThat(toggledReceiverValue).isTrue()
    }

    @Test
    fun safetyCenterEnabledChanged_valueDoesntChange_receiverNotCalled() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                true, TIMEOUT_SHORT)
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverDoesntHavePermission_receiverNotCalled() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithoutReceiverPermissionAndWait(
                false, TIMEOUT_SHORT)
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverNotInConfig_receiverNotCalled() {
        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false, TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_sourceSendsRescanData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.criticalWithIssue

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.criticalWithIssue)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_sourceSendsPageOpenData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_notCalledIfSourceDoesntSupportPageOpen() {
        safetyCenterCtsHelper.setConfig(NO_PAGE_OPEN_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN, TIMEOUT_SHORT)
        }

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_whenSourceClearsData_sourceSendsNullData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] = null

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_withMultipleSourcesInConfig_multipleSourcesSendData() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, SOURCE_ID_1)] =
            safetySourceCtsData.criticalWithIssue
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, SOURCE_ID_3)] =
            safetySourceCtsData.information

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceCtsData.criticalWithIssue)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isNull()
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_withMultipleSourcesOnPageOpen_onlyUpdatesAllowedSources() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        SafetySourceReceiver.safetySourceData[SafetySourceDataKey(REFRESH_GET_DATA, SOURCE_ID_1)] =
            safetySourceCtsData.criticalWithIssue
        SafetySourceReceiver.safetySourceData[SafetySourceDataKey(REFRESH_GET_DATA, SOURCE_ID_3)] =
            safetySourceCtsData.information

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceCtsData.criticalWithIssue)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isNull()
        // SOURCE_ID_3 doesn't support refresh on page open.
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isNull()
    }

    @Test
    fun refreshSafetySources_whenReceiverDoesntHavePermission_sourceDoesntSendData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.criticalWithIssue

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithoutReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK, TIMEOUT_SHORT)
        }
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_whenSourceNotInConfig_sourceDoesntSendData() {
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN, TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_sendsBroadcastId() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val lastReceivedBroadcastId =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertThat(lastReceivedBroadcastId).isNotNull()
    }

    @Test
    fun refreshSafetySources_sendsDifferentBroadcastIdsOnEachMethodCall() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        val broadcastId1 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK)
        val broadcastId2 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertThat(broadcastId1).isNotEqualTo(broadcastId2)
    }

    @Test
    fun refreshSafetySources_refreshAfterSuccessfulRefresh_completesSuccessfully() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.criticalWithIssue

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.criticalWithIssue)
    }

    @Test
    fun refreshSafetySources_refreshAfterFailedRefresh_completesSuccessfully() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information
        SafetySourceReceiver.shouldReportSafetySourceError = true
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)
        SafetySourceReceiver.shouldReportSafetySourceError = false

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_waitForPreviousRefreshToTimeout_completesSuccessfully() {
        SafetyCenterFlags.refreshTimeout = TIMEOUT_SHORT
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)
        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData1).isNull()
        // Wait for the ongoing refresh to timeout.
        listener.receiveSafetyCenterErrorDetails()
        SafetyCenterFlags.refreshTimeout = TIMEOUT_LONG
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_withoutAllowingPreviousRefreshToTimeout_completesSuccessfully() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_withTrackedSourceThatTimesOut_timesOut() {
        SafetyCenterFlags.refreshTimeout = TIMEOUT_SHORT
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        // SOURCE_ID_1 will timeout
        for (sourceId in listOf(SOURCE_ID_2, SOURCE_ID_3)) {
            SafetySourceReceiver.safetySourceData[
                    SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, sourceId)] =
                safetySourceCtsData.information
        }
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)
        val safetyCenterErrorDetailsFromListener = listener.receiveSafetyCenterErrorDetails()

        // TODO(b/229080761): Implement proper error message.
        assertThat(safetyCenterErrorDetailsFromListener)
            .isEqualTo(SafetyCenterErrorDetails("Scan timeout"))
    }

    @Test
    fun refreshSafetySources_withUntrackedSourceThatTimesOut_doesNotTimeOut() {
        SafetyCenterFlags.refreshTimeout = TIMEOUT_SHORT
        SafetyCenterFlags.untrackedSources = setOf(SOURCE_ID_1)
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        // SOURCE_ID_1 will timeout
        for (sourceId in listOf(SOURCE_ID_2, SOURCE_ID_3)) {
            SafetySourceReceiver.safetySourceData[
                    SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, sourceId)] =
                safetySourceCtsData.information
        }
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails()
        }
    }

    @Test
    fun refreshSafetySources_withMultipleUntrackedSourcesThatTimeOut_doesNotTimeOut() {
        SafetyCenterFlags.refreshTimeout = TIMEOUT_SHORT
        SafetyCenterFlags.untrackedSources = setOf(SOURCE_ID_1, SOURCE_ID_2)
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        // SOURCE_ID_1 and SOURCE_ID_2 will timeout
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, SOURCE_ID_3)] =
            safetySourceCtsData.information
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails()
        }
    }

    @Test
    fun refreshSafetySources_withEmptyUntrackedSourceConfigAndSourceThatTimesOut_timesOut() {
        SafetyCenterFlags.refreshTimeout = TIMEOUT_SHORT
        SafetyCenterFlags.untrackedSources = emptySet()
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        // SOURCE_ID_1 will timeout
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)
        val safetyCenterErrorDetailsFromListener = listener.receiveSafetyCenterErrorDetails()

        // TODO(b/229080761): Implement proper error message.
        assertThat(safetyCenterErrorDetailsFromListener)
            .isEqualTo(SafetyCenterErrorDetails("Scan timeout"))
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_notifiesUiDuringRescan() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val refreshStatus1 = listener.receiveSafetyCenterData().status.refreshStatus
        assertThat(refreshStatus1).isEqualTo(REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
        val refreshStatus2 = listener.receiveSafetyCenterData().status.refreshStatus
        assertThat(refreshStatus2).isEqualTo(REFRESH_STATUS_NONE)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_notifiesUiWithFetch() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val refreshStatus1 = listener.receiveSafetyCenterData().status.refreshStatus
        assertThat(refreshStatus1).isEqualTo(REFRESH_STATUS_DATA_FETCH_IN_PROGRESS)
        val refreshStatus2 = listener.receiveSafetyCenterData().status.refreshStatus
        assertThat(refreshStatus2).isEqualTo(REFRESH_STATUS_NONE)
    }

    @Test
    fun refreshSafetySources_withFlagDisabled_doesntRefreshSources() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setEnabled(false)

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN, TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withInvalidRefreshSeason_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.refreshSafetySourcesWithPermission(143201)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected refresh reason: 143201")
    }

    @Test
    fun refreshSafetySources_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_RESCAN_BUTTON_CLICK)
        }
    }

    @Test
    fun getSafetyCenterConfig_withFlagEnabled_isNotNull() {
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNotNull()
    }

    @Test
    fun getSafetyCenterConfig_withFlagDisabled_isNotNull() {
        safetyCenterCtsHelper.setEnabled(false)

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNotNull()
    }

    @Test
    fun getSafetyCenterConfig_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterConfig }
    }

    @Test
    fun getSafetyCenterData_withoutDataProvided_returnsDataFromConfig() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun getSafetyCenterData_withComplexConfigWithoutDataProvided_returnsDataFromConfig() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val apiSafetyCenterData =
            safetyCenterManager.getSafetyCenterDataWithPermission().normalize()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    fun getSafetyCenterData_withSomeDataProvided_returnsDataProvided() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.unspecified)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataUnspecified)
    }

    @Test
    fun getSafetyCenterData_withUpdatedData_returnsUpdatedData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        val previousApiSafetyCenterData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isNotEqualTo(previousApiSafetyCenterData)
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_withInformationIssue_returnsOverallStatusOkWithOneAlert() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalStatusAndInfoIssue_returnsOverallStatusOkReviewOneAlert() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithInformationIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkReviewOneAlert)
    }

    @Test
    fun getSafetyCenterData_withRecommendationIssue_returnsOverallStatusRecommendationOneAlert() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataRecommendationOneAlert)
    }

    @Test
    fun getSafetyCenterData_withComplexConfigWithSomeDataProvided_returnsDataProvided() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)
        safetyCenterCtsHelper.setData(DYNAMIC_BAREBONE_ID, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setData(
            DYNAMIC_DISABLED_ID, safetySourceCtsData.recommendationWithIssue)
        safetyCenterCtsHelper.setData(DYNAMIC_HIDDEN_ID, safetySourceCtsData.unspecified)
        safetyCenterCtsHelper.setData(
            DYNAMIC_HIDDEN_WITH_SEARCH_ID, safetySourceCtsData.information)
        safetyCenterCtsHelper.setData(
            ISSUE_ONLY_BAREBONE_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.criticalIssue))
        safetyCenterCtsHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.recommendationIssue))
        safetyCenterCtsHelper.setData(DYNAMIC_IN_RIGID_ID, safetySourceCtsData.unspecifiedWithIssue)
        safetyCenterCtsHelper.setData(
            ISSUE_ONLY_IN_RIGID_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.informationIssue))

        val apiSafetyCenterData =
            safetyCenterManager.getSafetyCenterDataWithPermission().normalize()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfigUpdated)
    }

    @Test
    fun getSafetyCenterData_withFlagDisabled_returnsDefaultData() {
        safetyCenterCtsHelper.setEnabled(false)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(SafetyCenterCtsData.DEFAULT)
    }

    @Test
    fun getSafetyCenterData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterData }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWithSafetyCenterDataFromConfig() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val listener = safetyCenterCtsHelper.addListener(skipInitialData = false)

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledOnSafetySourceData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataChanges() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataCriticalOneAlert)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataCleared() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataStaysNull() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataDoesntChange() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()
        val dataToSet = safetySourceCtsData.information
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToSet)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_oneShot_doesntDeadlock() {
        val listener = SafetyCenterCtsListener()
        val oneShotListener =
            object : OnSafetyCenterDataChangedListener {
                override fun onSafetyCenterDataChanged(safetyCenterData: SafetyCenterData) {
                    safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(this)
                    listener.onSafetyCenterDataChanged(safetyCenterData)
                }
            }
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), oneShotListener)

        // Check that we don't deadlock when using a one-shot listener. This is because adding the
        // listener could call it while holding a lock; which would cause a deadlock if the listener
        // wasn't oneway.
        listener.receiveSafetyCenterData()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withFlagDisabled_listenerNotCalled() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setEnabled(false)

        val listener = SafetyCenterCtsListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = SafetyCenterCtsListener()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(directExecutor(), listener)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerRemovedNotCalledOnSafetySourceData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener1 = safetyCenterCtsHelper.addListener()
        val listener2 = safetyCenterCtsHelper.addListener()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener2)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        listener1.receiveSafetyCenterData()
        assertFailsWith(TimeoutCancellationException::class) {
            listener2.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNeverCalledAfterRemoving() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val fakeExecutor = FakeExecutor()
        val listener = SafetyCenterCtsListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            fakeExecutor, listener)
        fakeExecutor.getNextTask().run()
        listener.receiveSafetyCenterData()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        val callListenerTask = fakeExecutor.getNextTask()
        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
        // Simulate the submitted task being run *after* the remove call completes. Our API should
        // guard against this raciness, as users of this class likely don't expect their listener to
        // be called after calling #removeOnSafetyCenterDataChangedListener.
        callListenerTask.run()

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_withFlagDisabled_removesListener() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterCtsHelper.setEnabled(false)

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)

        safetyCenterCtsHelper.setEnabled(true)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        // Listener is removed as a side effect of the ENABLED_CHANGED broadcast.
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = safetyCenterCtsHelper.addListener()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_existing_callsListenerAndDismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataOkReview)
    }

    @Test
    fun dismissSafetyCenterIssue_issueShowsUpAgainIfSourceStopsSendingItAtLeastOnce() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        val safetyCenterDataAfterSourceStopsSendingDismissedIssue =
            listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourceStopsSendingDismissedIssue)
            .isEqualTo(safetyCenterDataOk)

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)

        val safetyCenterDataAfterSourcePushesDismissedIssueAgain =
            listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourcePushesDismissedIssueAgain)
            .isEqualTo(safetyCenterDataCriticalOneAlert)
    }

    @Test
    fun dismissSafetyCenterIssue_existingWithDifferentIssueType_callsListenerAndDismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, issueTypeId = "some_other_issue_type_id"))

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataOkReview)
    }

    @Test
    fun dismissSafetyCenterIssue_nonExisting_doesntCallListenerOrDismiss() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, "some_unknown_id"))

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_alreadyDismissed_doesntCallListenerOrDismiss() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_dismissedWithDifferentIssueType_doesntCallListenerOrDismiss() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, issueTypeId = "some_other_issue_type_id"))

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withFlagDisabled_doesntCallListenerOrDismiss() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterCtsHelper.setEnabled(false)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_invalidId_throwsIllegalArgumentException() {
        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.dismissSafetyCenterIssueWithPermission("bleh")
        }
    }

    @Test
    fun dismissSafetyCenterIssue_invalidUser_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.dismissSafetyCenterIssueWithPermission(
                SafetyCenterCtsData.issueId(
                    SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, userId = USER_NULL))
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.dismissSafetyCenterIssue("bleh")
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_callsListenerWithInFlightActionAndExecutes() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(RESOLVING_ACTION, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringInlineAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterInlineAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_unmarkInFlightWhenInlineActionError() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()
        SafetySourceReceiver.shouldReportSafetySourceError = true
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(RESOLVING_ACTION, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringInlineAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterInlineAction)
            .isEqualTo(safetyCenterDataCriticalOneAlert)
        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(SafetyCenterErrorDetails("Error reported from source: $SINGLE_SOURCE_ID"))
    }

    @Test
    fun executeSafetyCenterIssueAction_nonExisting_doesntCallListenerOrExecute() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        val listener = safetyCenterCtsHelper.addListener()

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
                SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterCtsData.issueActionId(
                    SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID),
                TIMEOUT_SHORT)
        }
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_alreadyInFlight_doesntCallListenerOrExecute() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))
        listener.receiveSafetyCenterData()

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
                SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterCtsData.issueActionId(
                    SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID),
                TIMEOUT_SHORT)
        }
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_sourceDoesNotRespond_timesOutAndCallsListener() {
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringInlineAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterInlineAction)
            .isEqualTo(safetyCenterDataCriticalOneAlert)
        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error).isEqualTo(SafetyCenterErrorDetails("Resolving action timeout"))
    }

    @Test
    fun executeSafetyCenterIssueAction_tryAgainAfterTimeout_callsListenerWithInFlightAndExecutes() {
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterErrorDetails()
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_LONG
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(RESOLVING_ACTION, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringInlineAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterInlineAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_withFlagDisabled_doesntCallListenerOrExecute() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterCtsHelper.setEnabled(false)

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
                SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterCtsData.issueActionId(
                    SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID),
                TIMEOUT_SHORT)
        }
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_invalidId_throwsIllegalArgumentException() {
        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermission("barf", "burgh")
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_idsDontMatch_throwsIllegalArgumentException() {
        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
                SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterCtsData.issueActionId(
                    SOURCE_ID_1, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_invalidUser_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
                SafetyCenterCtsData.issueId(
                    SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, userId = USER_NULL),
                SafetyCenterCtsData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID,
                    userId = USER_NULL))
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.executeSafetyCenterIssueAction("bleh", "blah")
        }
    }

    @Test
    fun clearAllSafetySourceDataForTests_clearsAllSafetySourceData() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(SOURCE_ID_1, safetySourceCtsData.unspecified)
        safetyCenterCtsHelper.setData(SOURCE_ID_2, safetySourceCtsData.criticalWithIssue)

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        val data1AfterClearing = safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(data1AfterClearing).isNull()
        val data2AfterClearing = safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(data2AfterClearing).isNull()
    }

    @Test
    fun clearAllSafetySourceDataForTests_withFlagDisabled_clearsData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        safetyCenterCtsHelper.setEnabled(false)

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        safetyCenterCtsHelper.setEnabled(true)
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        // Data is cleared as a side effect of the ENABLED_CHANGED broadcast.
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun clearAllSafetySourceDataForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearAllSafetySourceDataForTests()
        }
    }

    @Test
    fun setSafetyCenterConfigForTests_setsConfig() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isEqualTo(SINGLE_SOURCE_CONFIG)
    }

    @Test
    fun setSafetyCenterConfigForTests_withFlagDisabled_doesntSetConfig() {
        safetyCenterCtsHelper.setEnabled(false)

        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)

        safetyCenterCtsHelper.setEnabled(true)
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isNotEqualTo(SINGLE_SOURCE_CONFIG)
    }

    @Test
    fun setSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetyCenterConfigForTests(SINGLE_SOURCE_CONFIG)
        }
    }

    @Test
    fun clearSafetyCenterConfigForTests_clearsConfigSetForTests_doesntSetConfigToNull() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isNotNull()
        assertThat(config).isNotEqualTo(SINGLE_SOURCE_CONFIG)
    }

    @Test
    fun clearSafetyCenterConfigForTests_withFlagDisabled_doesntClearConfig() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setEnabled(false)

        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        safetyCenterCtsHelper.setEnabled(true)
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isEqualTo(SINGLE_SOURCE_CONFIG)
    }

    @Test
    fun clearSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearSafetyCenterConfigForTests()
        }
    }

    private fun safetyCenterEntryDefaultBuilder(sourceId: String) =
        SafetyCenterEntry.Builder(SafetyCenterCtsData.entryId(sourceId), "OK")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
            .setSummary("OK")
            .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)

    private fun safetyCenterEntryUnspecified(
        sourceId: String,
        pendingIntent: PendingIntent? = safetySourceCtsData.redirectPendingIntent
    ) =
        SafetyCenterEntry.Builder(SafetyCenterCtsData.entryId(sourceId), "Unspecified title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
            .setSummary("Unspecified summary")
            .setPendingIntent(pendingIntent)
            .setEnabled(false)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    private fun safetyCenterEntryOk(sourceId: String) =
        SafetyCenterEntry.Builder(SafetyCenterCtsData.entryId(sourceId), "Ok title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_OK)
            .setSummary("Ok summary")
            .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    private fun safetyCenterEntryRecommendation(
        sourceId: String,
        summary: String = "Recommendation summary"
    ) =
        SafetyCenterEntry.Builder(SafetyCenterCtsData.entryId(sourceId), "Recommendation title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setSummary(summary)
            .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    private fun safetyCenterEntryCritical(sourceId: String) =
        SafetyCenterEntry.Builder(SafetyCenterCtsData.entryId(sourceId), "Critical title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
            .setSummary("Critical summary")
            .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    private fun safetyCenterIssueInformation(sourceId: String) =
        SafetyCenterIssue.Builder(
                SafetyCenterCtsData.issueId(sourceId, INFORMATION_ISSUE_ID),
                "Information issue title",
                "Information issue summary")
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_OK)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            SafetyCenterCtsData.issueActionId(
                                sourceId, INFORMATION_ISSUE_ID, INFORMATION_ISSUE_ACTION_ID),
                            "Review",
                            safetySourceCtsData.redirectPendingIntent)
                        .build()))
            .build()

    private fun safetyCenterIssueRecommendation(sourceId: String) =
        SafetyCenterIssue.Builder(
                SafetyCenterCtsData.issueId(sourceId, RECOMMENDATION_ISSUE_ID),
                "Recommendation issue title",
                "Recommendation issue summary")
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            SafetyCenterCtsData.issueActionId(
                                sourceId, RECOMMENDATION_ISSUE_ID, RECOMMENDATION_ISSUE_ACTION_ID),
                            "See issue",
                            safetySourceCtsData.redirectPendingIntent)
                        .build()))
            .build()

    private fun safetyCenterIssueCritical(sourceId: String, isActionInFlight: Boolean = false) =
        SafetyCenterIssue.Builder(
                SafetyCenterCtsData.issueId(sourceId, CRITICAL_ISSUE_ID),
                "Critical issue title",
                "Critical issue summary")
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            SafetyCenterCtsData.issueActionId(
                                sourceId, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID),
                            "Solve issue",
                            safetySourceCtsData.criticalIssueActionPendingIntent)
                        .setWillResolve(true)
                        .setIsInFlight(isActionInFlight)
                        .build()))
            .build()
}
