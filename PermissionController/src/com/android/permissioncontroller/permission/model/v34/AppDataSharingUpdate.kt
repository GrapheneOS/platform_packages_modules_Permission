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

package com.android.permissioncontroller.permission.model.v34

import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Class representing an update in an app's data sharing policy from its safety label.
 *
 * Note that safety labels are part of package information, and therefore the safety label
 * information applies to apps for all users that have the app installed.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
data class AppDataSharingUpdate(
    /** Package name for the app with the data sharing update. */
    val packageName: String,
    /** How data sharing for each category has changed. */
    val categorySharingUpdates: Map<String, DataSharingUpdateType>
) {

    /** Companion object for [AppDataSharingUpdate]. */
    companion object {
        // TODO(b/263153040): Use categories from safety label library.
        const val LOCATION_CATEGORY = "location"
    }
}

/** Different ways in which data sharing can be updated for a particular data category. */
enum class DataSharingUpdateType {
    NOT_SIGNIFICANT,
    ADDS_ADVERTISING_PURPOSE,
    ADDS_SHARING_WITHOUT_ADVERTISING_PURPOSE,
    ADDS_SHARING_WITH_ADVERTISING_PURPOSE
}
