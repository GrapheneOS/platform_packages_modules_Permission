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

package com.android.permissioncontroller.hibernation

import android.apphibernation.AppHibernationManager
import android.content.Context
import android.content.Context.APP_HIBERNATION_SERVICE
import android.os.Build
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness
import java.io.File
import org.mockito.Mockito.`when` as whenever

/**
 * Unit tests for [HibernationController].
 */
@RunWith(AndroidJUnit4::class)
class HibernationControllerTest {
    companion object {
        val application = mock(PermissionControllerApplication::class.java)

        const val USER_ID = 0
        const val PACKAGE_NAME_1 = "package_1"
        const val PACKAGE_NAME_2 = "package_2"
    }

    private var mockitoSession: MockitoSession? = null

    @Mock
    lateinit var context: Context
    @Mock
    lateinit var appHibernationManager: AppHibernationManager

    lateinit var filesDir: File

    lateinit var hibernationController: HibernationController

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        mockitoSession = mockitoSession().mockStatic(PermissionControllerApplication::class.java)
            .strictness(Strictness.LENIENT).startMocking()
        whenever(PermissionControllerApplication.get()).thenReturn(application)
        filesDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir()
        whenever(application.filesDir).thenReturn(filesDir)

        doReturn(context).`when`(context).createContextAsUser(any(), anyInt())
        doReturn(appHibernationManager).`when`(context).getSystemService(APP_HIBERNATION_SERVICE)

        hibernationController = HibernationController(context)
    }

    @After
    fun finish() {
        mockitoSession?.finishMocking()
        val logFile = File(filesDir, Constants.LOGS_TO_DUMP_FILE)
        logFile.delete()
    }

    @Test
    fun testHibernateApps_hibernatesAppsForUser() {
        // GIVEN a list of unused apps
        val userPackages = listOf(makePackageInfo(PACKAGE_NAME_1), makePackageInfo(PACKAGE_NAME_2))
        val map = mapOf(UserHandle.of(USER_ID) to userPackages)
        // WHEN the controller hibernates the apps
        val hibernatedApps = hibernationController.hibernateApps(map)

        // THEN the apps are hibernated for the user
        for (i in hibernatedApps.indices) {
            assertEquals(userPackages[i].packageName, hibernatedApps[i].first)
            verify(appHibernationManager).setHibernatingForUser(userPackages[i].packageName, true)
        }
    }

    private fun makePackageInfo(packageName: String): LightPackageInfo {
        return LightPackageInfo(
            packageName,
            emptyList(),
            emptyList(),
            emptyList(),
            0 /* uid */,
            Build.VERSION_CODES.CUR_DEVELOPMENT,
            false /* isInstantApp */,
            true /* enabled */,
            0 /* appFlags */,
            0 /* firstInstallTime */)
    }
}
