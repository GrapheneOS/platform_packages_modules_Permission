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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StyleRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;

/** A preference that displays a card representing a {@link SafetyCenterIssue}. */
public class IssueCardPreference extends Preference {

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
                new Button(
                        context,
                        null,
                        0,
                        getStyleFromSeverity(mIssue.getSeverityLevel(), isFirstButton));
        button.setText(action.getLabel());
        button.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        button.setOnClickListener(
                view -> mSafetyCenterViewModel.executeIssueAction(mIssue, action));
        return button;
    }

    @StyleRes
    private int getStyleFromSeverity(int issueSeverityLevel, boolean isFirstButton) {
        if (!isFirstButton) {
            return R.style.SafetyCenter_ActionButton;
        }

        switch (issueSeverityLevel) {
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK:
                return R.style.SafetyCenter_IssueCard_ActionButton_Info;
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION:
                return R.style.SafetyCenter_IssueCard_ActionButton_Recommendation;
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING:
                return R.style.SafetyCenter_IssueCard_ActionButton_Critical;
        }
        throw new IllegalArgumentException(
                String.format(
                        "Unexpected SafetyCenterIssue.IssueSeverityLevel: %s", issueSeverityLevel));
    }
}
