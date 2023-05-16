package com.android.permissioncontroller.ext

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.android.permissioncontroller.permission.utils.Utils

object ScopesUtils {

    fun getGroupPerms(groups: Set<String>): List<String> {
        return groups.fold(ArrayList(groups.size * 4)) { list, group ->
            list.addAll(Utils.getPlatformPermissionNamesOfGroup(group))
            list
        }
    }

    // returns whether at least one permission was revoked
    fun revokePermissions(ctx: Context, pkgName: String,
                          perms: List<String>, appOpPerms: Array<String> = emptyArray()): Boolean {
        val pm = ctx.packageManager
        val uid: Int
        val targetSdk: Int
        try {
            uid = pm.getPackageUid(pkgName, 0)
            targetSdk = pm.getApplicationInfo(pkgName, 0).targetSdkVersion
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }

        val appOps = ctx.getSystemService(AppOpsManager::class.java)!!
        val user = Process.myUserHandle()
        var numOfRevokations = 0
        for (permission in perms) {
            if (targetSdk >= 23) { // runtime permissions are always granted for targetSdk < 23 apps
                if (pm.checkPermission(permission, pkgName) == PackageManager.PERMISSION_GRANTED) {
                    pm.revokeRuntimePermission(pkgName, permission, user, "Scopes")
                    val permFlag = PackageManager.FLAG_PERMISSION_USER_SET
                    pm.updatePermissionFlags(permission, pkgName, permFlag, permFlag, user)
                    ++numOfRevokations
                }
            }
            val op = AppOpsManager.permissionToOp(permission) ?: continue
            if (AppOpsManager.opToDefaultMode(op) != AppOpsManager.MODE_ALLOWED
                    // unsafeCheckOpNoThrow is a better option than noteOpNoThrow for this particular use-case
                    && appOps.unsafeCheckOpNoThrow(op, uid, pkgName) == AppOpsManager.MODE_ALLOWED) {
                appOps.setUidMode(op, uid, AppOpsManager.MODE_IGNORED)
                ++numOfRevokations
            }
        }

        for (opPerm in appOpPerms) {
            val op = AppOpsManager.permissionToOp(opPerm)
            if (appOps.unsafeCheckOpNoThrow(op!!, uid, pkgName) == AppOpsManager.MODE_ALLOWED) {
                appOps.setUidMode(op, uid, AppOpsManager.MODE_ERRORED)
                ++numOfRevokations
            }
        }
        return numOfRevokations != 0
    }
}
