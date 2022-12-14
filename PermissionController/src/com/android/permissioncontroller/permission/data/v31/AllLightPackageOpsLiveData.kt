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

package com.android.permissioncontroller.permission.data.v31

import android.app.AppOpsManager
import android.app.Application
import android.os.UserHandle
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.data.StandardPermGroupNamesLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.v31.LightPackageOps
import com.android.permissioncontroller.permission.utils.PermissionMapping
import kotlinx.coroutines.Job

/**
 * LiveData class tracking [LightPackageOps] for all packages on the device and for all system
 * permission groups' ops.
 *
 * App ops data is retrieved from [AppOpsManager] and is updated whenever app ops data changes are
 * heard.
 */
class AllLightPackageOpsLiveData(app: Application) :
    SmartAsyncMediatorLiveData<Map<Pair<String, UserHandle>, LightPackageOps>>(),
    AppOpsManager.OnOpActiveChangedListener,
    AppOpsManager.OnOpNotedListener,
    AppOpsManager.OnOpChangedListener {

    private val appOpsManager = app.getSystemService(AppOpsManager::class.java)!!
    private var opNames: Set<String> = getOpNames(StandardPermGroupNamesLiveData.value)

    init {
        addSource(StandardPermGroupNamesLiveData) {
            opNames = getOpNames(it)
            update()
        }
    }

    override fun onActive() {
        super.onActive()

        try {
            appOpsManager.startWatchingActive(opNames.toTypedArray(), { it.run() }, this)
        } catch (ignored: IllegalArgumentException) {
            // Older builds may not support all requested app ops.
        }

        opNames.forEach {
            try {
                appOpsManager.startWatchingMode(it, /* all packages */ null, this)
            } catch (ignored: IllegalArgumentException) {
                // Older builds may not support all requested app ops.
            }
        }
    }

    override fun onInactive() {
        super.onInactive()

        appOpsManager.stopWatchingActive(this)
        appOpsManager.stopWatchingMode(this)
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        val packageOpsList =
            try {
                appOpsManager.getPackagesForOps(opNames.toTypedArray())
            } catch (e: NullPointerException) {
                // Older builds may not support all requested app ops.
                emptyList<AppOpsManager.PackageOps>()
            }

        postValue(
            packageOpsList.associateBy(
                { Pair(it.packageName, UserHandle.getUserHandleForUid(it.uid)) },
                { LightPackageOps(opNames, it) }))
    }

    override fun onOpChanged(op: String?, packageName: String?) {
        update()
    }

    override fun onOpActiveChanged(op: String, uid: Int, packageName: String, active: Boolean) {
        update()
    }

    override fun onOpNoted(
        code: String,
        uid: Int,
        packageName: String,
        attributionTag: String?,
        flags: Int,
        result: Int
    ) {
        update()
    }

    /** Returns all op names for all permissions in a list of permission groups. */
    private fun getOpNames(permissionGroupNames: List<String>?) =
        permissionGroupNames
            ?.flatMap { group -> PermissionMapping.getPlatformPermissionNamesOfGroup(group) }
            ?.mapNotNull { permName -> AppOpsManager.permissionToOp(permName) }
            ?.toSet()
            ?: setOf()
}
