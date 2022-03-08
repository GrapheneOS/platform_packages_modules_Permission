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
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.res.Resources
import android.os.Build.VERSION_CODES.TIRAMISU
import android.provider.DeviceConfig
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED
import android.safetycenter.SafetySourceIssue.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_CRITICAL_WARNING
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySourcesGroup
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
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
    private val somePendingIntent = PendingIntent.getActivity(
        context, 0 /* requestCode */,
        Intent(ACTION_SAFETY_CENTER).addFlags(FLAG_ACTIVITY_NEW_TASK),
        FLAG_IMMUTABLE
    )
    private val safetySourceDataOnPageOpen = SafetySourceData.Builder()
        .setStatus(
            SafetySourceStatus.Builder(
                "safetySourceDataOnPageOpen status title",
                "safetySourceDataOnPageOpen status summary",
                SafetySourceStatus.STATUS_LEVEL_NONE,
                somePendingIntent
            )
                .build()
        )
        .build()
    private val safetySourceDataOnRescanClick = SafetySourceData.Builder()
        .setStatus(
            SafetySourceStatus.Builder(
                "safetySourceDataOnRescanClick status title",
                "safetySourceDataOnRescanClick status summary",
                SafetySourceStatus.STATUS_LEVEL_RECOMMENDATION,
                somePendingIntent
            )
                .build()
        ).build()
    private val listenerChannel = Channel<SafetyCenterData>()
    // The lambda has to be wrapped to the right type because kotlin wraps lambdas in a new Java
    // functional interface implementation each time they are referenced/cast to a Java interface:
    // b/215569072.
    private val listener = OnSafetyCenterDataChangedListener {
        runBlockingWithTimeout {
            listenerChannel.send(it)
        }
    }

    @Before
    @After
    fun clearDataBetweenTest() {
        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
        safetyCenterManager.clearAllSafetySourceDataWithPermission()
        SafetySourceBroadcastReceiver.reset()
    }

    @Before
    fun setSafetyCenterConfigOverrideBeforeTest() {
        safetyCenterManager.clearSafetyCenterConfigOverrideWithPermission()
        // TODO(b/217944317): When the test API impl is finalized to override XML config, only
        //  override config in select test cases that require it. This is to ensure that we do have
        //  some test cases running with the XML config.
        safetyCenterManager.setSafetyCenterConfigOverrideWithPermission(CTS_ONLY_CONFIG)
    }

    @After
    fun clearSafetyCenterConfigOverrideAfterTest() {
        safetyCenterManager.clearSafetyCenterConfigOverrideWithPermission()
    }

    @Test
    fun getSafetySourceData_notSet_returnsNull() {
        val safetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission("some_unknown_id")

        assertThat(safetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_getSafetySourceDataReturnsNewValue() {
        val safetySourceData = SafetySourceData.Builder().build()
        safetyCenterManager.setSafetySourceDataWithPermission(
                CTS_SOURCE_ID,
                safetySourceData,
                EVENT_SOURCE_STATE_CHANGED
        )

        val safetySourceDataResult =
                safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)

        assertThat(safetySourceDataResult).isEqualTo(safetySourceData)
    }

    @Test
    fun setSafetySourceData_withSameId_replacesValue() {
        val firstSafetySourceData = SafetySourceData.Builder().build()
        safetyCenterManager.setSafetySourceDataWithPermission(
                CTS_SOURCE_ID,
                firstSafetySourceData,
                EVENT_SOURCE_STATE_CHANGED
        )

        val secondSafetySourceData = SafetySourceData.Builder().setStatus(
            SafetySourceStatus.Builder(
                "Status title", "Summary of the status", STATUS_LEVEL_CRITICAL_WARNING,
                somePendingIntent
            ).build()
        ).addIssue(
            SafetySourceIssue.Builder(
                "Issue id", "Issue title", "Summary of the issue",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                "issue_type_id"
            )
                .addAction(
                    SafetySourceIssue.Action.Builder(
                        "action_id",
                        "Solve issue",
                        somePendingIntent
                    ).build()
                ).build()
        ).build()
        safetyCenterManager.setSafetySourceDataWithPermission(
                CTS_SOURCE_ID,
                secondSafetySourceData,
                EVENT_SOURCE_STATE_CHANGED
        )

        val safetySourceData = safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)

        assertThat(safetySourceData).isEqualTo(secondSafetySourceData)
    }

    @Test
    fun isSafetyCenterEnabled_whenConfigEnabled_andFlagEnabled_returnsTrue() {
        if (!deviceSupportsSafetyCenter()) {
            return
        }

        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SAFETY_CENTER_ENABLED,
                /* value = */ true.toString(),
                /* makeDefault = */ false
            )
        }

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isTrue()
    }

    @Test
    fun isSafetyCenterEnabled_whenConfigEnabled_andFlagDisabled_returnsFalse() {
        if (!deviceSupportsSafetyCenter()) {
            return
        }

        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SAFETY_CENTER_ENABLED,
                /* value = */ false.toString(),
                /* makeDefault = */ false
            )
        }

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_whenConfigDisabled_andFlagEnabled_returnsFalse() {
        if (deviceSupportsSafetyCenter()) {
            return
        }

        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SAFETY_CENTER_ENABLED,
                /* value = */ true.toString(),
                /* makeDefault = */ false
            )
        }

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_whenConfigDisabled_andFlagDisabled_returnsFalse() {
        if (deviceSupportsSafetyCenter()) {
            return
        }

        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SAFETY_CENTER_ENABLED,
                /* value = */ false.toString(),
                /* makeDefault = */ false
            )
        }

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_whenAppDoesNotHoldPermission_methodThrows() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.isSafetyCenterEnabled
        }
    }

    @Test
    fun refreshSafetySources_withoutManageSafetyCenterPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_RESCAN_BUTTON_CLICK)
        }
    }

    @Test
    fun refreshSafetySources_whenReceiverDoesNotHaveSendingPermission_sourceDoesNotSendData() {
        SafetySourceBroadcastReceiver.safetySourceId = CTS_SOURCE_ID
        SafetySourceBroadcastReceiver.safetySourceDataOnRescanClick = safetySourceDataOnRescanClick

        safetyCenterManager.refreshSafetySourcesWithPermission(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceBroadcastReceiver.waitTillOnReceiveComplete(TIMEOUT_SHORT)
        }
        val safetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(safetySourceData).isNull()
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_sourceSendsRescanData() {
        SafetySourceBroadcastReceiver.safetySourceId = CTS_SOURCE_ID
        SafetySourceBroadcastReceiver.safetySourceDataOnRescanClick = safetySourceDataOnRescanClick

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val safetySourceData = safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(safetySourceData).isEqualTo(safetySourceDataOnRescanClick)
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_sourceSendsPageOpenData() {
        SafetySourceBroadcastReceiver.safetySourceId = CTS_SOURCE_ID
        SafetySourceBroadcastReceiver.safetySourceDataOnPageOpen = safetySourceDataOnPageOpen

        safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
            REFRESH_REASON_PAGE_OPEN
        )

        val safetySourceData = safetyCenterManager.getSafetySourceDataWithPermission(CTS_SOURCE_ID)
        assertThat(safetySourceData).isEqualTo(safetySourceDataOnPageOpen)
    }

    @Test
    fun refreshSafetySources_withInvalidRefreshSeason_throwsIllegalArgumentException() {
        SafetySourceBroadcastReceiver.safetySourceId = CTS_SOURCE_ID
        SafetySourceBroadcastReceiver.safetySourceDataOnPageOpen = safetySourceDataOnPageOpen
        SafetySourceBroadcastReceiver.safetySourceDataOnRescanClick = safetySourceDataOnRescanClick

        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(500)
        }
    }

    @Test
    fun getSafetyCenterData_returnsSafetyCenterData() {
        val safetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        // TODO(b/218830137): Assert on content.
        assertThat(safetyCenterData).isNotNull()
    }

    @Test
    fun getSafetyCenterData_whenAppDoesNotHoldPermission_methodThrows() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.safetyCenterData
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledWithSafetyCenterData() {
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener
        )
        val safetyCenterDataFromListener = receiveListenerUpdate()

        // TODO(b/218830137): Assert on content.
        assertThat(safetyCenterDataFromListener).isNotNull()
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerCalledOnSafetySourceData() {
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener
        )
        // Receive initial data.
        receiveListenerUpdate()

        val safetySourceData = SafetySourceData.Builder().build()
        safetyCenterManager.setSafetySourceDataWithPermission(
                CTS_SOURCE_ID,
                safetySourceData,
                EVENT_SOURCE_STATE_CHANGED
        )
        val safetyCenterDataFromListener = receiveListenerUpdate()

        // TODO(b/218830137): Assert on content.
        assertThat(safetyCenterDataFromListener).isNotNull()
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNotCalledOnSafetySourceData() {
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener
        )
        // Receive initial data.
        receiveListenerUpdate()

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)
        val safetySourceData = SafetySourceData.Builder().build()
        safetyCenterManager.setSafetySourceDataWithPermission(
                CTS_SOURCE_ID,
                safetySourceData,
                EVENT_SOURCE_STATE_CHANGED
        )

        assertFailsWith(TimeoutCancellationException::class) {
            receiveListenerUpdate(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_whenAppDoesNotHoldPermission_methodThrows() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(
                directExecutor(), listener
            )
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_whenAppDoesNotHoldPermission_methodThrows() {
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(), listener
        )

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    @Test
    fun dismissSafetyIssue_whenAppDoesNotHoldPermission_methodThrows() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.dismissSafetyIssue("bleh")
        }
    }

    private fun deviceSupportsSafetyCenter() =
        context.resources.getBoolean(
            Resources.getSystem().getIdentifier(
                "config_enableSafetyCenter",
                "bool",
                "android"
            )
        )

    private fun SafetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
        refreshReason: Int
    ) {
        try {
            runWithShellPermissionIdentity({
                refreshSafetySources(refreshReason)
                SafetySourceBroadcastReceiver.waitTillOnReceiveComplete(TIMEOUT_LONG)
            }, SEND_SAFETY_CENTER_UPDATE, MANAGE_SAFETY_CENTER)
        } catch (e: RuntimeException) {
            throw e.cause ?: e
        }
    }

    private fun receiveListenerUpdate(timeout: Duration = TIMEOUT_LONG): SafetyCenterData =
        runBlockingWithTimeout(timeout) {
            listenerChannel.receive()
        }

    private fun <T> runBlockingWithTimeout(
        timeout: Duration = TIMEOUT_LONG,
        block: suspend () -> T
    ) =
        runBlocking {
            withTimeout(timeout.toMillis()) {
                block()
            }
        }

    companion object {
        /** Name of the flag that determines whether SafetyCenter is enabled. */
        private const val PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled"
        private const val CTS_PACKAGE_NAME = "android.safetycenter.cts"
        private const val CTS_BROADCAST_RECEIVER_NAME =
            "android.safetycenter.cts.SafetySourceBroadcastReceiver"
        private val TIMEOUT_LONG: Duration = Duration.ofMillis(5000)
        private val TIMEOUT_SHORT: Duration = Duration.ofMillis(1000)
        private val EVENT_SOURCE_STATE_CHANGED =
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
        private const val CTS_SOURCE_ID = "cts_source_id"
        // TODO(b/217944317): Consider moving the following to a file where they can be used by
        //  other tests.
        private val CTS_SOURCE = SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                .setId(CTS_SOURCE_ID)
                .setPackageName(CTS_PACKAGE_NAME)
                .setTitleResId(R.string.reference)
                .setSummaryResId(R.string.reference)
                .setIntentAction("SafetyCenterManagerTest.INTENT_ACTION")
                .setBroadcastReceiverClassName(CTS_BROADCAST_RECEIVER_NAME)
                .setProfile(SafetySource.PROFILE_PRIMARY)
                .build()
        private val CTS_SOURCE_GROUP = SafetySourcesGroup.Builder()
                .setId("cts_source_group")
                .setTitleResId(R.string.reference)
                .setSummaryResId(R.string.reference)
                .addSafetySource(CTS_SOURCE)
                .build()
        private val CTS_ONLY_CONFIG = SafetyCenterConfig.Builder()
                .addSafetySourcesGroup(CTS_SOURCE_GROUP)
                .build()
    }
}
