package com.android.permissioncontroller.mscopes

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.GosPackageState
import android.os.UserHandle
import com.android.permissioncontroller.ext.ScopesUtils

private val PERMISSION_GROUPS = setOf(
        Manifest.permission_group.MICROPHONE
)

object MicrophoneScopesUtils {
    // This must match GosPackageStateFlag.MICROPHONE_SCOPES_ENABLED
    const val FLAG_MICROPHONE_SCOPES_ENABLED = 29

    fun isMicrophonePermissionGroup(name: String) = PERMISSION_GROUPS.contains(name)

    @JvmStatic
    fun isMicrophoneScopesEnabled(packageName: String, user: UserHandle): Boolean {
        return isMicrophoneScopesEnabled(GosPackageState.get(packageName, user))
    }

    fun isMicrophoneScopesEnabled(ps: GosPackageState): Boolean {
        return ps.hasFlag(FLAG_MICROPHONE_SCOPES_ENABLED)
    }

    fun revokeMicrophonePermissions(ctx: Context, pkgName: String): Boolean {
        val perms = ScopesUtils.getGroupPerms(PERMISSION_GROUPS)
        return ScopesUtils.revokePermissions(ctx, pkgName, perms)
    }

    fun createConfigActivityIntent(targetPkg: String): Intent {
        val i = Intent()
        val pkg = "com.android.permissioncontroller"
        i.component = ComponentName.createRelative(pkg, ".mscopes.MicrophoneScopesActivity")
        i.putExtra(Intent.EXTRA_PACKAGE_NAME, targetPkg)
        return i
    }
}
