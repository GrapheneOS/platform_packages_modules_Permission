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
import android.content.Context
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightInstallSourceInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightInstallSourceInfo.Companion.UNKNOWN_INSTALL_SOURCE
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * [LightInstallSourceInfo] [LiveData] for the specified package
 *
 * @param app current Application
 * @param packageName name of the package to get InstallSourceInfo for
 * @param user The user of the package
 */
class LightInstallSourceInfoLiveData
private constructor(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<LightInstallSourceInfo>(),
    PackageBroadcastReceiver.PackageBroadcastListener {

    override fun onActive() {
        super.onActive()
        PackageBroadcastReceiver.addChangeCallback(packageName, this)
    }

    override fun onInactive() {
        super.onInactive()
        PackageBroadcastReceiver.removeChangeCallback(packageName, this)
    }

    /**
     * Callback from the PackageBroadcastReceiver
     *
     * @param packageName the name of the package which was updated.
     */
    override fun onPackageUpdate(packageName: String) {
        update()
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        val lightInstallSourceInfo: LightInstallSourceInfo =
            try {
                val userContext = Utils.getUserContext(app, user)
                LightInstallSourceInfo(
                    getInstallSourceInfo(userContext, packageName).installingPackageName)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(LOG_TAG, "InstallSourceInfo for $packageName not found")
                SafetyLabelInfoLiveData.invalidateSingle(packageName to user)
                UNKNOWN_INSTALL_SOURCE
            }
        postValue(lightInstallSourceInfo)
    }

    companion object :
        DataRepositoryForPackage<Pair<String, UserHandle>, LightInstallSourceInfoLiveData>() {
        private val LOG_TAG = LightInstallSourceInfoLiveData::class.java.simpleName

        override fun newValue(key: Pair<String, UserHandle>): LightInstallSourceInfoLiveData {
            return LightInstallSourceInfoLiveData(
                PermissionControllerApplication.get(), key.first, key.second)
        }

        /** Returns the [InstallSourceInfo] for the given package */
        @Throws(PackageManager.NameNotFoundException::class)
        private fun getInstallSourceInfo(context: Context, packageName: String): InstallSourceInfo {
            return context.packageManager.getInstallSourceInfo(packageName)
        }
    }
}
