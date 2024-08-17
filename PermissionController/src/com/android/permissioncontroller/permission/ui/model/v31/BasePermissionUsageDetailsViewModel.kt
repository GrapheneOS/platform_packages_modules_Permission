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

import android.app.Application
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.handheld.v31.getDurationUsedStr
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.CLUSTER_SPACING_MINUTES
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.PermissionUsageDetailsUiState
import com.android.permissioncontroller.permission.utils.KotlinUtils
import java.util.concurrent.TimeUnit

abstract class BasePermissionUsageDetailsViewModel(val app: Application) : AndroidViewModel(app) {
    abstract fun getPermissionUsagesDetailsInfoUiLiveData(): LiveData<PermissionUsageDetailsUiState>

    abstract fun getShowSystem(): Boolean

    abstract val showSystemLiveData: LiveData<Boolean>

    abstract fun getShow7Days(): Boolean

    abstract fun updateShowSystemAppsToggle(showSystem: Boolean)

    abstract fun updateShow7DaysToggle(show7Days: Boolean)

    private val packageIconCache: MutableMap<Pair<String, UserHandle>, Drawable> = mutableMapOf()
    private val packageLabelCache: MutableMap<String, String> = mutableMapOf()

    /**
     * Returns the label for the provided package name, by first searching the cache otherwise
     * retrieving it from the app's [android.content.pm.ApplicationInfo].
     */
    fun getPackageLabel(packageName: String, user: UserHandle): String {
        if (packageLabelCache.containsKey(packageName)) {
            return requireNotNull(packageLabelCache[packageName])
        }
        val packageLabel = getPackageLabel(app, packageName, user)
        packageLabelCache[packageName] = packageLabel
        return packageLabel
    }

    open fun getPackageLabel(app: Application, packageName: String, user: UserHandle): String {
        return KotlinUtils.getPackageLabel(app, packageName, user)
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
        val packageIcon = getBadgedPackageIcon(app, packageName, userHandle)
        if (packageIcon != null) packageIconCache[packageNameWithUser] = packageIcon

        return packageIcon
    }

    open fun getBadgedPackageIcon(
        app: Application,
        packageName: String,
        user: UserHandle
    ): Drawable? {
        return KotlinUtils.getBadgedPackageIcon(app, packageName, user)
    }

    fun getDurationSummary(durationMs: Long): String? {
        // Only show the duration summary if it is at least (CLUSTER_SPACING_MINUTES + 1) minutes.
        // Displaying a time that is shorter than the cluster granularity
        // (CLUSTER_SPACING_MINUTES) will not convey useful information.
        if (durationMs >= TimeUnit.MINUTES.toMillis(CLUSTER_SPACING_MINUTES + 1)) {
            return getDurationUsedStr(app, durationMs)
        }
        return null
    }

    fun buildUsageSummary(
        subAttributionLabel: String?,
        proxyPackageLabel: String?,
        durationSummary: String?
    ): String? {
        val subTextStrings: MutableList<String> = mutableListOf()
        subAttributionLabel?.let { subTextStrings.add(subAttributionLabel) }
        proxyPackageLabel?.let { subTextStrings.add(it) }
        durationSummary?.let { subTextStrings.add(it) }
        return when (subTextStrings.size) {
            3 ->
                app.getString(
                    R.string.history_preference_subtext_3,
                    subTextStrings[0],
                    subTextStrings[1],
                    subTextStrings[2]
                )
            2 ->
                app.getString(
                    R.string.history_preference_subtext_2,
                    subTextStrings[0],
                    subTextStrings[1]
                )
            1 -> subTextStrings[0]
            else -> null
        }
    }
}
