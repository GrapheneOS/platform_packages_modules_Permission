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

package com.android.ecm

import android.permission.flags.Flags
import android.util.Log
import com.android.internal.annotations.Keep
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerStatsLog

/**
 * Provides ECM-related metrics logging for Permission APEX services.
 *
 * @hide
 */
@Keep
object EnhancedConfirmationStatsLogUtils {
    private val LOG_TAG = EnhancedConfirmationStatsLogUtils::class.java.simpleName

    fun logRestrictionCleared(uid: Int) {
        if (!SdkLevel.isAtLeastV() || !Flags.enhancedConfirmationModeApisEnabled()) {
            return
        }
        Log.v(LOG_TAG, "ENHANCED_CONFIRMATION_RESTRICTION_CLEARED: uid='$uid'")

        PermissionControllerStatsLog.write(
            PermissionControllerStatsLog.ENHANCED_CONFIRMATION_RESTRICTION_CLEARED,
            uid
        )
    }
}
