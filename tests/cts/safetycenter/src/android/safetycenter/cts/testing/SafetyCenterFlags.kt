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
import android.content.pm.PackageManager
import android.content.res.Resources
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import android.provider.DeviceConfig.Properties
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_LOCALE_CHANGE
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_REBOOT
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_SAFETY_CENTER_ENABLED
import android.safetycenter.SafetySourceData
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import java.time.Duration
import kotlin.reflect.KProperty

/** A class that facilitates working with Safety Center flags. */
object SafetyCenterFlags {

    /** Flag that determines whether Safety Center is enabled. */
    private val isEnabledFlag =
        Flag("safety_center_is_enabled", defaultValue = false, BooleanParser())

    /** Flag that determines whether Safety Center can send notifications. */
    private val notificationsFlag =
        Flag("safety_center_notifications_enabled", defaultValue = false, BooleanParser())

    /**
     * Flag containing a comma delimited list of IDs of sources that Safety Center can send
     * notifications about, in addition to those permitted by the current XML config.
     */
    private val notificationsAllowedSourcesFlag =
        Flag(
            "safety_center_notifications_allowed_sources",
            defaultValue = emptySet(),
            SetParser(StringParser())
        )

    /**
     * Flag that determines whether we should show error entries for sources that timeout when
     * refreshing them.
     */
    private val showErrorEntriesOnTimeoutFlag =
        Flag("safety_center_show_error_entries_on_timeout", defaultValue = false, BooleanParser())

    /** Flag that determines whether we should replace the IconAction of the lock screen source. */
    private val replaceLockScreenIconActionFlag =
        Flag("safety_center_replace_lock_screen_icon_action", defaultValue = true, BooleanParser())

    /**
     * Flag that determines the time for which a Safety Center refresh is allowed to wait for a
     * source to respond to a refresh request before timing out and marking the refresh as finished,
     * depending on the refresh reason.
     */
    private val refreshSourceTimeoutsFlag =
        Flag(
            "safety_center_refresh_sources_timeouts_millis",
            defaultValue = getAllRefreshTimeoutsMap(TIMEOUT_LONG),
            MapParser(IntParser(), DurationParser())
        )

    /**
     * Flag that determines the time for which Safety Center will wait for a source to respond to a
     * resolving action before timing out.
     */
    private val resolveActionTimeoutFlag =
        Flag(
            "safety_center_resolve_action_timeout_millis",
            defaultValue = TIMEOUT_LONG,
            DurationParser()
        )

    /**
     * Flag that determines the time for which Safety Center will wait before starting dismissal of
     * resolved issue UI
     */
    private val hideResolveUiTransitionDelayFlag =
        Flag(
            "safety_center_hide_resolved_ui_transition_delay_millis",
            defaultValue = Duration.ofMillis(400),
            DurationParser()
        )

    /**
     * Flag containing a comma delimited lists of source IDs that we won't track when deciding if a
     * broadcast is completed. We still send broadcasts to (and handle API calls from) these sources
     * as normal.
     */
    private val untrackedSourcesFlag =
        Flag(
            "safety_center_untracked_sources",
            defaultValue = emptySet(),
            SetParser(StringParser())
        )

    /**
     * Flag containing a map (a comma separated list of colon separated pairs) where the key is an
     * issue [SafetySourceData.SeverityLevel] and the value is the number of times an issue of this
     * [SafetySourceData.SeverityLevel] should be resurfaced.
     */
    private val resurfaceIssueMaxCountsFlag =
        Flag(
            "safety_center_resurface_issue_max_counts",
            defaultValue = emptyMap(),
            MapParser(IntParser(), LongParser())
        )

    /**
     * Flag containing a map (a comma separated list of colon separated pairs) where the key is an
     * issue [SafetySourceData.SeverityLevel] and the value is the time after which a dismissed
     * issue of this [SafetySourceData.SeverityLevel] will resurface if it has not reached the
     * maximum count for which a dismissed issue of this [SafetySourceData.SeverityLevel] should be
     * resurfaced.
     */
    private val resurfaceIssueDelaysFlag =
        Flag(
            "safety_center_resurface_issue_delays_millis",
            defaultValue = emptyMap(),
            MapParser(IntParser(), DurationParser())
        )

    /**
     * Flag containing a map (a comma separated list of colon separated pairs) where the key is an
     * issue [SafetySourceIssue.IssueCategory] and the value is a vertical-bar-delimited list of IDs
     * of safety sources that are allowed to send issues with this category.
     */
    private val issueCategoryAllowlistsFlag =
        Flag(
            "safety_center_issue_category_allowlists",
            defaultValue = emptyMap(),
            MapParser(IntParser(), SetParser(StringParser(), delimiter = "|"))
        )

    /**
     * Flag that represents a comma delimited list of IDs of sources that should only be refreshed
     * when Safety Center is on screen. We will refresh these sources only on page open and when the
     * scan button is clicked.
     */
    private val backgroundRefreshDeniedSourcesFlag =
        Flag(
            "safety_center_background_refresh_denied_sources",
            defaultValue = emptySet(),
            SetParser(StringParser())
        )

    /**
     * Flag that determines whether statsd logging is allowed in tests.
     *
     * This is useful to allow testing statsd logs in some specific tests, while keeping the other
     * tests from polluting our statsd logs.
     */
    private val allowStatsdLoggingInTestsFlag =
        Flag("safety_center_allow_statsd_logging_in_tests", defaultValue = false, BooleanParser())

    /**
     * The Package Manager flag used while toggling the QS tile component.
     *
     * This is to make sure that the SafetyCenter is not killed while toggling the QS tile component
     * during the CTS tests, which causes flakiness in them.
     */
    private val qsTileComponentSettingFlag =
        Flag(
            "safety_center_qs_tile_component_setting_flags",
            defaultValue = PackageManager.DONT_KILL_APP,
            IntParser()
        )

    /** Every Safety Center flag. */
    private val FLAGS: List<Flag<*>> =
        listOf(
            isEnabledFlag,
            notificationsFlag,
            notificationsAllowedSourcesFlag,
            showErrorEntriesOnTimeoutFlag,
            replaceLockScreenIconActionFlag,
            refreshSourceTimeoutsFlag,
            resolveActionTimeoutFlag,
            hideResolveUiTransitionDelayFlag,
            untrackedSourcesFlag,
            resurfaceIssueMaxCountsFlag,
            resurfaceIssueDelaysFlag,
            issueCategoryAllowlistsFlag,
            backgroundRefreshDeniedSourcesFlag,
            allowStatsdLoggingInTestsFlag,
            qsTileComponentSettingFlag
        )

    /** Returns whether the device supports Safety Center. */
    fun Context.deviceSupportsSafetyCenter() =
        resources.getBoolean(
            Resources.getSystem().getIdentifier("config_enableSafetyCenter", "bool", "android")
        )

    /** A property that allows getting and setting the [isEnabledFlag]. */
    var isEnabled: Boolean by isEnabledFlag

    /** A property that allows getting and setting the [notificationsFlag]. */
    var notificationsEnabled: Boolean by notificationsFlag

    /** A property that allowed getting and setting the [notificationsAllowedSourcesFlag]. */
    var notificationsAllowedSources: Set<String> by notificationsAllowedSourcesFlag

    /** A property that allows getting and setting the [showErrorEntriesOnTimeoutFlag]. */
    var showErrorEntriesOnTimeout: Boolean by showErrorEntriesOnTimeoutFlag

    /** A property that allows getting and setting the [replaceLockScreenIconActionFlag]. */
    var replaceLockScreenIconAction: Boolean by replaceLockScreenIconActionFlag

    /** A property that allows getting and setting the [refreshSourceTimeoutsFlag]. */
    var refreshTimeouts: Map<Int, Duration> by refreshSourceTimeoutsFlag

    /** A property that allows getting and setting the [resolveActionTimeoutFlag]. */
    var resolveActionTimeout: Duration by resolveActionTimeoutFlag

    /** A property that allows getting and setting the [hideResolveUiTransitionDelayFlag]. */
    var hideResolvedIssueUiTransitionDelay: Duration by hideResolveUiTransitionDelayFlag

    /** A property that allows getting and setting the [untrackedSourcesFlag]. */
    var untrackedSources: Set<String> by untrackedSourcesFlag

    /** A property that allows getting and setting the [resurfaceIssueMaxCountsFlag]. */
    var resurfaceIssueMaxCounts: Map<Int, Long> by resurfaceIssueMaxCountsFlag

    /** A property that allows getting and setting the [resurfaceIssueDelaysFlag]. */
    var resurfaceIssueDelays: Map<Int, Duration> by resurfaceIssueDelaysFlag

    /** A property that allows getting and setting the [issueCategoryAllowlistsFlag]. */
    var issueCategoryAllowlists: Map<Int, Set<String>> by issueCategoryAllowlistsFlag

    /** A property that allows getting and setting the [backgroundRefreshDeniedSourcesFlag]. */
    var backgroundRefreshDeniedSources: Set<String> by backgroundRefreshDeniedSourcesFlag

    /** A property that allows getting and setting the [allowStatsdLoggingInTestsFlag]. */
    var allowStatsdLoggingInTests: Boolean by allowStatsdLoggingInTestsFlag

    /**
     * Returns a snapshot of all the Safety Center flags.
     *
     * This snapshot is only taken once and cached afterwards. [setup] must be called at least once
     * prior to modifying any flag for the snapshot to be taken with the right values.
     */
    @Volatile lateinit var snapshot: Properties

    private val lazySnapshot: Properties by lazy {
        callWithShellPermissionIdentity(READ_DEVICE_CONFIG) {
            DeviceConfig.getProperties(NAMESPACE_PRIVACY, *FLAGS.map { it.name }.toTypedArray())
        }
    }

    /**
     * Takes a snapshot of all Safety Center flags and sets them up to their default values.
     *
     * This doesn't apply to [isEnabled] as it is handled separately by [SafetyCenterCtsHelper]:
     * there is a listener that listens to changes to this flag in system server, and we need to
     * ensure we wait for it to complete when modifying this flag.
     */
    fun setup() {
        snapshot = lazySnapshot
        FLAGS.filter { it.name != isEnabledFlag.name }
            .forEach { writeDeviceConfigProperty(it.name, it.defaultStringValue) }
    }

    /**
     * Resets the Safety Center flags based on the existing [snapshot] captured during [setup].
     *
     * This doesn't apply to [isEnabled] as it is handled separately by [SafetyCenterCtsHelper]:
     * there is a listener that listens to changes to this flag in system server, and we need to
     * ensure we wait for it to complete when modifying this flag.
     */
    fun reset() {
        // Write flags one by one instead of using `DeviceConfig#setProperties` as the latter does
        // not work when DeviceConfig sync is disabled and does not take uninitialized values into
        // account.
        FLAGS.filter { it.name != isEnabledFlag.name }
            .forEach {
                val key = it.name
                val value = snapshot.getString(key, /* defaultValue */ null)
                writeDeviceConfigProperty(key, value)
            }
    }

    /** Sets the [refreshTimeouts] for all refresh reasons to the given [refreshTimeout]. */
    fun setAllRefreshTimeoutsTo(refreshTimeout: Duration) {
        refreshTimeouts = getAllRefreshTimeoutsMap(refreshTimeout)
    }

    /** Returns the [isEnabledFlag] value of the Safety Center flags snapshot. */
    fun Properties.isSafetyCenterEnabled() =
        getBoolean(isEnabledFlag.name, /* defaultValue */ false)

    private fun getAllRefreshTimeoutsMap(refreshTimeout: Duration) =
        mapOf(
            REFRESH_REASON_PAGE_OPEN to refreshTimeout,
            REFRESH_REASON_RESCAN_BUTTON_CLICK to refreshTimeout,
            REFRESH_REASON_DEVICE_REBOOT to refreshTimeout,
            REFRESH_REASON_DEVICE_LOCALE_CHANGE to refreshTimeout,
            REFRESH_REASON_SAFETY_CENTER_ENABLED to refreshTimeout,
            REFRESH_REASON_OTHER to refreshTimeout
        )

    private interface Parser<T> {
        fun parseFromString(stringValue: String): T

        fun toString(value: T): String = value.toString()
    }

    private class StringParser : Parser<String> {
        override fun parseFromString(stringValue: String) = stringValue
    }

    private class BooleanParser : Parser<Boolean> {
        override fun parseFromString(stringValue: String) = stringValue.toBoolean()
    }

    private class IntParser : Parser<Int> {
        override fun parseFromString(stringValue: String) = stringValue.toInt()
    }

    private class LongParser : Parser<Long> {
        override fun parseFromString(stringValue: String) = stringValue.toLong()
    }

    private class DurationParser : Parser<Duration> {
        override fun parseFromString(stringValue: String) = Duration.ofMillis(stringValue.toLong())

        override fun toString(value: Duration) = value.toMillis().toString()
    }

    private class SetParser<T>(
        private val elementParser: Parser<T>,
        private val delimiter: String = ","
    ) : Parser<Set<T>> {
        override fun parseFromString(stringValue: String) =
            stringValue.split(delimiter).map(elementParser::parseFromString).toSet()

        override fun toString(value: Set<T>) =
            value.joinToString(delimiter, transform = elementParser::toString)
    }

    private class MapParser<K, V>(
        private val keyParser: Parser<K>,
        private val valueParser: Parser<V>,
        private val entriesDelimiter: String = ",",
        private val pairDelimiter: String = ":"
    ) : Parser<Map<K, V>> {
        override fun parseFromString(stringValue: String) =
            stringValue.split(entriesDelimiter).associate { pair ->
                val (keyString, valueString) = pair.split(pairDelimiter)
                keyParser.parseFromString(keyString) to valueParser.parseFromString(valueString)
            }

        override fun toString(value: Map<K, V>) =
            value
                .map {
                    "${keyParser.toString(it.key)}${pairDelimiter}${valueParser.toString(it.value)}"
                }
                .joinToString(entriesDelimiter)
    }

    private class Flag<T>(
        val name: String,
        private val defaultValue: T,
        private val parser: Parser<T>
    ) {
        val defaultStringValue = parser.toString(defaultValue)

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
            readDeviceConfigProperty(name)?.let(parser::parseFromString) ?: defaultValue

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            writeDeviceConfigProperty(name, parser.toString(value))
        }
    }

    private fun readDeviceConfigProperty(name: String): String? =
        callWithShellPermissionIdentity(READ_DEVICE_CONFIG) {
            DeviceConfig.getProperty(NAMESPACE_PRIVACY, name)
        }

    private fun writeDeviceConfigProperty(name: String, stringValue: String?) {
        callWithShellPermissionIdentity(WRITE_DEVICE_CONFIG) {
            val valueWasSet =
                DeviceConfig.setProperty(
                    NAMESPACE_PRIVACY,
                    name,
                    stringValue, /* makeDefault */
                    false
                )
            require(valueWasSet) { "Could not set $name to: $stringValue" }
        }
    }
}
