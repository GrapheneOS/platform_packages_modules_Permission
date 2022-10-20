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

package com.android.permission.access.appop

import android.app.AppOpsManager
import com.android.permission.access.SchemePolicy
import com.android.permission.access.util.* // ktlint-disable no-wildcard-imports

abstract class BaseAppOpPolicy : SchemePolicy() {
    protected fun getAppOpMode(modes: IndexedMap<String, Int>?, appOpName: String): Int =
        modes?.get(appOpName) ?: opToDefaultMode(appOpName)

    protected fun setAppOpMode(
        modes: IndexedMap<String, Int>,
        appOpName: String,
        decision: Int
    ) {
        if (decision == opToDefaultMode(appOpName)) {
            modes -= appOpName
        } else {
            modes[appOpName] = decision
        }
    }

    // TODO need to check that [AppOpsManager.getSystemAlertWindowDefault] works; likely no issue
    //  since running in system process.
    private fun opToDefaultMode(appOpName: String) = AppOpsManager.opToDefaultMode(appOpName)
}
