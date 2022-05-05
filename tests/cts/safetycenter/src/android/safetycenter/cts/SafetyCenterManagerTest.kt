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
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.content.Intent.FLAG_RECEIVER_FOREGROUND
import android.content.IntentFilter
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.UserHandle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceStatus
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.Coroutines.runBlockingWithTimeout
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
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.CTS_MULTIPLE_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_1
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_2
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.CTS_SEVERITY_ZERO_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.CTS_SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterEnabledChangedReceiver
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.ACTION_HANDLE_INLINE_ACTION
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.EXTRA_INLINE_ACTION_SOURCE_ID
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.EXTRA_INLINE_ACTION_SOURCE_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.EXTRA_INLINE_ACTION_SOURCE_ISSUE_ID
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
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    private val somePendingIntent =
        PendingIntent.getActivity(
            context, 0 /* requestCode */, Intent(ACTION_SAFETY_CENTER), FLAG_IMMUTABLE)
    private val criticalIssueActionPendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_HANDLE_INLINE_ACTION)
                .setFlags(FLAG_RECEIVER_FOREGROUND)
                .setPackage(context.packageName)
                .putExtra(EXTRA_INLINE_ACTION_SOURCE_ID, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
                .putExtra(EXTRA_INLINE_ACTION_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
                .putExtra(EXTRA_INLINE_ACTION_SOURCE_ISSUE_ACTION_ID, CRITICAL_ISSUE_ACTION_ID),
            FLAG_IMMUTABLE)
    private val safetySourceDataUnspecified =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Unspecified title",
                        "Unspecified summary",
                        SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED)
                    .setEnabled(false)
                    .build())
            .build()
    private val safetySourceDataInformation =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                    "Ok title", "Ok summary", SafetySourceData.SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(somePendingIntent)
                    .build())
            .build()
    private val safetySourceDataInformationWithIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                    "Ok title", "Ok summary", SafetySourceData.SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(somePendingIntent)
                    .build())
            .addIssue(
                SafetySourceIssue.Builder(
                    INFORMATION_ISSUE_ID,
                    "Information issue title",
                    "Information issue summary",
                    SafetySourceData.SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                    .addAction(
                        SafetySourceIssue.Action.Builder(
                            INFORMATION_ISSUE_ACTION_ID,
                            "Review",
                            somePendingIntent)
                            .build())
                    .build())
            .build()
    private val safetySourceDataCritical =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title",
                        "Critical summary",
                        SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING)
                    .setPendingIntent(somePendingIntent)
                    .build())
            .addIssue(
                SafetySourceIssue.Builder(
                        CRITICAL_ISSUE_ID,
                        "Critical issue title",
                        "Critical issue summary",
                        SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING,
                        "issue_type_id")
                    .addAction(
                        SafetySourceIssue.Action.Builder(
                                CRITICAL_ISSUE_ACTION_ID,
                                "Solve issue",
                                criticalIssueActionPendingIntent)
                            .setWillResolve(true)
                            .build())
                    .build())
            .build()
    private val safetyCenterDataFromConfig =
        SafetyCenterData(
            SafetyCenterStatus.Builder("Unknown", "Unknown safety status")
                .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                .build(),
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntry.Builder(
                            safetyCenterEntryId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID),
                            "OK")
                        .setSeverityLevel(
                            SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN)
                        .setSummary("OK")
                        .setPendingIntent(somePendingIntent)
                        .setSeverityUnspecifiedIconType(
                            SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
                        .build())),
            emptyList())
    private val safetyCenterDataUnspecified =
        SafetyCenterData(
            SafetyCenterStatus.Builder("All good", "No problemo maestro")
                .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                .build(),
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntry.Builder(
                            safetyCenterEntryId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID),
                            "Unspecified title")
                        .setSeverityLevel(
                            SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
                        .setSummary("Unspecified summary")
                        .setPendingIntent(somePendingIntent)
                        .setEnabled(false)
                        .setSeverityUnspecifiedIconType(
                            SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
                        .build())),
            emptyList())
    private val safetyCenterDataOk =
        SafetyCenterData(
            SafetyCenterStatus.Builder("All good", "No problemo maestro")
                .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
                .build(),
            emptyList(),
            listOf(
                SafetyCenterEntryOrGroup(
                    SafetyCenterEntry.Builder(
                            safetyCenterEntryId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID),
                            "Ok title")
                        .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
                        .setSummary("Ok summary")
                        .setPendingIntent(somePendingIntent)
                        .setSeverityUnspecifiedIconType(
                            SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
                        .build())),
            emptyList())
    private val safetyCenterStatusCritical =
        SafetyCenterStatus.Builder("Uh-oh", "Code red")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()
    private val safetyCenterIssueCritical =
        SafetyCenterIssue.Builder(
                safetyCenterIssueId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, CRITICAL_ISSUE_ID),
                "Critical issue title",
                "Critical issue summary")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            safetyCenterIssueActionId(
                                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
                                CRITICAL_ISSUE_ID,
                                CRITICAL_ISSUE_ACTION_ID),
                            "Solve issue",
                            criticalIssueActionPendingIntent)
                        .setWillResolve(true)
                        .build()))
            .build()
    private val safetyCenterEntryOrGroupCritical =
        SafetyCenterEntryOrGroup(
            SafetyCenterEntry.Builder(
                    safetyCenterEntryId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID),
                    "Critical title")
                .setSeverityLevel(
                    SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                .setSummary("Critical summary")
                .setPendingIntent(somePendingIntent)
                .setSeverityUnspecifiedIconType(
                    SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
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
                                        CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
                                        CRITICAL_ISSUE_ID,
                                        CRITICAL_ISSUE_ACTION_ID),
                                    "Solve issue",
                                    criticalIssueActionPendingIntent)
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
    private val listener =
        object : OnSafetyCenterDataChangedListener {
            private val dataChannel = Channel<SafetyCenterData>(UNLIMITED)
            private val errorChannel = Channel<SafetyCenterErrorDetails>(UNLIMITED)

            override fun onSafetyCenterDataChanged(data: SafetyCenterData) {
                runBlockingWithTimeout { dataChannel.send(data) }
            }

            override fun onError(errorDetails: SafetyCenterErrorDetails) {
                runBlockingWithTimeout { errorChannel.send(errorDetails) }
            }

            fun receiveSafetyCenterData(timeout: Duration = TIMEOUT_LONG) =
                runBlockingWithTimeout(timeout) { dataChannel.receive() }

            fun receiveSafetyCenterErrorDetails(timeout: Duration = TIMEOUT_LONG) =
                runBlockingWithTimeout(timeout) { errorChannel.receive() }

            fun cancelChannels() {
                dataChannel.cancel()
                errorChannel.cancel()
            }
        }

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(context.deviceSupportsSafetyCenter())
    }

    @Before
    @After
    fun clearDataBetweenTest() {
        SafetyCenterFlags.setSafetyCenterEnabled(true)
        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()
        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()
        SafetySourceReceiver.reset()
    }

    @After
    fun cancelChannelsAfterTest() {
        listener.cancelChannels()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagEnabled_returnsTrue() {
        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isTrue()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagDisabled_returnsFalse() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.isSafetyCenterEnabled }
    }

    @Test
    fun setSafetySourceData_validId_setsValue() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataUnspecified,
            EVENT_SOURCE_STATE_CHANGED)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataUnspecified)
    }

    @Test
    fun setSafetySourceData_twice_replacesValue() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataUnspecified,
            EVENT_SOURCE_STATE_CHANGED)

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataCritical)
    }

    @Test
    fun setSafetySourceData_null_clearsValue() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataUnspecified,
            EVENT_SOURCE_STATE_CHANGED)

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, safetySourceData = null, EVENT_SOURCE_STATE_CHANGED)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_unknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.setSafetySourceDataWithPermission(
                    CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
                    safetySourceDataUnspecified,
                    EVENT_SOURCE_STATE_CHANGED)
            }
        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source \"$CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID\"")
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevUnspecified_setsValue() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SEVERITY_ZERO_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataUnspecified,
            EVENT_SOURCE_STATE_CHANGED)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataUnspecified)
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevInformation_setsValue() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SEVERITY_ZERO_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataInformation)
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevInformationWithIssue_throwsException() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SEVERITY_ZERO_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.setSafetySourceDataWithPermission(
                    CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
                    safetySourceDataInformationWithIssue,
                    EVENT_SOURCE_STATE_CHANGED)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level \"${
                    SafetySourceData.SEVERITY_LEVEL_INFORMATION
                }\" for issue in safety source \"$CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID\"")
    }

    @Test
    fun setSafetySourceData_withMaxSevZeroAndSourceSevCritical_throwsException() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SEVERITY_ZERO_CONFIG)

        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.setSafetySourceDataWithPermission(
                    CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
                    safetySourceDataCritical,
                    EVENT_SOURCE_STATE_CHANGED)
            }

        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo(
                "Unexpected severity level \"${
                    SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
                }\" for safety source \"$CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID\"")
    }

    @Test
    fun setSafetySourceData_withFlagDisabled_noOp() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataUnspecified,
            EVENT_SOURCE_STATE_CHANGED)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetySourceData(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
                safetySourceDataUnspecified,
                EVENT_SOURCE_STATE_CHANGED)
        }
    }

    @Test
    fun getSafetySourceData_validId_noData_returnsNull() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)

        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun getSafetySourceData_unknownId_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.getSafetySourceDataWithPermission(
                    CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
            }
        assertThat(thrown)
            .hasMessageThat()
            .isEqualTo("Unexpected safety source \"$CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID\"")
    }

    @Test
    fun getSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.getSafetySourceData(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        }
    }

    @Test
    fun reportSafetySourceError_callsErrorListener() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        // Receive potentially modified data from error.
        // TODO(b/228832622): Ensure listeners are called only when data changes.
        listener.receiveSafetyCenterData()
        val safetyCenterErrorDetailsFromListener = listener.receiveSafetyCenterErrorDetails()

        assertThat(safetyCenterErrorDetailsFromListener)
            .isEqualTo(SafetyCenterErrorDetails("Error"))
    }

    @Test
    fun reportSafetySourceError_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.reportSafetySourceError(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
                SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED))
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenImplicitReceiverHasPermission_receiverCalled() {
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver()
        context.registerReceiver(
            enabledChangedReceiver, IntentFilter(ACTION_SAFETY_CENTER_ENABLED_CHANGED))

        var receiverValue =
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(false)

        assertThat(receiverValue).isFalse()

        val toggledReceiverValue =
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(true)

        assertThat(toggledReceiverValue).isTrue()
        context.unregisterReceiver(enabledChangedReceiver)
    }

    @Test
    fun safetyCenterEnabledChanged_whenImplicitReceiverDoesNotHavePermission_receiverNotCalled() {
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver()
        context.registerReceiver(
            enabledChangedReceiver, IntentFilter(ACTION_SAFETY_CENTER_ENABLED_CHANGED))

        SafetyCenterFlags.setSafetyCenterEnabled(false)

        assertFailsWith(TimeoutCancellationException::class) {
            enabledChangedReceiver.receiveSafetyCenterEnabledChanged(TIMEOUT_SHORT)
        }
        context.unregisterReceiver(enabledChangedReceiver)
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverHasPermission_receiverCalled() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        var receiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(false)

        assertThat(receiverValue).isFalse()

        val toggledReceiverValue =
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(true)

        assertThat(toggledReceiverValue).isTrue()
    }

    @Test
    fun safetyCenterEnabledChanged_valueDoesNotChange_receiverNotCalled() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                true, TIMEOUT_SHORT)
        }
    }

    @Test
    fun safetyCenterEnabledChanged_whenSourceReceiverDoesNotHavePermission_receiverNotCalled() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        SafetyCenterFlags.setSafetyCenterEnabled(false)

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
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_RESCAN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataCritical

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataCritical)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_sourceSendsPageOpenData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_PAGE_OPEN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataInformation

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataInformation)
    }

    @Test
    fun refreshSafetySources_whenSourceClearsData_sourceSendsNullData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_RESCAN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            null

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_withMultipleSourcesInConfig_multipleSourcesSendData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_MULTIPLE_SOURCES_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(
                    Reason.REFRESH_RESCAN, CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_1)] =
            safetySourceDataCritical
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(
                    Reason.REFRESH_RESCAN, CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_2)] =
            safetySourceDataInformation

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_1)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_2)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceDataCritical)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceDataInformation)
    }

    @Test
    fun refreshSafetySources_withMultipleSources_onlyOneSourceHasData_oneSourceSendData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_MULTIPLE_SOURCES_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(
                    Reason.REFRESH_RESCAN, CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_1)] =
            safetySourceDataCritical

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_1)
        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_2)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceDataCritical)
        assertThat(apiSafetySourceData2).isNull()
    }

    @Test
    fun refreshSafetySources_receiverSupportsMultipleSources_onlyRequestedSourceSendsData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_RESCAN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataCritical
        val otherSourceId = "other_source_ids"
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_RESCAN, otherSourceId)] =
            safetySourceDataInformation

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceDataCritical)
    }

    @Test
    fun refreshSafetySources_whenReceiverDoesNotHavePermission_sourceDoesNotSendData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_RESCAN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataCritical

        safetyCenterManager.refreshSafetySourcesWithPermission(REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.receiveRefreshSafetySources(TIMEOUT_SHORT)
        }
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_whenSourceNotInConfig_sourceDoesNotSendData() {
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_PAGE_OPEN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataInformation

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN, TIMEOUT_SHORT)
        }
    }

    @Test
    fun refreshSafetySources_sendsBroadcastId() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_RESCAN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataCritical

        val lastReceivedBroadcastId =
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertThat(lastReceivedBroadcastId).isNotNull()
    }

    @Test
    fun refreshSafetySources_sendsDifferentBroadcastIds_onEachMethodCall() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_RESCAN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataCritical

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
        SafetyCenterFlags.setSafetyCenterEnabled(false)
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_PAGE_OPEN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataInformation

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN, TIMEOUT_SHORT)
        }
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_refreshAfterSuccessfulRefresh_completesSuccessfully() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_PAGE_OPEN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataInformation

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData1).isEqualTo(safetySourceDataInformation)

        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_PAGE_OPEN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataCritical

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceDataCritical)
    }

    @Test
    fun refreshSafetySources_withoutAllowingPreviousRefreshToTimeout_completesSuccessfully() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)
        val apiSafetySourceData1 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData1).isEqualTo(null)

        // Don't wait for the ongoing refresh to timeout.
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.REFRESH_PAGE_OPEN, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataInformation

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData2 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        assertThat(apiSafetySourceData2).isEqualTo(safetySourceDataInformation)
    }

    @Test
    fun refreshSafetySources_withInvalidRefreshSeason_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.refreshSafetySourcesWithPermission(143201)
            }
        assertThat(thrown).hasMessageThat().isEqualTo("Invalid refresh reason: 143201")
    }

    @Test
    fun refreshSafetySources_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_RESCAN_BUTTON_CLICK)
        }
    }

    @Test
    fun getSafetyCenterConfig_isNotNull() {
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNotNull()
        assertThat(config).isNotEqualTo(CTS_SINGLE_SOURCE_CONFIG)
    }

    @Test
    fun getSafetyCenterConfig_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterConfig }
    }

    @Test
    fun getSafetyCenterData_withoutDataProvided_returnsDataFromConfig() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun getSafetyCenterData_withSomeDataProvided_returnsDataProvided() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataUnspecified,
            EVENT_SOURCE_STATE_CHANGED)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataUnspecified)
    }

    @Test
    fun getSafetyCenterData_withUpdatedData_returnsUpdatedData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)
        val previousApiSafetyCenterData =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isNotEqualTo(previousApiSafetyCenterData)
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataCritical)
    }

    @Test
    fun getSafetyCenterData_withFlagDisabled_returnsDefaultData() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(DEFAULT_SAFETY_CENTER_DATA)
    }

    @Test
    fun getSafetyCenterData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterData }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWithSafetyCenterDataFromConfig() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledOnSafetySourceData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataChanges() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataCritical)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataCleared() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)
        // Receive update from #setSagetSafetyCenterData_withoutDataProvided_fetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, safetySourceData = null, EVENT_SOURCE_STATE_CHANGED)
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataFromConfig)
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataStaysNull() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, safetySourceData = null, EVENT_SOURCE_STATE_CHANGED)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataDoesntChange() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withFlagDisabled_listenerNotCalled() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

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
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(directExecutor(), listener)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNotCalledOnSafetySourceData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNeverCalledAfterRemoving() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        val fakeExecutor = FakeExecutor()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            fakeExecutor, listener)
        // Receive initial data.
        fakeExecutor.getNextTask().run()
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)
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
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_existing_callsListenerWithoutDismissedIssue() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            safetyCenterIssueId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, CRITICAL_ISSUE_ID))

        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListener).isEqualTo(safetyCenterDataCriticalWithoutIssue)
    }

    @Test
    fun dismissSafetyCenterIssue_nonExisting_doesntCallListener() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            safetyCenterIssueId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, "some_unknown_id"))

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_alreadyDismissed_doesntCallListener() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED)
        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            safetyCenterIssueId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, CRITICAL_ISSUE_ID))
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            safetyCenterIssueId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, CRITICAL_ISSUE_ID))

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
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.INLINE_ACTION, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataInformation

        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            safetyCenterIssueId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, CRITICAL_ISSUE_ID),
            safetyCenterIssueActionId(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringInlineAction)
            .isEqualTo(safetyCenterDataCriticalInFlightAction)
        val safetyCenterDataFromListenerAfterInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterInlineAction).isEqualTo(safetyCenterDataOk)
    }

    @Test
    fun executeSafetyCenterIssueAction_existing_unmarkInFlightWhenInlineActionError() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()
        SafetySourceReceiver.shouldReportSafetySourceError = true
        SafetySourceReceiver.safetySourceData[
                SafetySourceDataKey(Reason.INLINE_ACTION, CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID)] =
            safetySourceDataInformation

        safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
            safetyCenterIssueId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, CRITICAL_ISSUE_ID),
            safetyCenterIssueActionId(
                CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID))

        val safetyCenterDataFromListenerDuringInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerDuringInlineAction)
            .isEqualTo(safetyCenterDataCriticalInFlightAction)
        val safetyCenterDataFromListenerAfterInlineAction = listener.receiveSafetyCenterData()
        assertThat(safetyCenterDataFromListenerAfterInlineAction)
            .isEqualTo(safetyCenterDataCritical)
        val error = listener.receiveSafetyCenterErrorDetails()
        assertThat(error).isEqualTo(SafetyCenterErrorDetails("Error"))
    }

    @Test
    fun executeSafetyCenterIssueAction_nonExisting_doesntCallListenerOrExecute() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
            safetySourceDataInformation,
            EVENT_SOURCE_STATE_CHANGED)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener)
        // Receive initial data.
        listener.receiveSafetyCenterData()

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithReceiverPermissionAndWait(
                safetyCenterIssueId(CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID, CRITICAL_ISSUE_ID),
                safetyCenterIssueActionId(
                    CTS_SINGLE_SOURCE_CONFIG_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID),
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
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_MULTIPLE_SOURCES_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_1,
            safetySourceDataUnspecified,
            EVENT_SOURCE_STATE_CHANGED)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_2,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED)
        val data1 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_1)
        assertThat(data1).isEqualTo(safetySourceDataUnspecified)
        val data2 =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_2)
        assertThat(data2).isEqualTo(safetySourceDataCritical)

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        val data1AfterClearing =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_1)
        assertThat(data1AfterClearing).isNull()
        val data2AfterClearing =
            safetyCenterManager.getSafetySourceDataWithPermission(
                CTS_MULTIPLE_SOURCES_CONFIG_SOURCE_ID_2)
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
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isEqualTo(CTS_SINGLE_SOURCE_CONFIG)
    }

    @Test
    fun setSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetyCenterConfigForTests(CTS_SINGLE_SOURCE_CONFIG)
        }
    }

    @Test
    fun clearSafetyCenterConfigForTests_clearsConfigSetForTests_doesNotSetConfigToNull() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(CTS_SINGLE_SOURCE_CONFIG)
        assertThat(safetyCenterManager.getSafetyCenterConfigWithPermission())
            .isEqualTo(CTS_SINGLE_SOURCE_CONFIG)
        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNotNull()
        assertThat(config).isNotEqualTo(CTS_SINGLE_SOURCE_CONFIG)
    }

    @Test
    fun clearSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearSafetyCenterConfigForTests()
        }
    }

    companion object {
        private val EVENT_SOURCE_STATE_CHANGED =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        private val DEFAULT_SAFETY_CENTER_DATA =
            SafetyCenterData(
                SafetyCenterStatus.Builder("Unknown", "Unknown safety status")
                    .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                    .build(),
                emptyList(),
                emptyList(),
                emptyList())

        private const val CRITICAL_ISSUE_ID = "critical_issue_id"

        private const val CRITICAL_ISSUE_ACTION_ID = "critical_issue_action_id"

        private const val INFORMATION_ISSUE_ID = "information_issue_id"

        private const val INFORMATION_ISSUE_ACTION_ID = "information_issue_action_id"

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
