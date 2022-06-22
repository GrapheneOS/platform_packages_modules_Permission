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
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.safetycenter.SafetyCenterStatus;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

import java.util.Objects;

/** Preference which displays a visual representation of {@link SafetyCenterStatus}. */
public class SafetyStatusPreference extends Preference implements ComparablePreference {
    private static final String TAG = "SafetyStatusPreference";

    @Nullable private SafetyCenterStatus mStatus;
    @Nullable private View.OnClickListener mRescanButtonOnClickListener;
    private boolean mHasIssues;

    public SafetyStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHasIssues = false;
        setLayoutResource(R.layout.preference_safety_status);
    }

    private boolean mRefreshRunning;

    private boolean mRefreshEnding;

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        if (mStatus == null) {
            return;
        }

        ImageView statusImage = (ImageView) holder.findViewById(R.id.status_image);
        View rescanButton = holder.findViewById(R.id.rescan_button);
        if (!mRefreshRunning) {
            statusImage.setImageResource(toStatusImageResId(mStatus.getSeverityLevel()));
        } else {
            rescanButton.setEnabled(false);
        }
        ((TextView) holder.findViewById(R.id.status_title)).setText(mStatus.getTitle());
        ((TextView) holder.findViewById(R.id.status_summary)).setText(mStatus.getSummary());
        holder.findViewById(R.id.status_title_and_summary).setContentDescription(
                getContext().getString(
                        R.string.safety_status_preference_title_and_summary_content_description,
                        mStatus.getTitle(), mStatus.getSummary()));

        // Hide the Safety Protection branding if there are any issue cards
        View safetyProtectionSectionView =
                holder.findViewById(R.id.safety_protection_section_view);
        safetyProtectionSectionView.setVisibility(mHasIssues ? View.GONE : View.VISIBLE);

        if (mRescanButtonOnClickListener != null) {
            rescanButton.setOnClickListener(view -> mRescanButtonOnClickListener.onClick(view));
        }

        boolean inRefreshStatus = mStatus.getRefreshStatus()
                == SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS
                || mStatus.getRefreshStatus()
                == SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS;
        if (inRefreshStatus && !mRefreshRunning) {
            startRescanAnimation(statusImage, rescanButton);
            mRefreshRunning = true;
        } else if (mRefreshRunning && !mRefreshEnding) {
            mRefreshEnding = true;
            endRescanAnimation(statusImage, rescanButton);
        } else {
            updateRescanButtonVisibility(rescanButton);
        }
    }

    private void startRescanAnimation(ImageView statusImage, View rescanButton) {
        statusImage.setImageResource(R.drawable.status_info_to_scanning_anim);
        AnimatedVectorDrawable animation = (AnimatedVectorDrawable) statusImage.getDrawable();
        animation.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        statusImage.setImageResource(R.drawable.status_scanning_anim);
                        AnimatedVectorDrawable scanningAnim =
                                (AnimatedVectorDrawable) statusImage.getDrawable();
                        scanningAnim.registerAnimationCallback(
                                new Animatable2.AnimationCallback() {
                                    @Override
                                    public void onAnimationEnd(Drawable drawable) {
                                        ((AnimatedVectorDrawable) drawable).start();
                                    }
                                });
                        scanningAnim.start();
                    }
                });
        animation.start();
        updateRescanButtonVisibility(rescanButton);
        rescanButton.setEnabled(false);
    }

    private void endRescanAnimation(ImageView statusImage, View rescanButton) {
        Drawable statusDrawable = statusImage.getDrawable();
        if (!(statusDrawable instanceof AnimatedVectorDrawable)) {
            return;
        }
        AnimatedVectorDrawable animatedStatusDrawable = (AnimatedVectorDrawable) statusDrawable;

        if (!animatedStatusDrawable.isRunning()) {
            return;
        }

        animatedStatusDrawable.clearAnimationCallbacks();
        animatedStatusDrawable.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        int exitAnimation = getEndingAnimation();
                        statusImage.setImageResource(exitAnimation);
                        AnimatedVectorDrawable animatedDrawable =
                                (AnimatedVectorDrawable) statusImage.getDrawable();
                        animatedDrawable.registerAnimationCallback(
                                new Animatable2.AnimationCallback() {
                                    @Override
                                    public void onAnimationEnd(Drawable drawable) {
                                        super.onAnimationEnd(drawable);
                                        finishScanAnimation(statusImage, rescanButton);
                                    }
                                });
                        animatedDrawable.start();
                    }
                });
    }

    private int getEndingAnimation() {
        switch (mStatus.getSeverityLevel()) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return R.drawable.status_scanning_to_warn_anim;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return R.drawable.status_scanning_to_recommend_anim;
            default:
                return R.drawable.status_scanning_to_info_anim;
        }
    }

    private void finishScanAnimation(ImageView statusImage, View rescanButton) {
        statusImage.setImageResource(toStatusImageResId(mStatus.getSeverityLevel()));
        mRefreshRunning = false;
        mRefreshEnding = false;
        rescanButton.setEnabled(true);
        updateRescanButtonVisibility(rescanButton);
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

    private void updateRescanButtonVisibility(View rescanButton) {
        rescanButton.setVisibility(
                mStatus.getSeverityLevel() != SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
                                || mHasIssues
                        ? View.GONE
                        : View.VISIBLE);
    }

    private static int toStatusImageResId(int overallSeverityLevel) {
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                return R.drawable.safety_status_info;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return R.drawable.safety_status_recommendation;
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return R.drawable.safety_status_warn;
            default:
                Log.w(
                        TAG,
                        String.format("Unexpected OverallSeverityLevel: %s", overallSeverityLevel));
                return R.drawable.safety_status_info;
        }
    }

    @Override
    public boolean isSameItem(@NonNull Preference preference) {
        return preference instanceof SafetyStatusPreference
                && TextUtils.equals(getKey(), preference.getKey());
    }

    @Override
    public boolean hasSameContents(@NonNull Preference preference) {
        return preference instanceof SafetyStatusPreference
                && Objects.equals(mStatus, (((SafetyStatusPreference) preference).mStatus));
    }
}
