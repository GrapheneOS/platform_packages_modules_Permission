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

package com.android.permissioncontroller.permission.service

import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PERMISSIONS
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PermissionDecision
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness
import java.io.File
import java.util.Date

@RunWith(AndroidJUnit4::class)
class PersistedStoragePackageUninstalledReceiverTest {

    companion object {
        val application = Mockito.mock(PermissionControllerApplication::class.java)
    }

    private val retryTimeoutMs = 200L
    private val retryAttempts = 3
    private val musicCalendarGrant = PermissionDecision(
        "package.test.music", "calendar", Date(2020, 0, 1).time, false)

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var packageManager: PackageManager

    @Mock
    lateinit var jobScheduler: JobScheduler

    private lateinit var mockitoSession: MockitoSession
    private lateinit var filesDir: File
    private lateinit var recentPermissionDecisionsStorage: RecentPermissionDecisionsStorage
    private lateinit var receiver: PersistedStoragePackageUninstalledReceiver

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession = ExtendedMockito.mockitoSession()
            .mockStatic(PermissionControllerApplication::class.java)
            .mockStatic(DeviceConfig::class.java)
            .strictness(Strictness.LENIENT).startMocking()
        `when`(PermissionControllerApplication.get()).thenReturn(application)
        val context: Context = ApplicationProvider.getApplicationContext()
        filesDir = context.cacheDir
        `when`(application.filesDir).thenReturn(filesDir)
        `when`(jobScheduler.schedule(any())).thenReturn(JobScheduler.RESULT_SUCCESS)
        `when`(DeviceConfig.getProperty(eq(NAMESPACE_PERMISSIONS), anyString())).thenReturn(null)

        recentPermissionDecisionsStorage = spy(
            RecentPermissionDecisionsStorageImpl(context, jobScheduler))
        receiver = spy(PersistedStoragePackageUninstalledReceiver(recentPermissionDecisionsStorage))
    }

    @After
    fun finish() {
        mockitoSession.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()
    }

    @Test
    fun onReceive_permissionDecisionsNotSupported_doesNothing() {
        setPermissionDecisionsSupported(false)
        val intent = Intent(Intent.ACTION_PACKAGE_DATA_CLEARED)

        receiver.onReceive(context, intent)

        verifyZeroInteractions(recentPermissionDecisionsStorage)
    }

    @Test
    fun onReceive_unsupportedAction_doesNothing() {
        setPermissionDecisionsSupported(true)
        val intent = Intent(Intent.ACTION_PACKAGES_SUSPENDED)

        receiver.onReceive(context, intent)

        verifyZeroInteractions(recentPermissionDecisionsStorage)
    }

    @Test
    fun onReceive_clearAction_removesDecisionsForPackage() {
        runBlocking {
            recentPermissionDecisionsStorage.storePermissionDecision(musicCalendarGrant)
            assertThat(recentPermissionDecisionsStorage.loadPermissionDecisions()).isNotEmpty()
        }
        setPermissionDecisionsSupported(true)
        val intent = Intent(Intent.ACTION_PACKAGE_DATA_CLEARED)
        intent.data = Uri.parse(musicCalendarGrant.packageName)

        receiver.onReceive(context, intent)

        // we don't get a callback for when the storage write operation has succeeded, so we poll
        // for the success result
        var result: List<PermissionDecision>? = null
        for (i in 0..retryAttempts) {
            runBlocking {
                result = recentPermissionDecisionsStorage.loadPermissionDecisions()
            }
            if (result?.isEmpty() == true) {
                break
            }
            runBlocking {
                delay(retryTimeoutMs)
            }
        }
        assertThat(result).isNotNull()
        assertThat(result).isEmpty()
    }

    private fun setPermissionDecisionsSupported(isSupported: Boolean) {
        doReturn(packageManager).`when`(context).packageManager
        doReturn(isSupported).`when`(packageManager)
            .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }
}