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

package com.android.permissioncontroller.permission.ui.wear

import android.app.Application
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.Category
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupDescription
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import com.android.settingslib.utils.applications.AppUtils
import java.text.Collator
import java.util.Random

/**
 * Helper class for WearPermissionsAppScreen.
 */
class WearPermissionsAppHelper(
    val application: Application,
    val permGroupName: String,
    val viewModel: PermissionAppsViewModel,
    private val isStorage: Boolean,
    private val onAppClick: (String, UserHandle, String) -> Unit,
    val onShowSystemClick: (Boolean) -> Unit,
    val logFragmentCreated: (String, UserHandle, Long, Boolean, Boolean, Boolean) -> Unit
) {
    fun categorizedAppsLiveData() = viewModel.categorizedAppsLiveData
    fun hasSystemAppsLiveData() = viewModel.hasSystemAppsLiveData
    fun shouldShowSystemLiveData() = viewModel.shouldShowSystemLiveData
    fun showAlways() = viewModel.showAllowAlwaysStringLiveData.value ?: false
    fun getTitle() = getPermGroupLabel(application, permGroupName).toString()
    fun getSubTitle() = getPermGroupDescription(application, permGroupName).toString()
    fun getChipsByCategory(
        categorizedApps: Map<Category, List<Pair<String, UserHandle>>>
    ): Map<String, List<ChipInfo>> {
        val chipsByCategory: MutableMap<String, MutableList<ChipInfo>> = HashMap()

        val context = application
        val collator = Collator.getInstance(
            context.getResources().getConfiguration().getLocales().get(0)
        )
        val comparator = ChipComparator(collator)

        val viewIdForLogging = Random().nextLong()
        for (category in Category.values()) {
            if (category == Category.ALLOWED && isStorage) {
                val allowedList = categorizedApps[Category.ALLOWED]
                if (!allowedList.isNullOrEmpty()) {
                    allowedList.partition { p ->
                        viewModel.packageHasFullStorage(
                            p.first,
                            p.second
                        )
                    }.let {
                        if (it.first.isNotEmpty()) {
                            chipsByCategory["allowed_storage_full"] =
                                convertToChips(category, it.first, viewIdForLogging, comparator)
                        }
                        if (it.second.isNotEmpty()) {
                            chipsByCategory["allowed_storage_scoped"] =
                                convertToChips(category, it.second, viewIdForLogging, comparator)
                        }
                    }
                }
                continue
            }
            val list = categorizedApps[category]
            if (!list.isNullOrEmpty()) {
                chipsByCategory[category.categoryName] =
                    convertToChips(category, list, viewIdForLogging, comparator)
            }
        }

        // Add no_apps chips to allowed and denied if it doesn't have an app.
        chipsByCategory[Category.ALLOWED.categoryName]?.let {
            if (it.isEmpty()) {
                it.add(
                    ChipInfo(
                        title = context.resources.getString(R.string.no_apps_allowed),
                        enabled = false
                    )
                )
            }
        }
        chipsByCategory[Category.DENIED.categoryName]?.let {
            if (it.isEmpty()) {
                it.add(
                    ChipInfo(
                        title = context.resources.getString(R.string.no_apps_denied),
                        enabled = false
                    )
                )
            }
        }
        return chipsByCategory
    }

    private fun convertToChips(
        category: Category,
        list: List<Pair<String, UserHandle>>,
        viewIdForLogging: Long,
        comparator: Comparator<ChipInfo>
    ) =
        list.map { p ->
            createAppChipInfo(
                application,
                p.first,
                p.second,
                category,
                onAppClick,
                viewIdForLogging
            )
        }.sortedWith(comparator).toMutableList()

    fun setCreationLogged(isLogged: Boolean) {
        viewModel.creationLogged = isLogged
    }

    private fun createAppChipInfo(
        application: Application,
        packageName: String,
        user: UserHandle,
        category: Category,
        onClick: (packageName: String, user: UserHandle, category: String) -> Unit,
        viewIdForLogging: Long
    ): ChipInfo {
        if (!viewModel.creationLogged) {
            logFragmentCreated(
                packageName,
                user,
                viewIdForLogging,
                category == Category.ALLOWED,
                category == Category.ALLOWED_FOREGROUND,
                category == Category.DENIED
            )
        }
        return ChipInfo(
            title = KotlinUtils.getPackageLabel(application, packageName, user),
            contentDescription = AppUtils.getAppContentDescription(
                application,
                packageName,
                user.getIdentifier()
            ),
            icon = KotlinUtils.getBadgedPackageIcon(application, packageName, user),
            onClick = { onClick(packageName, user, category.categoryName) }
        )
    }
}

class ChipInfo(
    val title: String,
    val contentDescription: String? = null,
    val onClick: () -> Unit = {},
    val icon: Drawable? = null,
    val enabled: Boolean = true
)

internal class ChipComparator(
    val collator: Collator
) : Comparator<ChipInfo> {
    override fun compare(lhs: ChipInfo, rhs: ChipInfo): Int {
        var result = collator.compare(lhs.title, rhs.title)
        if (result == 0) {
            result = lhs.title.compareTo(rhs.title)
        }
        return result
    }
}
