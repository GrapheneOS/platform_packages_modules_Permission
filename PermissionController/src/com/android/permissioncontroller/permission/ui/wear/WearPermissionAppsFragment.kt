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

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionFragment
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModelFactory

/**
 * This is a condensed version of
 * [com.android.permissioncontroller.permission.ui.handheld.PermissionAppsFragment],
 * tailored for Wear.
 *
 * Show and manage apps which request a single permission group.
 *
 * <p>Shows a list of apps which request at least on permission of this group.
 */
class WearPermissionAppsFragment : Fragment() {
    private val LOG_TAG = "PermissionAppsFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val permGroupName = arguments?.getString(Intent.EXTRA_PERMISSION_GROUP_NAME)
            ?: arguments?.getString(Intent.EXTRA_PERMISSION_NAME)
            ?: throw RuntimeException("Permission group name must not be null.")
        val sessionId: Long =
            arguments?.getLong(Constants.EXTRA_SESSION_ID) ?: Constants.INVALID_SESSION_ID
        val isStorage = permGroupName == Manifest.permission_group.STORAGE

        val activity = requireActivity()
        val factory = PermissionAppsViewModelFactory(
            activity.getApplication(),
            permGroupName,
            this,
            Bundle()
        )
        val viewModel =
            ViewModelProvider(this, factory).get(PermissionAppsViewModel::class.java)

        val onAppClick: (String, UserHandle, String) -> Unit = {
                packageName, user, category ->
            run {
                viewModel.navigateToAppPermission(
                    this,
                    packageName,
                    user,
                    AppPermissionFragment.createArgs(
                        packageName,
                        null,
                        permGroupName,
                        user,
                        this::class.java.name,
                        sessionId,
                        category
                    )
                )
            }
        }

        val onShowSystemClick: (Boolean) -> Unit = { showSystem ->
            run {
                viewModel.updateShowSystem(showSystem)
            }
        }

        val logPermissionAppsFragmentCreated:
                    (String, UserHandle, Long, Boolean, Boolean, Boolean) -> Unit =
            { packageName, user, viewId, isAllowed, isAllowedForeground, isDenied ->
                run {
                    viewModel.logPermissionAppsFragmentCreated(
                        packageName, user, viewId, isAllowed,
                        isAllowedForeground, isDenied, sessionId, activity.getApplication(),
                        permGroupName, LOG_TAG
                    )
                }
            }

        return ComposeView(requireContext()).apply {
            setContent {
                WearPermissionAppsScreen(
                    WearPermissionsAppHelper(
                        activity.getApplication(),
                        permGroupName,
                        viewModel,
                        isStorage,
                        onAppClick,
                        onShowSystemClick,
                        logPermissionAppsFragmentCreated
                    )
                )
            }
        }
    }
}