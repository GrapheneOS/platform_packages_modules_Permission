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

import android.Manifest.permission.MANAGE_SAFETY_CENTER
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.UserHandle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.FakeExecutor
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.refreshSafetySourcesWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.reportSafetySourceErrorWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.COMPLEX_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_ALL_OPTIONAL_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_DISABLED_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_GROUP_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_HIDDEN_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_HIDDEN_WITH_SEARCH_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_OTHER_PACKAGE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_ALL_OPTIONAL_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.MULTIPLE_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.NO_PAGE_OPEN_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SEVERITY_ZERO_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SOURCE_ID_3
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterCtsListener
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.SafetySourceDataKey.Reason
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.executeSafetyCenterIssueActionWithReceiverPermissionAndWait
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithReceiverPermissionAndWait
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.safetycenter.internaldata.SafetyCenterEntryGroupId
import com.android.safetycenter.internaldata.SafetyCenterEntryId
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId
import com.android.safetycenter.internaldata.SafetyCenterIssueId
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
    private val safetyCenterStatusUnknown =
        SafetyCenterStatus.Builder("Unknown", "Unknown safety status")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
            .build()
    private val safetyCenterStatusOk =
        SafetyCenterStatus.Builder("All good", "No problemo maestro")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_OK)
            .build()
    private val safetyCenterDataFromConfig =
        SafetyCenterData(
            safetyCenterStatusUnknown,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntry.Builder(safetyCenterEntryId(SINGLE_SOURCE_ID), "OK")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                        .setSummary("OK")
                        .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
                        .setSeverityUnspecifiedIconType(
                            SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
                        .build())),
            emptyList())
    private val safetyCenterDataUnspecified =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntry.Builder(
                            safetyCenterEntryId(SINGLE_SOURCE_ID), "Unspecified title")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                        .setSummary("Unspecified summary")
                        .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
                        .setEnabled(false)
                        .setSeverityUnspecifiedIconType(
                            SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
                        .build())),
            emptyList())
    private val safetyCenterEntryOk =
        SafetyCenterEntry.Builder(safetyCenterEntryId(SINGLE_SOURCE_ID), "Ok title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_OK)
            .setSummary("Ok summary")
            .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()
    private val safetyCenterDataOk =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(SafetyCenterEntryOrGroup(safetyCenterEntryOk)),
            emptyList())
    private val safetyCenterStatusCritical =
        SafetyCenterStatus.Builder("Uh-oh", "Code red")
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()
    private val safetyCenterIssueCritical =
        SafetyCenterIssue.Builder(
                safetyCenterIssueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                "Critical issue title",
                "Critical issue summary")
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            safetyCenterIssueActionId(
                                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID),
                            "Solve issue",
                            safetySourceCtsData.criticalIssueActionPendingIntent)
                        .setWillResolve(true)
                        .build()))
            .build()
    private val safetyCenterEntryOrGroupCritical =
        SafetyCenterEntryOrGroup(
            SafetyCenterEntry.Builder(safetyCenterEntryId(SINGLE_SOURCE_ID), "Critical title")
                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                .setSummary("Critical summary")
                .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
                .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
                .build())
    private val safetyCenterDataCritical =
        SafetyCenterData(
            safetyCenterStatusCritical,
            listOf(safetyCenterIssueCritical),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())
    private val safetyCenterDataCriticalInFlightAction =
        SafetyCenterData(
            safetyCenterStatusCritical,
            listOf(
                SafetyCenterIssue.Builder(safetyCenterIssueCritical)
                    .setActions(
                        listOf(
                            SafetyCenterIssue.Action.Builder(
                                    safetyCenterIssueActionId(
                                        SINGLE_SOURCE_ID,
                                        CRITICAL_ISSUE_ID,
                                        CRITICAL_ISSUE_ACTION_ID),
                                    "Solve issue",
                                    safetySourceCtsData.criticalIssueActionPendingIntent)
                                .setWillResolve(true)
                                .setIsInFlight(true)
                                .build()))
                    .build()),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())
    private val safetyCenterDataCriticalWithoutIssue =
        SafetyCenterData(
            safetyCenterStatusCritical,
            emptyList(),
            listOf(safetyCenterEntryOrGroupCritical),
            emptyList())
    private val stubPendingIntent =
        PendingIntent.getActivity(
            context, 0 /* requestCode */, Intent("Stub"), PendingIntent.FLAG_IMMUTABLE)
    private val safetyCenterEntryGeneric =
        SafetyCenterEntry.Builder("ID", "OK")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
            .setSummary("OK")
            .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()
    private val safetyCenterStaticEntryGroupFromComplexConfig =
        SafetyCenterStaticEntryGroup(
            "OK",
            listOf(
                SafetyCenterStaticEntry.Builder("OK").setPendingIntent(stubPendingIntent).build(),
                SafetyCenterStaticEntry.Builder("OK")
                    .setSummary("OK")
                    .setPendingIntent(stubPendingIntent)
                    .build()))
    private val safetyCenterDataFromComplexConfig =
        SafetyCenterData(
            safetyCenterStatusUnknown,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntryGroup.Builder(safetyCenterEntryGroupId(DYNAMIC_GROUP_ID), "OK")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                        .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                        .setSummary("OK")
                        .setEntries(
                            listOf(
                                SafetyCenterEntry.Builder(safetyCenterEntryGeneric)
                                    .setId(safetyCenterEntryId(DYNAMIC_BAREBONE_ID))
                                    .build(),
                                SafetyCenterEntry.Builder(safetyCenterEntryGeneric)
                                    .setId(safetyCenterEntryId(DYNAMIC_ALL_OPTIONAL_ID))
                                    .setEnabled(false)
                                    .build(),
                                SafetyCenterEntry.Builder(safetyCenterEntryGeneric)
                                    .setId(safetyCenterEntryId(DYNAMIC_DISABLED_ID))
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build(),
                                SafetyCenterEntry.Builder(safetyCenterEntryGeneric)
                                    .setId(safetyCenterEntryId(DYNAMIC_OTHER_PACKAGE_ID))
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build()))
                        .build())),
            listOf(safetyCenterStaticEntryGroupFromComplexConfig))
    private val safetyCenterDataFromComplexConfigUpdated =
        SafetyCenterData(
            safetyCenterStatusOk,
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntryGroup.Builder(safetyCenterEntryGroupId(DYNAMIC_GROUP_ID), "OK")
                        .setSeverityLevel(ENTRY_SEVERITY_LEVEL_OK)
                        .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
                        .setSummary("OK")
                        .setEntries(
                            listOf(
                                SafetyCenterEntry.Builder(safetyCenterEntryGeneric)
                                    .setId(safetyCenterEntryId(DYNAMIC_BAREBONE_ID))
                                    .build(),
                                SafetyCenterEntry.Builder(safetyCenterEntryGeneric)
                                    .setId(safetyCenterEntryId(DYNAMIC_ALL_OPTIONAL_ID))
                                    .setEnabled(false)
                                    .build(),
                                SafetyCenterEntry.Builder(safetyCenterEntryOk)
                                    .setId(safetyCenterEntryId(DYNAMIC_DISABLED_ID))
                                    .build(),
                                SafetyCenterEntry.Builder(
                                        safetyCenterEntryId(DYNAMIC_HIDDEN_ID), "Unspecified title")
                                    .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                                    .setSummary("Unspecified summary")
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .setSeverityUnspecifiedIconType(
                                        SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
                                    .build(),
                                SafetyCenterEntry.Builder(safetyCenterEntryOk)
                                    .setId(safetyCenterEntryId(DYNAMIC_HIDDEN_WITH_SEARCH_ID))
                                    .build(),
                                SafetyCenterEntry.Builder(safetyCenterEntryGeneric)
                                    .setId(safetyCenterEntryId(DYNAMIC_OTHER_PACKAGE_ID))
                                    .setPendingIntent(null)
                                    .setEnabled(false)
                                    .build()))
                        .build())),
            listOf(safetyCenterStaticEntryGroupFromComplexConfig))

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
    fun setSafetySourceData_withFlagDisabled_noOp() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setEnabled(false)

        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID, safetySourceCtsData.unspecified, EVENT_SOURCE_STATE_CHANGED)

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
    fun reportSafetySourceError_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.reportSafetySourceError(
                SINGLE_SOURCE_ID, SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenImplicitReceiverHasPermission_receiverCalled() {
        val enabledChangedReceiver = safetyCenterCtsHelper.addEnabledChangedReceiver()

        var receiverValue =
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(false)

        assertThat(receiverValue).isFalse()

        val toggledReceiverValue =
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(true)

        assertThat(toggledReceiverValue).isTrue()
    }

    @Test
    fun safetyCenterEnabledChanged_whenImplicitReceiverDoesNotHavePermission_receiverNotCalled() {
        val enabledChangedReceiver = safetyCenterCtsHelper.addEnabledChangedReceiver()

        SafetyCenterFlags.isEnabled = false

        assertFailsWith(TimeoutCancellationException::class) {
            enabledChangedReceiver.receiveSafetyCenterEnabledChanged(TIMEOUT_SHORT)
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverHasPermission_receiverCalled() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        var receiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(false)

        assertThat(receiverValue).isFalse()

        val toggledReceiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(true)

        assertThat(toggledReceiverValue).isTrue()
    }

    @Test
    fun safetyCenterEnabledChanged_valueDoesNotChange_receiverNotCalled() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                true, TIMEOUT_SHORT)
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverDoesNotHavePermission_receiverNotCalled() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        SafetyCenterFlags.isEnabled = false

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.receiveSafetyCenterEnabledChanged(TIMEOUT_SHORT)
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
                SafetySourceDataKey(Reason.REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] =
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
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
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
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
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
                SafetySourceDataKey(Reason.REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] = null

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
                SafetySourceDataKey(Reason.REFRESH_FETCH_FRESH_DATA, SOURCE_ID_1)] =
            safetySourceCtsData.criticalWithIssue
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_FETCH_FRESH_DATA, SOURCE_ID_3)] =
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
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SOURCE_ID_1)] =
            safetySourceCtsData.criticalWithIssue
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SOURCE_ID_3)] =
            safetySourceCtsData.information

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_1)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceCtsData.criticalWithIssue)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_2)
        assertThat(apiSafetySourceData2).isNull()
        val apiSafetySourceData3 =
            safetyCenterManager.getSafetySourceDataWithPermission(SOURCE_ID_3)
        assertThat(apiSafetySourceData3).isNull()
    }

    @Test
    fun refreshSafetySources_whenReceiverDoesNotHavePermission_sourceDoesNotSendData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_FETCH_FRESH_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.criticalWithIssue

        safetyCenterManager.refreshSafetySourcesWithPermission(REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.receiveRefreshSafetySources(TIMEOUT_SHORT)
        }
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_whenSourceNotInConfig_sourceDoesNotSendData() {
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
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

        val broadcastId1 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK)
        val broadcastId2 =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertThat(broadcastId1).isNotEqualTo(broadcastId2)
    }

    @Test
    fun refreshSafetySources_withFlagDisabled_noOp() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setEnabled(false)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
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
    fun refreshSafetySources_refreshAfterSuccessfulRefresh_completesSuccessfully() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceCtsData.information)

        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.criticalWithIssue

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceCtsData.criticalWithIssue)
    }

    @Test
    fun refreshSafetySources_withoutAllowingPreviousRefreshToTimeout_completesSuccessfully() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        // Don't wait for the ongoing refresh to timeout.
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_GET_DATA, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceCtsData.information)
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
    fun getSafetyCenterData_withoutDataProvided_returnsDataFromComplexConfig() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)

        val apiSafetyCenterData =
            normalizeSafetyCenterData(safetyCenterManager.getSafetyCenterDataWithPermission())

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
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataCritical)
    }

    @Test
    fun getSafetyCenterData_withHiddenSourcesAndUpdatedData_returnsVisibleSources() {
        safetyCenterCtsHelper.setConfig(COMPLEX_CONFIG)
        safetyCenterCtsHelper.setData(DYNAMIC_DISABLED_ID, safetySourceCtsData.information)
        safetyCenterCtsHelper.setData(DYNAMIC_HIDDEN_ID, safetySourceCtsData.unspecified)
        safetyCenterCtsHelper.setData(
            DYNAMIC_HIDDEN_WITH_SEARCH_ID, safetySourceCtsData.information)

        val apiSafetyCenterData =
            normalizeSafetyCenterData(safetyCenterManager.getSafetyCenterDataWithPermission())

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfigUpdated)
    }

    @Test
    fun getSafetyCenterData_withFlagDisabled_returnsDefaultData() {
        safetyCenterCtsHelper.setEnabled(false)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(DEFAULT_SAFETY_CENTER_DATA)
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

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataCritical)
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
    // Permission is held for the entire test to avoid a racy scenario where the shell identity is
    // dropped while it's being acquired on another thread.
    fun addOnSafetyCenterDataChangedListener_oneShot_doesntDeadlock() {
        callWithShellPermissionIdentity(
            {
                val listener = SafetyCenterCtsListener()
                val oneShotListener =
                    object : OnSafetyCenterDataChangedListener {
                        override fun onSafetyCenterDataChanged(safetyCenterData: SafetyCenterData) {
                            safetyCenterManager.removeOnSafetyCenterDataChangedListener(this)
                            listener.onSafetyCenterDataChanged(safetyCenterData)
                        }
                    }
                safetyCenterManager.addOnSafetyCenterDataChangedListener(
                    directExecutor(), oneShotListener)

                // Check that we don't deadlock when using a one-shot listener: this is because
                // adding the listener could call the listener while holding a lock on the binder
                // thread-pool; causing a deadlock when attempting to call the `SafetyCenterManager`
                // from that listener.
                listener.receiveSafetyCenterData()
            },
            MANAGE_SAFETY_CENTER)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = SafetyCenterCtsListener()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(directExecutor(), listener)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNotCalledOnSafetySourceData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.information)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
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
    fun removeOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = safetyCenterCtsHelper.addListener()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_existing_callsListenerWithoutDismissedIssue() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            safetyCenterIssueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataCriticalWithoutIssue)

        // Pushing data without the issue does not update the listener.
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.critical)
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_nonExisting_doesntCallListener() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            safetyCenterIssueId(SINGLE_SOURCE_ID, "some_unknown_id"))

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_alreadyDismissed_doesntCallListener() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, safetySourceCtsData.criticalWithIssue)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            safetyCenterIssueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))
        val listener = safetyCenterCtsHelper.addListener()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            safetyCenterIssueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID))

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
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
                SafetySourceDataKey(Reason.RESOLVING_ACTION, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            safetyCenterIssueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            safetyCenterIssueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringInlineAction)
            .isEqualTo(safetyCenterDataCriticalInFlightAction)
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
                SafetySourceDataKey(Reason.RESOLVING_ACTION, SINGLE_SOURCE_ID)] =
            safetySourceCtsData.information

        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            safetyCenterIssueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
            safetyCenterIssueActionId(
                SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringInlineAction)
            .isEqualTo(safetyCenterDataCriticalInFlightAction)
        val safetyCenterDataFromListenerAfterInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterInlineAction)
            .isEqualTo(safetyCenterDataCritical)
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
                safetyCenterIssueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                safetyCenterIssueActionId(
                    SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID),
                TIMEOUT_SHORT)
        }
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
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
    fun setSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetyCenterConfigForTests(SINGLE_SOURCE_CONFIG)
        }
    }

    @Test
    fun clearSafetyCenterConfigForTests_clearsConfigSetForTests_doesNotSetConfigToNull() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isNotNull()
        assertThat(config).isNotEqualTo(SINGLE_SOURCE_CONFIG)
    }

    @Test
    fun clearSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearSafetyCenterConfigForTests()
        }
    }

    private fun normalizeSafetyCenterData(safetyCenterData: SafetyCenterData) =
        SafetyCenterData(
            safetyCenterData.status,
            safetyCenterData.issues,
            safetyCenterData.entriesOrGroups.map {
                if (it.entry != null)
                    SafetyCenterEntryOrGroup(normalizeSafetyCenterEntry(it.entry!!))
                else SafetyCenterEntryOrGroup(normalizeSafetyCenterEntryGroup(it.entryGroup!!))
            },
            safetyCenterData.staticEntryGroups.map { normalizeSafetyCenterStaticEntryGroup(it) })

    private fun normalizeSafetyCenterEntryGroup(safetyCenterEntryGroup: SafetyCenterEntryGroup) =
        SafetyCenterEntryGroup.Builder(safetyCenterEntryGroup)
            .setEntries(safetyCenterEntryGroup.entries.map { normalizeSafetyCenterEntry(it) })
            .build()

    private fun normalizeSafetyCenterEntry(safetyCenterEntry: SafetyCenterEntry) =
        SafetyCenterEntry.Builder(safetyCenterEntry)
            .setPendingIntent(normalizePendingIntent(safetyCenterEntry.pendingIntent))
            .build()

    private fun normalizeSafetyCenterStaticEntryGroup(
        safetyCenterStaticEntryGroup: SafetyCenterStaticEntryGroup
    ) =
        SafetyCenterStaticEntryGroup(
            safetyCenterStaticEntryGroup.title,
            safetyCenterStaticEntryGroup.staticEntries.map { normalizeSafetyCenterStaticEntry(it) })

    private fun normalizeSafetyCenterStaticEntry(safetyCenterStaticEntry: SafetyCenterStaticEntry) =
        SafetyCenterStaticEntry.Builder(safetyCenterStaticEntry)
            .setPendingIntent(normalizePendingIntent(safetyCenterStaticEntry.pendingIntent))
            .build()

    private fun normalizePendingIntent(pendingIntent: PendingIntent?) =
        if (pendingIntent != null && pendingIntent.creatorPackage != context.packageName)
            stubPendingIntent
        else pendingIntent

    companion object {
        private val DEFAULT_SAFETY_CENTER_DATA =
            SafetyCenterData(
                SafetyCenterStatus.Builder("Unknown", "Unknown safety status")
                    .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
                    .build(),
                emptyList(),
                emptyList(),
                emptyList())

        private fun safetyCenterEntryGroupId(sourcesGroupId: String) =
            SafetyCenterIds.encodeToString(
                SafetyCenterEntryGroupId.newBuilder()
                    .setSafetySourcesGroupId(sourcesGroupId)
                    .build())

        private fun safetyCenterEntryId(sourceId: String) =
            SafetyCenterIds.encodeToString(
                SafetyCenterEntryId.newBuilder()
                    .setSafetySourceId(sourceId)
                    .setUserId(UserHandle.myUserId())
                    .build())

        private fun safetyCenterIssueId(sourceId: String, sourceIssueId: String) =
            SafetyCenterIds.encodeToString(
                SafetyCenterIssueId.newBuilder()
                    .setSafetySourceId(sourceId)
                    .setSafetySourceIssueId(sourceIssueId)
                    .setUserId(UserHandle.myUserId())
                    .build())

        private fun safetyCenterIssueActionId(
            sourceId: String,
            sourceIssueId: String,
            sourceIssueActionId: String
        ) =
            SafetyCenterIds.encodeToString(
                SafetyCenterIssueActionId.newBuilder()
                    .setSafetyCenterIssueId(
                        SafetyCenterIssueId.newBuilder()
                            .setSafetySourceId(sourceId)
                            .setSafetySourceIssueId(sourceIssueId)
                            .setUserId(UserHandle.myUserId())
                            .build())
                    .setSafetySourceIssueActionId(sourceIssueActionId)
                    .build())
    }
}
