package com.android.permissioncontroller.sscopes

import android.app.StorageScope
import android.content.Context
import android.content.pm.GosPackageState
import android.widget.Button
import androidx.navigation.fragment.NavHostFragment
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.PackageExtraConfigFragment
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionFragment
import com.android.permissioncontroller.permission.ui.handheld.ExtraPermissionLink

object StorageScopesLinks : ExtraPermissionLink() {

    override fun isVisible(ctx: Context, groupName: String, packageName: String) =
            StorageScopesUtils.isStoragePermissionGroup(groupName)

    override fun setupDialogButton(button: Button) {
        button.setText(R.string.setup_storage_scopes)
    }

    override fun onDialogButtonClick(activity: GrantPermissionsActivity, packageName: String) {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(StorageScope.createConfigActivityIntent(packageName),
                GrantPermissionsActivity.REQ_CODE_SETUP_STORAGE_SCOPES)
    }

    override fun getSettingsDeniedRadioButtonSuffix(ctx: Context, packageName: String,
                                                    packageState: GosPackageState?): String? {
        if (StorageScopesUtils.storageScopesEnabled(packageState)) {
            return " (+ " + ctx.getString(R.string.sscopes) + ")"
        }

        return null
    }

    override fun getSettingsLinkText(ctx: Context, packageName: String, packageState: GosPackageState?): CharSequence {
        return ctx.getText(R.string.sscopes)
    }

    override fun onSettingsLinkClick(fragment: AppPermissionFragment, packageName: String, packageState: GosPackageState?) {
        val args = PackageExtraConfigFragment.createArgs(packageName)
        NavHostFragment.findNavController(fragment).navigate(R.id.storage_scopes, args)
    }
}
