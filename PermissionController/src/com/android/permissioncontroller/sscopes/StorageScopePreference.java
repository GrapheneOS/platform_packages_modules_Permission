/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.permissioncontroller.sscopes;

import android.app.StorageScope;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

import java.util.List;

// based on .permission.ui.handheld.PermissionControlPreference
class StorageScopePreference extends Preference {
    private final @NonNull Context mContext;
    private @Nullable Drawable mWidgetIcon;
    private @Nullable View.OnClickListener mWidgetIconOnClickListener;
    private boolean mUseSmallerIcon;
    private boolean mEllipsizeEnd;
    private @Nullable List<Integer> mTitleIcons;
    private @Nullable List<Integer> mSummaryIcons;

    public StorageScopePreference(@NonNull Context context) {
        super(context);
        mContext = context;
    }

    /**
     * Sets this preference's right icon.
     *
     * Note that this must be called before preference layout to take effect.
     *
     * @param widgetIcon the icon to use.
     */
    public void setRightIcon(@NonNull Drawable widgetIcon) {
        mWidgetIcon = widgetIcon;
        setWidgetLayoutResource(R.layout.image_view);
    }

    /**
     * Sets this preference's right icon with an onClickListener.
     *
     * Note that this must be called before preference layout to take effect.
     *
     * @param widgetIcon the icon to use.
     * @param listener the onClickListener attached to the icon.
     */
    public void setRightIcon(@NonNull Drawable widgetIcon, @NonNull View.OnClickListener listener) {
        mWidgetIcon = widgetIcon;
        setWidgetLayoutResource(R.layout.image_view_with_divider);
        mWidgetIconOnClickListener = listener;
    }

    /**
     * Sets this preference's left icon to be smaller than normal.
     *
     * Note that this must be called before preference layout to take effect.
     */
    public void useSmallerIcon() {
        mUseSmallerIcon = true;
    }

    /**
     * Sets this preference's title to use an ellipsis at the end.
     *
     * Note that this must be called before preference layout to take effect.
     */
    public void setEllipsizeEnd() {
        mEllipsizeEnd = true;
    }

    /**
     * Sets this preference to show the given icons to the left of its title.
     *
     * @param titleIcons the icons to show.
     */
    public void setTitleIcons(@NonNull List<Integer> titleIcons) {
        mTitleIcons = titleIcons;
        setLayoutResource(R.layout.preference_usage);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        if (mUseSmallerIcon) {
            ImageView icon = ((ImageView) holder.findViewById(android.R.id.icon));
            icon.setMaxWidth(
                    mContext.getResources().getDimensionPixelSize(R.dimen.secondary_app_icon_size));
            icon.setMaxHeight(
                    mContext.getResources().getDimensionPixelSize(R.dimen.secondary_app_icon_size));
        }

        super.onBindViewHolder(holder);

        if (mWidgetIcon != null) {
            View widgetFrame = holder.findViewById(android.R.id.widget_frame);
            ((ImageView) widgetFrame.findViewById(R.id.icon)).setImageDrawable(mWidgetIcon);
            if (mWidgetIconOnClickListener != null) {
                widgetFrame.findViewById(R.id.icon).setOnClickListener(mWidgetIconOnClickListener);
            }
        }

        if (mEllipsizeEnd) {
            TextView title = (TextView) holder.findViewById(android.R.id.title);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
        }

        setIcons(holder, mSummaryIcons, R.id.summary_widget_frame);
        setIcons(holder, mTitleIcons, R.id.title_widget_frame);
    }

    private void setIcons(PreferenceViewHolder holder, @Nullable List<Integer> icons, int frameId) {
        ViewGroup frame = (ViewGroup) holder.findViewById(frameId);
        if (icons != null && !icons.isEmpty()) {
            frame.setVisibility(View.VISIBLE);
            frame.removeAllViews();
            LayoutInflater inflater = mContext.getSystemService(LayoutInflater.class);
            int numIcons = icons.size();
            for (int i = 0; i < numIcons; i++) {
                ViewGroup group = (ViewGroup) inflater.inflate(R.layout.title_summary_image_view,
                        null);
                ImageView imageView = group.requireViewById(R.id.icon);
                imageView.setImageResource(icons.get(i));
                frame.addView(group);
            }
        } else if (frame != null) {
            frame.setVisibility(View.GONE);
        }
    }
}
