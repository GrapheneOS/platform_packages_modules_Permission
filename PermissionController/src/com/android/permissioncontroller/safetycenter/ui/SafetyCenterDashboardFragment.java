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

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.INVALID_TASK_ID;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT;

import static java.util.Objects.requireNonNull;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.LiveSafetyCenterViewModelFactory;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterUiData;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Dashboard fragment for the Safety Center. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterDashboardFragment extends PreferenceFragmentCompat {

    private static final String TAG = SafetyCenterDashboardFragment.class.getSimpleName();

    private static final String SAFETY_STATUS_KEY = "safety_status";
    private static final String ISSUES_GROUP_KEY = "issues_group";
    private static final String ENTRIES_GROUP_KEY = "entries_group";
    private static final String STATIC_ENTRIES_GROUP_KEY = "static_entries_group";

    @Nullable private final ViewModelProvider.Factory mSafetyCenterViewModelFactoryOverride;

    private SafetyStatusPreference mSafetyStatusPreference;
    private CollapsableIssuesCardHelper mCollapsableIssuesCardHelper;
    private PreferenceGroup mIssuesGroup;
    private PreferenceGroup mEntriesGroup;
    private PreferenceGroup mStaticEntriesGroup;
    private SafetyCenterViewModel mViewModel;
    private List<String> mSameTaskEntries;
    private boolean mIsQuickSettingsFragment;

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

    /**
     * Create instance of SafetyCenterDashboardFragment with the arguments set
     *
     * @param isQuickSettingsFragment Denoting if it is the quick settings fragment
     * @return SafetyCenterDashboardFragment with the arguments set
     */
    public static SafetyCenterDashboardFragment newInstance(
            long sessionId, boolean isQuickSettingsFragment) {
        Bundle args = new Bundle();
        args.putLong(EXTRA_SESSION_ID, sessionId);
        args.putBoolean(QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT, isQuickSettingsFragment);

        SafetyCenterDashboardFragment frag = new SafetyCenterDashboardFragment();
        frag.setArguments(args);
        return frag;
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        /* By default, the PreferenceGroupAdapter does setHasStableIds(true).
         * Since each Preference is internally allocated with an auto-incremented ID,
         * it does not allow us to gracefully update only changed preferences based on
         * SafetyPreferenceComparisonCallback.
         * In order to allow the list to track the changes we need to ignore the Preference IDs. */
        RecyclerView.Adapter adapter = super.onCreateAdapter(preferenceScreen);
        adapter.setHasStableIds(false);
        return adapter;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.safety_center_dashboard, rootKey);
        mSameTaskEntries = Arrays.asList(new SafetyCenterResourcesContext(getContext())
                .getStringByName("config_same_task_safety_source_ids").split(","));

        if (getArguments() != null) {
            mIsQuickSettingsFragment =
                    getArguments().getBoolean(QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT, false);
        }

        mViewModel =
                new ViewModelProvider(requireActivity(), getSafetyCenterViewModelFactory())
                        .get(SafetyCenterViewModel.class);

        mCollapsableIssuesCardHelper = new CollapsableIssuesCardHelper(mViewModel);
        ParsedSafetyCenterIntent parsedSafetyCenterIntent =
                ParsedSafetyCenterIntent.toSafetyCenterIntent(requireActivity().getIntent());
        mCollapsableIssuesCardHelper.setFocusedIssueKey(
                parsedSafetyCenterIntent.getSafetyCenterIssueKey());

        // Set quick settings state first and allow restored state to override if necessary
        mCollapsableIssuesCardHelper.setQuickSettingsState(
                mIsQuickSettingsFragment, parsedSafetyCenterIntent.getShouldExpandIssuesGroup());
        mCollapsableIssuesCardHelper.restoreState(savedInstanceState);

        mSafetyStatusPreference =
                requireNonNull(getPreferenceScreen().findPreference(SAFETY_STATUS_KEY));
        // TODO: Use real strings here, or set more sensible defaults in the layout
        mSafetyStatusPreference.setSafetyStatus(
                new SafetyCenterStatus.Builder("Looks good", "")
                        .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                        .build());
        mSafetyStatusPreference.setViewModel(mViewModel);

        mIssuesGroup = getPreferenceScreen().findPreference(ISSUES_GROUP_KEY);
        mEntriesGroup = getPreferenceScreen().findPreference(ENTRIES_GROUP_KEY);
        mStaticEntriesGroup = getPreferenceScreen().findPreference(STATIC_ENTRIES_GROUP_KEY);
        if (mIsQuickSettingsFragment) {
            getPreferenceScreen().removePreference(mEntriesGroup);
            mEntriesGroup = null;
            getPreferenceScreen().removePreference(mStaticEntriesGroup);
            mStaticEntriesGroup = null;
        }

        mViewModel.getSafetyCenterUiLiveData().observe(this, this::renderSafetyCenterData);
        mViewModel.getErrorLiveData().observe(this, this::displayErrorDetails);

        getPreferenceManager()
                .setPreferenceComparisonCallback(new SafetyPreferenceComparisonCallback());
    }

    @Override
    public void onStart() {
        super.onStart();

        configureInteractionLogger();
        mViewModel.getInteractionLogger().record(Action.SAFETY_CENTER_VIEWED);
    }

    @Override
    public void onResume() {
        super.onResume();
        mViewModel.pageOpen();
    }

    private void configureInteractionLogger() {
        InteractionLogger logger = mViewModel.getInteractionLogger();

        logger.setSessionId(
                requireArguments()
                        .getLong(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID));
        logger.setViewType(mIsQuickSettingsFragment ? ViewType.QUICK_SETTINGS : ViewType.FULL);

        Intent intent = requireActivity().getIntent();
        logger.setNavigationSource(NavigationSource.fromIntent(intent));
        logger.setNavigationSensor(Sensor.fromIntent(intent));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mCollapsableIssuesCardHelper.saveState(outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Activity activity = getActivity();
        if (activity != null && activity.isChangingConfigurations()) {
            mViewModel.changingConfigurations();
        }
    }

    SafetyCenterViewModel getSafetyCenterViewModel() {
        return mViewModel;
    }

    private void renderSafetyCenterData(@Nullable SafetyCenterUiData uiData) {
        if (uiData == null) return;
        SafetyCenterData data = uiData.getSafetyCenterData();

        Log.i(TAG, String.format("renderSafetyCenterData called with: %s", data));

        Context context = getContext();
        if (context == null) {
            return;
        }

        mSafetyStatusPreference.setSafetyData(data);

        // TODO(b/208212820): Only update entries that have changed since last
        // update, rather than deleting and re-adding all.
        updateIssues(context, data.getIssues(), uiData.getResolvedIssues());
        if (!mIsQuickSettingsFragment) {
            updateSafetyEntries(context, data.getEntriesOrGroups());
            updateStaticSafetyEntries(context, data.getStaticEntryGroups());
        } else {
            SafetyCenterResourcesContext safetyCenterResourcesContext =
                    new SafetyCenterResourcesContext(context);
            boolean hasSettingsToReview =
                    safetyCenterResourcesContext
                            .getStringByName("overall_severity_level_ok_review_summary")
                            .equals(data.getStatus().getSummary().toString());
            setPendingActionState(hasSettingsToReview);
        }
    }

    /** Determine if there are pending actions and set pending actions state */
    private void setPendingActionState(boolean hasSettingsToReview) {
        if (hasSettingsToReview) {
            mSafetyStatusPreference.setHasPendingActions(
                    true,
                    l -> {
                        mViewModel.navigateToSafetyCenter(
                                this, NavigationSource.QUICK_SETTINGS_TILE);
                        mViewModel.getInteractionLogger().record(Action.REVIEW_SETTINGS_CLICKED);
                    });
        } else {
            mSafetyStatusPreference.setHasPendingActions(false, null);
        }
    }

    private void displayErrorDetails(@Nullable SafetyCenterErrorDetails errorDetails) {
        Context context = getContext();
        if (errorDetails == null || context == null) return;
        Toast.makeText(context, errorDetails.getErrorMessage(), Toast.LENGTH_LONG).show();
        mViewModel.clearError();
    }

    private void updateIssues(
            Context context, List<SafetyCenterIssue> issues, Map<String, String> resolvedIssues) {
        mIssuesGroup.removeAll();
        mCollapsableIssuesCardHelper.addIssues(
                context,
                mViewModel,
                getChildFragmentManager(),
                mIssuesGroup,
                issues,
                resolvedIssues);
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
                        getTaskIdForEntry(entry),
                        entry,
                        PositionInCardList.calculate(isFirstElement, isLastElement),
                        mViewModel));
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
        for (int i = 0, last = entries.size() - 1; i <= last; i++) {
            boolean isCardEnd = i == last;
            boolean isListEnd = isLastCard && isCardEnd;
            PositionInCardList positionInCardList =
                    PositionInCardList.calculate(
                            /* isListStart= */ false,
                            isListEnd,
                            /* isCardStart= */ false,
                            isCardEnd);
            mEntriesGroup.addPreference(
                    new SafetyEntryPreference(
                            context,
                            getTaskIdForEntry(entries.get(i)),
                            entries.get(i),
                            positionInCardList,
                            mViewModel));
        }
    }

    private void updateStaticSafetyEntries(
            Context context, List<SafetyCenterStaticEntryGroup> staticEntryGroups) {
        mStaticEntriesGroup.removeAll();

        for (SafetyCenterStaticEntryGroup group : staticEntryGroups) {
            PreferenceCategory category = new ComparablePreferenceCategory(context);
            category.setTitle(group.getTitle());
            mStaticEntriesGroup.addPreference(category);

            for (SafetyCenterStaticEntry entry : group.getStaticEntries()) {
                category.addPreference(
                        new StaticSafetyEntryPreference(
                                context, requireActivity().getTaskId(), entry, mViewModel));
            }
        }
    }

    private int getTaskIdForEntry(SafetyCenterEntry entry) {
        String issueId = SafetyCenterIds.entryIdFromString(entry.getId()).getSafetySourceId();
        return mSameTaskEntries.contains(issueId) ? requireActivity().getTaskId() : INVALID_TASK_ID;
    }
}
