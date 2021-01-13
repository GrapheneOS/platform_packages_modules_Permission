/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.os.UserHandle
import androidx.lifecycle.LiveData
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo

/**
 * A LiveData which tracks all app permission groups for a set of permission groups, either platform
 * or custom, as well as the UI information related to each app permission group, and the permission
 * group as a whole.
 *
 * @param app The current application
 */
class PermGroupsPackagesUiInfoLiveData(
    private val app: Application,
    groupNamesLiveData: LiveData<List<String>>
) : SmartUpdateMediatorLiveData<
    @kotlin.jvm.JvmSuppressWildcards Map<String, PermGroupPackagesUiInfo?>>() {

    /**
     * Map<permission group name, PermGroupUiLiveDatas>
     */
    private val permGroupPackagesLiveDatas = mutableMapOf<String,
        SinglePermGroupPackagesUiInfoLiveData>()
    private val allPackageData = mutableMapOf<String, PermGroupPackagesUiInfo?>()

    private lateinit var groupNames: List<String>

    init {
        addSource(groupNamesLiveData) {
            groupNames = it ?: emptyList()
            update()
            getPermGroupPackageLiveDatas()
        }
    }

    private fun getPermGroupPackageLiveDatas() {
        val getLiveData = { groupName: String -> SinglePermGroupPackagesUiInfoLiveData[groupName] }
        setSourcesToDifference(groupNames, permGroupPackagesLiveDatas, getLiveData)
    }

    private fun isGranted(grantState: AppPermGroupUiInfo.PermGrantState): Boolean {
        return grantState != AppPermGroupUiInfo.PermGrantState.PERMS_DENIED &&
                grantState != AppPermGroupUiInfo.PermGrantState.PERMS_ASK
    }

    private fun createPermGroupPackageUiInfo(
        groupName: String,
        appPermGroups: Map<Pair<String, UserHandle>, AppPermGroupUiInfo>
    ): PermGroupPackagesUiInfo {
        var nonSystem = 0
        var grantedNonSystem = 0
        var userInteractedNonSystem = 0
        var grantedSystem = 0
        var userInteractedSystem = 0

        for ((_, appPermGroup) in appPermGroups) {
            if (!appPermGroup.shouldShow) {
                continue
            }

            if (appPermGroup.isSystem) {
                if (isGranted(appPermGroup.permGrantState)) {
                    grantedSystem++
                    userInteractedSystem++
                } else if (appPermGroup.isUserSet) {
                    userInteractedSystem++
                }
            } else {
                nonSystem++

                if (isGranted(appPermGroup.permGrantState)) {
                    grantedNonSystem++
                    userInteractedNonSystem++
                } else if (appPermGroup.isUserSet) {
                    userInteractedNonSystem++
                }
            }
        }
        return PermGroupPackagesUiInfo(groupName, nonSystem, grantedNonSystem,
                userInteractedNonSystem, grantedSystem, userInteractedSystem)
    }

    override fun onUpdate() {
        /**
         * Only update when either-
         * We have a list of groups, and none have loaded their data, or
         * All packages have loaded their data
         */
        val haveAllLiveDatas = groupNames.all { permGroupPackagesLiveDatas.contains(it) }
        val allInitialized = permGroupPackagesLiveDatas.all { it.value.isInitialized }
        for (groupName in groupNames) {
            allPackageData[groupName] = if (haveAllLiveDatas && allInitialized) {
                permGroupPackagesLiveDatas[groupName]?.value?.let { uiInfo ->
                    createPermGroupPackageUiInfo(groupName, uiInfo)
                }
            } else {
                null
            }
        }
        value = allPackageData.toMap()
    }
}
