/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.permissionui.cts

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class StartForFutureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            Log.w(TAG, "Activity was recreated. (Perhaps due to a configuration change?)")
        }
    }

    fun startActivityForFuture(
        intent: Intent,
        future: CompletableFuture<Instrumentation.ActivityResult>
    ) {
        val requestCode = nextRequestCode.getAndIncrement()
        futures.put(requestCode, future)
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val future =
            futures.remove(requestCode)
                ?: throw IllegalStateException(
                    "StartForFutureActivity received an activity result with an unknown requestCode"
                )
        future.complete(Instrumentation.ActivityResult(resultCode, data))
        finish()
    }

    companion object {
        private val TAG = StartForFutureActivity::class.simpleName
        private var nextRequestCode = AtomicInteger(1)
        private val futures = LruCache<Int, CompletableFuture<Instrumentation.ActivityResult>>(10)
    }
}
