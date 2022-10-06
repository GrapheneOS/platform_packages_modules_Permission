/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permission.access.appop

import com.android.permission.access.AccessState
import com.android.permission.access.AccessUri
import com.android.permission.access.AppOpUri
import com.android.permission.access.UidUri
import com.android.permission.access.UserState
import com.android.permission.access.util.* // ktlint-disable no-wildcard-imports

class UidAppOpPolicy : BaseAppOpPolicy() {
    override val subjectScheme: String
        get() = UidUri.SCHEME

    override val objectScheme: String
        get() = AppOpUri.SCHEME

    override fun getDecision(subject: AccessUri, `object`: AccessUri, state: AccessState): Int {
        subject as UidUri
        `object` as AppOpUri
        return getAppOpMode(state.userStates[subject.userId]?.uidAppOpModes
            ?.get(subject.appId), `object`.appOpName)
    }

    override fun setDecision(
        subject: AccessUri,
        `object`: AccessUri,
        decision: Int,
        oldState: AccessState,
        newState: AccessState
    ) {
        subject as UidUri
        `object` as AppOpUri
        val modes = newState.userStates.getOrPut(subject.userId) { UserState() }
            .uidAppOpModes.getOrPut(subject.appId) { IndexedMap() }
        setAppOpMode(modes, `object`.appOpName, decision)
    }

    override fun onAppIdRemoved(appId: Int, oldState: AccessState, newState: AccessState) {
        newState.userStates.forEachIndexed { _, _, userState ->
            userState.uidAppOpModes -= appId
        }
    }
}
