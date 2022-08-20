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

package com.android.permission.access.permission

import com.android.permission.access.AccessDecisions
import com.android.permission.access.AccessState
import com.android.permission.access.AccessUri
import com.android.permission.access.PermissionUri
import com.android.permission.access.SchemePolicy
import com.android.permission.access.UidUri
import com.android.permission.access.UserState
import com.android.permission.access.external.PackageState
import com.android.permission.access.util.* // ktlint-disable no-wildcard-imports

class UidPermissionPolicy : SchemePolicy() {
    override val subjectScheme: String
        get() = UidUri.SCHEME

    override val objectScheme: String
        get() = PermissionUri.SCHEME

    override fun getDecision(subject: AccessUri, `object`: AccessUri, state: AccessState): Int {
        subject as UidUri
        `object` as PermissionUri
        val flags = state.userStates[subject.userId]?.permissionFlags?.get(subject.appId)
            ?.get(`object`.permissionName) ?: return AccessDecisions.DENIED
        return when (flags) {
            // TODO
            0 -> AccessDecisions.DENIED
            else -> error(flags)
        }
    }

    override fun setDecision(
        subject: AccessUri,
        `object`: AccessUri,
        decision: Int,
        oldState: AccessState,
        newState: AccessState
    ) {
        subject as UidUri
        `object` as PermissionUri
        val uidFlags = newState.userStates.getOrPut(subject.userId) { UserState() }
            .permissionFlags.getOrPut(subject.appId) { IndexedMap() }
        val flags = when (decision) {
            // TODO
            AccessDecisions.DENIED -> 0
            else -> error(decision)
        }
        uidFlags[`object`.permissionName] = flags
    }

    override fun onUserAdded(userId: Int, oldState: AccessState, newState: AccessState) {
        // TODO
    }

    override fun onUserRemoved(userId: Int, oldState: AccessState, newState: AccessState) {}

    override fun onAppIdAdded(appId: Int, oldState: AccessState, newState: AccessState) {
        // TODO
    }

    override fun onAppIdRemoved(appId: Int, oldState: AccessState, newState: AccessState) {
        // TODO
    }

    override fun onPackageAdded(
        packageState: PackageState,
        oldState: AccessState,
        newState: AccessState
    ) {
        // TODO
    }

    override fun onPackageRemoved(
        packageState: PackageState,
        oldState: AccessState,
        newState: AccessState
    ) {
        // TODO
    }
}
