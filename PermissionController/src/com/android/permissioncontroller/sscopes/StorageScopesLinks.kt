package com.android.permissioncontroller.sscopes

import android.app.StorageScope
import android.content.Context
import android.content.pm.GosPackageState
import android.os.UserHandle
import android.widget.Button
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.android.permissioncontroller.permission.ui.handheld.ExtraPermissionLink

object StorageScopesLinks : ExtraPermissionLink() {

    override fun isVisible(ctx: Context, groupName: String, packageName: String, user: UserHandle) =
            StorageScopesUtils.isStoragePermissionGroup(groupName)

    override fun setupDialogButton(button: Button) {
        button.setText(R.string.setup_storage_scopes)
    }

    override fun onDialogButtonClick(activity: GrantPermissionsActivity, packageName: String) {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(StorageScope.createConfigActivityIntent(packageName),
                GrantPermissionsActivity.REQ_CODE_SETUP_STORAGE_SCOPES)
    }

    override fun getSettingsDeniedRadioButtonSuffix(ctx: Context, packageState: GosPackageState): String? {
        if (StorageScopesUtils.isStorageScopesEnabled(packageState)) {
            return " (+ " + ctx.getString(R.string.sscopes) + ")"
        }

        return null
    }

    override fun getSettingsLinkText(ctx: Context): CharSequence {
        return ctx.getText(R.string.sscopes)
    }

    override fun onSettingsLinkClick(ctx: Context, packageName: String, user: UserHandle) {
        val intent = StorageScope.createConfigActivityIntent(packageName)
        ctx.startActivityAsUser(intent, user)
    }
}
