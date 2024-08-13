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

package com.android.permissioncontroller.appops.data.model.v31

/**
 * Data class representing historical package ops read from DiscreteRegistry, These ops are for one
 * permission group, as the permission timeline page show one group at a time.
 */
data class DiscretePackageOpsModel(
    val packageName: String,
    val userId: Int,
    val appOpAccesses: List<DiscreteOpModel>,
    // some package may support attribution, in that case we want to group ops by label.
    var attributionLabel: String? = null,
    // variable to indicate whether the permission group is user sensitive or not.
    var isUserSensitive: Boolean = false,
) {
    /**
     * The time stamps (i.e. accessTimeMillis and durationMillis) are with precision rounded to
     * minute.
     */
    data class DiscreteOpModel(
        val opName: String,
        val accessTimeMillis: Long,
        val durationMillis: Long,
        val attributionTag: String? = null,
        val proxyPackageName: String? = null,
        val proxyUserId: Int? = null,
    )
}
