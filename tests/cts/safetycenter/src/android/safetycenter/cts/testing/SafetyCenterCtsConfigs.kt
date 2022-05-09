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
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_STATIC
import android.safetycenter.config.SafetySourcesGroup

/**
 * A class that provides [SafetyCenterConfig] objects and associated constants to facilitate setting
 * up safety sources for testing.
 */
object SafetyCenterCtsConfigs {
    private const val CTS_PACKAGE_NAME = "android.safetycenter.cts"

    /**
     * ID of the only source provided in [SINGLE_SOURCE_CONFIG], [SEVERITY_ZERO_CONFIG] and
     * [NO_PAGE_OPEN_CONFIG].
     */
    const val SINGLE_SOURCE_ID = "cts_single_source_id"

    /**
     * ID of the only [SafetySourcesGroup] provided by [SINGLE_SOURCE_CONFIG],
     * [SEVERITY_ZERO_CONFIG] and [NO_PAGE_OPEN_CONFIG].
     */
    const val SINGLE_SOURCE_GROUP_ID = "cts_single_source_group_id"

    /**
     * A simple [SafetyCenterConfig] for CTS tests with a single source of id [SINGLE_SOURCE_ID].
     */
    val SINGLE_SOURCE_CONFIG = singleSourceConfig(dynamicSafetySource(SINGLE_SOURCE_ID))

    /** A simple [SafetyCenterConfig] for CTS tests with a source max severity level of 0. */
    val SEVERITY_ZERO_CONFIG =
        singleSourceConfig(
            dynamicSafetySourceBuilder(SINGLE_SOURCE_ID).setMaxSeverityLevel(0).build())

    /**
     * A simple [SafetyCenterConfig] for CTS tests with a source that does not support refresh on
     * page open.
     */
    val NO_PAGE_OPEN_CONFIG =
        singleSourceConfig(
            dynamicSafetySourceBuilder(SINGLE_SOURCE_ID).setRefreshOnPageOpenAllowed(false).build())

    /** ID of a source provided by [MULTIPLE_SOURCES_CONFIG]. */
    const val SOURCE_ID_1 = "cts_source_id_1"

    /** ID of a source provided by [MULTIPLE_SOURCES_CONFIG]. */
    const val SOURCE_ID_2 = "cts_source_id_2"

    /** ID of a source provided by [MULTIPLE_SOURCES_CONFIG]. */
    const val SOURCE_ID_3 = "cts_source_id_3"

    /**
     * ID of a [SafetySourcesGroup] provided by [MULTIPLE_SOURCES_CONFIG], containing two sources of
     * ids [SOURCE_ID_1] and [SOURCE_ID_2].
     */
    const val MULTIPLE_SOURCES_GROUP_ID_1 = "cts_multiple_sources_group_id_1"

    /**
     * ID of a [SafetySourcesGroup] provided by [MULTIPLE_SOURCES_CONFIG], containing a single
     * source of id [SOURCE_ID_3].
     */
    const val MULTIPLE_SOURCES_GROUP_ID_2 = "cts_multiple_sources_group_id_2"

    /** A simple [SafetyCenterConfig] for CTS tests with multiple sources. */
    val MULTIPLE_SOURCES_CONFIG =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_1)
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_1))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_2))
                    .build())
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_2)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(SOURCE_ID_3)
                            .setRefreshOnPageOpenAllowed(false)
                            .build())
                    .build())
            .build()

    /** Source provided by [STATIC_SOURCES_CONFIG]. */
    val STATIC_SOURCE_1 = staticSafetySourceBuilder("cts_static_source_id_1").build()

    /** Source provided by [STATIC_SOURCES_CONFIG]. */
    val STATIC_SOURCE_2 =
        staticSafetySourceBuilder("cts_static_source_id_2")
            .setTitleResId(android.R.string.copyUrl)
            .setSummaryResId(android.R.string.cut)
            .build()

    /**
     * Source group provided by [STATIC_SOURCES_CONFIG] containing a single source of id
     * [STATIC_SOURCE_1].
     */
    val STATIC_SOURCE_GROUP_1 =
        SafetySourcesGroup.Builder()
            .setId("cts_static_sources_group_id_1")
            .setTitleResId(android.R.string.paste)
            .addSafetySource(STATIC_SOURCE_1)
            .build()

    /**
     * Source group provided by [STATIC_SOURCES_CONFIG] containing a single source of id
     * [STATIC_SOURCE_2].
     */
    val STATIC_SOURCE_GROUP_2 =
        SafetySourcesGroup.Builder()
            .setId("cts_static_sources_group_id_2")
            .setTitleResId(android.R.string.copy)
            .addSafetySource(STATIC_SOURCE_2)
            .build()

    /** A simple [SafetyCenterConfig] for CTS tests with static sources. */
    val STATIC_SOURCES_CONFIG =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(STATIC_SOURCE_GROUP_1)
            .addSafetySourcesGroup(STATIC_SOURCE_GROUP_2)
            .build()

    private fun dynamicSafetySourceBuilder(id: String) =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_DYNAMIC)
            .setId(id)
            .setPackageName(CTS_PACKAGE_NAME)
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)
            .setIntentAction(ACTION_SAFETY_CENTER)
            .setProfile(SafetySource.PROFILE_PRIMARY)
            .setRefreshOnPageOpenAllowed(true)

    private fun dynamicSafetySource(id: String) = dynamicSafetySourceBuilder(id).build()

    private fun staticSafetySourceBuilder(id: String) =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_STATIC)
            .setId(id)
            .setTitleResId(android.R.string.dialog_alert_title)
            .setSummaryResId(android.R.string.autofill)
            .setIntentAction(ACTION_SAFETY_CENTER)
            .setProfile(SafetySource.PROFILE_PRIMARY)

    private fun safetySourcesGroupBuilder(id: String) =
        SafetySourcesGroup.Builder()
            .setId(id)
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)

    private fun singleSourceConfig(safetySource: SafetySource) =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(SINGLE_SOURCE_GROUP_ID)
                    .addSafetySource(safetySource)
                    .build())
            .build()
}
