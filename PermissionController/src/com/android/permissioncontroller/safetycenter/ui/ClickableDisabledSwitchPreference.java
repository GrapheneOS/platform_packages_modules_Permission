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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

/**
 * A preference that can be set to appear disabled, but remain clickable. However, the setChecked
 * method will not register any changes while it appears disabled.
 */
public class ClickableDisabledSwitchPreference extends SwitchPreference {

    private boolean mAppearDisabled;

    public ClickableDisabledSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClickableDisabledSwitchPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        if (holder.itemView instanceof ViewGroup) {
            applyEnableStateToChildren((ViewGroup) holder.itemView);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        if (mAppearDisabled) {
            return;
        }
        super.setChecked(checked);
    }

    /**
     * Set this preference to appear disabled. It will remain clickable, but will be frozen in its
     * current checked state.
     */
    public void setAppearDisabled(boolean appearDisabled) {
        if (appearDisabled == mAppearDisabled) {
            return;
        }
        mAppearDisabled = appearDisabled;
        notifyChanged();
    }

    private void applyEnableStateToChildren(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            child.setEnabled(!mAppearDisabled);
            if (child instanceof ViewGroup) {
                applyEnableStateToChildren((ViewGroup) child);
            }
        }
    }
}
