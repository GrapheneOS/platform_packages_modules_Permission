/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.permissioncontroller.permission.ui.auto

import android.content.Intent
import android.graphics.drawable.Drawable
import android.icu.text.MessageFormat
import android.os.Bundle
import android.os.UserHandle
import android.text.BidiFormatter
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import com.android.car.ui.preference.CarUiPreference
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.R
import com.android.permissioncontroller.auto.AutoSettingsFrameFragment
import com.android.permissioncontroller.permission.data.PermissionDecision
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionDecisionsViewModel
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionDecisionsViewModelFactory
import com.android.permissioncontroller.permission.utils.KotlinUtils.getBadgedPackageIcon
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPackageLabel
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Shows recent permission decisions. */
class AutoReviewPermissionDecisionsFragment : AutoSettingsFrameFragment() {

    companion object {
        private const val LOG_TAG = "AutoReviewPermissionDecisionsFragment"

        /**
         * Creates a new instance of [AutoReviewPermissionDecisionsFragment].
         */
        fun newInstance(
            sessionId: Long,
            userHandle: UserHandle
        ): AutoReviewPermissionDecisionsFragment {
            return AutoReviewPermissionDecisionsFragment().apply {
                arguments = Bundle().apply {
                    putLong(Constants.EXTRA_SESSION_ID, sessionId)
                    putParcelable(Intent.EXTRA_USER, userHandle)
                }
            }
        }
    }

    private lateinit var user: UserHandle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userArg = arguments?.getParcelable<UserHandle>(Intent.EXTRA_USER)
        if (userArg == null) {
            Log.e(LOG_TAG, "Missing argument ${Intent.EXTRA_USER}")
            activity?.finish()
            return
        }
        user = userArg
        val factory = ReviewPermissionDecisionsViewModelFactory(activity?.getApplication()!!, user)
        val viewModel = ViewModelProvider(this,
            factory)[ReviewPermissionDecisionsViewModel::class.java]
        viewModel.recentPermissionDecisionsLiveData.observe(this) { recentDecisions ->
            onRecentDecisionsChanged(recentDecisions)
        }
        headerLabel = getString(R.string.review_permission_decisions)
    }

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(context)
    }

    private fun onRecentDecisionsChanged(recentDecisions: List<PermissionDecision>) {
        preferenceScreen.removeAll()
        for (recentDecision in recentDecisions) {
            val decisionPreference = CarUiPreference(context).apply {
                icon = getAppIcon(recentDecision.packageName)
                title = createPreferenceTitle(recentDecision)
                summary = createSummaryText(recentDecision)
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    createManageAppPermissionIntent(recentDecision).also {
                        startActivity(it)
                    }
                    false
                }
            }
            preferenceScreen.addPreference(decisionPreference)
        }
    }

    private fun getAppIcon(packageName: String): Drawable? {
        return getBadgedPackageIcon(activity?.getApplication()!!, packageName, user)
    }

    private fun createPreferenceTitle(permissionDecision: PermissionDecision): String {
        val packageLabel = BidiFormatter.getInstance().unicodeWrap(
            getPackageLabel(activity?.getApplication()!!, permissionDecision.packageName, user))
        val permissionGroupLabel = getPermGroupLabel(requireContext(),
            permissionDecision.permissionGroupName).toString()
        return if (permissionDecision.isGranted) {
            getString(R.string.granted_permission_decision, packageLabel,
                permissionGroupLabel.lowercase())
        } else {
            getString(R.string.denied_permission_decision, packageLabel,
                permissionGroupLabel.lowercase())
        }
    }

    private fun createManageAppPermissionIntent(permissionDecision: PermissionDecision): Intent {
        return Intent(Intent.ACTION_MANAGE_APP_PERMISSION).apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, permissionDecision.packageName)
            putExtra(Intent.EXTRA_PERMISSION_NAME, permissionDecision.permissionGroupName)
            putExtra(Intent.EXTRA_USER, user)
            putExtra(ManagePermissionsActivity.EXTRA_CALLER_NAME,
                AutoReviewPermissionDecisionsFragment::class.java.name)
        }
    }

    private fun createSummaryText(permissionDecision: PermissionDecision): String {
        val diff = System.currentTimeMillis() - permissionDecision.decisionTime
        val daysAgo = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS).toInt()
        return MessageFormat(resources.getString(R.string.days_ago), Locale.getDefault()).let {
            it.format(mapOf("count" to daysAgo))
        }
    }
}