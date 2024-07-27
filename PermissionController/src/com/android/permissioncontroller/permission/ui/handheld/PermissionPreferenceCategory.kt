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

package com.android.permissioncontroller.permission.ui.handheld

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.R

// TODO b/327285817 add chunking/dividers oems can customize
open class PermissionPreferenceCategory : PreferenceCategory {
    constructor(c: Context) : super(c)

    constructor(c: Context, a: AttributeSet) : super(c, a)

    init {
        if (SdkLevel.isAtLeastV()) {
            layoutResource = R.layout.permission_preference_category
        }
    }
}
