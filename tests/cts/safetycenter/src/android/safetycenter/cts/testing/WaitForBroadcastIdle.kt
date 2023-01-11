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

package android.safetycenter.cts.testing

import android.Manifest.permission.DUMP
import android.app.ActivityManager
import android.content.Context
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity

/** A class that allows waiting for the broadcast queue to be idle. */
object WaitForBroadcastIdle {

    /** Waits for the broadcast queue to be idle. */
    fun Context.waitForBroadcastIdle() {
        val activityManager = getSystemService(ActivityManager::class.java)!!
        callWithShellPermissionIdentity(DUMP) { activityManager.waitForBroadcastIdle() }
    }
}
