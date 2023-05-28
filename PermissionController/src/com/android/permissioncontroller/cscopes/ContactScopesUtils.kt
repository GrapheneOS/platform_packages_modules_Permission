package com.android.permissioncontroller.cscopes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.GosPackageState
import com.android.permissioncontroller.ext.ScopesUtils

const val BUNDLED_CONTACTS_APP_PACKAGE = "com.android.contacts"

private const val CSCOPES_PREFS_NAME = "cscopes"
private const val PREF_KEY_ALLOW_CUSTOM_CONTACTS_APP = "custom_contacts_app_allowed"
private const val PREF_DEFAULT_ALLOW_CUSTOM_CONTACTS_APP = false

private val PERMISSION_GROUPS = setOf(
        Manifest.permission_group.CONTACTS
)

object ContactScopesUtils {

    fun isContactsPermissionGroup(name: String) = PERMISSION_GROUPS.contains(name)

    @JvmStatic
    fun isContactScopesEnabled(packageName: String): Boolean {
        val ps = GosPackageState.get(packageName)
        return isContactScopesEnabled(ps)
    }

    fun isContactScopesEnabled(ps: GosPackageState?): Boolean {
        return ps != null && ps.hasFlag(GosPackageState.FLAG_CONTACT_SCOPES_ENABLED)
    }

    private fun getPrefs(ctx: Context) = ctx.getSharedPreferences(CSCOPES_PREFS_NAME, Context.MODE_PRIVATE)

    fun maybeSpecifyPackage(ctx: Context, i: Intent) {
        if (!isCustomContactsAppAllowed(ctx)) {
            i.`package` = BUNDLED_CONTACTS_APP_PACKAGE
        }
    }

    fun isCustomContactsAppAllowed(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(
                PREF_KEY_ALLOW_CUSTOM_CONTACTS_APP,
                PREF_DEFAULT_ALLOW_CUSTOM_CONTACTS_APP
        )
    }

    fun setCustomContactsAppAllowed(ctx: Context, v: Boolean) {
        getPrefs(ctx).edit().run {
            putBoolean(PREF_KEY_ALLOW_CUSTOM_CONTACTS_APP, v)
            apply()
        }
    }

    fun revokeContactPermissions(ctx: Context, pkgName: String): Boolean {
        val perms = ScopesUtils.getGroupPerms(PERMISSION_GROUPS)
        return ScopesUtils.revokePermissions(ctx, pkgName, perms)
    }
}
