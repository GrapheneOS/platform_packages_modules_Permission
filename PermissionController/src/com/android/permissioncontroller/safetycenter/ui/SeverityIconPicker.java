/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.safetycenter.ui;

import android.safetycenter.SafetyCenterEntry;
import android.util.Log;

import com.android.permissioncontroller.R;

class SeverityIconPicker {

    private static final String TAG = SeverityIconPicker.class.getSimpleName();

    static int selectIconResId(int severityLevel, int severityUnspecifiedIconType) {
        switch (severityLevel) {
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN:
                return R.drawable.ic_safety_null_state;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED:
                return selectSeverityUnspecifiedIconResId(severityUnspecifiedIconType);
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK:
                return R.drawable.ic_safety_info;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION:
                return R.drawable.ic_safety_recommendation;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING:
                return R.drawable.ic_safety_warn;
        }
        Log.e(TAG,
                String.format(
                        "Unexpected SafetyCenterEntry.EntrySeverityLevel: %s", severityLevel));
        return R.drawable.ic_safety_null_state;
    }

    private static int selectSeverityUnspecifiedIconResId(int severityUnspecifiedIconType) {
        switch (severityUnspecifiedIconType) {
            case SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON:
                return R.drawable.ic_safety_empty;
            case SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY:
                return R.drawable.ic_privacy;
            case SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION:
                return R.drawable.ic_safety_null_state;
        }
        Log.e(TAG,
                String.format(
                        "Unexpected SafetyCenterEntry.SeverityNoneIconType: %s",
                        severityUnspecifiedIconType));
        return R.drawable.ic_safety_null_state;
    }

}
