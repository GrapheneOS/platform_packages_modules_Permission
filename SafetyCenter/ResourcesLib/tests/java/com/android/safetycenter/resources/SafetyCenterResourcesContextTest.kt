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
import java.lang.IllegalStateException
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyCenterResourcesContextTest {
    private val context: Context = getApplicationContext()

    @Test
    fun init_withValidInputs_returnsTrue() {
        val resourcesContext = createNewResourcesContext()

        val initialized = resourcesContext.init()

        assertThat(initialized).isTrue()
    }

    @Test
    fun init_withWrongAction_returnsFalse() {
        val resourcesContext = createNewResourcesContext(resourcesApkAction = "wrong")

        val initialized = resourcesContext.init()

        assertThat(initialized).isFalse()
    }

    @Test
    fun init_withWrongPath_returnsFalse() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkPath = "/apex/com.android.permission")

        val initialized = resourcesContext.init()

        assertThat(initialized).isFalse()
    }

    @Test
    fun init_withWrongFlags_returnsFalse() {
        val resourcesContext = createNewResourcesContext(flags = PackageManager.MATCH_SYSTEM_ONLY)

        val initialized = resourcesContext.init()

        assertThat(initialized).isFalse()
    }

    @Test
    fun getResourcesApkContext_withValidInputs_returnsResourcesApkContext() {
        val resourcesContext = createNewResourcesContext()

        val resourcesApkContext = resourcesContext.resourcesApkContext

        assertThat(resourcesApkContext.packageName).isEqualTo(RESOURCES_APK_PKG_NAME)
    }

    @Test
    fun getResourcesApkContext_withWrongAction_throws() {
        val resourcesContext = createNewResourcesContext(resourcesApkAction = "wrong")

        assertFailsWith(IllegalStateException::class) { resourcesContext.resourcesApkContext }
    }

    @Test
    fun getResourcesApkContext_withWrongPath_throws() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkPath = "/apex/com.android.permission")

        assertFailsWith(IllegalStateException::class) { resourcesContext.resourcesApkContext }
    }

    @Test
    fun getResourcesApkContext_withWrongFlags_throws() {
        val resourcesContext = createNewResourcesContext(flags = PackageManager.MATCH_SYSTEM_ONLY)

        assertFailsWith(IllegalStateException::class) { resourcesContext.resourcesApkContext }
    }

    @Test
    fun getResources_withValidInputs_returnsResourcesApkContextResources() {
        val resourcesContext = createNewResourcesContext()

        val resources = resourcesContext.resources

        assertThat(resources).isEqualTo(resourcesContext.resourcesApkContext.resources)
    }

    @Test
    fun getResources_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) { resourcesContext.resources }
    }

    @Test
    fun getSafetyCenterConfig_withValidInputs_returnsConfigContent() {
        val resourcesContext = createNewResourcesContext()

        val config = resourcesContext.safetyCenterConfig
        val configContent = config?.bufferedReader().use { it?.readText() }

        assertThat(config).isNotNull()
        assertThat(configContent).isEqualTo(CONFIG_CONTENT)
    }

    @Test
    fun getSafetyCenterConfig_anotherValidConfigName_returnsConfigContent() {
        val resourcesContext = createNewResourcesContext()

        val config = resourcesContext.getSafetyCenterConfig(CONFIG_NAME)
        val configContent = config?.bufferedReader().use { it?.readText() }

        assertThat(config).isNotNull()
        assertThat(configContent).isEqualTo(CONFIG_CONTENT)
    }

    @Test
    fun getSafetyCenterConfig_invalidConfigNameWithFallback_returnsNull() {
        val resourcesContext = createNewResourcesContext(fallback = true)

        assertThat(resourcesContext.getSafetyCenterConfig("wrong")).isNull()
    }

    @Test
    fun getSafetyCenterConfig_invalidConfigNameWithoutFallback_throws() {
        val resourcesContext = createNewResourcesContext(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesContext.getSafetyCenterConfig("wrong")
        }
    }

    @Test
    fun getSafetyCenterConfig_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) { resourcesContext.safetyCenterConfig }
    }

    @Test
    fun getString_validString_returnsString() {
        val resourcesContext = createNewResourcesContext()

        val ok = resourcesContext.getString(android.R.string.ok)

        assertThat(ok).isEqualTo("OK")
    }

    @Test
    fun getString_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesContext.getString(android.R.string.ok)
        }
    }

    @Test
    fun getStringWithFormatArgs_validString_returnsString() {
        val resourcesContext = createNewResourcesContext()

        val ok = resourcesContext.getString(android.R.string.ok, "")

        assertThat(ok).isEqualTo("OK")
    }

    @Test
    fun getStringWithFormatArgs_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesContext.getString(android.R.string.ok, "")
        }
    }

    @Test
    fun getStringByName_validString_returnsString() {
        val resourcesContext = createNewResourcesContext()

        assertThat(resourcesContext.getStringByName("valid_string")).isEqualTo("I exist!")
    }

    @Test
    fun getStringByName_invalidStringWithFallback_returnsEmptyString() {
        val resourcesContext = createNewResourcesContext(fallback = true)

        assertThat(resourcesContext.getStringByName("invalid_string")).isEqualTo("")
    }

    @Test
    fun getStringByName_invalidStringWithoutFallback_throws() {
        val resourcesContext = createNewResourcesContext(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesContext.getStringByName("invalid_string")
        }
    }

    @Test
    fun getStringByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesContext.getStringByName("valid_string")
        }
    }

    @Test
    fun getStringByNameWithFormatArgs_validString_returnsString() {
        val resourcesContext = createNewResourcesContext()

        assertThat(resourcesContext.getStringByName("valid_string", "")).isEqualTo("I exist!")
    }

    @Test
    fun getStringByNameWithFormatArgs_invalidStringWithFallback_returnsEmptyString() {
        val resourcesContext = createNewResourcesContext(fallback = true)

        assertThat(resourcesContext.getStringByName("invalid_string", "")).isEqualTo("")
    }

    @Test
    fun getStringByNameWithFormatArgs_invalidStringWithoutFallback_throws() {
        val resourcesContext = createNewResourcesContext(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesContext.getStringByName("invalid_string", "")
        }
    }

    @Test
    fun getStringByNameWithFormatArgs_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesContext.getStringByName("valid_string", "")
        }
    }

    @Test
    fun getOptionalString_validString_returnsString() {
        val resourcesContext = createNewResourcesContext()

        val ok = resourcesContext.getOptionalString(android.R.string.ok)

        assertThat(ok).isEqualTo("OK")
    }

    @Test
    fun getOptionalString_resourceIdNull_returnsNull() {
        val resourcesContext = createNewResourcesContext()

        val string = resourcesContext.getOptionalString(Resources.ID_NULL)

        assertThat(string).isNull()
    }

    @Test
    fun getOptionalString_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesContext.getOptionalString(android.R.string.ok)
        }
    }

    @Test
    fun getOptionalStringByName_validString_returnsString() {
        val resourcesContext = createNewResourcesContext()

        assertThat(resourcesContext.getOptionalStringByName("valid_string")).isEqualTo("I exist!")
    }

    @Test
    fun getOptionalStringByName_invalidStringWithFallback_returnsNull() {
        val resourcesContext = createNewResourcesContext(fallback = true)

        assertThat(resourcesContext.getOptionalStringByName("invalid_string")).isNull()
    }

    @Test
    fun getOptionalStringByName_invalidStringWithoutFallback_returnsNull() {
        val resourcesContext = createNewResourcesContext(fallback = false)

        assertThat(resourcesContext.getOptionalStringByName("invalid_string")).isNull()
    }

    @Test
    fun getOptionalStringByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesContext.getOptionalStringByName("valid_string")
        }
    }

    @Test
    fun getDrawableByName_validDrawable_returnsDrawable() {
        val resourcesContext = createNewResourcesContext()

        assertThat(resourcesContext.getDrawableByName("valid_drawable", context.theme)).isNotNull()
    }

    @Test
    fun getDrawableByName_invalidDrawableWithFallback_returnsNull() {
        val resourcesContext = createNewResourcesContext(fallback = true)

        assertThat(resourcesContext.getDrawableByName("invalid_drawable", context.theme)).isNull()
    }

    @Test
    fun getDrawableByName_invalidDrawableWithoutFallback_throws() {
        val resourcesContext = createNewResourcesContext(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesContext.getDrawableByName("invalid_drawable", context.theme)
        }
    }

    @Test
    fun getDrawableByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesContext.getDrawableByName("valid_drawable", context.theme)
        }
    }

    @Test
    fun getIconByDrawableName_validDrawable_returnsIcon() {
        val resourcesContext = createNewResourcesContext()

        assertThat(resourcesContext.getIconByDrawableName("valid_drawable")).isNotNull()
    }

    @Test
    fun getIconByDrawableName_invalidDrawableWithFallback_returnsNull() {
        val resourcesContext = createNewResourcesContext(fallback = true)

        assertThat(resourcesContext.getIconByDrawableName("invalid_drawable")).isNull()
    }

    @Test
    fun getIconByDrawableName_invalidDrawableWithoutFallback_throws() {
        val resourcesContext = createNewResourcesContext(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesContext.getIconByDrawableName("invalid_drawable")
        }
    }

    @Test
    fun getIconByDrawableByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesContext.getIconByDrawableName("valid_drawable")
        }
    }

    @Test
    fun getColorByName_validColor_returnsColor() {
        val resourcesContext = createNewResourcesContext()

        assertThat(resourcesContext.getColorByName("valid_color")).isNotNull()
    }

    @Test
    fun getColorByName_invalidColorWithFallback_returnsNull() {
        val resourcesContext = createNewResourcesContext(fallback = true)

        assertThat(resourcesContext.getColorByName("invalid_color")).isNull()
    }

    @Test
    fun getColorByName_invalidColorWithoutFallback_throws() {
        val resourcesContext = createNewResourcesContext(fallback = false)

        assertFailsWith(Resources.NotFoundException::class) {
            resourcesContext.getColorByName("invalid_color")
        }
    }

    @Test
    fun getColorByName_nullContext_throwsRegardlessOfFallback() {
        val resourcesContext =
            createNewResourcesContext(resourcesApkAction = "wrong", fallback = true)

        assertFailsWith(IllegalStateException::class) {
            resourcesContext.getColorByName("valid_color")
        }
    }

    private fun createNewResourcesContext(
        resourcesApkAction: String = RESOURCES_APK_ACTION,
        resourcesApkPath: String = "",
        flags: Int = 0,
        fallback: Boolean = false
    ) = SafetyCenterResourcesContext(context, resourcesApkAction, resourcesApkPath, flags, fallback)

    companion object {
        const val RESOURCES_APK_ACTION =
            "com.android.safetycenter.tests.intent.action.SAFETY_CENTER_TEST_RESOURCES_APK"
        const val RESOURCES_APK_PKG_NAME =
            "com.android.safetycenter.tests.config.safetycenterresourceslibtestresources"
        const val CONFIG_NAME = "test"
        const val CONFIG_CONTENT = "TEST"
    }
}
