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

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.annotation.NonNull;
import android.safetycenter.SafetySourceData;
import android.safetycenter.config.SafetySource;
import android.util.Log;

import androidx.annotation.RequiresApi;

/** A helper class to facilitate working with {@link SafetySource}. */
@RequiresApi(TIRAMISU)
final class SafetySources {

    private static final String TAG = "SafetySources";

    /**
     * Returns whether a {@link SafetySource} is external, i.e. if {@link SafetySourceData} can be
     * provided for it.
     */
    static boolean isExternal(@NonNull SafetySource safetySource) {
        int safetySourceType = safetySource.getType();
        switch (safetySourceType) {
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
                return false;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return true;
        }
        Log.w(TAG, "Unexpected safety source type: " + safetySourceType);
        return false;
    }

    /** Returns whether a {@link SafetySource} supports managed profiles. */
    static boolean supportsManagedProfiles(@NonNull SafetySource safetySource) {
        int safetySourceProfile = safetySource.getProfile();
        switch (safetySourceProfile) {
            case SafetySource.PROFILE_PRIMARY:
            case SafetySource.PROFILE_NONE:
                return false;
            case SafetySource.PROFILE_ALL:
                return true;
        }
        Log.w(TAG, "Unexpected safety source profile: " + safetySourceProfile);
        return false;
    }

    /** Returns whether a {@link SafetySource} default entry should be hidden in the UI. */
    static boolean isDefaultEntryHidden(@NonNull SafetySource safetySource) {
        int safetySourceType = safetySource.getType();
        switch (safetySourceType) {
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return false;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
                return safetySource.getInitialDisplayState()
                        == SafetySource.INITIAL_DISPLAY_STATE_HIDDEN;
        }
        Log.w(TAG, "Unexpected safety source type: " + safetySourceType);
        return false;
    }

    /** Returns whether a {@link SafetySource} default entry should be disabled in the UI. */
    static boolean isDefaultEntryDisabled(@NonNull SafetySource safetySource) {
        int safetySourceType = safetySource.getType();
        switch (safetySourceType) {
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC:
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY:
                return false;
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC:
                return safetySource.getInitialDisplayState()
                        == SafetySource.INITIAL_DISPLAY_STATE_DISABLED;
        }
        Log.w(TAG, "Unexpected safety source type: " + safetySourceType);
        return false;
    }
}
