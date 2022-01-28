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
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.PowerExemptionManager.REASON_REFRESH_SAFETY_SOURCES;
import static android.os.PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED;
import static android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK;
import static android.safetycenter.SafetyCenterManager.RefreshReason;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.safetycenter.IOnSafetyCenterDataChangedListener;
import android.safetycenter.ISafetyCenterManager;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetySourceData;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.permission.util.PermissionUtils;
import com.android.safetycenter.config.Parser;
import com.android.safetycenter.config.parser.SafetyCenterConfig;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;
import com.android.server.SystemService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * Time for which an app, upon receiving a particular broadcast, will be placed on a temporary
     * power allowlist allowing it to start a foreground service from the background.
     */
    private static final Long ALLOWLIST_DURATION_MILLIS = 20000L;

    @NonNull
    private final Object mLock = new Object();

    // TODO(b/202386571): Create a new data model to store both config and dynamic data in memory.
    @GuardedBy("mLock")
    @NonNull
    private final Map<Key, SafetySourceData> mSafetySourceDataForKey = new HashMap<>();
    @Nullable
    private SafetyCenterConfig mSafetyCenterConfig;

    // TODO(b/202387070): Send updates to SafetyCenterData out to listeners.
    @GuardedBy("mLock")
    private final List<IOnSafetyCenterDataChangedListener> mSafetyCenterDataChangedListeners =
            new ArrayList<>();

    @NonNull
    private final AppOpsManager mAppOpsManager;

    @NonNull
    private final SafetyCenterResourcesContext mSafetyCenterResourcesContext;

    public SafetyCenterService(@NonNull Context context) {
        super(context);
        mAppOpsManager = requireNonNull(context.getSystemService(AppOpsManager.class));
        mSafetyCenterResourcesContext = new SafetyCenterResourcesContext(context);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.SAFETY_CENTER_SERVICE, new Stub());
        readSafetyCenterConfig();
    }

    private void readSafetyCenterConfig() {
        // TODO(b/214568975): Decide if we should disable Safety Center if there is a problem
        // reading the config.
        String resoursePkgName = mSafetyCenterResourcesContext.getResourcesApkPkgName();
        if (resoursePkgName == null) {
            Log.e(TAG, "Cannot get Safety Center resources");
            return;
        }
        InputStream in = mSafetyCenterResourcesContext.getSafetyCenterConfig();
        if (in == null) {
            Log.e(TAG, "Cannot get Safety Center config");
            return;
        }
        try {
            mSafetyCenterConfig = Parser.parse(in, resoursePkgName,
                    mSafetyCenterResourcesContext.getResources());
            Log.i(TAG, "Safety Center config read successfully");
        } catch (Parser.ParseException e) {
            Log.e(TAG, "Cannot read Safety Center config", e);
        }
    }

    private static final class Key {
        @NonNull
        private final String mPackageName;
        private final int mUserId;
        @NonNull
        private final String mSafetySourceId;

        private Key(@NonNull String packageName, int userId, @NonNull String safetySourceId) {
            this.mPackageName = packageName;
            this.mUserId = userId;
            this.mSafetySourceId = safetySourceId;
        }

        @NonNull
        private static Key of(@NonNull String packageName, int userId,
                @NonNull String safetySourceId) {
            return new Key(packageName, userId, safetySourceId);
        }

        @Override
        public String toString() {
            return "Key{"
                    + "mPackageName='"
                    + mPackageName
                    + '\''
                    + ", mUserId="
                    + mUserId
                    + ", mSafetySourceId='"
                    + mSafetySourceId
                    + '\''
                    + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return mUserId == key.mUserId && mPackageName.equals(key.mPackageName)
                    && mSafetySourceId.equals(key.mSafetySourceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPackageName, mUserId, mSafetySourceId);
        }
    }

    private final class Stub extends ISafetyCenterManager.Stub {
        @Override
        public void sendSafetyCenterUpdate(@NonNull String packageName, @UserIdInt int userId,
                @NonNull SafetySourceData safetySourceData) {
            mAppOpsManager.checkPackage(Binder.getCallingUid(), packageName);
            PermissionUtils.enforceCrossUserPermission(userId, false, "sendSafetyCenterUpdate",
                    getContext());
            getContext().enforceCallingOrSelfPermission(SEND_SAFETY_CENTER_UPDATE,
                    "sendSafetyCenterUpdate");
            // TODO(b/205706756): Security: check certs?
            // TODO(b/203098016): Implement merging logic.
            synchronized (mLock) {
                mSafetySourceDataForKey.put(Key.of(packageName, userId, safetySourceData.getId()),
                        safetySourceData);
            }
        }

        @Override
        @Nullable
        public SafetySourceData getLastSafetyCenterUpdate(@NonNull String packageName,
                @UserIdInt int userId,
                @NonNull String safetySourceId) {
            mAppOpsManager.checkPackage(Binder.getCallingUid(), packageName);
            PermissionUtils.enforceCrossUserPermission(userId, false, "getLastSafetyCenterUpdate",
                    getContext());
            getContext().enforceCallingOrSelfPermission(SEND_SAFETY_CENTER_UPDATE,
                    "getLastSafetyCenterUpdate");
            // TODO(b/205706756): Security: check certs?
            synchronized (mLock) {
                return mSafetySourceDataForKey.get(Key.of(packageName, userId, safetySourceId));
            }
        }

        @Override
        public boolean isSafetyCenterEnabled() {
            enforceIsSafetyCenterEnabledPermissions("isSafetyCenterEnabled");

            // We don't require the caller to have READ_DEVICE_CONFIG permission.
            final long callingId = Binder.clearCallingIdentity();
            try {
                return DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_PRIVACY,
                        PROPERTY_SAFETY_CENTER_ENABLED,
                        /* defaultValue = */ false)
                        && getSafetyCenterConfigValue();
            } finally {
                Binder.restoreCallingIdentity(callingId);
            }
        }

        @Override
        public void refreshSafetySources(@RefreshReason int refreshReason) {
            getContext().enforceCallingPermission(MANAGE_SAFETY_CENTER,
                    "refreshSafetySources");

            int requestType;
            switch (refreshReason) {
                case REFRESH_REASON_RESCAN_BUTTON_CLICK:
                    requestType = EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
                    break;
                case REFRESH_REASON_PAGE_OPEN:
                    requestType = EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid refresh reason: " + refreshReason);
            }

            // TODO(b/215145516): Send explicit intents to safety sources instead.
            Intent broadcastIntent = new Intent(ACTION_REFRESH_SAFETY_SOURCES)
                    .putExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, requestType)
                    .setFlags(FLAG_RECEIVER_FOREGROUND);

            // We don't require the caller to have INTERACT_ACROSS_USERS and
            // START_FOREGROUND_SERVICES_FROM_BACKGROUND permissions.
            final long callingId = Binder.clearCallingIdentity();
            try {
                BroadcastOptions broadcastOptions = BroadcastOptions.makeBasic();
                // The following operation requires START_FOREGROUND_SERVICES_FROM_BACKGROUND
                // permission.
                broadcastOptions.setTemporaryAppAllowlist(ALLOWLIST_DURATION_MILLIS,
                        TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                        REASON_REFRESH_SAFETY_SOURCES,
                        "Safety Center is requesting data from safety sources");

                // TODO(b/215144069): Add cross profile support for safety sources which support
                //  both personal and work profile. This implementation invokes
                //  `sendBroadcastAsUser` in order to invoke the permission.
                // TODO(b/215165724): Add receiver permission `SEND_SAFETY_CENTER_UPDATE`.
                // The following operation requires INTERACT_ACROSS_USERS permission.
                getContext().sendBroadcastAsUser(broadcastIntent,
                        UserHandle.CURRENT,
                        null /* receiverPermission */,
                        broadcastOptions.toBundle());
            } finally {
                Binder.restoreCallingIdentity(callingId);
            }
        }

        @Override
        public void clearSafetyCenterData() {
            getContext().enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER,
                    "clearSafetyCenterData");

            synchronized (mLock) {
                mSafetySourceDataForKey.clear();
            }
        }

        @Override
        public SafetyCenterData getSafetyCenterData() {
            getContext().enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER,
                    "getSafetyCenterData");
            // TODO(b/202386935): Implement this with real merged data.
            return new SafetyCenterData(
                    new SafetyCenterStatus.Builder()
                            .setSeverityLevel(
                                    SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
                            .setTitle("Safety Center Unimplemented")
                            .setSummary("This should be implemented.")
                            .build(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>());
        }

        @Override
        public void addOnSafetyCenterDataChangedListener(
                IOnSafetyCenterDataChangedListener listener) {
            getContext().enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER,
                    "addOnSafetyCenterDataChangedListener");
            synchronized (mLock) {
                mSafetyCenterDataChangedListeners.add(listener);
            }
        }

        @Override
        public void removeOnSafetyCenterDataChangedListener(
                IOnSafetyCenterDataChangedListener listener) {
            getContext().enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER,
                    "removeOnSafetyCenterDataChangedListener");
            synchronized (mLock) {
                mSafetyCenterDataChangedListeners.remove(listener);
            }
        }

        @Override
        public void dismissSafetyIssue(String issueId) {
            getContext().enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER,
                    "dismissSafetyIssue");
            // TODO(b/202387059): Implement issue dismissal
        }

        private boolean getSafetyCenterConfigValue() {
            return getContext().getResources().getBoolean(Resources.getSystem().getIdentifier(
                    "config_enableSafetyCenter",
                    "bool",
                    "android"));
        }

        private void enforceIsSafetyCenterEnabledPermissions(@NonNull String message) {
            if (getContext().checkCallingOrSelfPermission(READ_SAFETY_CENTER_STATUS)
                    != PackageManager.PERMISSION_GRANTED
                    && getContext().checkCallingOrSelfPermission(SEND_SAFETY_CENTER_UPDATE)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException(message + " requires "
                        + READ_SAFETY_CENTER_STATUS + " or "
                        + SEND_SAFETY_CENTER_UPDATE);
            }
        }
    }
}
