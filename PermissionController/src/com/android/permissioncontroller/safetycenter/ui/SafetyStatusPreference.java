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
import static android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING;
import static android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
import static android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION;
import static android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;

import android.content.Context;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
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
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Objects;

import kotlin.Pair;

/** Preference which displays a visual representation of {@link SafetyCenterStatus}. */
@RequiresApi(TIRAMISU)
public class SafetyStatusPreference extends Preference implements ComparablePreference {
    private static final String TAG = "SafetyStatusPreference";

    @Nullable private SafetyCenterStatus mStatus;
    @Nullable private View.OnClickListener mReviewSettingsOnClickListener;
    @Nullable private SafetyCenterViewModel mViewModel;

    @NonNull
    private final TextFadeAnimator mTitleTextAnimator = new TextFadeAnimator(R.id.status_title);

    @NonNull
    private final TextFadeAnimator mSummaryTextAnimator = new TextFadeAnimator(R.id.status_summary);

    @NonNull
    private final TextFadeAnimator mAllTextAnimator =
            new TextFadeAnimator(List.of(R.id.status_title, R.id.status_summary));

    private boolean mFirstBind = true;
    private boolean mHasPendingActions;
    private boolean mHasIssues;

    public SafetyStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_safety_status);
    }

    private boolean mIsScanAnimationRunning;
    private boolean mIsIconChangeAnimationRunning;
    private boolean mIsTextChangeAnimationRunning;
    private int mQueuedScanAnimationSeverityLevel;
    private int mQueuedIconAnimationSeverityLevel;
    private int mSettledSeverityLevel = OVERALL_SEVERITY_LEVEL_UNKNOWN;

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
        if (mHasPendingActions) {
            reviewSettingsButton.setOnClickListener(mReviewSettingsOnClickListener);
            reviewSettingsButton.setVisibility(View.VISIBLE);
        } else {
            reviewSettingsButton.setVisibility(View.GONE);
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
            safetyProtectionSectionView.setVisibility(mHasIssues ? View.GONE : View.VISIBLE);
        }
        if (safetyProtectionSectionView.getVisibility() == View.GONE) {
            holder.itemView.setPaddingRelative(
                    holder.itemView.getPaddingStart(),
                    holder.itemView.getPaddingTop(),
                    holder.itemView.getPaddingEnd(),
                    /* bottom = */ getContext()
                            .getResources()
                            .getDimensionPixelSize(R.dimen.safety_center_card_margin_bottom));
        } else {
            holder.itemView.setPaddingRelative(
                    holder.itemView.getPaddingStart(),
                    holder.itemView.getPaddingTop(),
                    holder.itemView.getPaddingEnd(),
                    /* bottom = */ 0);
        }
    }

    private void updateStatusText(TextView title, TextView summary) {
        if (mFirstBind) {
            title.setText(mStatus.getTitle());
            summary.setText(getSummaryText());
        }
        runTextAnimationIfNeeded(title, summary);
    }

    private void updateStatusIcon(ImageView statusImage, View rescanButton) {
        int severityLevel = mStatus.getSeverityLevel();

        boolean isRefreshing = isRefreshInProgress();
        boolean shouldStartScanAnimation = isRefreshing && !mIsScanAnimationRunning;
        boolean shouldEndScanAnimation = !isRefreshing && mIsScanAnimationRunning;
        boolean shouldChangeIcon = mSettledSeverityLevel != severityLevel;

        if (shouldStartScanAnimation && !mIsIconChangeAnimationRunning) {
            mSettledSeverityLevel = severityLevel;
            startScanningAnimation(statusImage);
        } else if (shouldStartScanAnimation) {
            mQueuedScanAnimationSeverityLevel = severityLevel;
        } else if (mIsScanAnimationRunning && shouldChangeIcon) {
            mSettledSeverityLevel = severityLevel;
            continueScanningAnimation(statusImage);
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

    private void runTextAnimationIfNeeded(TextView titleView, TextView summaryView) {
        if (mIsTextChangeAnimationRunning) {
            return;
        }
        String titleText = mStatus.getTitle().toString();
        String summaryText = getSummaryText().toString();
        boolean titleEquals = titleView.getText().toString().equals(titleText);
        boolean summaryEquals = summaryView.getText().toString().equals(summaryText);
        Runnable onFinish = () -> {
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

    private CharSequence getSummaryText() {
        if (mHasPendingActions) {
            return getContext().getString(R.string.safety_center_qs_status_summary);
        } else {
            return mStatus.getSummary().toString();
        }
    }

    private boolean isRefreshInProgress() {
        int refreshStatus = mStatus.getRefreshStatus();
        return refreshStatus == SafetyCenterStatus.REFRESH_STATUS_FULL_RESCAN_IN_PROGRESS
                || refreshStatus == SafetyCenterStatus.REFRESH_STATUS_DATA_FETCH_IN_PROGRESS;
    }

    private void startScanningAnimation(ImageView statusImage) {
        mIsScanAnimationRunning = true;
        statusImage.setImageResource(
                StatusAnimationResolver.getScanningStartAnimation(mSettledSeverityLevel));
        AnimatedVectorDrawable animation = (AnimatedVectorDrawable) statusImage.getDrawable();
        animation.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        continueScanningAnimation(statusImage);
                    }
                });
        animation.start();
    }

    private void continueScanningAnimation(ImageView statusImage) {
        // clear previous scan animation in case we need to continue with different severity level
        Drawable statusDrawable = statusImage.getDrawable();
        if (statusDrawable instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) statusDrawable).clearAnimationCallbacks();
        }

        statusImage.setImageResource(
                StatusAnimationResolver.getScanningAnimation(mSettledSeverityLevel));
        AnimatedVectorDrawable scanningAnim = (AnimatedVectorDrawable) statusImage.getDrawable();
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
                                        finishScanAnimation(
                                                statusImage, rescanButton);
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
                        mSettledSeverityLevel, mStatus.getSeverityLevel());
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
        Drawable statusDrawable = statusImage.getDrawable();
        if (statusDrawable instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) statusDrawable).clearAnimationCallbacks();
        }

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
        safeNotifyChanged();
    }

    void setSafetyData(SafetyCenterData data) {
        mHasIssues = data.getIssues().size() > 0;
        mStatus = data.getStatus();
        safeNotifyChanged();
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
        safeNotifyChanged();
    }

    private void setRescanButtonState(View rescanButton) {
        rescanButton.setVisibility(shouldShowRescanButton() ? View.VISIBLE : View.GONE);
        rescanButton.setEnabled(!isRefreshInProgress());
    }

    private boolean shouldShowRescanButton() {
        int severityLevel = mStatus.getSeverityLevel();
        return !mHasIssues
                && !mHasPendingActions // hides the second button in QS to keep the UI clean
                && (severityLevel == OVERALL_SEVERITY_LEVEL_OK
                        || severityLevel == OVERALL_SEVERITY_LEVEL_UNKNOWN);
    }

    // Calling notifyChanged while recyclerview is scrolling or computing layout will result in an
    // IllegalStateException. Post to handler to wait for UI to settle.
    private void safeNotifyChanged() {
        new Handler(Looper.getMainLooper()).post(() -> notifyChanged());
    }

    private static int toStatusImageResId(int overallSeverityLevel) {
        switch (overallSeverityLevel) {
            case OVERALL_SEVERITY_LEVEL_UNKNOWN:
            case OVERALL_SEVERITY_LEVEL_OK:
                return R.drawable.safety_status_info;
            case OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return R.drawable.safety_status_recommendation;
            case OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
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
        return Objects.equals(mStatus, other.mStatus) && mHasIssues == other.mHasIssues;
    }
}
