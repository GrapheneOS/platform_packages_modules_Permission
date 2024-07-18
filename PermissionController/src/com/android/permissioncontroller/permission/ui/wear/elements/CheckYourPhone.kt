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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.android.permissioncontroller.R

private const val TOP_PADDING_SCREEN_PERCENTAGE = 0.1248f
private const val BOTTOM_PADDING_SCREEN_PERCENTAGE = 0.0624f
private const val SIDE_PADDING_SCREEN_PERCENTAGE = 0.052f
private const val TEXT_PADDING_SCREEN_PERCENTAGE = 0.0416f

enum class CheckYourPhoneState {
    InProgress,
    Success
}

/**
 * A screen to request the user to check their paired phone to proceed. It also allows a [message]
 * to be displayed.
 *
 * <img
 * src="https://media.githubusercontent.com/media/google/horologist/main/docs/auth-composables/check_your_phone_screen_code.png"
 * height="120" width="120"/>
 */
@Composable
fun CheckYourPhoneScreen(
    title: String,
    state: CheckYourPhoneState,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val configuration = LocalConfiguration.current

    val isLarge = configuration.isLargeScreen

    val topPadding = (configuration.screenHeightDp * TOP_PADDING_SCREEN_PERCENTAGE).dp
    val bottomPadding = (configuration.screenHeightDp * BOTTOM_PADDING_SCREEN_PERCENTAGE).dp
    val sidePadding = (configuration.screenHeightDp * SIDE_PADDING_SCREEN_PERCENTAGE).dp
    val textPadding =
        if (isLarge) (configuration.screenHeightDp * TEXT_PADDING_SCREEN_PERCENTAGE).dp else 0.dp

    Scaffold {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(
                        top = topPadding,
                        bottom = bottomPadding,
                        start = sidePadding,
                        end = sidePadding,
                    ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = textPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.W600),
                )

                if (message != null) {
                    Text(
                        text = message,
                        modifier =
                            Modifier.padding(top = 20.dp)
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            when (state) {
                CheckYourPhoneState.InProgress ->
                    RemoteConnectionProgressIndicator(
                        iconRes = R.drawable.ic_security_update_good,
                        Modifier.align(Alignment.CenterHorizontally)
                    )
                CheckYourPhoneState.Success ->
                    RemoteConnectionSuccess(
                        iconRes = R.drawable.ic_security_update_good,
                        Modifier.align(Alignment.CenterHorizontally)
                    )
            }
        }
    }
}

/** Whether the device is considered large screen for layout adjustment purposes. */
internal val Configuration.isLargeScreen: Boolean
    get() = screenHeightDp > 224
