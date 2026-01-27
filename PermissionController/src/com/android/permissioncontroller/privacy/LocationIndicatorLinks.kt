package com.android.permissioncontroller.privacy

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.GosPackageState
import android.os.UserHandle
import android.widget.Button
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.android.permissioncontroller.permission.ui.handheld.ExtraPermissionLink

object LocationIndicatorLinks : ExtraPermissionLink() {

    override fun isVisible(
        ctx: Context,
        groupName: String,
        packageName: String,
        user: UserHandle
    ): Boolean {
        // dont show on permission dialog requests or system apps
        val inPermissionDialog = ctx is GrantPermissionsActivity
        return groupName == Manifest.permission_group.LOCATION
                && !inPermissionDialog
                && !isSystemApp(ctx, user, packageName)
    }

    override fun setupDialogButton(button: Button) {
        button.setText(R.string.location_indicator)
    }

    override fun onDialogButtonClick(activity: GrantPermissionsActivity, packageName: String) {
        val intent = createConfigActivityIntent(packageName)
        @Suppress("DEPRECATION")
        activity.startActivityForResult(
            intent,
            GrantPermissionsActivity.REQ_CODE_SETUP_LOCATION_INDICATOR
        )
    }

    override fun isAllowPermissionSettingsButtonBlocked(
        ctx: Context, packageName: String,
        user: UserHandle
    ): Boolean {
        return false
    }

    override fun onAllowPermissionSettingsButtonClick(ctx: Context) {
    }

    override fun getSettingsDeniedRadioButtonSuffix(
        ctx: Context,
        packageState: GosPackageState
    ): String? {
        return null
    }

    override fun getSettingsLinkText(ctx: Context): CharSequence {
        return ctx.getText(R.string.location_indicator)
    }

    override fun onSettingsLinkClick(ctx: Context, packageName: String, user: UserHandle) {
        val intent = createConfigActivityIntent(packageName)
        ctx.startActivityAsUser(intent, user)
    }

    fun createConfigActivityIntent(targetPkg: String): Intent {
        val i = Intent()
        val pkg = "com.android.permissioncontroller"
        i.setComponent(ComponentName.createRelative(pkg, ".privacy.LocationIndicatorActivity"))
        i.putExtra(Intent.EXTRA_PACKAGE_NAME, targetPkg)
        return i
    }

    fun isSystemApp(ctx: Context, userHandle: UserHandle, packageName: String): Boolean {
        return try {
            val appInfo = ctx.packageManager.getApplicationInfoAsUser(
                packageName,
                0,
                userHandle
            )
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: Exception) {
            false
        }
    }
}

