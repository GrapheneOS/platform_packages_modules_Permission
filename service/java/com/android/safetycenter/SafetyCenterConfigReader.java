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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.XmlResourceParser;
import android.safetycenter.config.ParseException;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A class that reads the {@link SafetyCenterConfigInternal} from the associated {@link
 * SafetyCenterResourcesContext}.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
final class SafetyCenterConfigReader {

    private static final String TAG = "SafetyCenterConfigReade";

    @NonNull
    private final SafetyCenterResourcesContext mSafetyCenterResourcesContext;

    @Nullable
    private SafetyCenterConfigInternal mConfigInternalFromXml;

    @Nullable
    private SafetyCenterConfigInternal mConfigInternalOverrideForTests;

    /** Creates a {@link SafetyCenterConfigReader} from a {@link SafetyCenterResourcesContext}. */
    SafetyCenterConfigReader(@NonNull SafetyCenterResourcesContext safetyCenterResourcesContext) {
        mSafetyCenterResourcesContext = safetyCenterResourcesContext;
    }

    /**
     * Returns the {@link SafetyCenterConfigInternal} currently active to configure safety
     * sources for the {@link android.safetycenter.SafetyCenterManager} APIs.
     *
     * <p>Note: Only call this method if {@link #loadConfig()} has successfully parsed the XML
     * {@link SafetyCenterConfig} and returned {@code true}. If called before the config has been
     * loaded successfully, this method will throw a {@link NullPointerException}.
     */
    @NonNull
    SafetyCenterConfigInternal getCurrentConfigInternal() {
        // We require the XML config must be loaded successfully for SafetyCenterManager APIs to
        // function, regardless of whether the config is subsequently overridden.
        requireNonNull(mConfigInternalFromXml);

        if (mConfigInternalOverrideForTests == null) {
            return mConfigInternalFromXml;
        }

        return mConfigInternalOverrideForTests;
    }

    /**
     * Loads the {@link SafetyCenterConfigInternal} for it to be available when calling
     * {@link #getCurrentConfigInternal()} and returns whether the loading was successful.
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
     * Sets an override of the {@link SafetyCenterConfig} used for
     * {@link android.safetycenter.SafetyCenterManager} APIs for tests.
     *
     * <p>When set, {@link #getCurrentConfigInternal()} will return a
     * {@link SafetyCenterConfigInternal} created using the override {@link SafetyCenterConfig}.
     *
     * <p>To return to using the {@link SafetyCenterConfig} parsed through XML, clear the override
     * using {@link #clearConfigOverrideForTests()}.
     */
    void setConfigOverrideForTests(@NonNull SafetyCenterConfig safetyCenterConfig) {
        mConfigInternalOverrideForTests = SafetyCenterConfigInternal.from(safetyCenterConfig);
    }

    /**
     * Clears the override of the {@link SafetyCenterConfig} used for
     * {@link android.safetycenter.SafetyCenterManager} APIs for tests.
     *
     * @see #setConfigOverrideForTests
     */
    void clearConfigOverrideForTests() {
        mConfigInternalOverrideForTests = null;
    }

    @Nullable
    private SafetyCenterConfig readSafetyCenterConfig() {
        XmlResourceParser parser = mSafetyCenterResourcesContext.getSafetyCenterConfig();
        if (parser == null) {
            Log.e(TAG, "Cannot get safety center config file");
            return null;
        }

        try {
            SafetyCenterConfig safetyCenterConfig = SafetyCenterConfig.fromXml(parser);
            Log.i(TAG, "SafetyCenterConfig read successfully");
            return safetyCenterConfig;
        } catch (ParseException e) {
            Log.e(TAG, "Cannot read SafetyCenterConfig", e);
            return null;
        }
    }

    /** A wrapper class around the parsed XML config. */
    static final class SafetyCenterConfigInternal {

        @NonNull
        private final SafetyCenterConfig mConfig;
        @NonNull
        private final List<SafetySourcesGroup> mSafetySourcesGroups;
        @NonNull
        private final ArrayMap<String, SafetySource> mExternalSafetySources;
        @NonNull
        private final List<Broadcast> mBroadcasts;

        private SafetyCenterConfigInternal(
                @NonNull SafetyCenterConfig safetyCenterConfig,
                @NonNull List<SafetySourcesGroup> safetySourcesGroups,
                @NonNull ArrayMap<String, SafetySource> externalSafetySources,
                @NonNull List<Broadcast> broadcasts) {
            mConfig = safetyCenterConfig;
            mSafetySourcesGroups = safetySourcesGroups;
            mExternalSafetySources = externalSafetySources;
            mBroadcasts = broadcasts;
        }

        /**
         * Returns the underlying {@link SafetyCenterConfig} used to create this
         * {@link SafetyCenterConfigInternal}.
         */
        SafetyCenterConfig getSafetyCenterConfig() {
            return mConfig;
        }

        /**
         * Returns the groups of safety sources, in the order defined in XML and expected by the
         * UI.
         */
        List<SafetySourcesGroup> getSafetySourcesGroups() {
            return mSafetySourcesGroups;
        }

        /** Returns the map of safety source IDs that can provide data externally. */
        ArrayMap<String, SafetySource> getExternalSafetySources() {
            return mExternalSafetySources;
        }

        /**
         * Returns the broadcasts defined in the XML config, with all the sources that they should
         * handle and the profile on which they should be dispatched.
         */
        // TODO(b/221018937): Should we move this logic to `SafetyCenterBroadcastManager`?
        List<Broadcast> getBroadcasts() {
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
                    + "mSafetySourcesGroups="
                    + mSafetySourcesGroups
                    + ", mExternalSafetySources="
                    + mExternalSafetySources
                    + ", mBroadcastReceivers="
                    + mBroadcasts
                    + '}';
        }

        @NonNull
        private static SafetyCenterConfigInternal from(
                @NonNull SafetyCenterConfig safetyCenterConfig) {
            return new SafetyCenterConfigInternal(
                    safetyCenterConfig,
                    safetyCenterConfig.getSafetySourcesGroups(),
                    extractExternalSafetySources(safetyCenterConfig),
                    extractBroadcasts(safetyCenterConfig)
            );
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
                        broadcast = new Broadcast(safetySource.getPackageName(), new ArrayList<>(),
                                new ArrayList<>());
                        packageNameToBroadcast.put(safetySource.getPackageName(), broadcast);
                        broadcasts.add(broadcast);
                    }
                    broadcast.getSourceIdsForProfileOwner().add(safetySource.getId());
                    // TODO(b/217688797): This might also be handled by the source directly.
                    boolean needsManagedProfilesBroadcast = SafetySources.supportsManagedProfiles(
                            safetySource);
                    if (needsManagedProfilesBroadcast) {
                        broadcast.getSourceIdsForManagedProfiles().add(safetySource.getId());
                    }
                }
            }

            return broadcasts;
        }
    }

    /** A class that represents a broadcast to be sent to safety sources. */
    static final class Broadcast {

        @NonNull
        private final String mPackageName;

        @NonNull
        private final List<String> mSourceIdsForProfileOwner;

        @NonNull
        private final List<String> mSourceIdsForManagedProfiles;

        private Broadcast(
                @NonNull String packageName,
                @NonNull List<String> sourceIdsForProfileOwner,
                @NonNull List<String> sourceIdsForManagedProfiles) {
            mPackageName = packageName;
            mSourceIdsForProfileOwner = sourceIdsForProfileOwner;
            mSourceIdsForManagedProfiles = sourceIdsForManagedProfiles;
        }

        /** Returns the package name to dispatch the broadcast to. */
        public String getPackageName() {
            return mPackageName;
        }

        /**
         * Returns the safety source ids associated with this broadcast in the profile owner.
         *
         * <p>If this list is empty, there are no sources to dispatch to in the profile owner.
         */
        public List<String> getSourceIdsForProfileOwner() {
            return mSourceIdsForProfileOwner;
        }

        /**
         * Returns the safety source ids associated with this broadcast in the managed profile(s).
         *
         * <p>If this list is empty, there are no sources to dispatch to in the managed profile(s).
         */
        public List<String> getSourceIdsForManagedProfiles() {
            return mSourceIdsForManagedProfiles;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Broadcast)) return false;
            Broadcast that = (Broadcast) o;
            return mPackageName.equals(that.mPackageName) && mSourceIdsForProfileOwner.equals(
                    that.mSourceIdsForProfileOwner) && mSourceIdsForManagedProfiles.equals(
                    that.mSourceIdsForManagedProfiles);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPackageName, mSourceIdsForProfileOwner,
                    mSourceIdsForManagedProfiles);
        }

        @Override
        public String toString() {
            return "Broadcast{"
                    + "mPackageName="
                    + mPackageName
                    + ", mSourceIdsForProfileOwner="
                    + mSourceIdsForProfileOwner
                    + ", mSourceIdsForManagedProfiles="
                    + mSourceIdsForManagedProfiles
                    + '}';
        }
    }
}
