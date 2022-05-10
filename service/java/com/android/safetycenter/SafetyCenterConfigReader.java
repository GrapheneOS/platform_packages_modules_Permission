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

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.config.ParseException;
import com.android.safetycenter.config.SafetyCenterConfigParser;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class that reads the {@link SafetyCenterConfig} and allows overriding it for tests.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterConfigReader {

    private static final String TAG = "SafetyCenterConfigReade";

    @NonNull private final SafetyCenterResourcesContext mSafetyCenterResourcesContext;

    @Nullable private SafetyCenterConfigInternal mConfigInternalFromXml;

    @Nullable private SafetyCenterConfigInternal mConfigInternalOverrideForTests;

    /** Creates a {@link SafetyCenterConfigReader} from a {@link SafetyCenterResourcesContext}. */
    SafetyCenterConfigReader(@NonNull SafetyCenterResourcesContext safetyCenterResourcesContext) {
        mSafetyCenterResourcesContext = safetyCenterResourcesContext;
    }

    /**
     * Loads the {@link SafetyCenterConfig} from the XML file defined in {@code
     * safety_center_config.xml}; and returns whether this was successful.
     *
     * <p>This method must be called prior to any other call to this class. This call must also be
     * successful; interacting with this class requires checking that the boolean value returned by
     * this method was {@code true}.
     */
    boolean loadConfig() {
        SafetyCenterConfig safetyCenterConfig = readSafetyCenterConfig();
        if (safetyCenterConfig == null) {
            return false;
        }
        mConfigInternalFromXml = SafetyCenterConfigInternal.from(safetyCenterConfig);
        return true;
    }

    /**
     * Sets an override {@link SafetyCenterConfig} for tests.
     *
     * <p>When set, information provided by this class will be based on the overridden {@link
     * SafetyCenterConfig}.
     */
    void setConfigOverrideForTests(@NonNull SafetyCenterConfig safetyCenterConfig) {
        mConfigInternalOverrideForTests = SafetyCenterConfigInternal.from(safetyCenterConfig);
    }

    /**
     * Clears the {@link SafetyCenterConfig} override set by {@link
     * #setConfigOverrideForTests(SafetyCenterConfig)}, if any.
     */
    void clearConfigOverrideForTests() {
        mConfigInternalOverrideForTests = null;
    }

    /** Returns the currently active {@link SafetyCenterConfig}. */
    @NonNull
    SafetyCenterConfig getSafetyCenterConfig() {
        return getCurrentConfigInternal().getSafetyCenterConfig();
    }

    /** Returns the groups of {@link SafetySource}, in the order expected by the UI. */
    @NonNull
    List<SafetySourcesGroup> getSafetySourcesGroups() {
        return getCurrentConfigInternal().getSafetyCenterConfig().getSafetySourcesGroups();
    }

    /**
     * Returns the external {@link SafetySource} associated with the {@code safetySourceId}, if any.
     *
     * <p>The returned {@link SafetySource} can either be associated with the XML or overridden
     * {@link SafetyCenterConfig}; {@link #isExternalSafetySourceActive(String)} can be used to
     * check if it is associated with the current {@link SafetyCenterConfig}. This is to continue
     * allowing sources from the XML config to interact with SafetCenter during tests (but their
     * calls will be no-oped).
     */
    @Nullable
    SafetySource getExternalSafetySource(@NonNull String safetySourceId) {
        SafetySource safetySourceInCurrentConfig =
                getCurrentConfigInternal().getExternalSafetySources().get(safetySourceId);
        if (safetySourceInCurrentConfig != null) {
            return safetySourceInCurrentConfig;
        }

        return mConfigInternalFromXml.getExternalSafetySources().get(safetySourceId);
    }

    /**
     * Returns whether the {@code safetySourceId} is associated with an external {@link
     * SafetySource} that is currently active.
     */
    boolean isExternalSafetySourceActive(@NonNull String safetySourceId) {
        return getCurrentConfigInternal().getExternalSafetySources().containsKey(safetySourceId);
    }

    /**
     * Returns the {@link Broadcast} defined in the {@link SafetyCenterConfig}, with all the sources
     * that they should handle and the profile on which they should be dispatched.
     */
    @NonNull
    List<Broadcast> getBroadcasts() {
        return getCurrentConfigInternal().getBroadcasts();
    }

    @NonNull
    private SafetyCenterConfigInternal getCurrentConfigInternal() {
        // We require the XML config must be loaded successfully for SafetyCenterManager APIs to
        // function, regardless of whether the config is subsequently overridden.
        requireNonNull(mConfigInternalFromXml);

        if (mConfigInternalOverrideForTests == null) {
            return mConfigInternalFromXml;
        }

        return mConfigInternalOverrideForTests;
    }

    @Nullable
    private SafetyCenterConfig readSafetyCenterConfig() {
        InputStream in = mSafetyCenterResourcesContext.getSafetyCenterConfig();
        if (in == null) {
            Log.e(TAG, "Cannot get safety center config file");
            return null;
        }

        Resources resources = mSafetyCenterResourcesContext.getResources();
        if (resources == null) {
            Log.e(TAG, "Cannot get safety center resources");
            return null;
        }

        try {
            SafetyCenterConfig safetyCenterConfig =
                    SafetyCenterConfigParser.parseXmlResource(in, resources);
            Log.i(TAG, "SafetyCenterConfig read successfully");
            return safetyCenterConfig;
        } catch (ParseException e) {
            Log.e(TAG, "Cannot read SafetyCenterConfig", e);
            return null;
        }
    }

    /** A wrapper class around the parsed XML config. */
    private static final class SafetyCenterConfigInternal {

        @NonNull private final SafetyCenterConfig mConfig;
        @NonNull private final ArrayMap<String, SafetySource> mExternalSafetySources;
        @NonNull private final List<Broadcast> mBroadcasts;

        private SafetyCenterConfigInternal(
                @NonNull SafetyCenterConfig safetyCenterConfig,
                @NonNull ArrayMap<String, SafetySource> externalSafetySources,
                @NonNull List<Broadcast> broadcasts) {
            mConfig = safetyCenterConfig;
            mExternalSafetySources = externalSafetySources;
            mBroadcasts = broadcasts;
        }

        @NonNull
        private SafetyCenterConfig getSafetyCenterConfig() {
            return mConfig;
        }

        @NonNull
        private ArrayMap<String, SafetySource> getExternalSafetySources() {
            return mExternalSafetySources;
        }

        @NonNull
        private List<Broadcast> getBroadcasts() {
            return mBroadcasts;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SafetyCenterConfigInternal)) return false;
            SafetyCenterConfigInternal configInternal = (SafetyCenterConfigInternal) o;
            return mConfig.equals(configInternal.mConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mConfig);
        }

        @Override
        public String toString() {
            return "SafetyCenterConfigInternal{"
                    + "mConfig="
                    + mConfig
                    + ", mExternalSafetySources="
                    + mExternalSafetySources
                    + ", mBroadcasts="
                    + mBroadcasts
                    + '}';
        }

        @NonNull
        private static SafetyCenterConfigInternal from(
                @NonNull SafetyCenterConfig safetyCenterConfig) {
            return new SafetyCenterConfigInternal(
                    safetyCenterConfig,
                    extractExternalSafetySources(safetyCenterConfig),
                    unmodifiableList(extractBroadcasts(safetyCenterConfig)));
        }

        @NonNull
        private static ArrayMap<String, SafetySource> extractExternalSafetySources(
                @NonNull SafetyCenterConfig safetyCenterConfig) {
            ArrayMap<String, SafetySource> externalSafetySources = new ArrayMap<>();
            List<SafetySourcesGroup> safetySourcesGroups =
                    safetyCenterConfig.getSafetySourcesGroups();
            for (int i = 0; i < safetySourcesGroups.size(); i++) {
                SafetySourcesGroup safetySourcesGroup = safetySourcesGroups.get(i);

                List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
                for (int j = 0; j < safetySources.size(); j++) {
                    SafetySource safetySource = safetySources.get(j);

                    if (!SafetySources.isExternal(safetySource)) {
                        continue;
                    }

                    externalSafetySources.put(safetySource.getId(), safetySource);
                }
            }

            return externalSafetySources;
        }

        @NonNull
        private static List<Broadcast> extractBroadcasts(
                @NonNull SafetyCenterConfig safetyCenterConfig) {
            ArrayMap<String, Broadcast> packageNameToBroadcast = new ArrayMap<>();
            List<Broadcast> broadcasts = new ArrayList<>();
            List<SafetySourcesGroup> safetySourcesGroups =
                    safetyCenterConfig.getSafetySourcesGroups();
            for (int i = 0; i < safetySourcesGroups.size(); i++) {
                SafetySourcesGroup safetySourcesGroup = safetySourcesGroups.get(i);

                List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
                for (int j = 0; j < safetySources.size(); j++) {
                    SafetySource safetySource = safetySources.get(j);

                    if (!SafetySources.isExternal(safetySource)) {
                        continue;
                    }

                    Broadcast broadcast = packageNameToBroadcast.get(safetySource.getPackageName());
                    if (broadcast == null) {
                        broadcast = new Broadcast(safetySource.getPackageName());
                        packageNameToBroadcast.put(safetySource.getPackageName(), broadcast);
                        broadcasts.add(broadcast);
                    }
                    broadcast.mSourceIdsForProfileOwner.add(safetySource.getId());
                    if (safetySource.isRefreshOnPageOpenAllowed()) {
                        broadcast.mSourceIdsForProfileOwnerOnPageOpen.add(safetySource.getId());
                    }
                    // TODO(b/217688797): This might also be handled by the source directly.
                    boolean needsManagedProfilesBroadcast =
                            SafetySources.supportsManagedProfiles(safetySource);
                    if (needsManagedProfilesBroadcast) {
                        broadcast.mSourceIdsForManagedProfiles.add(safetySource.getId());
                        if (safetySource.isRefreshOnPageOpenAllowed()) {
                            broadcast.mSourceIdsForManagedProfilesOnPageOpen.add(
                                    safetySource.getId());
                        }
                    }
                }
            }

            return broadcasts;
        }
    }

    /** A class that represents a broadcast to be sent to safety sources. */
    static final class Broadcast {

        @NonNull private final String mPackageName;

        private final List<String> mSourceIdsForProfileOwner = new ArrayList<>();
        private final List<String> mSourceIdsForProfileOwnerOnPageOpen = new ArrayList<>();
        private final List<String> mSourceIdsForManagedProfiles = new ArrayList<>();
        private final List<String> mSourceIdsForManagedProfilesOnPageOpen = new ArrayList<>();

        private Broadcast(@NonNull String packageName) {
            mPackageName = packageName;
        }

        /** Returns the package name to dispatch the broadcast to. */
        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        /**
         * Returns the safety source ids associated with this broadcast in the profile owner.
         *
         * <p>If this list is empty, there are no sources to dispatch to in the profile owner.
         *
         * @param refreshReason the {@link RefreshReason} for the broadcast
         */
        @NonNull
        public List<String> getSourceIdsForProfileOwner(@RefreshReason int refreshReason) {
            if (refreshReason == SafetyCenterManager.REFRESH_REASON_PAGE_OPEN) {
                return unmodifiableList(mSourceIdsForProfileOwnerOnPageOpen);
            }
            return unmodifiableList(mSourceIdsForProfileOwner);
        }

        /**
         * Returns the safety source ids associated with this broadcast in the managed profile(s).
         *
         * <p>If this list is empty, there are no sources to dispatch to in the managed profile(s).
         *
         * @param refreshReason the {@link RefreshReason} for the broadcast
         */
        @NonNull
        public List<String> getSourceIdsForManagedProfiles(@RefreshReason int refreshReason) {
            if (refreshReason == SafetyCenterManager.REFRESH_REASON_PAGE_OPEN) {
                return unmodifiableList(mSourceIdsForManagedProfilesOnPageOpen);
            }
            return unmodifiableList(mSourceIdsForManagedProfiles);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Broadcast)) return false;
            Broadcast that = (Broadcast) o;
            return mPackageName.equals(that.mPackageName)
                    && mSourceIdsForProfileOwner.equals(that.mSourceIdsForProfileOwner)
                    && mSourceIdsForProfileOwnerOnPageOpen.equals(
                            that.mSourceIdsForProfileOwnerOnPageOpen)
                    && mSourceIdsForManagedProfiles.equals(that.mSourceIdsForManagedProfiles)
                    && mSourceIdsForManagedProfilesOnPageOpen.equals(
                            that.mSourceIdsForManagedProfilesOnPageOpen);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mPackageName,
                    mSourceIdsForProfileOwner,
                    mSourceIdsForProfileOwnerOnPageOpen,
                    mSourceIdsForManagedProfiles,
                    mSourceIdsForManagedProfilesOnPageOpen);
        }

        @Override
        public String toString() {
            return "Broadcast{"
                    + "mPackageName="
                    + mPackageName
                    + ", mSourceIdsForProfileOwner="
                    + mSourceIdsForProfileOwner
                    + ", mSourceIdsForProfileOwnerOnPageOpen="
                    + mSourceIdsForProfileOwnerOnPageOpen
                    + ", mSourceIdsForManagedProfiles="
                    + mSourceIdsForManagedProfiles
                    + ", mSourceIdsForManagedProfilesOnPageOpen="
                    + mSourceIdsForManagedProfilesOnPageOpen
                    + '}';
        }
    }
}
