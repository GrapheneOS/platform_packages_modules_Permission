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

package com.android.permissioncontroller.permission.ui.wear

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.SwipeToDismissBox
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.wear.elements.AlertDialog
import com.android.permissioncontroller.permission.ui.wear.elements.CheckYourPhoneScreen
import com.android.permissioncontroller.permission.ui.wear.elements.CheckYourPhoneState
import com.android.permissioncontroller.permission.ui.wear.elements.CheckYourPhoneState.InProgress
import com.android.permissioncontroller.permission.ui.wear.elements.CheckYourPhoneState.Success
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.ui.wear.elements.dismiss
import com.android.permissioncontroller.permission.ui.wear.elements.findActivity
import com.android.permissioncontroller.permission.ui.wear.model.WearEnhancedConfirmationViewModel
import com.android.permissioncontroller.permission.ui.wear.model.WearEnhancedConfirmationViewModel.ScreenState

@Composable
fun WearEnhancedConfirmationScreen(
    viewModel: WearEnhancedConfirmationViewModel,
    title: String?,
    message: CharSequence?,
) {
    var dismissed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val ecmScreenState = remember { viewModel.screenState }
    val activity = context.findActivity()

    val state = rememberSwipeToDismissBoxState()

    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissValue.Dismissed) {
            dismiss(activity)
            dismissed = true
            state.snapTo(SwipeToDismissValue.Default)
        }
    }

    fun dismiss(activity: Activity) {
        if (activity is FragmentActivity) {
            if (!activity.supportFragmentManager.popBackStackImmediate()) {
                activity.finish()
            }
        } else {
            activity.finish()
        }
    }

    @Composable
    fun ShowECMRestrictionDialog() =
        ScrollableScreen(
            showTimeText = false,
            title = title,
            subtitle = message,
            image = R.drawable.ic_android_security_privacy,
            content = {
                item {
                    Chip(
                        label = stringResource(R.string.enhanced_confirmation_dialog_ok),
                        onClick = { dismiss(activity) },
                        modifier = Modifier.fillMaxWidth(),
                        textColor = MaterialTheme.colors.surface,
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
                item {
                    Chip(
                        label = stringResource(R.string.enhanced_confirmation_dialog_learn_more),
                        onClick = { viewModel.openUriOnPhone(context) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        )

    @Composable
    fun ShowCheckYourPhoneDialog(state: CheckYourPhoneState) =
        CheckYourPhoneScreen(
            title = stringResource(id = R.string.wear_check_your_phone_title),
            state = state
        )

    @Composable
    fun ShowRemoteConnectionErrorDialog() =
        AlertDialog(
            title = stringResource(R.string.wear_phone_connection_error),
            message = stringResource(R.string.wear_phone_connection_should_retry),
            iconRes = R.drawable.ic_error,
            showDialog = true,
            okButtonIcon = R.drawable.ic_refresh,
            onOKButtonClick = { viewModel.openUriOnPhone(context) },
            onCancelButtonClick = { dismiss(activity) },
            scalingLazyListState = ScalingLazyListState(1)
        )

    SwipeToDismissBox(state = state) { isBackground ->
        if (isBackground || dismissed) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            when (ecmScreenState.value) {
                ScreenState.SHOW_RESTRICTION_DIALOG -> ShowECMRestrictionDialog()
                ScreenState.SHOW_CONNECTION_IN_PROGRESS -> ShowCheckYourPhoneDialog(InProgress)
                ScreenState.SHOW_CONNECTION_ERROR -> ShowRemoteConnectionErrorDialog()
                ScreenState.SHOW_CONNECTION_SUCCESS -> ShowCheckYourPhoneDialog(Success)
            }
        }
    }
}
