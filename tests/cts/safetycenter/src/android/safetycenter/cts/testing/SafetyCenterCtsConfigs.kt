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

import android.content.Context
import android.content.res.Resources
import android.safetycenter.SafetySourceData
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.config.SafetySource
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY
import android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_STATIC
import android.safetycenter.config.SafetySourcesGroup
import android.safetycenter.cts.testing.SettingsPackage.getSettingsPackageName

/**
 * A class that provides [SafetyCenterConfig] objects and associated constants to facilitate setting
 * up safety sources for testing.
 */
object SafetyCenterCtsConfigs {
    private const val CTS_PACKAGE_NAME = "android.safetycenter.cts"

    /** ID of a source not used in any config. */
    const val SAMPLE_SOURCE_ID = "cts_sample_source_id"

    /** Activity action: Launch the [TestActivity] used to check redirects in CTS tests. */
    const val ACTION_TEST_ACTIVITY = "android.safetycenter.cts.testing.action.TEST_ACTIVITY"

    /**
     * ID of the only source provided in [SINGLE_SOURCE_CONFIG], [SEVERITY_ZERO_CONFIG] and
     * [NO_PAGE_OPEN_CONFIG].
     */
    const val SINGLE_SOURCE_ID = "cts_single_source_id"

    /** ID of the only source provided in [SINGLE_SOURCE_ALL_PROFILE_CONFIG]. */
    const val SINGLE_SOURCE_ALL_PROFILE_ID = "cts_single_source_all_profile_id"

    /** ID of the only source provided in [ISSUE_ONLY_SOURCE_ALL_PROFILE_CONFIG]. */
    const val ISSUE_ONLY_ALL_PROFILE_SOURCE_ID = "cts_issue_only_all_profile_id"

    /**
     * ID of the only [SafetySourcesGroup] provided by [SINGLE_SOURCE_CONFIG],
     * [SEVERITY_ZERO_CONFIG] and [NO_PAGE_OPEN_CONFIG].
     */
    const val SINGLE_SOURCE_GROUP_ID = "cts_single_source_group_id"

    /**
     * A simple [SafetyCenterConfig] for CTS tests with a single source of id [SINGLE_SOURCE_ID].
     */
    val SINGLE_SOURCE_CONFIG = singleSourceConfig(dynamicSafetySource(SINGLE_SOURCE_ID))

    /**
     * A simple [SafetyCenterConfig] with an invalid intent action for CTS tests with a single
     * source of id [SINGLE_SOURCE_ID].
     */
    val SINGLE_SOURCE_INVALID_INTENT_CONFIG =
        singleSourceConfig(
            dynamicSafetySourceBuilder(SINGLE_SOURCE_ID).setIntentAction("stub").build()
        )

    /** A simple [SafetyCenterConfig] for CTS tests with a source max severity level of 0. */
    val SEVERITY_ZERO_CONFIG =
        singleSourceConfig(
            dynamicSafetySourceBuilder(SINGLE_SOURCE_ID).setMaxSeverityLevel(0).build()
        )

    /**
     * A simple [SafetyCenterConfig] for CTS tests with a source that does not support refresh on
     * page open.
     */
    val NO_PAGE_OPEN_CONFIG =
        singleSourceConfig(
            dynamicSafetySourceBuilder(SINGLE_SOURCE_ID).setRefreshOnPageOpenAllowed(false).build()
        )

    /** ID of a source provided by [MULTIPLE_SOURCES_CONFIG] and [SUMMARY_TEST_CONFIG]. */
    const val SOURCE_ID_1 = "cts_source_id_1"

    /** ID of a source provided by [MULTIPLE_SOURCES_CONFIG] and [SUMMARY_TEST_CONFIG]. */
    const val SOURCE_ID_2 = "cts_source_id_2"

    /** ID of a source provided by [MULTIPLE_SOURCES_CONFIG] and [SUMMARY_TEST_CONFIG]. */
    const val SOURCE_ID_3 = "cts_source_id_3"

    /** ID of a source provided by [SUMMARY_TEST_CONFIG]. */
    const val SOURCE_ID_4 = "cts_source_id_4"

    /** ID of a source provided by [SUMMARY_TEST_CONFIG]. */
    const val SOURCE_ID_5 = "cts_source_id_5"

    /** ID of a source provided by [SUMMARY_TEST_CONFIG]. */
    const val SOURCE_ID_6 = "cts_source_id_6"

    /** ID of a source provided by [SUMMARY_TEST_CONFIG]. */
    const val SOURCE_ID_7 = "cts_source_id_7"

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
     * ID of a [SafetySourcesGroup] provided by [MULTIPLE_SOURCES_CONFIG], containing two sources of
     * ids [SOURCE_ID_4] and [SOURCE_ID_5].
     */
    const val MULTIPLE_SOURCES_GROUP_ID_3 = "cts_multiple_sources_group_id_3"

    /**
     * ID of a [SafetySourcesGroup] provided by [SUMMARY_TEST_GROUP_CONFIG], containing sources:
     * [SOURCE_ID_1], [SOURCE_ID_2], [SOURCE_ID_3], [SOURCE_ID_4], [SOURCE_ID_5], [SOURCE_ID_6],
     * [SOURCE_ID_7], [STATIC_IN_COLLAPSIBLE_ID].
     */
    const val SUMMARY_TEST_GROUP_ID = "summary_test_group_id"

    /**
     * ID of a [SafetySourcesGroup] provided by [COMPLEX_CONFIG], containing sources:
     * [DYNAMIC_BAREBONE_ID], [DYNAMIC_ALL_OPTIONAL_ID], [DYNAMIC_DISABLED_ID], [DYNAMIC_HIDDEN_ID],
     * [DYNAMIC_HIDDEN_WITH_SEARCH_ID], [DYNAMIC_OTHER_PACKAGE_ID]. And provided by
     * [COMPLEX_ALL_PROFILE_CONFIG], containing sources: [DYNAMIC_BAREBONE_ID],
     * [DYNAMIC_DISABLED_ID], [DYNAMIC_HIDDEN_ID].
     */
    const val DYNAMIC_GROUP_ID = "dynamic"

    /**
     * ID of a [SafetySourcesGroup] provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG],
     * containing sources: [STATIC_BAREBONE_ID], [STATIC_ALL_OPTIONAL_ID].
     */
    const val STATIC_GROUP_ID = "static"

    /**
     * ID of a [SafetySourcesGroup] provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG],
     * containing sources: [ISSUE_ONLY_BAREBONE_ID], [ISSUE_ONLY_ALL_OPTIONAL_ID].
     */
    const val ISSUE_ONLY_GROUP_ID = "issue_only"

    /**
     * ID of a [SafetySourcesGroup] provided by [COMPLEX_CONFIG], containing sources:
     * [DYNAMIC_IN_COLLAPSIBLE_ID], [STATIC_IN_COLLAPSIBLE_ID].
     */
    const val MIXED_COLLAPSIBLE_GROUP_ID = "mixed_collapsible"

    /**
     * ID of a [SafetySourcesGroup] provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG],
     * containing sources: [DYNAMIC_IN_RIGID_ID], [STATIC_IN_RIGID_ID], [ISSUE_ONLY_IN_RIGID_ID].
     */
    const val MIXED_RIGID_GROUP_ID = "mixed_rigid"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], [COMPLEX_ALL_PROFILE_CONFIG], and
     * [ANDROID_LOCK_SCREEN_SOURCES_CONFIG], this is a dynamic, primary profile only, visible source
     * for which only the required fields are set.
     */
    const val DYNAMIC_BAREBONE_ID = "dynamic_barebone"

    /**
     * ID of a source provided by [COMPLEX_CONFIG] and [SINGLE_SOURCE_OTHER_PACKAGE_CONFIG], this is
     * a dynamic, primary profile only, visible source belonging to the [OTHER_PACKAGE_NAME] package
     * for which only the required fields are set.
     */
    const val DYNAMIC_OTHER_PACKAGE_ID = "dynamic_other_package"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a dynamic, primary profile only,
     * disabled by default source for which all the required and optional fields are set. Notably,
     * this includes the refresh on page open flag and a max severity level of recommendation.
     */
    const val DYNAMIC_ALL_OPTIONAL_ID = "dynamic_all_optional"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], [COMPLEX_ALL_PROFILE_CONFIG], and
     * [ANDROID_LOCK_SCREEN_SOURCES_CONFIG], this is a dynamic, disabled by default source for which
     * only the required fields are set.
     */
    const val DYNAMIC_DISABLED_ID = "dynamic_disabled"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], [COMPLEX_ALL_PROFILE_CONFIG], and
     * [ANDROID_LOCK_SCREEN_SOURCES_CONFIG], this ism a dynamic, hidden by default source for which
     * only the required fields are set.
     */
    const val DYNAMIC_HIDDEN_ID = "dynamic_hidden"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a dynamic, primary profile only, hidden
     * by default source for which all the required and optional fields are set.
     */
    const val DYNAMIC_HIDDEN_WITH_SEARCH_ID = "dynamic_hidden_with_search"

    /**
     * ID of a source provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG], this is a
     * static, primary profile only source for which only the required fields are set.
     */
    const val STATIC_BAREBONE_ID = "static_barebone"

    /**
     * ID of a source provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG], this is a
     * static source for which all the required and optional fields are set.
     */
    const val STATIC_ALL_OPTIONAL_ID = "static_all_optional"

    /**
     * ID of a source provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG], this is an
     * issue-only, primary profile only source for which only the required fields are set.
     */
    const val ISSUE_ONLY_BAREBONE_ID = "issue_only_barebone"

    /**
     * ID of a source provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG], this is an
     * issue-only source for which all the required and optional fields are set. Notably, this
     * includes the refresh on page open flag and a max severity level of recommendation.
     */
    const val ISSUE_ONLY_ALL_OPTIONAL_ID = "issue_only_all_optional"

    /**
     * ID of a source provided by [COMPLEX_CONFIG], this is a generic, dynamic, primary profile
     * only, visible source.
     */
    const val DYNAMIC_IN_COLLAPSIBLE_ID = "dynamic_in_collapsible"

    /**
     * ID of a source provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG], this is a
     * generic, dynamic, visible source.
     */
    const val DYNAMIC_IN_RIGID_ID = "dynamic_in_rigid"

    /**
     * ID of a source provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG], this is an
     * issue-only source.
     */
    const val ISSUE_ONLY_IN_RIGID_ID = "issue_only_in_rigid"

    /**
     * ID of a source provided by [COMPLEX_CONFIG] and [SUMMARY_TEST_CONFIG], this is a generic,
     * static, primary profile only source.
     */
    const val STATIC_IN_COLLAPSIBLE_ID = "static_in_collapsible"

    /**
     * ID of a source provided by [COMPLEX_CONFIG] and [COMPLEX_ALL_PROFILE_CONFIG], this is a
     * generic, static source.
     */
    const val STATIC_IN_RIGID_ID = "static_in_rigid"

    /** Package name for the [DYNAMIC_OTHER_PACKAGE_ID] source. */
    const val OTHER_PACKAGE_NAME = "other_package_name"

    /** A Simple [SafetyCenterConfig] with an issue only source. */
    val ISSUE_ONLY_SOURCE_CONFIG =
        singleSourceConfig(issueOnlySafetySourceBuilder(ISSUE_ONLY_ALL_OPTIONAL_ID).build())

    /** A Simple [SafetyCenterConfig] with an issue only source supporting all profiles. */
    val ISSUE_ONLY_SOURCE_ALL_PROFILE_CONFIG =
        singleSourceConfig(
            issueOnlyAllProfileSafetySourceBuilder(ISSUE_ONLY_ALL_PROFILE_SOURCE_ID).build()
        )

    /** A dynamic source with [OTHER_PACKAGE_NAME] */
    val DYNAMIC_OTHER_PACKAGE_SAFETY_SOURCE =
        dynamicSafetySourceBuilder(DYNAMIC_OTHER_PACKAGE_ID)
            .setRefreshOnPageOpenAllowed(false)
            .setPackageName(OTHER_PACKAGE_NAME)
            .build()

    /** A [SafetyCenterConfig] with a dynamic source in a different, missing package. */
    val SINGLE_SOURCE_OTHER_PACKAGE_CONFIG = singleSourceConfig(DYNAMIC_OTHER_PACKAGE_SAFETY_SOURCE)

    /** A simple [SafetyCenterConfig] with a source supporting all profiles. */
    val SINGLE_SOURCE_ALL_PROFILE_CONFIG =
        singleSourceConfig(
            dynamicAllProfileSafetySourceBuilder(SINGLE_SOURCE_ALL_PROFILE_ID).build()
        )

    /** A simple [SafetyCenterConfig] for CTS tests with multiple sources. */
    val MULTIPLE_SOURCES_CONFIG =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_1)
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_1))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_2))
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_2)
                    .setTitleResId(android.R.string.copy)
                    .setSummaryResId(android.R.string.cancel)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(SOURCE_ID_3)
                            .setTitleResId(android.R.string.copy)
                            .setSummaryResId(android.R.string.cancel)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .build()
            )
            .build()

    /** Source included in [DYNAMIC_SOURCE_GROUP_1]. */
    val DYNAMIC_SOURCE_1 = dynamicSafetySource(SOURCE_ID_1)

    /** Source included in [DYNAMIC_SOURCE_GROUP_1]. */
    val DYNAMIC_SOURCE_2 = dynamicSafetySource(SOURCE_ID_2)

    /** Source included in [DYNAMIC_SOURCE_GROUP_2]. */
    val DYNAMIC_SOURCE_3 = dynamicSafetySource(SOURCE_ID_3)

    private val DYNAMIC_SOURCE_4 = dynamicSafetySource(SOURCE_ID_4)
    private val DYNAMIC_SOURCE_5 = dynamicSafetySource(SOURCE_ID_5)

    /** Source group provided by [MULTIPLE_SOURCE_GROUPS_CONFIG]. */
    val DYNAMIC_SOURCE_GROUP_1 =
        safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_1)
            .addSafetySource(DYNAMIC_SOURCE_1)
            .addSafetySource(DYNAMIC_SOURCE_2)
            .setTitleResId(android.R.string.copy)
            .setSummaryResId(android.R.string.cut)
            .build()

    /** Source group provided by [MULTIPLE_SOURCE_GROUPS_CONFIG]. */
    val DYNAMIC_SOURCE_GROUP_2 =
        safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_2)
            .addSafetySource(DYNAMIC_SOURCE_3)
            .setTitleResId(android.R.string.paste)
            .setSummaryResId(android.R.string.cancel)
            .build()

    /** Source group provided by [MULTIPLE_SOURCE_GROUPS_CONFIG]. */
    val DYNAMIC_SOURCE_GROUP_3 =
        safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_3)
            .addSafetySource(DYNAMIC_SOURCE_4)
            .addSafetySource(DYNAMIC_SOURCE_5)
            .setTitleResId(android.R.string.dialog_alert_title)
            .setSummaryResId(android.R.string.selectAll)
            .build()

    /** A simple [SafetyCenterConfig] for CTS tests with multiple groups of multiple sources. */
    val MULTIPLE_SOURCE_GROUPS_CONFIG =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(DYNAMIC_SOURCE_GROUP_1)
            .addSafetySourcesGroup(DYNAMIC_SOURCE_GROUP_2)
            .addSafetySourcesGroup(DYNAMIC_SOURCE_GROUP_3)
            .build()

    /**
     * A simple [SafetyCenterConfig] for CTS tests with multiple sources with one source having an
     * invalid default intent.
     */
    val MULTIPLE_SOURCES_CONFIG_WITH_SOURCE_WITH_INVALID_INTENT =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MULTIPLE_SOURCES_GROUP_ID_1)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(SOURCE_ID_1).setIntentAction("stub").build()
                    )
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_2))
                    .build()
            )
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

    /** [SafetyCenterConfig] used in CTS tests for Your Work Policy Info source. */
    fun Context.getWorkPolicyInfoConfig() =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                SafetySourcesGroup.Builder()
                    .setId("AndroidAdvancedSources")
                    .setTitleResId(android.R.string.paste)
                    .addSafetySource(
                        SafetySource.Builder(SAFETY_SOURCE_TYPE_DYNAMIC)
                            .setId("AndroidWorkPolicyInfo")
                            .setPackageName(packageManager.permissionControllerPackageName)
                            .setProfile(SafetySource.PROFILE_PRIMARY)
                            .setRefreshOnPageOpenAllowed(true)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .build()
            )
            .build()

    /**
     * ID of a [SafetySourcesGroup] provided by [Context.getLockScreenSourceConfig] and
     * [ANDROID_LOCK_SCREEN_SOURCES_CONFIG], to replicate the lock screen sources group.
     */
    const val ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID = "AndroidLockScreenSources"

    /** [SafetyCenterConfig] used in CTS tests to replicate the lock screen source. */
    fun Context.getLockScreenSourceConfig() =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)
                    .addSafetySource(
                        dynamicSafetySourceBuilder("AndroidLockScreen")
                            .setPackageName(getSettingsPackageName())
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build()
                    )
                    .build()
            )
            .build()

    /** [SafetyCenterConfig] used in CTS tests to replicate the lock screen sources group. */
    val ANDROID_LOCK_SCREEN_SOURCES_CONFIG =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(ANDROID_LOCK_SCREEN_SOURCES_GROUP_ID)
                    // This is needed to have a collapsible group with an empty summary
                    .setSummaryResId(Resources.ID_NULL)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_HIDDEN_ID)
                            .setTitleResId(Resources.ID_NULL)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_DISABLED_ID)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build()
                    )
                    .build()
            )
            .build()

    /** A simple [SafetyCenterConfig] used in CTS tests that stress the group summary logic. */
    val SUMMARY_TEST_CONFIG =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(SUMMARY_TEST_GROUP_ID)
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_1))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_2))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_3))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_4))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_5))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_6))
                    .addSafetySource(dynamicSafetySource(SOURCE_ID_7))
                    .addSafetySource(staticSafetySource(STATIC_IN_COLLAPSIBLE_ID))
                    .build()
            )
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
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_ALL_OPTIONAL_ID)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .setMaxSeverityLevel(SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION)
                            .setSearchTermsResId(android.R.string.ok)
                            .setLoggingAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_DISABLED_ID)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_HIDDEN_ID)
                            .setTitleResId(Resources.ID_NULL)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_HIDDEN_WITH_SEARCH_ID)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .setSearchTermsResId(android.R.string.ok)
                            .build()
                    )
                    .addSafetySource(DYNAMIC_OTHER_PACKAGE_SAFETY_SOURCE)
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(STATIC_GROUP_ID)
                    .setSummaryResId(Resources.ID_NULL)
                    .addSafetySource(
                        staticSafetySourceBuilder(STATIC_BAREBONE_ID)
                            .setSummaryResId(Resources.ID_NULL)
                            .build()
                    )
                    .addSafetySource(
                        staticSafetySourceBuilder(STATIC_ALL_OPTIONAL_ID)
                            .setSearchTermsResId(android.R.string.ok)
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                SafetySourcesGroup.Builder()
                    .setId(ISSUE_ONLY_GROUP_ID)
                    .addSafetySource(
                        issueOnlySafetySourceBuilder(ISSUE_ONLY_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        issueOnlySafetySourceBuilder(ISSUE_ONLY_ALL_OPTIONAL_ID)
                            .setMaxSeverityLevel(SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION)
                            .setLoggingAllowed(false)
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MIXED_COLLAPSIBLE_GROUP_ID)
                    .addSafetySource(dynamicSafetySource(DYNAMIC_IN_COLLAPSIBLE_ID))
                    .addSafetySource(staticSafetySource(STATIC_IN_COLLAPSIBLE_ID))
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MIXED_RIGID_GROUP_ID)
                    .setSummaryResId(Resources.ID_NULL)
                    .addSafetySource(dynamicSafetySource(DYNAMIC_IN_RIGID_ID))
                    .addSafetySource(staticSafetySource(STATIC_IN_RIGID_ID))
                    .addSafetySource(issueOnlySafetySource(ISSUE_ONLY_IN_RIGID_ID))
                    .build()
            )
            .build()

    /**
     * A complex [SafetyCenterConfig] exploring different combinations of valid sources and groups.
     * Including sources that support all profiles.
     */
    val COMPLEX_ALL_PROFILE_CONFIG =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(DYNAMIC_GROUP_ID)
                    .addSafetySource(
                        dynamicSafetySourceBuilder(DYNAMIC_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        dynamicAllProfileSafetySourceBuilder(DYNAMIC_DISABLED_ID)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                            .build()
                    )
                    .addSafetySource(
                        dynamicAllProfileSafetySourceBuilder(DYNAMIC_HIDDEN_ID)
                            .setTitleResId(Resources.ID_NULL)
                            .setTitleForWorkResId(Resources.ID_NULL)
                            .setSummaryResId(Resources.ID_NULL)
                            .setIntentAction(null)
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(STATIC_GROUP_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(
                        staticSafetySourceBuilder(STATIC_BAREBONE_ID)
                            .setSummaryResId(Resources.ID_NULL)
                            .build()
                    )
                    .addSafetySource(
                        staticAllProfileSafetySourceBuilder(STATIC_ALL_OPTIONAL_ID)
                            .setSearchTermsResId(android.R.string.ok)
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                SafetySourcesGroup.Builder()
                    .setId(ISSUE_ONLY_GROUP_ID)
                    .addSafetySource(
                        issueOnlySafetySourceBuilder(ISSUE_ONLY_BAREBONE_ID)
                            .setRefreshOnPageOpenAllowed(false)
                            .build()
                    )
                    .addSafetySource(
                        issueOnlyAllProfileSafetySourceBuilder(ISSUE_ONLY_ALL_OPTIONAL_ID)
                            .setMaxSeverityLevel(SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION)
                            .setLoggingAllowed(false)
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder(MIXED_RIGID_GROUP_ID)
                    .setSummaryResId(Resources.ID_NULL)
                    .addSafetySource(
                        dynamicAllProfileSafetySourceBuilder(DYNAMIC_IN_RIGID_ID).build()
                    )
                    .addSafetySource(
                        staticAllProfileSafetySourceBuilder(STATIC_IN_RIGID_ID).build()
                    )
                    .addSafetySource(
                        issueOnlyAllProfileSafetySourceBuilder(ISSUE_ONLY_IN_RIGID_ID).build()
                    )
                    .build()
            )
            .build()

    /** A [SafetyCenterConfig] containing only hidden sources. */
    val HIDDEN_ONLY_CONFIG =
        SafetyCenterConfig.Builder()
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder("some_collapsible_group")
                    .addSafetySource(
                        dynamicSafetySourceBuilder("some_hidden_source")
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .build()
            )
            .addSafetySourcesGroup(
                safetySourcesGroupBuilder("some_rigid_group")
                    .setSummaryResId(Resources.ID_NULL)
                    .addSafetySource(
                        dynamicSafetySourceBuilder("another_hidden_source")
                            .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                            .build()
                    )
                    .build()
            )
            .build()

    private fun dynamicSafetySource(id: String) = dynamicSafetySourceBuilder(id).build()

    private fun dynamicSafetySourceBuilder(id: String) =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_DYNAMIC)
            .setId(id)
            .setPackageName(CTS_PACKAGE_NAME)
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)
            .setIntentAction(ACTION_TEST_ACTIVITY)
            .setProfile(SafetySource.PROFILE_PRIMARY)
            .setRefreshOnPageOpenAllowed(true)

    private fun dynamicAllProfileSafetySourceBuilder(id: String) =
        dynamicSafetySourceBuilder(id)
            .setProfile(SafetySource.PROFILE_ALL)
            .setTitleForWorkResId(android.R.string.paste)

    private fun staticSafetySource(id: String) = staticSafetySourceBuilder(id).build()

    private fun staticSafetySourceBuilder(id: String) =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_STATIC)
            .setId(id)
            .setTitleResId(android.R.string.ok)
            .setSummaryResId(android.R.string.ok)
            .setIntentAction(ACTION_TEST_ACTIVITY)
            .setProfile(SafetySource.PROFILE_PRIMARY)

    private fun staticAllProfileSafetySourceBuilder(id: String) =
        staticSafetySourceBuilder(id)
            .setProfile(SafetySource.PROFILE_ALL)
            .setTitleForWorkResId(android.R.string.paste)

    private fun issueOnlySafetySource(id: String) = issueOnlySafetySourceBuilder(id).build()

    private fun issueOnlySafetySourceBuilder(id: String) =
        SafetySource.Builder(SAFETY_SOURCE_TYPE_ISSUE_ONLY)
            .setId(id)
            .setPackageName(CTS_PACKAGE_NAME)
            .setProfile(SafetySource.PROFILE_PRIMARY)
            .setRefreshOnPageOpenAllowed(true)

    private fun issueOnlyAllProfileSafetySourceBuilder(id: String) =
        issueOnlySafetySourceBuilder(id).setProfile(SafetySource.PROFILE_ALL)

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
                    .build()
            )
            .build()
}
