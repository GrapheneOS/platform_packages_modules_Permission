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
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static java.util.Objects.requireNonNull;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.safetycenter.SafetyCenterIssue;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import com.google.android.material.button.MaterialButton;

import java.util.Objects;

/** A preference that displays a card representing a {@link SafetyCenterIssue}. */
@RequiresApi(TIRAMISU)
public class IssueCardPreference extends Preference implements ComparablePreference {

    public static final String TAG = IssueCardPreference.class.getSimpleName();

    private final IssueCardAnimator mIssueCardAnimator =
            new IssueCardAnimator(this::markIssueResolvedUiCompleted);
    private final SafetyCenterViewModel mSafetyCenterViewModel;
    private final SafetyCenterIssue mIssue;
    private final FragmentManager mDialogFragmentManager;
    private final SafetyCenterIssueId mDecodedIssueId;
    @Nullable private String mResolvedIssueActionId;
    @Nullable private final Integer mTaskId;
    private final boolean mIsDismissed;
    private final PositionInCardList mPositionInCardList;

    public IssueCardPreference(
            Context context,
            SafetyCenterViewModel safetyCenterViewModel,
            SafetyCenterIssue issue,
            @Nullable String resolvedIssueActionId,
            FragmentManager dialogFragmentManager,
            @Nullable Integer launchTaskId,
            boolean isDismissed,
            PositionInCardList positionInCardList) {
        super(context);
        setLayoutResource(R.layout.preference_issue_card);

        mSafetyCenterViewModel = requireNonNull(safetyCenterViewModel);
        mIssue = requireNonNull(issue);
        mDialogFragmentManager = dialogFragmentManager;
        mDecodedIssueId = SafetyCenterIds.issueIdFromString(mIssue.getId());
        mResolvedIssueActionId = resolvedIssueActionId;
        mTaskId = launchTaskId;
        mIsDismissed = isDismissed;
        mPositionInCardList = positionInCardList;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setBackgroundResource(mPositionInCardList.getBackgroundDrawableResId());
        int topMargin = mPositionInCardList.getTopMargin(getContext());
        MarginLayoutParams layoutParams = (MarginLayoutParams) holder.itemView.getLayoutParams();
        if (layoutParams.topMargin != topMargin) {
            layoutParams.topMargin = topMargin;
            holder.itemView.setLayoutParams(layoutParams);
        }

        // Set default group visibility in case view is being reused
        holder.findViewById(R.id.default_issue_content).setVisibility(View.VISIBLE);
        holder.findViewById(R.id.resolved_issue_content).setVisibility(View.GONE);

        configureDismissButton(holder.findViewById(R.id.issue_card_dismiss_btn));

        ((TextView) holder.findViewById(R.id.issue_card_title)).setText(mIssue.getTitle());
        ((TextView) holder.findViewById(R.id.issue_card_summary)).setText(mIssue.getSummary());

        CharSequence attributionTitle = SdkLevel.isAtLeastU() ? mIssue.getAttributionTitle() : null;
        TextView attributionTitleTextView =
                (TextView) holder.findViewById(R.id.issue_card_attribution_title);
        if (TextUtils.isEmpty(attributionTitle)) {
            attributionTitleTextView.setVisibility(View.GONE);
        } else {
            attributionTitleTextView.setText(attributionTitle);
            attributionTitleTextView.setVisibility(View.VISIBLE);
        }
        CharSequence subtitle = mIssue.getSubtitle();
        TextView subtitleTextView = (TextView) holder.findViewById(R.id.issue_card_subtitle);
        CharSequence contentDescription;
        // TODO(b/257972736): Add a11y support for attribution title.
        if (TextUtils.isEmpty(subtitle)) {
            subtitleTextView.setVisibility(View.GONE);
            contentDescription =
                    getContext()
                            .getString(
                                    R.string.safety_center_issue_card_content_description,
                                    mIssue.getTitle(),
                                    mIssue.getSummary());
        } else {
            subtitleTextView.setText(subtitle);
            subtitleTextView.setVisibility(View.VISIBLE);
            int contentDescriptionResId =
                    R.string.safety_center_issue_card_content_description_with_subtitle;
            contentDescription =
                    getContext()
                            .getString(
                                    contentDescriptionResId,
                                    mIssue.getTitle(),
                                    mIssue.getSubtitle(),
                                    mIssue.getSummary());
        }
        holder.itemView.setContentDescription(contentDescription);
        holder.itemView.setClickable(false);

        LinearLayout buttonList =
                ((LinearLayout) holder.findViewById(R.id.issue_card_action_button_list));
        buttonList.removeAllViews(); // This view may be recycled from another issue
        boolean isFirstButton = true;
        for (SafetyCenterIssue.Action action : mIssue.getActions()) {
            ActionButtonBuilder builder =
                    new ActionButtonBuilder(action, holder.itemView.getContext());
            builder.isLargeScreen(buttonList instanceof EqualWidthContainer);
            if (isFirstButton) {
                builder.setAsPrimaryButton();
                if (!mIsDismissed) {
                    builder.setAsFilledButton();
                }
                isFirstButton = false;
            }
            builder.buildAndAddToView(buttonList);

            if (mResolvedIssueActionId != null && mResolvedIssueActionId.equals(action.getId())) {
                mIssueCardAnimator.transitionToIssueResolvedThenMarkComplete(
                        getContext(), holder, action);
            }
        }

        configureSafetyProtectionView(holder);

        mSafetyCenterViewModel
                .getInteractionLogger()
                .recordForIssue(Action.SAFETY_ISSUE_VIEWED, mIssue);
    }

    private void configureSafetyProtectionView(PreferenceViewHolder holder) {
        View safetyProtectionSectionView =
                holder.findViewById(R.id.issue_card_protected_by_android);
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

    public int getSeverityLevel() {
        return mIssue.getSeverityLevel();
    }

    /** Returns the {@link SafetyCenterIssueKey} associated with this {@link IssueCardPreference} */
    public SafetyCenterIssueKey getIssueKey() {
        return mDecodedIssueId.getSafetyCenterIssueKey();
    }

    private void configureDismissButton(View dismissButton) {
        if (mIssue.isDismissible() && !mIsDismissed) {
            dismissButton.setOnClickListener(
                    mIssue.shouldConfirmDismissal()
                            ? new ConfirmDismissalOnClickListener()
                            : new DismissOnClickListener());
            dismissButton.setVisibility(View.VISIBLE);

            SafetyCenterTouchTarget.configureSize(
                    dismissButton, R.dimen.sc_icon_button_touch_target_size);
        } else {
            dismissButton.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean isSameItem(Preference preference) {
        return (preference instanceof IssueCardPreference)
                && TextUtils.equals(
                        mIssue.getId(), ((IssueCardPreference) preference).mIssue.getId());
    }

    @Override
    public boolean hasSameContents(Preference preference) {
        return (preference instanceof IssueCardPreference)
                && mIssue.equals(((IssueCardPreference) preference).mIssue)
                && Objects.equals(
                        mResolvedIssueActionId,
                        ((IssueCardPreference) preference).mResolvedIssueActionId)
                && mPositionInCardList == ((IssueCardPreference) preference).mPositionInCardList;
    }

    private class DismissOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mSafetyCenterViewModel.dismissIssue(mIssue);
            mSafetyCenterViewModel
                    .getInteractionLogger()
                    .recordForIssue(Action.ISSUE_DISMISS_CLICKED, mIssue);
        }
    }

    private class ConfirmDismissalOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            ConfirmDismissalDialogFragment.newInstance(mIssue)
                    .showNow(mDialogFragmentManager, /* tag= */ null);
        }
    }

    /** Fragment to display a dismissal confirmation dialog for an {@link IssueCardPreference}. */
    public static class ConfirmDismissalDialogFragment extends DialogFragment {
        private static final String ISSUE_KEY = "confirm_dialog_sc_issue";

        private static ConfirmDismissalDialogFragment newInstance(SafetyCenterIssue issue) {
            ConfirmDismissalDialogFragment fragment = new ConfirmDismissalDialogFragment();

            Bundle args = new Bundle();
            args.putParcelable(ISSUE_KEY, issue);
            fragment.setArguments(args);

            return fragment;
        }

        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            SafetyCenterViewModel safetyCenterViewModel =
                    ((SafetyCenterFragment) requireParentFragment()).getSafetyCenterViewModel();
            SafetyCenterIssue issue =
                    requireNonNull(
                            requireArguments().getParcelable(ISSUE_KEY, SafetyCenterIssue.class));
            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.safety_center_issue_card_dismiss_confirmation_title)
                    .setMessage(R.string.safety_center_issue_card_dismiss_confirmation_message)
                    .setPositiveButton(
                            R.string.safety_center_issue_card_confirm_dismiss_button,
                            (dialog, which) -> {
                                safetyCenterViewModel.dismissIssue(issue);
                                safetyCenterViewModel
                                        .getInteractionLogger()
                                        .recordForIssue(Action.ISSUE_DISMISS_CLICKED, issue);
                            })
                    .setNegativeButton(
                            R.string.safety_center_issue_card_cancel_dismiss_button, null)
                    .create();
        }
    }

    /** A dialog to prompt for a confirmation to performn an Action. */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    public static class ConfirmActionDialogFragment extends DialogFragment {
        private static final String ISSUE_KEY = "issue";
        private static final String ACTION_KEY = "action";
        private static final String TASK_ID_KEY = "taskId";
        private static final String IS_PRIMARY_BUTTON_KEY = "isPrimaryButton";

        /** Create new fragment with the data it will need. */
        public static ConfirmActionDialogFragment newInstance(
                SafetyCenterIssue issue,
                SafetyCenterIssue.Action action,
                @Nullable Integer taskId,
                boolean isFirstButton) {
            ConfirmActionDialogFragment fragment = new ConfirmActionDialogFragment();

            Bundle args = new Bundle();
            args.putParcelable(ISSUE_KEY, issue);
            args.putParcelable(ACTION_KEY, action);
            args.putBoolean(IS_PRIMARY_BUTTON_KEY, isFirstButton);
            if (taskId != null) {
                args.putInt(TASK_ID_KEY, taskId);
            }
            fragment.setArguments(args);

            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            SafetyCenterViewModel safetyCenterViewModel =
                    ((SafetyCenterFragment) requireParentFragment()).getSafetyCenterViewModel();
            SafetyCenterIssue issue =
                    requireNonNull(
                            requireArguments().getParcelable(ISSUE_KEY, SafetyCenterIssue.class));
            SafetyCenterIssue.Action action =
                    requireNonNull(
                            requireArguments()
                                    .getParcelable(ACTION_KEY, SafetyCenterIssue.Action.class));
            boolean isPrimaryButton = requireArguments().getBoolean(IS_PRIMARY_BUTTON_KEY);
            Integer taskId =
                    requireArguments().containsKey(TASK_ID_KEY)
                            ? requireArguments().getInt(TASK_ID_KEY)
                            : null;

            return new AlertDialog.Builder(getContext())
                    .setTitle(action.getConfirmationDialogDetails().getTitle())
                    .setMessage(action.getConfirmationDialogDetails().getText())
                    .setPositiveButton(
                            action.getConfirmationDialogDetails().getAcceptButtonText(),
                            (dialog, which) -> {
                                safetyCenterViewModel.executeIssueAction(issue, action, taskId);
                                // TODO(b/269097766): Is this the best logging model?
                                safetyCenterViewModel
                                        .getInteractionLogger()
                                        .recordForIssue(
                                                isPrimaryButton
                                                        ? Action.ISSUE_PRIMARY_ACTION_CLICKED
                                                        : Action.ISSUE_SECONDARY_ACTION_CLICKED,
                                                issue);
                            })
                    .setNegativeButton(
                            action.getConfirmationDialogDetails().getDenyButtonText(), null)
                    .create();
        }
    }

    private void markIssueResolvedUiCompleted() {
        if (mResolvedIssueActionId != null) {
            mResolvedIssueActionId = null;
            mSafetyCenterViewModel.markIssueResolvedUiCompleted(mIssue.getId());
        }
    }

    private class ActionButtonBuilder {
        private final SafetyCenterIssue.Action mAction;
        private final Context mContext;
        private final ContextThemeWrapper mContextThemeWrapper;
        private boolean mIsPrimaryButton = false;
        private boolean mIsFilled = false;
        private boolean mIsLargeScreen = false;

        ActionButtonBuilder(SafetyCenterIssue.Action action, Context context) {
            mAction = action;
            mContext = context;
            mContextThemeWrapper =
                    new ContextThemeWrapper(context, R.style.Theme_MaterialComponents_DayNight);
        }

        public ActionButtonBuilder setAsPrimaryButton() {
            mIsPrimaryButton = true;
            return this;
        }

        public ActionButtonBuilder setAsFilledButton() {
            mIsFilled = true;
            return this;
        }

        public ActionButtonBuilder isLargeScreen(boolean isLargeScreen) {
            mIsLargeScreen = isLargeScreen;
            return this;
        }

        public void buildAndAddToView(LinearLayout buttonList) {
            MaterialButton button = new MaterialButton(mContextThemeWrapper, null, getStyle());
            setButtonColors(button);
            setButtonLayout(button);
            button.setText(mAction.getLabel());
            button.setEnabled(!mAction.isInFlight());
            button.setOnClickListener(
                    view -> {
                        if (SdkLevel.isAtLeastU()
                                && mAction.getConfirmationDialogDetails() != null) {
                            ConfirmActionDialogFragment.newInstance(
                                            mIssue, mAction, mTaskId, mIsPrimaryButton)
                                    .showNow(mDialogFragmentManager, /* tag= */ null);
                        } else {
                            if (mAction.willResolve()) {
                                // Without a confirmation, the button remains tappable. Disable the
                                // button to prevent double-taps.
                                // We ideally want to do this on any button press, however out of an
                                // abundance of caution we only do it with actions that indicate
                                // they will resolve (and therefore we can rely on a model update to
                                // redraw state - either to isInFlight() or simply resolving the
                                // issue.
                                button.setEnabled(false);
                            }
                            mSafetyCenterViewModel.executeIssueAction(mIssue, mAction, mTaskId);
                            mSafetyCenterViewModel
                                    .getInteractionLogger()
                                    .recordForIssue(
                                            mIsPrimaryButton
                                                    ? Action.ISSUE_PRIMARY_ACTION_CLICKED
                                                    : Action.ISSUE_SECONDARY_ACTION_CLICKED,
                                            mIssue);
                        }
                    });

            maybeAddSpaceToView(buttonList);
            buttonList.addView(button);
        }

        private void maybeAddSpaceToView(LinearLayout buttonList) {
            if (mIsPrimaryButton) {
                return;
            }

            int margin =
                    mContext.getResources()
                            .getDimensionPixelSize(R.dimen.sc_action_button_list_margin);
            Space space = new Space(mContext);
            space.setLayoutParams(new ViewGroup.LayoutParams(margin, margin));
            buttonList.addView(space);
        }

        private int getStyle() {
            return mIsFilled ? R.attr.scActionButtonStyle : R.attr.scSecondaryActionButtonStyle;
        }

        private void setButtonColors(MaterialButton button) {
            if (mIsFilled) {
                button.setBackgroundTintList(
                        ContextCompat.getColorStateList(
                                mContext,
                                getPrimaryButtonColorFromSeverity(mIssue.getSeverityLevel())));
            } else {
                button.setStrokeColor(
                        ContextCompat.getColorStateList(
                                mContext,
                                getSecondaryButtonStrokeColorFromSeverity(
                                        mIssue.getSeverityLevel())));
            }
        }

        private void setButtonLayout(Button button) {
            MarginLayoutParams layoutParams = new MarginLayoutParams(layoutWidth(), WRAP_CONTENT);
            button.setLayoutParams(layoutParams);
        }

        private int layoutWidth() {
            if (mIsLargeScreen) {
                return WRAP_CONTENT;
            } else {
                return MATCH_PARENT;
            }
        }

        @ColorRes
        private int getPrimaryButtonColorFromSeverity(int issueSeverityLevel) {
            return pickColorForSeverityLevel(
                    issueSeverityLevel,
                    R.color.safety_center_button_info,
                    R.color.safety_center_button_recommend,
                    R.color.safety_center_button_warn);
        }

        @ColorRes
        private int getSecondaryButtonStrokeColorFromSeverity(int issueSeverityLevel) {
            return pickColorForSeverityLevel(
                    issueSeverityLevel,
                    R.color.safety_center_outline_button_info,
                    R.color.safety_center_outline_button_recommend,
                    R.color.safety_center_outline_button_warn);
        }

        @ColorRes
        private int pickColorForSeverityLevel(
                int issueSeverityLevel,
                @ColorRes int infoColor,
                @ColorRes int recommendColor,
                @ColorRes int warnColor) {
            switch (issueSeverityLevel) {
                case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK:
                    return infoColor;
                case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION:
                    return recommendColor;
                case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING:
                    return warnColor;
                default:
                    Log.w(
                            TAG,
                            String.format("Unexpected issueSeverityLevel: %s", issueSeverityLevel));
                    return infoColor;
            }
        }
    }
}
