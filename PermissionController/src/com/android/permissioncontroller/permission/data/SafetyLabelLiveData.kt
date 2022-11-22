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

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.util.Log
import com.android.permission.safetylabel.DataCategoryConstants
import com.android.permission.safetylabel.DataLabelConstants
import com.android.permission.safetylabel.DataTypeConstants
import com.android.permission.safetylabel.SafetyLabel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.KotlinUtils.isPermissionRationaleEnabled
import com.android.permissioncontroller.permission.utils.KotlinUtils.isPlaceholderSafetyLabelDataEnabled
import kotlinx.coroutines.Job

/**
 * SafetyLabel LiveData for the specified package
 *
 * @param app current Application
 * @param packageName name of the package to get SafetyLabel information for
 */
class SafetyLabelLiveData
private constructor(private val app: Application, private val packageName: String) :
    SmartAsyncMediatorLiveData<SafetyLabel>() {

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        if (!isPermissionRationaleEnabled()) {
            postValue(null)
            return
        }

        if (packageName.isEmpty()) {
            postValue(null)
            return
        }

        val safetyLabel: SafetyLabel? =
            try {
                val metadataBundle: PersistableBundle? = getInstallMetadataBundle()
                SafetyLabel.getSafetyLabelFromMetadata(metadataBundle)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(LOG_TAG, "SafetyLabel for $packageName not found")
                invalidateSingle(packageName)
                null
            }
        postValue(safetyLabel)
    }

    // TODO(b/257293222): Update when hooking up PackageManager APIs
    private fun getInstallMetadataBundle(): PersistableBundle? {
        return if (isPlaceholderSafetyLabelDataEnabled()) {
            placeholderMetadataBundle()
        } else {
            null
        }
    }

    // TODO(b/257293222): Remove when hooking up PackageManager APIs
    private fun placeholderMetadataBundle(): PersistableBundle {
        val approximateLocationBundle = PersistableBundle().apply {
            putIntArray(
                "purposes",
                (1..7).toList().toIntArray())
        }

        val locationBundle = PersistableBundle().apply {
            putPersistableBundle(
                DataTypeConstants.LOCATION_APPROX_LOCATION,
                approximateLocationBundle)
        }

        val dataSharedBundle = PersistableBundle().apply {
            putPersistableBundle(DataCategoryConstants.CATEGORY_LOCATION, locationBundle)
        }

        val dataLabelBundle = PersistableBundle().apply {
            putPersistableBundle(DataLabelConstants.DATA_USAGE_SHARED, dataSharedBundle)
        }

        val safetyLabelBundle = PersistableBundle().apply {
            putPersistableBundle("data_labels", dataLabelBundle)
        }

        return PersistableBundle().apply {
            putPersistableBundle("safety_labels", safetyLabelBundle)
        }
    }

    companion object : DataRepositoryForPackage<String, SafetyLabelLiveData>() {
        private val LOG_TAG = SafetyLabelLiveData::class.java.simpleName

        override fun newValue(key: String): SafetyLabelLiveData {
            return SafetyLabelLiveData(PermissionControllerApplication.get(), key)
        }
    }
}
