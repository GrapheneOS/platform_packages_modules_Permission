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
import android.app.AppOpsManager
import android.app.ecm.EnhancedConfirmationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle
import android.permission.flags.Flags
import android.util.Log
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_CANCELLED
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_LEARN_MORE
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_OK
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_SUPPRESSED
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_UNSPECIFIED
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_APPOP
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_OTHER
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_PERMISSION
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_ROLE
import com.android.permissioncontroller.PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_UNSPECIFIED
import com.android.permissioncontroller.permission.utils.Utils

/**
 * Provides ECM-related metrics logging for PermissionController.
 *
 * @hide
 */
object EnhancedConfirmationStatsLogUtils {
    private val LOG_TAG = EnhancedConfirmationStatsLogUtils::class.java.simpleName

    enum class DialogResult(val statsLogValue: Int) {
        Unspecified(ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_UNSPECIFIED),
        Cancelled(ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_CANCELLED),
        LearnMore(ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_LEARN_MORE),
        Okay(ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_OK),
        Suppressed(ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__RESULT__RESULT_SUPPRESSED)
    }

    enum class SettingType(val statsLogValue: Int) {
        Unspecified(
            ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_UNSPECIFIED
        ),
        Appop(ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_APPOP),
        Permission(
            ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_PERMISSION
        ),
        Role(ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_ROLE),
        Other(ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED__SETTING_TYPE__SETTING_TYPE_OTHER);

        companion object {
            fun fromIdentifier(settingIdentifier: String): SettingType =
                when {
                    settingIdentifier.startsWith("android:") -> Appop
                    settingIdentifier.startsWith("android.permission.") -> Permission
                    settingIdentifier.startsWith("android.permission-group.") -> Permission
                    settingIdentifier.startsWith("android.app.role.") -> Role
                    else -> Other
                }
        }
    }

    fun logDialogResultReported(
        uid: Int,
        settingIdentifier: String,
        firstShowForApp: Boolean,
        dialogResult: DialogResult
    ) {
        if (!SdkLevel.isAtLeastV() || !Flags.enhancedConfirmationModeApisEnabled()) {
            return
        }
        val settingType = SettingType.fromIdentifier(settingIdentifier)

        Log.v(
            LOG_TAG,
            "ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED: " +
                "uid='$uid', " +
                "settingIdentifier='$settingIdentifier', " +
                "firstShowForApp='$firstShowForApp', " +
                "settingType='$settingType', " +
                "result='${dialogResult.statsLogValue}'"
        )

        PermissionControllerStatsLog.write(
            PermissionControllerStatsLog.ENHANCED_CONFIRMATION_DIALOG_RESULT_REPORTED,
            uid,
            settingIdentifier,
            firstShowForApp,
            settingType.statsLogValue,
            dialogResult.statsLogValue
        )
    }

    @SuppressLint("MissingPermission")
    fun isPackageEcmRestricted(context: Context, packageName: String, uid: Int): Boolean {
        if (!SdkLevel.isAtLeastV() || !Flags.enhancedConfirmationModeApisEnabled()) {
            return false
        }
        val userContext = Utils.getUserContext(context, UserHandle.getUserHandleForUid(uid))
        val ecm = userContext.getSystemService(EnhancedConfirmationManager::class.java)
        return try {
            val arbitrarilyChosenRestrictedSetting = AppOpsManager.OPSTR_BIND_ACCESSIBILITY_SERVICE
            ecm?.isRestricted(packageName, arbitrarilyChosenRestrictedSetting) ?: false
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
