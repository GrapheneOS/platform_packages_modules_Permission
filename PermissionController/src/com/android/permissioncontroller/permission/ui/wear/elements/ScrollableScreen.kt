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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.scrollAway
import com.android.permissioncontroller.permission.ui.wear.elements.rotaryinput.rotaryWithScroll
import com.android.permissioncontroller.permission.ui.wear.theme.WearPermissionTheme

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
    subtitle: CharSequence? = null,
    image: Any? = null,
    isLoading: Boolean = false,
    titleTestTag: String? = null,
    subtitleTestTag: String? = null,
    content: ScalingLazyListScope.() -> Unit,
) {
    var dismissed by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()
    val state = rememberSwipeToDismissBoxState()

    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissValue.Dismissed) {
            dismiss(activity)
            dismissed = true
            state.snapTo(SwipeToDismissValue.Default)
        }
    }

    // To support Swipe-dismiss effect,
    // add the view to SwipeToDismissBox if the screen is not on the top fragment.
    if (getBackStackEntryCount(activity) > 0) {
        SwipeToDismissBox(state = state) { isBackground ->
            Scaffold(
                showTimeText,
                title,
                subtitle,
                image,
                isLoading = isLoading || isBackground || dismissed,
                content,
                titleTestTag,
                subtitleTestTag
            )
        }
    } else {
        Scaffold(
            showTimeText,
            title,
            subtitle,
            image,
            isLoading,
            content,
            titleTestTag,
            subtitleTestTag
        )
    }
}

@Composable
internal fun Scaffold(
    showTimeText: Boolean,
    title: String?,
    subtitle: CharSequence?,
    image: Any?,
    isLoading: Boolean,
    content: ScalingLazyListScope.() -> Unit,
    titleTestTag: String? = null,
    subtitleTestTag: String? = null,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val scrollContentHorizontalPadding = (screenWidth * 0.052).dp
    val titleHorizontalPadding = (screenWidth * 0.0884).dp
    val subtitleHorizontalPadding = (screenWidth * 0.0416).dp
    val scrollContentTopPadding = (screenHeight * 0.1456).dp
    val scrollContentBottomPadding = (screenHeight * 0.3636).dp
    val titleBottomPadding =
        if (subtitle == null) {
            8.dp
        } else {
            4.dp
        }
    val subtitleBottomPadding = 8.dp
    val timeTextTopPadding =
        if (showTimeText) {
            1.dp
        } else {
            0.dp
        }
    val titlePaddingValues =
        PaddingValues(
            start = titleHorizontalPadding,
            top = 4.dp,
            bottom = titleBottomPadding,
            end = titleHorizontalPadding
        )
    val subTitlePaddingValues =
        PaddingValues(
            start = subtitleHorizontalPadding,
            top = 4.dp,
            bottom = subtitleBottomPadding,
            end = subtitleHorizontalPadding
        )
    val initialCenterIndex = 0
    val centerHeightDp = Dp(LocalConfiguration.current.screenHeightDp / 2.0f)
    // We are adding TimeText's padding to create a smooth scrolling
    val initialCenterItemScrollOffset = scrollContentTopPadding + timeTextTopPadding
    val scrollAwayOffset = centerHeightDp - initialCenterItemScrollOffset
    val focusRequester = remember { FocusRequester() }
    val listState = remember { ScalingLazyListState(initialCenterItemIndex = initialCenterIndex) }
    LaunchedEffect(title) {
        listState.animateScrollToItem(index = 0) // Scroll to the top when triggerValue changes
    }
    WearPermissionTheme {
        Scaffold(
            // TODO: Use a rotary modifier from Wear Compose once Wear Compose 1.4 is landed.
            // (b/325560444)
            modifier =
                Modifier.rotaryWithScroll(
                    scrollableState = listState,
                    focusRequester = focusRequester
                ),
            timeText = {
                if (showTimeText && !isLoading) {
                    TimeText(
                        modifier =
                            Modifier.scrollAway(listState, initialCenterIndex, scrollAwayOffset)
                                .padding(top = timeTextTopPadding),
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
                        modifier = Modifier.fillMaxWidth(),
                        state = listState,
                        // Set autoCentering to null to avoid adding extra padding based on the
                        // content.
                        autoCentering = null,
                        contentPadding =
                            PaddingValues(
                                start = scrollContentHorizontalPadding,
                                end = scrollContentHorizontalPadding,
                                top = scrollContentTopPadding,
                                bottom = scrollContentBottomPadding
                            )
                    ) {
                        image?.let {
                            val imageModifier = Modifier.size(24.dp)
                            when (image) {
                                is Int ->
                                    item {
                                        Image(
                                            painter = painterResource(id = image),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = imageModifier
                                        )
                                    }
                                is Drawable ->
                                    item {
                                        Image(
                                            painter = rememberDrawablePainter(image),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = imageModifier
                                        )
                                    }
                                else -> {}
                            }
                        }
                        if (title != null) {
                            item {
                                var modifier: Modifier = Modifier
                                if (titleTestTag != null) {
                                    modifier = modifier.testTag(titleTestTag)
                                }
                                ListHeader(modifier = Modifier.padding(titlePaddingValues)) {
                                    Text(
                                        text = title,
                                        textAlign = TextAlign.Center,
                                        modifier = modifier
                                    )
                                }
                            }
                        }
                        if (subtitle != null) {
                            item {
                                var modifier: Modifier =
                                    Modifier.align(Alignment.Center).padding(subTitlePaddingValues)
                                if (subtitleTestTag != null) {
                                    modifier = modifier.testTag(subtitleTestTag)
                                }
                                AnnotatedText(
                                    text = subtitle,
                                    style =
                                        MaterialTheme.typography.body2.copy(
                                            color = MaterialTheme.colors.onSurfaceVariant
                                        ),
                                    modifier = modifier,
                                    shouldCapitalize = true
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

internal fun dismiss(activity: Activity) {
    if (activity is FragmentActivity) {
        if (!activity.supportFragmentManager.popBackStackImmediate()) {
            activity.finish()
        }
    } else {
        activity.finish()
    }
}

internal fun getBackStackEntryCount(activity: Activity): Int {
    return if (activity is FragmentActivity) {
        activity.supportFragmentManager.primaryNavigationFragment
            ?.childFragmentManager
            ?.backStackEntryCount
            ?: 0
    } else {
        0
    }
}

internal fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("The screen should be called in the context of an Activity")
}
