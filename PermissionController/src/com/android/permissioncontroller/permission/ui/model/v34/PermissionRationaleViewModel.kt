/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.model.v34

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.core.util.Consumer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permission.safetylabel.DataCategory
import com.android.permission.safetylabel.DataType
import com.android.permission.safetylabel.DataTypeConstants
import com.android.permission.safetylabel.SafetyLabel
import com.android.permissioncontroller.permission.data.SafetyLabelInfoLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.SafetyLabelInfo.Companion.UNAVAILABLE
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.KotlinUtils.getAppStoreIntent
import com.android.permissioncontroller.permission.utils.SafetyLabelPermissionMapping

/**
 * [ViewModel] for the [PermissionRationaleActivity]. Gets all information required safety label and
 * links required to inform user of data sharing usages by the app when granting this permission
 *
 * @param app: The current application
 * @param packageName: The packageName permissions are being requested for
 * @param permissionGroupName: The permission group requested
 * @param sessionId: A long to identify this session
 * @param storedState: Previous state, if this activity was stopped and is being recreated
 */
class PermissionRationaleViewModel(
    private val app: Application,
    private val packageName: String,
    private val permissionGroupName: String,
    // TODO(b/259961958): add PermissionRationale metrics
    private val sessionId: Long,
    private val storedState: Bundle?
) : ViewModel() {
    private val user = Process.myUserHandle()
    private val safetyLabelInfoLiveData = SafetyLabelInfoLiveData[packageName, user]

    var activityResultCallback: Consumer<Intent>? = null

    /**
     * A class which represents a permission rationale for permission group, and messages which
     * should be shown with it.
     */
    data class PermissionRationaleInfo(
        val groupName: String,
        val installSourcePackageName: String?,
        val installSourceLabel: CharSequence?,
        val purposeSet: Set<Int>
    )

    /** A [LiveData] which holds the currently pending PermissionRationaleInfo */
    val permissionRationaleInfoLiveData =
        object : SmartUpdateMediatorLiveData<PermissionRationaleInfo>() {

            init {
                addSource(safetyLabelInfoLiveData) { onUpdate() }

                // Load package state, if available
                onUpdate()
            }

            override fun onUpdate() {
                if (safetyLabelInfoLiveData.isStale) {
                    return
                }

                val safetyLabelInfo = safetyLabelInfoLiveData.value
                val safetyLabel = safetyLabelInfo?.safetyLabel

                if (safetyLabelInfo == null ||
                    safetyLabelInfo == UNAVAILABLE ||
                    safetyLabel == null) {
                    Log.e(LOG_TAG, "Safety label for $packageName not found")
                    value = null
                    return
                }

                val installSourcePackageName = safetyLabelInfo.installSourcePackageName
                val installSourceLabel: CharSequence? =
                    installSourcePackageName?.let {
                        KotlinUtils.getPackageLabel(app, it, Process.myUserHandle())
                    }

                value =
                    PermissionRationaleInfo(
                        permissionGroupName,
                        installSourcePackageName,
                        installSourceLabel,
                        getSafetyLabelSharingPurposesForGroup(safetyLabel, permissionGroupName))
            }

            private fun getSafetyLabelSharingPurposesForGroup(
                safetyLabel: SafetyLabel,
                groupName: String
            ): Set<Int> {
                val purposeSet = mutableSetOf<Int>()
                val categoriesForPermission: List<String> =
                    SafetyLabelPermissionMapping.getCategoriesForPermissionGroup(groupName)
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
        }

    fun canLinkToAppStore(context: Context, installSourcePackageName: String): Boolean {
        return getAppStoreIntent(context, installSourcePackageName, packageName) != null
    }

    fun sendToAppStore(context: Context, installSourcePackageName: String) {
        val storeIntent = getAppStoreIntent(context, installSourcePackageName, packageName)
        context.startActivity(storeIntent)
    }

    companion object {
        private val LOG_TAG = PermissionRationaleViewModel::class.java.simpleName
    }
}

/** Factory for a [PermissionRationaleViewModel] */
class PermissionRationaleViewModelFactory(
    private val app: Application,
    private val packageName: String,
    private val permissionGroupName: String,
    private val sessionId: Long,
    private val savedState: Bundle?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PermissionRationaleViewModel(
            app, packageName, permissionGroupName, sessionId, savedState)
            as T
    }
}
