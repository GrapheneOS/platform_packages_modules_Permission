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

package com.android.permissioncontroller.permission.ui.wear.elements

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.scrollAway
import kotlinx.coroutines.launch

/**
 * Screen that contains a list of items defined using the [content] parameter, adds the time text
 * (if [showTimeText] is true), the tile (if [title] is not null), the vignette and the position
 * indicator. It also manages the scaling animation and allows the user to scroll the content using
 * the crown.
 */
@Composable
fun ScrollableScreen(
    showTimeText: Boolean = true,
    title: String? = null,
    subtitle: String? = null,
    image: Any? = null,
    isLoading: Boolean = false,
    content: ScalingLazyListScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val listState = remember { ScalingLazyListState(initialCenterItemIndex = 0) }
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme {
        Scaffold(
            modifier =
                Modifier.onRotaryScrollEvent {
                        coroutineScope.launch { listState.scrollBy(it.verticalScrollPixels) }
                        true
                    }
                    .focusRequester(focusRequester)
                    .focusable(),
            timeText = {
                if (showTimeText && !isLoading) {
                    TimeText(
                        modifier = Modifier.scrollAway(listState),
                        contentPadding = PaddingValues(15.dp)
                    )
                }
            },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    ScalingLazyColumn(
                        state = listState,
                        // Set autoCentering to null to avoid adding extra padding based on the
                        // content.
                        autoCentering = null,
                        contentPadding =
                            PaddingValues(start = 10.dp, end = 10.dp, top = 32.dp, bottom = 70.dp)
                    ) {
                        image?.let {
                            when (image) {
                                is Int ->
                                    item {
                                        Image(
                                            painter = painterResource(id = image),
                                            contentDescription = null
                                        )
                                    }
                                is Drawable ->
                                    item {
                                        Image(
                                            painter = rememberDrawablePainter(image),
                                            contentDescription = null
                                        )
                                    }
                                else -> {}
                            }
                        }
                        if (title != null) {
                            item { ListHeader { Text(text = title, textAlign = TextAlign.Center) } }
                        }
                        if (subtitle != null) {
                            item {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.body2,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        content()
                    }
                    RequestFocusOnResume(focusRequester = focusRequester)
                }
            }
        }
    }
}

@Composable
private fun RequestFocusOnResume(focusRequester: FocusRequester) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(state = Lifecycle.State.RESUMED) {
            focusRequester.requestFocus()
        }
    }
}
