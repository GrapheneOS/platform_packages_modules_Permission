/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.util

import android.Manifest.permission.READ_CONTACTS
import android.Manifest.permission_group.CONTACTS
import android.Manifest.permission_group.UNDEFINED
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.test.platform.app.InstrumentationRegistry
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.permission.utils.Utils
import com.google.common.truth.Truth.assertThat
import org.junit.Test


class UtilsTest {
    private val context = InstrumentationRegistry.getInstrumentation().context as Context

    @Test
    fun assertGetOrGenerateSessionIdExpectedSessionId() {
        val intent = Intent()
        intent.putExtra(EXTRA_SESSION_ID, VALID_SESSION_ID)
        val sessionId = Utils.getOrGenerateSessionId(intent)
        assertThat(sessionId).isEqualTo(VALID_SESSION_ID)
    }

    @Test
    fun assertGetOrGenerateSessionIdRandomSessionId() {
        val intent = Intent()
        val sessionId = Utils.getOrGenerateSessionId(intent)
        assertThat(sessionId).isNotEqualTo(INVALID_SESSION_ID)
    }

    @Test
    fun assertGetGroupPermissionInfosValidGroup() {
        val permissionInfos = Utils.getGroupPermissionInfos(GROUP_NAME, context)
        assertThat(permissionInfos).isNotNull()
        val permissions = mutableListOf<String>()
        for (permissionInfo in permissionInfos!!) {
            permissions.add(permissionInfo.name)
        }
        assertThat(permissions.contains(READ_CONTACTS)).isTrue()
    }

    @Test
    fun assertGetGroupPermissionInfosInValidGroup() {
        assertThat(Utils.getGroupPermissionInfos(INVALID_GROUP_NAME, context)).isNull()
    }

    @Test
    fun assertGetGroupPermissionInfosUndefinedGroup() {
        val permissionInfos = Utils.getGroupPermissionInfos(UNDEFINED, context)
        assertThat(permissionInfos).isNotNull()
    }

    @Test
    fun assertGetGroupPermissionInfoWithPermissionName() {
        val permissionInfos = Utils.getGroupPermissionInfos(READ_CONTACTS, context)
        assertThat(permissionInfos).isNotNull()
        assertThat(permissionInfos!!.size).isEqualTo(1)
        assertThat(permissionInfos[0].name).isEqualTo(READ_CONTACTS)
    }

    @Test
    fun assertGetColorResIdValidId() {
        assertThat(Utils.getColorResId(context, android.R.attr.colorPrimary))
            .isNotEqualTo(Resources.ID_NULL)
    }

    @Test
    fun assertGetColorResIdInValidId() {
        assertThat(Utils.getColorResId(context, INVALID_ATTR_ID)).isEqualTo(Resources.ID_NULL)
    }

    companion object {
        private const val INVALID_ATTR_ID = 1000
        private const val VALID_SESSION_ID = 10000L
        private const val GROUP_NAME = CONTACTS
        private const val INVALID_GROUP_NAME = "invalid group name"
    }
}
