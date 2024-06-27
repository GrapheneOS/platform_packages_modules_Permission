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

package com.android.permissioncontroller.tests.mocking.hibernation

import android.app.job.JobScheduler
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.preference.PreferenceManager
import android.provider.DeviceConfig
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.hibernation.HibernationBroadcastReceiver
import com.android.permissioncontroller.hibernation.ONE_DAY_MS
import com.android.permissioncontroller.hibernation.PREF_KEY_SYSTEM_TIME_SNAPSHOT
import com.android.permissioncontroller.hibernation.PREF_KEY_ELAPSED_REALTIME_SNAPSHOT
import com.android.permissioncontroller.hibernation.PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING
import com.android.permissioncontroller.hibernation.SNAPSHOT_UNINITIALIZED
import com.android.permissioncontroller.hibernation.getStartTimeOfUnusedAppTracking
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness
import java.io.File

/** Unit tests for [HibernationPolicy]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S, codeName = "S")
class HibernationPolicyTest {

    companion object {
        private val application = Mockito.mock(PermissionControllerApplication::class.java)
        private const val USER_SETUP_INCOMPLETE = 0
        private const val USER_SETUP_COMPLETE = 1
    }

    @Mock lateinit var jobScheduler: JobScheduler
    @Mock lateinit var context: Context
    @Mock lateinit var userManager: UserManager
    @Mock lateinit var contentResolver: ContentResolver

    private lateinit var realContext: Context
    private lateinit var receiver: HibernationBroadcastReceiver
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var mockitoSession: MockitoSession
    private lateinit var filesDir: File

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .mockStatic(DeviceConfig::class.java)
                .mockStatic(Settings.Secure::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        `when`(PermissionControllerApplication.get()).thenReturn(application)
        `when`(Settings.Secure.getInt(any(), eq(Settings.Secure.USER_SETUP_COMPLETE), anyInt()))
                .thenReturn(USER_SETUP_COMPLETE)
        `when`(Settings.Secure.getUriFor(any())).thenReturn(Mockito.mock(Uri::class.java))

        realContext = ApplicationProvider.getApplicationContext()
        sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(realContext.applicationContext)

        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)
        `when`(context.getSystemService(UserManager::class.java)).thenReturn(userManager)

        filesDir = realContext.cacheDir
        `when`(application.filesDir).thenReturn(filesDir)
        `when`(context.getSystemService(JobScheduler::class.java)).thenReturn(jobScheduler)
        `when`(context.contentResolver).thenReturn(contentResolver)
        `when`(jobScheduler.schedule(Mockito.any())).thenReturn(JobScheduler.RESULT_SUCCESS)

        receiver = HibernationBroadcastReceiver()
    }

    @After
    fun cleanup() {
        mockitoSession.finishMocking()
        sharedPreferences.edit().clear().commit()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()
    }

    @Test
    fun onReceive_userSetupIncomplete_doesNotInitializeStartTime() {
        `when`(Settings.Secure.getInt(any(), eq(Settings.Secure.USER_SETUP_COMPLETE), anyInt()))
                .thenReturn(USER_SETUP_INCOMPLETE)

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val startTimeOfUnusedAppTracking =
                sharedPreferences.getLong(
                        PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING,
                        SNAPSHOT_UNINITIALIZED
                )
        assertThat(startTimeOfUnusedAppTracking).isEqualTo(SNAPSHOT_UNINITIALIZED)
    }

    @Test
    fun onReceive_userSetupCompletes_initializesStartTime() {
        `when`(Settings.Secure.getInt(any(), eq(Settings.Secure.USER_SETUP_COMPLETE), anyInt()))
                .thenReturn(USER_SETUP_INCOMPLETE)

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val contentObserverCaptor = ArgumentCaptor.forClass(ContentObserver::class.java)
        val uri = Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE)
        verify(contentResolver).registerContentObserver(
                eq(uri),
                anyBoolean(),
                contentObserverCaptor.capture())
        val contentObserver = contentObserverCaptor.value
        `when`(Settings.Secure.getInt(any(), eq(Settings.Secure.USER_SETUP_COMPLETE), anyInt()))
                .thenReturn(USER_SETUP_COMPLETE)
        contentObserver.onChange(/* selfChange= */ false, uri)

        val startTimeOfUnusedAppTracking =
                sharedPreferences.getLong(
                        PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING,
                        SNAPSHOT_UNINITIALIZED
                )
        assertThat(startTimeOfUnusedAppTracking).isNotEqualTo(SNAPSHOT_UNINITIALIZED)
    }

    @Test
    fun onReceive_shouldInitializeAndAdjustStartTimeOfUnusedAppTracking() {
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        val startTimeOfUnusedAppTracking =
            sharedPreferences.getLong(
                PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING,
                SNAPSHOT_UNINITIALIZED
            )
        val systemTimeSnapshot =
            sharedPreferences.getLong(PREF_KEY_SYSTEM_TIME_SNAPSHOT, SNAPSHOT_UNINITIALIZED)
        val realtimeSnapshot =
            sharedPreferences.getLong(PREF_KEY_ELAPSED_REALTIME_SNAPSHOT, SNAPSHOT_UNINITIALIZED)
        val currentTimeMillis = System.currentTimeMillis()
        val currentRealTime = SystemClock.elapsedRealtime()
        assertThat(startTimeOfUnusedAppTracking).isNotEqualTo(SNAPSHOT_UNINITIALIZED)
        assertThat(startTimeOfUnusedAppTracking).isNotEqualTo(currentTimeMillis)
        assertThat(systemTimeSnapshot).isNotEqualTo(SNAPSHOT_UNINITIALIZED)
        assertThat(realtimeSnapshot).isNotEqualTo(SNAPSHOT_UNINITIALIZED)
        assertThat(systemTimeSnapshot).isLessThan(currentTimeMillis)
        assertThat(realtimeSnapshot).isLessThan(currentRealTime)

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        assertAdjustedTime(systemTimeSnapshot, realtimeSnapshot)

        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertAdjustedTime(systemTimeSnapshot, realtimeSnapshot)
    }

    @Test
    fun getStartTimeOfUnusedAppTracking_shouldReturnExpectedValue() {
        assertThat(getStartTimeOfUnusedAppTracking(sharedPreferences))
            .isNotEqualTo(SNAPSHOT_UNINITIALIZED)
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        val systemTimeSnapshot =
            sharedPreferences.getLong(PREF_KEY_SYSTEM_TIME_SNAPSHOT, SNAPSHOT_UNINITIALIZED)
        sharedPreferences
            .edit()
            .putLong(PREF_KEY_SYSTEM_TIME_SNAPSHOT, systemTimeSnapshot - ONE_DAY_MS)
            .apply()
        assertThat(getStartTimeOfUnusedAppTracking(sharedPreferences))
            .isNotEqualTo(systemTimeSnapshot)
    }

    private fun assertAdjustedTime(systemTimeSnapshot: Long, realtimeSnapshot: Long) {
        val newStartTimeOfUnusedAppTracking =
            sharedPreferences.getLong(
                PREF_KEY_START_TIME_OF_UNUSED_APP_TRACKING,
                SNAPSHOT_UNINITIALIZED
            )
        val newSystemTimeSnapshot =
            sharedPreferences.getLong(PREF_KEY_SYSTEM_TIME_SNAPSHOT, SNAPSHOT_UNINITIALIZED)
        val newRealtimeSnapshot =
            sharedPreferences.getLong(PREF_KEY_ELAPSED_REALTIME_SNAPSHOT, SNAPSHOT_UNINITIALIZED)
        assertThat(newStartTimeOfUnusedAppTracking).isNotEqualTo(SNAPSHOT_UNINITIALIZED)
        assertThat(newSystemTimeSnapshot).isNotEqualTo(SNAPSHOT_UNINITIALIZED)
        assertThat(newSystemTimeSnapshot).isGreaterThan(systemTimeSnapshot)
        assertThat(newRealtimeSnapshot).isNotEqualTo(SNAPSHOT_UNINITIALIZED)
        assertThat(newRealtimeSnapshot).isAtLeast(realtimeSnapshot)
    }
}
