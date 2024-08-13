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

/**
 * This model class provides data for creating one row/preference on the permission timeline page.
 * The time stamps are with precision rounded to minute.
 */
data class PermissionTimelineUsageModel(
    val packageName: String,
    val userId: Int,
    val opNames: Set<String>,
    val accessStartMillis: Long,
    val accessEndMillis: Long,
    val durationMillis: Long,
    val isUserSensitive: Boolean = false,
    val attributionLabel: String? = null,
    val attributionTags: Set<String>? = null,
    val proxyPackageName: String? = null,
    val proxyUserId: Int? = null,
)

sealed class PermissionTimelineUsageModelWrapper {
    data object Loading : PermissionTimelineUsageModelWrapper()

    data class Success(val timelineUsageModels: List<PermissionTimelineUsageModel>) :
        PermissionTimelineUsageModelWrapper()
}
