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

package com.android.permission.access.data

import android.content.pm.PermissionInfo

data class Permission(
    val permissionInfo: PermissionInfo,
    val isReconciled: Boolean,
    val type: Int,
    val appId: Int
) {
    val name: String
        get() = permissionInfo.name

    val packageName: String
        get() = permissionInfo.packageName

    val isDynamic: Boolean
        get() = type == TYPE_DYNAMIC

    val protectionLevel: Int
        get() = permissionInfo.protectionLevel

    val knownCerts: Set<String>
        get() = permissionInfo.knownCerts

    companion object {
        // The permission is defined in an application manifest.
        const val TYPE_MANIFEST = 0
        // The permission is defined in a system config.
        const val TYPE_CONFIG = 1
        // The permission is defined dynamically.
        const val TYPE_DYNAMIC = 2
    }
}
