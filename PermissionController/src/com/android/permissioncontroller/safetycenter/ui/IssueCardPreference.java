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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static java.util.Objects.requireNonNull;

import android.app.AlertDialog;
import android.content.Context;
import android.safetycenter.SafetyCenterIssue;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;

import com.google.android.material.button.MaterialButton;

/** A preference that displays a card representing a {@link SafetyCenterIssue}. */
public class IssueCardPreference extends Preference implements ComparablePreference {

    public static final String TAG = IssueCardPreference.class.getSimpleName();

    private final SafetyCenterViewModel mSafetyCenterViewModel;
    private final SafetyCenterIssue mIssue;

    public IssueCardPreference(
            Context context, SafetyCenterViewModel safetyCenterViewModel, SafetyCenterIssue issue) {
        super(context);
        setLayoutResource(R.layout.preference_issue_card);

        mSafetyCenterViewModel = requireNonNull(safetyCenterViewModel);
        mIssue = requireNonNull(issue);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        configureDismissButton(holder.findViewById(R.id.issue_card_dismiss_btn));

        ((TextView) holder.findViewById(R.id.issue_card_title)).setText(mIssue.getTitle());
        ((TextView) holder.findViewById(R.id.issue_card_summary)).setText(mIssue.getSummary());

        CharSequence subtitle = mIssue.getSubtitle();
        if (TextUtils.isEmpty(subtitle)) {
            holder.findViewById(R.id.issue_card_subtitle).setVisibility(View.GONE);
        } else {
            ((TextView) holder.findViewById(R.id.issue_card_subtitle)).setText(subtitle);
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

    private void configureDismissButton(View dismissButton) {
        if (mIssue.isDismissible()) {
            dismissButton.setOnClickListener(
                    mIssue.shouldConfirmDismissal()
                            ? new ConfirmDismissalOnClickListener()
                            : new DismissOnClickListener());
            dismissButton.setVisibility(View.VISIBLE);
        } else {
            dismissButton.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean isSameItem(@NonNull Preference preference) {
        return (preference instanceof IssueCardPreference) && TextUtils.equals(
                mIssue.getId(),
                ((IssueCardPreference) preference).mIssue.getId());
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
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.safety_center_issue_card_dismiss_confirmation_title)
                    .setPositiveButton(
                            R.string.safety_center_issue_card_confirm_dismiss_button,
                            (dialog, which) -> mSafetyCenterViewModel.dismissIssue(mIssue))
                    .setNegativeButton(
                            R.string.safety_center_issue_card_cancel_dismiss_button, null)
                    .create()
                    .show();
        }
    }

    private Button buildActionButton(
            SafetyCenterIssue.Action action, Context context, boolean isFirstButton) {
        Button button =
                isFirstButton ? createFirstButton(context) : createSubsequentButton(context);
        button.setText(action.getLabel());
        button.setOnClickListener(
                view -> mSafetyCenterViewModel.executeIssueAction(mIssue, action));
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

        int margin = context.getResources()
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
