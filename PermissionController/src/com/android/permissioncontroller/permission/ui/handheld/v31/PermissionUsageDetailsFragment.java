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

package com.android.permissioncontroller.permission.ui.handheld.v31;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.permissioncontroller.PermissionControllerApplication;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity;
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;
import com.android.permissioncontroller.permission.ui.model.v31.BasePermissionUsageDetailsViewModel;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.AppPermissionAccessUiInfo;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.PermissionUsageDetailsUiState;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.PermissionUsageDetailsViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** The permission details page showing the history/timeline of a permission */
@RequiresApi(Build.VERSION_CODES.S)
public class PermissionUsageDetailsFragment extends SettingsWithLargeHeader {
    private static final String KEY_SESSION_ID = "_session_id";
    private static final String SESSION_ID_KEY =
            PermissionUsageDetailsFragment.class.getName() + KEY_SESSION_ID;
    private static final String TAG = PermissionUsageDetailsFragment.class.getName();

    private static final int MENU_SHOW_7_DAYS_DATA = Menu.FIRST + 4;
    private static final int MENU_SHOW_24_HOURS_DATA = Menu.FIRST + 5;
    private String mPermissionGroup;
    private boolean mHasSystemApps;
    private boolean mMenuItemsCreated = false;

    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private MenuItem mShow7DaysDataMenu;
    private MenuItem mShow24HoursDataMenu;

    private BasePermissionUsageDetailsViewModel mViewModel;

    private long mSessionId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPermissionGroup = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermissionGroup == null) {
            Log.e(TAG, "No permission group was provided for PermissionDetailsFragment");
            return;
        }
        PermissionUsageDetailsViewModelFactory factory =
                new PermissionUsageDetailsViewModelFactory(
                        PermissionControllerApplication.get(), this, mPermissionGroup);
        mViewModel =
                new ViewModelProvider(this, factory).get(BasePermissionUsageDetailsViewModel.class);

        if (savedInstanceState != null) {
            mSessionId = savedInstanceState.getLong(SESSION_ID_KEY);
        } else {
            mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        }

        mViewModel.updateShowSystemAppsToggle(
                getArguments().getBoolean(ManagePermissionsActivity.EXTRA_SHOW_SYSTEM, false));
        mViewModel.updateShow7DaysToggle(
                KotlinUtils.INSTANCE.is7DayToggleEnabled()
                        && getArguments()
                                .getBoolean(ManagePermissionsActivity.EXTRA_SHOW_7_DAYS, false));

        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
        setLoading(true, false);

        mViewModel.getPermissionUsagesDetailsInfoUiLiveData().observe(this, this::updateAllUI);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView =
                (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        PermissionDetailsWrapperFragment parentFragment =
                (PermissionDetailsWrapperFragment) requireParentFragment();
        CoordinatorLayout coordinatorLayout = parentFragment.getCoordinatorLayout();
        inflater.inflate(R.layout.permission_details_extended_fab, coordinatorLayout);
        ExtendedFloatingActionButton extendedFab =
                coordinatorLayout.requireViewById(R.id.extended_fab);
        // Load the background tint color from the application theme
        // rather than the Material Design theme
        Activity activity = getActivity();
        ColorStateList backgroundColor =
                activity.getColorStateList(android.R.color.system_accent3_100);
        extendedFab.setBackgroundTintList(backgroundColor);
        extendedFab.setText(R.string.manage_permission);
        boolean isUiModeNight =
                (activity.getResources().getConfiguration().uiMode
                                & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        int textColorAttr =
                isUiModeNight
                        ? android.R.attr.textColorPrimaryInverse
                        : android.R.attr.textColorPrimary;
        TypedArray typedArray = activity.obtainStyledAttributes(new int[] {textColorAttr});
        ColorStateList textColor = typedArray.getColorStateList(0);
        typedArray.recycle();
        extendedFab.setTextColor(textColor);
        extendedFab.setIcon(activity.getDrawable(R.drawable.ic_settings_outline));
        extendedFab.setVisibility(View.VISIBLE);
        extendedFab.setOnClickListener(
                view -> {
                    Intent intent =
                            new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                                    .putExtra(Intent.EXTRA_PERMISSION_NAME, mPermissionGroup);
                    startActivity(intent);
                });
        RecyclerView recyclerView = getListView();
        int bottomPadding =
                getResources()
                        .getDimensionPixelSize(
                                R.dimen.privhub_details_recycler_view_bottom_padding);
        recyclerView.setPadding(0, 0, 0, bottomPadding);
        recyclerView.setClipToPadding(false);
        recyclerView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        CharSequence title = getString(R.string.permission_history_title);
        if (mPermissionGroup != null) {
            title =
                    getResources()
                            .getString(
                                    R.string.permission_group_usage_title,
                                    KotlinUtils.INSTANCE.getPermGroupLabel(
                                            requireActivity(), mPermissionGroup));
        }
        requireActivity().setTitle(title);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(SESSION_ID_KEY, mSessionId);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        mShowSystemMenu =
                menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE, R.string.menu_show_system);
        mHideSystemMenu =
                menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE, R.string.menu_hide_system);
        if (KotlinUtils.INSTANCE.is7DayToggleEnabled()) {
            mShow7DaysDataMenu =
                    menu.add(
                            Menu.NONE,
                            MENU_SHOW_7_DAYS_DATA,
                            Menu.NONE,
                            R.string.menu_show_7_days_data);
            mShow24HoursDataMenu =
                    menu.add(
                            Menu.NONE,
                            MENU_SHOW_24_HOURS_DATA,
                            Menu.NONE,
                            R.string.menu_show_24_hours_data);
            mMenuItemsCreated = true;
            updateShow7DaysToggle(mViewModel.getShow7Days());
        }
        mMenuItemsCreated = true;
        updateShowSystemToggle(mViewModel.getShowSystem());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                requireActivity().finishAfterTransition();
                return true;
            case MENU_SHOW_SYSTEM:
                mViewModel.updateShowSystemAppsToggle(true);
                break;
            case MENU_HIDE_SYSTEM:
                mViewModel.updateShowSystemAppsToggle(false);
                break;
            case MENU_SHOW_7_DAYS_DATA:
                mViewModel.updateShow7DaysToggle(KotlinUtils.INSTANCE.is7DayToggleEnabled());
                break;
            case MENU_SHOW_24_HOURS_DATA:
                mViewModel.updateShow7DaysToggle(false);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Updates page content and menu items. */
    private void updateAllUI(PermissionUsageDetailsUiState uiInfo) {
        if (getActivity() == null || uiInfo instanceof PermissionUsageDetailsUiState.Loading) {
            return;
        }
        PermissionUsageDetailsUiState.Success uiData =
                (PermissionUsageDetailsUiState.Success) uiInfo;
        Context context = getActivity();
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }
        screen.removeAll();
        boolean show7Days = mViewModel.getShow7Days();
        Preference subtitlePreference = new Preference(context);
        updateShow7DaysToggle(show7Days);
        int usageSubtitle = show7Days
                ? R.string.permission_group_usage_subtitle_7d
                : R.string.permission_group_usage_subtitle_24h;

        subtitlePreference.setSummary(
                getResources()
                        .getString(
                                usageSubtitle,
                                KotlinUtils.INSTANCE.getPermGroupLabel(
                                        context, mPermissionGroup)));
        subtitlePreference.setSelectable(false);
        screen.addPreference(subtitlePreference);

        boolean containsSystemAppAccesses = uiData.getContainsSystemAppUsage();
        if (mHasSystemApps != containsSystemAppAccesses) {
            mHasSystemApps = containsSystemAppAccesses;
        }
        updateShowSystemToggle(mViewModel.getShowSystem());

        // Make these variables effectively final so that
        // we can use these captured variables in the below lambda expression
        AtomicReference<PreferenceCategory> category =
                new AtomicReference<>(createDayCategoryPreference());
        screen.addPreference(category.get());

        renderHistoryPreferences(uiData.getAppPermissionAccessUiInfoList(), category, screen);
        setLoading(false, true);
        setProgressBarVisible(false);
    }

    /** Render the provided appPermissionAccessUiInfoList into the [preferenceScreen] UI. */
    private void renderHistoryPreferences(
            List<AppPermissionAccessUiInfo> appPermissionAccessUiInfoList,
            AtomicReference<PreferenceCategory> category,
            PreferenceScreen preferenceScreen) {
        Context context = requireContext();
        long midnightToday =
                ZonedDateTime.now(ZoneId.systemDefault())
                                .truncatedTo(ChronoUnit.DAYS)
                                .toEpochSecond()
                        * 1000L;
        long midnightYesterday =
                ZonedDateTime.now(ZoneId.systemDefault())
                                .minusDays(1)
                                .truncatedTo(ChronoUnit.DAYS)
                                .toEpochSecond()
                        * 1000L;

        long previousAccessDateMs = 0L;
        ZoneId zoneId = Clock.system(ZoneId.systemDefault()).getZone();

        for (int i = 0; i < appPermissionAccessUiInfoList.size(); i++) {
            AppPermissionAccessUiInfo appPermissionAccessUiInfo =
                    appPermissionAccessUiInfoList.get(i);
            long accessEndTime = appPermissionAccessUiInfo.getAccessEndTime();
            long accessDateMS =
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(accessEndTime), zoneId)
                            .truncatedTo(ChronoUnit.DAYS)
                            .toEpochSecond()
                            * 1000L;
            if (accessDateMS != previousAccessDateMs) {
                if (previousAccessDateMs != 0L) {
                    category.set(createDayCategoryPreference());
                    preferenceScreen.addPreference(category.get());
                }
                if (accessEndTime > midnightToday) {
                    category.get().setTitle(R.string.permission_history_category_today);
                } else if (accessEndTime > midnightYesterday) {
                    category.get().setTitle(R.string.permission_history_category_yesterday);
                } else {
                    category.get()
                            .setTitle(DateFormat.getLongDateFormat(context).format(accessDateMS));
                }
                previousAccessDateMs = accessDateMS;
            }

            Preference permissionUsagePreference =
                    new PermissionHistoryPreference(
                            context,
                            appPermissionAccessUiInfo.getUserHandle(),
                            appPermissionAccessUiInfo.getPackageName(),
                            appPermissionAccessUiInfo.getBadgedPackageIcon(),
                            appPermissionAccessUiInfo.getPackageLabel(),
                            appPermissionAccessUiInfo.getPermissionGroup(),
                            appPermissionAccessUiInfo.getAccessStartTime(),
                            appPermissionAccessUiInfo.getAccessEndTime(),
                            appPermissionAccessUiInfo.getSummaryText(),
                            appPermissionAccessUiInfo.getShowingAttribution(),
                            appPermissionAccessUiInfo.getAttributionTags(),
                            i == appPermissionAccessUiInfoList.size() - 1,
                            mSessionId,
                            appPermissionAccessUiInfo.isEmergencyLocationAccess());

            category.get().addPreference(permissionUsagePreference);
        }
    }

    private void updateShowSystemToggle(boolean showSystem) {
        if (!mMenuItemsCreated) return;

        if (mHasSystemApps) {
            mShowSystemMenu.setVisible(!showSystem);
            mShowSystemMenu.setEnabled(true);

            mHideSystemMenu.setVisible(showSystem);
            mHideSystemMenu.setEnabled(true);
        } else {
            mShowSystemMenu.setVisible(true);
            mShowSystemMenu.setEnabled(false);

            mHideSystemMenu.setVisible(false);
            mHideSystemMenu.setEnabled(false);
        }
    }

    private void updateShow7DaysToggle(boolean show7Days) {
        if (!mMenuItemsCreated) return;

        if (KotlinUtils.INSTANCE.is7DayToggleEnabled()) {
            mShow7DaysDataMenu.setVisible(!show7Days);
            mShow24HoursDataMenu.setVisible(show7Days);
        }
    }

    private PreferenceCategory createDayCategoryPreference() {
        PreferenceCategory category = new PreferenceCategory(getContext());
        // Do not reserve icon space, so that the text moves all the way left.
        category.setIconSpaceReserved(false);
        return category;
    }
}
