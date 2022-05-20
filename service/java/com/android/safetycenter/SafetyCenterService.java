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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
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
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.BackgroundThread;
import com.android.permission.util.UserUtils;
import com.android.safetycenter.SafetyCenterConfigReader.Broadcast;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueId;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;
import com.android.server.SystemService;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Service for the safety center.
 *
 * @hide
 */
@Keep
@RequiresApi(TIRAMISU)
public final class SafetyCenterService extends SystemService {

    private static final String TAG = "SafetyCenterService";

    /** Phenotype flag that determines whether SafetyCenter is enabled. */
    private static final String PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled";

    /**
     * Time for which a refresh is allowed to wait for sources to set data before timing out and
     * marking the refresh as finished.
     */
    // TODO(b/218285164): Decide final timeout and use a Device Config value instead so that this
    //  duration can be easily adjusted. Once done, add a test that overrides this Device Config
    //  value in CTS tests.
    private static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Time for which a resolving action is allowed to run for before timing out and unmarking it as
     * in-flight.
     */
    // TODO(b/218285164): Decide final timeout and use a Device Config value instead so that this
    //  duration can be easily adjusted. Once done, add a test that overrides this Device Config
    //  value in CTS tests.
    private static final Duration RESOLVING_ACTION_TIMEOUT = Duration.ofSeconds(10);

    private final Object mApiLock = new Object();

    @GuardedBy("mApiLock")
    private final SafetyCenterListeners mSafetyCenterListeners = new SafetyCenterListeners();

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;

    @GuardedBy("mApiLock")
    @NonNull
    private final SafetyCenterDataTracker mSafetyCenterDataTracker;

    @NonNull private final SafetyCenterBroadcastDispatcher mSafetyCenterBroadcastDispatcher;

    @NonNull private final AppOpsManager mAppOpsManager;
    private final boolean mDeviceSupportsSafetyCenter;

    /** Whether the {@link SafetyCenterConfig} was successfully loaded. */
    private volatile boolean mConfigAvailable;

    public SafetyCenterService(@NonNull Context context) {
        super(context);
        SafetyCenterResourcesContext safetyCenterResourcesContext =
                new SafetyCenterResourcesContext(context);
        mSafetyCenterConfigReader = new SafetyCenterConfigReader(safetyCenterResourcesContext);
        mSafetyCenterRefreshTracker = new SafetyCenterRefreshTracker(mSafetyCenterConfigReader);
        mSafetyCenterDataTracker =
                new SafetyCenterDataTracker(
                        context,
                        safetyCenterResourcesContext,
                        mSafetyCenterConfigReader,
                        mSafetyCenterRefreshTracker);
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
            }
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_BOOT_COMPLETED && canUseSafetyCenter()) {
            Executor backgroundThreadExecutor = BackgroundThread.getExecutor();
            SafetyCenterEnabledListener listener = new SafetyCenterEnabledListener();
            // Ensure the listener is called first with the current state on the same thread.
            backgroundThreadExecutor.execute(listener::setInitialState);
            DeviceConfig.addOnPropertiesChangedListener(
                    DeviceConfig.NAMESPACE_PRIVACY, backgroundThreadExecutor, listener);
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
                if (!hasUpdate) {
                    return;
                }
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        userProfileGroup,
                        mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup),
                        null);
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
                SafetyCenterErrorDetails safetyCenterErrorDetails =
                        mSafetyCenterDataTracker.getSafetyCenterErrorDetails(
                                safetySourceId, errorDetails);
                if (safetyCenterErrorDetails == null && !hasUpdate) {
                    return;
                }
                SafetyCenterData safetyCenterData = null;
                if (hasUpdate) {
                    safetyCenterData =
                            mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup);
                }
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        userProfileGroup, safetyCenterData, safetyCenterErrorDetails);
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
                // TODO(b/229060064): Check if a refresh is currently in progress, and only start a
                //  new refresh if it should be replaced.
                refreshBroadcastId =
                        mSafetyCenterRefreshTracker.reportRefreshInProgress(
                                refreshReason, userProfileGroup);
            }

            RefreshTimeout refreshTimeout =
                    new RefreshTimeout(refreshBroadcastId, userProfileGroup);
            BackgroundThread.getHandler().postDelayed(refreshTimeout, REFRESH_TIMEOUT.toMillis());

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
        public SafetyCenterData getSafetyCenterData(@UserIdInt int userId) {
            getContext()
                    .enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER, "getSafetyCenterData");
            if (!enforceCrossUserPermission("getSafetyCenterData", userId)
                    || !checkApiEnabled("getSafetyCenterData")) {
                return SafetyCenterDataTracker.getDefaultSafetyCenterData();
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);

            synchronized (mApiLock) {
                return mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup);
            }
        }

        @Override
        public void addOnSafetyCenterDataChangedListener(
                @NonNull IOnSafetyCenterDataChangedListener listener, @UserIdInt int userId) {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "addOnSafetyCenterDataChangedListener");
            requireNonNull(listener);
            if (!enforceCrossUserPermission("addOnSafetyCenterDataChangedListener", userId)
                    || !checkApiEnabled("addOnSafetyCenterDataChangedListener")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            synchronized (mApiLock) {
                boolean registered = mSafetyCenterListeners.addListener(listener, userId);
                if (!registered) {
                    return;
                }
                SafetyCenterListeners.deliverUpdate(
                        listener,
                        mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup),
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
            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            enforceSameUserProfileGroup(
                    "dismissSafetyCenterIssue", userProfileGroup, safetyCenterIssueId.getUserId());
            synchronized (mApiLock) {
                SafetySourceIssue safetySourceIssue =
                        mSafetyCenterDataTracker.getSafetySourceIssue(safetyCenterIssueId);
                if (safetySourceIssue == null) {
                    Log.w(
                            TAG,
                            "Attempt to dismiss an issue that is not provided by the source, or "
                                    + "that was dismissed already");
                    // Don't send the error to the UI here, since it could happen when clicking the
                    // button multiple times in a row.
                    return;
                }
                mSafetyCenterDataTracker.dismissSafetyCenterIssue(safetyCenterIssueId);
                PendingIntent onDismissPendingIntent =
                        safetySourceIssue.getOnDismissPendingIntent();
                if (onDismissPendingIntent != null
                        && !dispatchPendingIntent(onDismissPendingIntent)) {
                    Log.w(
                            TAG,
                            "Error dispatching dismissal for issue: "
                                    + safetyCenterIssueId.getSafetySourceIssueId()
                                    + ", of source: "
                                    + safetyCenterIssueId.getSafetySourceId());
                    // We still consider the dismissal a success if there is an error dispatching
                    // the dismissal PendingIntent, since SafetyCenter won't surface this warning
                    // anymore.
                }
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        userProfileGroup,
                        mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup),
                        null);
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
            SafetyCenterIssueActionId safetyCenterIssueActionId =
                    SafetyCenterIds.issueActionIdFromString(issueActionId);
            if (!safetyCenterIssueActionId.getSafetyCenterIssueId().equals(safetyCenterIssueId)) {
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
                    safetyCenterIssueId.getUserId());
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
                                    + safetyCenterIssueActionId
                                            .getSafetyCenterIssueId()
                                            .getSafetySourceIssueId()
                                    + ", of source: "
                                    + safetyCenterIssueActionId
                                            .getSafetyCenterIssueId()
                                            .getSafetySourceId());
                    mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                            userProfileGroup,
                            null,
                            // TODO(b/229080761): Implement proper error message.
                            new SafetyCenterErrorDetails("Error executing action"));
                    return;
                }
                if (safetySourceIssueAction.willResolve()) {
                    mSafetyCenterDataTracker.markSafetyCenterIssueActionAsInFlight(
                            safetyCenterIssueActionId);
                    ResolvingActionTimeout resolvingActionTimeout =
                            new ResolvingActionTimeout(safetyCenterIssueActionId, userProfileGroup);
                    BackgroundThread.getHandler()
                            .postDelayed(
                                    resolvingActionTimeout, RESOLVING_ACTION_TIMEOUT.toMillis());
                    mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                            userProfileGroup,
                            mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup),
                            null);
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
                mSafetyCenterDataTracker.clear();
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
                mSafetyCenterDataTracker.clear();
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
                mSafetyCenterDataTracker.clear();
                // TODO(b/223550097): Should we clear the listeners here? Or should we dispatch a
                //  new listener update since the SafetyCenterData will have changed?
            }
        }

        private boolean isApiEnabled() {
            return canUseSafetyCenter() && getSafetyCenterEnabledProperty();
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
     * An {@link OnPropertiesChangedListener} for {@link #PROPERTY_SAFETY_CENTER_ENABLED} that sends
     * broadcasts when the SafetyCenter property is enabled or disabled.
     *
     * <p>This listener assumes that the {@link #PROPERTY_SAFETY_CENTER_ENABLED} value maps to
     * {@link SafetyCenterManager#isSafetyCenterEnabled()}. It should only be registered if the
     * device supports SafetyCenter and the {@link SafetyCenterConfig} was loaded successfully.
     *
     * <p>This listener is not thread-safe; it should be called on a single thread.
     */
    private final class SafetyCenterEnabledListener implements OnPropertiesChangedListener {

        private boolean mSafetyCenterEnabled;

        @Override
        public void onPropertiesChanged(DeviceConfig.Properties properties) {
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
            mSafetyCenterEnabled = getSafetyCenterEnabledProperty();
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
                mSafetyCenterDataTracker.clear();
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
                boolean hasClearedRefresh =
                        mSafetyCenterRefreshTracker.clearRefresh(mRefreshBroadcastId);
                if (!hasClearedRefresh) {
                    return;
                }
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        mUserProfileGroup,
                        mSafetyCenterDataTracker.getSafetyCenterData(mUserProfileGroup),
                        // TODO(b/229080761): Implement proper error message.
                        new SafetyCenterErrorDetails("Refresh timeout"));
            }
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
                boolean safetyCenterDataHasChanged =
                        mSafetyCenterDataTracker.unmarkSafetyCenterIssueActionAsInFlight(
                                mSafetyCenterIssueActionId);
                if (!safetyCenterDataHasChanged) {
                    return;
                }
                mSafetyCenterListeners.deliverUpdateForUserProfileGroup(
                        mUserProfileGroup,
                        mSafetyCenterDataTracker.getSafetyCenterData(mUserProfileGroup),
                        // TODO(b/229080761): Implement proper error message.
                        new SafetyCenterErrorDetails("Resolving action timeout"));
            }
        }
    }

    private boolean canUseSafetyCenter() {
        return mDeviceSupportsSafetyCenter && mConfigAvailable;
    }

    private boolean getSafetyCenterEnabledProperty() {
        // This call requires the READ_DEVICE_CONFIG permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_PRIVACY,
                    PROPERTY_SAFETY_CENTER_ENABLED,
                    /* defaultValue = */ false);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }
}
