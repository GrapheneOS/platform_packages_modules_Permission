package com.android.permissioncontroller.permission.ui.handheld

import android.content.Context
import android.content.pm.GosPackageState
import android.widget.Button
import com.android.permissioncontroller.cscopes.ContactScopesLinks
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.android.permissioncontroller.sscopes.StorageScopesLinks

abstract class ExtraPermissionLink {
    abstract fun isVisible(ctx: Context, groupName: String, packageName: String): Boolean

    abstract fun setupDialogButton(button: Button)

    abstract fun onDialogButtonClick(activity: GrantPermissionsActivity, packageName: String)

    open fun isAllowPermissionSettingsButtonBlocked(ctx: Context, packageName: String): Boolean = false

    open fun onAllowPermissionSettingsButtonClick(ctx: Context, packageName: String) {}

    abstract fun getSettingsDeniedRadioButtonSuffix(ctx: Context, packageName: String, packageState: GosPackageState?): String?

    abstract fun getSettingsLinkText(ctx: Context, packageName: String, packageState: GosPackageState?): CharSequence

    abstract fun onSettingsLinkClick(fragment: AppPermissionFragment, packageName: String, packageState: GosPackageState?)
}

private val allExtraPermissionLinks = arrayOf(
        StorageScopesLinks,
        ContactScopesLinks,
)

fun getExtraPermissionLink(ctx: Context, packageName: String, groupName: String): ExtraPermissionLink? {
    val btn = allExtraPermissionLinks.find { it.isVisible(ctx, groupName, packageName) }

    if (btn != null && !GosPackageState.attachableToPackage(packageName)) {
        return null
    }

    return btn
}
