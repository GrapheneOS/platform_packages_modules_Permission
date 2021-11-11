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

package com.android.permissioncontroller.permission.data

/**
 * A record of a user's permission decision for an app.
 *
 * @param packageName package name of the app the user made the decision for
 * @param permissionGroupName permission group name of the decision that was made
 * @param decisionTime the time of the decision, in epoch time. Should be rounded to day-level
 * precision for user privacy.
 * @param isGranted whether the permission was granted or denied
 */
data class PermissionDecision(
    val packageName: String,
    val permissionGroupName: String,
    val decisionTime: Long,
    val isGranted: Boolean
)
