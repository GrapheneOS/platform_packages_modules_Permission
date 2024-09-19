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

package com.android.role

import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.role.RoleServicePlatformHelper
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class RoleUserStateTest {
    private var roleServicePlatformHelper = mock(RoleServicePlatformHelper::class.java)
    private var roleUserStateCallback = mock(RoleUserState.Callback::class.java)

    private val userId = UserHandle.myUserId()
    private val roleUserState =
        RoleUserState(userId, roleServicePlatformHelper, roleUserStateCallback, false)

    @Before
    fun setUp() {
        setUpRole(ROLE_NAME_1, true)
        setUpRole(ROLE_NAME_2, false)
    }

    private fun setUpRole(roleName: String, fallbackEnabled: Boolean) {
        roleUserState.addRoleName(roleName)
        roleUserState.setFallbackEnabled(roleName, fallbackEnabled)
    }

    @Test
    fun testUpgradeVersion_upgradeNotNeeded_remainsUnchanged() {
        roleUserState.version = RoleUserState.VERSION_FALLBACK_STATE_MIGRATED
        val legacyFallbackDisabledRoles = listOf(ROLE_NAME_1, ROLE_NAME_2)

        roleUserState.upgradeVersion(legacyFallbackDisabledRoles)

        assertRoleFallbackState(ROLE_NAME_1, roleUserState.isFallbackEnabled(ROLE_NAME_1), true)
        assertRoleFallbackState(ROLE_NAME_2, roleUserState.isFallbackEnabled(ROLE_NAME_2), false)
    }

    @Test
    fun testUpgradeVersion_upgradeNeeded_disabledFallbackStateMigrated() {
        roleUserState.version = RoleUserState.VERSION_UNDEFINED
        val legacyFallbackDisabledRoles = listOf(ROLE_NAME_1, ROLE_NAME_2)

        roleUserState.upgradeVersion(legacyFallbackDisabledRoles)

        assertRoleFallbackState(ROLE_NAME_1, roleUserState.isFallbackEnabled(ROLE_NAME_1), false)
        assertRoleFallbackState(ROLE_NAME_2, roleUserState.isFallbackEnabled(ROLE_NAME_2), false)
    }

    @Test
    fun testUpgradeVersion_upgradeNeeded_enabledFallbackStateMigrated() {
        roleUserState.version = RoleUserState.VERSION_UNDEFINED
        val legacyFallbackDisabledRoles = emptyList<String>()

        roleUserState.upgradeVersion(legacyFallbackDisabledRoles)

        assertRoleFallbackState(ROLE_NAME_1, roleUserState.isFallbackEnabled(ROLE_NAME_1), true)
        assertRoleFallbackState(ROLE_NAME_2, roleUserState.isFallbackEnabled(ROLE_NAME_2), true)
    }

    private fun assertRoleFallbackState(roleName: String, actual: Boolean, expected: Boolean) {
        assertWithMessage(
                "Fallback enabled state for role: $roleName is $actual while" +
                    " is expected to be $expected"
            )
            .that(actual)
            .isEqualTo(expected)
    }

    companion object {
        private const val ROLE_NAME_1 = "ROLE_NAME_1"
        private const val ROLE_NAME_2 = "ROLE_NAME_2"
    }
}
