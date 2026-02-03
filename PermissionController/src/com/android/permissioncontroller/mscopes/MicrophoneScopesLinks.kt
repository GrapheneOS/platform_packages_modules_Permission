package com.android.permissioncontroller.mscopes

import android.content.Context
import android.content.pm.GosPackageState
import android.os.UserHandle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.android.permissioncontroller.permission.ui.handheld.ExtraPermissionLink

object MicrophoneScopesLinks : ExtraPermissionLink() {

    override fun isVisible(ctx: Context, groupName: String, packageName: String, user: UserHandle): Boolean {
        if (!MicrophoneScopesUtils.isMicrophonePermissionGroup(groupName)) {
            return false
        }
        return true
    }

    override fun setupDialogButton(button: Button) {
        button.setText(R.string.setup_microphone_scopes)
    }

    override fun onDialogButtonClick(activity: GrantPermissionsActivity, packageName: String) {
        val intent = MicrophoneScopesUtils.createConfigActivityIntent(packageName)
        @Suppress("DEPRECATION")
        activity.startActivityForResult(intent, GrantPermissionsActivity.REQ_CODE_SETUP_MICROPHONE_SCOPES)
    }

    override fun isAllowPermissionSettingsButtonBlocked(ctx: Context, packageName: String,
                                                        user: UserHandle): Boolean {
        return MicrophoneScopesUtils.isMicrophoneScopesEnabled(packageName, user)
    }

    override fun onAllowPermissionSettingsButtonClick(ctx: Context) {
        AlertDialog.Builder(ctx).run {
            setMessage(R.string.mscopes_allow_microphone_permission_blocked_msg)
            show()
        }
    }

    override fun getSettingsDeniedRadioButtonSuffix(ctx: Context, packageState: GosPackageState): String? {
        if (!MicrophoneScopesUtils.isMicrophoneScopesEnabled(packageState)) {
            return null
        }

        val mscopes = ctx.getString(R.string.microphone_scopes)
        return " (+ $mscopes)"
    }

    override fun getSettingsLinkText(ctx: Context): CharSequence {
        return ctx.getText(R.string.microphone_scopes)
    }

    override fun onSettingsLinkClick(ctx: Context, packageName: String, user: UserHandle) {
        val intent = MicrophoneScopesUtils.createConfigActivityIntent(packageName)
        ctx.startActivityAsUser(intent, user)
    }
}
