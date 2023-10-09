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

package com.android.permissioncontroller.permission.ui.wear

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.model.AppPermissions
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModel
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModelFactory
import com.android.permissioncontroller.permission.ui.wear.model.AppPermissionGroupsRevokeDialogViewModel
import com.android.permissioncontroller.permission.ui.wear.model.AppPermissionGroupsRevokeDialogViewModelFactory

class WearAppPermissionGroupsFragment : Fragment() {
    private lateinit var helper: WearAppPermissionGroupsHelper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val packageName = arguments?.getString(Intent.EXTRA_PACKAGE_NAME) ?: ""
        val user =
            arguments?.let {
                BundleCompat.getParcelable(it, Intent.EXTRA_USER, UserHandle::class.java)!!
            }
                ?: UserHandle.SYSTEM

        val activity: Activity = requireActivity()
        val packageManager = activity.packageManager

        val packageInfo: PackageInfo? =
            try {
                packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.i(LOG_TAG, "No package:" + activity.getCallingPackage(), e)
                null
            }

        if (packageInfo == null) {
            Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show()
            activity.finish()
            return null
        }
        val sessionId = arguments?.getLong(EXTRA_SESSION_ID, 0) ?: 0
        val appPermissions = AppPermissions(activity, packageInfo, true, { activity.finish() })
        val factory = AppPermissionGroupsViewModelFactory(packageName, user, sessionId)
        val viewModel =
            ViewModelProvider(this, factory).get(AppPermissionGroupsViewModel::class.java)
        val revokeDialogViewModel =
            ViewModelProvider(this, AppPermissionGroupsRevokeDialogViewModelFactory())
                .get(AppPermissionGroupsRevokeDialogViewModel::class.java)
        helper =
            WearAppPermissionGroupsHelper(
                context = requireContext(),
                fragment = this,
                user = user,
                sessionId = sessionId,
                appPermissions = appPermissions,
                viewModel = viewModel,
                revokeDialogViewModel = revokeDialogViewModel
            )

        return ComposeView(activity).apply { setContent { WearAppPermissionGroupsScreen(helper) } }
    }

    override fun onPause() {
        super.onPause()
        helper.logAndClearToggledGroups()
    }

    companion object {
        const val LOG_TAG = "WearAppPermissionGroups"
    }
}
