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

package android.safetycenter.cts.testing

import android.content.Intent.ACTION_SAFETY_CENTER
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC
import android.safetycenter.config.SafetySourcesGroup

/**
 * A class that provides a [SafetyCenterConfig] object and associated constants to facilitate
 * setting up a safety sources for testing.
 */
object SafetyCenterCtsConfig {
    private const val CTS_PACKAGE_NAME = "android.safetycenter.cts"

    /** ID of the only source provided by [CTS_CONFIG]. */
    const val CTS_SOURCE_ID = "cts_source_id"

    /** ID of the only [SafetySourcesGroup] provided by [CTS_CONFIG]. */
    const val CTS_SOURCE_GROUP_ID = "cts_source_group"

    private val CTS_SOURCE =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_DYNAMIC)
            .setId(CTS_SOURCE_ID)
            .setPackageName(CTS_PACKAGE_NAME)
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)
            .setIntentAction(ACTION_SAFETY_CENTER)
            .setProfile(SafetySource.PROFILE_PRIMARY)
            .build()

    private val CTS_SOURCE_GROUP =
        SafetySourcesGroup.Builder()
            .setId(CTS_SOURCE_GROUP_ID)
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)
            .addSafetySource(CTS_SOURCE)
            .build()

    /** A simple [SafetyCenterConfig] for CTS tests with a single source of id [CTS_SOURCE_ID]. */
    val CTS_CONFIG = SafetyCenterConfig.Builder().addSafetySourcesGroup(CTS_SOURCE_GROUP).build()
}
