/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.tests.mocking.privacysources

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceStatus
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_REFRESH_REQUESTED
import com.android.permissioncontroller.privacysources.v34.HealthConnectPrivacySource
import com.android.permissioncontroller.privacysources.v34.HealthConnectPrivacySource.Companion.HEALTH_CONNECT_SOURCE_ID
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/** Tests for [HealthConnectPrivacySource]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class HealthConnectPrivacySourceTest {

    private lateinit var mockitoSession: MockitoSession
    private lateinit var healthConnectPrivacySource: HealthConnectPrivacySource
    private lateinit var context: Context
    @Mock lateinit var mockSafetyCenterManager: SafetyCenterManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig::class.java)
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(Utils::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        whenever(
                Utils.getSystemServiceSafe(
                    any(ContextWrapper::class.java),
                    eq(SafetyCenterManager::class.java)
                )
            )
            .thenReturn(mockSafetyCenterManager)

        healthConnectPrivacySource = HealthConnectPrivacySource()
    }

    @After
    fun cleanup() {
        mockitoSession.finishMocking()
    }

    @Test
    fun safetyCenterEnabledChanged_enabled_doesNothing() {
        healthConnectPrivacySource.safetyCenterEnabledChanged(context, true)

        verifyZeroInteractions(mockSafetyCenterManager)
    }

    @Test
    fun safetyCenterEnabledChanged_disabled_doesNothing() {
        healthConnectPrivacySource.safetyCenterEnabledChanged(context, false)

        verifyZeroInteractions(mockSafetyCenterManager)
    }

    @Test
    fun rescanAndPushSafetyCenterData_healthPermissionUIEnabled_setsDataWithStatus() {
        val refreshIntent =
            Intent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, REFRESH_ID)

        healthConnectPrivacySource.rescanAndPushSafetyCenterData(
            context,
            refreshIntent,
            EVENT_REFRESH_REQUESTED
        )

        val expectedSafetySourceData =
            if (Utils.isHealthPermissionUiEnabled()) {
                SafetySourceData.Builder()
                    .setStatus(
                        SafetySourceStatus.Builder(
                                HEALTH_CONNECT_TITLE,
                                HEALTH_CONNECT_SUMMARY,
                                SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
                            )
                            .setPendingIntent(
                                PendingIntent.getActivity(
                                    context,
                                    /* requestCode= */ 0,
                                    Intent(HEALTH_CONNECT_INTENT_ACTION),
                                    FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                                )
                            )
                            .build()
                    )
                    .build()
            } else {
                null
            }

        val expectedSafetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(REFRESH_ID)
                .build()
        verify(mockSafetyCenterManager)
            .setSafetySourceData(
                HEALTH_CONNECT_SOURCE_ID,
                expectedSafetySourceData,
                expectedSafetyEvent
            )
    }

    @Test
    fun rescanAndPushSafetyCenterData_healthPermissionUIDisabled_setsNullData() {
        setHealthPermissionUIEnabled(false)
        val refreshIntent =
            Intent(ACTION_REFRESH_SAFETY_SOURCES)
                .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID, REFRESH_ID)

        healthConnectPrivacySource.rescanAndPushSafetyCenterData(
            context,
            refreshIntent,
            EVENT_REFRESH_REQUESTED
        )

        val expectedSafetyEvent =
            SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(REFRESH_ID)
                .build()
        verify(mockSafetyCenterManager)
            .setSafetySourceData(HEALTH_CONNECT_SOURCE_ID, null, expectedSafetyEvent)
    }

    /** Companion object for [HealthConnectPrivacySourceTest]. */
    companion object {
        // Real context, used in order to avoid mocking resources.
        const val HEALTH_CONNECT_TITLE: String = "Health Connect"
        const val HEALTH_CONNECT_SUMMARY: String = "App permissions and data management"
        const val REFRESH_ID: String = "refresh_id"
        const val HEALTH_CONNECT_INTENT_ACTION =
            "android.health.connect.action.HEALTH_HOME_SETTINGS"

        /** Sets the value for the Health Permission feature [DeviceConfig] property. */
        private fun setHealthPermissionUIEnabled(enabled: Boolean) {
            whenever(
                    DeviceConfig.getBoolean(
                        eq(NAMESPACE_PRIVACY),
                        eq("health_permission_ui_enabled"),
                        anyBoolean()
                    )
                )
                .thenReturn(enabled)
        }
    }
}
