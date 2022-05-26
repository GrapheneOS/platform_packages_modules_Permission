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

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.os.Bundle;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterEntry;
import android.safetycenter.SafetyCenterEntryGroup;
import android.safetycenter.SafetyCenterEntryOrGroup;
import android.safetycenter.SafetyCenterErrorDetails;
import android.safetycenter.SafetyCenterIssue;
import android.safetycenter.SafetyCenterStaticEntry;
import android.safetycenter.SafetyCenterStaticEntryGroup;
import android.safetycenter.SafetyCenterStatus;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.LiveSafetyCenterViewModelFactory;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;

import java.util.List;

/** Dashboard fragment for the Safety Center. */
public final class SafetyCenterDashboardFragment extends PreferenceFragmentCompat {

    private static final String TAG = SafetyCenterDashboardFragment.class.getSimpleName();

    private static final String SAFETY_STATUS_KEY = "safety_status";
    private static final String ISSUES_GROUP_KEY = "issues_group";
    private static final String ENTRIES_GROUP_KEY = "entries_group";
    private static final String STATIC_ENTRIES_GROUP_KEY = "static_entries_group";

    @Nullable
    private final ViewModelProvider.Factory mSafetyCenterViewModelFactoryOverride;

    private SafetyStatusPreference mSafetyStatusPreference;
    private PreferenceGroup mIssuesGroup;
    private PreferenceGroup mEntriesGroup;
    private PreferenceGroup mStaticEntriesGroup;
    private SafetyCenterViewModel mViewModel;

    public SafetyCenterDashboardFragment() {
        this(null);
    }

    /**
     * Allows providing an override view model factory for testing this fragment. The view model
     * factory will not be retained between recreations of the fragment.
     */
    @VisibleForTesting
    public SafetyCenterDashboardFragment(
            @Nullable ViewModelProvider.Factory safetyCenterViewModelFactoryOverride) {
        mSafetyCenterViewModelFactoryOverride = safetyCenterViewModelFactoryOverride;
    }

    private ViewModelProvider.Factory getSafetyCenterViewModelFactory() {
        return mSafetyCenterViewModelFactoryOverride != null
                ? mSafetyCenterViewModelFactoryOverride
                : new LiveSafetyCenterViewModelFactory(requireActivity().getApplication());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.safety_center_dashboard, rootKey);

        mViewModel =
                new ViewModelProvider(requireActivity(), getSafetyCenterViewModelFactory())
                        .get(SafetyCenterViewModel.class);

        mSafetyStatusPreference =
                requireNonNull(getPreferenceScreen().findPreference(SAFETY_STATUS_KEY));
        // TODO: Use real strings here, or set more sensible defaults in the layout
        mSafetyStatusPreference.setSafetyStatus(
                new SafetyCenterStatus.Builder("Looks good", "")
                        .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                        .build());
        mSafetyStatusPreference.setRescanButtonOnClickListener(unused -> mViewModel.rescan());

        mIssuesGroup = getPreferenceScreen().findPreference(ISSUES_GROUP_KEY);
        mEntriesGroup = getPreferenceScreen().findPreference(ENTRIES_GROUP_KEY);
        mStaticEntriesGroup = getPreferenceScreen().findPreference(STATIC_ENTRIES_GROUP_KEY);

        mViewModel.getSafetyCenterLiveData().observe(this, this::renderSafetyCenterData);
        mViewModel.getErrorLiveData().observe(this, this::displayErrorDetails);
        getLifecycle().addObserver(mViewModel.getAutoRefreshManager());

        getPreferenceManager()
                .setPreferenceComparisonCallback(new SafetyPreferenceComparisonCallback());
    }

    private void renderSafetyCenterData(@Nullable SafetyCenterData data) {
        if (data == null) return;

        Log.i(TAG, String.format("renderSafetyCenterData called with: %s", data));

        Context context = getContext();
        if (context == null) {
            return;
        }

        mSafetyStatusPreference.setSafetyStatus(data.getStatus());
        mSafetyStatusPreference.setHasIssues(!data.getIssues().isEmpty());

        // TODO(b/208212820): Only update entries that have changed since last
        // update, rather than deleting and re-adding all.

        updateIssues(context, data.getIssues());
        updateSafetyEntries(context, data.getEntriesOrGroups());
        updateStaticSafetyEntries(context, data.getStaticEntryGroups());
    }

    private void displayErrorDetails(@Nullable SafetyCenterErrorDetails errorDetails) {
        Context context = getContext();
        if (errorDetails == null || context == null) return;
        Toast.makeText(context, errorDetails.getErrorMessage(), Toast.LENGTH_LONG).show();
        mViewModel.clearError();
    }

    private void updateIssues(Context context, List<SafetyCenterIssue> issues) {
        mIssuesGroup.removeAll();

        issues.stream()
                .map(issue -> new IssueCardPreference(context, mViewModel, issue))
                .forEachOrdered(mIssuesGroup::addPreference);
    }

    // TODO(b/208212820): Add groups and move to separate controller
    private void updateSafetyEntries(
            Context context, List<SafetyCenterEntryOrGroup> entriesOrGroups) {
        mEntriesGroup.removeAll();

        for (int i = 0, size = entriesOrGroups.size(); i < size; i++) {
            SafetyCenterEntryOrGroup entryOrGroup = entriesOrGroups.get(i);
            SafetyCenterEntry entry = entryOrGroup.getEntry();
            SafetyCenterEntryGroup group = entryOrGroup.getEntryGroup();

            boolean isFirstElement = i == 0;
            boolean isLastElement = i == size - 1;

            if (entry != null) {
                addTopLevelEntry(context, entry, isFirstElement, isLastElement);
            } else if (group != null) {
                addGroupEntries(context, group, isFirstElement, isLastElement);
            }
        }
    }

    private void addTopLevelEntry(
            Context context,
            SafetyCenterEntry entry,
            boolean isFirstElement,
            boolean isLastElement) {
        mEntriesGroup.addPreference(
                new SafetyEntryPreference(
                        context,
                        entry,
                        PositionInCardList.calculate(isFirstElement, isLastElement)));
    }

    private void addGroupEntries(
            Context context,
            SafetyCenterEntryGroup group,
            boolean isFirstCard,
            boolean isLastCard) {
        mEntriesGroup.addPreference(
                new SafetyGroupHeaderEntryPreference(
                        context,
                        group,
                        isFirstCard
                                ? PositionInCardList.LIST_START
                                : PositionInCardList.CARD_START));

        List<SafetyCenterEntry> entries = group.getEntries();
        for (int j = 0, last = entries.size() - 1; j <= last; j++) {
            boolean isCardEnd = j == last;
            boolean isListEnd = isLastCard && isCardEnd;
            PositionInCardList positionInCardList =
                    PositionInCardList.calculate(
                            /* isListStart= */ false,
                            isListEnd,
                            /* isCardStart= */ false,
                            isCardEnd);
            mEntriesGroup.addPreference(
                    new SafetyEntryPreference(context, entries.get(j), positionInCardList));
        }
    }

    private void updateStaticSafetyEntries(
            Context context, List<SafetyCenterStaticEntryGroup> staticEntryGroups) {
        mStaticEntriesGroup.removeAll();

        for (SafetyCenterStaticEntryGroup group : staticEntryGroups) {
            PreferenceCategory category = new PreferenceCategory(context);
            category.setTitle(group.getTitle());
            mStaticEntriesGroup.addPreference(category);

            for (SafetyCenterStaticEntry entry : group.getStaticEntries()) {
                category.addPreference(new StaticSafetyEntryPreference(context, entry));
            }
        }
    }

}
