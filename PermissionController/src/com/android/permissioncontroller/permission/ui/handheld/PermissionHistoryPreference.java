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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.role.utils.UiUtils;

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

        setIcon(mPermissionIcon);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ViewGroup widgetFrame = (ViewGroup) holder.findViewById(android.R.id.widget_frame);
        LinearLayout widgetFrameParent = (LinearLayout) widgetFrame.getParent();

        ImageView icon = (ImageView) holder.findViewById(android.R.id.icon);
        int size = getContext().getResources().getDimensionPixelSize(
                R.dimen.permission_icon_size);
        icon.getLayoutParams().width = size;
        icon.getLayoutParams().height = size;
        ViewGroup.MarginLayoutParams marginLayoutParams =
                (ViewGroup.MarginLayoutParams) icon.getLayoutParams();
        int margin = UiUtils.dpToPxSize(12, icon.getContext());
        marginLayoutParams.topMargin = margin;
        marginLayoutParams.bottomMargin = margin;

        ViewGroup widget = (ViewGroup) holder.findViewById(R.id.permission_history_layout);
        if (widget == null) {
            LayoutInflater inflater = mContext.getSystemService(LayoutInflater.class);
            widget = (ViewGroup) inflater.inflate(R.layout.permission_history_widget,
                    widgetFrameParent, false);

            widgetFrameParent.addView(widget, 0);
        }

        widgetFrameParent.setGravity(Gravity.TOP);

        TextView permissionHistoryTime = widget.findViewById(R.id.permission_history_time);
        permissionHistoryTime.setText(mAccessTime);
    }
}
