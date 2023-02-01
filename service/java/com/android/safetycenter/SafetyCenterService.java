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

package com.android.safetycenter;

import static android.Manifest.permission.MANAGE_SAFETY_CENTER;
import static android.Manifest.permission.READ_SAFETY_CENTER_STATUS;
import static android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE;
import static android.Manifest.permission.START_TASKS_FROM_RECENTS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER;
import static android.safetycenter.SafetyCenterManager.RefreshReason;
import static android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED;

import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE;
import static com.android.safetycenter.SafetyCenterFlags.PROPERTY_SAFETY_CENTER_ENABLED;
import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.PendingIntent;
import android.app.StatsManager;
import android.app.StatsManager.StatsPullAtomCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.res.Resources;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.OnPropertiesChangedListener;
import android.safetycenter.IOnSafetyCenterDataChangedListener;
import android.safetycenter.ISafetyCenterManager;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterErrorDetails;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceErrorDetails;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.config.SafetyCenterConfig;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.BackgroundThread;
import com.android.modules.utils.build.SdkLevel;
import com.android.permission.util.ForegroundThread;
import com.android.permission.util.UserUtils;
import com.android.safetycenter.data.SafetyCenterInFlightIssueActionRepository;
import com.android.safetycenter.data.SafetyCenterIssueDeduplicator;
import com.android.safetycenter.data.SafetyCenterIssueDismissalRepository;
import com.android.safetycenter.data.SafetyCenterIssueRepository;
import com.android.safetycenter.data.SafetySourceDataRepository;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterPullAtomCallback;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;
import com.android.safetycenter.pendingintents.PendingIntentSender;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Service for the safety center.
 *
 * @hide
 */
@Keep
@RequiresApi(TIRAMISU)
public final class SafetyCenterService extends SystemService {

    private static final String TAG = "SafetyCenterService";

    private final Object mApiLock = new Object();

    @GuardedBy("mApiLock")
    private final SafetyCenterTimeouts mSafetyCenterTimeouts = new SafetyCenterTimeouts();

    @NonNull private final SafetyCenterResourcesContext mSafetyCenterResourcesContext;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetySourceDataRepository mSafetySourceDataRepository;

    @GuardedBy("mApiLock")
    @NonNull
    private final PendingIntentFactory mPendingIntentFactory;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterDataFactory mSafetyCenterDataFactory;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterListeners mSafetyCenterListeners;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterNotificationSender mNotificationSender;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterIssueRepository mSafetyCenterIssueRepository;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterIssueDismissalRepository mSafetyCenterIssueDismissalRepository;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterBroadcastDispatcher mSafetyCenterBroadcastDispatcher;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterDataChangeNotifier mSafetyCenterDataChangeNotifier;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterInFlightIssueActionRepository
            mSafetyCenterInFlightIssueActionRepository;

    @NonNull private final StatsPullAtomCallback mPullAtomCallback;
    private final boolean mDeviceSupportsSafetyCenter;

    /** Whether the {@link SafetyCenterConfig} was successfully loaded. */
    private volatile boolean mConfigAvailable;

    public SafetyCenterService(@NonNull Context context) {
        super(context);
        mSafetyCenterResourcesContext = new SafetyCenterResourcesContext(context);
        mSafetyCenterConfigReader = new SafetyCenterConfigReader(mSafetyCenterResourcesContext);
        SafetyCenterStatsdLogger safetyCenterStatsdLogger =
                new SafetyCenterStatsdLogger(context, mSafetyCenterConfigReader);
        mSafetyCenterInFlightIssueActionRepository =
                new SafetyCenterInFlightIssueActionRepository(safetyCenterStatsdLogger);
        mSafetyCenterRefreshTracker = new SafetyCenterRefreshTracker(safetyCenterStatsdLogger);
        mSafetyCenterIssueDismissalRepository =
                new SafetyCenterIssueDismissalRepository(mApiLock, mSafetyCenterConfigReader);
        mPendingIntentFactory = new PendingIntentFactory(context, mSafetyCenterResourcesContext);
        mSafetySourceDataRepository =
                new SafetySourceDataRepository(
                        context,
                        mSafetyCenterConfigReader,
                        mSafetyCenterRefreshTracker,
                        mSafetyCenterInFlightIssueActionRepository,
                        mSafetyCenterIssueDismissalRepository);
        mSafetyCenterIssueRepository =
                new SafetyCenterIssueRepository(
                        context,
                        mSafetySourceDataRepository,
                        mSafetyCenterConfigReader,
                        SdkLevel.isAtLeastU()
                                ? new SafetyCenterIssueDeduplicator(
                                        mSafetyCenterIssueDismissalRepository)
                                : null);
        mSafetyCenterDataFactory =
                new SafetyCenterDataFactory(
                        mSafetyCenterResourcesContext,
                        mSafetyCenterConfigReader,
                        mSafetyCenterRefreshTracker,
                        mPendingIntentFactory,
                        mSafetyCenterInFlightIssueActionRepository,
                        mSafetyCenterIssueDismissalRepository,
                        mSafetySourceDataRepository,
                        mSafetyCenterIssueRepository);
        mSafetyCenterListeners = new SafetyCenterListeners(mSafetyCenterDataFactory);
        mNotificationSender =
                new SafetyCenterNotificationSender(
                        context,
                        new SafetyCenterNotificationFactory(
                                context,
                                new SafetyCenterNotificationChannels(
                                        mSafetyCenterResourcesContext)),
                        mSafetyCenterIssueDismissalRepository,
                        mSafetyCenterIssueRepository);
        mSafetyCenterBroadcastDispatcher =
                new SafetyCenterBroadcastDispatcher(
                        context,
                        mSafetyCenterConfigReader,
                        mSafetyCenterRefreshTracker,
                        mSafetySourceDataRepository);
        mPullAtomCallback =
                new SafetyCenterPullAtomCallback(
                        context,
                        mApiLock,
                        safetyCenterStatsdLogger,
                        mSafetyCenterConfigReader,
                        mSafetySourceDataRepository,
                        mSafetyCenterDataFactory,
                        mSafetyCenterIssueDismissalRepository,
                        mSafetyCenterIssueRepository);
        mSafetyCenterDataChangeNotifier =
                new SafetyCenterDataChangeNotifier(mNotificationSender, mSafetyCenterListeners);
        mDeviceSupportsSafetyCenter =
                context.getResources()
                        .getBoolean(
                                Resources.getSystem()
                                        .getIdentifier(
                                                "config_enableSafetyCenter", "bool", "android"));
        if (!mDeviceSupportsSafetyCenter) {
            Log.v(TAG, "Device does not support safety center, safety center will be disabled.");
        }
    }

    @Override
    public void onStart() {
        publishBinderService(Context.SAFETY_CENTER_SERVICE, new Stub());
        if (mDeviceSupportsSafetyCenter) {
            synchronized (mApiLock) {
                mConfigAvailable = mSafetyCenterConfigReader.loadConfig();
                if (mConfigAvailable) {
                    mSafetyCenterIssueDismissalRepository.loadStateFromFile();
                    new UserBroadcastReceiver().register(getContext());
                    new SafetyCenterNotificationReceiver(
                                    this,
                                    mSafetyCenterIssueDismissalRepository,
                                    mSafetyCenterDataChangeNotifier,
                                    mApiLock)
                            .register(getContext());
                }
            }
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_BOOT_COMPLETED && canUseSafetyCenter()) {
            registerSafetyCenterEnabledListener();
            registerSafetyCenterPullAtomCallback();
        }
    }

    private void registerSafetyCenterEnabledListener() {
        Executor foregroundThreadExecutor = ForegroundThread.getExecutor();
        SafetyCenterEnabledListener listener = new SafetyCenterEnabledListener();
        // Ensure the listener is called first with the current state on the same thread.
        foregroundThreadExecutor.execute(listener::setInitialState);
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_PRIVACY, foregroundThreadExecutor, listener);
    }

    private void registerSafetyCenterPullAtomCallback() {
        StatsManager statsManager =
                requireNonNull(getContext().getSystemService(StatsManager.class));
        statsManager.setPullAtomCallback(
                SAFETY_STATE, null, BackgroundThread.getExecutor(), mPullAtomCallback);
    }

    /** Service implementation of {@link ISafetyCenterManager.Stub}. */
    private final class Stub extends ISafetyCenterManager.Stub {
        @Override
        public boolean isSafetyCenterEnabled() {
            enforceAnyCallingOrSelfPermissions(
                    "isSafetyCenterEnabled", READ_SAFETY_CENTER_STATUS, SEND_SAFETY_CENTER_UPDATE);

            return isApiEnabled();
        }

        @Override
        public void setSafetySourceData(
                @NonNull String safetySourceId,
                @Nullable SafetySourceData safetySourceData,
                @NonNull SafetyEvent safetyEvent,
                @NonNull String packageName,
                @UserIdInt int userId) {
            requireNonNull(safetySourceId);
            requireNonNull(safetyEvent);
            requireNonNull(packageName);
            getContext()
                    .enforceCallingOrSelfPermission(
                            SEND_SAFETY_CENTER_UPDATE, "setSafetySourceData");
            if (!enforceCrossUserPermission("setSafetySourceData", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("setSafetySourceData")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            synchronized (mApiLock) {
                boolean hasUpdate =
                        mSafetySourceDataRepository.setSafetySourceData(
                                safetySourceData, safetySourceId, safetyEvent, packageName, userId);
                if (hasUpdate) {
                    mSafetyCenterIssueRepository.updateIssues(userId);
                    mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup, userId);
                }
            }
        }

        @Override
        @Nullable
        public SafetySourceData getSafetySourceData(
                @NonNull String safetySourceId,
                @NonNull String packageName,
                @UserIdInt int userId) {
            requireNonNull(safetySourceId);
            requireNonNull(packageName);
            getContext()
                    .enforceCallingOrSelfPermission(
                            SEND_SAFETY_CENTER_UPDATE, "getSafetySourceData");
            if (!enforceCrossUserPermission("getSafetySourceData", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("getSafetySourceData")) {
                return null;
            }

            synchronized (mApiLock) {
                return mSafetySourceDataRepository.getSafetySourceData(
                        safetySourceId, packageName, userId);
            }
        }

        @Override
        public void reportSafetySourceError(
                @NonNull String safetySourceId,
                @NonNull SafetySourceErrorDetails errorDetails,
                @NonNull String packageName,
                @UserIdInt int userId) {
            requireNonNull(safetySourceId);
            requireNonNull(errorDetails);
            requireNonNull(packageName);
            getContext()
                    .enforceCallingOrSelfPermission(
                            SEND_SAFETY_CENTER_UPDATE, "reportSafetySourceError");
            if (!enforceCrossUserPermission("reportSafetySourceError", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("reportSafetySourceError")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            synchronized (mApiLock) {
                boolean hasUpdate =
                        mSafetySourceDataRepository.reportSafetySourceError(
                                errorDetails, safetySourceId, packageName, userId);
                SafetyCenterErrorDetails safetyCenterErrorDetails = null;
                if (hasUpdate
                        && errorDetails.getSafetyEvent().getType()
                                == SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED) {
                    safetyCenterErrorDetails =
                            new SafetyCenterErrorDetails(
                                    mSafetyCenterResourcesContext.getStringByName(
                                            "resolving_action_error"));
                }
                if (hasUpdate) {
                    mSafetyCenterIssueRepository.updateIssues(userId);
                    mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup, userId);
                }
                if (safetyCenterErrorDetails != null) {
                    mSafetyCenterListeners.deliverErrorForUserProfileGroup(
                            userProfileGroup, safetyCenterErrorDetails);
                }
            }
        }

        @Override
        public void refreshSafetySources(@RefreshReason int refreshReason, @UserIdInt int userId) {
            RefreshReasons.validate(refreshReason);
            getContext().enforceCallingPermission(MANAGE_SAFETY_CENTER, "refreshSafetySources");
            if (!enforceCrossUserPermission("refreshSafetySources", userId)
                    || !checkApiEnabled("refreshSafetySources")) {
                return;
            }
            startRefreshingSafetySources(refreshReason, userId);
        }

        @Override
        @RequiresApi(UPSIDE_DOWN_CAKE)
        public void refreshSpecificSafetySources(
                @RefreshReason int refreshReason,
                @UserIdInt int userId,
                @NonNull List<String> safetySourceIds) {
            requireNonNull(safetySourceIds, "safetySourceIds cannot be null");
            RefreshReasons.validate(refreshReason);
            getContext()
                    .enforceCallingPermission(MANAGE_SAFETY_CENTER, "refreshSpecificSafetySources");
            if (!enforceCrossUserPermission("refreshSpecificSafetySources", userId)
                    || !checkApiEnabled("refreshSpecificSafetySources")) {
                return;
            }
            startRefreshingSafetySources(refreshReason, userId, safetySourceIds);
        }

        @Override
        @Nullable
        public SafetyCenterConfig getSafetyCenterConfig() {
            getContext()
                    .enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER, "getSafetyCenterConfig");
            // We still return the SafetyCenterConfig object when the API is disabled, as Settings
            // search works by adding all the entries very rarely (and relies on filtering them out
            // instead).
            if (!canUseSafetyCenter()) {
                Log.w(TAG, "Called getSafetyConfig, but Safety Center is not supported");
                return null;
            }

            synchronized (mApiLock) {
                return mSafetyCenterConfigReader.getSafetyCenterConfig();
            }
        }

        @Override
        @NonNull
        public SafetyCenterData getSafetyCenterData(
                @NonNull String packageName, @UserIdInt int userId) {
            requireNonNull(packageName);
            getContext()
                    .enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER, "getSafetyCenterData");
            if (!enforceCrossUserPermission("getSafetyCenterData", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("getSafetyCenterData")) {
                return SafetyCenterDataFactory.getDefaultSafetyCenterData();
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);

            synchronized (mApiLock) {
                return mSafetyCenterDataFactory.assembleSafetyCenterData(
                        packageName, userProfileGroup);
            }
        }

        @Override
        public void addOnSafetyCenterDataChangedListener(
                @NonNull IOnSafetyCenterDataChangedListener listener,
                @NonNull String packageName,
                @UserIdInt int userId) {
            requireNonNull(listener);
            requireNonNull(packageName);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "addOnSafetyCenterDataChangedListener");
            if (!enforceCrossUserPermission("addOnSafetyCenterDataChangedListener", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("addOnSafetyCenterDataChangedListener")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            synchronized (mApiLock) {
                IOnSafetyCenterDataChangedListener registeredListener =
                        mSafetyCenterListeners.addListener(listener, packageName, userId);
                if (registeredListener == null) {
                    return;
                }
                SafetyCenterListeners.deliverDataForListener(
                        registeredListener,
                        mSafetyCenterDataFactory.assembleSafetyCenterData(
                                packageName, userProfileGroup));
            }
        }

        @Override
        public void removeOnSafetyCenterDataChangedListener(
                @NonNull IOnSafetyCenterDataChangedListener listener, @UserIdInt int userId) {
            requireNonNull(listener);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "removeOnSafetyCenterDataChangedListener");
            if (!enforceCrossUserPermission("removeOnSafetyCenterDataChangedListener", userId)
                    || !checkApiEnabled("removeOnSafetyCenterDataChangedListener")) {
                return;
            }

            synchronized (mApiLock) {
                mSafetyCenterListeners.removeListener(listener, userId);
            }
        }

        @Override
        public void dismissSafetyCenterIssue(@NonNull String issueId, @UserIdInt int userId) {
            requireNonNull(issueId);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "dismissSafetyCenterIssue");
            if (!enforceCrossUserPermission("dismissSafetyCenterIssue", userId)
                    || !checkApiEnabled("dismissSafetyCenterIssue")) {
                return;
            }

            SafetyCenterIssueId safetyCenterIssueId = SafetyCenterIds.issueIdFromString(issueId);
            SafetyCenterIssueKey safetyCenterIssueKey =
                    safetyCenterIssueId.getSafetyCenterIssueKey();
            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            enforceSameUserProfileGroup(
                    "dismissSafetyCenterIssue", userProfileGroup, safetyCenterIssueKey.getUserId());
            synchronized (mApiLock) {
                SafetySourceIssue safetySourceIssue =
                        mSafetySourceDataRepository.getSafetySourceIssue(safetyCenterIssueKey);
                if (safetySourceIssue == null) {
                    Log.w(
                            TAG,
                            "Attempt to dismiss an issue that is not provided by the source, or "
                                    + "that was dismissed already");
                    // Don't send the error to the UI here, since it could happen when clicking the
                    // button multiple times in a row.
                    return;
                }
                mSafetyCenterIssueDismissalRepository.dismissIssue(safetyCenterIssueKey);
                PendingIntent onDismissPendingIntent =
                        safetySourceIssue.getOnDismissPendingIntent();
                if (onDismissPendingIntent != null
                        && !dispatchPendingIntent(onDismissPendingIntent, null)) {
                    Log.w(
                            TAG,
                            "Error dispatching dismissal for issue: "
                                    + safetyCenterIssueKey.getSafetySourceIssueId()
                                    + ", of source: "
                                    + safetyCenterIssueKey.getSafetySourceId());
                    // We still consider the dismissal a success if there is an error dispatching
                    // the dismissal PendingIntent, since SafetyCenter won't surface this warning
                    // anymore.
                }
                mSafetyCenterIssueRepository.updateIssues(userId);
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup, userId);
            }
        }

        @Override
        public void executeSafetyCenterIssueAction(
                @NonNull String issueId, @NonNull String issueActionId, @UserIdInt int userId) {
            requireNonNull(issueId);
            requireNonNull(issueActionId);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "executeSafetyCenterIssueAction");
            if (!enforceCrossUserPermission("executeSafetyCenterIssueAction", userId)
                    || !checkApiEnabled("executeSafetyCenterIssueAction")) {
                return;
            }

            SafetyCenterIssueId safetyCenterIssueId = SafetyCenterIds.issueIdFromString(issueId);
            SafetyCenterIssueKey safetyCenterIssueKey =
                    safetyCenterIssueId.getSafetyCenterIssueKey();
            SafetyCenterIssueActionId safetyCenterIssueActionId =
                    SafetyCenterIds.issueActionIdFromString(issueActionId);
            if (!safetyCenterIssueActionId.getSafetyCenterIssueKey().equals(safetyCenterIssueKey)) {
                throw new IllegalArgumentException(
                        toUserFriendlyString(safetyCenterIssueId)
                                + " and "
                                + toUserFriendlyString(safetyCenterIssueActionId)
                                + " do not match");
            }
            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            enforceSameUserProfileGroup(
                    "executeSafetyCenterIssueAction",
                    userProfileGroup,
                    safetyCenterIssueKey.getUserId());
            Integer taskId =
                    safetyCenterIssueId.hasTaskId() ? safetyCenterIssueId.getTaskId() : null;
            executeIssueActionInternal(
                    safetyCenterIssueKey, safetyCenterIssueActionId, userProfileGroup, taskId);
        }

        @Override
        public void clearAllSafetySourceDataForTests() {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "clearAllSafetySourceDataForTests");
            if (!checkApiEnabled("clearAllSafetySourceDataForTests")) {
                return;
            }

            List<UserProfileGroup> userProfileGroups =
                    UserProfileGroup.getAllUserProfileGroups(getContext());
            synchronized (mApiLock) {
                // TODO(b/236693607): Should tests leave real data untouched?
                clearDataLocked();
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroups);
            }
        }

        @Override
        public void setSafetyCenterConfigForTests(@NonNull SafetyCenterConfig safetyCenterConfig) {
            requireNonNull(safetyCenterConfig);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "setSafetyCenterConfigForTests");
            if (!checkApiEnabled("setSafetyCenterConfigForTests")) {
                return;
            }

            List<UserProfileGroup> userProfileGroups =
                    UserProfileGroup.getAllUserProfileGroups(getContext());
            synchronized (mApiLock) {
                mSafetyCenterConfigReader.setConfigOverrideForTests(safetyCenterConfig);
                // TODO(b/236693607): Should tests leave real data untouched?
                clearDataLocked();
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroups);
            }
        }

        @Override
        public void clearSafetyCenterConfigForTests() {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "clearSafetyCenterConfigForTests");
            if (!checkApiEnabled("clearSafetyCenterConfigForTests")) {
                return;
            }

            List<UserProfileGroup> userProfileGroups =
                    UserProfileGroup.getAllUserProfileGroups(getContext());
            synchronized (mApiLock) {
                mSafetyCenterConfigReader.clearConfigOverrideForTests();
                // TODO(b/236693607): Should tests leave real data untouched?
                clearDataLocked();
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroups);
            }
        }

        private boolean isApiEnabled() {
            return canUseSafetyCenter() && SafetyCenterFlags.getSafetyCenterEnabled();
        }

        private void enforceAnyCallingOrSelfPermissions(
                @NonNull String message, @NonNull String... permissions) {
            if (permissions.length == 0) {
                throw new IllegalArgumentException("Must check at least one permission");
            }
            for (int i = 0; i < permissions.length; i++) {
                if (getContext().checkCallingOrSelfPermission(permissions[i])
                        == PERMISSION_GRANTED) {
                    return;
                }
            }
            throw new SecurityException(
                    message
                            + " requires any of: "
                            + Arrays.toString(permissions)
                            + ", but none were granted");
        }

        /** Enforces cross user permission and returns whether the user is existent. */
        private boolean enforceCrossUserPermission(@NonNull String message, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false, message, getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(
                        TAG,
                        "Called "
                                + message
                                + " with user id "
                                + userId
                                + ", which does not correspond to an existing user");
                return false;
            }
            return true;
        }

        /**
         * Returns {@code true} if the {@code packageName} exists and it belongs to the {@code
         * callingUid}.
         *
         * <p>Throws a {@link SecurityException} if the {@code packageName} does not belong to the
         * {@code callingUid}.
         */
        private boolean enforcePackage(
                int callingUid, @NonNull String packageName, @UserIdInt int userId) {
            if (TextUtils.isEmpty(packageName)) {
                throw new IllegalArgumentException("packageName may not be empty");
            }
            int actualUid;
            PackageManager packageManager = getContext().getPackageManager();
            try {
                actualUid =
                        packageManager.getPackageUidAsUser(
                                packageName, PackageInfoFlags.of(0), userId);
            } catch (NameNotFoundException e) {
                Log.e(TAG, "packageName=" + packageName + ", not found for userId=" + userId, e);
                return false;
            }
            if (callingUid == Process.ROOT_UID || callingUid == Process.SYSTEM_UID) {
                return true;
            }
            if (UserHandle.getAppId(callingUid) != UserHandle.getAppId(actualUid)) {
                throw new SecurityException(
                        "packageName="
                                + packageName
                                + ", does not belong to callingUid="
                                + callingUid);
            }
            return true;
        }

        private boolean checkApiEnabled(@NonNull String message) {
            if (!isApiEnabled()) {
                Log.w(TAG, "Called " + message + ", but Safety Center is disabled");
                return false;
            }
            return true;
        }

        private void enforceSameUserProfileGroup(
                @NonNull String message,
                @NonNull UserProfileGroup userProfileGroup,
                @UserIdInt int userId) {
            if (!userProfileGroup.contains(userId)) {
                throw new SecurityException(
                        message
                                + " requires target user id "
                                + userId
                                + " to be within the same profile group of the caller: "
                                + userProfileGroup);
            }
        }

        @Override
        public int handleShellCommand(
                @NonNull ParcelFileDescriptor in,
                @NonNull ParcelFileDescriptor out,
                @NonNull ParcelFileDescriptor err,
                @NonNull String[] args) {
            return new SafetyCenterShellCommandHandler(
                            getContext(), this, mDeviceSupportsSafetyCenter)
                    .exec(
                            this,
                            in.getFileDescriptor(),
                            out.getFileDescriptor(),
                            err.getFileDescriptor(),
                            args);
        }

        /** Dumps state for debugging purposes. */
        @Override
        protected void dump(
                @NonNull FileDescriptor fd, @NonNull PrintWriter fout, @Nullable String[] args) {
            if (!checkDumpPermission(fout)) {
                return;
            }
            List<String> subjects = Arrays.asList(args);
            boolean all = subjects.isEmpty();
            synchronized (mApiLock) {
                if (all || subjects.contains("service")) {
                    SafetyCenterService.this.dumpLocked(fout);
                }
                if (all || subjects.contains("flags")) {
                    SafetyCenterFlags.dump(fout);
                }
                if (all || subjects.contains("config")) {
                    mSafetyCenterConfigReader.dump(fout);
                }
                if (all || subjects.contains("repository")) {
                    mSafetySourceDataRepository.dump(fout);
                }
                if (all || subjects.contains("issues")) {
                    mSafetyCenterIssueRepository.dump(fout);
                }
                if (all || subjects.contains("dismissals")) {
                    mSafetyCenterIssueDismissalRepository.dump(fd, fout);
                }
                if (all || subjects.contains("refresh")) {
                    mSafetyCenterRefreshTracker.dump(fout);
                }
                if (all || subjects.contains("timeouts")) {
                    mSafetyCenterTimeouts.dump(fout);
                }
                if (all || subjects.contains("listeners")) {
                    mSafetyCenterListeners.dump(fout);
                }
                if (all || subjects.contains("notifications")) {
                    mNotificationSender.dump(fout);
                }
                if (all || subjects.contains("inflight")) {
                    mSafetyCenterInFlightIssueActionRepository.dump(fout);
                }
            }
        }

        private boolean checkDumpPermission(@NonNull PrintWriter writer) {
            if (getContext().checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                    != PERMISSION_GRANTED) {
                writer.println(
                        "Permission Denial: can't dump "
                                + "safety_center"
                                + " from from pid="
                                + Binder.getCallingPid()
                                + ", uid="
                                + Binder.getCallingUid()
                                + " due to missing "
                                + android.Manifest.permission.DUMP
                                + " permission");
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * An {@link OnPropertiesChangedListener} for {@link
     * SafetyCenterFlags#PROPERTY_SAFETY_CENTER_ENABLED} that sends broadcasts when the SafetyCenter
     * property is enabled or disabled.
     *
     * <p>This listener assumes that the {@link SafetyCenterFlags#PROPERTY_SAFETY_CENTER_ENABLED}
     * value maps to {@link SafetyCenterManager#isSafetyCenterEnabled()}. It should only be
     * registered if the device supports SafetyCenter and the {@link SafetyCenterConfig} was loaded
     * successfully.
     *
     * <p>This listener is not thread-safe; it should be called on a single thread.
     */
    @NotThreadSafe
    private final class SafetyCenterEnabledListener implements OnPropertiesChangedListener {

        private boolean mSafetyCenterEnabled;

        @Override
        public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
            if (!properties.getKeyset().contains(PROPERTY_SAFETY_CENTER_ENABLED)) {
                return;
            }
            boolean safetyCenterEnabled =
                    properties.getBoolean(PROPERTY_SAFETY_CENTER_ENABLED, false);
            if (mSafetyCenterEnabled == safetyCenterEnabled) {
                return;
            }
            onSafetyCenterEnabledChanged(safetyCenterEnabled);
        }

        private void setInitialState() {
            mSafetyCenterEnabled = SafetyCenterFlags.getSafetyCenterEnabled();
            Log.v(TAG, "SafetyCenter is " + (mSafetyCenterEnabled ? "enabled." : "disabled."));
        }

        private void onSafetyCenterEnabledChanged(boolean safetyCenterEnabled) {
            Log.v(TAG, "SafetyCenter is now " + (safetyCenterEnabled ? "enabled." : "disabled."));

            if (safetyCenterEnabled) {
                onApiEnabled();
            } else {
                onApiDisabled();
            }
            mSafetyCenterEnabled = safetyCenterEnabled;
        }

        private void onApiEnabled() {
            synchronized (mApiLock) {
                mSafetyCenterBroadcastDispatcher.sendEnabledChanged();
            }
        }

        private void onApiDisabled() {
            synchronized (mApiLock) {
                clearDataLocked();
                mSafetyCenterListeners.clear();
                mSafetyCenterBroadcastDispatcher.sendEnabledChanged();
            }
        }
    }

    /** A {@link Runnable} that is called to signal a refresh timeout. */
    private final class RefreshTimeout implements Runnable {

        @NonNull private final String mRefreshBroadcastId;
        @NonNull private final UserProfileGroup mUserProfileGroup;

        RefreshTimeout(
                @NonNull String refreshBroadcastId, @NonNull UserProfileGroup userProfileGroup) {
            mRefreshBroadcastId = refreshBroadcastId;
            mUserProfileGroup = userProfileGroup;
        }

        @Override
        public void run() {
            synchronized (mApiLock) {
                mSafetyCenterTimeouts.remove(this);
                ArraySet<SafetySourceKey> stillInFlight =
                        mSafetyCenterRefreshTracker.timeoutRefresh(mRefreshBroadcastId);
                if (stillInFlight == null) {
                    return;
                }
                boolean showErrorEntriesOnTimeout =
                        SafetyCenterFlags.getShowErrorEntriesOnTimeout();
                if (showErrorEntriesOnTimeout) {
                    for (int i = 0; i < stillInFlight.size(); i++) {
                        mSafetySourceDataRepository.setSafetySourceError(stillInFlight.valueAt(i));
                    }
                }
                mSafetyCenterIssueRepository.updateIssues(mUserProfileGroup);
                mSafetyCenterDataChangeNotifier.updateDataConsumers(mUserProfileGroup);
                if (!showErrorEntriesOnTimeout) {
                    mSafetyCenterListeners.deliverErrorForUserProfileGroup(
                            mUserProfileGroup,
                            new SafetyCenterErrorDetails(
                                    mSafetyCenterResourcesContext.getStringByName(
                                            "refresh_timeout")));
                }
            }

            Log.v(
                    TAG,
                    "Cleared refresh with broadcastId:" + mRefreshBroadcastId + " after a timeout");
        }

        @Override
        public String toString() {
            return "RefreshTimeout{"
                    + "mRefreshBroadcastId='"
                    + mRefreshBroadcastId
                    + '\''
                    + ", mUserProfileGroup="
                    + mUserProfileGroup
                    + '}';
        }
    }

    /** A {@link Runnable} that is called to signal a resolving action timeout. */
    private final class ResolvingActionTimeout implements Runnable {

        @NonNull private final SafetyCenterIssueActionId mSafetyCenterIssueActionId;
        @NonNull private final UserProfileGroup mUserProfileGroup;

        ResolvingActionTimeout(
                @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId,
                @NonNull UserProfileGroup userProfileGroup) {
            mSafetyCenterIssueActionId = safetyCenterIssueActionId;
            mUserProfileGroup = userProfileGroup;
        }

        @Override
        public void run() {
            synchronized (mApiLock) {
                mSafetyCenterTimeouts.remove(this);
                SafetySourceIssue safetySourceIssue =
                        mSafetySourceDataRepository.getSafetySourceIssue(
                                mSafetyCenterIssueActionId.getSafetyCenterIssueKey());
                boolean safetyCenterDataHasChanged =
                        mSafetyCenterInFlightIssueActionRepository
                                .unmarkSafetyCenterIssueActionInFlight(
                                        mSafetyCenterIssueActionId,
                                        safetySourceIssue,
                                        SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT);
                if (!safetyCenterDataHasChanged) {
                    return;
                }
                mSafetyCenterDataChangeNotifier.updateDataConsumers(mUserProfileGroup);
                mSafetyCenterListeners.deliverErrorForUserProfileGroup(
                        mUserProfileGroup,
                        new SafetyCenterErrorDetails(
                                mSafetyCenterResourcesContext.getStringByName(
                                        "resolving_action_error")));
            }
        }

        @Override
        public String toString() {
            return "ResolvingActionTimeout{"
                    + "mSafetyCenterIssueActionId="
                    + toUserFriendlyString(mSafetyCenterIssueActionId)
                    + ", mUserProfileGroup="
                    + mUserProfileGroup
                    + '}';
        }
    }

    private boolean canUseSafetyCenter() {
        return mDeviceSupportsSafetyCenter && mConfigAvailable;
    }

    /**
     * {@link BroadcastReceiver} which handles user and work profile related broadcasts that Safety
     * Center is interested including quiet mode turning on/off and accounts being added/removed.
     */
    private final class UserBroadcastReceiver extends BroadcastReceiver {

        private static final String TAG = "UserBroadcastReceiver";

        @NonNull
        void register(@NonNull Context context) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_ADDED);
            filter.addAction(Intent.ACTION_USER_REMOVED);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
            context.registerReceiverForAllUsers(this, filter, null, null);
        }

        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                Log.w(TAG, "Received broadcast with null action!");
                return;
            }

            UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
            if (userHandle == null) {
                Log.w(TAG, "Received " + action + " broadcast missing user extra!");
                return;
            }

            int userId = userHandle.getIdentifier();
            Log.d(TAG, "Received " + action + " broadcast for user " + userId);

            switch (action) {
                case Intent.ACTION_USER_REMOVED:
                case Intent.ACTION_MANAGED_PROFILE_REMOVED:
                    removeUser(userId, true);
                    break;
                case Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE:
                    removeUser(userId, false);
                    // fall through!
                case Intent.ACTION_USER_ADDED:
                case Intent.ACTION_MANAGED_PROFILE_ADDED:
                case Intent.ACTION_MANAGED_PROFILE_AVAILABLE:
                    startRefreshingSafetySources(REFRESH_REASON_OTHER, userId);
                    break;
            }
        }
    }

    private void removeUser(@UserIdInt int userId, boolean clearDataPermanently) {
        UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
        synchronized (mApiLock) {
            mSafetyCenterListeners.clearForUser(userId);
            mSafetyCenterRefreshTracker.clearRefreshForUser(userId);

            if (clearDataPermanently) {
                mSafetySourceDataRepository.clearForUser(userId);
                mSafetyCenterInFlightIssueActionRepository.clearForUser(userId);
                mSafetyCenterIssueDismissalRepository.clearForUser(userId);
                mSafetyCenterIssueRepository.updateIssues(userId);
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup, userId);
            } else {
                mSafetyCenterListeners.deliverDataForUserProfileGroup(userProfileGroup);
            }
        }
    }

    private void startRefreshingSafetySources(
            @RefreshReason int refreshReason, @UserIdInt int userId) {
        startRefreshingSafetySources(refreshReason, userId, null);
    }

    private void startRefreshingSafetySources(
            @RefreshReason int refreshReason,
            @UserIdInt int userId,
            @Nullable List<String> selectedSafetySourceIds) {
        UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
        synchronized (mApiLock) {
            mSafetySourceDataRepository.clearSafetySourceErrors(userProfileGroup);

            String refreshBroadcastId =
                    mSafetyCenterBroadcastDispatcher.sendRefreshSafetySources(
                            refreshReason, userProfileGroup, selectedSafetySourceIds);
            if (refreshBroadcastId == null) {
                return;
            }

            RefreshTimeout refreshTimeout =
                    new RefreshTimeout(refreshBroadcastId, userProfileGroup);
            mSafetyCenterTimeouts.add(
                    refreshTimeout, SafetyCenterFlags.getRefreshSourcesTimeout(refreshReason));

            mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup);
        }
    }

    /**
     * Executes the {@link SafetySourceIssue.Action} specified by the given {@link
     * SafetyCenterIssueActionId}.
     *
     * <p>No validation is performed on the contents of the given ID.
     */
    void executeIssueActionInternal(@NonNull SafetyCenterIssueActionId safetyCenterIssueActionId) {
        SafetyCenterIssueKey safetyCenterIssueKey =
                safetyCenterIssueActionId.getSafetyCenterIssueKey();
        UserProfileGroup userProfileGroup =
                UserProfileGroup.from(getContext(), safetyCenterIssueKey.getUserId());
        executeIssueActionInternal(
                safetyCenterIssueKey, safetyCenterIssueActionId, userProfileGroup, null);
    }

    private void executeIssueActionInternal(
            @NonNull SafetyCenterIssueKey safetyCenterIssueKey,
            @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId,
            @NonNull UserProfileGroup userProfileGroup,
            @Nullable Integer taskId) {
        synchronized (mApiLock) {
            SafetySourceIssue.Action safetySourceIssueAction =
                    mSafetySourceDataRepository.getSafetySourceIssueAction(
                            safetyCenterIssueActionId);

            if (safetySourceIssueAction == null) {
                Log.w(
                        TAG,
                        "Attempt to execute an issue action that is not provided by the source,"
                                + " that was dismissed, or is already in flight");
                // Don't send the error to the UI here, since it could happen when clicking the
                // button multiple times in a row.
                return;
            }
            PendingIntent issueActionPendingIntent =
                    mPendingIntentFactory.maybeOverridePendingIntent(
                            safetyCenterIssueKey.getSafetySourceId(),
                            safetySourceIssueAction.getPendingIntent(),
                            false);
            if (!dispatchPendingIntent(issueActionPendingIntent, taskId)) {
                Log.w(
                        TAG,
                        "Error dispatching action: "
                                + toUserFriendlyString(safetyCenterIssueActionId));
                CharSequence errorMessage;
                if (safetySourceIssueAction.willResolve()) {
                    errorMessage =
                            mSafetyCenterResourcesContext.getStringByName("resolving_action_error");
                } else {
                    errorMessage =
                            mSafetyCenterResourcesContext.getStringByName("redirecting_error");
                }
                mSafetyCenterListeners.deliverErrorForUserProfileGroup(
                        userProfileGroup, new SafetyCenterErrorDetails(errorMessage));
                return;
            }
            if (safetySourceIssueAction.willResolve()) {
                mSafetyCenterInFlightIssueActionRepository.markSafetyCenterIssueActionInFlight(
                        safetyCenterIssueActionId);
                ResolvingActionTimeout resolvingActionTimeout =
                        new ResolvingActionTimeout(safetyCenterIssueActionId, userProfileGroup);
                mSafetyCenterTimeouts.add(
                        resolvingActionTimeout, SafetyCenterFlags.getResolvingActionTimeout());
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup);
            }
        }
    }

    private boolean dispatchPendingIntent(
            @NonNull PendingIntent pendingIntent, @Nullable Integer launchTaskId) {
        if (launchTaskId != null
                && getContext().checkCallingOrSelfPermission(START_TASKS_FROM_RECENTS)
                        != PERMISSION_GRANTED) {
            launchTaskId = null;
        }
        return PendingIntentSender.trySend(pendingIntent, launchTaskId);
    }

    @GuardedBy("mApiLock")
    private void clearDataLocked() {
        mSafetySourceDataRepository.clear();
        mSafetyCenterInFlightIssueActionRepository.clear();
        mSafetyCenterIssueDismissalRepository.clear();
        mSafetyCenterTimeouts.clear();
        mSafetyCenterRefreshTracker.clearRefresh();
        mNotificationSender.cancelAllNotifications();
        mSafetyCenterIssueRepository.clear();
    }

    /** Dumps state for debugging purposes. */
    @GuardedBy("mApiLock")
    private void dumpLocked(@NonNull PrintWriter fout) {
        fout.println("SERVICE");
        fout.println(
                "\tSafetyCenterService{"
                        + "mDeviceSupportsSafetyCenter="
                        + mDeviceSupportsSafetyCenter
                        + ", mConfigAvailable="
                        + mConfigAvailable
                        + '}');
        fout.println();
    }
}
