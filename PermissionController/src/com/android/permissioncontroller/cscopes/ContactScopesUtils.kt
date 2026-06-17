package com.android.permissioncontroller.cscopes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.GosPackageState
import android.content.pm.GosPackageStateFlag
import android.os.UserHandle
import com.android.permissioncontroller.ext.ScopesUtils

const val BUNDLED_CONTACTS_APP_PACKAGE = "com.android.contacts"

private val PERMISSION_GROUPS = setOf(
        Manifest.permission_group.CONTACTS
)

object ContactScopesUtils {

    fun isContactsPermissionGroup(name: String) = PERMISSION_GROUPS.contains(name)

    @JvmStatic
    fun isContactScopesEnabled(packageName: String, user: UserHandle): Boolean {
        return isContactScopesEnabled(GosPackageState.get(packageName, user))
    }

    fun isContactScopesEnabled(ps: GosPackageState): Boolean {
        return ps.hasFlag(GosPackageStateFlag.CONTACT_SCOPES_ENABLED)
    }

    fun revokeContactPermissions(ctx: Context, pkgName: String): Boolean {
        val perms = ScopesUtils.getGroupPerms(PERMISSION_GROUPS)
        return ScopesUtils.revokePermissions(ctx, pkgName, perms)
    }
}
