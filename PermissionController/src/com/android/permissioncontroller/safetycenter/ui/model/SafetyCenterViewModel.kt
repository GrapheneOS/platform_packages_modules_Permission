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

package com.android.permissioncontroller.safetycenter.ui.model

import android.app.Application
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterIssue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData

abstract class SafetyCenterViewModel(protected val app: Application) : AndroidViewModel(app) {

    abstract val safetyCenterLiveData: LiveData<SafetyCenterData>
    abstract val errorLiveData: LiveData<SafetyCenterErrorDetails>
    val autoRefreshManager = AutoRefreshManager()

    abstract fun dismissIssue(issue: SafetyCenterIssue)

    abstract fun executeIssueAction(issue: SafetyCenterIssue, action: SafetyCenterIssue.Action)

    abstract fun rescan()

    abstract fun clearError()

    protected abstract fun refresh()

    inner class AutoRefreshManager : DefaultLifecycleObserver {
        // TODO(b/222323674): We may need to do this in onResume to cover certain edge cases.
        // i.e. FMD changed from quick settings while SC is open
        override fun onStart(owner: LifecycleOwner) {
            refresh()
        }
    }
}
