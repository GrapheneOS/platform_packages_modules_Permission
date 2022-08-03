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
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.os.Build
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterErrorDetails
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterManager
import android.safetycenter.config.SafetySource
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getMainExecutor
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.safetycenter.ui.InteractionLogger
import com.android.permissioncontroller.safetycenter.ui.NavigationSource
import java.util.concurrent.atomic.AtomicBoolean

/* A SafetyCenterViewModel that talks to the real backing service for Safety Center. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiveSafetyCenterViewModel(app: Application) : SafetyCenterViewModel(app) {

    private val TAG: String = LiveSafetyCenterViewModel::class.java.simpleName
    override val safetyCenterUiLiveData: LiveData<SafetyCenterUiData> by this::_safetyCenterLiveData
    override val errorLiveData: LiveData<SafetyCenterErrorDetails> by this::_errorLiveData

    private val _safetyCenterLiveData = SafetyCenterLiveData()
    private val _errorLiveData = MutableLiveData<SafetyCenterErrorDetails>()

    override val interactionLogger: InteractionLogger by lazy {
        fun isLoggable(safetySource: SafetySource): Boolean {
            return try {
                safetySource.isLoggingAllowed
            } catch (ex: UnsupportedOperationException) {
                // isLoggingAllowed will throw if you call it on a static source :(
                // Default to logging all sources that don't support this config value.
                true
            }
        }

        // Fetching the config to build this set of source IDs requires IPC, so we do this
        // initialization lazily.
        val safetyCenterConfig = safetyCenterManager.safetyCenterConfig

        InteractionLogger(
            if (safetyCenterConfig != null) {
                safetyCenterConfig.safetySourcesGroups
                    .asSequence()
                    .flatMap { it.safetySources }
                    .filterNot(::isLoggable)
                    .map { it.id }
                    .toSet()
            } else {
                setOf()
            })
    }

    private var changingConfigurations = AtomicBoolean(false)

    private val safetyCenterManager = app.getSystemService(SafetyCenterManager::class.java)!!

    override fun dismissIssue(issue: SafetyCenterIssue) {
        safetyCenterManager.dismissSafetyCenterIssue(issue.id)
    }

    override fun executeIssueAction(issue: SafetyCenterIssue, action: SafetyCenterIssue.Action) {
        safetyCenterManager.executeSafetyCenterIssueAction(issue.id, action.id)
    }

    override fun markIssueResolvedUiCompleted(issueId: IssueId) {
        _safetyCenterLiveData.markIssueResolvedUiCompleted(issueId)
    }

    override fun rescan() {
        safetyCenterManager.refreshSafetySources(
            SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK)
    }

    override fun clearError() {
        _errorLiveData.value = null
    }

    override fun navigateToSafetyCenter(fragment: Fragment, navigationSource: NavigationSource?) {
        val intent = Intent(ACTION_SAFETY_CENTER)

        if (navigationSource != null) {
            navigationSource.addToIntent(intent)
        }

        fragment.startActivity(intent)
    }

    override fun pageOpen() {
        if (!changingConfigurations.getAndSet(false)) {
            // Refresh unless this is a config change
            safetyCenterManager.refreshSafetySources(SafetyCenterManager.REFRESH_REASON_PAGE_OPEN)
        }
    }

    override fun changingConfigurations() {
        changingConfigurations.set(true)
    }

    inner class SafetyCenterLiveData :
        MutableLiveData<SafetyCenterUiData>(),
        SafetyCenterManager.OnSafetyCenterDataChangedListener {

        // Managing the data queue isn't designed to support multithreading. Any methods that
        // manipulate it, or the inFlight or resolved issues lists should only be called on the
        // main thread, and are marked accordingly.
        private val safetyCenterDataQueue = ArrayDeque<SafetyCenterData>()
        private var currentInFlightIssues = mapOf<IssueId, ActionId>()
        private val currentResolvedIssues = mutableMapOf<IssueId, ActionId>()

        override fun onActive() {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(
                    getMainExecutor(app.applicationContext), this)
            super.onActive()
        }

        override fun onInactive() {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(this)

            // Remove all the tracked state and start from scratch when active again.
            currentInFlightIssues = mapOf()
            currentResolvedIssues.clear()
            safetyCenterDataQueue.clear()
            super.onInactive()
        }

        @MainThread
        override fun onSafetyCenterDataChanged(data: SafetyCenterData) {
            safetyCenterDataQueue.addLast(data)
            maybeProcessDataToNextResolvedIssues()
        }

        override fun onError(errorDetails: SafetyCenterErrorDetails) {
            _errorLiveData.value = errorDetails
        }

        @MainThread
        private fun maybeProcessDataToNextResolvedIssues() {
            // Only process data updates while we aren't waiting for issue resolution animations
            // to complete.
            if (currentResolvedIssues.isNotEmpty()) {
                Log.d(
                        TAG,
                        "Received SafetyCenterData while issue resolution animations" +
                                " occurring. Will update UI with new data soon.")
                return
            }

            while (safetyCenterDataQueue.isNotEmpty() && currentResolvedIssues.isEmpty()) {
                val nextSafetyCenterData = safetyCenterDataQueue.first()

                // Calculate newly resolved issues by diffing the tracked in-flight issues and the
                // current update. Resolved issues are formerly in-flight issues that no longer
                // appear in a subsequent SafetyCenterData update.
                val nextResolvedIssues: Map<IssueId, ActionId> =
                        determineResolvedIssues(nextSafetyCenterData, currentInFlightIssues)

                // Save the set of in-flight issues to diff against the next data update.
                currentInFlightIssues = nextSafetyCenterData.getInFlightIssues()

                if (nextResolvedIssues.isEmpty()) {
                    sendNextData()
                } else {
                    currentResolvedIssues.putAll(nextResolvedIssues)
                    sendResolvedIssuesAndCurrentData()
                }
            }
        }

        private fun determineResolvedIssues(
            incomingData: SafetyCenterData,
            inFlightIssues: Map<IssueId, ActionId>
        ): Map<IssueId, ActionId> {
            // Any previously in-flight issue that does not appear in the incoming SafetyCenterData
            // is considered resolved.
            val issueIdSet: Set<IssueId> = incomingData.issues.map { issue -> issue.id }.toSet()
            return inFlightIssues.filterNot { issue -> issueIdSet.contains(issue.key) }
        }

        private fun sendNextData() {
            value = SafetyCenterUiData(safetyCenterDataQueue.removeFirst())
        }

        private fun sendResolvedIssuesAndCurrentData() {
            val currentData = value?.safetyCenterData
            if (currentData == null || currentResolvedIssues.isEmpty()) {
                // There can only be resolved issues after receiving data with in-flight issues,
                // so we should always have already sent data here.
                throw IllegalArgumentException("No current data or no resolved issues")
            }

            // The current SafetyCenterData still contains the resolved SafetyCenterIssue objects.
            // Send it with the resolved IDs so the UI can generate the correct preferences and
            // trigger the right animations for issue resolution.
            value = SafetyCenterUiData(currentData, currentResolvedIssues)
        }

        @MainThread
        fun markIssueResolvedUiCompleted(issueId: IssueId) {
            currentResolvedIssues.remove(issueId)
            maybeProcessDataToNextResolvedIssues()
        }
    }
}

private fun SafetyCenterData.getInFlightIssues(): Map<IssueId, ActionId> =
    issues
        .map { issue ->
            issue.actions
                .filter { it.isInFlight }
                .map { issue.id to it.id }
        }
        .flatten()
        .toMap()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiveSafetyCenterViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return LiveSafetyCenterViewModel(app) as T
    }
}
