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

package com.android.permissioncontroller.permission.ui.wear.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme

@Composable
fun RemoteConnectionProgressIndicator(iconRes: Int, modifier: Modifier) {
    val indicatorPadding = 8.dp
    val iconSize = 48.dp
    val progressBarStrokeWidth = 4.dp
    Box(
        modifier = modifier.size(iconSize).clip(CircleShape),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(iconSize - progressBarStrokeWidth + indicatorPadding),
            strokeWidth = progressBarStrokeWidth,
        )
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier =
                Modifier.align(Alignment.Center)
                    .size(iconSize - indicatorPadding - 8.dp)
                    .clip(CircleShape),
        )
    }
}

@Composable
fun RemoteConnectionSuccess(iconRes: Int, modifier: Modifier) {
    val indicatorPadding = 8.dp
    val iconSize = 48.dp
    val backgroundColor = MaterialTheme.colors.onSurface
    val contentColor = MaterialTheme.colors.surface
    Box(
        modifier = modifier.size(iconSize).clip(CircleShape).background(backgroundColor),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier =
                Modifier.align(Alignment.Center)
                    .size(iconSize - indicatorPadding - 8.dp)
                    .clip(CircleShape),
        )
    }
}
