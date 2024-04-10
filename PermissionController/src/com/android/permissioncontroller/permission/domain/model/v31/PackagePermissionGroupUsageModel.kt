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

/** This data class stores all data accesses (derived from app ops) for a package and user. */
data class PackagePermissionGroupUsageModel(
    val packageName: String,
    /** Permission group and recent usage time in milliseconds since the epoch */
    val usages: Map<String, Long>,
    val userId: Int
)
