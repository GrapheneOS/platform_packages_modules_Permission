/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.permission.ui.wear.model

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.ResultReceiver
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/*This class can be removed when the androidx library is available in Gerrit */
class RemoteActivityHelper(val context: Context) {

    companion object {
        @SuppressWarnings("ActionValue")
        private const val ACTION_REMOTE_INTENT: String =
            "com.google.android.wearable.intent.action.REMOTE_INTENT"

        private const val EXTRA_INTENT: String = "com.google.android.wearable.intent.extra.INTENT"

        private const val EXTRA_RESULT_RECEIVER: String =
            "com.google.android.wearable.intent.extra.RESULT_RECEIVER"

        private const val RESULT_OK: Int = 0

        private const val DEFAULT_PACKAGE = "com.google.android.wearable.app"

        private fun getResultReceiverForSending(receiver: ResultReceiver): ResultReceiver {
            val parcel = Parcel.obtain()
            receiver.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val receiverForSending = ResultReceiver.CREATOR.createFromParcel(parcel)
            parcel.recycle()
            return receiverForSending
        }
    }

    suspend fun startRemoteActivity(targetIntent: Intent): Boolean =
        suspendCancellableCoroutine { cont ->
            require(Intent.ACTION_VIEW == targetIntent.action) {
                "Only ${Intent.ACTION_VIEW} action is currently supported for starting a" +
                    " remote activity"
            }
            requireNotNull(targetIntent.data) {
                "Data Uri is required when starting a remote activity"
            }
            require(targetIntent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true) {
                "The category ${Intent.CATEGORY_BROWSABLE} must be present on the intent"
            }
            val remoteResultReceiver = RemoteIntentResultReceiver(cont)
            val remoteIntent =
                Intent(ACTION_REMOTE_INTENT).apply {
                    setPackage(DEFAULT_PACKAGE)
                    putExtra(EXTRA_INTENT, targetIntent)
                    putExtra(
                        EXTRA_RESULT_RECEIVER,
                        getResultReceiverForSending(remoteResultReceiver)
                    )
                }
            context.sendBroadcast(remoteIntent)
        }

    private class RemoteIntentResultReceiver(val continuation: Continuation<Boolean>) :
        ResultReceiver(null) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            continuation.resume(resultCode == RESULT_OK)
        }
    }
}
