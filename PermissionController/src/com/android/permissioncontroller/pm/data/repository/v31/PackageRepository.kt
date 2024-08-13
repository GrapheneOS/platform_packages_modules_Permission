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

package com.android.permissioncontroller.pm.data.repository.v31

import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.pm.data.model.v31.PackageAttributionModel
import com.android.permissioncontroller.pm.data.model.v31.PackageInfoModel
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository to access package info data exposed by [PackageManager]. Domain and view layer
 * shouldn't access [PackageManager] directly, instead they should use the repository.
 */
interface PackageRepository {
    /**
     * Returns a package label for the given [packageName] and [user] Returns [packageName] if the
     * package is not found.
     */
    fun getPackageLabel(packageName: String, user: UserHandle): String

    /**
     * Returns a package's badged icon for the given [packageName] and [user] Returns null if the
     * package is not found.
     */
    fun getBadgedPackageIcon(packageName: String, user: UserHandle): Drawable?

    /**
     * Returns a [PackageInfoModel] for the given [packageName] and [user] Returns null if the
     * package is not found.
     */
    suspend fun getPackageInfo(
        packageName: String,
        user: UserHandle,
        flags: Int = PackageManager.GET_PERMISSIONS
    ): PackageInfoModel?

    /**
     * Returns a [PackageAttributionModel] for the given [packageName] and [user] Returns null if
     * the package is not found.
     */
    suspend fun getPackageAttributionInfo(
        packageName: String,
        user: UserHandle,
    ): PackageAttributionModel?

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
    override fun getPackageLabel(packageName: String, user: UserHandle): String {
        return try {
            val userContext = Utils.getUserContext(app, user)
            val appInfo = userContext.packageManager.getApplicationInfo(packageName, 0)
            Utils.getFullAppLabel(appInfo, app)
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    override fun getBadgedPackageIcon(packageName: String, user: UserHandle): Drawable? {
        return try {
            val userContext = Utils.getUserContext(app, user)
            val appInfo = userContext.packageManager.getApplicationInfo(packageName, 0)
            Utils.getBadgedIcon(app, appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

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
                        .getPackageInfo(packageName, flags)
                PackageInfoModel(packageInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(LOG_TAG, "package $packageName not found for user ${user.identifier}")
                null
            }
        }

    @Suppress("DEPRECATION")
    override suspend fun getPackageAttributionInfo(
        packageName: String,
        user: UserHandle,
    ): PackageAttributionModel? =
        withContext(dispatcher) {
            try {
                val packageInfo =
                    Utils.getUserContext(app, user)
                        .packageManager
                        .getPackageInfo(packageName, PackageManager.GET_ATTRIBUTIONS)
                val attributionUserVisible =
                    packageInfo.applicationInfo?.areAttributionsUserVisible() ?: false
                if (attributionUserVisible && SdkLevel.isAtLeastS()) {
                    val pkgContext = app.createPackageContext(packageName, 0)
                    val attributionTagToLabelRes =
                        packageInfo.attributions?.associate { it.tag to it.label }
                    val labelResToLabelStringMap =
                        attributionTagToLabelRes
                            ?.mapNotNull { entry ->
                                val labelString =
                                    try {
                                        pkgContext.getString(entry.value)
                                    } catch (e: Resources.NotFoundException) {
                                        null
                                    }
                                if (labelString != null) entry.value to labelString else null
                            }
                            ?.toMap()
                    PackageAttributionModel(
                        packageName,
                        true,
                        attributionTagToLabelRes,
                        labelResToLabelStringMap
                    )
                } else {
                    PackageAttributionModel(packageName)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(LOG_TAG, "package $packageName not found for user ${user.identifier}")
                null
            }
        }

    companion object {
        private const val LOG_TAG = "PackageRepository"
    }
}
