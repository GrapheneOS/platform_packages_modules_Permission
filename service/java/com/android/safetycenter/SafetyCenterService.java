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

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.provider.DeviceConfig;
import android.safetycenter.IOnSafetyCenterDataChangedListener;
import android.safetycenter.ISafetyCenterManager;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterStatus;
import android.safetycenter.SafetySourceData;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.permission.util.PermissionUtils;
import com.android.server.SystemService;

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

    /** Phenotype flag that determines whether SafetyCenter is enabled. */
    private static final String PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled";

    @NonNull
    private final Object mLock = new Object();
    // TODO(b/206789604): Use persistent storage instead.
    @GuardedBy("mLock")
    @NonNull
    private final Map<Key, SafetySourceData> mSafetySourceDataForKey = new HashMap<>();

    @NonNull
    private final AppOpsManager mAppOpsManager;

    @NonNull
    private final List<IOnSafetyCenterDataChangedListener> mSafetyCenterDataChangedListeners =
            new ArrayList<>();

    public SafetyCenterService(@NonNull Context context) {
        super(context);
        mAppOpsManager = requireNonNull(context.getSystemService(AppOpsManager.class));
    }

    @Override
    public void onStart() {
        publishBinderService(Context.SAFETY_CENTER_SERVICE, new Stub());
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
        public void clearSafetyCenterData() {
            getContext().enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER,
                    "clearSafetyCenterData");

            synchronized (mLock) {
                mSafetySourceDataForKey.clear();
            }
        }

        @Override
        public SafetyCenterData getSafetyCenterData() {
            // TODO(b/203098016): Implement this with real data.
            return new SafetyCenterData(
                    new SafetyCenterStatus.Builder()
                            .setSeverityLevel(
                                    SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
                            .setTitle("Safety Center Unimplemented")
                            .setSummary("This should be implemented.")
                            .build(),
                    new ArrayList<>(),
                    new ArrayList<>());
        }

        @Override
        public void addOnSafetyCenterDataChangedListener(
                IOnSafetyCenterDataChangedListener listener) {
            // TODO(b/203098016): Protect this with a permission so only PC can use.
            mSafetyCenterDataChangedListeners.add(listener);
        }

        @Override
        public void removeOnSafetyCenterDataChangedListener(
                IOnSafetyCenterDataChangedListener listener) {
            // TODO(b/203098016): Protect this with a permission so only PC can use.
            mSafetyCenterDataChangedListeners.remove(listener);
        }

        @Override
        public void dismissSafetyIssue(String issueId) {
            // TODO(b/203098016): Implement this with real data.
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
