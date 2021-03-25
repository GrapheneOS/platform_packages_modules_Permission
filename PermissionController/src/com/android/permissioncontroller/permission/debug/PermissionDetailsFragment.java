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

package com.android.permissioncontroller.permission.debug;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.ArraySet;
import android.util.Pair;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps;
import com.android.permissioncontroller.permission.ui.handheld.PermissionGroupPreference;
import com.android.permissioncontroller.permission.ui.handheld.PermissionHistoryPreference;
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The permission details page showing the history/timeline of a permission
 */
public class PermissionDetailsFragment extends SettingsWithLargeHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback {
    public static final int FILTER_24_HOURS = 2;

    private @Nullable String mFilterGroup;
    private @Nullable List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();
    private @NonNull List<TimeFilterItem> mFilterTimes;
    private int mFilterTimeIndex;
    private @NonNull PermissionUsages mPermissionUsages;
    private boolean mFinishedInitialLoad;

    /**
     * Construct a new instance of PermissionDetailsFragment
     */
    public static @NonNull PermissionDetailsFragment newInstance(@Nullable String groupName,
            long numMillis) {
        PermissionDetailsFragment fragment = new PermissionDetailsFragment();
        Bundle arguments = new Bundle();
        if (groupName != null) {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putLong(Intent.EXTRA_DURATION_MILLIS, numMillis);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFinishedInitialLoad = false;
        initializeTimeFilter();
        mFilterTimeIndex = FILTER_24_HOURS;

        if (mFilterGroup == null) {
            mFilterGroup = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        }

        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        Context context = getPreferenceManager().getContext();

        mPermissionUsages = new PermissionUsages(context);
        reloadData();
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(R.string.permission_history_title);
    }

    @Override
    public void onPermissionUsagesChanged() {
        if (mPermissionUsages.getUsages().isEmpty()) {
            return;
        }
        mAppPermissionUsages = new ArrayList<>(mPermissionUsages.getUsages());

        // Ensure the group name is valid.
        if (getGroup(mFilterGroup) == null) {
            mFilterGroup = null;
        }

        updateUI();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateUI() {
        if (mAppPermissionUsages.isEmpty() || getActivity() == null) {
            return;
        }
        Context context = getActivity();
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }
        screen.removeAll();

        final TimeFilterItem timeFilterItem = mFilterTimes.get(mFilterTimeIndex);
        long curTime = System.currentTimeMillis();
        long startTime = Math.max(timeFilterItem == null ? 0 : (curTime - timeFilterItem.getTime()),
                0);

        PermissionGroupPreference permissionPreference = new PermissionGroupPreference(context,
                getResources(), mFilterGroup);
        screen.addPreference(permissionPreference);

        List<Pair<AppPermissionUsage, Long>> usages = new ArrayList<>();
        ArrayList<PermissionApps.PermissionApp> permApps = new ArrayList<>();
        int numApps = mAppPermissionUsages.size();
        for (int appNum = 0; appNum < numApps; appNum++) {
            AppPermissionUsage appUsage = mAppPermissionUsages.get(appNum);
            boolean used = false;
            List<AppPermissionUsage.GroupUsage> appGroups = appUsage.getGroupUsages();
            int numGroups = appGroups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                AppPermissionUsage.GroupUsage groupUsage = appGroups.get(groupNum);
                if (!groupUsage.hasDiscreteData()) {
                    continue;
                }

                List<Long> allDiscreteAccessTime = groupUsage.getAllDiscreteAccessTime();
                for (Long discreteAccessTime : allDiscreteAccessTime) {
                    if (discreteAccessTime == 0) {
                        continue;
                    }
                    if (discreteAccessTime < startTime) {
                        continue;
                    }

                    used = true;
                    // identify whether the group matches the group for the page
                    if (groupUsage.getGroup().getName().equals(mFilterGroup)) {
                        usages.add(Pair.create(appUsage, discreteAccessTime));
                    }
                }
            }

            if (used) {
                permApps.add(appUsage.getApp());
            }

        }

        usages.sort(PermissionDetailsFragment::compareAppUsageByTime);

        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);

        new PermissionApps.AppDataLoader(context, () -> {
            final int numUsages = usages.size();
            for (int usageNum = 0; usageNum < numUsages; usageNum++) {
                final Pair<AppPermissionUsage, Long> usage = usages.get(usageNum);
                String accessTime = UtilsKt.getAbsoluteTimeString(context, usage.second);

                PermissionHistoryPreference permissionUsagePreference =
                        new PermissionHistoryPreference(context, accessTime,
                                usage.first.getApp().getIcon());
                permissionUsagePreference.setTitle(usage.first.getApp().getLabel());

                category.addPreference(permissionUsagePreference);
            }

            setLoading(false, true);
            mFinishedInitialLoad = true;
            setProgressBarVisible(false);
            mPermissionUsages.stopLoader(getActivity().getLoaderManager());

        }).execute(permApps.toArray(new PermissionApps.PermissionApp[permApps.size()]));
    }

    private static int compareAppUsageByTime(Pair<AppPermissionUsage, Long> x,
            Pair<AppPermissionUsage, Long> y) {
        return y.second.compareTo(x.second);
    }

    /**
     * Get an AppPermissionGroup that represents the given permission group (and an arbitrary app).
     *
     * @param groupName The name of the permission group.
     *
     * @return an AppPermissionGroup rerepsenting the given permission group or null if no such
     * AppPermissionGroup is found.
     */
    private @Nullable AppPermissionGroup getGroup(@NonNull String groupName) {
        List<AppPermissionGroup> groups = getOSPermissionGroups();
        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            if (groups.get(i).getName().equals(groupName)) {
                return groups.get(i);
            }
        }
        return null;
    }

    /**
     * Get the permission groups declared by the OS.
     *
     * TODO: theianchen change the method name to make that clear,
     * and return a list of string group names, not AppPermissionGroups.
     * @return a list of the permission groups declared by the OS.
     */
    private @NonNull List<AppPermissionGroup> getOSPermissionGroups() {
        final List<AppPermissionGroup> groups = new ArrayList<>();
        final Set<String> seenGroups = new ArraySet<>();
        final int numGroups = mAppPermissionUsages.size();
        for (int i = 0; i < numGroups; i++) {
            final AppPermissionUsage appUsage = mAppPermissionUsages.get(i);
            final List<AppPermissionUsage.GroupUsage> groupUsages = appUsage.getGroupUsages();
            final int groupUsageCount = groupUsages.size();
            for (int j = 0; j < groupUsageCount; j++) {
                final AppPermissionUsage.GroupUsage groupUsage = groupUsages.get(j);
                if (Utils.isModernPermissionGroup(groupUsage.getGroup().getName())) {
                    if (seenGroups.add(groupUsage.getGroup().getName())) {
                        groups.add(groupUsage.getGroup());
                    }
                }
            }
        }
        return groups;
    }

    private void reloadData() {
        final TimeFilterItem timeFilterItem = mFilterTimes.get(mFilterTimeIndex);
        final long filterTimeBeginMillis = Math.max(System.currentTimeMillis()
                - timeFilterItem.getTime(), 0);
        mPermissionUsages.load(null /*filterPackageName*/, null /*filterPermissionGroups*/,
                filterTimeBeginMillis, Long.MAX_VALUE, PermissionUsages.USAGE_FLAG_LAST
                        | PermissionUsages.USAGE_FLAG_HISTORICAL, getActivity().getLoaderManager(),
                false /*getUiInfo*/, false /*getNonPlatformPermissions*/, this /*callback*/,
                false /*sync*/);
        if (mFinishedInitialLoad) {
            setProgressBarVisible(true);
        }
    }

    /**
     * Initialize the time filter to show the smallest entry greater than the time passed in as an
     * argument.  If nothing is passed, this simply initializes the possible values.
     */
    private void initializeTimeFilter() {
        Context context = getPreferenceManager().getContext();
        mFilterTimes = new ArrayList<>();
        mFilterTimes.add(new TimeFilterItem(Long.MAX_VALUE,
                context.getString(R.string.permission_usage_any_time)));
        mFilterTimes.add(new TimeFilterItem(DAYS.toMillis(7),
                context.getString(R.string.permission_usage_last_7_days)));
        mFilterTimes.add(new TimeFilterItem(DAYS.toMillis(1),
                context.getString(R.string.permission_usage_last_day)));
        mFilterTimes.add(new TimeFilterItem(HOURS.toMillis(1),
                context.getString(R.string.permission_usage_last_hour)));
        mFilterTimes.add(new TimeFilterItem(MINUTES.toMillis(15),
                context.getString(R.string.permission_usage_last_15_minutes)));
        mFilterTimes.add(new TimeFilterItem(MINUTES.toMillis(1),
                context.getString(R.string.permission_usage_last_minute)));

        // TODO: theianchen add code for filtering by time here.
    }

    /**
     * A class representing a given time, e.g., "in the last hour".
     */
    private static class TimeFilterItem {
        private final long mTime;
        private final @NonNull String mLabel;

        TimeFilterItem(long time, @NonNull String label) {
            mTime = time;
            mLabel = label;
        }

        /**
         * Get the time represented by this object in milliseconds.
         *
         * @return the time represented by this object.
         */
        public long getTime() {
            return mTime;
        }

        public @NonNull String getLabel() {
            return mLabel;
        }
    }
}
