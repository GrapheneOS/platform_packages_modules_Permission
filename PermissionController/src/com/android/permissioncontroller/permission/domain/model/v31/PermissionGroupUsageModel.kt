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

package com.android.permissioncontroller.permission.domain.model.v31

/** Represents the private data access protected by the permission group. */
data class PermissionGroupUsageModel(
    val permissionGroup: String,
    /** Milliseconds since the epoch */
    val lastAccessTimestampMillis: Long,
    /**
     * Represents whether the permission group is highly visible to the user. Permission groups for
     * non system apps are always considered user sensitive and the usages are always shown in the
     * dashboard. If the permission group is not user sensitive (in case of system apps), those
     * usages are only shown when user click "Show system" button.
     */
    val isUserSensitive: Boolean,
)
