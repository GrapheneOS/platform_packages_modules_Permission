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

import android.Manifest.permission_group;
import android.app.ActionBar;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The permission details page showing the history/timeline of a permission
 */
public class PermissionDetailsFragment extends SettingsWithLargeHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback {
    public static final int FILTER_24_HOURS = 2;

    private static final List<String> ALLOW_CLUSTERING_PERMISSION_GROUPS = Arrays.asList(
            permission_group.LOCATION, permission_group.CAMERA, permission_group.MICROPHONE
    );
    private static final int ONE_HOUR_MS = 3600000;
    private static final int ONE_MINUTE_MS = 60000;
    private static final int CLUSTER_MINUTES_APART = 1;

    private static final String KEY_SHOW_SYSTEM_PREFS = "_show_system";
    private static final String SHOW_SYSTEM_KEY = PermissionUsageFragment.class.getName()
            + KEY_SHOW_SYSTEM_PREFS;

    private @Nullable String mFilterGroup;
    private @Nullable List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();
    private @NonNull List<TimeFilterItem> mFilterTimes;
    private int mFilterTimeIndex;
    private @NonNull PermissionUsages mPermissionUsages;
    private boolean mFinishedInitialLoad;

    private boolean mShowSystem;
    private boolean mHasSystemApps;

    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private @NonNull RoleManager mRoleManager;

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

        mShowSystem = false;
        if (savedInstanceState != null) {
            mShowSystem = savedInstanceState.getBoolean(SHOW_SYSTEM_KEY);
        }

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
        mRoleManager = Utils.getSystemServiceSafe(context, RoleManager.class);

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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_SYSTEM_KEY, mShowSystem);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mHasSystemApps) {
            mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                    R.string.menu_show_system);
            mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                    R.string.menu_hide_system);
        }

        updateMenu();
    }

    private void updateMenu() {
        if (mHasSystemApps) {
            mShowSystemMenu.setVisible(!mShowSystem);
            mHideSystemMenu.setVisible(mShowSystem);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().finish();
                return true;
            case MENU_SHOW_SYSTEM:
            case MENU_HIDE_SYSTEM:
                mShowSystem = item.getItemId() == MENU_SHOW_SYSTEM;
                // We already loaded all data, so don't reload
                updateUI();
                updateMenu();
                break;
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

        Set<String> exemptedPackages = Utils.getExemptedPackages(mRoleManager);

        PermissionGroupPreference permissionPreference = new PermissionGroupPreference(context,
                getResources(), mFilterGroup);
        screen.addPreference(permissionPreference);

        AtomicBoolean seenSystemApp = new AtomicBoolean(false);

        ArrayList<PermissionApps.PermissionApp> permApps = new ArrayList<>();
        List<AppPermissionUsageEntry> usages = mAppPermissionUsages.stream()
                .filter(appUsage -> !exemptedPackages.contains(appUsage.getPackageName()))
                .map(appUsage -> {
            // Fetch the access time list of the app accesses mFilterGroup permission group
            // The DiscreteAccessTime is a Pair of (access time, access duration) of that app
            List<Pair<Long, Long>> discreteAccessTimeList = new ArrayList<>();
            List<AppPermissionUsage.GroupUsage> appGroups = appUsage.getGroupUsages();
            int numGroups = appGroups.size();
            for (int groupIndex = 0; groupIndex < numGroups; groupIndex++) {
                AppPermissionUsage.GroupUsage groupUsage = appGroups.get(groupIndex);
                if (!groupUsage.getGroup().getName().equals(mFilterGroup)
                        || !groupUsage.hasDiscreteData()) {
                    continue;
                }

                final boolean isSystemApp = !Utils.isGroupOrBgGroupUserSensitive(
                        groupUsage.getGroup());
                seenSystemApp.set(seenSystemApp.get() || isSystemApp);
                if (isSystemApp && !mShowSystem) {
                    continue;
                }

                List<Pair<Long, Long>> allDiscreteAccessTime = groupUsage
                        .getAllDiscreteAccessTime();
                int numAllDiscreteAccessTime = allDiscreteAccessTime.size();
                for (int discreteAccessTimeIndex = 0;
                        discreteAccessTimeIndex < numAllDiscreteAccessTime;
                        discreteAccessTimeIndex++) {
                    Pair<Long, Long> discreteAccessTime = allDiscreteAccessTime
                            .get(discreteAccessTimeIndex);
                    if (discreteAccessTime.first == 0 || discreteAccessTime.first < startTime) {
                        continue;
                    }

                    discreteAccessTimeList.add(discreteAccessTime);
                }
            }

            Collections.sort(discreteAccessTimeList, (x, y) -> y.first.compareTo(x.first));

            if (discreteAccessTimeList.size() > 0) {
                permApps.add(appUsage.getApp());
            }

            // If the current permission group is not LOCATION or there's only one access for
            // the app, return individual entry early.
            if (!ALLOW_CLUSTERING_PERMISSION_GROUPS.contains(mFilterGroup)
                    || discreteAccessTimeList.size() <= 1) {
                return discreteAccessTimeList.stream().map(
                    time -> new AppPermissionUsageEntry(appUsage, time.first,
                        Collections.singletonList(time))).collect(Collectors.toList());
            }

            // Group access time list
            List<AppPermissionUsageEntry> usageEntries = new ArrayList<>();
            AppPermissionUsageEntry ongoingEntry = null;
            for (Pair<Long, Long> time : discreteAccessTimeList) {
                if (ongoingEntry == null) {
                    ongoingEntry = new AppPermissionUsageEntry(appUsage, time.first,
                        Stream.of(time).collect(Collectors.toCollection(ArrayList::new)));
                } else {
                    List<Pair<Long, Long>> ongoingAccessTimeList =
                            ongoingEntry.mClusteredAccessTimeList;
                    if (time.first / ONE_HOUR_MS
                            != ongoingAccessTimeList.get(0).first / ONE_HOUR_MS
                            || ongoingAccessTimeList.get(ongoingAccessTimeList.size() - 1).first
                            / ONE_MINUTE_MS - time.first / ONE_MINUTE_MS
                            > CLUSTER_MINUTES_APART) {
                        // If the current access time is not in the same hour nor within
                        // CLUSTER_MINUTES_APART, add the ongoing entry to the usage list and start
                        // a new ongoing entry.
                        usageEntries.add(ongoingEntry);
                        ongoingEntry = new AppPermissionUsageEntry(appUsage, time.first,
                            Stream.of(time).collect(Collectors.toCollection(ArrayList::new)));
                    } else {
                        ongoingAccessTimeList.add(time);
                        ongoingEntry.mStartTime = time.first;
                    }
                }
            }
            usageEntries.add(ongoingEntry);

            return usageEntries;
        }).flatMap(Collection::stream).sorted((x, y) -> {
            // Sort all usage entries by startTime desc, and then by app name.
            int timeCompare = Long.compare(y.mStartTime, x.mStartTime);
            if (timeCompare != 0) {
                return timeCompare;
            }
            return x.mAppPermissionUsage.getApp().getLabel().compareTo(
                    y.mAppPermissionUsage.getApp().getLabel());
        }).collect(Collectors.toList());

        if (mHasSystemApps != seenSystemApp.get()) {
            mHasSystemApps = seenSystemApp.get();
            getActivity().invalidateOptionsMenu();
        }

        long midnightToday = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
        AppPermissionUsageEntry midnightTodayEntry = new AppPermissionUsageEntry(
                null, midnightToday, null);

        // Use the placeholder pair midnightTodayPair to get
        // the index of the first usage entry from yesterday
        int todayCategoryIndex = 0;
        int yesterdayCategoryIndex = Collections.binarySearch(usages,
                midnightTodayEntry, (e1, e2) -> Long.compare(e2.getStartTime(), e1.getStartTime()));
        if (yesterdayCategoryIndex < 0) {
            yesterdayCategoryIndex = -1 * (yesterdayCategoryIndex + 1);
        }

        // Make these variables effectively final so that
        // we can use these captured variables in the below lambda expression
        AtomicReference<PreferenceCategory> category = new AtomicReference<>(
                new PreferenceCategory(context));
        screen.addPreference(category.get());
        PreferenceScreen finalScreen = screen;
        int finalYesterdayCategoryIndex = yesterdayCategoryIndex;

        new PermissionApps.AppDataLoader(context, () -> {
            final int numUsages = usages.size();
            for (int usageNum = 0; usageNum < numUsages; usageNum++) {
                AppPermissionUsageEntry usage = usages.get(usageNum);
                if (finalYesterdayCategoryIndex == usageNum) {
                    if (finalYesterdayCategoryIndex != todayCategoryIndex) {
                        // We create a new category only when we need it.
                        // We will not create a new category if we only need one category for
                        // either today's or yesterday's usage
                        category.set(new PreferenceCategory(context));
                        finalScreen.addPreference(category.get());
                    }
                    category.get().setTitle(R.string.permission_history_category_yesterday);
                } else if (todayCategoryIndex == usageNum) {
                    category.get().setTitle(R.string.permission_history_category_today);
                }

                String accessTime = DateFormat.getTimeFormat(context).format(usage.mStartTime);
                Long accessDurationLong = usage.mClusteredAccessTimeList
                        .stream()
                        .map(p -> p.second)
                        .filter(dur -> dur > 0)
                        .reduce(0L, (dur1, dur2) -> dur1 + dur2);

                String accessDuration = null;
                if (accessDurationLong > 0) {
                    accessDuration = UtilsKt.getDurationUsedStr(context, accessDurationLong);
                }

                List<Long> accessTimeList = usage.mClusteredAccessTimeList
                        .stream().map(p -> p.first).collect(Collectors.toList());
                ArrayList<String> attributionTags =
                        usage.mAppPermissionUsage.getGroupUsages().stream().filter(groupUsage ->
                                groupUsage.getGroup().getName().equals(mFilterGroup)).map(
                                AppPermissionUsage.GroupUsage::getAttributionTags).filter(
                                Objects::nonNull).flatMap(Collection::stream).collect(
                                Collectors.toCollection(ArrayList::new));
                PermissionHistoryPreference permissionUsagePreference = new
                        PermissionHistoryPreference(context, usage.mAppPermissionUsage,
                        mFilterGroup, accessTime, accessDuration, accessTimeList, attributionTags);

                category.get().addPreference(permissionUsagePreference);
            }

            setLoading(false, true);
            mFinishedInitialLoad = true;
            setProgressBarVisible(false);
            mPermissionUsages.stopLoader(getActivity().getLoaderManager());

        }).execute(permApps.toArray(new PermissionApps.PermissionApp[permApps.size()]));
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

    /**
     * A class representing an app usage entry in Permission Usage.
     */
    private static class AppPermissionUsageEntry {
        private final AppPermissionUsage mAppPermissionUsage;
        private final List<Pair<Long, Long>> mClusteredAccessTimeList;
        private long mStartTime;

        AppPermissionUsageEntry(AppPermissionUsage appPermissionUsage, long startTime,
                List<Pair<Long, Long>> clusteredAccessTimeList) {
            mAppPermissionUsage = appPermissionUsage;
            mStartTime = startTime;
            mClusteredAccessTimeList = clusteredAccessTimeList;
        }

        public AppPermissionUsage getAppPermissionUsage() {
            return mAppPermissionUsage;
        }

        public long getStartTime() {
            return mStartTime;
        }

        public List<Pair<Long, Long>> getAccessTime() {
            return mClusteredAccessTimeList;
        }
    }
}
