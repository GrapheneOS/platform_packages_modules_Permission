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

package com.android.permissioncontroller.role.data.repository.v31

import android.app.Application
import android.app.role.RoleManager
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This repository encapsulates roles data (i.e. querying/adding role holders) exposed by
 * [RoleManager].
 */
interface RoleRepository {
    /**
     * @return Set of package names, the usage of private data by these packages is not shown in the
     *   privacy dashboard.
     */
    suspend fun getExemptedPackages(): Set<String>

    companion object {
        @Volatile private var instance: RoleRepository? = null

        fun getInstance(application: Application): RoleRepository =
            instance
                ?: synchronized(this) { RoleRepositoryImpl(application).also { instance = it } }
    }
}

class RoleRepositoryImpl(application: Application) : RoleRepository {
    private val roleManager = application.getSystemService(RoleManager::class.java)!!

    override suspend fun getExemptedPackages(): Set<String> =
        withContext(Dispatchers.Default) {
            return@withContext buildSet {
                add(OS_PKG)
                addAll(EXEMPTED_ROLES.map { role -> roleManager.getRoleHolders(role) }.flatten())
            }
        }

    companion object {
        private const val OS_PKG = "android"
        private const val SYSTEM_AMBIENT_AUDIO_INTELLIGENCE =
            "android.app.role.SYSTEM_AMBIENT_AUDIO_INTELLIGENCE"
        private const val SYSTEM_UI_INTELLIGENCE = "android.app.role.SYSTEM_UI_INTELLIGENCE"
        private const val SYSTEM_AUDIO_INTELLIGENCE = "android.app.role.SYSTEM_AUDIO_INTELLIGENCE"
        private const val SYSTEM_NOTIFICATION_INTELLIGENCE =
            "android.app.role.SYSTEM_NOTIFICATION_INTELLIGENCE"
        private const val SYSTEM_TEXT_INTELLIGENCE = "android.app.role.SYSTEM_TEXT_INTELLIGENCE"
        private const val SYSTEM_VISUAL_INTELLIGENCE = "android.app.role.SYSTEM_VISUAL_INTELLIGENCE"

        private val EXEMPTED_ROLES =
            arrayOf(
                SYSTEM_AMBIENT_AUDIO_INTELLIGENCE,
                SYSTEM_UI_INTELLIGENCE,
                SYSTEM_AUDIO_INTELLIGENCE,
                SYSTEM_NOTIFICATION_INTELLIGENCE,
                SYSTEM_TEXT_INTELLIGENCE,
                SYSTEM_VISUAL_INTELLIGENCE
            )
    }
}
