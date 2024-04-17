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

package com.android.permissioncontroller.permission.ui.auto;

import android.content.Context;

import com.android.car.ui.preference.CarUiPreference;
import com.android.permissioncontroller.R;

/**
 * A Preference representing a banner message represented as a CardView
 */
public class AutoCardViewPreference extends CarUiPreference {

    public AutoCardViewPreference(Context context) {
        super(context);
        this.setLayoutResource(R.layout.car_warning_banner_preference_card);
    }
}
