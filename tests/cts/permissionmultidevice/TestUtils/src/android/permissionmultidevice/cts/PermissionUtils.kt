package android.permissionmultidevice.cts

import android.content.Context
import android.content.pm.PackageManager
import android.permission.PermissionManager
import android.permission.PermissionManager.PermissionState
import com.android.compatibility.common.util.SystemUtil

object PermissionUtils {
    fun getAllPermissionStates(
        context: Context,
        packageName: String,
        companionDeviceId: String
    ): Map<String, PermissionState> {
        val permissionManager = context.getSystemService(PermissionManager::class.java)!!
        return SystemUtil.runWithShellPermissionIdentity<Map<String, PermissionState>> {
            permissionManager.getAllPermissionStates(packageName, companionDeviceId)
        }
    }

    fun isAutomotive(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun isWatch(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
}
