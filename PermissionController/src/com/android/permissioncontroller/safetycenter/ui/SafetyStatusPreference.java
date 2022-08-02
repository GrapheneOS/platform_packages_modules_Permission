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

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.content.Context;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterStatus;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;

import com.google.android.material.button.MaterialButton;

import java.util.Objects;

/** Preference which displays a visual representation of {@link SafetyCenterStatus}. */
@RequiresApi(TIRAMISU)
public class SafetyStatusPreference extends Preference implements ComparablePreference {
    private static final String TAG = "SafetyStatusPreference";

    @Nullable private SafetyCenterStatus mStatus;
    @Nullable private View.OnClickListener mReviewSettingsOnClickListener;
    @Nullable private SafetyCenterViewModel mViewModel;
    private boolean mHasPendingActions;
    private boolean mHasIssues;

    public SafetyStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_safety_status);
    }

    private boolean mIsScanAnimationRunning;
    private boolean mIsIconChangeAnimationRunning;
    private int mQueuedScanAnimationSeverityLevel;
    private int mQueuedIconAnimationSeverityLevel;
    private int mSettledSeverityLevel = SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        if (mStatus == null) {
            return;
        }

        Context context = getContext();
        ImageView statusImage = (ImageView) holder.findViewById(R.id.status_image);
        MaterialButton rescanButton = (MaterialButton) holder.findViewById(R.id.rescan_button);
        MaterialButton pendingActionsRescanButton =
                (MaterialButton) holder.findViewById(R.id.pending_actions_rescan_button);
        View reviewSettingsButton = holder.findViewById(R.id.review_settings_button);
        TextView summaryTextView = ((TextView) holder.findViewById(R.id.status_summary));
        ((TextView) holder.findViewById(R.id.status_title)).setText(mStatus.getTitle());
        if (mHasPendingActions) {
            reviewSettingsButton.setOnClickListener(mReviewSettingsOnClickListener);
            reviewSettingsButton.setVisibility(View.VISIBLE);
            summaryTextView.setText(context.getString(R.string.safety_center_qs_status_summary));
        } else {
            reviewSettingsButton.setVisibility(View.GONE);
            summaryTextView.setText(mStatus.getSummary());
        }
        rescanButton = updateRescanButtonUi(rescanButton, pendingActionsRescanButton);
        setRescanButtonState(rescanButton);

        int contentDescriptionResId =
                R.string.safety_status_preference_title_and_summary_content_description;
        holder.findViewById(R.id.status_title_and_summary)
                .setContentDescription(
                        getContext()
                                .getString(
                                        contentDescriptionResId,
                                        mStatus.getTitle(),
                                        mStatus.getSummary()));

        // Hide the Safety Protection branding if there are any issue cards
        View safetyProtectionSectionView = holder.findViewById(R.id.safety_protection_section_view);
        safetyProtectionSectionView.setVisibility(mHasIssues ? View.GONE : View.VISIBLE);

        rescanButton.setOnClickListener(unused -> {
            SafetyCenterViewModel viewModel = requireViewModel();
            viewModel.rescan();
            viewModel.getInteractionLogger().record(Action.SCAN_INITIATED);
        });

        updateStatusIcon(statusImage, rescanButton);
    }

    private void updateStatusIcon(ImageView statusImage, View rescanButton) {
        int severityLevel = mStatus.getSeverityLevel();

        boolean isRefreshing = isRefreshInProgress();
        boolean shouldStartScanAnimation = isRefreshing && !mIsScanAnimationRunning;
        boolean shouldEndScanAnimation = !isRefreshing && mIsScanAnimationRunning;
        boolean shouldChangeIcon = mSettledSeverityLevel != severityLevel;

        if (shouldStartScanAnimation && !mIsIconChangeAnimationRunning) {
            startScanningAnimation(statusImage);
        } else if (shouldStartScanAnimation) {
            mQueuedScanAnimationSeverityLevel = severityLevel;
        } else if (shouldEndScanAnimation) {
            endScanningAnimation(statusImage, rescanButton);
        } else if (shouldChangeIcon && !mIsScanAnimationRunning) {
            startIconChangeAnimation(statusImage);
        } else if (shouldChangeIcon) {
            mQueuedIconAnimationSeverityLevel = severityLevel;
        } else if (!mIsScanAnimationRunning && !mIsIconChangeAnimationRunning) {
            setSettledStatus(statusImage);
        }
    }

    private boolean isRefreshInProgress() {
        int refreshStatus = mStatus.getRefreshStatus();
        return refreshStatus == SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS
                || refreshStatus
                == SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS;
    }

    private void startScanningAnimation(ImageView statusImage) {
        mIsScanAnimationRunning = true;
        int currentSeverityLevel = mStatus.getSeverityLevel();
        statusImage.setImageResource(
                StatusAnimationResolver.getScanningStartAnimation(currentSeverityLevel));
        AnimatedVectorDrawable animation = (AnimatedVectorDrawable) statusImage.getDrawable();
        animation.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        statusImage.setImageResource(
                                StatusAnimationResolver.getScanningAnimation(currentSeverityLevel));
                        AnimatedVectorDrawable scanningAnim =
                                (AnimatedVectorDrawable) statusImage.getDrawable();
                        scanningAnim.registerAnimationCallback(
                                new Animatable2.AnimationCallback() {
                                    @Override
                                    public void onAnimationEnd(Drawable drawable) {
                                        if (mIsScanAnimationRunning && isRefreshInProgress()) {
                                            scanningAnim.start();
                                        } else {
                                            scanningAnim.clearAnimationCallbacks();
                                        }
                                    }
                                });
                        scanningAnim.start();
                    }
                });
        animation.start();
    }

    private void endScanningAnimation(ImageView statusImage, View rescanButton) {
        Drawable statusDrawable = statusImage.getDrawable();
        if (!(statusDrawable instanceof AnimatedVectorDrawable)) {
            finishScanAnimation(statusImage, rescanButton);
            return;
        }
        AnimatedVectorDrawable animatedStatusDrawable = (AnimatedVectorDrawable) statusDrawable;

        if (!animatedStatusDrawable.isRunning()) {
            finishScanAnimation(statusImage, rescanButton);
            return;
        }

        int scanningSeverityLevel = mSettledSeverityLevel;
        animatedStatusDrawable.clearAnimationCallbacks();
        animatedStatusDrawable.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        statusImage.setImageResource(
                                StatusAnimationResolver.getScanningEndAnimation(
                                        scanningSeverityLevel, mStatus.getSeverityLevel()));
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

    private void finishScanAnimation(ImageView statusImage, View rescanButton) {
        mIsScanAnimationRunning = false;
        setRescanButtonState(rescanButton);
        setSettledStatus(statusImage);
        handleQueuedAction(statusImage);
    }

    private void startIconChangeAnimation(ImageView statusImage) {
        int changeAnimationResId =
                StatusAnimationResolver.getStatusChangeAnimation(
                        mSettledSeverityLevel,
                        mStatus.getSeverityLevel());
        if (changeAnimationResId == 0) {
            setSettledStatus(statusImage);
            return;
        }
        mIsIconChangeAnimationRunning = true;
        statusImage.setImageResource(changeAnimationResId);
        AnimatedVectorDrawable animation = (AnimatedVectorDrawable) statusImage.getDrawable();
        animation.clearAnimationCallbacks();
        animation.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        mIsIconChangeAnimationRunning = false;
                        setSettledStatus(statusImage);
                        handleQueuedAction(statusImage);
                    }
                });
        animation.start();
    }

    private void setSettledStatus(ImageView statusImage) {
        mSettledSeverityLevel = mStatus.getSeverityLevel();
        statusImage.setImageResource(toStatusImageResId(mSettledSeverityLevel));
    }

    private void handleQueuedAction(ImageView statusImage) {
        if (mQueuedScanAnimationSeverityLevel != 0) {
            mQueuedScanAnimationSeverityLevel = 0;
            startScanningAnimation(statusImage);
        } else if (mQueuedIconAnimationSeverityLevel != 0) {
            mQueuedIconAnimationSeverityLevel = 0;
            startIconChangeAnimation(statusImage);
        }
    }

    /**
     * Updates UI for the rescan button depending on the pending actions state and returns the
     * correctly styled rescan button
     */
    private MaterialButton updateRescanButtonUi(
            MaterialButton rescanButton, MaterialButton pendingActionsRescanButton) {
        if (mHasPendingActions) {
            rescanButton.setVisibility(View.GONE);
            pendingActionsRescanButton.setVisibility(View.VISIBLE);
            return pendingActionsRescanButton;
        }
        pendingActionsRescanButton.setVisibility(View.GONE);
        rescanButton.setVisibility(View.VISIBLE);
        return rescanButton;
    }

    void setSafetyStatus(SafetyCenterStatus status) {
        mStatus = status;
        notifyChanged();
    }

    void setSafetyData(SafetyCenterData data) {
        mHasIssues = data.getIssues().size() > 0;
        mStatus = data.getStatus();
        notifyChanged();
    }

    void setViewModel(SafetyCenterViewModel viewModel) {
        mViewModel = Objects.requireNonNull(viewModel);
    }

    private SafetyCenterViewModel requireViewModel() {
        return Objects.requireNonNull(mViewModel);
    }

    /**
     * System has pending actions when the user security and privacy signals are deemed to be safe,
     * but the user has previously dismissed some warnings that may need their review
     */
    void setHasPendingActions(boolean hasPendingActions, View.OnClickListener listener) {
        mHasPendingActions = hasPendingActions;
        mReviewSettingsOnClickListener = listener;
        notifyChanged();
    }

    private void setRescanButtonState(View rescanButton) {
        rescanButton.setVisibility(
                mStatus.getSeverityLevel() != SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK
                        || mHasIssues
                        ? View.GONE
                        : View.VISIBLE);
        rescanButton.setEnabled(!isRefreshInProgress());
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
        if (!(preference instanceof SafetyStatusPreference)) {
            return false;
        }
        SafetyStatusPreference other = (SafetyStatusPreference) preference;
        return Objects.equals(mStatus, other.mStatus)
                && mHasIssues == other.mHasIssues;
    }
}
