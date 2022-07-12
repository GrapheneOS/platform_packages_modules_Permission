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
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.safetycenter.SafetyCenterManager.RefreshReason;
import static android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED;

import static com.android.safetycenter.SafetyCenterFlags.PROPERTY_SAFETY_CENTER_ENABLED;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.annotation.WorkerThread;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.ApexEnvironment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Handler;
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
import com.android.permission.util.ForegroundThread;
import com.android.permission.util.UserUtils;
import com.android.safetycenter.SafetyCenterConfigReader.Broadcast;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.persistence.PersistedSafetyCenterIssue;
import com.android.safetycenter.persistence.PersistenceException;
import com.android.safetycenter.persistence.SafetyCenterIssuesPersistence;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;
import com.android.server.SystemService;

import java.io.File;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

    /** The APEX name used to retrieve the APEX owned data directories. */
    private static final String APEX_MODULE_NAME = "com.android.permission";

    /** The name of the file used to persist the Safety Center issue cache. */
    private static final String SAFETY_CENTER_ISSUES_CACHE_FILE_NAME = "safety_center_issues.xml";

    /** The time delay used to throttle and aggregate writes to disk. */
    private static final long WRITE_DELAY_MILLIS = 500;

    private final Handler mWriteHandler = BackgroundThread.getHandler();

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
    private final SafetyCenterDataTracker mSafetyCenterDataTracker;

    @GuardedBy("mApiLock")
    private final SafetyCenterListeners mSafetyCenterListeners;

    @GuardedBy("mApiLock")
    private boolean mSafetyCenterIssueCacheWriteScheduled;

    @NonNull private final SafetyCenterBroadcastDispatcher mSafetyCenterBroadcastDispatcher;

    @NonNull private final AppOpsManager mAppOpsManager;
    private final boolean mDeviceSupportsSafetyCenter;

    /** Whether the {@link SafetyCenterConfig} was successfully loaded. */
    private volatile boolean mConfigAvailable;

    public SafetyCenterService(@NonNull Context context) {
        super(context);
        mSafetyCenterResourcesContext = new SafetyCenterResourcesContext(context);
        mSafetyCenterConfigReader = new SafetyCenterConfigReader(mSafetyCenterResourcesContext);
        mSafetyCenterRefreshTracker = new SafetyCenterRefreshTracker(mSafetyCenterConfigReader);
        mSafetyCenterDataTracker =
                new SafetyCenterDataTracker(
                        context,
                        mSafetyCenterResourcesContext,
                        mSafetyCenterConfigReader,
                        mSafetyCenterRefreshTracker);
        mSafetyCenterListeners = new SafetyCenterListeners(mSafetyCenterDataTracker);
        mSafetyCenterBroadcastDispatcher = new SafetyCenterBroadcastDispatcher(context);
        mAppOpsManager = requireNonNull(context.getSystemService(AppOpsManager.class));
        mDeviceSupportsSafetyCenter =
                context.getResources()
                        .getBoolean(
                                Resources.getSystem()
                                        .getIdentifier(
                                                "config_enableSafetyCenter", "bool", "android"));
    }

    @Override
    public void onStart() {
        publishBinderService(Context.SAFETY_CENTER_SERVICE, new Stub());
        if (mDeviceSupportsSafetyCenter) {
            synchronized (mApiLock) {
                mConfigAvailable = mSafetyCenterConfigReader.loadConfig();
                if (mConfigAvailable) {
                    readSafetyCenterIssueCacheFileLocked();
                    registerUserRemovedReceiver();
                }
            }
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_BOOT_COMPLETED && canUseSafetyCenter()) {
            Executor foregroundThreadExecutor = ForegroundThread.getExecutor();
            SafetyCenterEnabledListener listener = new SafetyCenterEnabledListener();
            // Ensure the listener is called first with the current state on the same thread.
            foregroundThreadExecutor.execute(listener::setInitialState);
            DeviceConfig.addOnPropertiesChangedListener(
                    DeviceConfig.NAMESPACE_PRIVACY, foregroundThreadExecutor, listener);
        }
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
            getContext()
                    .enforceCallingOrSelfPermission(
                            SEND_SAFETY_CENTER_UPDATE, "setSafetySourceData");
            requireNonNull(safetySourceId);
            requireNonNull(safetyEvent);
            requireNonNull(packageName);
            mAppOpsManager.checkPackage(Binder.getCallingUid(), packageName);
            if (!enforceCrossUserPermission("setSafetySourceData", userId)
                    || !checkApiEnabled("setSafetySourceData")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            synchronized (mApiLock) {
                boolean hasUpdate =
                        mSafetyCenterDataTracker.setSafetySourceData(
                                safetySourceData, safetySourceId, safetyEvent, packageName, userId);
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        userProfileGroup, hasUpdate, null);
                scheduleWriteSafetyCenterIssueCacheFileIfNeededLocked();
            }
        }

        @Override
        @Nullable
        public SafetySourceData getSafetySourceData(
                @NonNull String safetySourceId,
                @NonNull String packageName,
                @UserIdInt int userId) {
            getContext()
                    .enforceCallingOrSelfPermission(
                            SEND_SAFETY_CENTER_UPDATE, "getSafetySourceData");
            requireNonNull(safetySourceId);
            requireNonNull(packageName);
            mAppOpsManager.checkPackage(Binder.getCallingUid(), packageName);
            if (!enforceCrossUserPermission("getSafetySourceData", userId)
                    || !checkApiEnabled("getSafetySourceData")) {
                return null;
            }

            synchronized (mApiLock) {
                return mSafetyCenterDataTracker.getSafetySourceData(
                        safetySourceId, packageName, userId);
            }
        }

        @Override
        public void reportSafetySourceError(
                @NonNull String safetySourceId,
                @NonNull SafetySourceErrorDetails errorDetails,
                @NonNull String packageName,
                @UserIdInt int userId) {
            getContext()
                    .enforceCallingOrSelfPermission(
                            SEND_SAFETY_CENTER_UPDATE, "reportSafetySourceError");
            requireNonNull(safetySourceId);
            requireNonNull(errorDetails);
            requireNonNull(packageName);
            mAppOpsManager.checkPackage(Binder.getCallingUid(), packageName);
            if (!enforceCrossUserPermission("reportSafetySourceError", userId)
                    || !checkApiEnabled("reportSafetySourceError")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            synchronized (mApiLock) {
                boolean hasUpdate =
                        mSafetyCenterDataTracker.reportSafetySourceError(
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
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        userProfileGroup, hasUpdate, safetyCenterErrorDetails);
            }
        }

        @Override
        public void refreshSafetySources(@RefreshReason int refreshReason, @UserIdInt int userId) {
            getContext().enforceCallingPermission(MANAGE_SAFETY_CENTER, "refreshSafetySources");
            if (!enforceCrossUserPermission("refreshSafetySources", userId)
                    || !checkApiEnabled("refreshSafetySources")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);

            List<Broadcast> broadcasts;
            String refreshBroadcastId;

            synchronized (mApiLock) {
                broadcasts = mSafetyCenterConfigReader.getBroadcasts();
                mSafetyCenterDataTracker.clearSafetySourceErrors(userProfileGroup);
                refreshBroadcastId =
                        mSafetyCenterRefreshTracker.reportRefreshInProgress(
                                refreshReason, userProfileGroup);

                RefreshTimeout refreshTimeout =
                        new RefreshTimeout(refreshBroadcastId, userProfileGroup);
                mSafetyCenterTimeouts.add(refreshTimeout, SafetyCenterFlags.getRefreshTimeout());

                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        userProfileGroup, true, null);
            }

            mSafetyCenterBroadcastDispatcher.sendRefreshSafetySources(
                    broadcasts, refreshBroadcastId, refreshReason, userProfileGroup);
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
            getContext()
                    .enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER, "getSafetyCenterData");
            requireNonNull(packageName);
            mAppOpsManager.checkPackage(Binder.getCallingUid(), packageName);
            if (!enforceCrossUserPermission("getSafetyCenterData", userId)
                    || !checkApiEnabled("getSafetyCenterData")) {
                // This call is thread safe and there is no need to hold the mApiLock
                @SuppressWarnings("GuardedBy")
                SafetyCenterData defaultData =
                        mSafetyCenterDataTracker.getDefaultSafetyCenterData();
                return defaultData;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);

            synchronized (mApiLock) {
                return mSafetyCenterDataTracker.getSafetyCenterData(packageName, userProfileGroup);
            }
        }

        @Override
        public void addOnSafetyCenterDataChangedListener(
                @NonNull IOnSafetyCenterDataChangedListener listener,
                @NonNull String packageName,
                @UserIdInt int userId) {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "addOnSafetyCenterDataChangedListener");
            requireNonNull(listener);
            requireNonNull(packageName);
            mAppOpsManager.checkPackage(Binder.getCallingUid(), packageName);
            if (!enforceCrossUserPermission("addOnSafetyCenterDataChangedListener", userId)
                    || !checkApiEnabled("addOnSafetyCenterDataChangedListener")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            synchronized (mApiLock) {
                boolean registered =
                        mSafetyCenterListeners.addListener(listener, packageName, userId);
                if (!registered) {
                    return;
                }
                SafetyCenterListeners.deliverUpdateForListener(
                        listener,
                        mSafetyCenterDataTracker.getSafetyCenterData(packageName, userProfileGroup),
                        null);
            }
        }

        @Override
        public void removeOnSafetyCenterDataChangedListener(
                @NonNull IOnSafetyCenterDataChangedListener listener, @UserIdInt int userId) {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "removeOnSafetyCenterDataChangedListener");
            requireNonNull(listener);
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
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "dismissSafetyCenterIssue");
            requireNonNull(issueId);
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
                        mSafetyCenterDataTracker.getSafetySourceIssue(safetyCenterIssueKey);
                if (safetySourceIssue == null) {
                    Log.w(
                            TAG,
                            "Attempt to dismiss an issue that is not provided by the source, or "
                                    + "that was dismissed already");
                    // Don't send the error to the UI here, since it could happen when clicking the
                    // button multiple times in a row.
                    return;
                }
                mSafetyCenterDataTracker.dismissSafetyCenterIssue(safetyCenterIssueKey);
                scheduleWriteSafetyCenterIssueCacheFileIfNeededLocked();
                PendingIntent onDismissPendingIntent =
                        safetySourceIssue.getOnDismissPendingIntent();
                if (onDismissPendingIntent != null
                        && !dispatchPendingIntent(onDismissPendingIntent)) {
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
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        userProfileGroup, true, null);
            }
        }

        @Override
        public void executeSafetyCenterIssueAction(
                @NonNull String issueId, @NonNull String issueActionId, @UserIdInt int userId) {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "executeSafetyCenterIssueAction");
            requireNonNull(issueId);
            requireNonNull(issueActionId);
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
                        "issueId: "
                                + safetyCenterIssueId
                                + " and issueActionId: "
                                + safetyCenterIssueActionId
                                + " do not match");
            }
            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            enforceSameUserProfileGroup(
                    "executeSafetyCenterIssueAction",
                    userProfileGroup,
                    safetyCenterIssueKey.getUserId());
            synchronized (mApiLock) {
                SafetySourceIssue.Action safetySourceIssueAction =
                        mSafetyCenterDataTracker.getSafetySourceIssueAction(
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
                if (!dispatchPendingIntent(safetySourceIssueAction.getPendingIntent())) {
                    Log.w(
                            TAG,
                            "Error dispatching action: "
                                    + safetyCenterIssueActionId.getSafetySourceIssueActionId()
                                    + ", for issue: "
                                    + safetyCenterIssueKey.getSafetySourceIssueId()
                                    + ", of source: "
                                    + safetyCenterIssueKey.getSafetySourceId());
                    CharSequence errorMessage;
                    if (safetySourceIssueAction.willResolve()) {
                        errorMessage =
                                mSafetyCenterResourcesContext.getStringByName(
                                        "resolving_action_error");
                    } else {
                        errorMessage =
                                mSafetyCenterResourcesContext.getStringByName("redirecting_error");
                    }
                    mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                            userProfileGroup, false, new SafetyCenterErrorDetails(errorMessage));
                    return;
                }
                if (safetySourceIssueAction.willResolve()) {
                    mSafetyCenterDataTracker.markSafetyCenterIssueActionAsInFlight(
                            safetyCenterIssueActionId);
                    ResolvingActionTimeout resolvingActionTimeout =
                            new ResolvingActionTimeout(safetyCenterIssueActionId, userProfileGroup);
                    mSafetyCenterTimeouts.add(
                            resolvingActionTimeout, SafetyCenterFlags.getResolvingActionTimeout());
                    mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                            userProfileGroup, true, null);
                }
            }
        }

        @Override
        public void clearAllSafetySourceDataForTests() {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "clearAllSafetySourceDataForTests");
            if (!checkApiEnabled("clearAllSafetySourceDataForTests")) {
                return;
            }

            synchronized (mApiLock) {
                // TODO(b/236693607): Should tests leave real data untouched?
                clearDataLocked();
                // TODO(b/223550097): Should we dispatch a new listener update here? This call can
                //  modify the SafetyCenterData.
            }
        }

        @Override
        public void setSafetyCenterConfigForTests(@NonNull SafetyCenterConfig safetyCenterConfig) {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "setSafetyCenterConfigForTests");
            requireNonNull(safetyCenterConfig);
            if (!checkApiEnabled("setSafetyCenterConfigForTests")) {
                return;
            }

            synchronized (mApiLock) {
                mSafetyCenterConfigReader.setConfigOverrideForTests(safetyCenterConfig);
                // TODO(b/236693607): Should tests leave real data untouched?
                clearDataLocked();
                // TODO(b/223550097): Should we clear the listeners here? Or should we dispatch a
                //  new listener update since the SafetyCenterData will have changed?
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

            synchronized (mApiLock) {
                mSafetyCenterConfigReader.clearConfigOverrideForTests();
                // TODO(b/236693607): Should tests leave real data untouched?
                clearDataLocked();
                // TODO(b/223550097): Should we clear the listeners here? Or should we dispatch a
                //  new listener update since the SafetyCenterData will have changed?
            }
        }

        private boolean isApiEnabled() {
            return canUseSafetyCenter() && SafetyCenterFlags.getSafetyCenterEnabled();
        }

        private void enforceAnyCallingOrSelfPermissions(
                @NonNull String message, String... permissions) {
            if (permissions.length == 0) {
                throw new IllegalArgumentException("Must check at least one permission");
            }
            for (int i = 0; i < permissions.length; i++) {
                if (getContext().checkCallingOrSelfPermission(permissions[i])
                        == PackageManager.PERMISSION_GRANTED) {
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
            // TODO(b/223132917): Check if user is enabled, running and/or if quiet mode is enabled?
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

        private boolean dispatchPendingIntent(@NonNull PendingIntent pendingIntent) {
            try {
                pendingIntent.send();
                return true;
            } catch (PendingIntent.CanceledException ex) {
                Log.w(TAG, "Couldn't dispatch PendingIntent", ex);
                return false;
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
        }

        private void onSafetyCenterEnabledChanged(boolean safetyCenterEnabled) {
            if (safetyCenterEnabled) {
                onApiEnabled();
            } else {
                onApiDisabled();
            }
            mSafetyCenterEnabled = safetyCenterEnabled;
        }

        private void onApiEnabled() {
            List<Broadcast> broadcasts;
            synchronized (mApiLock) {
                broadcasts = mSafetyCenterConfigReader.getBroadcasts();
            }

            mSafetyCenterBroadcastDispatcher.sendEnabledChanged(broadcasts);
        }

        private void onApiDisabled() {
            List<Broadcast> broadcasts;
            synchronized (mApiLock) {
                broadcasts = mSafetyCenterConfigReader.getBroadcasts();
                clearDataLocked();
                mSafetyCenterListeners.clear();
            }

            mSafetyCenterBroadcastDispatcher.sendEnabledChanged(broadcasts);
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
                        mSafetyCenterRefreshTracker.clearRefresh(mRefreshBroadcastId);
                if (stillInFlight == null) {
                    return;
                }
                boolean showErrorEntriesOnTimeout =
                        SafetyCenterFlags.getShowErrorEntriesOnTimeout();
                if (showErrorEntriesOnTimeout) {
                    for (int i = 0; i < stillInFlight.size(); i++) {
                        mSafetyCenterDataTracker.setSafetySourceError(stillInFlight.valueAt(i));
                    }
                }
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        mUserProfileGroup,
                        true,
                        showErrorEntriesOnTimeout
                                ? null
                                : new SafetyCenterErrorDetails(
                                        mSafetyCenterResourcesContext.getStringByName(
                                                "refresh_timeout")));
            }

            Log.v(
                    TAG,
                    "Cleared refresh with broadcastId:" + mRefreshBroadcastId + " after a timeout");
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
                boolean safetyCenterDataHasChanged =
                        mSafetyCenterDataTracker.unmarkSafetyCenterIssueActionAsInFlight(
                                mSafetyCenterIssueActionId);
                if (!safetyCenterDataHasChanged) {
                    return;
                }
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        mUserProfileGroup,
                        true,
                        new SafetyCenterErrorDetails(
                                mSafetyCenterResourcesContext.getStringByName(
                                        "resolving_action_error")));
            }
        }
    }

    /**
     * A wrapper class to track the timeouts that are currently in flight.
     *
     * <p>This class isn't thread safe. Thread safety must be handled by the caller.
     */
    @NotThreadSafe
    private static final class SafetyCenterTimeouts {

        /**
         * The maximum number of timeouts we are tracking at a given time. This is to avoid having
         * the {@code mTimeouts} queue grow unbounded. In practice, we should never have more than 1
         * or 2 timeouts in flight.
         */
        private static final int MAX_TRACKED = 10;

        private final ArrayDeque<Runnable> mTimeouts = new ArrayDeque<>(MAX_TRACKED);
        private final Handler mForegroundHandler = ForegroundThread.getHandler();

        SafetyCenterTimeouts() {}

        private void add(@NonNull Runnable timeoutAction, @NonNull Duration timeoutDuration) {
            if (mTimeouts.size() + 1 >= MAX_TRACKED) {
                remove(mTimeouts.pollFirst());
            }
            mTimeouts.addLast(timeoutAction);
            mForegroundHandler.postDelayed(timeoutAction, timeoutDuration.toMillis());
        }

        private void remove(@NonNull Runnable timeoutAction) {
            mTimeouts.remove(timeoutAction);
            mForegroundHandler.removeCallbacks(timeoutAction);
        }

        private void clear() {
            while (!mTimeouts.isEmpty()) {
                mForegroundHandler.removeCallbacks(mTimeouts.pollFirst());
            }
        }
    }

    private boolean canUseSafetyCenter() {
        return mDeviceSupportsSafetyCenter && mConfigAvailable;
    }

    private void registerUserRemovedReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_REMOVED);
        getContext()
                .registerReceiverForAllUsers(
                        new BroadcastReceiver() {
                            @Override
                            public void onReceive(
                                    @NonNull Context context, @NonNull Intent intent) {
                                if (TextUtils.equals(
                                        intent.getAction(), Intent.ACTION_USER_REMOVED)) {
                                    int userId =
                                            intent.getParcelableExtra(
                                                            Intent.EXTRA_USER, UserHandle.class)
                                                    .getIdentifier();
                                    onRemoveUser(userId);
                                }
                            }
                        },
                        intentFilter,
                        null,
                        null);
    }

    private void onRemoveUser(@UserIdInt int userId) {
        UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
        synchronized (mApiLock) {
            mSafetyCenterDataTracker.clearForUser(userId);
            mSafetyCenterListeners.clearForUser(userId);
            mSafetyCenterRefreshTracker.clearRefreshForUser(userId);
            mSafetyCenterListeners.deliverUpdateForUserProfileGroup(userProfileGroup, true, null);
            scheduleWriteSafetyCenterIssueCacheFileIfNeededLocked();
        }
    }

    /** Schedule writing the cache to file. */
    @GuardedBy("mApiLock")
    private void scheduleWriteSafetyCenterIssueCacheFileIfNeededLocked() {
        if (!mSafetyCenterDataTracker.isSafetyCenterIssueCacheDirty()) {
            return;
        }
        if (!mSafetyCenterIssueCacheWriteScheduled) {
            mWriteHandler.postDelayed(this::writeSafetyCenterIssueCacheFile, WRITE_DELAY_MILLIS);
            mSafetyCenterIssueCacheWriteScheduled = true;
        }
    }

    @WorkerThread
    private void writeSafetyCenterIssueCacheFile() {
        List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues;

        synchronized (mApiLock) {
            mSafetyCenterIssueCacheWriteScheduled = false;
            persistedSafetyCenterIssues = mSafetyCenterDataTracker.snapshotSafetyCenterIssueCache();
            // Since all write operations are scheduled in the same background thread, we can safely
            // release the lock after creating a snapshot and know that all snapshots will be
            // written in the correct order even if we are not holding the lock.
        }

        SafetyCenterIssuesPersistence.write(
                persistedSafetyCenterIssues, getSafetyCenterIssueCacheFile());
    }

    @GuardedBy("mApiLock")
    private void readSafetyCenterIssueCacheFileLocked() {
        List<PersistedSafetyCenterIssue> persistedSafetyCenterIssues = new ArrayList<>();

        try {
            persistedSafetyCenterIssues =
                    SafetyCenterIssuesPersistence.read(getSafetyCenterIssueCacheFile());
            Log.i(TAG, "Safety Center persisted issues read successfully");
        } catch (PersistenceException e) {
            Log.e(TAG, "Cannot read Safety Center persisted issues", e);
        }

        mSafetyCenterDataTracker.loadSafetyCenterIssueCache(persistedSafetyCenterIssues);
        scheduleWriteSafetyCenterIssueCacheFileIfNeededLocked();
    }

    @NonNull
    private static File getSafetyCenterIssueCacheFile() {
        ApexEnvironment apexEnvironment = ApexEnvironment.getApexEnvironment(APEX_MODULE_NAME);
        File dataDirectory = apexEnvironment.getDeviceProtectedDataDir();
        // It should resolve to /data/misc/apexdata/com.android.permission/safety_center_issues.xml
        return new File(dataDirectory, SAFETY_CENTER_ISSUES_CACHE_FILE_NAME);
    }

    @GuardedBy("mApiLock")
    private void clearDataLocked() {
        mSafetyCenterDataTracker.clear();
        mSafetyCenterTimeouts.clear();
        mSafetyCenterRefreshTracker.clearRefresh();
        scheduleWriteSafetyCenterIssueCacheFileIfNeededLocked();
    }
}
