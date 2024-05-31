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

package android.permissionmultidevice.cts

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log

object PermissionUtils {
    private val TAG = PermissionUtils::class.java.getSimpleName()

    fun isAutomotive(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    fun isWatch(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

    /**
     * This method checks for the minimum screen size described in CDD {@see
     * https://source.android.com/docs/compatibility/14/android-14-cdd#7111_screen_size_and_shape}
     */
    fun isCddCompliantScreenSize(): Boolean {
        if (
            Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_WATCH
        ) {
            Log.d(TAG, "UI mode is UI_MODE_TYPE_WATCH, skipping the min dp check")
            return true
        }

        val screenSize =
            Resources.getSystem().configuration.screenLayout and
                Configuration.SCREENLAYOUT_SIZE_MASK
        return when (screenSize) {
            Configuration.SCREENLAYOUT_SIZE_SMALL -> hasMinScreenSize(426, 320)
            Configuration.SCREENLAYOUT_SIZE_NORMAL -> hasMinScreenSize(480, 320)
            Configuration.SCREENLAYOUT_SIZE_LARGE -> hasMinScreenSize(640, 480)
            Configuration.SCREENLAYOUT_SIZE_XLARGE -> hasMinScreenSize(960, 720)
            else -> {
                Log.e(TAG, "Unknown screen size: $screenSize")
                true
            }
        }
    }

    private fun hasMinScreenSize(minWidthDp: Int, minHeightDp: Int): Boolean {
        val dpi = Resources.getSystem().displayMetrics.densityDpi
        val widthDp = (160f / dpi) * Resources.getSystem().displayMetrics.widthPixels
        val heightDp = (160f / dpi) * Resources.getSystem().displayMetrics.heightPixels

        // CDD does seem to follow width & height convention correctly, hence checking both ways
        return (widthDp >= minWidthDp && heightDp >= minHeightDp) ||
            (widthDp >= minHeightDp && heightDp >= minWidthDp)
    }
}
