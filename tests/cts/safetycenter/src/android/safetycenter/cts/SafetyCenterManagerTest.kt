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
import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_NONE
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_OK
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC
import android.safetycenter.config.SafetySourcesGroup
import android.safetycenter.testing.SafetyCenterFlags
import android.safetycenter.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.testing.SafetySourceBroadcastReceiver
import android.safetycenter.testing.addOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.testing.clearAllSafetySourceDataWithPermission
import android.safetycenter.testing.clearSafetyCenterConfigOverrideWithPermission
import android.safetycenter.testing.getSafetyCenterDataWithPermission
import android.safetycenter.testing.getSafetySourceDataWithPermission
import android.safetycenter.testing.isSafetyCenterEnabledWithPermission
import android.safetycenter.testing.refreshSafetySourcesWithPermission
import android.safetycenter.testing.removeOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.testing.reportSafetySourceErrorWithPermission
import android.safetycenter.testing.setSafetyCenterConfigOverrideWithPermission
import android.safetycenter.testing.setSafetySourceDataWithPermission
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    private val somePendingIntent =
        PendingIntent.getActivity(
            context,
            0 /* requestCode */,
            Intent(ACTION_SAFETY_CENTER),
            FLAG_IMMUTABLE
        )
    private val safetySourceDataNone =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                    "None title",
                    "None summary",
                    STATUS_LEVEL_NONE,
                    somePendingIntent
                )
                    .build()
            )
            .build()
    private val safetySourceDataOk =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", STATUS_LEVEL_OK,
                    somePendingIntent)
                    .build()
            )
            .build()
    private val safetySourceDataCritical =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                    "Critical title",
                    "Critical summary",
                    STATUS_LEVEL_CRITICAL_WARNING,
                    somePendingIntent
                )
                    .build()
            )
            .addIssue(
                SafetySourceIssue.Builder(
                    "critical_issue_id",
                    "Critical issue title",
                    "Critical issue summary",
                    SEVERITY_LEVEL_CRITICAL_WARNING,
                    "issue_type_id"
                )
                    .addAction(
                        SafetySourceIssue.Action.Builder("critical_action_id", "Solve issue",
                            somePendingIntent)
                            .build()
                    )
                    .build()
            )
            .build()
    private val listener = object : OnSafetyCenterDataChangedListener {
        private val dataChannel = Channel<SafetyCenterData>()
        private val errorChannel = Channel<SafetyCenterErrorDetails>()

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
        safetyCenterManager.clearAllSafetySourceDataWithPermission()
        safetyCenterManager.clearSafetyCenterConfigOverrideWithPermission()
        SafetySourceBroadcastReceiver.reset()
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
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataNone,
            EVENT_SOURCE_STATE_CHANGED
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataNone)
    }

    @Test
    fun setSafetySourceData_twice_replacesValue() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataNone,
            EVENT_SOURCE_STATE_CHANGED
        )

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataCritical)
    }

    @Test
    fun setSafetySourceData_null_clearsValue() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataNone,
            EVENT_SOURCE_STATE_CHANGED
        )

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceData = null,
            EVENT_SOURCE_STATE_CHANGED
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_withFlagDisabled_noOp() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataNone,
            EVENT_SOURCE_STATE_CHANGED
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetySourceData(
                CTS_SOURCE_ID,
                safetySourceDataNone,
                EVENT_SOURCE_STATE_CHANGED
            )
        }
    }

    @Test
    fun getSafetySourceData_validId_noData_returnsNull() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)

        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun getSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.getSafetySourceData(CTS_SOURCE_ID)
        }
    }

    @Test
    fun reportSafetySourceError_callsErrorListener() {
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            CTS_SOURCE_ID,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )
        val safetyCenterErrorDetailsFromListener = listener.receiveSafetyCenterErrorDetails()

        assertThat(safetyCenterErrorDetailsFromListener).isEqualTo(
            SafetyCenterErrorDetails("Error"))
    }

    @Test
    fun reportSafetySourceError_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.reportSafetySourceError(
                CTS_SOURCE_ID,
                SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
            )
        }
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_sourceSendsRescanData() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        SafetySourceBroadcastReceiver.safetySourceId = CTS_SOURCE_ID
        SafetySourceBroadcastReceiver.safetySourceDataOnRescanClick = safetySourceDataCritical

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataCritical)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_sourceSendsPageOpenData() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        SafetySourceBroadcastReceiver.safetySourceId = CTS_SOURCE_ID
        SafetySourceBroadcastReceiver.safetySourceDataOnPageOpen = safetySourceDataOk

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(safetySourceDataOk)
    }

    @Test
    fun refreshSafetySources_whenReceiverDoesNotHaveSendingPermission_sourceDoesNotSendData() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        SafetySourceBroadcastReceiver.safetySourceId = CTS_SOURCE_ID
        SafetySourceBroadcastReceiver.safetySourceDataOnRescanClick = safetySourceDataCritical

        safetyCenterManager.refreshSafetySourcesWithPermission(REFRESH_REASON_RESCAN_BUTTON_CLICK)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceBroadcastReceiver.waitTillOnReceiveComplete(TIMEOUT_SHORT)
        }
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_withFlagDisabled_noOp() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        SafetySourceBroadcastReceiver.safetySourceId = CTS_SOURCE_ID
        SafetySourceBroadcastReceiver.safetySourceDataOnPageOpen = safetySourceDataOk

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN, TIMEOUT_SHORT)
        }
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_withInvalidRefreshSeason_throwsIllegalArgumentException() {
        val thrown =
            assertFailsWith(IllegalArgumentException::class) {
                safetyCenterManager.refreshSafetySourcesWithPermission(500)
            }
        assertThat(thrown).hasMessageThat().isEqualTo("Invalid refresh reason: 500")
    }

    @Test
    fun refreshSafetySources_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_RESCAN_BUTTON_CLICK)
        }
    }

    @Test
    fun getSafetyCenterData_withoutDataProvided_returnsDataFromConfig() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        // TODO(b/218830137): Assert on content.
        assertThat(apiSafetyCenterData).isNotNull()
    }

    @Test
    fun getSafetyCenterData_withSomeDataProvided_returnsDataProvided() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataNone,
            EVENT_SOURCE_STATE_CHANGED
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        // TODO(b/218830137): Assert on content.
        assertThat(apiSafetyCenterData).isNotNull()
    }

    @Test
    fun getSafetyCenterData_withUpdatedData_returnsUpdatedData() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataOk,
            EVENT_SOURCE_STATE_CHANGED
        )
        val previousApiSafetyCenterData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED
        )

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        // TODO(b/218830137): Assert on content.
        assertThat(apiSafetyCenterData).isNotEqualTo(previousApiSafetyCenterData)
    }

    @Test
    fun getSafetyCenterData_withFlagDisabled_returnsDefaultData() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(SafetyCenterData(
            SafetyCenterStatus.Builder()
                .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
                .setTitle("Unknown")
                .setSummary("Unknown safety status")
                .build(),
            emptyList(),
            emptyList(),
            emptyList()
        ))
    }

    @Test
    fun getSafetyCenterData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterData }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWithSafetyCenterDataFromConfig() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        // TODO(b/218830137): Assert on content.
        assertThat(safetyCenterDataFromListener).isNotNull()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledOnSafetySourceData() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataOk,
            EVENT_SOURCE_STATE_CHANGED
        )
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        // TODO(b/218830137): Assert on content.
        assertThat(safetyCenterDataFromListener).isNotNull()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataChanges() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )
        // Receive initial data.
        listener.receiveSafetyCenterData()
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataOk,
            EVENT_SOURCE_STATE_CHANGED
        )
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataCritical,
            EVENT_SOURCE_STATE_CHANGED
        )
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        // TODO(b/218830137): Assert on content.
        assertThat(safetyCenterDataFromListener).isNotNull()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWhenSafetySourceDataCleared() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )
        // Receive initial data.
        listener.receiveSafetyCenterData()
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataOk,
            EVENT_SOURCE_STATE_CHANGED
        )
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceData = null,
            EVENT_SOURCE_STATE_CHANGED
        )
        val safetyCenterDataFromListener = listener.receiveSafetyCenterData()

        // TODO(b/218830137): Assert on content.
        assertThat(safetyCenterDataFromListener).isNotNull()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataStaysNull() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceData = null,
            EVENT_SOURCE_STATE_CHANGED
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalledWhenSafetySourceDataDoesntChange() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )
        // Receive initial data.
        listener.receiveSafetyCenterData()
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataOk,
            EVENT_SOURCE_STATE_CHANGED
        )
        // Receive update from #setSafetySourceData call.
        listener.receiveSafetyCenterData()

        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataOk,
            EVENT_SOURCE_STATE_CHANGED
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withFlagDisabled_listenerNotCalled() {
        SafetyCenterFlags.setSafetyCenterEnabled(false)
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)

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
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(directExecutor(), listener)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_oneShot_doesntDeadlock() {
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

        // Check that we don't deadlock when using a one-shot listener: this is because adding the
        // listener could call the listener while holding a lock on the binder thread-pool; causing
        // a deadlock when attempting to call the `SafetyCenterManager` from that listener.
        listener.receiveSafetyCenterData()
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNotCalledOnSafetySourceData() {
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_CONFIG)
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )
        // Receive initial data.
        listener.receiveSafetyCenterData()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
        safetyCenterManager.setSafetySourceDataWithPermission(
            CTS_SOURCE_ID,
            safetySourceDataOk,
            EVENT_SOURCE_STATE_CHANGED
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.dismissSafetyCenterIssue("bleh")
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.executeSafetyCenterIssueAction("bleh", "blah")
        }
    }

    @Test
    fun clearAllSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.clearAllSafetySourceData() }
    }

    @Test
    fun setSafetyCenterConfigOverride_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetyCenterConfigOverride(CTS_CONFIG)
        }
    }

    @Test
    fun clearSafetyCenterConfigOverride_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearSafetyCenterConfigOverride()
        }
    }

    private fun SafetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
        refreshReason: Int,
        timeout: Duration = TIMEOUT_LONG
    ) {
        callWithShellPermissionIdentity(
            {
                refreshSafetySources(refreshReason)
                SafetySourceBroadcastReceiver.waitTillOnReceiveComplete(timeout)
            },
            SEND_SAFETY_CENTER_UPDATE,
            MANAGE_SAFETY_CENTER
        )
    }

    private fun <T> runBlockingWithTimeout(
        timeout: Duration = TIMEOUT_LONG,
        block: suspend () -> T
    ) =
        runBlocking {
            withTimeout(timeout.toMillis()) { block() }
        }

    companion object {
        private const val CTS_PACKAGE_NAME = "android.safetycenter.cts"
        private const val CTS_BROADCAST_RECEIVER_NAME =
            "android.safetycenter.testing.SafetySourceBroadcastReceiver"
        private val TIMEOUT_LONG: Duration = Duration.ofMillis(5000)
        private val TIMEOUT_SHORT: Duration = Duration.ofMillis(1000)
        private val EVENT_SOURCE_STATE_CHANGED =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
        private const val CTS_SOURCE_ID = "cts_source_id"
        private const val CTS_SOURCE_GROUP_ID = "cts_source_group"

        // TODO(b/217944317): Consider moving the following to a file where they can be used by
        //  other tests.
        private val CTS_SOURCE =
            SafetySource.Builder(SAFETY_SOURCE_TYPE_DYNAMIC)
                .setId(CTS_SOURCE_ID)
                .setPackageName(CTS_PACKAGE_NAME)
                .setTitleResId(android.R.string.ok)
                .setSummaryResId(android.R.string.ok)
                .setIntentAction(ACTION_SAFETY_CENTER)
                .setBroadcastReceiverClassName(CTS_BROADCAST_RECEIVER_NAME)
                .setProfile(SafetySource.PROFILE_PRIMARY)
                .build()
        private val CTS_SOURCE_GROUP =
            SafetySourcesGroup.Builder()
                .setId(CTS_SOURCE_GROUP_ID)
                .setTitleResId(android.R.string.ok)
                .setSummaryResId(android.R.string.ok)
                .addSafetySource(CTS_SOURCE)
                .build()
        private val CTS_CONFIG =
            SafetyCenterConfig.Builder().addSafetySourcesGroup(CTS_SOURCE_GROUP).build()
    }
}
