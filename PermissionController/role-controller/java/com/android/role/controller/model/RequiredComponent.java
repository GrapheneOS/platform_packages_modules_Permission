/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.role.controller.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Specifies a required component for an application to qualify for a {@link Role}.
 */
public abstract class RequiredComponent {

    /**
     * The {@code Intent} or {@code IntentFilter} data to match the components.
     */
    @NonNull
    private final IntentFilterData mIntentFilterData;

    /**
     * The minimum target SDK version for this component to be required.
     * <p>
     * This also implies a minimum platform SDK version for this component to be required.
     */
    private final int mMinTargetSdkVersion;

    /**
     * Optional flags required to be set on a component for match to succeed.
     */
    private final int mFlags;

    /**
     * Optional permission required on a component for match to succeed.
     *
     * @see android.content.pm.ActivityInfo#permission
     * @see android.content.pm.ServiceInfo#permission
     */
    @Nullable
    private final String mPermission;

    /**
     * The query flags to match the components with.
     */
    private final int mQueryFlags;

    /**
     * The meta data required on a component for match to succeed.
     *
     * @see android.content.pm.PackageItemInfo#metaData
     */
    @NonNull
    private final List<RequiredMetaData> mMetaData;

    public RequiredComponent(@NonNull IntentFilterData intentFilterData, int minTargetSdkVersion,
            int flags, @Nullable String permission, int queryFlags,
            @NonNull List<RequiredMetaData> metaData) {
        mIntentFilterData = intentFilterData;
        mMinTargetSdkVersion = minTargetSdkVersion;
        mFlags = flags;
        mPermission = permission;
        mQueryFlags = queryFlags;
        mMetaData = metaData;
    }

    @NonNull
    public IntentFilterData getIntentFilterData() {
        return mIntentFilterData;
    }

    public int getMinTargetSdkVersion() {
        return mMinTargetSdkVersion;
    }

    /**
     * Check whether this required component is available.
     *
     * @return whether this required component is available
     */
    public boolean isAvailable() {
        // Workaround to match the value 35+ for V+ in roles.xml before SDK finalization.
        if (mMinTargetSdkVersion >= 35) {
            return SdkLevel.isAtLeastV();
        } else {
            return Build.VERSION.SDK_INT >= mMinTargetSdkVersion;
        }
    }

    /**
     * Check whether this required component is required for a package.
     *
     * @param applicationInfo the {@link ApplicationInfo} for the package
     * @return whether this required component is required
     */
    public boolean isRequired(@NonNull ApplicationInfo applicationInfo) {
        return isAvailable() && applicationInfo.targetSdkVersion >= mMinTargetSdkVersion;
    }

    public int getFlags() {
        return mFlags;
    }

    @Nullable
    public String getPermission() {
        return mPermission;
    }

    @NonNull
    public List<RequiredMetaData> getMetaData() {
        return mMetaData;
    }

    /**
     * Get the component that matches this required component within a package, if any.
     *
     * @param packageName the package name for this query
     * @param user the user of the component
     * @param context the {@code Context} to retrieve system services
     *
     * @return the matching component, or {@code null} if none.
     */
    @Nullable
    public ComponentName getQualifyingComponentForPackageAsUser(@NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        List<ComponentName> componentNames = getQualifyingComponentsAsUserInternal(packageName,
                user, context);
        return !componentNames.isEmpty() ? componentNames.get(0) : null;
    }

    /**
     * Get the list of components that match this required component, <b>at most one component per
     * package</b> and ordered from best to worst.
     *
     * @param user the user to get the qualifying components.
     * @param context the {@code Context} to retrieve system services
     *
     * @return the list of matching components
     *
     * @see Role#getQualifyingPackagesAsUser(UserHandle, Context)
     */
    @NonNull
    public List<ComponentName> getQualifyingComponentsAsUser(@NonNull UserHandle user,
            @NonNull Context context) {
        return getQualifyingComponentsAsUserInternal(null, user, context);
    }

    @NonNull
    private List<ComponentName> getQualifyingComponentsAsUserInternal(@Nullable String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        Intent intent = mIntentFilterData.createIntent();
        if (packageName != null) {
            intent.setPackage(packageName);
        }
        int queryFlags = mQueryFlags | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        boolean hasMetaData = !mMetaData.isEmpty();
        if (hasMetaData) {
            queryFlags |= PackageManager.GET_META_DATA;
        }
        List<ResolveInfo> resolveInfos = queryIntentComponentsAsUser(intent, queryFlags, user,
                context);

        ArraySet<String> componentPackageNames = new ArraySet<>();
        List<ComponentName> componentNames = new ArrayList<>();
        int resolveInfosSize = resolveInfos.size();
        for (int resolveInfosIndex = 0; resolveInfosIndex < resolveInfosSize; resolveInfosIndex++) {
            ResolveInfo resolveInfo = resolveInfos.get(resolveInfosIndex);

            if (!isComponentQualified(resolveInfo)) {
                continue;
            }

            if (mFlags != 0) {
                int componentFlags = getComponentFlags(resolveInfo);
                if ((componentFlags & mFlags) != mFlags) {
                    continue;
                }
            }

            if (mPermission != null) {
                String componentPermission = getComponentPermission(resolveInfo);
                if (!Objects.equals(componentPermission, mPermission)) {
                    continue;
                }
            }

            ComponentInfo componentInfo = getComponentComponentInfo(resolveInfo);
            if (hasMetaData) {
                Bundle componentMetaData = componentInfo.metaData;
                if (componentMetaData == null) {
                    componentMetaData = Bundle.EMPTY;
                }
                boolean isMetaDataQualified = true;
                int metaDataSize = mMetaData.size();
                for (int metaDataIndex = 0; metaDataIndex < metaDataSize; metaDataIndex++) {
                    RequiredMetaData metaData = mMetaData.get(metaDataIndex);

                    if (!metaData.isQualified(componentMetaData)) {
                        isMetaDataQualified = false;
                        break;
                    }
                }
                if (!isMetaDataQualified) {
                    continue;
                }
            }

            String componentPackageName = componentInfo.packageName;
            if (componentPackageNames.contains(componentPackageName)) {
                continue;
            }
            componentPackageNames.add(componentPackageName);

            ComponentName componentName = new ComponentName(componentPackageName,
                    componentInfo.name);
            componentNames.add(componentName);
        }
        return componentNames;
    }

    /**
     * Query the {@code PackageManager} for components matching an {@code Intent}, ordered from best
     * to worst.
     *
     * @param intent the {@code Intent} to match against
     * @param flags the flags for this query
     * @param user the user for this query
     * @param context the {@code Context} to retrieve system services
     *
     * @return the list of matching components
     */
    @NonNull
    protected abstract List<ResolveInfo> queryIntentComponentsAsUser(@NonNull Intent intent,
            int flags, @NonNull UserHandle user, @NonNull Context context);

    protected boolean isComponentQualified(@NonNull ResolveInfo resolveInfo) {
        return true;
    }

    /**
     * Get the {@code ComponentInfo} of a component.
     *
     * @param resolveInfo the {@code ResolveInfo} of the component
     *
     * @return the {@code ComponentInfo} of the component
     */
    @NonNull
    protected abstract ComponentInfo getComponentComponentInfo(@NonNull ResolveInfo resolveInfo);

    /**
     * Get the flags that have been set on a component.
     *
     * @param resolveInfo the {@code ResolveInfo} of the component
     *
     * @return the flags that have been set on a component
     */
    protected abstract int getComponentFlags(@NonNull ResolveInfo resolveInfo);

    /**
     * Get the permission required to access a component.
     *
     * @param resolveInfo the {@code ResolveInfo} of the component
     *
     * @return the permission required to access a component
     */
    @Nullable
    protected abstract String getComponentPermission(@NonNull ResolveInfo resolveInfo);

    @Override
    public String toString() {
        return "RequiredComponent{"
                + "mIntentFilterData=" + mIntentFilterData
                + ", mMinTargetSdkVersion=" + mMinTargetSdkVersion
                + ", mFlags='" + mFlags + '\''
                + ", mPermission='" + mPermission + '\''
                + ", mQueryFlags=" + mQueryFlags
                + ", mMetaData=" + mMetaData
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        RequiredComponent that = (RequiredComponent) object;
        return Objects.equals(mIntentFilterData, that.mIntentFilterData)
                && Objects.equals(mMinTargetSdkVersion, that.mMinTargetSdkVersion)
                && mFlags == that.mFlags
                && Objects.equals(mPermission, that.mPermission)
                && mQueryFlags == that.mQueryFlags
                && Objects.equals(mMetaData, that.mMetaData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIntentFilterData, mMinTargetSdkVersion, mFlags, mPermission,
                mQueryFlags, mMetaData);
    }
}
