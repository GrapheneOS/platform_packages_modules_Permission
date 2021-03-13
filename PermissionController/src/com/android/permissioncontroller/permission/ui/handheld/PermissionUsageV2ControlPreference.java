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

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * Preference for the top level privacy hub page
 */
public class PermissionUsageV2ControlPreference extends Preference {
    private final @NonNull Context mContext;
    private @Nullable CharSequence mGroupName;

    public PermissionUsageV2ControlPreference(@NonNull Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        setOnPreferenceClickListener((preference) -> {
            Intent intent = new Intent(Intent.ACTION_REVIEW_PERMISSION_HISTORY);
            intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mGroupName);

            mContext.startActivity(intent);
            return true;
        });
    }

    public void setGroup(CharSequence groupName) {
        this.mGroupName = groupName;
    }

    public CharSequence getGroupName() {
        return this.mGroupName;
    }
}
