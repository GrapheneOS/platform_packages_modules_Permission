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
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import java.time.Duration

/** A class that facilitates working with Safety Center flags. */
object SafetyCenterFlags {

    /** Flag that determines whether SafetyCenter is enabled. */
    private const val PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled"

    /**
     * Flag that determines the time for which a Safety Center refresh is allowed to wait for a
     * source to respond to a refresh request before timing out and marking the refresh as finished.
     */
    private const val PROPERTY_SAFETY_CENTER_REFRESH_SOURCE_TIMEOUT =
        "safety_center_refresh_source_timeout_millis"

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
     * Default time for which a Safety Center refresh is allowed to wait for a source to respond to
     * a refresh request before timing out and marking the refresh as finished.
     */
    private val REFRESH_SOURCE_TIMEOUT_DEFAULT_DURATION = Duration.ofSeconds(10)

    /**
     * Default time for which Safety Center will wait for a source to respond to a resolving action
     * before timing out.
     */
    private val RESOLVE_ACTION_TIMEOUT_DEFAULT_DURATION = Duration.ofSeconds(10)

    /** Returns whether the device supports Safety Center. */
    fun Context.deviceSupportsSafetyCenter() =
        resources.getBoolean(
            Resources.getSystem().getIdentifier("config_enableSafetyCenter", "bool", "android"))

    /** A property that allows getting and modifying [PROPERTY_SAFETY_CENTER_ENABLED]. */
    var isEnabled: Boolean
        get() = readFlag(PROPERTY_SAFETY_CENTER_ENABLED, defaultValue = false) { it.toBoolean() }
        set(value) {
            writeFlag(PROPERTY_SAFETY_CENTER_ENABLED, value.toString())
        }

    /**
     * A property that allows getting and setting the
     * [PROPERTY_SAFETY_CENTER_REFRESH_SOURCE_TIMEOUT] device config flag.
     */
    var refreshTimeout: Duration
        get() =
            readFlag(
                PROPERTY_SAFETY_CENTER_REFRESH_SOURCE_TIMEOUT,
                REFRESH_SOURCE_TIMEOUT_DEFAULT_DURATION) { Duration.ofMillis(it.toLong()) }
        set(value) {
            writeFlag(PROPERTY_SAFETY_CENTER_REFRESH_SOURCE_TIMEOUT, value.toMillis().toString())
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
                    PROPERTY_SAFETY_CENTER_REFRESH_SOURCE_TIMEOUT,
                    PROPERTY_SAFETY_CENTER_RESOLVE_ACTION_TIMEOUT,
                    PROPERTY_UNTRACKED_SOURCES)
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
