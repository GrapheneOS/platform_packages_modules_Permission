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

package com.android.permissioncontroller.permission.ui.model.v31

import android.Manifest
import android.app.AppOpsManager
import android.app.AppOpsManager.OPSTR_EMERGENCY_LOCATION
import android.app.AppOpsManager.OPSTR_PHONE_CALL_CAMERA
import android.app.AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.Build
import android.os.UserHandle
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.application
import androidx.lifecycle.asLiveData
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.R
import com.android.permissioncontroller.appops.data.repository.v31.AppOpRepository
import com.android.permissioncontroller.permission.compat.IntentCompat
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.domain.model.v31.PermissionTimelineUsageModel
import com.android.permissioncontroller.permission.domain.model.v31.PermissionTimelineUsageModelWrapper
import com.android.permissioncontroller.permission.domain.usecase.v31.GetPermissionGroupUsageDetailsUseCase
import com.android.permissioncontroller.permission.ui.handheld.v31.getDurationUsedStr
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import com.android.permissioncontroller.role.data.repository.v31.RoleRepository
import com.android.permissioncontroller.user.data.repository.v31.UserRepository
import java.time.Instant
import java.util.Objects
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class PermissionUsageDetailsViewModel(
    app: Application,
    private val getPermissionUsageDetailsUseCase: GetPermissionGroupUsageDetailsUseCase,
    private val state: SavedStateHandle,
    private val permissionGroup: String,
    scope: CoroutineScope? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val packageRepository: PackageRepository = PackageRepository.getInstance(app),
) : AndroidViewModel(app) {
    private val coroutineScope = scope ?: viewModelScope
    private val context = app

    private val packageIconCache: MutableMap<Pair<String, UserHandle>, Drawable> = mutableMapOf()
    private val packageLabelCache: MutableMap<String, String> = mutableMapOf()

    private val showSystemFlow = MutableStateFlow(state[SHOULD_SHOW_SYSTEM_KEY] ?: false)
    private val show7DaysFlow = MutableStateFlow(state[SHOULD_SHOW_7_DAYS_KEY] ?: false)

    private val permissionTimelineUsagesFlow:
        StateFlow<PermissionTimelineUsageModelWrapper> by lazy {
        getPermissionUsageDetailsUseCase(coroutineScope)
            .flowOn(defaultDispatcher)
            .stateIn(
                coroutineScope,
                SharingStarted.WhileSubscribed(5000),
                PermissionTimelineUsageModelWrapper.Loading,
            )
    }

    @VisibleForTesting
    val permissionUsageDetailsUiStateFlow: Flow<PermissionUsageDetailsUiState> by lazy {
        combine(permissionTimelineUsagesFlow, showSystemFlow, show7DaysFlow) {
                permissionTimelineUsages,
                showSystem,
                show7Days ->
                permissionTimelineUsages.buildPermissionUsageDetailsUiInfo(showSystem, show7Days)
            }
            .flowOn(defaultDispatcher)
    }

    fun getPermissionUsagesDetailsInfoUiLiveData(): LiveData<PermissionUsageDetailsUiState> {
        return permissionUsageDetailsUiStateFlow.asLiveData(
            context = coroutineScope.coroutineContext
        )
    }

    private fun PermissionTimelineUsageModelWrapper.buildPermissionUsageDetailsUiInfo(
        showSystem: Boolean,
        show7Days: Boolean,
    ): PermissionUsageDetailsUiState {
        if (this is PermissionTimelineUsageModelWrapper.Loading) {
            return PermissionUsageDetailsUiState.Loading
        }
        val timelineUsageModels =
            (this as PermissionTimelineUsageModelWrapper.Success).timelineUsageModels
        val startTime =
            (System.currentTimeMillis() - getUsageDuration(show7Days)).coerceAtLeast(
                Instant.EPOCH.toEpochMilli()
            )

        val permissionTimelineUsageModels =
            timelineUsageModels.filter { it.accessEndMillis > startTime }
        val containsSystemUsages = permissionTimelineUsageModels.any { !it.isUserSensitive }
        val result =
            permissionTimelineUsageModels
                .filter { showSystem || it.isUserSensitive }
                .map { clusterOps ->
                    val durationSummaryLabel =
                        if (clusterOps.durationMillis > 0) {
                            getDurationSummary(clusterOps.durationMillis)
                        } else {
                            null
                        }
                    val proxyLabel = getProxyPackageLabel(clusterOps)
                    val isEmergencyLocationAccess =
                        isLocationByPassEnabled() &&
                            clusterOps.opNames.any { it == OPSTR_EMERGENCY_LOCATION }
                    val subAttributionLabel =
                        if (isEmergencyLocationAccess) {
                            emergencyLocationAttributionLabel
                        } else {
                            clusterOps.attributionLabel
                        }
                    val showingSubAttribution = !subAttributionLabel.isNullOrEmpty()
                    val summary =
                        buildUsageSummary(subAttributionLabel, proxyLabel, durationSummaryLabel)
                    PermissionUsageDetailsViewModel.AppPermissionAccessUiInfo(
                        UserHandle.of(clusterOps.userId),
                        clusterOps.packageName,
                        getPackageLabel(clusterOps.packageName, UserHandle.of(clusterOps.userId)),
                        permissionGroup,
                        clusterOps.accessStartMillis,
                        clusterOps.accessEndMillis,
                        summary,
                        showingSubAttribution,
                        clusterOps.attributionTags ?: emptySet(),
                        getBadgedPackageIcon(
                            clusterOps.packageName,
                            UserHandle.of(clusterOps.userId),
                        ),
                        isEmergencyLocationAccess,
                    )
                }
                .sortedBy { -1 * it.accessStartTime }
        return PermissionUsageDetailsUiState.Success(
            result,
            containsSystemUsages,
            showSystem,
            show7Days,
        )
    }

    private val emergencyLocationAttributionLabel: String by lazy {
        context.getString(R.string.privacy_dashboard_emergency_location_enforced_attribution_label)
    }

    fun getShowSystem(): Boolean = showSystemFlow.value

    val showSystemLiveData = showSystemFlow.asLiveData(context = coroutineScope.coroutineContext)

    fun getShow7Days(): Boolean = show7DaysFlow.value

    private fun getUsageDuration(show7Days: Boolean): Long {
        return if (show7Days && DeviceUtils.isHandheld()) {
            TIME_7_DAYS_DURATION
        } else {
            TIME_24_HOURS_DURATION
        }
    }

    private fun getProxyPackageLabel(accessCluster: PermissionTimelineUsageModel): String? =
        accessCluster.proxyPackageName?.let { proxyPackageName ->
            if (accessCluster.proxyUserId != null) {
                getPackageLabel(proxyPackageName, UserHandle.of(accessCluster.proxyUserId))
            } else null
        }

    fun updateShowSystemAppsToggle(showSystem: Boolean) {
        if (showSystem != state[SHOULD_SHOW_SYSTEM_KEY]) {
            state[SHOULD_SHOW_SYSTEM_KEY] = showSystem
        }
        showSystemFlow.compareAndSet(!showSystem, showSystem)
    }

    fun updateShow7DaysToggle(show7Days: Boolean) {
        if (show7Days != state[SHOULD_SHOW_7_DAYS_KEY]) {
            state[SHOULD_SHOW_7_DAYS_KEY] = show7Days
        }
        show7DaysFlow.compareAndSet(!show7Days, show7Days)
    }

    /**
     * Returns the label for the provided package name, by first searching the cache otherwise
     * retrieving it from the app's [android.content.pm.ApplicationInfo].
     */
    fun getPackageLabel(packageName: String, user: UserHandle): String {
        if (packageLabelCache.containsKey(packageName)) {
            return requireNotNull(packageLabelCache[packageName])
        }
        val packageLabel = packageRepository.getPackageLabel(packageName, user)
        packageLabelCache[packageName] = packageLabel
        return packageLabel
    }

    /**
     * Returns the icon for the provided package name and user, by first searching the cache
     * otherwise retrieving it from the app's [android.content.pm.ApplicationInfo].
     */
    fun getBadgedPackageIcon(packageName: String, userHandle: UserHandle): Drawable? {
        val packageNameWithUser: Pair<String, UserHandle> = Pair(packageName, userHandle)
        if (packageIconCache.containsKey(packageNameWithUser)) {
            return requireNotNull(packageIconCache[packageNameWithUser])
        }
        val packageIcon = packageRepository.getBadgedPackageIcon(packageName, userHandle)
        if (packageIcon != null) {
            packageIconCache[packageNameWithUser] = packageIcon
        }

        return packageIcon
    }

    private fun getDurationSummary(durationMs: Long): String? {
        // Only show the duration summary if it is at least (CLUSTER_SPACING_MINUTES + 1) minutes.
        // Displaying a time that is shorter than the cluster granularity
        // (CLUSTER_SPACING_MINUTES) will not convey useful information.
        if (durationMs >= TimeUnit.MINUTES.toMillis(CLUSTER_SPACING_MINUTES + 1)) {
            return getDurationUsedStr(application, durationMs)
        }
        return null
    }

    private fun buildUsageSummary(
        subAttributionLabel: String?,
        proxyPackageLabel: String?,
        durationSummary: String?,
    ): String? {
        val subTextStrings: MutableList<String> = mutableListOf()
        subAttributionLabel?.let { subTextStrings.add(subAttributionLabel) }
        proxyPackageLabel?.let { subTextStrings.add(it) }
        durationSummary?.let { subTextStrings.add(it) }
        return when (subTextStrings.size) {
            3 ->
                application.getString(
                    R.string.history_preference_subtext_3,
                    subTextStrings[0],
                    subTextStrings[1],
                    subTextStrings[2],
                )
            2 ->
                application.getString(
                    R.string.history_preference_subtext_2,
                    subTextStrings[0],
                    subTextStrings[1],
                )
            1 -> subTextStrings[0]
            else -> null
        }
    }

    /** Companion object for [PermissionUsageDetailsViewModel]. */
    companion object {
        const val ONE_MINUTE_MS = 60_000
        const val CLUSTER_SPACING_MINUTES: Long = 1L
        val TIME_7_DAYS_DURATION: Long = DAYS.toMillis(7)
        val TIME_24_HOURS_DURATION: Long = DAYS.toMillis(1)
        internal const val SHOULD_SHOW_SYSTEM_KEY = "showSystem"
        internal const val SHOULD_SHOW_7_DAYS_KEY = "show7Days"

        /** Creates the [Intent] for the click action of a privacy dashboard app usage event. */
        fun createHistoryPreferenceClickIntent(
            context: Context,
            userHandle: UserHandle,
            packageName: String,
            permissionGroup: String,
            accessStartTime: Long,
            accessEndTime: Long,
            showingAttribution: Boolean,
            attributionTags: Set<String>,
        ): Intent {
            return getManagePermissionUsageIntent(
                context,
                packageName,
                permissionGroup,
                accessStartTime,
                accessEndTime,
                showingAttribution,
                attributionTags,
            ) ?: getDefaultManageAppPermissionsIntent(packageName, userHandle)
        }

        /**
         * Gets an [Intent.ACTION_MANAGE_PERMISSION_USAGE] intent, or null if attribution shouldn't
         * be shown or the intent can't be handled.
         */
        @Suppress("DEPRECATION")
        private fun getManagePermissionUsageIntent(
            context: Context,
            packageName: String,
            permissionGroup: String,
            accessStartTime: Long,
            accessEndTime: Long,
            showingAttribution: Boolean,
            attributionTags: Set<String>,
        ): Intent? {
            if (
                !showingAttribution ||
                    !SdkLevel.isAtLeastT() ||
                    !context
                        .getSystemService(LocationManager::class.java)!!
                        .isProviderPackage(packageName)
            ) {
                // We should only limit this intent to location provider
                return null
            }
            val intent =
                Intent(Intent.ACTION_MANAGE_PERMISSION_USAGE).apply {
                    setPackage(packageName)
                    putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, permissionGroup)
                    putExtra(Intent.EXTRA_ATTRIBUTION_TAGS, attributionTags.toTypedArray())
                    putExtra(Intent.EXTRA_START_TIME, accessStartTime)
                    putExtra(Intent.EXTRA_END_TIME, accessEndTime)
                    putExtra(IntentCompat.EXTRA_SHOWING_ATTRIBUTION, showingAttribution)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val resolveInfo =
                context.packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0),
                )
            if (
                resolveInfo?.activityInfo == null ||
                    !Objects.equals(
                        resolveInfo.activityInfo.permission,
                        Manifest.permission.START_VIEW_PERMISSION_USAGE,
                    )
            ) {
                return null
            }
            intent.component = ComponentName(packageName, resolveInfo.activityInfo.name)
            return intent
        }

        private fun getDefaultManageAppPermissionsIntent(
            packageName: String,
            userHandle: UserHandle,
        ): Intent {
            @Suppress("DEPRECATION")
            return Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS).apply {
                putExtra(Intent.EXTRA_USER, userHandle)
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            }
        }

        private fun isLocationByPassEnabled(): Boolean = SdkLevel.isAtLeastV()

        fun create(
            app: Application,
            handle: SavedStateHandle,
            permissionGroup: String,
        ): PermissionUsageDetailsViewModel {
            val permissionRepository = PermissionRepository.getInstance(app)
            val packageRepository = PackageRepository.getInstance(app)
            val appOpRepository = AppOpRepository.getInstance(app, permissionRepository)
            val roleRepository = RoleRepository.getInstance(app)
            val userRepository = UserRepository.getInstance(app)
            val useCase =
                GetPermissionGroupUsageDetailsUseCase(
                    permissionGroup,
                    packageRepository,
                    permissionRepository,
                    appOpRepository,
                    roleRepository,
                    userRepository,
                )
            return PermissionUsageDetailsViewModel(app, useCase, handle, permissionGroup)
        }
    }

    /** Data used to create a preference for an app's permission usage. */
    data class AppPermissionAccessUiInfo(
        val userHandle: UserHandle,
        val packageName: String,
        val packageLabel: String,
        val permissionGroup: String,
        val accessStartTime: Long,
        val accessEndTime: Long,
        val summaryText: CharSequence?,
        val showingAttribution: Boolean,
        val attributionTags: Set<String>,
        val badgedPackageIcon: Drawable?,
        val isEmergencyLocationAccess: Boolean,
    )

    sealed class PermissionUsageDetailsUiState {
        data object Loading : PermissionUsageDetailsUiState()

        data class Success(
            val appPermissionAccessUiInfoList: List<AppPermissionAccessUiInfo>,
            val containsSystemAppUsage: Boolean,
            val showSystem: Boolean,
            val show7Days: Boolean,
        ) : PermissionUsageDetailsUiState()
    }

    /** Factory for [PermissionUsageDetailsViewModel]. */
    @RequiresApi(Build.VERSION_CODES.S)
    class PermissionUsageDetailsViewModelFactory(
        val app: Application,
        private val permissionGroup: String,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return create(app, extras.createSavedStateHandle(), permissionGroup) as T
        }
    }
}
