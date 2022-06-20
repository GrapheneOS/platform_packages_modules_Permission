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
import android.safetycenter.cts.testing.Coroutines.runBlockingWithTimeout
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

/** A class that allows waiting for the broadcast queue to be idle. */
object WaitForBroadcastIdle {

    /** Waits for the broadcast queue to be idle. */
    fun Context.waitForBroadcastIdle() {
        try {
            callWithShellPermissionIdentity(
                { runBlockingWithTimeout { waitForBroadcastIdleAsync() } }, DUMP)
        } catch (ex: TimeoutCancellationException) {
            Log.e(TAG, "Timeout while waiting for broadcast queue to be idle")
        }
    }

    // This method is here to be able to timeout the `waitForBroadcastIdle` call, since the timeout
    // cannot apply to blocking code.
    private suspend fun Context.waitForBroadcastIdleAsync() {
        val activityManager = getSystemService(ActivityManager::class.java)!!
        withContext(Dispatchers.Default) {
            activityManager.waitForBroadcastIdle()
        }
    }

    private const val TAG = "WaitForBroadcastIdle"
}
