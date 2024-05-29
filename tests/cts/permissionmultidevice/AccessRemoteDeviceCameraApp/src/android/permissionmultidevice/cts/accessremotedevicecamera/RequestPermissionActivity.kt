/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.permissionmultidevice.cts.accessremotedevicecamera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.RemoteCallback
import android.permissionmultidevice.cts.TestConstants

class RequestPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId =
            intent.getIntExtra(
                PackageManager.EXTRA_REQUEST_PERMISSIONS_DEVICE_ID,
                Context.DEVICE_ID_DEFAULT
            )

        requestPermissions(DEVICE_AWARE_PERMISSIONS, 1001, deviceId)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        val resultReceiver =
            intent.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER, RemoteCallback::class.java)
        val result =
            Bundle().apply {
                putStringArray(TestConstants.PERMISSION_RESULT_KEY_PERMISSIONS, permissions)
                putIntArray(TestConstants.PERMISSION_RESULT_KEY_GRANT_RESULTS, grantResults)
                putInt(TestConstants.PERMISSION_RESULT_KEY_DEVICE_ID, deviceId)
            }

        resultReceiver?.sendResult(result)
        finish()
    }

    companion object {
        private val DEVICE_AWARE_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
