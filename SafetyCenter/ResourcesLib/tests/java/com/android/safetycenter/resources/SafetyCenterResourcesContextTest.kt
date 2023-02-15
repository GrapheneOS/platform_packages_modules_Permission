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

package com.android.safetycenter.resources

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyCenterResourcesContextTest {
    private val context: Context = getApplicationContext()
    private val theme: Resources.Theme? = context.theme

    @Test
    fun validDataWithValidInputs() {
        val safetyCenterResourcesContext =
            SafetyCenterResourcesContext(context, RESOURCES_APK_ACTION, null, CONFIG_NAME, 0, false)
        assertThat(safetyCenterResourcesContext.resourcesApkPkgName)
            .isEqualTo(RESOURCES_APK_PKG_NAME)
        val configContent =
            safetyCenterResourcesContext.safetyCenterConfig?.bufferedReader().use { it?.readText() }
        assertThat(configContent).isEqualTo(CONFIG_CONTENT)
        assertThat(safetyCenterResourcesContext.assets).isNotNull()
        assertThat(safetyCenterResourcesContext.resources).isNotNull()
        assertThat(safetyCenterResourcesContext.theme).isNotNull()
    }

    @Test
    fun nullDataWithWrongAction() {
        val safetyCenterResourcesContext = createNewResourcesContext(resourcesApkAction = "wrong")
        assertThat(safetyCenterResourcesContext.resourcesApkPkgName).isNull()
        assertThat(safetyCenterResourcesContext.safetyCenterConfig).isNull()
        assertThat(safetyCenterResourcesContext.assets).isNull()
        assertThat(safetyCenterResourcesContext.resources).isNull()
        assertThat(safetyCenterResourcesContext.theme).isNull()
    }

    @Test
    fun nullDataWithWrongPath() {
        val safetyCenterResourcesContext =
            createNewResourcesContext(resourcesApkPath = "/apex/com.android.permission")
        assertThat(safetyCenterResourcesContext.resourcesApkPkgName).isNull()
        assertThat(safetyCenterResourcesContext.safetyCenterConfig).isNull()
        assertThat(safetyCenterResourcesContext.assets).isNull()
        assertThat(safetyCenterResourcesContext.resources).isNull()
        assertThat(safetyCenterResourcesContext.theme).isNull()
    }

    @Test
    fun nullDataWithWrongFlag() {
        val safetyCenterResourcesContext =
            createNewResourcesContext(flags = PackageManager.MATCH_SYSTEM_ONLY)
        assertThat(safetyCenterResourcesContext.resourcesApkPkgName).isNull()
        assertThat(safetyCenterResourcesContext.safetyCenterConfig).isNull()
        assertThat(safetyCenterResourcesContext.assets).isNull()
        assertThat(safetyCenterResourcesContext.resources).isNull()
        assertThat(safetyCenterResourcesContext.theme).isNull()
    }

    @Test
    fun nullConfigWithWrongConfigName() {
        val safetyCenterResourcesContext = createNewResourcesContext(configName = "wrong")
        assertThat(safetyCenterResourcesContext.resourcesApkPkgName).isNotNull()
        assertThat(safetyCenterResourcesContext.safetyCenterConfig).isNull()
        assertThat(safetyCenterResourcesContext.assets).isNotNull()
        assertThat(safetyCenterResourcesContext.resources).isNotNull()
        assertThat(safetyCenterResourcesContext.theme).isNotNull()
    }

    @Test
    fun getStringByName_withFallback_emptyStringForInvalidId() {
        val safetyCenterResourcesContext =
            createNewResourcesContext(fallbackIfResourceNotFound = true)
        assertThat(safetyCenterResourcesContext.getStringByName("valid_string"))
            .isEqualTo("I exist!")
        assertThat(safetyCenterResourcesContext.getStringByName("invalid_string")).isEqualTo("")
    }

    @Test
    fun getStringByName_withoutFallback_crashesForInvalidId() {
        val safetyCenterResourcesContext =
            createNewResourcesContext(fallbackIfResourceNotFound = false)
        assertThat(safetyCenterResourcesContext.getStringByName("valid_string"))
            .isEqualTo("I exist!")
        assertFailsWith(Resources.NotFoundException::class) {
            safetyCenterResourcesContext.getStringByName("invalid_string")
        }
    }

    @Test
    fun getDrawableByName_withFallback_nullResourceForInvalidId() {
        val safetyCenterResourcesContext =
            createNewResourcesContext(fallbackIfResourceNotFound = true)
        assertThat(safetyCenterResourcesContext.getDrawableByName("valid_drawable", theme))
            .isNotNull()
        assertThat(safetyCenterResourcesContext.getDrawableByName("invalid_drawable", theme))
            .isNull()
    }

    @Test
    fun getDrawableByName_withoutFallback_crashesForInvalidId() {
        val safetyCenterResourcesContext =
            createNewResourcesContext(fallbackIfResourceNotFound = false)
        assertThat(safetyCenterResourcesContext.getDrawableByName("valid_drawable", theme))
            .isNotNull()
        assertFailsWith(Resources.NotFoundException::class) {
            safetyCenterResourcesContext.getDrawableByName("invalid_drawable", theme)
        }
    }

    private fun createNewResourcesContext(
        resourcesApkAction: String = RESOURCES_APK_ACTION,
        resourcesApkPath: String? = null,
        configName: String = CONFIG_NAME,
        flags: Int = 0,
        fallbackIfResourceNotFound: Boolean = false
    ) =
        SafetyCenterResourcesContext(
            context,
            resourcesApkAction,
            resourcesApkPath,
            configName,
            flags,
            fallbackIfResourceNotFound
        )

    companion object {
        const val RESOURCES_APK_ACTION =
            "com.android.safetycenter.tests.intent.action.SAFETY_CENTER_TEST_RESOURCES_APK"
        const val RESOURCES_APK_PKG_NAME =
            "com.android.safetycenter.tests.config.safetycenterresourceslibtestresources"
        const val CONFIG_NAME = "test"
        const val CONFIG_CONTENT = "TEST"
    }
}
