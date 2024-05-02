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

package android.permissionui.cts.usepermission

import android.Manifest.permission.READ_CONTACTS
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler

class Activity1 : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(READ_CONTACTS), 2)
    }

    // Once the permissions dialog is showing over this activity, trigger the next dialog, and
    // finish
    override fun onPause() {
        super.onPause()
        finish()
        Handler().postDelayed({ startActivity(Intent(this, Activity2::class.java)) }, 1000)
    }
}
