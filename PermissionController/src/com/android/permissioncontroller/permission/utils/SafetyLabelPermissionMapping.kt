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

package com.android.permissioncontroller.permission.utils

import android.Manifest
import com.android.permission.safetylabel.DataCategoryConstants

/**
 * This file contains the canonical mapping of permission and permission group to Safety Label
 * categories and types used in the Permission settings screens and grant dialog. It also includes
 * methods related to that mapping.
 */
object SafetyLabelPermissionMapping {

    /**
     * Get the Safety Label categories pertaining to a specified permission group.
     *
     * @param groupName the permission group name
     *
     * @return The categories or an empty list if the group does not have supported and mapped group
     * to safety label category
     */
    fun getCategoriesForPermissionGroup(groupName: String): List<String> {
        return if (groupName == Manifest.permission_group.LOCATION) {
            listOf(DataCategoryConstants.CATEGORY_LOCATION)
        } else {
            emptyList()
        }
    }
}
