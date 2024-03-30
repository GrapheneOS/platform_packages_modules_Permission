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

package com.android.permissioncontroller.pm.data.repository

import android.app.Application
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.pm.data.model.PackageInfoModel
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository to access package info data exposed by [PackageManager]. Domain and view layer
 * shouldn't access [PackageManager] directly, instead they should use the repository.
 */
interface PackageRepository {
    suspend fun getPackageInfo(
        packageName: String,
        user: UserHandle,
        flags: Int = PackageManager.GET_PERMISSIONS
    ): PackageInfoModel?

    companion object {
        @Volatile private var instance: PackageRepository? = null

        fun getInstance(app: Application): PackageRepository =
            instance ?: synchronized(this) { PackageRepositoryImpl(app).also { instance = it } }
    }
}

class PackageRepositoryImpl(
    private val app: Application,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PackageRepository {
    override suspend fun getPackageInfo(
        packageName: String,
        user: UserHandle,
        flags: Int
    ): PackageInfoModel? =
        withContext(dispatcher) {
            try {
                val packageInfo =
                    Utils.getUserContext(app, user)
                        .packageManager
                        .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                PackageInfoModel(packageInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(LOG_TAG, "package $packageName not found for user ${user.identifier}")
                null
            }
        }

    companion object {
        private const val LOG_TAG = "PackageRepository"
    }
}
