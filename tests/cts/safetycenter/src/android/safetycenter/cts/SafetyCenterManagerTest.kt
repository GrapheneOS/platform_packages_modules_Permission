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

import android.content.Context
import android.os.UserHandle.USER_NULL
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_INFO
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS
import android.safetycenter.SafetyCenterStatus.REFRESH_STATUS_NONE
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_DEVICE
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_GENERAL
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.Coroutines.waitForWithTimeout
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
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ANDROID_LOCK_SCREEN_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID
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
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SAMPLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SEVERITY_ZERO_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_INVALID_INTENT_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_OTHER_PACKAGE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_3
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_4
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_5
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_6
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_7
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_IN_COLLAPSIBLE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SUMMARY_TEST_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SUMMARY_TEST_GROUP_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.getLockScreenSourceConfig
import android.safetycenter.cts.testing.SafetyCenterCtsData
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterCtsListener
import android.safetycenter.cts.testing.SafetyCenterEnabledChangedReceiver
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.INFORMATION_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.RECOMMENDATION_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Request
import android.safetycenter.cts.testing.SafetySourceIntentHandler.Response
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.dismissSafetyCenterIssueWithPermissionAndWait
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.executeSafetyCenterIssueActionWithPermissionAndWait
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithReceiverPermissionAndWait
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithoutReceiverPermissionAndWait
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.preconditions.ScreenLockHelper
import com.android.safetycenter.resources.SafetyCenterResourcesContext
import com.google.common.base.Preconditions.checkState
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterManager]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterResourcesContext = SafetyCenterResourcesContext.forTests(context)
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)
    private val safetyCenterCtsData = SafetyCenterCtsData(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    private val safetyCenterStatusOk =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_title"),
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_summary"))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusUnknownScanning =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName("scanning_title"),
                safetyCenterResourcesContext.getStringByName("loading_summary"))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
            .setRefreshStatus(REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
            .build()

    private val safetyCenterStatusOkOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_title"),
                safetyCenterCtsData.getAlertString(1))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusOkReviewOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_title"),
                safetyCenterCtsData.getAlertString(1))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusOkReview =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_title"),
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_summary"))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusGeneralRecommendationOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_safety_recommendation_title"),
                safetyCenterCtsData.getAlertString(1))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val safetyCenterStatusAccountRecommendationOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_account_recommendation_title"),
                safetyCenterCtsData.getAlertString(1))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val safetyCenterStatusDeviceRecommendationOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_device_recommendation_title"),
                safetyCenterCtsData.getAlertString(1))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val safetyCenterStatusGeneralCriticalOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_safety_warning_title"),
                safetyCenterCtsData.getAlertString(1))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusGeneralCriticalTwoAlerts =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_safety_warning_title"),
                safetyCenterCtsData.getAlertString(2))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusAccountCriticalOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_account_warning_title"),
                safetyCenterCtsData.getAlertString(1))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusAccountCriticalTwoAlerts =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_account_warning_title"),
                safetyCenterCtsData.getAlertString(2))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusDeviceCriticalOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_device_warning_title"),
                safetyCenterCtsData.getAlertString(1))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusDeviceCriticalTwoAlerts =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_device_warning_title"),
                safetyCenterCtsData.getAlertString(2))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterEntryOrGroupRecommendation =
        SafetyCenterEntryOrGroup(
            safetyCenterCtsData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID))

    private val safetyCenterEntryOrGroupCritical =
        SafetyCenterEntryOrGroup(safetyCenterCtsData.safetyCenterEntryCritical(SINGLE_SOURCE_ID))

    private val safetyCenterEntryGroupMixedFromComplexConfig =
        SafetyCenterEntryOrGroup(
            SafetyCenterEntryGroup.Builder(
                    SafetyCenterCtsData.entryGroupId(MIXED_COLLAPSIBLE_GROUP_ID), "OK")
                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                .setSummary(safetyCenterResourcesContext.getStringByName("group_unknown_summary"))
                .setEntries(
                    listOf(
                        safetyCenterCtsData.safetyCenterEntryDefault(DYNAMIC_IN_COLLAPSIBLE_ID),
                        SafetyCenterEntry.Builder(
                                SafetyCenterCtsData.entryId(STATIC_IN_COLLAPSIBLE_ID), "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                            .setSummary("OK")
                            .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
                            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)
                            .build()))
                .build())

    private val safetyCenterStaticEntryGroupFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("OK")
                    .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
                    .build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
                    .build()))

    private val safetyCenterStaticEntryGroupMixedFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
                    .build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
                    .build()))

    private val safetyCenterStaticEntryGroupMixedUpdatedFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("Unspecified title")
                    .setSummary("Unspecified summary")
                    .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
                    .build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
                    .build()))

    private val safetyCenterDataFromConfigScanning =
        SafetyCenterData(
            safetyCenterStatusUnknownScanning,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData.safetyCenterEntryDefault(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataFromConfig =
        SafetyCenterData(
            safetyCenterCtsData.safetyCenterStatusUnknown,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData.safetyCenterEntryDefault(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataUnspecified =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData.safetyCenterEntryUnspecified(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataOk =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData.safetyCenterEntryOk(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataOkWithIconAction =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData
                        .safetyCenterEntryOkBuilder(SINGLE_SOURCE_ID)
                        .setIconAction(
                            ICON_ACTION_TYPE_INFO,
                            safetySourceCtsData.testActivityRedirectPendingIntent)
                        .build())),
            emptyList())

    private val safetyCenterDataUnknownReviewError =
        SafetyCenterData(
            safetyCenterCtsData.safetyCenterStatusUnknown,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData.safetyCenterEntryError(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataOkOneAlert =
        SafetyCenterData(
            safetyCenterStatusOkOneAlert,
            listOf(safetyCenterCtsData.safetyCenterIssueInformation(SINGLE_SOURCE_ID)),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData.safetyCenterEntryOk(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataOkReviewCriticalEntry =
        SafetyCenterData(
            safetyCenterStatusOkReview,
            emptyList(),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataOkReviewRecommendationEntry =
        SafetyCenterData(
            safetyCenterStatusOkReview,
            emptyList(),
            listOf(safetyCenterEntryOrGroupRecommendation),
            emptyList())

    private val safetyCenterDataOkReviewOneAlert =
        SafetyCenterData(
            safetyCenterStatusOkReviewOneAlert,
            listOf(safetyCenterCtsData.safetyCenterIssueInformation(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataGeneralRecommendationOneAlert =
        SafetyCenterData(
            safetyCenterStatusGeneralRecommendationOneAlert,
            listOf(safetyCenterCtsData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataAccountRecommendationOneAlert =
        SafetyCenterData(
            safetyCenterStatusAccountRecommendationOneAlert,
            listOf(safetyCenterCtsData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataDeviceRecommendationOneAlert =
        SafetyCenterData(
            safetyCenterStatusDeviceRecommendationOneAlert,
            listOf(safetyCenterCtsData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterCtsData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID))),
            emptyList())

    private val safetyCenterDataGeneralCriticalOneAlert =
        SafetyCenterData(
            safetyCenterStatusGeneralCriticalOneAlert,
            listOf(safetyCenterCtsData.safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataAccountCriticalOneAlert =
        SafetyCenterData(
            safetyCenterStatusAccountCriticalOneAlert,
            listOf(safetyCenterCtsData.safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataDeviceCriticalOneAlert =
        SafetyCenterData(
            safetyCenterStatusDeviceCriticalOneAlert,
            listOf(safetyCenterCtsData.safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataCriticalOneAlertInFlight =
        SafetyCenterData(
            safetyCenterStatusGeneralCriticalOneAlert,
            listOf(
                safetyCenterCtsData.safetyCenterIssueCritical(
                    SINGLE_SOURCE_ID, isActionInFlight = true)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())

    private val safetyCenterDataFromComplexConfig =
        SafetyCenterData(
            safetyCenterCtsData.safetyCenterStatusUnknown,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntryGroup.Builder(
                            SafetyCenterCtsData.entryGroupId(DYNAMIC_GROUP_ID), "OK")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                        .setSummary(
                            safetyCenterResourcesContext.getStringByName("group_unknown_summary"))
                        .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                        .setEntries(
                            listOf(
                                safetyCenterCtsData.safetyCenterEntryDefault(DYNAMIC_BAREBONE_ID),
                                safetyCenterCtsData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                                    .setEnabled(false)
                                    .build(),
                                safetyCenterCtsData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_DISABLED_ID)
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build(),
                                safetyCenterCtsData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_OTHER_PACKAGE_ID)
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
            safetyCenterCtsData.safetyCenterStatusCritical(6),
            listOf(
                safetyCenterCtsData.safetyCenterIssueCritical(DYNAMIC_BAREBONE_ID),
                safetyCenterCtsData.safetyCenterIssueCritical(ISSUE_ONLY_BAREBONE_ID),
                safetyCenterCtsData.safetyCenterIssueRecommendation(DYNAMIC_DISABLED_ID),
                safetyCenterCtsData.safetyCenterIssueRecommendation(ISSUE_ONLY_ALL_OPTIONAL_ID),
                safetyCenterCtsData.safetyCenterIssueInformation(DYNAMIC_IN_RIGID_ID),
                safetyCenterCtsData.safetyCenterIssueInformation(ISSUE_ONLY_IN_RIGID_ID)),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntryGroup.Builder(
                            SafetyCenterCtsData.entryGroupId(DYNAMIC_GROUP_ID), "OK")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                        .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                        .setSummary("Critical summary")
                        .setEntries(
                            listOf(
                                safetyCenterCtsData.safetyCenterEntryCritical(DYNAMIC_BAREBONE_ID),
                                safetyCenterCtsData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                                    .setEnabled(false)
                                    .build(),
                                safetyCenterCtsData.safetyCenterEntryRecommendation(
                                    DYNAMIC_DISABLED_ID),
                                safetyCenterCtsData.safetyCenterEntryUnspecified(
                                    DYNAMIC_HIDDEN_ID, pendingIntent = null),
                                safetyCenterCtsData.safetyCenterEntryOk(
                                    DYNAMIC_HIDDEN_WITH_SEARCH_ID),
                                safetyCenterCtsData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_OTHER_PACKAGE_ID)
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
        safetyCenterCtsHelper.setup()
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

        val dataToSet = safetySourceCtsData.criticalWithResolvingGeneralIssue
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
                    SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
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

        val dataToSet = safetySourceCtsData.recommendationWithGeneralIssue
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
                    DYNAMIC_ALL_OPTIONAL_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
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

        val dataToSet =
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.recommendationGeneralIssue)
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
                    SafetySourceCtsData.issuesOnly(
                        safetySourceCtsData.criticalResolvingGeneralIssue))
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                    SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                }, for issue in safety source: $ISSUE_ONLY_ALL_OPTIONAL_ID")
    }

    @Test
    fun setSafetySourceData_withEmptyCategoryAllowlists_met() {
        SafetyCenterFlags.issueCategoryAllowlists = emptyMap()
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val dataToSet = safetySourceCtsData.recommendationWithAccountIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMissingAllowlistForCategory_met() {
        SafetyCenterFlags.issueCategoryAllowlists =
            mapOf(
                ISSUE_CATEGORY_DEVICE to setOf(SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_GENERAL to setOf(SAMPLE_SOURCE_ID))
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val dataToSet = safetySourceCtsData.recommendationWithAccountIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withAllowlistedSourceForCategory_met() {
        SafetyCenterFlags.issueCategoryAllowlists =
            mapOf(
                ISSUE_CATEGORY_ACCOUNT to setOf(SINGLE_SOURCE_ID, SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_DEVICE to setOf(SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_GENERAL to setOf(SAMPLE_SOURCE_ID))
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val dataToSet = safetySourceCtsData.recommendationWithAccountIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withEmptyAllowlistForCategory_notMet() {
        SafetyCenterFlags.issueCategoryAllowlists = mapOf(ISSUE_CATEGORY_ACCOUNT to emptySet())
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(
                    SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithAccountIssue)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected issue category: $ISSUE_CATEGORY_ACCOUNT, for issue in safety source: " +
                    SINGLE_SOURCE_ID)
    }

    @Test
    fun setSafetySourceData_withoutSourceInAllowlistForCategory_notMet() {
        SafetyCenterFlags.issueCategoryAllowlists =
            mapOf(
                ISSUE_CATEGORY_ACCOUNT to setOf(SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_DEVICE to setOf(SINGLE_SOURCE_ID))
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterCtsHelper.setData(
                    SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithAccountIssue)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected issue category: $ISSUE_CATEGORY_ACCOUNT, for issue in safety source: " +
                    SINGLE_SOURCE_ID)
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
    fun reportSafetySourceError_notifiesErrorEntryButDoesntCallErrorListener() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataUnknownReviewError)
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
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
    fun reportSafetySourceError_withFlagDisabled_doesntCallListener() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterCtsHelper.setEnabled(false)

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
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
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.criticalWithResolvingGeneralIssue))
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData)
            .isEqualTo(safetySourceCtsData.criticalWithResolvingGeneralIssue)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_sourceSendsPageOpenData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_allowsRefreshingInAForegroundService() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.runInForegroundService = true
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_notCalledIfSourceDoesntSupportPageOpen() {
        safetyCenterCtsHelper.setConfig(NO_PAGE_OPEN_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))

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
        SafetySourceReceiver.setResponse(Request.Rescan(SINGLE_SOURCE_ID), Response.ClearData)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_withMultipleSourcesInConfig_multipleSourcesSendData() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        SafetySourceReceiver.apply {
            setResponse(
                Request.Rescan(SOURCE_ID_1),
                Response.SetData(safetySourceCtsData.criticalWithResolvingGeneralIssue))
            setResponse(
                Request.Rescan(SOURCE_ID_3), Response.SetData(safetySourceCtsData.information))
        }

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1)
            .isEqualTo(safetySourceCtsData.criticalWithResolvingGeneralIssue)
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
        SafetySourceReceiver.apply {
            setResponse(
                Request.Refresh(SOURCE_ID_1),
                Response.SetData(safetySourceCtsData.criticalWithResolvingGeneralIssue))
            setResponse(
                Request.Refresh(SOURCE_ID_3), Response.SetData(safetySourceCtsData.information))
        }

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1)
            .isEqualTo(safetySourceCtsData.criticalWithResolvingGeneralIssue)
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
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.criticalWithResolvingGeneralIssue))

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
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))

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
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))

        val broadcastId1 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK)
        val broadcastId2 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertThat(broadcastId1).isNotEqualTo(broadcastId2)
    }

    @Test
    fun refreshSafetySources_repliesWithWrongBroadcastId_doesntCompleteRefresh() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information, overrideBroadcastId = "invalid"))
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        // Because wrong ID, refresh hasn't finished. Wait for timeout.
        listener.receiveSafetyCenterErrorDetails()
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_LONG)

        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_refreshAfterSuccessfulRefresh_completesSuccessfully() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.criticalWithResolvingGeneralIssue))

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData)
            .isEqualTo(safetySourceCtsData.criticalWithResolvingGeneralIssue)
    }

    @Test
    fun refreshSafetySources_refreshAfterFailedRefresh_completesSuccessfully() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(Request.Rescan(SINGLE_SOURCE_ID), Response.Error)
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_waitForPreviousRefreshToTimeout_completesSuccessfully() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)
        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData1).isNull()
        // Wait for the ongoing refresh to timeout.
        listener.receiveSafetyCenterErrorDetails()
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_LONG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))

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
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceCtsData.information)
    }

    @Test
    fun refreshSafetySources_withTrackedSourceThatTimesOut_timesOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        // SOURCE_ID_1 will timeout
        for (sourceId in listOf(SOURCE_ID_2, SOURCE_ID_3)) {
            SafetySourceReceiver.setResponse(
                Request.Rescan(sourceId), Response.SetData(safetySourceCtsData.information))
        }
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val safetyCenterErrorDetailsFromListener = listener.receiveSafetyCenterErrorDetails()
        assertThat(safetyCenterErrorDetailsFromListener)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("refresh_timeout")))
    }

    @Test
    fun refreshSafetySources_withUntrackedSourceThatTimesOut_doesNotTimeOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        SafetyCenterFlags.untrackedSources = setOf(SOURCE_ID_1)
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        // SOURCE_ID_1 will timeout
        for (sourceId in listOf(SOURCE_ID_2, SOURCE_ID_3)) {
            SafetySourceReceiver.setResponse(
                Request.Rescan(sourceId), Response.SetData(safetySourceCtsData.information))
        }
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withMultipleUntrackedSourcesThatTimeOut_doesNotTimeOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        SafetyCenterFlags.untrackedSources = setOf(SOURCE_ID_1, SOURCE_ID_2)
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        // SOURCE_ID_1 and SOURCE_ID_2 will timeout
        SafetySourceReceiver.setResponse(
            Request.Rescan(SOURCE_ID_3), Response.SetData(safetySourceCtsData.information))
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withEmptyUntrackedSourceConfigAndSourceThatTimesOut_timesOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        // SINGLE_SOURCE_ID will timeout
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val safetyCenterErrorDetailsFromListener = listener.receiveSafetyCenterErrorDetails()
        assertThat(safetyCenterErrorDetailsFromListener)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("refresh_timeout")))
    }

    @Test
    fun refreshSafetySources_withTrackedSourceThatHasNoReceiver_doesNotTimeOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_OTHER_PACKAGE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithPermission(REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withShowEntriesOnTimeout_marksSafetySourceAsError() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        SafetyCenterFlags.showErrorEntriesOnTimeout = true
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val safetyCenterBeforeTimeout = listener.receiveSafetyCenterData()
        assertThat(safetyCenterBeforeTimeout).isEqualTo(safetyCenterDataFromConfigScanning)
        val safetyCenterDataAfterTimeout = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterTimeout).isEqualTo(safetyCenterDataUnknownReviewError)
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withShowEntriesOnTimeout_stopsShowingErrorWhenTryingAgain() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        SafetyCenterFlags.showErrorEntriesOnTimeout = true
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterData()

        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_LONG)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val safetyCenterDataWhenTryingAgain = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataWhenTryingAgain).isEqualTo(safetyCenterDataFromConfigScanning)
        val safetyCenterDataWhenFinishingRefresh = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataWhenFinishingRefresh).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_notifiesUiDuringRescan() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val status1 = listener.receiveSafetyCenterData().status
        assertThat(status1.refreshStatus).isEqualTo(REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
        assertThat(status1.title.toString())
            .isEqualTo(safetyCenterResourcesContext.getStringByName("scanning_title"))
        assertThat(status1.summary.toString())
            .isEqualTo(safetyCenterResourcesContext.getStringByName("loading_summary"))
        val status2 = listener.receiveSafetyCenterData().status
        assertThat(status2.refreshStatus).isEqualTo(REFRESH_STATUS_NONE)
        assertThat(status2).isEqualTo(safetyCenterStatusOk)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_notifiesUiWithFetch() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val status1 = listener.receiveSafetyCenterData().status
        assertThat(status1.refreshStatus).isEqualTo(REFRESH_STATUS_DATA_FETCH_IN_PROGRESS)
        assertThat(status1.title.toString())
            .isEqualTo(safetyCenterResourcesContext.getStringByName("scanning_title"))
        assertThat(status1.summary.toString())
            .isEqualTo(safetyCenterResourcesContext.getStringByName("loading_summary"))
        val status2 = listener.receiveSafetyCenterData().status
        assertThat(status2.refreshStatus).isEqualTo(REFRESH_STATUS_NONE)
        assertThat(status2).isEqualTo(safetyCenterStatusOk)
    }

    @Test
    fun refreshSafetySources_pageOpenRefreshWithPreExistingData_notifiesUiWithExistingTitle() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID), Response.SetData(safetySourceCtsData.information))
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val status1 = listener.receiveSafetyCenterData().status
        assertThat(status1.refreshStatus).isEqualTo(REFRESH_STATUS_DATA_FETCH_IN_PROGRESS)
        assertThat(status1.title.toString()).isEqualTo(safetyCenterStatusOk.title.toString())
        assertThat(status1.summary.toString())
            .isEqualTo(safetyCenterResourcesContext.getStringByName("loading_summary"))
        val status2 = listener.receiveSafetyCenterData().status
        assertThat(status2.refreshStatus).isEqualTo(REFRESH_STATUS_NONE)
        assertThat(status2).isEqualTo(safetyCenterStatusOk)
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
    fun refreshSafetySources_withRefreshReasonOther_backgroundRefreshDeniedSourcesDoNotSendData() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        // All three sources have data
        SafetySourceReceiver.apply {
            setResponse(
                Request.Refresh(SOURCE_ID_1),
                Response.SetData(safetySourceCtsData.criticalWithResolvingGeneralIssue))
            setResponse(
                Request.Refresh(SOURCE_ID_2), Response.SetData(safetySourceCtsData.information))
            setResponse(
                Request.Refresh(SOURCE_ID_3), Response.SetData(safetySourceCtsData.information))
        }
        // But sources 1 and 3 should not be refreshed in background
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SOURCE_ID_1, SOURCE_ID_3)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(REFRESH_REASON_OTHER)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1).isNull()
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceCtsData.information)
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isNull()
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_noBackgroundRefreshSourceSendsData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.criticalWithResolvingGeneralIssue))
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SINGLE_SOURCE_ID)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isEqualTo(safetySourceCtsData.criticalWithResolvingGeneralIssue)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonButtonClicked_noBackgroundRefreshSourceSendsData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.criticalWithResolvingGeneralIssue))
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SINGLE_SOURCE_ID)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isEqualTo(safetySourceCtsData.criticalWithResolvingGeneralIssue)
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

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

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
    fun getSafetyCenterData_withIconAction_returnsDataWithIconAction() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIconAction)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkWithIconAction)
    }

    @Test
    fun getSafetyCenterData_withUpdatedData_returnsUpdatedData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        val previousApiSafetyCenterData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isNotEqualTo(previousApiSafetyCenterData)
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
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
    fun getSafetyCenterData_withRecommendationGeneralIssue_returnsGeneralRecommendationOneAlert() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithGeneralIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataGeneralRecommendationOneAlert)
    }

    @Test
    fun getSafetyCenterData_withRecommendationAccountIssue_returnsAccountRecommendationOneAlert() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithAccountIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataAccountRecommendationOneAlert)
    }

    @Test
    fun getSafetyCenterData_withRecommendationDeviceIssue_returnsDeviceRecommendationOneAlert() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithDeviceIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataDeviceRecommendationOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalGeneralIssue_returnsOverallStatusGeneralCriticalOneAlert() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalAccountIssue_returnsOverallStatusAccountCriticalOneAlert() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingAccountIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataAccountCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalDeviceIssue_returnsOverallStatusDeviceCriticalOneAlert() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingDeviceIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataDeviceCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_singleSourceIssues_returnsOverallStatusBasedOnHigherSeverityIssue() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingDeviceIssueAndRecommendationIssue)

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusDeviceCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_multipleSourcesIssues_returnsOverallStatusBasedOnHigherSeverityIssue() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.recommendationWithAccountIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3, safetySourceCtsData.criticalWithResolvingDeviceIssue)

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusDeviceCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_multipleIssues_returnsOverallStatusBasedOnConfigOrdering() {
        safetyCenterCtsHelper.setConfig(MULTIPLE_SOURCES_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3, safetySourceCtsData.criticalWithResolvingDeviceIssue)

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusGeneralCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_criticalDeviceIssues_returnsOverallStatusBasedOnAddIssueCallOrder() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceCtsData
                .defaultCriticalDataBuilder()
                .addIssue(
                    safetySourceCtsData
                        .defaultCriticalResolvingIssueBuilder("critical issue num 1")
                        .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT)
                        .build())
                .addIssue(
                    safetySourceCtsData
                        .defaultCriticalResolvingIssueBuilder("critical issue num 2")
                        .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
                        .build())
                .build())

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusAccountCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_withComplexConfigWithSomeDataProvided_returnsDataProvided() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)
        safetyCenterCtsHelper.setData(
            DYNAMIC_BAREBONE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            DYNAMIC_DISABLED_ID, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(DYNAMIC_HIDDEN_ID, safetySourceCtsData.unspecified)
        safetyCenterCtsHelper.setData(
            DYNAMIC_HIDDEN_WITH_SEARCH_ID, safetySourceCtsData.information)
        safetyCenterCtsHelper.setData(
            ISSUE_ONLY_BAREBONE_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.criticalResolvingGeneralIssue))
        safetyCenterCtsHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.recommendationGeneralIssue))
        safetyCenterCtsHelper.setData(DYNAMIC_IN_RIGID_ID, safetySourceCtsData.unspecifiedWithIssue)
        safetyCenterCtsHelper.setData(
            ISSUE_ONLY_IN_RIGID_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.informationIssue))

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfigUpdated)
    }

    @Test
    fun getSafetyCenterData_withCriticalEntriesAsMax_usesCriticalSummaryForGroup() {
        safetyCenterCtsHelper.setConfig(SUMMARY_TEST_CONFIG)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_4,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_5,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_RECOMMENDATION, entrySummary = "recommendation"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_6,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_CRITICAL_WARNING, entrySummary = "critical 1"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_7,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_CRITICAL_WARNING, entrySummary = "critical 2"))
        // STATIC_IN_COLLAPSIBLE_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("critical 1")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun getSafetyCenterData_withRecommendationEntriesAsMax_usesRecommendationSummaryForGroup() {
        safetyCenterCtsHelper.setConfig(SUMMARY_TEST_CONFIG)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_4,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_5,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_RECOMMENDATION, entrySummary = "recommendation 1"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_6,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_RECOMMENDATION, entrySummary = "recommendation 2"))
        // SOURCE_ID_7 leave as an UNKNOWN dynamic entry
        // STATIC_IN_COLLAPSIBLE_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("recommendation 1")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
    }

    @Test
    fun getSafetyCenterData_withInformationWithIssueEntriesAsMax_usesInformationSummaryForGroup() {
        safetyCenterCtsHelper.setConfig(SUMMARY_TEST_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 1"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 2"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 1"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_4,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue 1",
                withIssue = true))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_5,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue 2",
                withIssue = true))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_6,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 2"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_7,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 3"))
        // STATIC_IN_COLLAPSIBLE_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("information with issue 1")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSafetyCenterData_withInformationEntriesAsMax_usesDefaultSummaryForGroup() {
        safetyCenterCtsHelper.setConfig(SUMMARY_TEST_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 1"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 2"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 1"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_4,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 2"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_5,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 3"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_6,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 3"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_7,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 4"))
        // STATIC_IN_COLLAPSIBLE_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("OK")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSafetyCenterData_withUnspecifiedEntriesAsMax_usesDefaultSummaryForGroup() {
        safetyCenterCtsHelper.setConfig(SUMMARY_TEST_CONFIG)
        safetyCenterCtsHelper.setData(
            SOURCE_ID_1,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 1"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 2"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 3"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_4,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 4"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_5,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified with issue",
                withIssue = true))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_6,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 6"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_7,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified 7"))
        // STATIC_IN_COLLAPSIBLE_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("OK")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
    }

    @Test
    fun getSafetyCenterData_withErrorEntries_usesPluralErrorSummaryForGroup() {
        safetyCenterCtsHelper.setConfig(SUMMARY_TEST_CONFIG)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_2, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_4,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified"))
        // SOURCE_ID_5 leave as an UNKNOWN dynamic entry
        safetyCenterCtsHelper.setData(
            SOURCE_ID_6,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION, entrySummary = "information"))
        // SOURCE_ID_7 leave as an UNKNOWN dynamic entry
        // STATIC_IN_COLLAPSIBLE_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo(safetyCenterCtsData.getRefreshErrorString(2))
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getSafetyCenterData_withErrorEntry_usesSingularErrorSummaryForGroup() {
        safetyCenterCtsHelper.setConfig(SUMMARY_TEST_CONFIG)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        // SOURCE_ID_2 leave as an UNKNOWN dynamic entry
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_4,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified"))
        // SOURCE_ID_5 leave as an UNKNOWN dynamic entry
        // SOURCE_ID_6 leave as an UNKNOWN dynamic entry
        safetyCenterCtsHelper.setData(
            SOURCE_ID_7,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION, entrySummary = "information"))
        // STATIC_IN_COLLAPSIBLE_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo(safetyCenterCtsData.getRefreshErrorString(1))
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getSafetyCenterData_withUnknownEntries_usesUnknownSummaryForGroup() {
        safetyCenterCtsHelper.setConfig(SUMMARY_TEST_CONFIG)
        // SOURCE_ID_1 leave as an UNKNOWN dynamic entry
        // SOURCE_ID_2 leave as an UNKNOWN dynamic entry
        safetyCenterCtsHelper.setData(
            SOURCE_ID_3,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_4,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED, entrySummary = "unspecified"))
        safetyCenterCtsHelper.setData(
            SOURCE_ID_5,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION, entrySummary = "information"))
        // SOURCE_ID_6 leave as an UNKNOWN dynamic entry
        safetyCenterCtsHelper.setData(
            SOURCE_ID_7,
            safetySourceCtsData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified with issue",
                withIssue = true))
        // STATIC_IN_COLLAPSIBLE_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary)
            .isEqualTo(safetyCenterResourcesContext.getStringByName("group_unknown_summary"))
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getSafetyCenterData_withAndroidLockScreenGroup_returnsCombinedTitlesAsSummaryForGroup() {
        safetyCenterCtsHelper.setConfig(ANDROID_LOCK_SCREEN_SOURCES_CONFIG)

        val initialGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(initialGroup.summary)
            .isEqualTo(safetyCenterResourcesContext.getStringByName("group_unknown_summary"))
        assertThat(initialGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)

        safetyCenterCtsHelper.setData(DYNAMIC_BAREBONE_ID, safetySourceCtsData.unspecified)
        safetyCenterCtsHelper.setData(DYNAMIC_DISABLED_ID, safetySourceCtsData.information)

        val partialGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(partialGroup.summary).isEqualTo("Unspecified title, Ok title")
        assertThat(partialGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)

        safetyCenterCtsHelper.setData(DYNAMIC_HIDDEN_ID, safetySourceCtsData.information)

        val fullGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(fullGroup.summary).isEqualTo("Unspecified title, Ok title, Ok title")
        assertThat(fullGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)

        safetyCenterCtsHelper.setData(DYNAMIC_DISABLED_ID, safetySourceCtsData.informationWithIssue)

        val informationWithIssueGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(informationWithIssueGroup.summary).isEqualTo("Ok summary")
        assertThat(informationWithIssueGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSafetyCenterData_withFlagDisabled_returnsDefaultData() {
        safetyCenterCtsHelper.setEnabled(false)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(SafetyCenterCtsData.DEFAULT)
    }

    @Test
    fun getSafetyCenterData_defaultDataWithIncorrectIntent_returnsDisabledEntries() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_INVALID_INTENT_CONFIG)

        val safetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            SafetyCenterData(
                safetyCenterCtsData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterCtsData
                            .safetyCenterEntryDefaultBuilder(SINGLE_SOURCE_ID)
                            .setPendingIntent(null)
                            .setEnabled(false)
                            .build())),
                emptyList())
        assertThat(safetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    fun getSafetyCenterData_withInvalidDefaultIntent_shouldReturnUnspecifiedSeverityLevel() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_INVALID_INTENT_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.informationWithNullIntent)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            SafetyCenterData(
                safetyCenterStatusOk,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterCtsData
                            .safetyCenterEntryOkBuilder(SINGLE_SOURCE_ID)
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                            .setPendingIntent(null)
                            .setEnabled(false)
                            .build())),
                emptyList())
        assertThat(apiSafetyCenterData).isEqualTo(expectedSafetyCenterData)
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

        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
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
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataOkReviewCriticalEntry)
    }

    @Test
    fun dismissSafetyCenterIssue_issueShowsUpAgainIfSourceStopsSendingItAtLeastOnce() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        val safetyCenterDataAfterSourceStopsSendingDismissedIssue =
            listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourceStopsSendingDismissedIssue)
            .isEqualTo(safetyCenterDataOk)

        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        val safetyCenterDataAfterSourcePushesDismissedIssueAgain =
            listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourcePushesDismissedIssueAgain)
            .isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun dismissSafetyCenterIssue_existingWithDifferentIssueType_callsListenerAndDismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, issueTypeId = "some_other_issue_type_id"))

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataOkReviewCriticalEntry)
    }

    @Test
    fun dismissSafetyCenterIssue_withDismissPendingIntent_callsDismissPendingIntentAndDismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.recommendationDismissPendingIntentIssue)
        SafetySourceReceiver.setResponse(
            Request.DismissIssue(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information))
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID))

        val safetyCenterDataAfterDismissal = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterDismissal)
            .isEqualTo(safetyCenterDataOkReviewRecommendationEntry)
        val safetyCenterDataAfterSourceHandledDismissal = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourceHandledDismissal).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun dismissSafetyCenterIssue_errorDispatchPendingIntent_doesntCallErrorListenerAndDismisses() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val recommendationDismissPendingIntentIssue =
            safetySourceCtsData.recommendationDismissPendingIntentIssue
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, recommendationDismissPendingIntentIssue)
        recommendationDismissPendingIntentIssue.issues.first().onDismissPendingIntent!!.cancel()
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID))

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener)
            .isEqualTo(safetyCenterDataOkReviewRecommendationEntry)
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_nonExisting_doesntCallListenerOrDismiss() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
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
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
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
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
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
    fun dismissSafetyCenterIssue_withEmptyMaxCountMap_doesNotResurface() {
        SafetyCenterFlags.resurfaceIssueMaxCounts = emptyMap()
        SafetyCenterFlags.resurfaceIssueDelays = mapOf(SEVERITY_LEVEL_INFORMATION to Duration.ZERO)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIssue)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataOkOneAlert)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, INFORMATION_ISSUE_ID))

        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = TIMEOUT_SHORT) {
                val hasResurfaced =
                    safetyCenterManager.getSafetyCenterDataWithPermission() != safetyCenterDataOk
                hasResurfaced
            }
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withZeroMaxCount_doesNotResurface() {
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 99L)
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to Duration.ZERO,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.informationWithIssue)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataOkOneAlert)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, INFORMATION_ISSUE_ID))

        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = TIMEOUT_SHORT) {
                val hasResurfaced =
                    safetyCenterManager.getSafetyCenterDataWithPermission() != safetyCenterDataOk
                hasResurfaced
            }
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withTwoMaxCount_resurfacesTwice() {
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 0L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 2L)
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingDeviceIssue)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataDeviceCriticalOneAlert)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))
        val apiSafetyCenterDataResurface = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterDataResurface == safetyCenterDataDeviceCriticalOneAlert)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))
        val apiSafetyCenterDataResurfaceAgain =
            safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterDataResurfaceAgain == safetyCenterDataDeviceCriticalOneAlert)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))

        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = TIMEOUT_SHORT) {
                val hasResurfaced =
                    safetyCenterManager.getSafetyCenterDataWithPermission() !=
                        safetyCenterDataOkReviewCriticalEntry
                hasResurfaced
            }
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withNonZeroMaxCountAndNonZeroDelay_resurfacesAfterDelay() {
        // We cannot rely on a listener in this test to assert on the API content at all times!
        // The listener will not receive an update when a dismissed issue resurfaces, and it will
        // not receive an update after subsequent dismissals because as far as the listener cache is
        // concerned the dismissed issue never resurfaced. This is working as intended.
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 0L)
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO)
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.recommendationWithDeviceIssue)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataDeviceRecommendationOneAlert)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID))

        val safetyCenterDataAfterDismissal = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterDismissal)
            .isEqualTo(safetyCenterDataOkReviewRecommendationEntry)
        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfacedExactly =
                safetyCenterManager.getSafetyCenterDataWithPermission() ==
                    safetyCenterDataDeviceRecommendationOneAlert
            hasResurfacedExactly
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withFlagDisabled_doesntCallListenerOrDismiss() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
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
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information))

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_errorWithDispatchingRedirectingAction() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val criticalWithRedirectingIssue = safetySourceCtsData.criticalWithRedirectingIssue
        criticalWithRedirectingIssue.issues.first().actions.first().pendingIntent.cancel()
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, criticalWithRedirectingIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("redirecting_error")))
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_errorWithDispatchingResolvingAction() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val criticalWithResolvingIssue = safetySourceCtsData.criticalWithResolvingGeneralIssue
        criticalWithResolvingIssue.issues.first().actions.first().pendingIntent.cancel()
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, criticalWithResolvingIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("resolving_action_error")))
    }

    @Test
    // This test runs the default no-op implementation of OnSafetyCenterDataChangedListener#onError
    // for code coverage purposes.
    fun executeSafetyCenterIssueAction_errorWithDispatchingOnDefaultErrorListener() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val criticalWithRedirectingIssue = safetySourceCtsData.criticalWithRedirectingIssue
        criticalWithRedirectingIssue.issues.first().actions.first().pendingIntent.cancel()
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, criticalWithRedirectingIssue)
        val fakeExecutor = FakeExecutor()
        val listener =
            object : OnSafetyCenterDataChangedListener {
                override fun onSafetyCenterDataChanged(safetyCenterData: SafetyCenterData) {}
            }
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            fakeExecutor, listener)
        fakeExecutor.getNextTask().run()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))
        fakeExecutor.getNextTask().run()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_unmarkInFlightWhenResolveActionError() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), Response.Error)

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction)
            .isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("resolving_action_error")))
    }

    @Test
    fun executeSafetyCenterIssueAction_nonExisting_doesntCallListenerOrExecute() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)
        val listener = safetyCenterCtsHelper.addListener()

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
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
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))
        listener.receiveSafetyCenterData()

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
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
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction)
            .isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("resolving_action_error")))
    }

    @Test
    fun executeSafetyCenterIssueAction_tryAgainAfterTimeout_callsListenerWithInFlightAndExecutes() {
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterErrorDetails()
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_LONG
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information))

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_withFlagDisabled_doesntCallListenerOrExecute() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()
        safetyCenterCtsHelper.setEnabled(false)

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
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
    fun executeSafetyCenterIssueAction_issueIdDoesNotMatch_throwsErrorAndDoesNotResolveIssue() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information))

        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterCtsData.issueActionId(
                    SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID + "invalid", CRITICAL_ISSUE_ACTION_ID),
                TIMEOUT_SHORT)
        }

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_actionIdDoesNotMatch_doesNotResolveIssue() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information))

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterCtsData.issueActionId(
                    SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID + "invalid"),
                TIMEOUT_SHORT)
        }

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_idsDontMatch_canStillResolve() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        val listener = safetyCenterCtsHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceCtsData.information))
        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterCtsData.issueActionId(
                    SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID + "invalid", CRITICAL_ISSUE_ACTION_ID),
                TIMEOUT_SHORT)
        }

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterCtsData.issueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction).isEqualTo(safetyCenterDataOk)
    }
    @Test
    fun executeSafetyCenterIssueAction_sourceIdsDontMatch_throwsIllegalArgumentException() {
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
        safetyCenterCtsHelper.setData(
            SOURCE_ID_2, safetySourceCtsData.criticalWithResolvingGeneralIssue)

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        val data1AfterClearing = safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(data1AfterClearing).isNull()
        val data2AfterClearing = safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(data2AfterClearing).isNull()
    }

    @Test
    fun clearAllSafetySourceDataForTests_withFlagDisabled_clearsData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(
            SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
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

    @Test
    fun lockScreenSource_withoutReplaceLockScreenIconActionFlag_doesntReplace() {
        // Must have a screen lock for the icon action to be set
        assumeTrue(ScreenLockHelper.isDeviceSecure(context))
        safetyCenterCtsHelper.setConfig(context.getLockScreenSourceConfig())
        val listener = safetyCenterCtsHelper.addListener()
        SafetyCenterFlags.replaceLockScreenIconAction = false

        safetyCenterManager.refreshSafetySourcesWithPermission(REFRESH_REASON_PAGE_OPEN)
        // Skip loading data.
        listener.receiveSafetyCenterData()

        val lockScreenSafetyCenterData = listener.receiveSafetyCenterData()
        val lockScreenEntry = lockScreenSafetyCenterData.entriesOrGroups.first().entry!!
        val entryPendingIntent = lockScreenEntry.pendingIntent!!
        val iconActionPendingIntent = lockScreenEntry.iconAction!!.pendingIntent
        // This test passes for now but will eventually start failing once we introduce the fix in
        // the Settings app. This will warn if the assumption is failed rather than fail, at which
        // point we can remove this test (and potentially even this magnificent hack).
        assumeTrue(iconActionPendingIntent == entryPendingIntent)
    }

    @Test
    fun lockScreenSource_withReplaceLockScreenIconActionFlag_replaces() {
        // Must have a screen lock for the icon action to be set
        assumeTrue(ScreenLockHelper.isDeviceSecure(context))
        safetyCenterCtsHelper.setConfig(context.getLockScreenSourceConfig())
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithPermission(REFRESH_REASON_PAGE_OPEN)
        // Skip loading data.
        listener.receiveSafetyCenterData()

        val lockScreenSafetyCenterData = listener.receiveSafetyCenterData()
        val lockScreenEntry = lockScreenSafetyCenterData.entriesOrGroups.first().entry!!
        val entryPendingIntent = lockScreenEntry.pendingIntent!!
        val iconActionPendingIntent = lockScreenEntry.iconAction!!.pendingIntent
        assertThat(iconActionPendingIntent).isNotEqualTo(entryPendingIntent)
    }

    private fun SafetyCenterData.getGroup(groupId: String): SafetyCenterEntryGroup =
        entriesOrGroups
            .first { it.entryGroup?.id == SafetyCenterCtsData.entryGroupId(groupId) }
            .entryGroup!!

    companion object {
        private val RESURFACE_DELAY = Duration.ofMillis(500)
        // Wait 1.5 times the RESURFACE_DELAY before asserting whether an issue has or has not
        // resurfaced. Use a constant additive error buffer if we increase the delay considerably.
        private val RESURFACE_TIMEOUT = RESURFACE_DELAY.multipliedBy(3).dividedBy(2)
        // Check more than once during a RESURFACE_DELAY before asserting whether an issue has or
        // has not resurfaced. Use a different check logic (focused at the expected resurface time)
        // if we increase the delay considerably.
        private val RESURFACE_CHECK = RESURFACE_DELAY.dividedBy(4)
    }
}
