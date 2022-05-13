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
import android.safetycenter.SafetyCenterManager
import androidx.core.content.ContextCompat.getMainExecutor
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/* A SafetyCenterViewModel that talks to the real backing service for Safety Center. */
class LiveSafetyCenterViewModel(app: Application) : SafetyCenterViewModel(app) {

    override val safetyCenterLiveData: LiveData<SafetyCenterData>
        get() = _safetyCenterLiveData
    override val errorLiveData: LiveData<SafetyCenterErrorDetails>
        get() = _errorLiveData

    private val _safetyCenterLiveData = SafetyCenterLiveData()
    private val _errorLiveData = MutableLiveData<SafetyCenterErrorDetails>()

    private val safetyCenterManager = app.getSystemService(SafetyCenterManager::class.java)!!

    override fun dismissIssue(issue: SafetyCenterIssue) {
        safetyCenterManager.dismissSafetyCenterIssue(issue.id)
    }

    override fun executeIssueAction(issue: SafetyCenterIssue, action: SafetyCenterIssue.Action) {
        safetyCenterManager.executeSafetyCenterIssueAction(issue.id, action.id)
    }

    override fun rescan() {
        safetyCenterManager.refreshSafetySources(
            SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK)
    }

    override fun clearError() {
        _errorLiveData.value = null
    }

    override fun refresh() {
        safetyCenterManager.refreshSafetySources(SafetyCenterManager.REFRESH_REASON_PAGE_OPEN)
    }

    inner class SafetyCenterLiveData :
        MutableLiveData<SafetyCenterData>(), SafetyCenterManager.OnSafetyCenterDataChangedListener {

        override fun onActive() {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(
                getMainExecutor(app.applicationContext), this)
            super.onActive()
        }

        override fun onInactive() {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(this)
            super.onInactive()
        }

        override fun onSafetyCenterDataChanged(data: SafetyCenterData) {
            value = data
        }

        override fun onError(errorDetails: SafetyCenterErrorDetails) {
            _errorLiveData.value = errorDetails
        }
    }

    inner class AutoRefreshManager : DefaultLifecycleObserver {
        // TODO(b/222323674): We may need to do this in onResume to cover certain edge cases.
        // i.e. FMD changed from quick settings while SC is open
        override fun onStart(owner: LifecycleOwner) {
            refresh()
        }
    }
}

class LiveSafetyCenterViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return LiveSafetyCenterViewModel(app) as T
    }
}
