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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import com.google.android.material.button.MaterialButton;

/** A preference that displays a card representing a {@link SafetyCenterIssue}. */
@RequiresApi(TIRAMISU)
public class IssueCardPreference extends Preference implements ComparablePreference {

    public static final String TAG = IssueCardPreference.class.getSimpleName();

    private final SafetyCenterViewModel mSafetyCenterViewModel;
    private final SafetyCenterIssue mIssue;
    private final FragmentManager mDialogFragmentManager;

    public IssueCardPreference(
            Context context,
            SafetyCenterViewModel safetyCenterViewModel,
            SafetyCenterIssue issue,
            FragmentManager dialogFragmentManager) {
        super(context);
        setLayoutResource(R.layout.preference_issue_card);

        mSafetyCenterViewModel = requireNonNull(safetyCenterViewModel);
        mIssue = requireNonNull(issue);
        mDialogFragmentManager = dialogFragmentManager;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        configureDismissButton(holder.findViewById(R.id.issue_card_dismiss_btn));

        ((TextView) holder.findViewById(R.id.issue_card_title)).setText(mIssue.getTitle());
        ((TextView) holder.findViewById(R.id.issue_card_summary)).setText(mIssue.getSummary());

        CharSequence subtitle = mIssue.getSubtitle();
        TextView subtitleTextView = (TextView) holder.findViewById(R.id.issue_card_subtitle);
        if (TextUtils.isEmpty(subtitle)) {
            subtitleTextView.setVisibility(View.GONE);
        } else {
            subtitleTextView.setText(subtitle);
            subtitleTextView.setVisibility(View.VISIBLE);
        }

        LinearLayout buttonList =
                ((LinearLayout) holder.findViewById(R.id.issue_card_action_button_list));
        buttonList.removeAllViews(); // This view may be recycled from another issue
        boolean isFirstButton = true;
        for (SafetyCenterIssue.Action action : mIssue.getActions()) {
            buttonList.addView(
                    buildActionButton(action, holder.itemView.getContext(), isFirstButton));
            isFirstButton = false;
        }
    }

    public int getSeverityLevel() {
        return mIssue.getSeverityLevel();
    }

    /** Returns the {@link SafetyCenterIssueKey} associated with this {@link IssueCardPreference} */
    public SafetyCenterIssueKey getIssueKey() {
        SafetyCenterIssueId safetyCenterIssueId = SafetyCenterIds.issueIdFromString(mIssue.getId());
        if (!safetyCenterIssueId.hasSafetyCenterIssueKey()) {
            Log.d(TAG, "preference has no issue key");
            return null;
        }
        return safetyCenterIssueId.getSafetyCenterIssueKey();
    }

    private void configureDismissButton(View dismissButton) {
        if (mIssue.isDismissible()) {
            dismissButton.setOnClickListener(
                    mIssue.shouldConfirmDismissal()
                            ? new ConfirmDismissalOnClickListener()
                            : new DismissOnClickListener());
            dismissButton.setVisibility(View.VISIBLE);

            SafetyCenterTouchTarget.configureSize(
                    dismissButton,
                    R.dimen.safety_center_icon_button_touch_target_size);
        } else {
            dismissButton.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean isSameItem(@NonNull Preference preference) {
        return (preference instanceof IssueCardPreference)
                && TextUtils.equals(
                        mIssue.getId(), ((IssueCardPreference) preference).mIssue.getId());
    }

    @Override
    public boolean hasSameContents(@NonNull Preference preference) {
        return (preference instanceof IssueCardPreference)
                && mIssue.equals(((IssueCardPreference) preference).mIssue);
    }

    private class DismissOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mSafetyCenterViewModel.dismissIssue(mIssue);
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
                    ((SafetyCenterDashboardFragment) requireParentFragment())
                            .getSafetyCenterViewModel();
            SafetyCenterIssue issue =
                    requireNonNull(
                            requireArguments().getParcelable(ISSUE_KEY, SafetyCenterIssue.class));
            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.safety_center_issue_card_dismiss_confirmation_title)
                    .setPositiveButton(
                            R.string.safety_center_issue_card_confirm_dismiss_button,
                            (dialog, which) -> safetyCenterViewModel.dismissIssue(issue))
                    .setNegativeButton(
                            R.string.safety_center_issue_card_cancel_dismiss_button, null)
                    .create();
        }
    }

    private Button buildActionButton(
            SafetyCenterIssue.Action action, Context context, boolean isFirstButton) {
        Button button =
                isFirstButton ? createFirstButton(context) : createSubsequentButton(context);
        button.setText(action.getLabel());
        button.setEnabled(!action.isInFlight());
        button.setOnClickListener((view) -> {
            if (action.willResolve()) {
                // Disable the button to prevent double-taps.
                // We ideally want to do this on any button press, however out of an abundance of
                // caution we only do it with actions that indicate they will resolve (and therefore
                // we can rely on a model update to redraw state). We expect the model to update
                // with either isInFlight() or simply removing/updating the issue.
                button.setEnabled(false);
            }
            mSafetyCenterViewModel.executeIssueAction(mIssue, action);
        });
        return button;
    }

    private Button createFirstButton(Context context) {
        ContextThemeWrapper themedContext =
                new ContextThemeWrapper(context, R.style.Theme_MaterialComponents_DayNight);
        Button button = new MaterialButton(themedContext, null, R.attr.scActionButtonStyle);
        button.setBackgroundTintList(
                ContextCompat.getColorStateList(
                        context, getButtonColorFromSeverity(mIssue.getSeverityLevel())));

        button.setLayoutParams(new ViewGroup.MarginLayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return button;
    }

    private Button createSubsequentButton(Context context) {
        ContextThemeWrapper themedContext =
                new ContextThemeWrapper(context, R.style.Theme_MaterialComponents_DayNight);
        MaterialButton button =
                new MaterialButton(themedContext, null, R.attr.scSecondaryActionButtonStyle);
        button.setStrokeColor(
                ContextCompat.getColorStateList(
                        context, getButtonColorFromSeverity(mIssue.getSeverityLevel())));

        int margin =
                context.getResources()
                        .getDimensionPixelSize(R.dimen.safety_center_action_button_list_margin);
        ViewGroup.MarginLayoutParams layoutParams =
                new ViewGroup.MarginLayoutParams(MATCH_PARENT, WRAP_CONTENT);
        layoutParams.setMargins(0, margin, 0, 0);
        button.setLayoutParams(layoutParams);
        return button;
    }

    private static int getButtonColorFromSeverity(int issueSeverityLevel) {
        switch (issueSeverityLevel) {
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK:
                return R.color.safety_center_button_info;
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION:
                return R.color.safety_center_button_recommend;
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING:
                return R.color.safety_center_button_warn;
            default:
                Log.w(TAG, String.format("Unexpected issueSeverityLevel: %s", issueSeverityLevel));
                return R.color.safety_center_button_info;
        }
    }
}
