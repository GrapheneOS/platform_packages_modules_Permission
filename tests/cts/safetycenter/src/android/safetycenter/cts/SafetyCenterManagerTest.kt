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
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
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
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PERIODIC
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
import android.safetycenter.cts.testing.FakeExecutor
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.preconditions.ScreenLockHelper
import com.android.safetycenter.resources.SafetyCenterResourcesContext
import com.android.safetycenter.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.Coroutines.waitForWithTimeout
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.executeSafetyCenterIssueActionWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.refreshSafetySourcesWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.reportSafetySourceErrorWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import com.android.safetycenter.testing.SafetyCenterEnabledChangedReceiver
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_ALL_OPTIONAL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_DISABLED_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_HIDDEN_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_HIDDEN_WITH_SEARCH_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_IN_STATEFUL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_IN_STATELESS_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.DYNAMIC_OTHER_PACKAGE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_ALL_OPTIONAL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.ISSUE_ONLY_IN_STATELESS_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MIXED_STATEFUL_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MIXED_STATELESS_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MULTIPLE_SOURCES_GROUP_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.MULTIPLE_SOURCES_GROUP_ID_2
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.PACKAGE_CERT_HASH_INVALID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SAMPLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_2
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_3
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_4
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_5
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_6
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_7
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.STATIC_BAREBONE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.STATIC_IN_STATEFUL_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SUMMARY_TEST_GROUP_ID
import com.android.safetycenter.testing.SafetyCenterTestData
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withAttributionTitleInIssuesIfAtLeastU
import com.android.safetycenter.testing.SafetyCenterTestData.Companion.withDismissedIssuesIfAtLeastU
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetyCenterTestListener
import com.android.safetycenter.testing.SafetySourceIntentHandler.Request
import com.android.safetycenter.testing.SafetySourceIntentHandler.Response
import com.android.safetycenter.testing.SafetySourceReceiver
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.dismissSafetyCenterIssueWithPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.executeSafetyCenterIssueActionWithPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithReceiverPermissionAndWait
import com.android.safetycenter.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithoutReceiverPermissionAndWait
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ACTION_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.CRITICAL_ISSUE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.EVENT_SOURCE_STATE_CHANGED
import com.android.safetycenter.testing.SafetySourceTestData.Companion.INFORMATION_ISSUE_ID
import com.android.safetycenter.testing.SafetySourceTestData.Companion.RECOMMENDATION_ISSUE_ID
import com.google.common.base.Preconditions.checkState
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetyCenterManager]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterResourcesContext = SafetyCenterResourcesContext.forTests(context)
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestData = SafetyCenterTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    private val safetyCenterStatusOk =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_title"),
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_summary")
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusUnknownScanning =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName("scanning_title"),
                safetyCenterResourcesContext.getStringByName("loading_summary")
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
            .setRefreshStatus(REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS)
            .build()

    private val safetyCenterStatusOkOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName("overall_severity_level_ok_title"),
                safetyCenterTestData.getAlertString(1)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusOkReviewOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_title"
                ),
                safetyCenterTestData.getAlertString(1)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusOkReview =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_title"
                ),
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_summary"
                )
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val safetyCenterStatusGeneralRecommendationOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_safety_recommendation_title"
                ),
                safetyCenterTestData.getAlertString(1)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val safetyCenterStatusAccountRecommendationOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_account_recommendation_title"
                ),
                safetyCenterTestData.getAlertString(1)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val safetyCenterStatusDeviceRecommendationOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_device_recommendation_title"
                ),
                safetyCenterTestData.getAlertString(1)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val safetyCenterStatusGeneralCriticalOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_safety_warning_title"
                ),
                safetyCenterTestData.getAlertString(1)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusGeneralCriticalTwoAlerts =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_safety_warning_title"
                ),
                safetyCenterTestData.getAlertString(2)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusAccountCriticalOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_account_warning_title"
                ),
                safetyCenterTestData.getAlertString(1)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusAccountCriticalTwoAlerts =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_account_warning_title"
                ),
                safetyCenterTestData.getAlertString(2)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusDeviceCriticalOneAlert =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_device_warning_title"
                ),
                safetyCenterTestData.getAlertString(1)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterStatusDeviceCriticalTwoAlerts =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_device_warning_title"
                ),
                safetyCenterTestData.getAlertString(2)
            )
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    private val safetyCenterEntryOrGroupRecommendation =
        SafetyCenterEntryOrGroup(
            safetyCenterTestData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID)
        )

    private val safetyCenterEntryOrGroupCritical =
        SafetyCenterEntryOrGroup(safetyCenterTestData.safetyCenterEntryCritical(SINGLE_SOURCE_ID))

    private val safetyCenterEntryGroupMixedFromComplexConfig =
        SafetyCenterEntryOrGroup(
            SafetyCenterEntryGroup.Builder(MIXED_STATEFUL_GROUP_ID, "OK")
                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                .setSummary(safetyCenterResourcesContext.getStringByName("group_unknown_summary"))
                .setEntries(
                    listOf(
                        safetyCenterTestData.safetyCenterEntryDefault(DYNAMIC_IN_STATEFUL_ID),
                        SafetyCenterEntry.Builder(
                                SafetyCenterTestData.entryId(STATIC_IN_STATEFUL_ID),
                                "OK"
                            )
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                            .setSummary("OK")
                            .setPendingIntent(
                                safetySourceTestData.testActivityRedirectPendingIntent
                            )
                            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)
                            .build()
                    )
                )
                .build()
        )

    private val safetyCenterStaticEntryGroupFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("OK")
                    .setPendingIntent(safetySourceTestData.testActivityRedirectPendingIntent)
                    .build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(safetySourceTestData.testActivityRedirectPendingIntent)
                    .build()
            )
        )

    private val safetyCenterStaticEntryGroupMixedFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(safetySourceTestData.testActivityRedirectPendingIntent)
                    .build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(safetySourceTestData.testActivityRedirectPendingIntent)
                    .build()
            )
        )

    private val safetyCenterStaticEntryGroupMixedUpdatedFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("Unspecified title")
                    .setSummary("Unspecified summary")
                    .setPendingIntent(safetySourceTestData.testActivityRedirectPendingIntent)
                    .build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(safetySourceTestData.testActivityRedirectPendingIntent)
                    .build()
            )
        )

    private val safetyCenterDataFromConfigScanning =
        SafetyCenterData(
            safetyCenterStatusUnknownScanning,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterTestData.safetyCenterEntryDefault(SINGLE_SOURCE_ID)
                )
            ),
            emptyList()
        )

    private val safetyCenterDataFromConfig =
        SafetyCenterData(
            safetyCenterTestData.safetyCenterStatusUnknown,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterTestData.safetyCenterEntryDefault(SINGLE_SOURCE_ID)
                )
            ),
            emptyList()
        )

    private val safetyCenterDataUnspecified =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterTestData.safetyCenterEntryUnspecified(SINGLE_SOURCE_ID)
                )
            ),
            emptyList()
        )

    private val safetyCenterDataOk =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(safetyCenterTestData.safetyCenterEntryOk(SINGLE_SOURCE_ID))
            ),
            emptyList()
        )

    private val safetyCenterDataOkWithIconAction =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterTestData
                        .safetyCenterEntryOkBuilder(SINGLE_SOURCE_ID)
                        .setIconAction(
                            ICON_ACTION_TYPE_INFO,
                            safetySourceTestData.testActivityRedirectPendingIntent
                        )
                        .build()
                )
            ),
            emptyList()
        )

    private val safetyCenterDataUnknownReviewError =
        SafetyCenterData(
            safetyCenterTestData.safetyCenterStatusUnknown,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterTestData.safetyCenterEntryError(SINGLE_SOURCE_ID)
                )
            ),
            emptyList()
        )

    private val safetyCenterDataOkOneAlert =
        SafetyCenterData(
            safetyCenterStatusOkOneAlert,
            listOf(safetyCenterTestData.safetyCenterIssueInformation(SINGLE_SOURCE_ID)),
            listOf(
                SafetyCenterEntryOrGroup(safetyCenterTestData.safetyCenterEntryOk(SINGLE_SOURCE_ID))
            ),
            emptyList()
        )

    private val safetyCenterDataOkReviewCriticalEntry =
        SafetyCenterData(
            safetyCenterStatusOkReview,
            emptyList(),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList()
        )

    private val safetyCenterDataOkReviewRecommendationEntry =
        SafetyCenterData(
            safetyCenterStatusOkReview,
            emptyList(),
            listOf(safetyCenterEntryOrGroupRecommendation),
            emptyList()
        )

    private val safetyCenterDataOkReviewOneAlert =
        SafetyCenterData(
            safetyCenterStatusOkReviewOneAlert,
            listOf(safetyCenterTestData.safetyCenterIssueInformation(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList()
        )

    private val safetyCenterDataGeneralRecommendationOneAlert =
        SafetyCenterData(
            safetyCenterStatusGeneralRecommendationOneAlert,
            listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterTestData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID)
                )
            ),
            emptyList()
        )

    private val safetyCenterDataAccountRecommendationOneAlert =
        SafetyCenterData(
            safetyCenterStatusAccountRecommendationOneAlert,
            listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterTestData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID)
                )
            ),
            emptyList()
        )

    private val safetyCenterDataDeviceRecommendationOneAlert =
        SafetyCenterData(
            safetyCenterStatusDeviceRecommendationOneAlert,
            listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID)),
            listOf(
                SafetyCenterEntryOrGroup(
                    safetyCenterTestData.safetyCenterEntryRecommendation(SINGLE_SOURCE_ID)
                )
            ),
            emptyList()
        )

    private val safetyCenterDataGeneralCriticalOneAlert =
        SafetyCenterData(
            safetyCenterStatusGeneralCriticalOneAlert,
            listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList()
        )

    private val safetyCenterDataAccountCriticalOneAlert =
        SafetyCenterData(
            safetyCenterStatusAccountCriticalOneAlert,
            listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList()
        )

    private val safetyCenterDataDeviceCriticalOneAlert =
        SafetyCenterData(
            safetyCenterStatusDeviceCriticalOneAlert,
            listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID)),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList()
        )

    private val safetyCenterDataCriticalOneAlertInFlight =
        SafetyCenterData(
            safetyCenterStatusGeneralCriticalOneAlert,
            listOf(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SINGLE_SOURCE_ID,
                    isActionInFlight = true
                )
            ),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList()
        )

    private val safetyCenterDataFromComplexConfig =
        SafetyCenterData(
            safetyCenterTestData.safetyCenterStatusUnknown,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntryGroup.Builder(DYNAMIC_GROUP_ID, "OK")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                        .setSummary(
                            safetyCenterResourcesContext.getStringByName("group_unknown_summary")
                        )
                        .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                        .setEntries(
                            listOf(
                                safetyCenterTestData.safetyCenterEntryDefault(DYNAMIC_BAREBONE_ID),
                                safetyCenterTestData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                                    .setEnabled(false)
                                    .build(),
                                safetyCenterTestData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_DISABLED_ID)
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build(),
                                safetyCenterTestData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_OTHER_PACKAGE_ID)
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build()
                            )
                        )
                        .build()
                ),
                safetyCenterEntryGroupMixedFromComplexConfig
            ),
            listOf(
                safetyCenterStaticEntryGroupFromComplexConfig,
                safetyCenterStaticEntryGroupMixedFromComplexConfig
            )
        )

    private val safetyCenterDataFromComplexConfigUpdated =
        SafetyCenterData(
            safetyCenterTestData.safetyCenterStatusCritical(6),
            listOf(
                safetyCenterTestData.safetyCenterIssueCritical(
                    DYNAMIC_BAREBONE_ID,
                    groupId = DYNAMIC_GROUP_ID
                ),
                safetyCenterTestData.safetyCenterIssueCritical(
                    ISSUE_ONLY_BAREBONE_ID,
                    groupId = ISSUE_ONLY_GROUP_ID
                ),
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    DYNAMIC_DISABLED_ID,
                    groupId = DYNAMIC_GROUP_ID
                ),
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    ISSUE_ONLY_ALL_OPTIONAL_ID,
                    groupId = ISSUE_ONLY_GROUP_ID
                ),
                safetyCenterTestData.safetyCenterIssueInformation(
                    DYNAMIC_IN_STATELESS_ID,
                    groupId = MIXED_STATELESS_GROUP_ID
                ),
                safetyCenterTestData.safetyCenterIssueInformation(
                    ISSUE_ONLY_IN_STATELESS_ID,
                    groupId = MIXED_STATELESS_GROUP_ID
                )
            ),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntryGroup.Builder(DYNAMIC_GROUP_ID, "OK")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                        .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                        .setSummary("Critical summary")
                        .setEntries(
                            listOf(
                                safetyCenterTestData.safetyCenterEntryCritical(DYNAMIC_BAREBONE_ID),
                                safetyCenterTestData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                                    .setEnabled(false)
                                    .build(),
                                safetyCenterTestData.safetyCenterEntryRecommendation(
                                    DYNAMIC_DISABLED_ID
                                ),
                                safetyCenterTestData.safetyCenterEntryUnspecified(
                                    DYNAMIC_HIDDEN_ID,
                                    pendingIntent = null
                                ),
                                safetyCenterTestData.safetyCenterEntryOk(
                                    DYNAMIC_HIDDEN_WITH_SEARCH_ID
                                ),
                                safetyCenterTestData
                                    .safetyCenterEntryDefaultBuilder(DYNAMIC_OTHER_PACKAGE_ID)
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build()
                            )
                        )
                        .build()
                ),
                safetyCenterEntryGroupMixedFromComplexConfig
            ),
            listOf(
                safetyCenterStaticEntryGroupFromComplexConfig,
                safetyCenterStaticEntryGroupMixedUpdatedFromComplexConfig
            )
        )

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
        safetyCenterTestHelper.setup()
    }

    @After
    fun clearDataAfterTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterTestHelper.reset()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagEnabled_returnsTrue() {
        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isTrue()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagDisabled_returnsFalse() {
        safetyCenterTestHelper.setEnabled(false)

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.isSafetyCenterEnabled }
    }

    @Test
    fun setSafetySourceData_validId_setsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataToSet = safetySourceTestData.unspecified
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_twice_replacesValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)

        val dataToSet = safetySourceTestData.criticalWithResolvingGeneralIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_null_clearsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_sourceInStatelessGroupUnspecified_setsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val dataToSet = safetySourceTestData.unspecified
        safetyCenterTestHelper.setData(DYNAMIC_IN_STATELESS_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_IN_STATELESS_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_unknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun setSafetySourceData_staticId_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(STATIC_BAREBONE_ID, safetySourceTestData.unspecified)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_differentPackage_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    DYNAMIC_OTHER_PACKAGE_ID,
                    safetySourceTestData.unspecified
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_wronglySignedPackage_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceWithFakeCert)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Invalid signature for package " + context.packageName)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_invalidPackageCertificate_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceWithInvalidCert)

        val thrown =
            assertFailsWith(IllegalStateException::class) {
                safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Failed to parse signing certificate: " + PACKAGE_CERT_HASH_INVALID)
    }

    @Test
    fun setSafetySourceData_sourceInStatelessGroupNotUnspecified_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    DYNAMIC_IN_STATELESS_ID,
                    safetySourceTestData.information
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Safety source: $DYNAMIC_IN_STATELESS_ID is in a stateless group but specified a " +
                    "severity level: $SEVERITY_LEVEL_INFORMATION"
            )
    }

    @Test
    fun setSafetySourceData_nullUnknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun setSafetySourceData_nullStaticId_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(STATIC_BAREBONE_ID, safetySourceData = null)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_nullDifferentPackage_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(DYNAMIC_OTHER_PACKAGE_ID, safetySourceData = null)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID
            )
    }

    @Test
    fun setSafetySourceData_issueOnlyWithStatus_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    ISSUE_ONLY_BAREBONE_ID,
                    safetySourceTestData.unspecified
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected status for issue only safety source: $ISSUE_ONLY_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_dynamicWithIssueOnly_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    DYNAMIC_BAREBONE_ID,
                    SafetySourceTestData.issuesOnly(safetySourceTestData.informationIssue)
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Missing status for dynamic safety source: $DYNAMIC_BAREBONE_ID")
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevUnspecified_setsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.severityZeroConfig)

        val dataToSet = safetySourceTestData.unspecified
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevInformation_setsValue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.severityZeroConfig)

        val dataToSet = safetySourceTestData.information
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevInformationWithIssue_throwsException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.severityZeroConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    SINGLE_SOURCE_ID,
                    safetySourceTestData.informationWithIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                            SafetySourceData.SEVERITY_LEVEL_INFORMATION
                        }, for issue in safety source: $SINGLE_SOURCE_ID"
            )
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevCritical_throwsException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.severityZeroConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    SINGLE_SOURCE_ID,
                    safetySourceTestData.criticalWithResolvingGeneralIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                            SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                        }, for safety source: $SINGLE_SOURCE_ID"
            )
    }

    @Test
    fun setSafetySourceData_withMaxSevRecommendation_met() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val dataToSet = safetySourceTestData.recommendationWithGeneralIssue
        safetyCenterTestHelper.setData(DYNAMIC_ALL_OPTIONAL_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_ALL_OPTIONAL_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMaxSevRecommendation_notMet() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    DYNAMIC_ALL_OPTIONAL_ID,
                    safetySourceTestData.criticalWithResolvingGeneralIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                            SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                        }, for safety source: $DYNAMIC_ALL_OPTIONAL_ID"
            )
    }

    @Test
    fun setSafetySourceData_issueOnlyWithMaxSevRecommendation_met() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val dataToSet =
            SafetySourceTestData.issuesOnly(safetySourceTestData.recommendationGeneralIssue)
        safetyCenterTestHelper.setData(ISSUE_ONLY_ALL_OPTIONAL_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(ISSUE_ONLY_ALL_OPTIONAL_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_issueOnlyWithMaxSevRecommendation_notMet() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    ISSUE_ONLY_ALL_OPTIONAL_ID,
                    SafetySourceTestData.issuesOnly(
                        safetySourceTestData.criticalResolvingGeneralIssue
                    )
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level: ${
                            SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                        }, for issue in safety source: $ISSUE_ONLY_ALL_OPTIONAL_ID"
            )
    }

    @Test
    fun setSafetySourceData_withEmptyCategoryAllowlists_met() {
        SafetyCenterFlags.issueCategoryAllowlists = emptyMap()
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataToSet = safetySourceTestData.recommendationWithAccountIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withMissingAllowlistForCategory_met() {
        SafetyCenterFlags.issueCategoryAllowlists =
            mapOf(
                ISSUE_CATEGORY_DEVICE to setOf(SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_GENERAL to setOf(SAMPLE_SOURCE_ID)
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataToSet = safetySourceTestData.recommendationWithAccountIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

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
                ISSUE_CATEGORY_GENERAL to setOf(SAMPLE_SOURCE_ID)
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val dataToSet = safetySourceTestData.recommendationWithAccountIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
    }

    @Test
    fun setSafetySourceData_withEmptyAllowlistForCategory_notMet() {
        SafetyCenterFlags.issueCategoryAllowlists = mapOf(ISSUE_CATEGORY_ACCOUNT to emptySet())
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    SINGLE_SOURCE_ID,
                    safetySourceTestData.recommendationWithAccountIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected issue category: $ISSUE_CATEGORY_ACCOUNT, for issue in safety source: " +
                    SINGLE_SOURCE_ID
            )
    }

    @Test
    fun setSafetySourceData_withoutSourceInAllowlistForCategory_notMet() {
        SafetyCenterFlags.issueCategoryAllowlists =
            mapOf(
                ISSUE_CATEGORY_ACCOUNT to setOf(SAMPLE_SOURCE_ID),
                ISSUE_CATEGORY_DEVICE to setOf(SINGLE_SOURCE_ID)
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterTestHelper.setData(
                    SINGLE_SOURCE_ID,
                    safetySourceTestData.recommendationWithAccountIssue
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected issue category: $ISSUE_CATEGORY_ACCOUNT, for issue in safety source: " +
                    SINGLE_SOURCE_ID
            )
    }

    @Test
    fun setSafetySourceData_withFlagDisabled_doesntSetData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceTestData.unspecified,
            EVENT_SOURCE_STATE_CHANGED
        )

        safetyCenterTestHelper.setEnabled(true)
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetySourceData(
                SINGLE_SOURCE_ID,
                safetySourceTestData.unspecified,
                EVENT_SOURCE_STATE_CHANGED
            )
        }
    }

    @Test
    fun getSafetySourceData_validId_noData_returnsNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.getSafetySourceDataWithPermission(DYNAMIC_OTHER_PACKAGE_ID)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID
            )
    }

    @Test
    fun getSafetySourceData_withFlagDisabled_returnsNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)
        safetyCenterTestHelper.setEnabled(false)

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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )

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
                    SINGLE_SOURCE_ID,
                    SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
                )
            }

        assertThat(thrown).hasMessageThat().isEqualTo("Unexpected safety source: $SINGLE_SOURCE_ID")
    }

    @Test
    fun reportSafetySourceError_staticId_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.reportSafetySourceErrorWithPermission(
                    STATIC_BAREBONE_ID,
                    SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source: $STATIC_BAREBONE_ID")
    }

    @Test
    fun reportSafetySourceError_differentPackage_throwsIllegalArgumentException() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.reportSafetySourceErrorWithPermission(
                    DYNAMIC_OTHER_PACKAGE_ID,
                    SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
                )
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected package name: ${context.packageName}, for safety source: " +
                    DYNAMIC_OTHER_PACKAGE_ID
            )
    }

    @Test
    fun reportSafetySourceError_withFlagDisabled_doesntCallListener() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun reportSafetySourceError_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.reportSafetySourceError(
                SINGLE_SOURCE_ID,
                SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
            )
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
                false,
                TIMEOUT_SHORT
            )
        }
        enabledChangedReceiver.unregister()
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverHasPermission_receiverCalled() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val receiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(false)

        assertThat(receiverValue).isFalse()

        val toggledReceiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(true)

        assertThat(toggledReceiverValue).isTrue()
    }

    @Test
    fun safetyCenterEnabledChanged_valueDoesntChange_receiverNotCalled() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                true,
                TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverDoesntHavePermission_receiverNotCalled() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithoutReceiverPermissionAndWait(
                false,
                TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverNotInConfig_receiverNotCalled() {
        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false,
                TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_sourceSendsRescanData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData)
            .isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_sourceSendsPageOpenData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_allowsRefreshingInAForegroundService() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.runInForegroundService = true
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_reasonPageOpen_noConditionsMet_noBroadcastSent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.noPageOpenConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.informationWithIssue)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                TIMEOUT_SHORT
            )
        }

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_reasonPageOpen_allowedByConfig_broadcastSent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.informationWithIssue)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.informationWithIssue)
    }

    @Test
    fun refreshSafetySources_reasonPageOpen_allowedByFlag_broadcastSent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.noPageOpenConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        SafetyCenterFlags.overrideRefreshOnPageOpenSources = setOf(SINGLE_SOURCE_ID)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.informationWithIssue)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.informationWithIssue)
    }

    @Test
    fun refreshSafetySources_reasonPageOpen_allowedByFlagLater_broadcastSentLater() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.noPageOpenConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.informationWithIssue)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                TIMEOUT_SHORT
            )
        }
        val apiSafetySourceDataBeforeSettingFlag =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        SafetyCenterFlags.overrideRefreshOnPageOpenSources = setOf(SINGLE_SOURCE_ID)
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        val apiSafetySourceDataAfterSettingFlag =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        assertThat(apiSafetySourceDataBeforeSettingFlag).isEqualTo(safetySourceTestData.information)
        assertThat(apiSafetySourceDataAfterSettingFlag)
            .isEqualTo(safetySourceTestData.informationWithIssue)
    }

    @Test
    fun refreshSafetySources_reasonPageOpen_noDataForSource_broadcastSent() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.noPageOpenConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_whenSourceClearsData_sourceSendsNullData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        SafetySourceReceiver.setResponse(Request.Rescan(SINGLE_SOURCE_ID), Response.ClearData)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_withMultipleSourcesInConfig_multipleSourcesSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        SafetySourceReceiver.apply {
            setResponse(
                Request.Rescan(SOURCE_ID_1),
                Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
            )
            setResponse(
                Request.Rescan(SOURCE_ID_3),
                Response.SetData(safetySourceTestData.information)
            )
        }

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1)
            .isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isNull()
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_withMultipleSourcesOnPageOpen_onlyUpdatesAllowedSources() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(SOURCE_ID_3, safetySourceTestData.information)
        SafetySourceReceiver.apply {
            setResponse(
                Request.Refresh(SOURCE_ID_1),
                Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_3),
                Response.SetData(safetySourceTestData.informationWithIssue)
            )
        }

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1)
            .isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isNull()
        // SOURCE_ID_3 doesn't support refresh on page open.
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_whenReceiverDoesntHavePermission_sourceDoesntSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithoutReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK,
                TIMEOUT_SHORT
            )
        }
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_whenSourceNotInConfig_sourceDoesntSendData() {
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun refreshSafetySources_sendsBroadcastId() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val lastReceivedBroadcastId =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK
            )

        assertThat(lastReceivedBroadcastId).isNotNull()
    }

    @Test
    fun refreshSafetySources_sendsDifferentBroadcastIdsOnEachMethodCall() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        val broadcastId1 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK
            )
        val broadcastId2 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK
            )

        assertThat(broadcastId1).isNotEqualTo(broadcastId2)
    }

    @Test
    fun refreshSafetySources_repliesWithWrongBroadcastId_doesntCompleteRefresh() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information, overrideBroadcastId = "invalid")
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        // Because wrong ID, refresh hasn't finished. Wait for timeout.
        listener.receiveSafetyCenterErrorDetails()
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_LONG)

        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_refreshAfterSuccessfulRefresh_completesSuccessfully() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData)
            .isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    fun refreshSafetySources_refreshAfterFailedRefresh_completesSuccessfully() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(Request.Rescan(SINGLE_SOURCE_ID), Response.Error)
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_waitForPreviousRefreshToTimeout_completesSuccessfully() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData1).isNull()
        // Wait for the ongoing refresh to timeout.
        listener.receiveSafetyCenterErrorDetails()
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_LONG)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_withoutAllowingPreviousRefreshToTimeout_completesSuccessfully() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceTestData.information)
    }

    @Test
    fun refreshSafetySources_withTrackedSourceThatTimesOut_timesOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        // SOURCE_ID_1 will timeout
        for (sourceId in listOf(SOURCE_ID_2, SOURCE_ID_3)) {
            SafetySourceReceiver.setResponse(
                Request.Rescan(sourceId),
                Response.SetData(safetySourceTestData.information)
            )
        }
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val safetyCenterErrorDetailsFromListener = listener.receiveSafetyCenterErrorDetails()
        assertThat(safetyCenterErrorDetailsFromListener)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("refresh_timeout")
                )
            )
    }

    @Test
    fun refreshSafetySources_withUntrackedSourceThatTimesOut_doesNotTimeOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        SafetyCenterFlags.untrackedSources = setOf(SOURCE_ID_1)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        // SOURCE_ID_1 will timeout
        for (sourceId in listOf(SOURCE_ID_2, SOURCE_ID_3)) {
            SafetySourceReceiver.setResponse(
                Request.Rescan(sourceId),
                Response.SetData(safetySourceTestData.information)
            )
        }
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withMultipleUntrackedSourcesThatTimeOut_doesNotTimeOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        SafetyCenterFlags.untrackedSources = setOf(SOURCE_ID_1, SOURCE_ID_2)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        // SOURCE_ID_1 and SOURCE_ID_2 will timeout
        SafetySourceReceiver.setResponse(
            Request.Rescan(SOURCE_ID_3),
            Response.SetData(safetySourceTestData.information)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withEmptyUntrackedSourceConfigAndSourceThatTimesOut_timesOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        // SINGLE_SOURCE_ID will timeout
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val safetyCenterErrorDetailsFromListener = listener.receiveSafetyCenterErrorDetails()
        assertThat(safetyCenterErrorDetailsFromListener)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("refresh_timeout")
                )
            )
    }

    @Test
    fun refreshSafetySources_withTrackedSourceThatHasNoReceiver_doesNotTimeOut() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceOtherPackageConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithPermission(REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_withShowEntriesOnTimeout_marksSafetySourceAsError() {
        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_SHORT)
        SafetyCenterFlags.showErrorEntriesOnTimeout = true
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterData()

        SafetyCenterFlags.setAllRefreshTimeoutsTo(TIMEOUT_LONG)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val safetyCenterDataWhenTryingAgain = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataWhenTryingAgain).isEqualTo(safetyCenterDataFromConfigScanning)
        val safetyCenterDataWhenFinishingRefresh = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataWhenFinishingRefresh).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_notifiesUiDuringRescan() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setEnabled(false)

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                TIMEOUT_SHORT
            )
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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        // All three sources have data
        SafetySourceReceiver.apply {
            setResponse(
                Request.Refresh(SOURCE_ID_1),
                Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_2),
                Response.SetData(safetySourceTestData.information)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_3),
                Response.SetData(safetySourceTestData.information)
            )
        }
        // But sources 1 and 3 should not be refreshed in background
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SOURCE_ID_1, SOURCE_ID_3)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(REFRESH_REASON_OTHER)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1).isNull()
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceTestData.information)
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isNull()
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_noBackgroundRefreshSourceSendsData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SINGLE_SOURCE_ID)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonButtonClicked_noBackgroundRefreshSourceSendsData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Rescan(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SINGLE_SOURCE_ID)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun refreshSafetySources_withRefreshReasonPeriodic_noBackgroundRefreshSourceDoesNotSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        SafetyCenterFlags.backgroundRefreshDeniedSources = setOf(SINGLE_SOURCE_ID)

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PERIODIC,
                TIMEOUT_SHORT
            )
        }

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun refreshSafetySources_withRefreshReasonPeriodic_backgroundRefreshSourceSendsData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PERIODIC
        )

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isEqualTo(safetySourceTestData.criticalWithResolvingGeneralIssue)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun refreshSafetySources_withSafetySourceIds_onlySpecifiedSourcesSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        SafetySourceReceiver.apply {
            setResponse(
                Request.Refresh(SOURCE_ID_1),
                Response.SetData(safetySourceTestData.information)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_2),
                Response.SetData(safetySourceTestData.information)
            )
            setResponse(
                Request.Refresh(SOURCE_ID_3),
                Response.SetData(safetySourceTestData.information)
            )
        }

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN,
            safetySourceIds = listOf(SOURCE_ID_1, SOURCE_ID_2)
        )

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceTestData.information)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceTestData.information)
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isNull()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun refreshSafetySources_withEmptySafetySourceIds_noSourcesSendData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        SafetySourceReceiver.setResponse(
            Request.Refresh(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.criticalWithResolvingGeneralIssue)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                TIMEOUT_SHORT,
                emptyList()
            )
        }

        val sourceData = safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(sourceData).isNull()
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun refreshSafetySources_versionLessThanU_throwsUnsupportedOperationException() {
        // TODO(b/258228790): Remove after U is no longer in pre-release
        assumeFalse(Build.VERSION.CODENAME == "UpsideDownCake")
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)

        val exception =
            assertFailsWith(UnsupportedOperationException::class) {
                safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                    REFRESH_REASON_PAGE_OPEN,
                    safetySourceIds = listOf(SOURCE_ID_1, SOURCE_ID_3)
                )
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Method not supported for versions lower than UPSIDE_DOWN_CAKE")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun refreshSafetySources_withSafetySourceIds_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_PAGE_OPEN, listOf())
        }
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
        safetyCenterTestHelper.setEnabled(false)

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNotNull()
    }

    @Test
    fun getSafetyCenterConfig_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterConfig }
    }

    @Test
    fun getSafetyCenterData_withoutDataProvided_returnsDataFromConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun getSafetyCenterData_withComplexConfigWithoutDataProvided_returnsDataFromConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    fun getSafetyCenterData_withOnlyHiddenSourcesWithoutDataProvided_returnsNoGroups() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.hiddenOnlyConfig)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData)
            .isEqualTo(
                SafetyCenterData(safetyCenterStatusOk, emptyList(), emptyList(), emptyList())
            )
    }

    @Test
    fun getSafetyCenterData_withSomeDataProvided_returnsDataProvided() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.unspecified)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataUnspecified)
    }

    @Test
    fun getSafetyCenterData_withIconAction_returnsDataWithIconAction() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.informationWithIconAction
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkWithIconAction)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_attributionTitleProvidedBySource_returnsIssueWithAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.informationWithIssueWithAttributionTitle
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            safetyCenterDataOkOneAlert.withAttributionTitleInIssuesIfAtLeastU("Attribution Title")
        assertThat(apiSafetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_attributionTitleNotProvided_returnsGroupTitleAsAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            safetyCenterDataOkOneAlert.withAttributionTitleInIssuesIfAtLeastU("OK")
        assertThat(apiSafetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_attributionNotSetAndGroupTitleNull_returnsNullAttributionTitle() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceNoGroupTitleConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.recommendationGeneralIssue)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            SafetyCenterData(
                safetyCenterStatusGeneralRecommendationOneAlert,
                listOf(
                    safetyCenterTestData.safetyCenterIssueRecommendation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID,
                        attributionTitle = null
                    )
                ),
                emptyList(),
                emptyList()
            )
        assertThat(apiSafetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    @SdkSuppress(maxSdkVersion = TIRAMISU)
    fun getSafetyCenterData_attributionNotSetBySourceOnTiramisu_returnsNullAttributionTitle() {
        // TODO(b/258228790): Remove after U is no longer in pre-release
        assumeFalse(Build.VERSION.CODENAME == "UpsideDownCake")
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkOneAlert)
    }

    @Test
    fun getSafetyCenterData_withUpdatedData_returnsUpdatedData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val previousApiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isNotEqualTo(previousApiSafetyCenterData)
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_withInformationIssue_returnsOverallStatusOkWithOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalStatusAndInfoIssue_returnsOverallStatusOkReviewOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithInformationIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataOkReviewOneAlert)
    }

    @Test
    fun getSafetyCenterData_withRecommendationGeneralIssue_returnsGeneralRecommendationOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithGeneralIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataGeneralRecommendationOneAlert)
    }

    @Test
    fun getSafetyCenterData_withRecommendationAccountIssue_returnsAccountRecommendationOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataAccountRecommendationOneAlert)
    }

    @Test
    fun getSafetyCenterData_withRecommendationDeviceIssue_returnsDeviceRecommendationOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithDeviceIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataDeviceRecommendationOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalGeneralIssue_returnsOverallStatusGeneralCriticalOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalAccountIssue_returnsOverallStatusAccountCriticalOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingAccountIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataAccountCriticalOneAlert)
    }

    @Test
    fun getSafetyCenterData_withCriticalDeviceIssue_returnsOverallStatusDeviceCriticalOneAlert() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataDeviceCriticalOneAlert)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_withRecommendationDataIssue_returnsDataRecommendationStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultRecommendationIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DATA)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_data_recommendation_title",
                    OVERALL_SEVERITY_LEVEL_RECOMMENDATION
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_withCriticalDataIssue_returnsDataCriticalStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultCriticalResolvingIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DATA)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_critical_data_warning_title",
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_withRecommendationPasswordsIssue_returnsDataRecommendationStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultRecommendationIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PASSWORDS)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_passwords_recommendation_title",
                    OVERALL_SEVERITY_LEVEL_RECOMMENDATION
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_withCriticalPasswordsIssue_returnsDataCriticalStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultCriticalResolvingIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PASSWORDS)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_critical_passwords_warning_title",
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_withRecommendationPersonalIssue_returnsDataRecommendationStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultRecommendationIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PERSONAL_SAFETY)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_personal_recommendation_title",
                    OVERALL_SEVERITY_LEVEL_RECOMMENDATION
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_withCriticalPersonalIssue_returnsDataCriticalStatus() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.issueOnlySourceConfig)
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData
                    .defaultCriticalResolvingIssueBuilder()
                    .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_PERSONAL_SAFETY)
                    .build()
            )
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus)
            .isEqualTo(
                safetyCenterTestData.safetyCenterStatusOneAlert(
                    "overall_severity_level_critical_personal_warning_title",
                    OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
                )
            )
    }

    @Test
    fun getSafetyCenterData_singleSourceIssues_returnsOverallStatusBasedOnHigherSeverityIssue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingDeviceIssueAndRecommendationIssue
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusDeviceCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_multipleSourcesIssues_returnsOverallStatusBasedOnHigherSeverityIssue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.recommendationWithAccountIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusDeviceCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_multipleIssues_returnsOverallStatusBasedOnConfigOrdering() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusGeneralCriticalTwoAlerts)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_duplicateIssuesOfSameSeverities_issueOfFirstSourceInConfigShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_differentDuplicationId_bothIssuesShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("different")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                ),
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_differentDuplicationGroup_bothIssuesShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                ),
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_6,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_threeDuplicateIssues_onlyOneIssueShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_4,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_duplicateIssuesOfDifferentSeverities_moreSevereIssueShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_multipleDuplicationsOfIssues_correctlyDeduplicated() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("A")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("A")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_2
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("B")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("B")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("A")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("B")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_3
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("B")
            )
        )

        val apiSafetyCenterIssues = safetyCenterManager.getSafetyCenterDataWithPermission().issues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                ),
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    SOURCE_ID_3,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                ),
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    SOURCE_ID_4,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
            .inOrder()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_duplicateIssuesBothDismissed_topOneShownAsDismissed() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_5, RECOMMENDATION_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterIssues = apiSafetyCenterData.issues
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterIssues).isEmpty()
        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_duplicateIssuesLowerSeverityOneDismissed_topOneShown() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_5, RECOMMENDATION_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterIssues = apiSafetyCenterData.issues
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
        assertThat(apiSafetyCenterDismissedIssues).isEmpty()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_duplicateIssuesHigherSeverityOneDismissed_topOneShownAsDismissed() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterIssues = apiSafetyCenterData.issues
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterIssues).isEmpty()
        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_dupIssuesLowerPrioritySameSeverityOneDismissed_topShownAsDismissed() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_5, CRITICAL_ISSUE_ID)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterIssues = apiSafetyCenterData.issues
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterIssues).isEmpty()
        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_dupIssuesTopOneDismissedThenDisappears_bottomOneReemergesTimely() {
        SafetyCenterFlags.resurfaceIssueMaxCounts = mapOf(SEVERITY_LEVEL_CRITICAL_WARNING to 99L)
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(SEVERITY_LEVEL_CRITICAL_WARNING to RESURFACE_DELAY)
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )
        safetyCenterManager.getSafetyCenterDataWithPermission() // data used, 2nd issue dismissed
        safetyCenterTestHelper.setData(SOURCE_ID_1, SafetySourceTestData.issuesOnly())

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfaced =
                safetyCenterManager
                    .getSafetyCenterDataWithPermission()
                    .issues
                    .contains(
                        safetyCenterTestData.safetyCenterIssueCritical(
                            SOURCE_ID_5,
                            groupId = MULTIPLE_SOURCES_GROUP_ID_2
                        )
                    )
            hasResurfaced
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_dupsOfDiffSeveritiesTopOneDismissedThenGone_bottomOneReemergesTimely() {
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 0L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO
            )
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )
        safetyCenterManager.getSafetyCenterDataWithPermission() // data used, 2nd issue dismissed
        safetyCenterTestHelper.setData(SOURCE_ID_1, SafetySourceTestData.issuesOnly())

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueRecommendation(
                    SOURCE_ID_5,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_2
                )
            )
        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfaced =
                safetyCenterManager
                    .getSafetyCenterDataWithPermission()
                    .issues
                    .contains(
                        safetyCenterTestData.safetyCenterIssueRecommendation(
                            SOURCE_ID_5,
                            groupId = MULTIPLE_SOURCES_GROUP_ID_2
                        )
                    )
            hasResurfaced
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_duplicateIssuesLowerOneResurfaces_lowerOneStillFilteredOut() {
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 99L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to RESURFACE_DELAY.multipliedBy(100)
            )
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )

        // data used, 2nd issue dismissed
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
                val hasResurfaced =
                    safetyCenterManager
                        .getSafetyCenterDataWithPermission()
                        .issues
                        .contains(
                            safetyCenterTestData.safetyCenterIssueRecommendation(
                                SOURCE_ID_5,
                                groupId = MULTIPLE_SOURCES_GROUP_ID_2
                            )
                        )
                hasResurfaced
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun getSafetyCenterData_duplicateIssuesTopOneResurfaces_topOneShown() {
        SafetyCenterFlags.resurfaceIssueMaxCounts =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to 0L,
                SEVERITY_LEVEL_RECOMMENDATION to 99L,
                SEVERITY_LEVEL_CRITICAL_WARNING to 99L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY.multipliedBy(100),
                SEVERITY_LEVEL_CRITICAL_WARNING to RESURFACE_DELAY
            )
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.multipleSourcesWithDeduplicationInfoConfig
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.criticalIssueWithDeduplicationId("same")
            )
        )
        // Belongs to DEDUPLICATION_GROUP_1
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            SafetySourceTestData.issuesOnly(
                safetySourceTestData.recommendationIssueWithDeduplicationId("same")
            )
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SOURCE_ID_1, CRITICAL_ISSUE_ID)
        )

        // data used, 2nd issue dismissed
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        val apiSafetyCenterDismissedIssues = apiSafetyCenterData.dismissedIssues

        assertThat(apiSafetyCenterDismissedIssues)
            .containsExactly(
                safetyCenterTestData.safetyCenterIssueCritical(
                    SOURCE_ID_1,
                    groupId = MULTIPLE_SOURCES_GROUP_ID_1
                )
            )
        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfaced =
                safetyCenterManager
                    .getSafetyCenterDataWithPermission()
                    .issues
                    .contains(
                        safetyCenterTestData.safetyCenterIssueCritical(
                            SOURCE_ID_1,
                            groupId = MULTIPLE_SOURCES_GROUP_ID_1
                        )
                    )
            hasResurfaced
        }
    }

    @Test
    fun getSafetyCenterData_criticalDeviceIssues_returnsOverallStatusBasedOnAddIssueCallOrder() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData
                .defaultCriticalDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultCriticalResolvingIssueBuilder("critical issue num 1")
                        .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT)
                        .build()
                )
                .addIssue(
                    safetySourceTestData
                        .defaultCriticalResolvingIssueBuilder("critical issue num 2")
                        .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
                        .build()
                )
                .build()
        )

        val apiSafetyCenterStatus = safetyCenterManager.getSafetyCenterDataWithPermission().status

        assertThat(apiSafetyCenterStatus).isEqualTo(safetyCenterStatusAccountCriticalTwoAlerts)
    }

    @Test
    fun getSafetyCenterData_withComplexConfigWithSomeDataProvided_returnsDataProvided() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.complexConfig)
        safetyCenterTestHelper.setData(
            DYNAMIC_BAREBONE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setData(
            DYNAMIC_DISABLED_ID,
            safetySourceTestData.recommendationWithGeneralIssue
        )
        safetyCenterTestHelper.setData(DYNAMIC_HIDDEN_ID, safetySourceTestData.unspecified)
        safetyCenterTestHelper.setData(
            DYNAMIC_HIDDEN_WITH_SEARCH_ID,
            safetySourceTestData.information
        )
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_BAREBONE_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.criticalResolvingGeneralIssue)
        )
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.recommendationGeneralIssue)
        )
        safetyCenterTestHelper.setData(
            DYNAMIC_IN_STATELESS_ID,
            safetySourceTestData.unspecifiedWithIssue
        )
        safetyCenterTestHelper.setData(
            ISSUE_ONLY_IN_STATELESS_ID,
            SafetySourceTestData.issuesOnly(safetySourceTestData.informationIssue)
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfigUpdated)
    }

    @Test
    fun getSafetyCenterData_withCriticalEntriesAsMax_usesCriticalSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                entrySummary = "recommendation"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_CRITICAL_WARNING,
                entrySummary = "critical 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_CRITICAL_WARNING,
                entrySummary = "critical 2"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("critical 1")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun getSafetyCenterData_withRecommendationEntriesAsMax_usesRecommendationSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                entrySummary = "recommendation 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_RECOMMENDATION,
                entrySummary = "recommendation 2"
            )
        )
        // SOURCE_ID_7 leave as an UNKNOWN dynamic entry
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("recommendation 1")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
    }

    @Test
    fun getSafetyCenterData_withInformationWithIssueEntriesAsMax_usesInformationSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue 1",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue 2",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 3"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("information with issue 1")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSafetyCenterData_withInformationEntriesAsMax_usesDefaultSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 3"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information without issues 3"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 4"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("OK")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSafetyCenterData_withUnspecifiedEntriesAsMax_usesDefaultSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 1"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 2"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 3"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 4"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 6"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified 7"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo("OK")
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
    }

    @Test
    fun getSafetyCenterData_withErrorEntries_usesPluralErrorSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_2,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        // SOURCE_ID_5 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_6,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information"
            )
        )
        // SOURCE_ID_7 leave as an UNKNOWN dynamic entry
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo(safetyCenterTestData.getRefreshErrorString(2))
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getSafetyCenterData_withErrorEntry_usesSingularErrorSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SOURCE_ID_1,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        // SOURCE_ID_2 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        // SOURCE_ID_5 leave as an UNKNOWN dynamic entry
        // SOURCE_ID_6 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information"
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary).isEqualTo(safetyCenterTestData.getRefreshErrorString(1))
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getSafetyCenterData_withUnknownEntries_usesUnknownSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.summaryTestConfig)
        // SOURCE_ID_1 leave as an UNKNOWN dynamic entry
        // SOURCE_ID_2 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_3,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information with issue",
                withIssue = true
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_4,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified"
            )
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_5,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_INFORMATION,
                entrySummary = "information"
            )
        )
        // SOURCE_ID_6 leave as an UNKNOWN dynamic entry
        safetyCenterTestHelper.setData(
            SOURCE_ID_7,
            safetySourceTestData.buildSafetySourceDataWithSummary(
                severityLevel = SEVERITY_LEVEL_UNSPECIFIED,
                entrySummary = "unspecified with issue",
                withIssue = true
            )
        )
        // STATIC_IN_STATEFUL_ID behaves like an UNSPECIFIED dynamic entry

        val group =
            safetyCenterManager.getSafetyCenterDataWithPermission().getGroup(SUMMARY_TEST_GROUP_ID)

        assertThat(group.summary)
            .isEqualTo(safetyCenterResourcesContext.getStringByName("group_unknown_summary"))
        assertThat(group.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)
    }

    @Test
    fun getSafetyCenterData_withAndroidLockScreenGroup_returnsCombinedTitlesAsSummaryForGroup() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.androidLockScreenSourcesConfig)

        val initialGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(initialGroup.summary)
            .isEqualTo(safetyCenterResourcesContext.getStringByName("group_unknown_summary"))
        assertThat(initialGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_UNKNOWN)

        safetyCenterTestHelper.setData(DYNAMIC_BAREBONE_ID, safetySourceTestData.unspecified)
        safetyCenterTestHelper.setData(DYNAMIC_DISABLED_ID, safetySourceTestData.information)

        val partialGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(partialGroup.summary).isEqualTo("Unspecified title, Ok title")
        assertThat(partialGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)

        safetyCenterTestHelper.setData(DYNAMIC_HIDDEN_ID, safetySourceTestData.information)

        val fullGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(fullGroup.summary).isEqualTo("Unspecified title, Ok title, Ok title")
        assertThat(fullGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)

        safetyCenterTestHelper.setData(
            DYNAMIC_DISABLED_ID,
            safetySourceTestData.informationWithIssue
        )

        val informationWithIssueGroup =
            safetyCenterManager
                .getSafetyCenterDataWithPermission()
                .getGroup(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)

        assertThat(informationWithIssueGroup.summary).isEqualTo("Ok summary")
        assertThat(informationWithIssueGroup.severityLevel).isEqualTo(ENTRY_SEVERITY_LEVEL_OK)
    }

    @Test
    fun getSafetyCenterData_withFlagDisabled_returnsDefaultData() {
        safetyCenterTestHelper.setEnabled(false)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(SafetyCenterTestData.DEFAULT)
    }

    @Test
    fun getSafetyCenterData_defaultDataWithIncorrectIntent_returnsDisabledEntries() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceInvalidIntentConfig)

        val safetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            SafetyCenterData(
                safetyCenterTestData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData
                            .safetyCenterEntryDefaultBuilder(SINGLE_SOURCE_ID)
                            .setPendingIntent(null)
                            .setEnabled(false)
                            .build()
                    )
                ),
                emptyList()
            )
        assertThat(safetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    fun getSafetyCenterData_withInvalidDefaultIntent_shouldReturnUnspecifiedSeverityLevel() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceInvalidIntentConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.informationWithNullIntent
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val expectedSafetyCenterData =
            SafetyCenterData(
                safetyCenterStatusOk,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterTestData
                            .safetyCenterEntryOkBuilder(SINGLE_SOURCE_ID)
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                            .setPendingIntent(null)
                            .setEnabled(false)
                            .build()
                    )
                ),
                emptyList()
            )
        assertThat(apiSafetyCenterData).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    fun getSafetyCenterData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterData }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWithSafetyCenterDataFromConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val listener = safetyCenterTestHelper.addListener(skipInitialData = false)

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledOnSafetySourceData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataChanges() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataCleared() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataStaysNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceData = null)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataDoesntChange() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        val dataToSet = safetySourceTestData.information
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, dataToSet)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_oneShot_doesntDeadlock() {
        val listener = SafetyCenterTestListener()
        val oneShotListener =
            object : OnSafetyCenterDataChangedListener {
                override fun onSafetyCenterDataChanged(safetyCenterData: SafetyCenterData) {
                    safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(this)
                    listener.onSafetyCenterDataChanged(safetyCenterData)
                }
            }
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            oneShotListener
        )

        // Check that we don't deadlock when using a one-shot listener. This is because adding the
        // listener could call it while holding a lock; which would cause a deadlock if the listener
        // wasn't oneway.
        listener.receiveSafetyCenterData()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withFlagDisabled_listenerNotCalled() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setEnabled(false)

        val listener = SafetyCenterTestListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = SafetyCenterTestListener()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(directExecutor(), listener)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerRemovedNotCalledOnSafetySourceData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener1 = safetyCenterTestHelper.addListener()
        val listener2 = safetyCenterTestHelper.addListener()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener2)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        listener1.receiveSafetyCenterData()
        assertFailsWith(TimeoutCancellationException::class) {
            listener2.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNeverCalledAfterRemoving() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val fakeExecutor = FakeExecutor()
        val listener = SafetyCenterTestListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            fakeExecutor,
            listener
        )
        fakeExecutor.getNextTask().run()
        listener.receiveSafetyCenterData()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)

        safetyCenterTestHelper.setEnabled(true)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        // Listener is removed as a side effect of the ENABLED_CHANGED broadcast.
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = safetyCenterTestHelper.addListener()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_existing_callsListenerAndDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        val expectedSafetyCenterData =
            safetyCenterDataOkReviewCriticalEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataFromListener).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    fun dismissSafetyCenterIssue_issueShowsUpAgainIfSourceStopsSendingItAtLeastOnce() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        val safetyCenterDataAfterSourceStopsSendingDismissedIssue =
            listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourceStopsSendingDismissedIssue)
            .isEqualTo(safetyCenterDataOk)

        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        val safetyCenterDataAfterSourcePushesDismissedIssueAgain =
            listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourcePushesDismissedIssueAgain)
            .isEqualTo(safetyCenterDataGeneralCriticalOneAlert)
    }

    @Test
    fun dismissSafetyCenterIssue_existingWithDifferentIssueType_callsListenerAndDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                issueTypeId = "some_other_issue_type_id"
            )
        )

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        val expectedSafetyCenterData =
            safetyCenterDataOkReviewCriticalEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataFromListener).isEqualTo(expectedSafetyCenterData)
    }

    @Test
    fun dismissSafetyCenterIssue_withDismissPendingIntent_callsDismissPendingIntentAndDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationDismissPendingIntentIssue
        )
        SafetySourceReceiver.setResponse(
            Request.DismissIssue(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID)
        )

        val safetyCenterDataAfterDismissal = listener.receiveSafetyCenterData()
        val expectedSafetyCenterData =
            safetyCenterDataOkReviewRecommendationEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataAfterDismissal).isEqualTo(expectedSafetyCenterData)
        val safetyCenterDataAfterSourceHandledDismissal = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataAfterSourceHandledDismissal).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun dismissSafetyCenterIssue_errorDispatchPendingIntent_doesntCallErrorListenerAndDismisses() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val recommendationDismissPendingIntentIssue =
            safetySourceTestData.recommendationDismissPendingIntentIssue
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, recommendationDismissPendingIntentIssue)
        recommendationDismissPendingIntentIssue.issues.first().onDismissPendingIntent!!.cancel()
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID)
        )

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        val exectedSafetyCenterData =
            safetyCenterDataOkReviewRecommendationEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataFromListener).isEqualTo(exectedSafetyCenterData)
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterErrorDetails(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_nonExisting_doesntCallListenerOrDismiss() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, "some_unknown_id")
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_alreadyDismissed_doesntCallListenerOrDismiss() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_dismissedWithDifferentIssueType_doesntCallListenerOrDismiss() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                issueTypeId = "some_other_issue_type_id"
            )
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withEmptyMaxCountMap_doesNotResurface() {
        SafetyCenterFlags.resurfaceIssueMaxCounts = emptyMap()
        SafetyCenterFlags.resurfaceIssueDelays = mapOf(SEVERITY_LEVEL_INFORMATION to Duration.ZERO)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataOkOneAlert)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, INFORMATION_ISSUE_ID)
        )

        val expectedSafetyCenterData =
            safetyCenterDataOk.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueInformation(SINGLE_SOURCE_ID))
            )
        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = TIMEOUT_SHORT) {
                val hasResurfaced =
                    safetyCenterManager.getSafetyCenterDataWithPermission() !=
                        expectedSafetyCenterData
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
                SEVERITY_LEVEL_CRITICAL_WARNING to 99L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to Duration.ZERO,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataOkOneAlert)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, INFORMATION_ISSUE_ID)
        )

        val expectedSafetyCenterData =
            safetyCenterDataOk.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueInformation(SINGLE_SOURCE_ID))
            )
        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = TIMEOUT_SHORT) {
                val hasResurfaced =
                    safetyCenterManager.getSafetyCenterDataWithPermission() !=
                        expectedSafetyCenterData
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
                SEVERITY_LEVEL_CRITICAL_WARNING to 2L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataDeviceCriticalOneAlert)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val apiSafetyCenterDataResurface = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterDataResurface == safetyCenterDataDeviceCriticalOneAlert)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )
        val apiSafetyCenterDataResurfaceAgain =
            safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterDataResurfaceAgain == safetyCenterDataDeviceCriticalOneAlert)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

        val expectedSafetyCenterData =
            safetyCenterDataOkReviewCriticalEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueCritical(SINGLE_SOURCE_ID))
            )
        assertFailsWith(TimeoutCancellationException::class) {
            waitForWithTimeout(timeout = TIMEOUT_SHORT) {
                val hasResurfaced =
                    safetyCenterManager.getSafetyCenterDataWithPermission() !=
                        expectedSafetyCenterData

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
                SEVERITY_LEVEL_CRITICAL_WARNING to 0L
            )
        SafetyCenterFlags.resurfaceIssueDelays =
            mapOf(
                SEVERITY_LEVEL_INFORMATION to Duration.ZERO,
                SEVERITY_LEVEL_RECOMMENDATION to RESURFACE_DELAY,
                SEVERITY_LEVEL_CRITICAL_WARNING to Duration.ZERO
            )
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithDeviceIssue
        )
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()
        checkState(apiSafetyCenterData == safetyCenterDataDeviceRecommendationOneAlert)
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, RECOMMENDATION_ISSUE_ID)
        )

        val safetyCenterDataAfterDismissal = listener.receiveSafetyCenterData()
        val expectedSafetyCenterData =
            safetyCenterDataOkReviewRecommendationEntry.withDismissedIssuesIfAtLeastU(
                listOf(safetyCenterTestData.safetyCenterIssueRecommendation(SINGLE_SOURCE_ID))
            )
        assertThat(safetyCenterDataAfterDismissal).isEqualTo(expectedSafetyCenterData)
        waitForWithTimeout(timeout = RESURFACE_TIMEOUT, checkPeriod = RESURFACE_CHECK) {
            val hasResurfacedExactly =
                safetyCenterManager.getSafetyCenterDataWithPermission() ==
                    safetyCenterDataDeviceRecommendationOneAlert
            hasResurfacedExactly
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withFlagDisabled_doesntCallListenerOrDismiss() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

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
                SafetyCenterTestData.issueId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    userId = USER_NULL
                )
            )
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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_errorWithDispatchingRedirectingAction() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val criticalWithRedirectingIssue = safetySourceTestData.criticalWithRedirectingIssue
        criticalWithRedirectingIssue.issues.first().actions.first().pendingIntent.cancel()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, criticalWithRedirectingIssue)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("redirecting_error")
                )
            )
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_errorWithDispatchingResolvingAction() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val criticalWithResolvingIssue = safetySourceTestData.criticalWithResolvingGeneralIssue
        criticalWithResolvingIssue.issues.first().actions.first().pendingIntent.cancel()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, criticalWithResolvingIssue)
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error)
            .isEqualTo(
                SafetyCenterErrorDetails(
                    safetyCenterResourcesContext.getStringByName("resolving_action_error")
                )
            )
    }

    @Test
    // This test runs the default no-op implementation of OnSafetyCenterDataChangedListener#onError
    // for code coverage purposes.
    fun executeSafetyCenterIssueAction_errorWithDispatchingOnDefaultErrorListener() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        val criticalWithRedirectingIssue = safetySourceTestData.criticalWithRedirectingIssue
        criticalWithRedirectingIssue.issues.first().actions.first().pendingIntent.cancel()
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, criticalWithRedirectingIssue)
        val fakeExecutor = FakeExecutor()
        val listener =
            object : OnSafetyCenterDataChangedListener {
                override fun onSafetyCenterDataChanged(safetyCenterData: SafetyCenterData) {}
            }
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            fakeExecutor,
            listener
        )
        fakeExecutor.getNextTask().run()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )
        fakeExecutor.getNextTask().run()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_unmarkInFlightWhenResolveActionError() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), Response.Error)

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

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
                    safetyCenterResourcesContext.getStringByName("resolving_action_error")
                )
            )
    }

    @Test
    fun executeSafetyCenterIssueAction_nonExisting_doesntCallListenerOrExecute() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)
        val listener = safetyCenterTestHelper.addListener()

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID
                ),
                TIMEOUT_SHORT
            )
        }
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_alreadyInFlight_doesntCallListenerOrExecute() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )
        listener.receiveSafetyCenterData()

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID
                ),
                TIMEOUT_SHORT
            )
        }
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_sourceDoesNotRespond_timesOutAndCallsListener() {
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

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
                    safetyCenterResourcesContext.getStringByName("resolving_action_error")
                )
            )
    }

    @Test
    fun executeSafetyCenterIssueAction_tryAgainAfterTimeout_callsListenerWithInFlightAndExecutes() {
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_SHORT
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterData()
        listener.receiveSafetyCenterErrorDetails()
        SafetyCenterFlags.resolveActionTimeout = TIMEOUT_LONG
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

        val safetyCenterDataFromListenerDuringResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringResolveAction)
            .isEqualTo(safetyCenterDataCriticalOneAlertInFlight)
        val safetyCenterDataFromListenerAfterResolveAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterResolveAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_withFlagDisabled_doesntCallListenerOrExecute() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        safetyCenterTestHelper.setEnabled(false)

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID
                ),
                TIMEOUT_SHORT
            )
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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID + "invalid",
                    CRITICAL_ISSUE_ACTION_ID
                ),
                TIMEOUT_SHORT
            )
        }

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_actionIdDoesNotMatch_doesNotResolveIssue() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID + "invalid"
                ),
                TIMEOUT_SHORT
            )
        }

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_idsDontMatch_canStillResolve() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val listener = safetyCenterTestHelper.addListener()
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID + "invalid",
                    CRITICAL_ISSUE_ACTION_ID
                ),
                TIMEOUT_SHORT
            )
        }

        safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
            SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            SafetyCenterTestData.issueActionId(
                SINGLE_SOURCE_ID,
                CRITICAL_ISSUE_ID,
                CRITICAL_ISSUE_ACTION_ID
            )
        )

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
                SafetyCenterTestData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterTestData.issueActionId(
                    SOURCE_ID_1,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID
                )
            )
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_invalidUser_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermission(
                SafetyCenterTestData.issueId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    userId = USER_NULL
                ),
                SafetyCenterTestData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID,
                    userId = USER_NULL
                )
            )
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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        safetyCenterTestHelper.setData(SOURCE_ID_1, safetySourceTestData.unspecified)
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        val data1AfterClearing = safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(data1AfterClearing).isNull()
        val data2AfterClearing = safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(data2AfterClearing).isNull()
    }

    @Test
    fun clearAllSafetySourceDataForTests_withFlagDisabled_clearsData() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        safetyCenterTestHelper.setEnabled(true)
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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isEqualTo(safetyCenterTestConfigs.singleSourceConfig)
    }

    @Test
    fun setSafetyCenterConfigForTests_withFlagDisabled_doesntSetConfig() {
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
            safetyCenterTestConfigs.singleSourceConfig
        )

        safetyCenterTestHelper.setEnabled(true)
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isNotEqualTo(safetyCenterTestConfigs.singleSourceConfig)
    }

    @Test
    fun setSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetyCenterConfigForTests(
                safetyCenterTestConfigs.singleSourceConfig
            )
        }
    }

    @Test
    fun clearSafetyCenterConfigForTests_clearsConfigSetForTests_doesntSetConfigToNull() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)

        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isNotNull()
        assertThat(config).isNotEqualTo(safetyCenterTestConfigs.singleSourceConfig)
    }

    @Test
    fun clearSafetyCenterConfigForTests_withFlagDisabled_doesntClearConfig() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
        safetyCenterTestHelper.setEnabled(false)

        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        safetyCenterTestHelper.setEnabled(true)
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isEqualTo(safetyCenterTestConfigs.singleSourceConfig)
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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.settingsLockScreenSourceConfig)
        val listener = safetyCenterTestHelper.addListener()
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
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.settingsLockScreenSourceConfig)
        val listener = safetyCenterTestHelper.addListener()

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
        entriesOrGroups.first { it.entryGroup?.id == groupId }.entryGroup!!

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
