/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.ecm

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.app.ecm.EnhancedConfirmationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.permission.flags.Flags
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.Keep
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants.EXTRA_IS_ECM_IN_APP
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ecm.EnhancedConfirmationStatsLogUtils.DialogResult
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.Utils
import com.android.role.controller.model.Roles

@Keep
class EnhancedConfirmationDialogActivity : FragmentActivity() {
    companion object {
        private const val KEY_WAS_CLEAR_RESTRICTION_ALLOWED = "KEY_WAS_CLEAR_RESTRICTION_ALLOWED"
    }

    private var wasClearRestrictionAllowed: Boolean = false
    private var dialogResult: DialogResult = DialogResult.Cancelled

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM, codename = "VanillaIceCream")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SdkLevel.isAtLeastV() || !Flags.enhancedConfirmationModeApisEnabled()) {
            finish()
            return
        }
        if (savedInstanceState != null) {
            wasClearRestrictionAllowed =
                savedInstanceState.getBoolean(KEY_WAS_CLEAR_RESTRICTION_ALLOWED)
            return
        }

        val uid = intent.getIntExtra(Intent.EXTRA_UID, Process.INVALID_UID)
        val packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        val settingIdentifier = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val isEcmInApp = intent.getBooleanExtra(EXTRA_IS_ECM_IN_APP, false)

        require(uid != Process.INVALID_UID) { "EXTRA_UID cannot be null or invalid" }
        require(!packageName.isNullOrEmpty()) { "EXTRA_PACKAGE_NAME cannot be null or empty" }
        require(!settingIdentifier.isNullOrEmpty()) { "EXTRA_SUBJECT cannot be null or empty" }

        wasClearRestrictionAllowed =
            setClearRestrictionAllowed(packageName, UserHandle.getUserHandleForUid(uid))

        val setting = Setting.fromIdentifier(this, settingIdentifier, isEcmInApp)
        val dialogFragment =
            EnhancedConfirmationDialogFragment.newInstance(setting.title, setting.message)
        dialogFragment.show(supportFragmentManager, EnhancedConfirmationDialogFragment.TAG)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAS_CLEAR_RESTRICTION_ALLOWED, wasClearRestrictionAllowed)
    }

    private fun setClearRestrictionAllowed(packageName: String, user: UserHandle): Boolean {
        val userContext = createContextAsUser(user, 0)
        val ecm = Utils.getSystemServiceSafe(userContext, EnhancedConfirmationManager::class.java)
        try {
            val wasClearRestrictionAllowed = ecm.isClearRestrictionAllowed(packageName)
            ecm.setClearRestrictionAllowed(packageName)
            return wasClearRestrictionAllowed
        } catch (e: PackageManager.NameNotFoundException) {
            throw IllegalArgumentException("unknown package: $packageName")
        }
    }

    private data class Setting(val title: String?, val message: CharSequence?) {
        companion object {
            fun fromIdentifier(
                context: Context,
                settingIdentifier: String,
                isEcmInApp: Boolean
            ): Setting {
                val settingType = SettingType.fromIdentifier(context, settingIdentifier, isEcmInApp)
                val label =
                    when (settingType) {
                        SettingType.PLATFORM_PERMISSION ->
                            KotlinUtils.getPermGroupLabel(
                                context,
                                PermissionMapping.getGroupOfPlatformPermission(settingIdentifier)!!
                            )
                        SettingType.PLATFORM_PERMISSION_GROUP ->
                            KotlinUtils.getPermGroupLabel(context, settingIdentifier)
                        SettingType.ROLE ->
                            context.getString(
                                Roles.get(context)[settingIdentifier]!!.shortLabelResource
                            )
                        SettingType.OTHER -> null
                    }
                val url =
                    context.getString(R.string.help_url_action_disabled_by_restricted_settings)
                return Setting(
                    title = settingType.titleRes?.let { context.getString(it, label) },
                    message =
                        settingType.messageRes?.let { Html.fromHtml(context.getString(it, url), 0) }
                )
            }
        }
    }

    private enum class SettingType(val titleRes: Int?, val messageRes: Int?) {
        PLATFORM_PERMISSION(
            R.string.enhanced_confirmation_dialog_title_permission,
            R.string.enhanced_confirmation_dialog_desc_permission
        ),
        PLATFORM_PERMISSION_GROUP(
            R.string.enhanced_confirmation_dialog_title_permission,
            R.string.enhanced_confirmation_dialog_desc_permission
        ),
        ROLE(
            R.string.enhanced_confirmation_dialog_title_role,
            R.string.enhanced_confirmation_dialog_desc_role
        ),
        OTHER(
            R.string.enhanced_confirmation_dialog_title_settings_default,
            R.string.enhanced_confirmation_dialog_desc_settings_default
        );

        companion object {
            fun fromIdentifier(
                context: Context,
                settingIdentifier: String,
                isEcmInApp: Boolean
            ): SettingType {
                if (!isEcmInApp) return SettingType.OTHER
                return when {
                    PermissionMapping.isRuntimePlatformPermission(settingIdentifier) &&
                        PermissionMapping.getGroupOfPlatformPermission(settingIdentifier) != null ->
                        PLATFORM_PERMISSION
                    PermissionMapping.isPlatformPermissionGroup(settingIdentifier) ->
                        PLATFORM_PERMISSION_GROUP
                    settingIdentifier.startsWith("android.app.role.") &&
                        Roles.get(context).containsKey(settingIdentifier) -> ROLE
                    else -> SettingType.OTHER
                }
            }
        }
    }

    private fun onDialogResult(dialogResult: DialogResult) {
        this.dialogResult = dialogResult
        setResult(
            RESULT_OK,
            Intent().apply { putExtra(Intent.EXTRA_RETURN_RESULT, dialogResult.statsLogValue) }
        )
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            EnhancedConfirmationStatsLogUtils.logDialogResultReported(
                uid = intent.getIntExtra(Intent.EXTRA_UID, Process.INVALID_UID),
                settingIdentifier = intent.getStringExtra(Intent.EXTRA_SUBJECT)!!,
                firstShowForApp = !wasClearRestrictionAllowed,
                dialogResult = dialogResult
            )
        }
    }

    class EnhancedConfirmationDialogFragment() : DialogFragment() {
        companion object {
            val TAG = EnhancedConfirmationDialogFragment::class.simpleName
            private const val KEY_TITLE = "KEY_TITLE"
            private const val KEY_MESSAGE = "KEY_MESSAGE"

            fun newInstance(title: String? = null, message: CharSequence? = null) =
                EnhancedConfirmationDialogFragment().apply {
                    arguments =
                        Bundle().apply {
                            putString(KEY_TITLE, title)
                            putCharSequence(KEY_MESSAGE, message)
                        }
                }
        }

        private lateinit var dialogActivity: EnhancedConfirmationDialogActivity

        override fun onAttach(context: Context) {
            super.onAttach(context)
            dialogActivity = context as EnhancedConfirmationDialogActivity
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val title = arguments!!.getString(KEY_TITLE)
            val message = arguments!!.getCharSequence(KEY_MESSAGE)

            return AlertDialog.Builder(dialogActivity)
                .setView(createDialogView(dialogActivity, title, message))
                .setPositiveButton(R.string.enhanced_confirmation_dialog_ok) { _, _ ->
                    dialogActivity.onDialogResult(DialogResult.Okay)
                }
                .create()
        }

        override fun onCancel(dialog: DialogInterface) {
            super.onCancel(dialog)
            dialogActivity.onDialogResult(DialogResult.Cancelled)
        }

        @SuppressLint("InflateParams")
        private fun createDialogView(
            context: Context,
            title: String?,
            message: CharSequence?
        ): View =
            LayoutInflater.from(context)
                .inflate(R.layout.enhanced_confirmation_dialog, null)
                .apply {
                    title?.let {
                        requireViewById<TextView>(R.id.enhanced_confirmation_dialog_title).text = it
                    }
                    message?.let {
                        val descTextView =
                            requireViewById<TextView>(R.id.enhanced_confirmation_dialog_desc)
                        descTextView.text = it
                        descTextView.movementMethod = LinkMovementMethod.getInstance()
                    }
                }
    }
}
