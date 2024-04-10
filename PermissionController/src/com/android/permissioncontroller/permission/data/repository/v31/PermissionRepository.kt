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

package com.android.permissioncontroller.permission.data.repository.v31

import android.app.Application
import android.content.Context
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.text.TextUtils
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.permission.utils.PermissionMapping
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This repository encapsulates permission data (i.e. permission or permission group definitions,
 * package permission states etc.) exposed by [android.permission.PermissionManager] and
 * [PackageManager].
 */
interface PermissionRepository {
    /**
     * Gets the flags associated with a permission.
     *
     * @see PackageManager.getPermissionFlags
     */
    suspend fun getPermissionFlags(
        permissionName: String,
        packageName: String,
        user: UserHandle
    ): Int

    /**
     * Gets a permission group label for a given permission group.
     *
     * @see PackageManager.getPermissionGroupInfo
     * @see PackageItemInfo.loadSafeLabel
     */
    suspend fun getPermissionGroupLabel(context: Context, groupName: String): CharSequence

    /** Gets a list of permission group to be shown in the privacy dashboard. */
    fun getPermissionGroupsForPrivacyDashboard(): List<String>

    companion object {
        @Volatile private var instance: PermissionRepository? = null

        fun getInstance(application: Application): PermissionRepository =
            instance
                ?: synchronized(this) {
                    PermissionRepositoryImpl(application).also { instance = it }
                }
    }
}

class PermissionRepositoryImpl(
    application: Application,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PermissionRepository {
    private val packageManager = application.packageManager

    override suspend fun getPermissionFlags(
        permissionName: String,
        packageName: String,
        user: UserHandle
    ): Int =
        withContext(dispatcher) {
            packageManager.getPermissionFlags(permissionName, packageName, user)
        }

    /**
     * Gets a permission group's label from the system.
     *
     * @param context The context from which to get the label
     * @param groupName The name of the permission group whose label we want
     * @return The permission group's label, or the group name, if the group is invalid
     */
    override suspend fun getPermissionGroupLabel(
        context: Context,
        groupName: String
    ): CharSequence =
        withContext(dispatcher) {
            val groupInfo = getPermissionGroupInfo(groupName, context)
            groupInfo?.loadSafeLabel(
                context.packageManager,
                0f,
                TextUtils.SAFE_STRING_FLAG_FIRST_LINE or TextUtils.SAFE_STRING_FLAG_TRIM
            )
                ?: groupName
        }

    /**
     * Get the [PackageItemInfo] for the given permission group.
     *
     * @param groupName the group
     * @param context the `Context` to retrieve `PackageManager`
     * @return The info of permission group or null if the group does not have runtime permissions.
     */
    private fun getPermissionGroupInfo(groupName: String, context: Context): PackageItemInfo? {
        return try {
            context.packageManager.getPermissionGroupInfo(groupName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
            ?: try {
                context.packageManager.getPermissionInfo(groupName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
    }

    override fun getPermissionGroupsForPrivacyDashboard(): List<String> {
        return if (SdkLevel.isAtLeastT()) {
            PermissionMapping.getPlatformPermissionGroups().filter {
                it != android.Manifest.permission_group.NOTIFICATIONS
            }
        } else {
            PermissionMapping.getPlatformPermissionGroups()
        }
    }
}
