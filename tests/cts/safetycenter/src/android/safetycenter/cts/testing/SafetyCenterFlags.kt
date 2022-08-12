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

import android.Manifest.permission.READ_DEVICE_CONFIG
import android.Manifest.permission.WRITE_DEVICE_CONFIG
import android.content.Context
import android.content.res.Resources
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import android.provider.DeviceConfig.Properties
import android.safetycenter.SafetySourceData
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import java.time.Duration

/** A class that facilitates working with Safety Center flags. */
object SafetyCenterFlags {

    /** Flag that determines whether SafetyCenter is enabled. */
    private const val PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled"

    /**
     * Flag that determines whether we should show error entries for sources that timeout when
     * refreshing them.
     */
    private const val PROPERTY_SHOW_ERROR_ENTRIES_ON_TIMEOUT = "show_error_entries_on_timeout"

    /** Flag that determines whether we should replace the IconAction of the lock screen source. */
    private const val PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION =
        "safety_center_replace_lock_screen_icon_action"

    /**
     * Flag that determines the time for which a Safety Center refresh is allowed to wait for a
     * source to respond to a refresh request before timing out and marking the refresh as finished,
     * depending on the refresh reason.
     */
    private const val PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS =
        "safety_center_refresh_sources_timeouts_millis"

    /**
     * Device Config flag that determines the time for which Safety Center will wait for a source to
     * respond to a resolving action before timing out.
     */
    private const val PROPERTY_SAFETY_CENTER_RESOLVE_ACTION_TIMEOUT =
        "safety_center_resolve_action_timeout_millis"

    /**
     * Device config flag containing a comma delimited lists of source IDs that we won't track when
     * deciding if a broadcast is completed. We still send broadcasts to (and handle API calls from)
     * these sources as normal.
     */
    private const val PROPERTY_UNTRACKED_SOURCES = "safety_center_untracked_sources"

    /**
     * Device Config flag containing a map (a comma separated list of colon separated pairs) where
     * the key is an issue [SafetySourceData.SeverityLevel] and the value is the number of times an
     * issue of this [SafetySourceData.SeverityLevel] should be resurfaced.
     */
    private const val PROPERTY_RESURFACE_ISSUE_MAX_COUNTS =
        "safety_center_resurface_issue_max_counts"

    /**
     * Device Config flag containing a map (a comma separated list of colon separated pairs) where
     * the key is an issue [SafetySourceData.SeverityLevel] and the value is the time after which a
     * dismissed issue of this [SafetySourceData.SeverityLevel] will resurface if it has not reached
     * the maximum count for which a dismissed issue of this [SafetySourceData.SeverityLevel] should
     * be resurfaced.
     */
    private const val PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS =
        "safety_center_resurface_issue_delays_millis"

    /**
     * Device Config flag containing a map (a comma separated list of colon separated pairs) where
     * the key is an issue [SafetySourceIssue.IssueCategory] and the value is a
     * vertical-bar-delimited list of IDs of safety sources that are allowed to send issues with
     * this category.
     */
    private const val PROPERTY_ISSUE_CATEGORY_ALLOWLISTS = "safety_center_issue_category_allowlists"

    /**
     * Comma delimited list of IDs of sources that should only be refreshed when Safety Center is on
     * screen. We will refresh these sources only on page open and when the scan button is clicked.
     */
    private const val PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES =
        "safety_center_background_refresh_denied_sources"

    /**
     * Default time for which a Safety Center refresh is allowed to wait for a source to respond to
     * a refresh request before timing out and marking the refresh as finished.
     */
    private val REFRESH_SOURCE_TIMEOUT_DEFAULT_DURATION = Duration.ofSeconds(10)

    /**
     * Default time for which Safety Center will wait for a source to respond to a resolving action
     * before timing out.
     */
    private val RESOLVE_ACTION_TIMEOUT_DEFAULT_DURATION = Duration.ofSeconds(10)

    /** Default maximum number of times that Safety Center will resurface a dismissed issue. */
    private const val RESURFACE_ISSUE_DEFAULT_MAX_COUNT: Long = 0

    /** Default time for which Safety Center will wait before resurfacing a dismissed issue. */
    private val RESURFACE_ISSUE_DEFAULT_DELAY = Duration.ofDays(180)

    /** Returns whether the device supports Safety Center. */
    fun Context.deviceSupportsSafetyCenter() =
        resources.getBoolean(
            Resources.getSystem().getIdentifier("config_enableSafetyCenter", "bool", "android"))

    /**
     * A property that allows getting and modifying the [PROPERTY_SAFETY_CENTER_ENABLED] device
     * config flag.
     */
    var isEnabled: Boolean
        get() = readFlag(PROPERTY_SAFETY_CENTER_ENABLED, defaultValue = false) { it.toBoolean() }
        set(value) {
            writeFlag(PROPERTY_SAFETY_CENTER_ENABLED, value.toString())
        }

    /**
     * A property that allows getting and modifying the [PROPERTY_SHOW_ERROR_ENTRIES_ON_TIMEOUT]
     * device config flag.
     */
    var showErrorEntriesOnTimeout: Boolean
        get() =
            readFlag(PROPERTY_SHOW_ERROR_ENTRIES_ON_TIMEOUT, defaultValue = false) {
                it.toBoolean()
            }
        set(value) {
            writeFlag(PROPERTY_SHOW_ERROR_ENTRIES_ON_TIMEOUT, value.toString())
        }

    /**
     * A property that allows getting and modifying the [PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION]
     * device config flag.
     */
    var replaceLockScreenIconAction: Boolean
        get() =
            readFlag(PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION, defaultValue = true) {
                it.toBoolean()
            }
        set(value) {
            writeFlag(PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION, value.toString())
        }

    /**
     * A property that allows getting and setting the [PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS]
     * device config flag.
     */
    var refreshTimeouts: Map<Int, Duration>
        get() =
            readFlag(PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS, defaultValue = emptyMap()) {
                it.toMap(String::toInt, { valueString -> Duration.ofMillis(valueString.toLong()) })
            }
        set(value) {
            writeFlag(
                PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS,
                value.joinToListOfPairsString(
                    Int::toString, { value -> value.toMillis().toString() }))
        }

    /**
     * A property that allows getting and setting the
     * [PROPERTY_SAFETY_CENTER_RESOLVE_ACTION_TIMEOUT] device config flag.
     */
    var resolveActionTimeout: Duration
        get() =
            readFlag(
                PROPERTY_SAFETY_CENTER_RESOLVE_ACTION_TIMEOUT,
                RESOLVE_ACTION_TIMEOUT_DEFAULT_DURATION) { Duration.ofMillis(it.toLong()) }
        set(value) {
            writeFlag(PROPERTY_SAFETY_CENTER_RESOLVE_ACTION_TIMEOUT, value.toMillis().toString())
        }

    /**
     * A property that allows getting and setting the [PROPERTY_UNTRACKED_SOURCES] device config
     * flag.
     */
    var untrackedSources: Set<String>
        get() =
            readFlag(PROPERTY_UNTRACKED_SOURCES, defaultValue = emptySet()) {
                it.split(",").toSet()
            }
        set(value) {
            writeFlag(PROPERTY_UNTRACKED_SOURCES, value.joinToString(","))
        }

    /**
     * A property that allows getting and setting the [PROPERTY_RESURFACE_ISSUE_MAX_COUNTS] device
     * config flag.
     */
    var resurfaceIssueMaxCounts: Map<Int, Long>
        get() =
            readFlag(PROPERTY_RESURFACE_ISSUE_MAX_COUNTS, defaultValue = emptyMap()) {
                it.toMap(String::toInt, String::toLong)
            }
        set(value) {
            writeFlag(
                PROPERTY_RESURFACE_ISSUE_MAX_COUNTS,
                value.joinToListOfPairsString(Int::toString, Long::toString))
        }

    /**
     * A property that allows getting and setting the [PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS]
     * device config flag.
     */
    var resurfaceIssueDelays: Map<Int, Duration>
        get() =
            readFlag(PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS, defaultValue = emptyMap()) {
                it.toMap(String::toInt, { valueString -> Duration.ofMillis(valueString.toLong()) })
            }
        set(value) {
            writeFlag(
                PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS,
                value.joinToListOfPairsString(
                    Int::toString, { value -> value.toMillis().toString() }))
        }

    /**
     * A property that allows getting and setting the [PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES]
     * device config flag.
     */
    var backgroundRefreshDeniedSources: Set<String>
        get() =
            readFlag(PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES, defaultValue = emptySet()) {
                it.split(",").toSet()
            }
        set(value) {
            writeFlag(PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES, value.joinToString(","))
        }

    /**
     * A property that allows getting and setting the [PROPERTY_ISSUE_CATEGORY_ALLOWLISTS] device
     * config flag.
     */
    var issueCategoryAllowlists: Map<Int, Set<String>>
        get() =
            readFlag(PROPERTY_ISSUE_CATEGORY_ALLOWLISTS, defaultValue = emptyMap()) {
                it.toMap(String::toInt, { valueString -> valueString.split("|").toSet() })
            }
        set(value) {
            writeFlag(
                PROPERTY_ISSUE_CATEGORY_ALLOWLISTS,
                value.joinToListOfPairsString(Int::toString, { value -> value.joinToString("|") }))
        }

    /** Converts a comma separated list of colon separated pairs into a map. */
    private fun <K, V> String.toMap(
        keyFromString: (String) -> K,
        valueFromString: (String) -> V
    ): Map<K, V> =
        split(",").associate { pair ->
            val (keyString, valueString) = pair.split(":")
            keyFromString(keyString) to valueFromString(valueString)
        }

    /** Converts a map into a comma separated list of colon separated pairs. */
    private fun <K, V> Map<K, V>.joinToListOfPairsString(
        stringFromKey: (K) -> String,
        stringFromValue: (V) -> String
    ): String = map { "${stringFromKey(it.key)}:${stringFromValue(it.value)}" }.joinToString(",")

    private fun <T> readFlag(name: String, defaultValue: T, parseFromString: (String) -> T) =
        callWithShellPermissionIdentity(
            {
                val value = DeviceConfig.getProperty(NAMESPACE_PRIVACY, name)
                if (value == null) {
                    defaultValue
                } else {
                    parseFromString(value)
                }
            },
            READ_DEVICE_CONFIG)

    private fun writeFlag(name: String, stringValue: String?) {
        callWithShellPermissionIdentity(
            {
                val valueWasSet =
                    DeviceConfig.setProperty(
                        NAMESPACE_PRIVACY, name, stringValue, /* makeDefault */ false)
                require(valueWasSet) { "Could not set $name to: $stringValue" }
            },
            WRITE_DEVICE_CONFIG)
    }

    /**
     * Returns a snapshot of all the Safety Center flags.
     *
     * This snapshot is only taken once and cached afterwards. This must be called at least once
     * prior to modifying any flag.
     */
    val snapshot: Properties by lazy {
        callWithShellPermissionIdentity(
            {
                DeviceConfig.getProperties(
                    NAMESPACE_PRIVACY,
                    PROPERTY_SAFETY_CENTER_ENABLED,
                    PROPERTY_SHOW_ERROR_ENTRIES_ON_TIMEOUT,
                    PROPERTY_REPLACE_LOCK_SCREEN_ICON_ACTION,
                    PROPERTY_REFRESH_SOURCES_TIMEOUTS_MILLIS,
                    PROPERTY_SAFETY_CENTER_RESOLVE_ACTION_TIMEOUT,
                    PROPERTY_UNTRACKED_SOURCES,
                    PROPERTY_RESURFACE_ISSUE_MAX_COUNTS,
                    PROPERTY_RESURFACE_ISSUE_DELAYS_MILLIS,
                    PROPERTY_BACKGROUND_REFRESH_DENIED_SOURCES,
                    PROPERTY_ISSUE_CATEGORY_ALLOWLISTS)
            },
            READ_DEVICE_CONFIG)
    }

    /** Resets the Safety Center flags based on the given [snapshot]. */
    fun reset(snapshot: Properties) {
        // Write flags one by one instead of using `DeviceConfig#setProperties` as the latter does
        // not work when DeviceConfig sync is disabled.
        snapshot.keyset.forEach {
            val key = it
            val value = snapshot.getString(key, /* defaultValue */ null)
            writeFlag(key, value)
        }
    }

    /** Returns the [PROPERTY_SAFETY_CENTER_ENABLED] of the Safety Center flags snapshot. */
    fun Properties.isSafetyCenterEnabled() =
        getBoolean(PROPERTY_SAFETY_CENTER_ENABLED, /* defaultValue */ false)
}
