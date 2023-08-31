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

package com.android.permissioncontroller.role.ui.wear

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.wear.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.wear.elements.Chip
import com.android.permissioncontroller.permission.ui.wear.elements.DrawablePainter
import com.android.permissioncontroller.permission.ui.wear.elements.EmptyPainter
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.role.ui.RoleItem

@Composable
fun WearDefaultAppListScreen(
        defaultAppsLiveData: LiveData<List<RoleItem>>,
        onClickChip: (RoleItem) -> Unit
) {
    val context: Context = LocalContext.current
    val packageManager: PackageManager = context.packageManager
    val defaultAppsState = defaultAppsLiveData.observeAsState(emptyList())
    val defaultApps: List<RoleItem> by remember {
        derivedStateOf {
            defaultAppsState.value
        }
    }

    ScrollableScreen(title = stringResource(R.string.default_apps)) {
        if (defaultApps.isEmpty()) {
            item {
                Text(stringResource(R.string.no_default_apps))
            }
            return@ScrollableScreen
        }
        defaultApps.forEach {
            item {
                RoleChip(
                        it,
                        packageManager
                ) { onClickChip(it) }
            }
        }
    }
}

@Composable
fun RoleChip(
        roleItem: RoleItem,
        packageManager: PackageManager,
        onClick: () -> Unit
) {
    val roleHolder: RoleHolder = if (roleItem.holderApplicationInfos.isEmpty()) {
        RoleHolder(stringResource(R.string.default_app_none), EmptyPainter)
    } else {
        val appInfo = roleItem.holderApplicationInfos[0]
        RoleHolder(
                Utils.getAppLabel(appInfo, LocalContext.current),
                DrawablePainter(appInfo.loadIcon(packageManager))
        )
    }
    Chip(
            label = stringResource(roleItem.role.shortLabelResource),
            secondaryLabel = roleHolder.appLabel,
            icon = {
                Image(
                        painter = roleHolder.icon,
                        contentDescription = "",
                        modifier = Modifier.size(width = 30.dp, height = 30.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
    )
}

data class RoleHolder(val appLabel: String, val icon: Painter)