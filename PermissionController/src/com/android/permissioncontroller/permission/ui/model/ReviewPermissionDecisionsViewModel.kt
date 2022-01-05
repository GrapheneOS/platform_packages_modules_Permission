/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.model

import android.app.Application
import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.permission.data.PermissionDecision
import com.android.permissioncontroller.permission.data.RecentPermissionDecisionsLiveData
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.data.UserPackageInfosLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/** Viewmodel for [ReviewPermissionDecisionsFragment] */
class ReviewPermissionDecisionsViewModel(app: Application, user: UserHandle) : ViewModel() {

    val LOG_TAG = "ReviewPermissionDecisionsViewModel"

    private val recentPermissionsLiveData = RecentPermissionDecisionsLiveData()
    private val userPackageInfosLiveData = UserPackageInfosLiveData[user]

    val recentPermissionDecisionsLiveData = object
        : SmartAsyncMediatorLiveData<List<PermissionDecision>>(
        alwaysUpdateOnActive = false
    ) {

        init {
            addSource(recentPermissionsLiveData) {
                onUpdate()
            }

            addSource(userPackageInfosLiveData) {
                onUpdate()
            }
        }

        override suspend fun loadDataAndPostValue(job: Job) {
            if (!recentPermissionsLiveData.isInitialized ||
                !userPackageInfosLiveData.isInitialized) {
                return
            }

            // create package info lookup map for performance
            val packageToLightPackageInfo: MutableMap<String, LightPackageInfo> = mutableMapOf()
            for (lightPackageInfo in userPackageInfosLiveData.value!!) {
                packageToLightPackageInfo[lightPackageInfo.packageName] = lightPackageInfo
            }

            // verify that permission state is still correct. Will also filter out any apps that
            // were uninstalled
            val decisionsToReview: MutableList<PermissionDecision> = mutableListOf()
            for (recentDecision in recentPermissionsLiveData.value!!) {
                val lightPackageInfo = packageToLightPackageInfo[recentDecision.packageName]
                if (lightPackageInfo == null) {
                    DumpableLog.e(LOG_TAG, "Package $recentDecision.packageName " +
                        "is no longer installed")
                    continue
                }
                val grantedGroups: List<String?> = lightPackageInfo.grantedPermissions.map {
                    Utils.getGroupOfPermission(
                        app.packageManager.getPermissionInfo(it, /* flags= */ 0))
                }
                val currentlyGranted = grantedGroups.contains(recentDecision.permissionGroupName)
                if (currentlyGranted && recentDecision.isGranted) {
                    decisionsToReview.add(recentDecision)
                } else if (!currentlyGranted && !recentDecision.isGranted) {
                    decisionsToReview.add(recentDecision)
                } else {
                    // It's okay for this to happen - the state could change due to role changes,
                    // app hibernation, or other non-user-driven actions.
                    DumpableLog.d(LOG_TAG,
                        "Permission decision grant state (${recentDecision.isGranted}) " +
                            "for ${recentDecision.packageName} access to " +
                            "${recentDecision.permissionGroupName} does not match current " +
                            "grant state $currentlyGranted")
                }
            }

            postValue(decisionsToReview)
        }
    }
}

/**
 * Factory for a [ReviewPermissionDecisionsViewModel]
 */
class ReviewPermissionDecisionsViewModelFactory(val app: Application, val user: UserHandle) :
    ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ReviewPermissionDecisionsViewModel(app, user) as T
    }
}