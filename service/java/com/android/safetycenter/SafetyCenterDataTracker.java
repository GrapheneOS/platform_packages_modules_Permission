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

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Collections.emptyList;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.annotation.UserIdInt;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterEntry;
import android.safetycenter.SafetyCenterEntryGroup;
import android.safetycenter.SafetyCenterEntryOrGroup;
import android.safetycenter.SafetyCenterIssue;
import android.safetycenter.SafetyCenterStaticEntry;
import android.safetycenter.SafetyCenterStaticEntryGroup;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.SafetySourceStatus;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.permission.util.UserUtils;
import com.android.safetycenter.SafetyCenterConfigReader.Config;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A class that keeps track of all the {@link SafetySourceData} set by safety sources, and
 * aggregates them into a {@link SafetyCenterData} object to be used by PermissionController.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
final class SafetyCenterDataTracker {

    private static final String TAG = "SafetyCenterDataTracker";

    private final ArrayMap<Key, SafetySourceData> mSafetySourceDataForKey = new ArrayMap<>();

    @NonNull
    private final Context mContext;
    @NonNull
    private final SafetyCenterResourcesContext mSafetyCenterResourcesContext;

    /**
     * Creates a {@link SafetyCenterDataTracker} using the given {@link Context} and {@link
     * SafetyCenterResourcesContext}.
     */
    SafetyCenterDataTracker(
            @NonNull Context context,
            @NonNull SafetyCenterResourcesContext safetyCenterResourcesContext) {
        mContext = context;
        mSafetyCenterResourcesContext = safetyCenterResourcesContext;
    }

    /**
     * Sets the latest {@link SafetySourceData} for the given {@code safetySourceId} and {@code
     * userId}, and returns whether there was a change to the underlying {@link SafetyCenterData}
     * against the given {@link Config}.
     *
     * <p>Throws if the request is invalid based on the Safety Center config: the given {@code
     * safetySourceId}, {@code packageName} and {@code userId} are unexpected; or the {@link
     * SafetySourceData} does not respect all constraints defined in the config.
     *
     * <p>Setting a {@code null} {@link SafetySourceData} evicts the current {@link
     * SafetySourceData} entry.
     */
    boolean setSafetySourceData(
            @NonNull Config config,
            @Nullable SafetySourceData safetySourceData,
            @NonNull String safetySourceId,
            @NonNull String packageName,
            @UserIdInt int userId) {
        validateRequest(config, safetySourceData, safetySourceId, packageName, userId);

        Key key = Key.of(safetySourceId, userId);
        SafetySourceData existingSafetySourceData = mSafetySourceDataForKey.get(key);
        if (Objects.equals(safetySourceData, existingSafetySourceData)) {
            return false;
        }

        if (safetySourceData == null) {
            mSafetySourceDataForKey.remove(key);
        } else {
            mSafetySourceDataForKey.put(key, safetySourceData);
        }

        return true;
    }

    /**
     * Returns the latest {@link SafetySourceData} that was set by {@link #setSafetySourceData} for
     * the given {@code safetySourceId} and {@code userId} against the given {@link Config}.
     *
     * <p>Throws if the request is invalid based on the Safety Center config: the given {@code
     * safetySourceId}, {@code packageName} and {@code userId} are unexpected.
     *
     * <p>Returns {@code null} if it was never set since boot, or if the entry was evicted using
     * {@link #setSafetySourceData} with a {@code null} value.
     */
    @Nullable
    SafetySourceData getSafetySourceData(
            @NonNull Config config,
            @NonNull String safetySourceId,
            @NonNull String packageName,
            @UserIdInt int userId) {
        validateRequest(config, null, safetySourceId, packageName, userId);
        return mSafetySourceDataForKey.get(Key.of(safetySourceId, userId));
    }

    /** Clears all the {@link SafetySourceData} set received so far, for all users. */
    void clear() {
        mSafetySourceDataForKey.clear();
    }

    /**
     * Returns the current {@link SafetyCenterData} for the given {@link UserProfileGroup},
     * aggregated from all the {@link SafetySourceData} set so far against the given {@link Config}.
     *
     * <p>Returns an arbitrary default value if the {@link SafetyCenterConfig} is not available.
     *
     * <p>If a {@link SafetySourceData} was not set, the default value from the {@link
     * SafetyCenterConfig} is used.
     */
    @NonNull
    SafetyCenterData getSafetyCenterData(
            @NonNull Config config, @NonNull UserProfileGroup userProfileGroup) {
        return getSafetyCenterData(config.getSafetySourcesGroups(), userProfileGroup);
    }

    /**
     * Returns a default {@link SafetyCenterData} object to be returned when the API is disabled.
     */
    @NonNull
    static SafetyCenterData getDefaultSafetyCenterData() {
        return new SafetyCenterData(
                new SafetyCenterStatus.Builder()
                        .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN)
                        .setTitle(getSafetyCenterStatusTitle(
                                SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN))
                        .setSummary(
                                getSafetyCenterStatusSummary(
                                        SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN))
                        .build(),
                emptyList(),
                emptyList(),
                emptyList());
    }

    private void validateRequest(
            @NonNull Config config,
            @Nullable SafetySourceData safetySourceData,
            @NonNull String safetySourceId,
            @NonNull String packageName,
            @UserIdInt int userId) {
        SafetySource safetySource = config.getExternalSafetySources().get(safetySourceId);
        if (safetySource == null) {
            throw new IllegalArgumentException(
                    String.format("Unexpected safety source \"%s\"", safetySourceId));
        }

        // TODO(b/222330089): Security: check certs?
        if (!packageName.equals(safetySource.getPackageName())) {
            throw new IllegalArgumentException(
                    String.format(
                            "Unexpected package name \"%s\" for safety source \"%s\"",
                            packageName, safetySourceId));
        }

        // TODO(b/222327845)): Security: check package is installed for user?

        if (UserUtils.isManagedProfile(userId, mContext)
                && !SafetySources.supportsManagedProfiles(safetySource)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Unexpected managed profile request for safety source \"%s\"",
                            safetySourceId));
        }

        boolean retrievingOrClearingData = safetySourceData == null;
        if (retrievingOrClearingData) {
            return;
        }

        if (safetySource.getType() == SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY
                && safetySourceData.getStatus() != null) {
            throw new IllegalArgumentException(
                    String.format("Unexpected status for issue only safety source \"%s\"",
                            safetySourceId));
        }

        if (safetySource.getType() == SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC
                && safetySourceData.getStatus() == null) {
            throw new IllegalArgumentException(
                    String.format("Missing status for dynamic safety source \"%s\"",
                            safetySourceId));
        }

        int maxSeverityLevel =
                safetySourceData.getStatus() != null
                        ? safetySourceData.getStatus().getSeverityLevel()
                        : Integer.MIN_VALUE;
        for (int i = 0; i < safetySourceData.getIssues().size(); i++) {
            maxSeverityLevel =
                    Math.max(maxSeverityLevel,
                            safetySourceData.getIssues().get(i).getSeverityLevel());
        }
        if (maxSeverityLevel > safetySource.getMaxSeverityLevel()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Unexpected max severity level \"%d\" for safety source \"%s\"",
                            maxSeverityLevel, safetySourceId));
        }
    }

    @NonNull
    private SafetyCenterData getSafetyCenterData(
            @NonNull List<SafetySourcesGroup> safetySourcesGroups,
            @NonNull UserProfileGroup userProfileGroup) {
        int maxSafetyCenterEntryLevel = SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
        List<SafetyCenterIssue> safetyCenterIssues = new ArrayList<>();
        List<SafetyCenterEntryOrGroup> safetyCenterEntryOrGroups = new ArrayList<>();
        List<SafetyCenterStaticEntryGroup> safetyCenterStaticEntryGroups = new ArrayList<>();

        for (int i = 0; i < safetySourcesGroups.size(); i++) {
            SafetySourcesGroup safetySourcesGroup = safetySourcesGroups.get(i);

            int groupSafetyCenterEntryLevel = SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
            switch (safetySourcesGroup.getType()) {
                case SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE: {
                    groupSafetyCenterEntryLevel =
                            Math.max(
                                    addSafetyCenterIssues(safetyCenterIssues, safetySourcesGroup,
                                            userProfileGroup),
                                    addSafetyCenterEntryGroup(
                                            safetyCenterEntryOrGroups, safetySourcesGroup,
                                            userProfileGroup));
                    break;
                }
                case SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_RIGID: {
                    addSafetyCenterStaticEntryGroup(
                            safetyCenterStaticEntryGroups, safetySourcesGroup, userProfileGroup);
                    break;
                }
                case SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN: {
                    groupSafetyCenterEntryLevel =
                            addSafetyCenterIssues(safetyCenterIssues, safetySourcesGroup,
                                    userProfileGroup);
                    break;
                }
            }
            maxSafetyCenterEntryLevel = Math.max(maxSafetyCenterEntryLevel,
                    groupSafetyCenterEntryLevel);
        }

        // TODO(b/223349473): Reorder safetyCenterIssues based on some criteria.
        int safetyCenterOverallSeverityLevel =
                entryToSafetyCenterStatusOverallLevel(maxSafetyCenterEntryLevel);
        return new SafetyCenterData(
                new SafetyCenterStatus.Builder()
                        .setSeverityLevel(safetyCenterOverallSeverityLevel)
                        .setTitle(getSafetyCenterStatusTitle(safetyCenterOverallSeverityLevel))
                        .setSummary(getSafetyCenterStatusSummary(safetyCenterOverallSeverityLevel))
                        .build(),
                safetyCenterIssues,
                safetyCenterEntryOrGroups,
                safetyCenterStaticEntryGroups);
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private int addSafetyCenterIssues(
            @NonNull List<SafetyCenterIssue> safetyCenterIssues,
            @NonNull SafetySourcesGroup safetySourcesGroup,
            @NonNull UserProfileGroup userProfileGroup) {
        int maxSafetyCenterEntrySeverityLevel = SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        for (int i = 0; i < safetySources.size(); i++) {
            SafetySource safetySource = safetySources.get(i);

            if (!SafetySources.isExternal(safetySource)) {
                continue;
            }

            maxSafetyCenterEntrySeverityLevel =
                    Math.max(
                            maxSafetyCenterEntrySeverityLevel,
                            addSafetyCenterIssues(
                                    safetyCenterIssues, safetySource,
                                    userProfileGroup.getProfileOwnerUserId()));

            if (!SafetySources.supportsManagedProfiles(safetySource)) {
                continue;
            }

            int[] managedProfileUserIds = userProfileGroup.getManagedProfilesUserIds();
            for (int j = 0; j < managedProfileUserIds.length; j++) {
                int managedProfileUserId = managedProfileUserIds[i];

                maxSafetyCenterEntrySeverityLevel =
                        Math.max(
                                maxSafetyCenterEntrySeverityLevel,
                                addSafetyCenterIssues(safetyCenterIssues, safetySource,
                                        managedProfileUserId));
            }
        }

        return maxSafetyCenterEntrySeverityLevel;
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private int addSafetyCenterIssues(
            @NonNull List<SafetyCenterIssue> safetyCenterIssues,
            @NonNull SafetySource safetySource,
            @UserIdInt int userId) {
        Key key = Key.of(safetySource.getId(), userId);
        SafetySourceData safetySourceData = mSafetySourceDataForKey.get(key);

        if (safetySourceData == null) {
            return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
        }

        int maxSafetyCenterEntrySeverityLevel = SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;

        List<SafetySourceIssue> safetySourceIssues = safetySourceData.getIssues();
        for (int i = 0; i < safetySourceIssues.size(); i++) {
            SafetySourceIssue safetySourceIssue = safetySourceIssues.get(i);

            SafetyCenterIssue safetyCenterIssue = toSafetyCenterIssue(safetySourceIssue);
            maxSafetyCenterEntrySeverityLevel =
                    Math.max(
                            maxSafetyCenterEntrySeverityLevel,
                            issueToSafetyCenterEntryLevel(safetyCenterIssue.getSeverityLevel()));
            safetyCenterIssues.add(safetyCenterIssue);
        }

        return maxSafetyCenterEntrySeverityLevel;
    }

    @NonNull
    private static SafetyCenterIssue toSafetyCenterIssue(
            @NonNull SafetySourceIssue safetySourceIssue) {
        List<SafetySourceIssue.Action> safetySourceIssueActions = safetySourceIssue.getActions();
        List<SafetyCenterIssue.Action> safetyCenterIssueActions =
                new ArrayList<>(safetySourceIssueActions.size());
        for (int i = 0; i < safetySourceIssueActions.size(); i++) {
            SafetySourceIssue.Action safetySourceIssueAction = safetySourceIssueActions.get(i);

            safetyCenterIssueActions.add(toSafetyCenterIssueAction(safetySourceIssueAction));
        }

        // TODO(b/218817233): Add dismissible and shouldConfirmDismissal. Still TBD by UX: green
        // issues won't have confirm on dismiss and red might not be dismissible.
        return new SafetyCenterIssue.Builder(safetySourceIssue.getId())
                .setSeverityLevel(
                        sourceToSafetyCenterIssueSeverityLevel(
                                safetySourceIssue.getSeverityLevel()))
                .setTitle(safetySourceIssue.getTitle())
                .setSummary(safetySourceIssue.getSummary())
                .setSubtitle(safetySourceIssue.getSubtitle())
                .setActions(safetyCenterIssueActions)
                .build();
    }

    @NonNull
    private static SafetyCenterIssue.Action toSafetyCenterIssueAction(
            @NonNull SafetySourceIssue.Action safetySourceIssueAction) {
        // TODO(b/218817233): Add whether the action is in flight.
        return new SafetyCenterIssue.Action.Builder(safetySourceIssueAction.getId())
                .setLabel(safetySourceIssueAction.getLabel())
                .setSuccessMessage(safetySourceIssueAction.getSuccessMessage())
                .setPendingIntent(safetySourceIssueAction.getPendingIntent())
                .setWillResolve(safetySourceIssueAction.willResolve())
                .build();
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private int addSafetyCenterEntryGroup(
            @NonNull List<SafetyCenterEntryOrGroup> safetyCenterEntryOrGroups,
            @NonNull SafetySourcesGroup safetySourcesGroup,
            @NonNull UserProfileGroup userProfileGroup) {
        int maxSafetyCenterEntryLevel = SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
        int severityUnspecifiedIconType =
                toSeverityUnspecifiedIconType(safetySourcesGroup.getStatelessIconType());

        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        List<SafetyCenterEntry> entries = new ArrayList<>(safetySources.size());
        for (int i = 0; i < safetySources.size(); i++) {
            SafetySource safetySource = safetySources.get(i);

            maxSafetyCenterEntryLevel =
                    Math.max(
                            maxSafetyCenterEntryLevel,
                            addSafetyCenterEntry(
                                    entries,
                                    safetySource,
                                    severityUnspecifiedIconType,
                                    false,
                                    userProfileGroup.getProfileOwnerUserId()));

            if (!SafetySources.supportsManagedProfiles(safetySource)) {
                continue;
            }

            int[] managedProfileUserIds = userProfileGroup.getManagedProfilesUserIds();
            for (int j = 0; j < managedProfileUserIds.length; j++) {
                int managedProfileUserId = managedProfileUserIds[i];

                maxSafetyCenterEntryLevel =
                        Math.max(
                                maxSafetyCenterEntryLevel,
                                addSafetyCenterEntry(
                                        entries,
                                        safetySource,
                                        severityUnspecifiedIconType,
                                        true,
                                        managedProfileUserId));
            }
        }

        // TODO(b/218817233): Revisit how severityUnspecifiedIconType is implemented.
        safetyCenterEntryOrGroups.add(
                new SafetyCenterEntryOrGroup(
                        new SafetyCenterEntryGroup.Builder(safetySourcesGroup.getId())
                                .setSeverityLevel(maxSafetyCenterEntryLevel)
                                .setTitle(getString(safetySourcesGroup.getTitleResId()))
                                .setSummary(getString(safetySourcesGroup.getSummaryResId()))
                                .setEntries(entries)
                                .setSeverityUnspecifiedIconType(severityUnspecifiedIconType)
                                .build()));

        return maxSafetyCenterEntryLevel;
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private int addSafetyCenterEntry(
            @NonNull List<SafetyCenterEntry> entries,
            @NonNull SafetySource safetySource,
            @SafetyCenterEntry.SeverityUnspecifiedIconType int severityUnspecifiedIconType,
            boolean isUserManaged,
            @UserIdInt int userId) {
        SafetyCenterEntry safetyCenterEntry =
                toSafetyCenterEntry(safetySource, severityUnspecifiedIconType, isUserManaged,
                        userId);
        if (safetyCenterEntry == null) {
            return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
        }

        entries.add(safetyCenterEntry);
        return safetyCenterEntry.getSeverityLevel();
    }

    @Nullable
    private SafetyCenterEntry toSafetyCenterEntry(
            @NonNull SafetySource safetySource,
            @SafetyCenterEntry.SeverityUnspecifiedIconType int severityUnspecifiedIconType,
            boolean isUserManaged,
            @UserIdInt int userId) {
        switch (safetySource.getType()) {
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY: {
                Log.w(TAG, "Issue only safety source found in collapsible group");
                return null;
            }
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC: {
                Key key = Key.of(safetySource.getId(), userId);
                SafetySourceStatus safetySourceStatus =
                        getSafetySourceStatus(mSafetySourceDataForKey.get(key));
                if (safetySourceStatus != null) {
                    PendingIntent pendingIntent = safetySourceStatus.getPendingIntent();
                    boolean enabled = safetySourceStatus.isEnabled();
                    if (pendingIntent == null) {
                        pendingIntent =
                                toPendingIntent(
                                        safetySource.getIntentAction(),
                                        safetySource.getPackageName(), userId);
                        enabled = enabled && pendingIntent != null;
                    }
                    // TODO(b/218817233): Add IconAction field and revisit how
                    // severityUnspecifiedIconType is implemented.
                    return new SafetyCenterEntry.Builder(safetySource.getId())
                            .setSeverityLevel(
                                    sourceToSafetyCenterEntrySeverityLevel(
                                            safetySourceStatus.getSeverityLevel()))
                            .setTitle(safetySourceStatus.getTitle())
                            .setSummary(safetySourceStatus.getSummary())
                            .setEnabled(enabled)
                            .setSeverityUnspecifiedIconType(severityUnspecifiedIconType)
                            .setPendingIntent(pendingIntent)
                            .build();
                }
                return toDefaultSafetyCenterEntry(
                        safetySource,
                        safetySource.getPackageName(),
                        SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN,
                        severityUnspecifiedIconType,
                        isUserManaged,
                        userId);
            }
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC: {
                return toDefaultSafetyCenterEntry(
                        safetySource,
                        null,
                        SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED,
                        severityUnspecifiedIconType,
                        isUserManaged,
                        userId);
            }
        }
        Log.w(
                TAG,
                String.format(
                        "Unknown safety source type found in collapsible group: %s",
                        safetySource.getType()));
        return null;
    }

    @Nullable
    private SafetyCenterEntry toDefaultSafetyCenterEntry(
            @NonNull SafetySource safetySource,
            @Nullable String packageName,
            @SafetyCenterEntry.EntrySeverityLevel int entrySeverityLevel,
            @SafetyCenterEntry.SeverityUnspecifiedIconType int severityUnspecifiedIconType,
            boolean isUserManaged,
            @UserIdInt int userId) {
        if (SafetySources.isDefaultEntryHidden(safetySource)) {
            return null;
        }

        PendingIntent pendingIntent =
                toPendingIntent(safetySource.getIntentAction(), packageName, userId);

        boolean enabled = pendingIntent != null && !SafetySources.isDefaultEntryDisabled(
                safetySource);
        // TODO(b/218817233): Add IconAction field and revisit how severityUnspecifiedIconType is
        // implemented.
        return new SafetyCenterEntry.Builder(safetySource.getId())
                .setSeverityLevel(entrySeverityLevel)
                .setTitle(
                        getString(
                                isUserManaged ? safetySource.getTitleForWorkResId()
                                        : safetySource.getTitleResId()))
                .setSummary(getString(safetySource.getSummaryResId()))
                .setEnabled(enabled)
                .setPendingIntent(pendingIntent)
                .setSeverityUnspecifiedIconType(severityUnspecifiedIconType)
                .build();
    }

    private void addSafetyCenterStaticEntryGroup(
            @NonNull List<SafetyCenterStaticEntryGroup> safetyCenterStaticEntryGroups,
            @NonNull SafetySourcesGroup safetySourcesGroup,
            @NonNull UserProfileGroup userProfileGroup) {
        List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
        List<SafetyCenterStaticEntry> staticEntries = new ArrayList<>(safetySources.size());
        for (int i = 0; i < safetySources.size(); i++) {
            SafetySource safetySource = safetySources.get(i);

            addSafetyCenterStaticEntry(
                    staticEntries, safetySource, false, userProfileGroup.getProfileOwnerUserId());

            if (!SafetySources.supportsManagedProfiles(safetySource)) {
                continue;
            }

            int[] managedProfileUserIds = userProfileGroup.getManagedProfilesUserIds();
            for (int j = 0; j < managedProfileUserIds.length; j++) {
                int managedProfileUserId = managedProfileUserIds[i];

                addSafetyCenterStaticEntry(staticEntries, safetySource, true, managedProfileUserId);
            }
        }

        safetyCenterStaticEntryGroups.add(
                new SafetyCenterStaticEntryGroup(
                        getString(safetySourcesGroup.getTitleResId()), staticEntries));
    }

    private void addSafetyCenterStaticEntry(
            @NonNull List<SafetyCenterStaticEntry> staticEntries,
            @NonNull SafetySource safetySource,
            boolean isUserManaged,
            @UserIdInt int userId) {
        SafetyCenterStaticEntry staticEntry =
                toSafetyCenterStaticEntry(safetySource, isUserManaged, userId);
        if (staticEntry == null) {
            return;
        }
        staticEntries.add(staticEntry);
    }

    @Nullable
    private SafetyCenterStaticEntry toSafetyCenterStaticEntry(
            @NonNull SafetySource safetySource, boolean isUserManaged, @UserIdInt int userId) {
        switch (safetySource.getType()) {
            case SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY: {
                Log.w(TAG, "Issue only safety source found in rigid group");
                return null;
            }
            case SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC: {
                Key key = Key.of(safetySource.getId(), userId);
                SafetySourceStatus safetySourceStatus =
                        getSafetySourceStatus(mSafetySourceDataForKey.get(key));
                if (safetySourceStatus != null) {
                    PendingIntent pendingIntent = safetySourceStatus.getPendingIntent();
                    if (pendingIntent == null) {
                        // TODO(b/222838784): Decide strategy for static entries when the intent is
                        // null.
                        return null;
                    }
                    return new SafetyCenterStaticEntry.Builder()
                            .setTitle(safetySourceStatus.getTitle())
                            .setSummary(safetySourceStatus.getSummary())
                            .setPendingIntent(pendingIntent)
                            .build();
                }
                return toDefaultSafetyCenterStaticEntry(safetySource, safetySource.getPackageName(),
                        isUserManaged, userId);
            }
            case SafetySource.SAFETY_SOURCE_TYPE_STATIC: {
                return toDefaultSafetyCenterStaticEntry(safetySource, null, isUserManaged, userId);
            }
        }
        Log.w(
                TAG,
                String.format(
                        "Unknown safety source type found in rigid group: %s",
                        safetySource.getType()));
        return null;
    }

    @Nullable
    private SafetyCenterStaticEntry toDefaultSafetyCenterStaticEntry(
            @NonNull SafetySource safetySource,
            @Nullable String packageName,
            boolean isUserManaged,
            @UserIdInt int userId) {
        if (SafetySources.isDefaultEntryHidden(safetySource)) {
            return null;
        }

        PendingIntent pendingIntent = toPendingIntent(safetySource.getIntentAction(), packageName,
                userId);
        if (pendingIntent == null) {
            // TODO(b/222838784): Decide strategy for static entries when the intent is null.
            return null;
        }

        return new SafetyCenterStaticEntry.Builder()
                .setTitle(
                        getString(
                                isUserManaged ? safetySource.getTitleForWorkResId()
                                        : safetySource.getTitleResId()))
                .setSummary(getString(safetySource.getSummaryResId()))
                .setPendingIntent(pendingIntent)
                .build();
    }

    @Nullable
    private PendingIntent toPendingIntent(
            @Nullable String intentAction, @Nullable String packageName, @UserIdInt int userId) {
        if (intentAction == null) {
            return null;
        }

        Context context;
        if (packageName == null) {
            context = mContext;
        } else {
            // This call requires the INTERACT_ACROSS_USERS permission.
            final long identity = Binder.clearCallingIdentity();
            try {
                context = mContext.createPackageContextAsUser(packageName, 0,
                        UserHandle.of(userId));
            } catch (NameNotFoundException e) {
                Log.w(TAG, String.format("Package name %s not found", packageName), e);
                return null;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        // TODO(b/222838784): Validate that the intent action is available.

        // TODO(b/219699223): Is it safe to create a PendingIntent as system server here?
        // This call is required for getIntentSender() to be allowed to send as another package.
        final long identity = Binder.clearCallingIdentity();
        try {
            return PendingIntent.getActivity(
                    context, 0, new Intent(intentAction), PendingIntent.FLAG_IMMUTABLE);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Nullable
    private static SafetySourceStatus getSafetySourceStatus(
            @Nullable SafetySourceData safetySourceData) {
        if (safetySourceData == null) {
            return null;
        }

        return safetySourceData.getStatus();
    }

    @SafetyCenterStatus.OverallSeverityLevel
    private static int entryToSafetyCenterStatusOverallLevel(
            @SafetyCenterEntry.EntrySeverityLevel int safetyCenterEntrySeverityLevel) {
        switch (safetyCenterEntrySeverityLevel) {
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED:
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(TAG,
                String.format(
                        "Unexpected SafetyCenterEntry.EntrySeverityLevel: %s",
                        safetyCenterEntrySeverityLevel));
        return SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN;
    }

    // TODO(b/219700241): Should we consolidate IntDefs? and in which parts of the API?
    @SafetyCenterEntry.EntrySeverityLevel
    private static int issueToSafetyCenterEntryLevel(
            @SafetyCenterIssue.IssueSeverityLevel int safetyCenterIssueSeverityLevel) {
        switch (safetyCenterIssueSeverityLevel) {
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK;
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(TAG,
                String.format(
                        "Unexpected SafetyCenterIssue.IssueSeverityLevel: %s",
                        safetyCenterIssueSeverityLevel));
        return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
    }

    @SafetyCenterEntry.EntrySeverityLevel
    private static int sourceToSafetyCenterEntrySeverityLevel(
            @SafetySourceData.SeverityLevel int safetySourceSeverityLevel) {
        switch (safetySourceSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED;
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(TAG,
                String.format(
                        "Unexpected SafetySourceSeverity.Level in SafetySourceStatus: %s",
                        safetySourceSeverityLevel));
        return SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN;
    }

    @SafetyCenterIssue.IssueSeverityLevel
    private static int sourceToSafetyCenterIssueSeverityLevel(
            @SafetySourceData.SeverityLevel int safetySourceIssueSeverityLevel) {
        switch (safetySourceIssueSeverityLevel) {
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                Log.w(
                        TAG,
                        "Unexpected use of SafetySourceSeverity.LEVEL_UNSPECIFIED in "
                                + "SafetySourceStatus");
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK;
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION;
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING;
        }

        Log.w(TAG,
                String.format(
                        "Unexpected SafetySourceSeverity.Level in SafetySourceIssue: %s",
                        safetySourceIssueSeverityLevel));
        return SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK;
    }

    @SafetyCenterEntry.SeverityUnspecifiedIconType
    private static int toSeverityUnspecifiedIconType(
            @SafetySourcesGroup.StatelessIconType int statelessIconType) {
        switch (statelessIconType) {
            case SafetySourcesGroup.STATELESS_ICON_TYPE_NONE:
                return SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION;
            case SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY:
                return SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY;
        }

        Log.w(TAG,
                String.format(
                        "Unexpected SafetySourcesGroup.StatelessIconType in SafetySourcesGroup: %s",
                        statelessIconType));
        return SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON;
    }

    // TODO(b/218801295): Use the right strings and localize them.
    private static String getSafetyCenterStatusTitle(
            @SafetyCenterStatus.OverallSeverityLevel int overallSeverityLevel) {
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
                return "Unknown";
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                return "All good";
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return "Some warnings";
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return "Uh-oh";
        }

        Log.w(TAG,
                String.format(
                        "Unexpected SafetyCenterStatus.OverallSeverityLevel: %s",
                        overallSeverityLevel));
        return "";
    }

    // TODO(b/218801295): Use the right strings and localize them.
    private static String getSafetyCenterStatusSummary(
            @SafetyCenterStatus.OverallSeverityLevel int overallSeverityLevel) {
        switch (overallSeverityLevel) {
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN:
                return "Unknown safety status";
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK:
                return "No problemo maestro";
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION:
                return "Careful there";
            case SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING:
                return "Code red";
        }

        Log.w(TAG,
                String.format(
                        "Unexpected SafetyCenterStatus.OverallSeverityLevel: %s",
                        overallSeverityLevel));
        return "";
    }

    /**
     * A key for {@link SafetySourceData}; based on the {@code safetySourceId} and {@code userId}.
     */
    // TODO(b/219697341): Look into using AutoValue for this data class.
    private static final class Key {
        @NonNull
        private final String mSafetySourceId;
        @UserIdInt
        private final int mUserId;

        private Key(@NonNull String safetySourceId, @UserIdInt int userId) {
            mSafetySourceId = safetySourceId;
            mUserId = userId;
        }

        @NonNull
        private static Key of(@NonNull String safetySourceId, @UserIdInt int userId) {
            return new Key(safetySourceId, userId);
        }

        @Override
        public String toString() {
            return "Key{"
                    + "mSafetySourceId='"
                    + mSafetySourceId
                    + '\''
                    + ", mUserId="
                    + mUserId
                    + '\''
                    + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return mSafetySourceId.equals(key.mSafetySourceId) && mUserId == key.mUserId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSafetySourceId, mUserId);
        }
    }

    /**
     * Returns a {@link String} resource from the given {@code stringId}, using the {@link
     * SafetyCenterResourcesContext}.
     *
     * <p>Throws a {@link NullPointerException} if the resource cannot be accessed.
     */
    @NonNull
    private String getString(@StringRes int stringId) {
        return mSafetyCenterResourcesContext.getString(stringId);
    }
}
