/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.hibernation

import android.apphibernation.AppHibernationManager
import android.content.Context
import android.content.Context.APP_HIBERNATION_SERVICE
import android.os.UserHandle
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo

/**
 * Hibernation controller that handles modifying hibernation state.
 */
class HibernationController(val context: Context) {

    companion object {
        private const val LOG_TAG = "HibernationController"
        private const val DEBUG_HIBERNATION = true
    }

    /**
     * Hibernates the apps provided for each user.
     *
     * @param apps map of each user to a list of packages that should be hibernated for the user
     * @return list of apps that were successfully hibernated
     */
    fun hibernateApps(
        apps: Map<UserHandle, List<LightPackageInfo>>
    ): List<Pair<String, UserHandle>> {
        val hibernatedApps = mutableListOf<Pair<String, UserHandle>>()
        for ((user, userApps) in apps) {
            val userContext = context.createContextAsUser(user, 0 /* flags */)
            val hibernationManager =
                userContext.getSystemService(APP_HIBERNATION_SERVICE) as AppHibernationManager
            for (pkg in userApps) {
                try {
                    hibernationManager.setHibernatingForUser(pkg.packageName, true)
                    hibernatedApps.add(pkg.packageName to user)
                } catch (e: Exception) {
                    DumpableLog.e(LOG_TAG, "Failed to hibernate package: ${pkg.packageName}", e)
                }
            }
        }
        if (DEBUG_HIBERNATION) {
            DumpableLog.i(LOG_TAG,
                "Done hibernating apps $hibernatedApps")
        }
        return hibernatedApps
    }
}
