package android.permissionmultidevice.cts

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import org.junit.Assert

object PermissionUtils {
    fun assertAppHasPermissionForDevice(
        context: Context,
        packageName: String,
        permissionName: String,
        deviceId: Int,
        expectPermissionGranted: Boolean
    ) {
        val checkPermissionResult =
            context
                .createDeviceContext(deviceId)
                .packageManager
                .checkPermission(permissionName, packageName)

        if (expectPermissionGranted) {
            Assert.assertEquals(PackageManager.PERMISSION_GRANTED, checkPermissionResult)
        } else {
            Assert.assertEquals(PackageManager.PERMISSION_DENIED, checkPermissionResult)
        }
    }

    fun getHostDeviceName(context: Context): String {
        return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }
}
