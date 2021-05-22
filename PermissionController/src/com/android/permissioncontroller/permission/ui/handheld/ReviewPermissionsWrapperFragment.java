/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld;

import android.content.pm.PackageInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Wrapper over ReviewPermissionsFragment
 */
public class ReviewPermissionsWrapperFragment extends PermissionsCollapsingToolbarBaseFragment {

    @NonNull
    @Override
    public PreferenceFragmentCompat createPreferenceFragment() {
        return new ReviewPermissionsFragment();
    }

    /**
     * @return a new fragment
     */
    public static ReviewPermissionsWrapperFragment newInstance(PackageInfo packageInfo) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(ReviewPermissionsFragment.EXTRA_PACKAGE_INFO,
                packageInfo);
        ReviewPermissionsWrapperFragment instance = new ReviewPermissionsWrapperFragment();
        instance.setArguments(arguments);
        instance.setRetainInstance(true);
        return instance;
    }
}
