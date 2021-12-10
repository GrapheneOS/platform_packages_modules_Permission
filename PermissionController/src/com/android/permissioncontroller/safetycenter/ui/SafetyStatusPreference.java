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

package com.android.permissioncontroller.safetycenter.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/** Preference which displays a visual representation of {@link OverallSafetyStatus}. */
public class SafetyStatusPreference extends Preference {

    // Default status will be overwritten before displaying to the user. This is just here to avoid
    // NPEs if this preference is misused.
    private OverallSafetyStatus mStatus = new OverallSafetyStatus(
            OverallSafetyStatus.Level.SAFETY_STATUS_LEVEL_UNKNOWN, "", "");

    public SafetyStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_safety_status);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ((ImageView) holder.findViewById(R.id.status_image)).setImageResource(
                mStatus.getImageResId());
        ((TextView) holder.findViewById(R.id.status_title)).setText(mStatus.getTitle());
        ((TextView) holder.findViewById(R.id.status_summary)).setText(mStatus.getSummary());
    }

    /** Set the {@link OverallSafetyStatus} to display. */
    public void setSafetyStatus(OverallSafetyStatus status) {
        mStatus = status;
        notifyChanged();
    }
}
