package com.android.permissioncontroller.permission.utils

import android.Manifest
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.DeviceUtils

object MultiDeviceUtils {
    const val DEFAULT_REMOTE_DEVICE_NAME = "remote device"

    /**
     * Defines what runtime permissions are device aware. This can be replaced with an API from VDM
     * which can take device's capabilities into account
     */
    // TODO: b/298661870 - Use new API to get the list of device aware permissions
    private val DEVICE_AWARE_PERMISSIONS: Set<String> =
        setOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

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
}
