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
import android.content.res.Resources
import android.safetycenter.SafetySourceData
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY
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

    /**
     * ID of a [SafetySourcesGroup] provided by [COMPLEX_CONFIG], containing sources:
     * [DYNAMIC_BAREBONE_ID], [DYNAMIC_OTHER_PACKAGE_ID], [DYNAMIC_ALL_OPTIONAL_ID],
     * [DYNAMIC_DISABLED_ID], [DYNAMIC_HIDDEN_ID], [DYNAMIC_HIDDEN_WITH_SEARCH_ID].
     */
    const val DYNAMIC_GROUP_ID = "dynamic"

    /**
     * ID of a [SafetySourcesGroup] provided by [COMPLEX_CONFIG], containing sources:
     * [STATIC_BAREBONE_ID], [STATIC_ALL_OPTIONAL_ID].
     */
    const val STATIC_GROUP_ID = "static"

    /**
     * ID of a [SafetySourcesGroup] provided by [COMPLEX_CONFIG], containing sources:
     * [ISSUE_ONLY_BAREBONE_ID], [ISSUE_ONLY_ALL_OPTIONAL_ID].
     */
    const val ISSUE_ONLY_GROUP_ID = "issue_only"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a dynamic, primary profile only, visible
     * source for which only the required fields are set.
     */
    const val DYNAMIC_BAREBONE_ID = "dynamic_barebone"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a dynamic, primary profile only,
     * disabled by default source belonging to the [OTHER_PACKAGE_NAME] package for which only the
     * required fields are set
     */
    const val DYNAMIC_OTHER_PACKAGE_ID = "dynamic_other_package"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a dynamic, primary profile only,
     * disabled by default source for which all the required and optional fields are set. Notably,
     * this includes the refresh on page open flag and a max severity level of recommendation.
     */
    const val DYNAMIC_ALL_OPTIONAL_ID = "dynamic_all_optional"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a dynamic, primary profile only,
     * disabled by default source for which only the required fields are set.
     */
    const val DYNAMIC_DISABLED_ID = "dynamic_disabled"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a dynamic, primary profile only, hidden
     * by default source for which only the required fields are set.
     */
    const val DYNAMIC_HIDDEN_ID = "dynamic_hidden"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a dynamic, primary profile only, hidden
     * by default source for which all the required and optional fields are set.
     */
    const val DYNAMIC_HIDDEN_WITH_SEARCH_ID = "dynamic_hidden_with_search"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a static, primary profile only source
     * for which only the required fields are set.
     */
    const val STATIC_BAREBONE_ID = "static_barebone"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a static, primary profile only source
     * for which all the required and optional fields are set.
     */
    const val STATIC_ALL_OPTIONAL_ID = "static_all_optional"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is an issue-only, primary profile only
     * source for which only the required fields are set.
     */
    const val ISSUE_ONLY_BAREBONE_ID = "issue_only_barebone"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is an issue-only, primary profile only
     * source for which all the required and optional fields are set. Notably, this includes the
     * refresh on page open flag and a max severity level of recommendation.
     */
    const val ISSUE_ONLY_ALL_OPTIONAL_ID = "issue_only_all_optional"

    /** Package name the [DYNAMIC_OTHER_PACKAGE_ID] source used in [COMPLEX_CONFIG]. */
    const val OTHER_PACKAGE_NAME = "other_package_name"

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
    val STATIC_SOURCE_1 =
        staticSafetySourceBuilder("cts_static_source_id_1")
            .setTitleResId(android.R.string.dialog_alert_title)
            .setSummaryResId(android.R.string.autofill)
            .build()

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

    /**
     * A complex [SafetyCenterConfig] exploring different combinations of valid sources and groups.
     */
    val COMPLEX_CONFIG =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(DYNAMIC_GROUP_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build())
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .setMaxSeverityLevel(SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION)
                            .setSearchTermsResId(android.R.string.ok)
                            .setLoggingAllowed(false)
                            .build())
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_DISABLED_ID)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build())
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_HIDDEN_ID)
                            .setTitleResId(Resources.ID_NULL)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build())
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_HIDDEN_WITH_SEARCH_ID)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .setSearchTermsResId(android.R.string.ok)
                            .build())
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_OTHER_PACKAGE_ID)
                            .setPackageName(OTHER_PACKAGE_NAME)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build())
                    .build())
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(STATIC_GROUP_ID)
                    .setSummaryResId(Resources.ID_NULL)
                    .addSafetySource(
                        staticSafetySourceBuilder(STATIC_BAREBONE_ID)
                            .setSummaryResId(Resources.ID_NULL)
                            .build())
                    .addSafetySource(
                        staticSafetySourceBuilder(STATIC_ALL_OPTIONAL_ID)
                            .setSearchTermsResId(android.R.string.ok)
                            .build())
                    .build())
            .addSafetySourcesGroup(
                SafetySourcesGroup.Builder()
                    .setId(ISSUE_ONLY_GROUP_ID)
                    .addSafetySource(
                        issueOnlySafetySourceBuilder(ISSUE_ONLY_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build())
                    .addSafetySource(
                        issueOnlySafetySourceBuilder(ISSUE_ONLY_ALL_OPTIONAL_ID)
                            .setMaxSeverityLevel(SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION)
                            .setLoggingAllowed(false)
                            .build())
                    .build())
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
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)
            .setIntentAction(ACTION_SAFETY_CENTER)
            .setProfile(SafetySource.PROFILE_PRIMARY)

    private fun issueOnlySafetySourceBuilder(id: String) =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_ISSUE_ONLY)
            .setId(id)
            .setPackageName(CTS_PACKAGE_NAME)
            .setProfile(SafetySource.PROFILE_PRIMARY)
            .setRefreshOnPageOpenAllowed(true)

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
