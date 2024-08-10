/*
 * Copyright (C) 2024 The Android Open Source Project
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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.SwipeToDismissBox
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.Scaffold
import com.android.permissioncontroller.permission.ui.wear.model.LocationProviderInterceptDialogArgs

@Composable
fun LocationProviderDialogScreen(args: LocationProviderInterceptDialogArgs?) {
    args?.apply {
        val state = rememberSwipeToDismissBoxState()
        LaunchedEffect(state.currentValue) {
            // If the swipe is complete
            if (state.currentValue == SwipeToDismissValue.Dismissed) {
                onOkButtonClick()
            }
        }
        SwipeToDismissBox(state = state) { isBackground ->
            Scaffold(
                showTimeText = false,
                image = iconId,
                title = stringResource(titleId),
                subtitle = message,
                isLoading = isBackground,
                content = {
                    item {
                        Chip(
                            label = stringResource(locationSettingsId),
                            onClick = onLocationSettingsClick,
                            modifier = Modifier.fillMaxWidth(),
                            textColor = MaterialTheme.colors.surface,
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                    item {
                        Chip(
                            label = stringResource(okButtonTitleId),
                            onClick = onOkButtonClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            )
        }
    }
}
