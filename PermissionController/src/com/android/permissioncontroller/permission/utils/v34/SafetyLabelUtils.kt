/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.utils.v34

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.APP_METADATA_SOURCE_APK
import android.util.Log
import com.android.modules.utils.build.SdkLevel
import com.android.permission.safetylabel.DataCategory
import com.android.permission.safetylabel.DataType
import com.android.permission.safetylabel.DataTypeConstants
import com.android.permission.safetylabel.SafetyLabel
import com.android.permissioncontroller.permission.utils.PermissionMapping

object SafetyLabelUtils {
    private val LOG_TAG = SafetyLabelUtils::class.java.simpleName

    /*
     * Get the sharing purposes for a SafetyLabel related to a specific permission group.
     */
    @JvmStatic
    fun getSafetyLabelSharingPurposesForGroup(
        safetyLabel: SafetyLabel,
        groupName: String
    ): Set<Int> {
        val purposeSet = mutableSetOf<Int>()
        val categoriesForPermission =
            PermissionMapping.getDataCategoriesForPermissionGroup(groupName)
        categoriesForPermission.forEach categoryLoop@{ category ->
            val dataCategory: DataCategory? = safetyLabel.dataLabel.dataShared[category]
            if (dataCategory == null) {
                // Continue to next
                return@categoryLoop
            }
            val typesForCategory = DataTypeConstants.getValidDataTypesForCategory(category)
            typesForCategory.forEach typeLoop@{ type ->
                val dataType: DataType? = dataCategory.dataTypes[type]
                if (dataType == null) {
                    // Continue to next
                    return@typeLoop
                }
                if (dataType.purposeSet.isNotEmpty()) {
                    purposeSet.addAll(dataType.purposeSet)
                }
            }
        }

        return purposeSet
    }

    /**
     * Returns the {@code TRUE} if [AppMetadataSource] for the given package is
     * supported for permission rationale, as well as for U- where getAppMetadataSource isn't
     * available.
     */
    fun isAppMetadataSourceSupported(userContext: Context, packageName: String): Boolean {
        if (!SdkLevel.isAtLeastV() || !android.content.pm.Flags.aslInApkAppMetadataSource()) {
            // PackageManager.getAppMetadataSource() is not available and ASL in APK is ignored in
            // U and below. We can assume it came from oem/pre-install or installer source (app
            // store). Treat this as AppMetadataSource allowed.
            return true
        }

        return try {
            userContext.packageManager.getAppMetadataSource(packageName) != APP_METADATA_SOURCE_APK
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(LOG_TAG, "AppMetadataSource for $packageName not found")
            false
        }
    }
}
