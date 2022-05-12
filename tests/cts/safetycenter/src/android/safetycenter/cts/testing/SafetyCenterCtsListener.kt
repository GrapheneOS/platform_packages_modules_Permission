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

import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.runBlockingWithTimeout
import java.time.Duration
import kotlinx.coroutines.channels.Channel

/**
 * An [OnSafetyCenterDataChangedListener] that facilitates receiving updates from SafetyCenter in
 * tests.
 */
class SafetyCenterCtsListener : OnSafetyCenterDataChangedListener {
    private val dataChannel = Channel<SafetyCenterData>(Channel.UNLIMITED)
    private val errorChannel = Channel<SafetyCenterErrorDetails>(Channel.UNLIMITED)

    override fun onSafetyCenterDataChanged(data: SafetyCenterData) {
        runBlockingWithTimeout { dataChannel.send(data) }
    }

    override fun onError(errorDetails: SafetyCenterErrorDetails) {
        runBlockingWithTimeout { errorChannel.send(errorDetails) }
    }

    /** Waits for a [SafetyCenterData] update from SafetyCenter within the given [timeout]. */
    fun receiveSafetyCenterData(timeout: Duration = TIMEOUT_LONG) =
        runBlockingWithTimeout(timeout) { dataChannel.receive() }

    /**
     * Waits for a [SafetyCenterErrorDetails] update from SafetyCenter within the given [timeout].
     */
    fun receiveSafetyCenterErrorDetails(timeout: Duration = TIMEOUT_LONG) =
        runBlockingWithTimeout(timeout) { errorChannel.receive() }

    /** Resets the state of this [SafetyCenterCtsListener]. */
    fun reset() {
        dataChannel.cancel()
        errorChannel.cancel()
    }
}
