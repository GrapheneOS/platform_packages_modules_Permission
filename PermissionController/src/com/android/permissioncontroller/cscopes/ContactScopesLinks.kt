package com.android.permissioncontroller.cscopes

import android.content.Context
import android.content.pm.GosPackageState
import android.ext.cscopes.ContactScopesApi
import android.os.UserHandle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.NavHostFragment
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.PackageExtraConfigFragment
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.android.permissioncontroller.permission.ui.handheld.ExtraPermissionLink

object ContactScopesLinks : ExtraPermissionLink() {

    override fun isVisible(ctx: Context, groupName: String, packageName: String, user: UserHandle): Boolean {
        if (!ContactScopesUtils.isContactsPermissionGroup(groupName)) {
            return false
        }

        return when (packageName) {
            BUNDLED_CONTACTS_APP_PACKAGE,
            // Google Contacts app connects to com.google.android.gms.people.gal.provider in GmsCore,
            // which enforces the READ_CONTACTS permission.
            // This is fundamentally incompatible with Contact Scopes
            "com.google.android.contacts",
            -> false

            else -> true
        }
    }

    override fun setupDialogButton(button: Button) {
        button.setText(R.string.setup_contact_scopes)
    }

    override fun onDialogButtonClick(activity: GrantPermissionsActivity, packageName: String) {
        val intent = ContactScopesApi.createConfigActivityIntent(packageName)
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, GrantPermissionsActivity.REQ_CODE_SETUP_CONTACT_SCOPES)
    }

    override fun isAllowPermissionSettingsButtonBlocked(ctx: Context, packageName: String,
                                                        user: UserHandle): Boolean {
        return ContactScopesUtils.isContactScopesEnabled(packageName, user)
    }

    override fun onAllowPermissionSettingsButtonClick(ctx: Context) {
        AlertDialog.Builder(ctx).run {
            setMessage(R.string.cscopes_allow_contacts_permission_blocked_msg)
            show()
        }
    }

    override fun getSettingsDeniedRadioButtonSuffix(ctx: Context, packageState: GosPackageState): String? {
        if (!ContactScopesUtils.isContactScopesEnabled(packageState)) {
            return null
        }

        val cscopes = ctx.getString(R.string.contact_scopes)
        return " (+ $cscopes)"
    }

    override fun getSettingsLinkText(ctx: Context): CharSequence {
        return ctx.getText(R.string.contact_scopes)
    }

    override fun onSettingsLinkClick(ctx: Context, packageName: String, user: UserHandle) {
        val intent = ContactScopesApi.createConfigActivityIntent(packageName)
        ctx.startActivityAsUser(intent, user)
    }
}
