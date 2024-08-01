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

package com.android.permissioncontroller.data.repository.v31

import android.app.AppOpsManager
import android.content.pm.PackageManager
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.permission.data.PackageBroadcastReceiver

class PermissionChangeListener(
    private val packageManager: PackageManager,
    private val callback: () -> Unit
) : PackageManager.OnPermissionsChangedListener {
    fun register() {
        packageManager.addOnPermissionsChangeListener(this)
    }

    fun unregister() {
        packageManager.removeOnPermissionsChangeListener(this)
    }

    override fun onPermissionsChanged(uid: Int) {
        callback()
    }
}

class PackageChangeListener(private val callback: () -> Unit) :
    PackageBroadcastReceiver.PackageBroadcastListener {

    fun register() {
        PackageBroadcastReceiver.addAllCallback(this)
    }

    fun unregister() {
        PackageBroadcastReceiver.removeAllCallback(this)
    }

    override fun onPackageUpdate(packageName: String) {
        callback()
    }
}

// Suppress OnOpNotedListener, it was introduced in Q but became System API in U.
@SuppressWarnings("NewApi")
class AppOpChangeListener(
    private val opNames: Set<String>,
    private val appOpsManager: AppOpsManager,
    private val callback: () -> Unit
) :
    AppOpsManager.OnOpChangedListener,
    AppOpsManager.OnOpNotedListener,
    AppOpsManager.OnOpActiveChangedListener {

    fun register() {
        opNames.forEach { opName ->
            // TODO(b/262035952): We watch each active op individually as
            //  startWatchingActive only registers the callback if all ops are valid.
            //  Fix this behavior so if one op is invalid it doesn't affect the other ops.
            try {
                appOpsManager.startWatchingActive(arrayOf(opName), { it.run() }, this)
            } catch (ignored: IllegalArgumentException) {
                // Older builds may not support all requested app ops.
            }

            try {
                appOpsManager.startWatchingMode(opName, /* all packages */ null, this)
            } catch (ignored: IllegalArgumentException) {
                // Older builds may not support all requested app ops.
            }

            if (SdkLevel.isAtLeastU()) {
                try {
                    appOpsManager.startWatchingNoted(arrayOf(opName), this)
                } catch (ignored: IllegalArgumentException) {
                    // Older builds may not support all requested app ops.
                }
            }
        }
    }

    fun unregister() {
        appOpsManager.stopWatchingActive(this)
        appOpsManager.stopWatchingMode(this)
        if (SdkLevel.isAtLeastU()) {
            appOpsManager.stopWatchingNoted(this)
        }
    }

    override fun onOpChanged(op: String?, packageName: String?) {
        callback()
    }

    override fun onOpNoted(
        op: String,
        uid: Int,
        packageName: String,
        attributionTag: String?,
        flags: Int,
        result: Int
    ) {
        callback()
    }

    override fun onOpActiveChanged(op: String, uid: Int, packageName: String, active: Boolean) {
        callback()
    }
}
