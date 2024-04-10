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
 * Collection of app op usages for a package and user. App op usage represent private data access
 * (i.e. location, contact access) by the app/package.
 */
data class PackageAppOpUsageModel(
    val packageName: String,
    val usages: List<AppOpUsageModel>,
    val userId: Int
) {
    /** Data class representing an app op and the recent access time by an app. */
    data class AppOpUsageModel(
        val appOpName: String,
        /** Milliseconds since the epoch */
        val lastAccessTimestampMillis: Long,
    )
}
