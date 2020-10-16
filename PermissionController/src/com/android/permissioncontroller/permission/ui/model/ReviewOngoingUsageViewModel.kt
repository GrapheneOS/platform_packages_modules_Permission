/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.Manifest.permission_group.CAMERA
import android.Manifest.permission_group.LOCATION
import android.Manifest.permission_group.MICROPHONE
import android.Manifest.permission_group.PHONE
import android.content.res.Resources.ID_NULL
import android.location.LocationManager
import android.media.AudioManager
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.os.Bundle
import android.os.UserHandle
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.AppPermGroupUiInfoLiveData
import com.android.permissioncontroller.permission.data.AttributionLabelLiveData
import com.android.permissioncontroller.permission.data.LoadAndFreezeLifeData
import com.android.permissioncontroller.permission.data.OpUsageLiveData
import com.android.permissioncontroller.permission.data.PermGroupUsageLiveData
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.micMutedLiveData
import com.android.permissioncontroller.permission.debug.shouldShowLocationIndicators
import com.android.permissioncontroller.permission.debug.shouldShowPermissionsDashboard
import com.android.permissioncontroller.permission.ui.handheld.ReviewOngoingUsageFragment.PHONE_CALL
import com.android.permissioncontroller.permission.ui.handheld.ReviewOngoingUsageFragment.VIDEO_CALL
import com.android.permissioncontroller.permission.utils.KotlinUtils.getMapAndListDifferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.max

private const val FIRST_OPENED_KEY = "FIRST_OPENED"
private const val CALL_OP_USAGE_KEY = "CALL_OP_USAGE"
private const val USAGES_KEY = "USAGES_KEY"
private const val MIC_MUTED_KEY = "MIC_MUTED_KEY"

/**
 * ViewModel for {@link ReviewOngoingUsageFragment}
 */
class ReviewOngoingUsageViewModel(
    state: SavedStateHandle,
    extraDurationMills: Long
) : ViewModel() {
    /** Time of oldest usages considered */
    private val startTime = max(state.get<Long>(FIRST_OPENED_KEY)!! - extraDurationMills,
            Instant.EPOCH.toEpochMilli())

    data class Usages(
        /** attribution-res-id/packageName/user -> perm groups accessed */
        val appUsages: Map<Triple<Int, String, UserHandle>, Set<String>>,
        /** Op-names of phone call accesses */
        val callUsages: Collection<String>,
        /** Perm groups accessed by system */
        val systemUsages: Set<String>
    )

    /**
     * Base permission usage that will filtered by SystemPermGroupUsages and
     * UserSensitivePermGroupUsages.
     *
     * <p>Note: This does not use a cached live-data to avoid getting stale data
     */
    private val permGroupUsages = LoadAndFreezeLifeData(state, USAGES_KEY,
            PermGroupUsageLiveData(PermissionControllerApplication.get(),
                    if (shouldShowPermissionsDashboard() || shouldShowLocationIndicators()) {
                        listOf(CAMERA, LOCATION, MICROPHONE)
                    } else {
                        listOf(CAMERA, MICROPHONE)
                    }, System.currentTimeMillis() - startTime))

    /**
     * Whether the mic is muted
     */
    private val isMicMuted = LoadAndFreezeLifeData(state, MIC_MUTED_KEY, micMutedLiveData)

    /** App runtime permission usages */
    private val appUsagesLiveData = object : SmartUpdateMediatorLiveData<Map<Triple<Int,
        String, UserHandle>, Set<String>>>() {
        /** (packageName, user, permissionGroupName) -> uiInfo */
        private var permGroupUiInfos = mutableMapOf<Triple<String, UserHandle, String>,
            AppPermGroupUiInfoLiveData>()

        /** (packageName, user, attribution tag) -> attribution label */
        private var attributionLabels = mutableMapOf<Triple<String?, String, UserHandle>,
            AttributionLabelLiveData>()

        init {
            addSource(permGroupUsages) {
                update()
            }

            addSource(isMicMuted) {
                update()
            }
        }

        /**
         * Adjust {@code currentSources} to only include {@link requiredSources} by removing
         * obsolete sources and creating and adding newly required sources
         */
        private fun <K, V : LiveData<out Any>> adjustSources(
            currentSources: MutableMap<K, V>,
            requiredSources: List<K>,
            newSource: (K) -> V
        ) {
            val (toAdd, toRemove) = getMapAndListDifferences(requiredSources, currentSources)

            for (key in toRemove) {
                removeSource(currentSources.remove(key)!!)
            }

            for (key in toAdd) {
                val new = newSource(key)
                currentSources[key] = new

                addSource(new) {
                    // Delay updates to prevent recursive updates
                    GlobalScope.launch(Dispatchers.Main) {
                        update()
                    }
                }
            }
        }

        override fun onUpdate() {
            if (!permGroupUsages.isInitialized || !isMicMuted.isInitialized) {
                return
            }

            if (permGroupUsages.value == null) {
                value = null
                return
            }

            // Update set of permGroupUiInfos if needed
            val requiredUiInfos = permGroupUsages.value!!.flatMap {
                (permissionGroupName, accesses) ->
                accesses.map { access ->
                    Triple(access.packageName, access.user, permissionGroupName)
                }
            }

            adjustSources(permGroupUiInfos, requiredUiInfos) {
                (packageName, user, permGroupName) ->
                AppPermGroupUiInfoLiveData[packageName, permGroupName, user]
            }

            // Update set of attributionLabels needed
            val requiredLabels = permGroupUsages.value!!.flatMap {
                (_, accesses) ->
                accesses.map { access ->
                    Triple(access.attributionTag, access.packageName, access.user)
                }
            }.distinct()

            adjustSources(attributionLabels, requiredLabels) {
                (attributionTag, packageName, user) ->
                AttributionLabelLiveData[attributionTag, packageName, user]
            }

            if (permGroupUiInfos.values.any { !it.isInitialized } ||
                attributionLabels.values.any { !it.isInitialized }) {
                return
            }

            // Filter out system (== non user sensitive) apps
            val filteredUsages = mutableMapOf<Triple<Int, String, UserHandle>,
                MutableSet<String>>()
            for ((permGroupName, usages) in permGroupUsages.value!!) {
                for (usage in usages) {
                    if (permGroupUiInfos[Triple(usage.packageName, usage.user, permGroupName)]!!
                            .value?.isSystem == false) {
                        if (permGroupName == MICROPHONE && isMicMuted.value == true) {
                            continue
                        }

                        filteredUsages.getOrPut(Triple(
                            attributionLabels[Triple(usage.attributionTag,
                                usage.packageName,
                                usage.user)]!!.value ?: ID_NULL,
                            usage.packageName,
                            usage.user), { mutableSetOf() }).add(permGroupName)
                    }
                }
            }

            value = filteredUsages
        }
    }

    /** System runtime permission usages */
    private val systemUsagesLiveData = object : SmartAsyncMediatorLiveData<Set<String>>() {
        private val app = PermissionControllerApplication.get()

        init {
            addSource(permGroupUsages) {
                update()
            }

            addSource(isMicMuted) {
                update()
            }
        }

        override suspend fun loadDataAndPostValue(job: Job) {
            if (job.isCancelled) {
                return
            }

            if (!permGroupUsages.isInitialized || !isMicMuted.isInitialized) {
                return
            }

            if (permGroupUsages.value == null) {
                value = null
                return
            }

            val filteredUsages = mutableSetOf<String>()
            for ((permGroupName, usages) in permGroupUsages.value!!) {
                for (usage in usages) {
                    if (app.getSystemService(LocationManager::class.java)
                            .isProviderPackage(usage.packageName) &&
                        (permGroupName == CAMERA ||
                            (permGroupName == MICROPHONE &&
                                isMicMuted.value == false))) {
                        filteredUsages.add(permGroupName)
                    }
                }
            }

            postValue(filteredUsages)
        }
    }

    /** Phone call usages */
    private val callOpUsageLiveData =
        object : SmartUpdateMediatorLiveData<Collection<String>>() {
            private val rawOps = LoadAndFreezeLifeData(state, CALL_OP_USAGE_KEY,
                OpUsageLiveData[listOf(PHONE_CALL, VIDEO_CALL),
                    System.currentTimeMillis() - startTime])

            init {
                addSource(rawOps) {
                    update()
                }

                addSource(isMicMuted) {
                    update()
                }
            }

            override fun onUpdate() {
                if (!isMicMuted.isInitialized || !rawOps.isInitialized) {
                    return
                }

                value = if (isMicMuted.value == true) {
                    rawOps.value!!.keys.filter { it != PHONE_CALL }
                } else {
                    rawOps.value!!.keys
                }
            }
        }

    /** App, system, and call usages in a single, nice, handy package */
    val usages = object : SmartAsyncMediatorLiveData<Usages>() {
        private val app = PermissionControllerApplication.get()

        init {
            addSource(appUsagesLiveData) {
                update()
            }

            addSource(systemUsagesLiveData) {
                update()
            }

            addSource(callOpUsageLiveData) {
                update()
            }
        }

        override suspend fun loadDataAndPostValue(job: Job) {
            if (job.isCancelled) {
                return
            }

            if (!callOpUsageLiveData.isInitialized || !appUsagesLiveData.isInitialized ||
                    !systemUsagesLiveData.isInitialized) {
                return
            }

            val callOpUsages = callOpUsageLiveData.value?.toMutableSet()
            val appUsages = appUsagesLiveData.value?.toMutableMap()
            val systemUsages = systemUsagesLiveData.value

            if (callOpUsages == null || appUsages == null || systemUsages == null) {
                postValue(null)
                return
            }

            // If there is nothing to show the dialog should be closed, hence return a "invalid"
            // value
            if (appUsages.isEmpty() && callOpUsages.isEmpty() && systemUsages.isEmpty()) {
                postValue(null)
                return
            }

            // If we are in a VOIP call (aka MODE_IN_COMMUNICATION), and have a carrier privileged
            // app using the mic, hide phone usage.
            val audioManager = app.getSystemService(AudioManager::class.java)!!
            if (callOpUsages.isNotEmpty() && audioManager.mode == MODE_IN_COMMUNICATION) {
                val telephonyManager = app.getSystemService(TelephonyManager::class.java)!!
                for ((pkg, usages) in appUsages) {
                    if (telephonyManager.checkCarrierPrivilegesForPackage(pkg.second) ==
                        CARRIER_PRIVILEGE_STATUS_HAS_ACCESS && usages.contains(MICROPHONE)) {
                        appUsages[pkg] = usages.toMutableSet().apply {
                            // TODO ntmyren: Replace this with real behavior
                            remove(MICROPHONE)
                            add(PHONE)
                        }

                        callOpUsages.clear()
                        continue
                    }
                }
            }

            postValue(Usages(appUsages, callOpUsages, systemUsages))
        }
    }
}

/**
 * Factory for a ReviewOngoingUsageViewModel
 *
 * @param extraDurationMillis The number of milliseconds old usages are considered for
 * @param owner The owner of this saved state
 * @param defaultArgs The default args to pass
 */
class ReviewOngoingUsageViewModelFactory(
    private val extraDurationMillis: Long,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    override fun <T : ViewModel?> create(p0: String, p1: Class<T>, state: SavedStateHandle): T {
        state.set(FIRST_OPENED_KEY, state.get<Long>(FIRST_OPENED_KEY)
                ?: System.currentTimeMillis())
        @Suppress("UNCHECKED_CAST")
        return ReviewOngoingUsageViewModel(state, extraDurationMillis) as T
    }
}
