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

package com.android.safetycenter.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.safetycenter.tests.config.safetycenterconfig.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigValidTest {
    private val context: Context = getApplicationContext()

    @Test
    fun validConfig_matchesExpected() {
        val inputStream = context.resources.openRawResource(R.raw.config_valid)
        val expected = SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(SafetySourcesGroup.Builder()
                .setId("security")
                .setTitleResId(R.string.reference)
                .setSummaryResId(R.string.reference)
                .addSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId("app_security")
                    .setPackageName("package")
                    .setTitleResId(R.string.reference)
                    .setSummaryResId(R.string.reference)
                    .setIntentAction("intent")
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setSearchTermsResId(R.string.reference)
                    .setBroadcastReceiverClassName("broadcast")
                    .setDisallowLogging(true)
                    .setAllowRefreshOnPageOpen(true)
                    .build())
                .addSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_INTERNAL)
                    .setId("device_security")
                    .build())
                .addSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                    .setId("lockscreen")
                    .setTitleResId(R.string.reference)
                    .setSummaryResId(R.string.reference)
                    .setIntentAction("intent")
                    .setProfile(SafetySource.PROFILE_PRIMARY)
                    .setSearchTermsResId(R.string.reference)
                    .build())
                .build())
            .addSafetySourcesGroup(SafetySourcesGroup.Builder()
                .setId("privacy")
                .setTitleResId(R.string.reference)
                .setSummaryResId(R.string.reference)
                .addSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_INTERNAL)
                    .setId("privacy_dashboard")
                    .build())
                .addSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                    .setId("permissions")
                    .setTitleResId(R.string.reference)
                    .setSummaryResId(R.string.reference)
                    .setIntentAction("intent")
                    .setProfile(SafetySource.PROFILE_ALL)
                    .build())
                .addSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId("privacy_controls")
                    .setPackageName("package")
                    .setTitleResId(R.string.reference)
                    .setSummaryResId(R.string.reference)
                    .setIntentAction("intent")
                    .setProfile(SafetySource.PROFILE_ALL)
                    .build())
                .build())
            .addStaticSafetySourcesGroup(StaticSafetySourcesGroup.Builder()
                .setId("oem")
                .setTitleResId(R.string.reference)
                .addStaticSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                    .setId("oem_setting_1")
                    .setTitleResId(R.string.reference)
                    .setSummaryResId(R.string.reference)
                    .setIntentAction("intent")
                    .setProfile(SafetySource.PROFILE_PRIMARY)
                    .build())
                .addStaticSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                    .setId("oem_setting_2")
                    .setTitleResId(R.string.reference)
                    .setSummaryResId(R.string.reference)
                    .setIntentAction("intent")
                    .setProfile(SafetySource.PROFILE_PRIMARY)
                    .setSearchTermsResId(R.string.reference)
                    .build())
                .build())
            .addStaticSafetySourcesGroup(StaticSafetySourcesGroup.Builder()
                .setId("advanced")
                .setTitleResId(R.string.reference)
                .addStaticSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                    .setId("advanced_security")
                    .setTitleResId(R.string.reference)
                    .setSummaryResId(R.string.reference)
                    .setIntentAction("intent")
                    .setProfile(SafetySource.PROFILE_PRIMARY)
                    .build())
                .addStaticSafetySource(SafetySource.Builder()
                    .setType(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                    .setId("advanced_privacy")
                    .setTitleResId(R.string.reference)
                    .setSummaryResId(R.string.reference)
                    .setIntentAction("intent")
                    .setProfile(SafetySource.PROFILE_PRIMARY)
                    .setSearchTermsResId(R.string.reference)
                    .build())
                .build())
            .build()
        assertEquals(expected, Parser.parse(inputStream, context.packageName, context.resources))
    }
}
