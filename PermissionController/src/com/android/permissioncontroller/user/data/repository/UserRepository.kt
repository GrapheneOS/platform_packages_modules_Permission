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

package com.android.permissioncontroller.user.data.repository

import android.app.Application
import android.content.pm.UserProperties
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** This repository encapsulate user and user profiles data exposed by [UserManager]. */
interface UserRepository {
    /**
     * Returns a list of UserHandles for profiles associated with the context user, including the
     * user itself.
     *
     * <p>Note that this includes all profile types (not including Restricted profiles).
     */
    suspend fun getUserProfilesIncludingCurrentUser(): List<Int>

    /**
     * Returns whether a user should be shown in the Settings and sharing surfaces depending on the
     * quiet mode. This is only applicable to profile users since the quiet mode concept is only
     * applicable to profile users.
     */
    suspend fun shouldShowInQuietMode(userId: Int): Boolean

    /**
     * Returns whether the given profile is in quiet mode or not. Notes: Quiet mode is only
     * supported for managed profiles.
     */
    suspend fun isQuietModeEnabled(userId: Int): Boolean

    companion object {
        @Volatile private var instance: UserRepository? = null

        fun getInstance(application: Application): UserRepository =
            instance
                ?: synchronized(this) { UserRepositoryImpl(application).also { instance = it } }
    }
}

class UserRepositoryImpl(
    application: Application,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : UserRepository {
    private val userManager = application.getSystemService(UserManager::class.java)!!

    override suspend fun getUserProfilesIncludingCurrentUser(): List<Int> =
        withContext(dispatcher) { userManager.userProfiles.map { it.identifier } }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override suspend fun shouldShowInQuietMode(userId: Int): Boolean =
        withContext(dispatcher) {
            val quiteMode = userManager.getUserProperties(UserHandle.of(userId)).showInQuietMode
            quiteMode != UserProperties.SHOW_IN_QUIET_MODE_HIDDEN
        }

    override suspend fun isQuietModeEnabled(userId: Int): Boolean =
        withContext(dispatcher) { userManager.isQuietModeEnabled(UserHandle.of(userId)) }
}
