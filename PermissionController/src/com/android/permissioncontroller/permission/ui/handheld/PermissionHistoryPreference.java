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
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/**
 * Preference for the permission history page
 */
public class PermissionHistoryPreference extends Preference {
    private final @NonNull Context mContext;
    private final String mAccessTime;
    private final Drawable mPermissionIcon;

    public PermissionHistoryPreference(@NonNull Context context, @NonNull String accessTime,
            @NonNull Drawable permissionIcon) {
        super(context);
        mContext = context;
        mAccessTime = accessTime;
        mPermissionIcon = permissionIcon;

        setWidgetLayoutResource(R.layout.permission_history_widget);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView permissionHistoryTime = (TextView) holder
                .findViewById(R.id.permission_history_time);
        ImageView permissionIcon = (ImageView) holder.findViewById(R.id.permission_history_icon);

        permissionHistoryTime.setText(mAccessTime);
        permissionIcon.setImageDrawable(mPermissionIcon);
    }
}
