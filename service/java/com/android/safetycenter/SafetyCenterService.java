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
import android.os.RemoteCallbackList;
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
    private static final Duration RESOLVE_ACTION_TIMEOUT = Duration.ofSeconds(10);

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

            SafetyCenterData safetyCenterData;
            List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> listeners;
            synchronized (mApiLock) {
                boolean hasUpdate =
                        mSafetyCenterDataTracker.setSafetySourceData(
                                safetySourceData, safetySourceId, safetyEvent, packageName, userId);
                if (!hasUpdate) {
                    return;
                }
                safetyCenterData = mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup);
                listeners = mSafetyCenterListeners.getListeners(userProfileGroup);
            }

            // TODO(b/228832622): Ensure listeners are called only when data changes.
            SafetyCenterListeners.deliverUpdate(listeners, safetyCenterData, null);
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

            SafetyCenterData safetyCenterData = null;
            SafetyCenterErrorDetails safetyCenterErrorDetails;
            List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> listeners;
            synchronized (mApiLock) {
                boolean hasUpdate =
                        mSafetyCenterDataTracker.reportSafetySourceError(
                                errorDetails, safetySourceId, packageName, userId);
                safetyCenterErrorDetails =
                        mSafetyCenterDataTracker.getSafetyCenterErrorDetails(
                                safetySourceId, errorDetails);
                if (safetyCenterErrorDetails == null && !hasUpdate) {
                    return;
                }
                if (hasUpdate) {
                    safetyCenterData =
                            mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup);
                }
                listeners = mSafetyCenterListeners.getListeners(userProfileGroup);
            }

            // TODO(b/228832622): Ensure listeners are called only when data changes.
            SafetyCenterListeners.deliverUpdate(
                    listeners, safetyCenterData, safetyCenterErrorDetails);
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

            SafetyCenterData safetyCenterData;
            synchronized (mApiLock) {
                boolean registered = mSafetyCenterListeners.addListener(listener, userId);
                if (!registered) {
                    return;
                }
                safetyCenterData = mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup);
            }

            SafetyCenterListeners.deliverUpdate(listener, safetyCenterData, null);
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

            SafetySourceIssue safetySourceIssue;
            SafetyCenterData safetyCenterData;
            List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> listeners;
            synchronized (mApiLock) {
                safetySourceIssue =
                        mSafetyCenterDataTracker.getSafetySourceIssue(safetyCenterIssueId);
                if (safetySourceIssue == null) {
                    Log.w(
                            TAG,
                            "Attempt to dismiss an issue that is not provided by the source, or "
                                    + "that was dismissed already");
                    return;
                }
                mSafetyCenterDataTracker.dismissSafetyCenterIssue(safetyCenterIssueId);
                safetyCenterData = mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup);
                listeners = mSafetyCenterListeners.getListeners(userProfileGroup);
            }

            // TODO(b/228832622): Ensure listeners are called only when data changes.
            SafetyCenterListeners.deliverUpdate(listeners, safetyCenterData, null);

            PendingIntent onDismissPendingIntent = safetySourceIssue.getOnDismissPendingIntent();
            if (onDismissPendingIntent != null) {
                dispatchPendingIntent(onDismissPendingIntent);
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
                        String.format(
                                "issueId: %s and issueActionId: %s do not match",
                                safetyCenterIssueId, safetyCenterIssueActionId));
            }
            UserProfileGroup userProfileGroup = UserProfileGroup.from(getContext(), userId);
            enforceSameUserProfileGroup(
                    "executeSafetyCenterIssueAction",
                    userProfileGroup,
                    safetyCenterIssueId.getUserId());

            SafetySourceIssue.Action safetySourceIssueAction;
            SafetyCenterData safetyCenterData = null;
            List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> listeners = null;
            synchronized (mApiLock) {
                safetySourceIssueAction =
                        mSafetyCenterDataTracker.getSafetySourceIssueAction(
                                safetyCenterIssueActionId);
                if (safetySourceIssueAction == null) {
                    Log.w(
                            TAG,
                            "Attempt to execute an issue action that is not provided by the source,"
                                    + " that was dismissed, or is already in flight");
                    return;
                }
                if (safetySourceIssueAction.willResolve()) {
                    mSafetyCenterDataTracker.markSafetyCenterIssueActionAsInFlight(
                            safetyCenterIssueActionId);
                    safetyCenterData =
                            mSafetyCenterDataTracker.getSafetyCenterData(userProfileGroup);
                    listeners = mSafetyCenterListeners.getListeners(userProfileGroup);
                }
            }

            if (safetySourceIssueAction.willResolve()) {
                ResolvingActionTimeout resolvingActionTimeout =
                        new ResolvingActionTimeout(safetyCenterIssueActionId, userProfileGroup);
                BackgroundThread.getHandler()
                        .postDelayed(resolvingActionTimeout, RESOLVE_ACTION_TIMEOUT.toMillis());
            }

            if (listeners != null) {
                // TODO(b/228832622): Ensure listeners are called only when data changes.
                SafetyCenterListeners.deliverUpdate(listeners, safetyCenterData, null);
            }
            // TODO(b/229080116): Unmark as in flight if there is an issue dispatching the
            //  PendingIntent.
            dispatchPendingIntent(safetySourceIssueAction.getPendingIntent());
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
                    String.format(
                            "%s requires any of: %s, but none were granted",
                            message, Arrays.toString(permissions)));
        }

        /** Enforces cross user permission and returns whether the user is existent. */
        private boolean enforceCrossUserPermission(@NonNull String message, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false, message, getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(
                        TAG,
                        String.format(
                                "Called %s with user id %s, which does not correspond to an"
                                        + " existing user",
                                message, userId));
                return false;
            }
            // TODO(b/223132917): Check if user is enabled, running and/or if quiet mode is enabled?
            return true;
        }

        private boolean checkApiEnabled(@NonNull String message) {
            if (!isApiEnabled()) {
                Log.w(TAG, String.format("Called %s, but Safety Center is disabled", message));
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
                        String.format(
                                "%s requires target user id %s to be within the same profile group"
                                        + " of the caller: %s",
                                message, userId, userProfileGroup));
            }
        }

        private void dispatchPendingIntent(@NonNull PendingIntent pendingIntent) {
            try {
                pendingIntent.send();
            } catch (PendingIntent.CanceledException ex) {
                Log.w(TAG, "Couldn't dispatch PendingIntent", ex);
                // TODO(b/229080116): Propagate error with listeners here?
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
            List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> listeners;
            SafetyCenterData safetyCenterData;
            synchronized (mApiLock) {
                boolean hasClearedRefresh =
                        mSafetyCenterRefreshTracker.clearRefresh(mRefreshBroadcastId);
                if (!hasClearedRefresh) {
                    return;
                }
                safetyCenterData = mSafetyCenterDataTracker.getSafetyCenterData(mUserProfileGroup);
                listeners = mSafetyCenterListeners.getListeners(mUserProfileGroup);
            }

            // TODO(b/228832622): Ensure listeners are called only when data changes.
            SafetyCenterListeners.deliverUpdate(
                    listeners,
                    safetyCenterData,
                    // TODO(b/229080761): Implement proper error message.
                    new SafetyCenterErrorDetails("Refresh timeout"));
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
            List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> listeners;
            SafetyCenterData safetyCenterData;
            synchronized (mApiLock) {
                boolean hasClearedInFlightAction =
                        mSafetyCenterDataTracker.unmarkSafetyCenterIssueActionAsInFlight(
                                mSafetyCenterIssueActionId);
                if (!hasClearedInFlightAction) {
                    return;
                }
                safetyCenterData = mSafetyCenterDataTracker.getSafetyCenterData(mUserProfileGroup);
                listeners = mSafetyCenterListeners.getListeners(mUserProfileGroup);
            }

            // TODO(b/228832622): Ensure listeners are called only when data changes.
            SafetyCenterListeners.deliverUpdate(
                    listeners,
                    safetyCenterData,
                    // TODO(b/229080761): Implement proper error message.
                    new SafetyCenterErrorDetails("Resolve action timeout"));
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
