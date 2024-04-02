/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.permissioncontroller.permission.utils.v35

import android.Manifest
import android.app.Application
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.content.pm.PackageManager.FLAG_PERMISSION_ONE_TIME
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.os.Build
import android.permission.PermissionManager
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.permission.utils.ContextCompat

object MultiDeviceUtils {
    const val DEFAULT_REMOTE_DEVICE_NAME = "remote device"

    /**
     * Defines what runtime permissions are device aware. This can be replaced with an API from VDM
     * which can take device's capabilities into account
     */
    // TODO: b/298661870 - Use new API to get the list of device aware permissions
    private val DEVICE_AWARE_PERMISSIONS: Set<String> =
        setOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    private const val DEVICE_AWARE_PERMISSION_FLAG_MASK =
        FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED or
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED or
            FLAG_PERMISSION_ONE_TIME or
            FLAG_PERMISSION_USER_SET or
            FLAG_PERMISSION_USER_FIXED

    @JvmStatic
    fun isDeviceAwarePermissionSupported(context: Context): Boolean =
        SdkLevel.isAtLeastV() &&
            !(DeviceUtils.isTelevision(context) ||
                DeviceUtils.isAuto(context) ||
                DeviceUtils.isWear(context))

    @JvmStatic
    fun isPermissionDeviceAware(permission: String): Boolean =
        permission in DEVICE_AWARE_PERMISSIONS

    @JvmStatic
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun isPermissionDeviceAware(context: Context, deviceId: Int, permission: String): Boolean {
        if (!SdkLevel.isAtLeastV()) {
            return false
        }

        if (permission !in DEVICE_AWARE_PERMISSIONS) {
            return false
        }

        val virtualDevice =
            context.getSystemService(VirtualDeviceManager::class.java)!!.getVirtualDevice(deviceId)
                ?: return false

        return when (permission) {
            Manifest.permission.CAMERA -> virtualDevice.hasCustomCameraSupport()
            Manifest.permission.RECORD_AUDIO -> virtualDevice.hasCustomAudioInputSupport()
            else -> false
        }
    }

    @JvmStatic
    fun getDeviceName(context: Context, deviceId: Int): String? {
        // Pre Android V no permission requests can affect the VirtualDevice, thus return local
        // device name.
        if (!SdkLevel.isAtLeastV() || deviceId == ContextCompat.DEVICE_ID_DEFAULT) {
            return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }
        val vdm: VirtualDeviceManager? = context.getSystemService(VirtualDeviceManager::class.java)
        if (vdm != null) {
            val virtualDevice = vdm.getVirtualDevice(deviceId)
            if (virtualDevice != null) {
                return if (virtualDevice.displayName != null) virtualDevice.displayName.toString()
                else DEFAULT_REMOTE_DEVICE_NAME
            }
        }
        throw IllegalArgumentException("No device name for device: $deviceId")
    }

    @JvmStatic
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun isDefaultDeviceId(persistentDeviceId: String?) =
        !SdkLevel.isAtLeastV() ||
            persistentDeviceId.isNullOrBlank() ||
            persistentDeviceId == VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT

    @JvmStatic
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun getDeviceName(context: Context, persistentDeviceId: String): String {
        if (
            !SdkLevel.isAtLeastV() ||
                persistentDeviceId == VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT
        ) {
            return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }
        val vdm: VirtualDeviceManager =
            context.getSystemService(VirtualDeviceManager::class.java)
                ?: throw RuntimeException("VirtualDeviceManager not found")
        val deviceName =
            vdm.getDisplayNameForPersistentDeviceId(persistentDeviceId)
                ?: DEFAULT_REMOTE_DEVICE_NAME
        return deviceName.toString()
    }

    @JvmStatic
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun getDefaultDevicePersistentDeviceId(): String =
        if (!SdkLevel.isAtLeastV()) {
            "default: ${ContextCompat.DEVICE_ID_DEFAULT}"
        } else {
            VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT
        }

    /**
     * Grants external device permissions to the specified package. Permissions will be extracted
     * from the group name.
     *
     * @param app The current application
     * @param persistentDeviceId The external device identifier
     * @param packageName Name of the package to which permission needs to granted
     * @param permissions Permissions that needs to be granted
     * @param userSet Whether to mark the permission as user set
     *
     * TODO: b/328839130: This method is meant to use it on External Devices and on Device Aware
     *   permissions only. It does not follow the default device implementation because of the
     *   LightAppPermGroup requirement. The data class LightAppPermGroup is not available for
     *   external devices at present, hence the implementation differs.
     */
    @JvmStatic
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun grantRuntimePermissionsWithPersistentDeviceId(
        app: Application,
        persistentDeviceId: String,
        packageName: String,
        permissions: Set<String>,
        userSet: Boolean
    ) {
        if (!SdkLevel.isAtLeastV() || isDefaultDeviceId(persistentDeviceId)) {
            return
        }
        permissions
            .filter { isPermissionDeviceAware(it) }
            .forEach { permission ->
                grantRuntimePermissionWithPersistentDeviceId(
                    app,
                    persistentDeviceId,
                    packageName,
                    permission,
                    userSet
                )
            }
    }

    /**
     * Grants the external device permission to the specified package
     *
     * @param app The current application
     * @param persistentDeviceId The external device identifier
     * @param packageName Name of the package to which permission needs to granted
     * @param permission Permission that needs to be granted
     * @param userSet Whether to mark the permission as user set
     *
     * TODO: b/328839130: This method is meant to use it on External Devices and on Device Aware
     *   permissions only. It does not follow the default device implementation because of the
     *   LightAppPermGroup requirement. The data class LightAppPermGroup is not available for
     *   external devices at present, hence the implementation differs.
     */
    @JvmStatic
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun grantRuntimePermissionWithPersistentDeviceId(
        app: Application,
        persistentDeviceId: String,
        packageName: String,
        permission: String,
        userSet: Boolean
    ) {
        if (!SdkLevel.isAtLeastV() || isDefaultDeviceId(persistentDeviceId)) {
            return
        }
        val permissionManager = app.getSystemService(PermissionManager::class.java)!!
        var newFlag =
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED or
                FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
        if (userSet) {
            newFlag = newFlag or FLAG_PERMISSION_USER_SET
        }
        permissionManager.updatePermissionFlags(
            packageName,
            permission,
            persistentDeviceId,
            DEVICE_AWARE_PERMISSION_FLAG_MASK,
            newFlag
        )
        permissionManager.grantRuntimePermission(packageName, permission, persistentDeviceId)
    }

    /**
     * Revokes the external device permissions from the specified package. Permissions will be
     * extracted from the group name.
     *
     * @param app The current application
     * @param persistentDeviceId The external device identifier
     * @param packageName Name of the package to which permission needs to revoked
     * @param permissions Permissions that needs to be revoked
     * @param userSet Whether to mark the permission as user set
     * @param oneTime Whether this is a one-time permission grant permissions
     * @param reason The reason for the revoke, or {@code null} for unspecified
     *
     * TODO: b/328839130: This method is meant to use it on External Devices and on Device Aware
     *   permissions only. It does not follow the default device implementation because of the
     *   LightAppPermGroup requirement. The data class LightAppPermGroup is not available for
     *   external devices at present, hence the implementation differs.
     */
    @JvmStatic
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun revokeRuntimePermissionsWithPersistentDeviceId(
        app: Application,
        persistentDeviceId: String,
        packageName: String,
        permissions: Set<String>,
        userSet: Boolean,
        oneTime: Boolean,
        reason: String? = null
    ) {
        if (!SdkLevel.isAtLeastV() || isDefaultDeviceId(persistentDeviceId)) {
            return
        }
        permissions
            .filter { isPermissionDeviceAware(it) }
            .forEach { permission ->
                revokeRuntimePermissionWithPersistentDeviceId(
                    app,
                    persistentDeviceId,
                    packageName,
                    permission,
                    userSet,
                    oneTime,
                    reason
                )
            }
    }

    /**
     * Revokes the external device permission to the specified package.
     *
     * @param app The current application
     * @param persistentDeviceId The external device identifier
     * @param packageName Name of the package to which permission needs to revoked
     * @param permission Permission that needs to be revoked
     * @param userSet Whether to mark the permission as user set
     * @param oneTime Whether this is a one-time permission grant permissions
     * @param reason The reason for the revoke, or {@code null} for unspecified
     *
     * TODO: b/328839130: This method is meant to use it on External Devices and on Device Aware
     *   permissions only. It does not follow the default device implementation because of the
     *   LightAppPermGroup requirement. The data class LightAppPermGroup is not available for
     *   external devices at present, hence the implementation differs.
     */
    @JvmStatic
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun revokeRuntimePermissionWithPersistentDeviceId(
        app: Application,
        persistentDeviceId: String,
        packageName: String,
        permission: String,
        userSet: Boolean,
        oneTime: Boolean,
        reason: String? = null
    ) {
        if (!SdkLevel.isAtLeastV() || isDefaultDeviceId(persistentDeviceId)) {
            return
        }
        val permissionManager = app.getSystemService(PermissionManager::class.java)!!
        var newFlag =
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED or
                FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
        if (oneTime) {
            newFlag = newFlag or FLAG_PERMISSION_ONE_TIME
        }
        if (userSet) {
            newFlag = newFlag or FLAG_PERMISSION_USER_SET
        }
        if (isPermissionUserFixed(app, persistentDeviceId, packageName, permission) && !oneTime) {
            newFlag = newFlag or FLAG_PERMISSION_USER_FIXED
        }
        permissionManager.updatePermissionFlags(
            packageName,
            permission,
            persistentDeviceId,
            DEVICE_AWARE_PERMISSION_FLAG_MASK,
            newFlag
        )
        permissionManager.revokeRuntimePermission(
            packageName,
            permission,
            persistentDeviceId,
            reason
        )
    }

    /**
     * Determines if the permission is UserFixed. This method is for to use with V and above only.
     * Supports both external and default devices, need to specify persistentDeviceId accordingly.
     */
    @ChecksSdkIntAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun isPermissionUserFixed(
        app: Application,
        persistentDeviceId: String,
        packageName: String,
        permission: String
    ): Boolean {
        if (!SdkLevel.isAtLeastV()) {
            return true
        }
        val permissionManager = app.getSystemService(PermissionManager::class.java)!!
        val flags =
            permissionManager.getPermissionFlags(packageName, permission, persistentDeviceId)
        return flags and FLAG_PERMISSION_USER_FIXED != 0
    }
}
