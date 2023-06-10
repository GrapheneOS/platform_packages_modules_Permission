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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * A slot based composable for creating a list header item.
 */
@Composable
public fun ListHeader(labelStringRes: Int) {
    ListHeader(stringResource(labelStringRes))
}

@Composable
public fun ListHeader(labelString: String) {
    Text(
        text = labelString,
        style = MaterialTheme.typography.caption1,
        modifier =
        Modifier.padding(top = 16.dp, bottom = 12.dp, start = 8.dp, end = 8.dp).fillMaxWidth(),
        textAlign = TextAlign.Start
    )
}
