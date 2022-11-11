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

import com.android.permission.access.collection.* // ktlint-disable no-wildcard-imports

class AccessPersistence {
    fun read(state: AccessState) {
        readSystemState(state.systemState)
        val userStates = state.userStates
        state.systemState.userIds.forEachIndexed { _, userId ->
            readUserState(userId, userStates.getOrPut(userId) { UserState() })
        }
    }

    private fun readSystemState(systemState: SystemState) {
        TODO()
    }

    private fun readUserState(userId: Int, userState: UserState) {
        TODO()
    }

    fun write(state: AccessState) {
        writeState(state.systemState, ::writeSystemState)
        state.userStates.forEachIndexed { _, userId, userState ->
            writeState(userState) { writeUserState(userId, it) }
        }
    }

    private inline fun <T : WritableState> writeState(state: T, write: (T) -> Unit) {
        when (val writeMode = state.writeMode) {
            WriteMode.NONE -> {}
            WriteMode.SYNC -> write(state)
            WriteMode.ASYNC -> TODO()
            else -> error(writeMode)
        }
    }

    private fun writeSystemState(systemState: SystemState) {
        TODO()
    }

    private fun writeUserState(userId: Int, userState: UserState) {
        TODO()
    }
}
