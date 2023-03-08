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
import android.os.Handler;
import android.os.Looper;
import android.safetycenter.SafetyCenterStatus;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;
import com.android.permissioncontroller.safetycenter.ui.model.StatusUiData;

import com.google.android.material.button.MaterialButton;

import kotlin.Pair;

import java.util.List;
import java.util.Objects;

/** Preference which displays a visual representation of {@link SafetyCenterStatus}. */
@RequiresApi(TIRAMISU)
public class SafetyStatusPreference extends Preference implements ComparablePreference {

    @Nullable private StatusUiData mStatus;
    @Nullable private SafetyCenterViewModel mViewModel;

    private final TextFadeAnimator mTitleTextAnimator = new TextFadeAnimator(R.id.status_title);

    private final TextFadeAnimator mSummaryTextAnimator = new TextFadeAnimator(R.id.status_summary);

    private final TextFadeAnimator mAllTextAnimator =
            new TextFadeAnimator(List.of(R.id.status_title, R.id.status_summary));

    private boolean mFirstBind = true;

    public SafetyStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_safety_status);
    }

    private boolean mIsTextChangeAnimationRunning;
    private final SafetyStatusAnimationSequencer mSequencer = new SafetyStatusAnimationSequencer();

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
        if (mStatus.hasPendingActions()) {
            reviewSettingsButton.setOnClickListener(
                    l -> {
                        requireViewModel()
                                .navigateToSafetyCenter(
                                        context, NavigationSource.QUICK_SETTINGS_TILE);
                        requireViewModel()
                                .getInteractionLogger()
                                .record(Action.REVIEW_SETTINGS_CLICKED);
                    });
            reviewSettingsButton.setVisibility(View.VISIBLE);
        } else {
            reviewSettingsButton.setVisibility(View.GONE);
        }
        rescanButton = updateRescanButtonUi(rescanButton, pendingActionsRescanButton);
        setRescanButtonState(rescanButton);

        holder.findViewById(R.id.status_title_and_summary)
                .setContentDescription(mStatus.getContentDescription(context));

        rescanButton.setOnClickListener(
                unused -> {
                    SafetyCenterViewModel viewModel = requireViewModel();
                    viewModel.rescan();
                    viewModel.getInteractionLogger().record(Action.SCAN_INITIATED);
                });

        updateStatusIcon(statusImage, rescanButton);

        TextView titleTextView = (TextView) holder.findViewById(R.id.status_title);
        TextView summaryTextView = (TextView) holder.findViewById(R.id.status_summary);
        updateStatusText(titleTextView, summaryTextView);

        configureSafetyProtectionView(holder, context);
        mFirstBind = false;
    }

    private void configureSafetyProtectionView(PreferenceViewHolder holder, Context context) {
        View safetyProtectionSectionView = holder.findViewById(R.id.safety_protection_section_view);
        if (KotlinUtils.INSTANCE.shouldShowSafetyProtectionResources(context)) {
            // Hide the Safety Protection branding if there are any issue cards
            safetyProtectionSectionView.setVisibility(
                    mStatus.hasIssues() ? View.GONE : View.VISIBLE);
        }
        if (safetyProtectionSectionView.getVisibility() == View.GONE) {
            holder.itemView.setPaddingRelative(
                    holder.itemView.getPaddingStart(),
                    holder.itemView.getPaddingTop(),
                    holder.itemView.getPaddingEnd(),
                    /* bottom= */ getContext()
                            .getResources()
                            .getDimensionPixelSize(R.dimen.sc_card_margin_bottom));
        } else {
            holder.itemView.setPaddingRelative(
                    holder.itemView.getPaddingStart(),
                    holder.itemView.getPaddingTop(),
                    holder.itemView.getPaddingEnd(),
                    /* bottom= */ 0);
        }
    }

    private void updateStatusText(TextView title, TextView summary) {
        if (mFirstBind) {
            title.setText(mStatus.getTitle());
            summary.setText(mStatus.getSummary(getContext()));
        }
        runTextAnimationIfNeeded(title, summary);
    }

    private void updateStatusIcon(ImageView statusImage, View rescanButton) {
        int severityLevel = mStatus.getSeverityLevel();
        boolean isRefreshing = mStatus.isRefreshInProgress();

        handleAnimationSequencerAction(
                mSequencer.onUpdateReceived(isRefreshing, severityLevel),
                statusImage,
                rescanButton,
                /* scanningAnimation= */ null);
    }

    private void runTextAnimationIfNeeded(TextView titleView, TextView summaryView) {
        if (mIsTextChangeAnimationRunning) {
            return;
        }
        String titleText = mStatus.getTitle().toString();
        String summaryText = mStatus.getSummary(getContext()).toString();
        boolean titleEquals = titleView.getText().toString().equals(titleText);
        boolean summaryEquals = summaryView.getText().toString().equals(summaryText);
        Runnable onFinish =
                () -> {
                    mIsTextChangeAnimationRunning = false;
                    runTextAnimationIfNeeded(titleView, summaryView);
                };
        mIsTextChangeAnimationRunning = !titleEquals || !summaryEquals;
        if (!titleEquals && !summaryEquals) {
            Pair<TextView, String> titleChange = new Pair<>(titleView, titleText);
            Pair<TextView, String> summaryChange = new Pair<>(summaryView, summaryText);
            mAllTextAnimator.animateChangeText(List.of(titleChange, summaryChange), onFinish);
        } else if (!titleEquals) {
            mTitleTextAnimator.animateChangeText(titleView, titleText, onFinish);
        } else if (!summaryEquals) {
            mSummaryTextAnimator.animateChangeText(summaryView, summaryText, onFinish);
        }
    }

    private void startScanningAnimation(ImageView statusImage, View rescanButton) {
        mSequencer.onStartScanningAnimationStart();
        statusImage.setImageResource(
                StatusAnimationResolver.getScanningStartAnimation(
                        mSequencer.getCurrentlyVisibleSeverityLevel()));
        AnimatedVectorDrawable animation = (AnimatedVectorDrawable) statusImage.getDrawable();
        animation.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        handleAnimationSequencerAction(
                                mSequencer.onStartScanningAnimationEnd(),
                                statusImage,
                                rescanButton,
                                /* scanningAnimation= */ null);
                    }
                });
        animation.start();
    }

    private void continueScanningAnimation(ImageView statusImage, View rescanButton) {
        // clear previous scan animation in case we need to continue with different severity level
        Drawable statusDrawable = statusImage.getDrawable();
        if (statusDrawable instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) statusDrawable).clearAnimationCallbacks();
        }

        statusImage.setImageResource(
                StatusAnimationResolver.getScanningAnimation(
                        mSequencer.getCurrentlyVisibleSeverityLevel()));
        AnimatedVectorDrawable scanningAnim = (AnimatedVectorDrawable) statusImage.getDrawable();
        scanningAnim.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        handleAnimationSequencerAction(
                                mSequencer.onContinueScanningAnimationEnd(
                                        mStatus.isRefreshInProgress(), mStatus.getSeverityLevel()),
                                statusImage,
                                rescanButton,
                                scanningAnim);
                    }
                });
        scanningAnim.start();
    }

    private void endScanningAnimation(ImageView statusImage, View rescanButton) {
        Drawable statusDrawable = statusImage.getDrawable();
        int finishingSeverityLevel = mStatus.getSeverityLevel();
        if (!(statusDrawable instanceof AnimatedVectorDrawable)) {
            finishScanAnimation(statusImage, rescanButton, finishingSeverityLevel);
            return;
        }
        AnimatedVectorDrawable animatedStatusDrawable = (AnimatedVectorDrawable) statusDrawable;

        if (!animatedStatusDrawable.isRunning()) {
            finishScanAnimation(statusImage, rescanButton, finishingSeverityLevel);
            return;
        }

        int scanningSeverityLevel = mSequencer.getCurrentlyVisibleSeverityLevel();
        animatedStatusDrawable.clearAnimationCallbacks();
        animatedStatusDrawable.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        statusImage.setImageResource(
                                StatusAnimationResolver.getScanningEndAnimation(
                                        scanningSeverityLevel, finishingSeverityLevel));
                        AnimatedVectorDrawable animatedDrawable =
                                (AnimatedVectorDrawable) statusImage.getDrawable();
                        animatedDrawable.registerAnimationCallback(
                                new Animatable2.AnimationCallback() {
                                    @Override
                                    public void onAnimationEnd(Drawable drawable) {
                                        super.onAnimationEnd(drawable);
                                        finishScanAnimation(
                                                statusImage, rescanButton, finishingSeverityLevel);
                                    }
                                });
                        animatedDrawable.start();
                    }
                });
    }

    private void finishScanAnimation(
            ImageView statusImage, View rescanButton, int finishedSeverityLevel) {
        setRescanButtonState(rescanButton);
        handleAnimationSequencerAction(
                mSequencer.onFinishScanAnimationEnd(
                        mStatus.isRefreshInProgress(), finishedSeverityLevel),
                statusImage,
                rescanButton,
                /* scanningAnimation= */ null);
    }

    private void startIconChangeAnimation(ImageView statusImage, View rescanButton) {
        int finalSeverityLevel = mStatus.getSeverityLevel();
        int changeAnimationResId =
                StatusAnimationResolver.getStatusChangeAnimation(
                        mSequencer.getCurrentlyVisibleSeverityLevel(), finalSeverityLevel);
        if (changeAnimationResId == 0) {
            handleAnimationSequencerAction(
                    mSequencer.onCouldNotStartIconChangeAnimation(
                            mStatus.isRefreshInProgress(), finalSeverityLevel),
                    statusImage,
                    rescanButton,
                    /* scanningAnimation= */ null);
            return;
        }
        mSequencer.onIconChangeAnimationStart();
        statusImage.setImageResource(changeAnimationResId);
        AnimatedVectorDrawable animation = (AnimatedVectorDrawable) statusImage.getDrawable();
        animation.clearAnimationCallbacks();
        animation.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        handleAnimationSequencerAction(
                                mSequencer.onIconChangeAnimationEnd(
                                        mStatus.isRefreshInProgress(), finalSeverityLevel),
                                statusImage,
                                rescanButton,
                                /* scanningAnimation= */ null);
                    }
                });
        animation.start();
    }

    private void handleAnimationSequencerAction(
            @Nullable SafetyStatusAnimationSequencer.Action action,
            ImageView statusImage,
            View rescanButton,
            @Nullable AnimatedVectorDrawable scanningAnimation) {
        if (action == null) {
            return;
        }
        switch (action) {
            case START_SCANNING_ANIMATION:
                startScanningAnimation(statusImage, rescanButton);
                break;
            case CONTINUE_SCANNING_ANIMATION:
                if (scanningAnimation != null) {
                    scanningAnimation.start();
                } else {
                    continueScanningAnimation(statusImage, rescanButton);
                }
                break;
            case RESET_SCANNING_ANIMATION:
                continueScanningAnimation(statusImage, rescanButton);
                break;
            case FINISH_SCANNING_ANIMATION:
                endScanningAnimation(statusImage, rescanButton);
                break;
            case START_ICON_CHANGE_ANIMATION:
                startIconChangeAnimation(statusImage, rescanButton);
                break;
            case CHANGE_ICON_WITHOUT_ANIMATION:
                setSettledStatus(statusImage);
                break;
        }
    }

    private void setSettledStatus(ImageView statusImage) {
        Drawable statusDrawable = statusImage.getDrawable();
        if (statusDrawable instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) statusDrawable).clearAnimationCallbacks();
        }
        statusImage.setImageResource(
                StatusUiData.Companion.getStatusImageResId(
                        mSequencer.getCurrentlyVisibleSeverityLevel()));
    }

    /**
     * Updates UI for the rescan button depending on the pending actions state and returns the
     * correctly styled rescan button
     */
    private MaterialButton updateRescanButtonUi(
            MaterialButton rescanButton, MaterialButton pendingActionsRescanButton) {
        if (mStatus.hasPendingActions()) {
            rescanButton.setVisibility(View.GONE);
            pendingActionsRescanButton.setVisibility(View.VISIBLE);
            return pendingActionsRescanButton;
        }
        pendingActionsRescanButton.setVisibility(View.GONE);
        rescanButton.setVisibility(View.VISIBLE);
        return rescanButton;
    }

    void setData(StatusUiData statusUiData) {
        mStatus = statusUiData;
        safeNotifyChanged();
    }

    void setViewModel(SafetyCenterViewModel viewModel) {
        mViewModel = Objects.requireNonNull(viewModel);
    }

    private SafetyCenterViewModel requireViewModel() {
        return Objects.requireNonNull(mViewModel);
    }

    private void setRescanButtonState(View rescanButton) {
        rescanButton.setVisibility(mStatus.shouldShowRescanButton() ? View.VISIBLE : View.GONE);
        rescanButton.setEnabled(!mStatus.isRefreshInProgress());
    }

    // Calling notifyChanged while recyclerview is scrolling or computing layout will result in an
    // IllegalStateException. Post to handler to wait for UI to settle.
    private void safeNotifyChanged() {
        new Handler(Looper.getMainLooper()).post(this::notifyChanged);
    }

    @Override
    public boolean isSameItem(Preference preference) {
        return preference instanceof SafetyStatusPreference
                && TextUtils.equals(getKey(), preference.getKey());
    }

    @Override
    public boolean hasSameContents(Preference preference) {
        if (!(preference instanceof SafetyStatusPreference)) {
            return false;
        }
        SafetyStatusPreference other = (SafetyStatusPreference) preference;
        return Objects.equals(mStatus, other.mStatus);
    }
}
