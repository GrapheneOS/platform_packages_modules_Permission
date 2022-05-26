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
import android.safetycenter.SafetyCenterStatus;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/** Preference which displays a visual representation of {@link SafetyCenterStatus}. */
public class SafetyStatusPreference extends Preference {
    private static final String TAG = "SafetyStatusPreference";

    @Nullable private SafetyCenterStatus mStatus;
    @Nullable private View.OnClickListener mRescanButtonOnClickListener;
    private boolean mHasIssues;

    public SafetyStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHasIssues = false;
        setLayoutResource(R.layout.preference_safety_status);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        if (mStatus == null) {
            return;
        }

        ImageView statusImage = (ImageView) holder.findViewById(R.id.status_image);
        statusImage.setImageResource(toStatusImageResId(mStatus.getSeverityLevel()));

        ((TextView) holder.findViewById(R.id.status_title)).setText(mStatus.getTitle());
        ((TextView) holder.findViewById(R.id.status_summary)).setText(mStatus.getSummary());

        ProgressBar rescanProgressBar = (ProgressBar) holder.findViewById(R.id.rescan_progress_bar);

        View rescanButton = holder.findViewById(R.id.rescan_button);
        rescanButton.setBackgroundTintList(
                ContextCompat.getColorStateList(
                        getContext(), toButtonColor(mStatus.getSeverityLevel())));
        if (mRescanButtonOnClickListener != null) {
            rescanButton.setOnClickListener(view -> mRescanButtonOnClickListener.onClick(view));
        }

        if (mStatus.getRefreshStatus()
                == SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS) {
            startRescanAnimation(statusImage, rescanButton, rescanProgressBar);
        } else {
            endRescanAnimation(statusImage, rescanProgressBar, rescanButton);
        }
    }

    private void startRescanAnimation(
            ImageView statusImage, View rescanButton, ProgressBar rescanProgressBar) {
        rescanButton.setVisibility(View.VISIBLE);
        statusImage.setVisibility(View.INVISIBLE);
        rescanProgressBar.setVisibility(View.VISIBLE);
        rescanButton.setEnabled(false);
    }

    private void endRescanAnimation(
            ImageView statusImage, ProgressBar rescanProgressBar, View rescanButton) {
        statusImage.setVisibility(View.VISIBLE);
        rescanProgressBar.setVisibility(View.INVISIBLE);
        rescanButton.setEnabled(true);
        rescanButton.setVisibility(mHasIssues ? View.GONE : View.VISIBLE);
    }

    void setSafetyStatus(SafetyCenterStatus status) {
        mStatus = status;
        notifyChanged();
    }

    void setHasIssues(boolean hasIssues) {
        mHasIssues = hasIssues;
        notifyChanged();
    }

    void setRescanButtonOnClickListener(View.OnClickListener listener) {
        mRescanButtonOnClickListener = listener;
        notifyChanged();
    }

    private static int toStatusImageResId(int overallSeverityLevel) {
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
                return R.drawable.safety_status_info;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                return R.drawable.safety_status_info;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return R.drawable.safety_status_recommendation;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return R.drawable.safety_status_warn;
        }
        throw new IllegalArgumentException(
                String.format(
                        "Unexpected SafetyCenterStatus.OverallSeverityLevel: %s",
                        overallSeverityLevel));
    }

    private static int toButtonColor(int overallSeverityLevel) {
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                return R.color.safety_center_button_info;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return R.color.safety_center_button_recommend;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return R.color.safety_center_button_warn;
            default:
                Log.w(
                        TAG,
                        String.format("Unexpected OverallSeverityLevel: %s", overallSeverityLevel));
                return R.color.safety_center_button_info;
        }
    }
}
