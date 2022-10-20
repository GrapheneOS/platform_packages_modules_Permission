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

package com.android.permission.access

import android.content.pm.PermissionGroupInfo
import com.android.permission.access.data.Permission
import com.android.permission.access.external.PackageState
import com.android.permission.access.util.* // ktlint-disable no-wildcard-imports

class AccessState private constructor(
    val systemState: SystemState,
    val userStates: IntMap<UserState>
) {
    constructor() : this(SystemState(), IntMap())

    fun copy(): AccessState = AccessState(systemState.copy(), userStates.copy { it.copy() })
}

class SystemState private constructor(
    val userIds: IntSet,
    val packageStates: IndexedMap<String, PackageState>,
    val appIds: IntMap<IndexedListSet<String>>,
    val permissionGroups: IndexedMap<String, PermissionGroupInfo>,
    val permissionTrees: IndexedMap<String, Permission>,
    val permissions: IndexedMap<String, Permission>
) : WritableState() {
    constructor() : this(IntSet(), IndexedMap(), IntMap(), IndexedMap(), IndexedMap(), IndexedMap())

    fun copy(): SystemState =
        SystemState(
            userIds.copy(), packageStates.copy { it }, appIds.copy { it.copy() },
            permissionGroups.copy { it }, permissionTrees.copy { it }, permissions.copy { it }
        )
}

class UserState private constructor(
    val permissionFlags: IntMap<IndexedMap<String, Int>>,
    val uidAppOpModes: IntMap<IndexedMap<String, Int>>,
    val packageAppOpModes: IndexedMap<String, IndexedMap<String, Int>>,
) : WritableState() {
    constructor() : this(IntMap(), IntMap(), IndexedMap())

    fun copy(): UserState = UserState(permissionFlags.copy { it.copy { it } },
        uidAppOpModes.copy { it.copy { it } }, packageAppOpModes.copy { it.copy { it } })
}

object WriteMode {
    const val NONE = 0
    const val SYNC = 1
    const val ASYNC = 2
}

abstract class WritableState {
    var writeMode: Int = WriteMode.NONE
        private set

    fun requestWrite(sync: Boolean = false) {
        if (sync) {
            writeMode = WriteMode.SYNC
        } else {
            if (writeMode != WriteMode.SYNC) {
                writeMode = WriteMode.ASYNC
            }
        }
    }
}
