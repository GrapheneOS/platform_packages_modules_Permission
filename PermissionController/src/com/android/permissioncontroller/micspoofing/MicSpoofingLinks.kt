package com.android.permissioncontroller.micspoofing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.GosPackageState
import android.ext.micspoofing.MicSpoofingApi
import android.os.UserHandle
import android.widget.Button
import androidx.annotation.RequiresPermission
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.android.permissioncontroller.permission.ui.handheld.ExtraPermissionLink

object MicSpoofingLinks : ExtraPermissionLink() {

    private val PERMISSION_GROUPS = setOf(
        Manifest.permission_group.MICROPHONE,
    )

    private fun isMicrophonePermissionGroup(name: String): Boolean {
        return PERMISSION_GROUPS.contains(name)
    }

    override fun isVisible(
        ctx: Context,
        groupName: String,
        packageName: String,
        user: UserHandle,
    ): Boolean {
        return isMicrophonePermissionGroup(groupName)
    }

    override fun setupDialogButton(button: Button) {
        button.setText(R.string.setup_microphone_spoofing)
    }

    override fun onDialogButtonClick(activity: GrantPermissionsActivity, packageName: String) {
        val intent = createConfigActivityIntent(packageName)
        @Suppress("DEPRECATION")
        activity.startActivityForResult(
            intent,
            GrantPermissionsActivity.REQ_CODE_SETUP_MICROPHONE_SPOOFING,
        )
    }

    override fun getSettingsDeniedRadioButtonSuffix(
        ctx: Context,
        packageState: GosPackageState,
    ): String? {
        if (!isMicSpoofingEnabled(packageState)) {
            return null
        }

        val micSpoofing = ctx.getString(R.string.microphone_spoofing)
        return " (+ $micSpoofing)"
    }

    override fun getSettingsLinkText(ctx: Context): CharSequence {
        return ctx.getText(R.string.setup_microphone_spoofing)
    }

    override fun getSettingsLinkIconResId(ctx: Context): Int {
        return R.drawable.ic_settings
    }

    @RequiresPermission(Manifest.permission.INTERACT_ACROSS_USERS)
    override fun onSettingsLinkClick(ctx: Context, packageName: String, user: UserHandle) {
        ctx.startActivityAsUser(createConfigActivityIntent(packageName), user)
    }

    private fun createConfigActivityIntent(packageName: String): Intent {
        return MicSpoofingApi.createConfigActivityIntent(packageName)
    }
}
