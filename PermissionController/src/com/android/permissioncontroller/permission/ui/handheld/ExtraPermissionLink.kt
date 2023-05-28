package com.android.permissioncontroller.permission.ui.handheld

import android.content.Context
import android.content.pm.GosPackageState
import android.os.UserHandle
import android.widget.Button
import com.android.permissioncontroller.cscopes.ContactScopesLinks
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.android.permissioncontroller.sscopes.StorageScopesLinks

abstract class ExtraPermissionLink {
    abstract fun isVisible(ctx: Context, groupName: String, packageName: String, user: UserHandle): Boolean

    abstract fun setupDialogButton(button: Button)

    abstract fun onDialogButtonClick(activity: GrantPermissionsActivity, packageName: String)

    open fun isAllowPermissionSettingsButtonBlocked(ctx: Context, packageName: String, user: UserHandle): Boolean = false

    open fun onAllowPermissionSettingsButtonClick(ctx: Context) {}

    abstract fun getSettingsDeniedRadioButtonSuffix(ctx: Context, packageState: GosPackageState): String?

    abstract fun getSettingsLinkText(ctx: Context): CharSequence

    abstract fun onSettingsLinkClick(ctx: Context, packageName: String, user: UserHandle)
}

private val allExtraPermissionLinks = arrayOf(
        StorageScopesLinks,
        ContactScopesLinks,
)

fun getExtraPermissionLink(ctx: Context, packageName: String, user: UserHandle, groupName: String): ExtraPermissionLink? {
    return allExtraPermissionLinks.find { it.isVisible(ctx, groupName, packageName, user) }
}
