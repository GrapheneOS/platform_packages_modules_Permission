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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity;
import com.android.permissioncontroller.permission.utils.KotlinUtils;

import java.util.List;

/**
 * Preference for the top level privacy hub page
 */
public class PermissionUsageV2ControlPreference extends Preference {
    private static final List<String> SENSOR_DATA_PERMISSIONS = List.of(
            Manifest.permission_group.LOCATION,
            Manifest.permission_group.CAMERA,
            Manifest.permission_group.MICROPHONE
    );

    private final Context mContext;
    private final String mGroupName;
    private final int mCount;
    private final boolean mShowSystem;

    public PermissionUsageV2ControlPreference(@NonNull Context context, @NonNull String groupName,
            int count, boolean showSystem) {
        super(context);
        mContext = context;
        mGroupName = groupName;
        mCount = count;
        mShowSystem = showSystem;

        CharSequence permGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(mContext, mGroupName);
        setTitle(permGroupLabel);
        setIcon(KotlinUtils.INSTANCE.getPermGroupIcon(mContext, mGroupName));
        setSummary(mContext.getResources().getQuantityString(
                R.plurals.permission_usage_preference_label, mCount, mCount));

        if (mCount == 0) {
            this.setEnabled(false);
            setSummary(R.string.permission_usage_preference_summary_not_used);
        } else if (SENSOR_DATA_PERMISSIONS.contains(groupName)) {
            setOnPreferenceClickListener((preference) -> {
                Intent intent = new Intent(Intent.ACTION_REVIEW_PERMISSION_HISTORY);
                intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mGroupName);
                intent.putExtra(ManagePermissionsActivity.EXTRA_SHOW_SYSTEM, mShowSystem);

                mContext.startActivity(intent);
                return true;
            });
        } else {
            setOnPreferenceClickListener((preference) -> {
                Intent intent = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS);
                intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mGroupName);

                mContext.startActivity(intent);
                return true;
            });
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);

        if (SdkLevel.isAtLeastS()) {
            TextView titleView = (TextView) view.findViewById(android.R.id.title);
            TypedArray ta = mContext.obtainStyledAttributes(
                    new int[]{android.R.attr.textAppearanceListItem});
            int resId = ta.getResourceId(0, 0);
            ta.recycle();
            titleView.setTextAppearance(resId);
        }
    }
}
