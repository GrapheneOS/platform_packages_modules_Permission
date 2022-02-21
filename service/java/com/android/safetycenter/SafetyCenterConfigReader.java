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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.UserHandle;
import android.safetycenter.config.Parser;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A class that reads the {@link Config} from the associated {@link
 * SafetyCenterResourcesContext}.
 */
@RequiresApi(TIRAMISU)
final class SafetyCenterConfigReader {

    private static final String TAG = "SafetyCenterConfigReade";

    @NonNull
    private final SafetyCenterResourcesContext mSafetyCenterResourcesContext;

    @Nullable
    private volatile Config mConfig;

    /**
     * Creates a {@link SafetyCenterConfigReader} from a {@link Context} object by wrapping it into
     * a {@link SafetyCenterResourcesContext}.
     */
    SafetyCenterConfigReader(@NonNull Context context) {
        mSafetyCenterResourcesContext = new SafetyCenterResourcesContext(context);
    }

    /**
     * Returns the {@link Config} read by {@link #loadConfig()}.
     *
     * <p>Returns {@code null} if {@link #loadConfig()} was never called or if there was
     * an issue when reading the {@link Config}.
     */
    @Nullable
    Config getConfig() {
        return mConfig;
    }

    /**
     * Returns a {@link String} resource from the given {@code stringId}, using the {@link
     * SafetyCenterResourcesContext}.
     *
     * <p>Returns {@code null} if the resource cannot be accessed.
     */
    @Nullable
    String readStringResource(@StringRes int stringId) {
        Resources resources = mSafetyCenterResourcesContext.getResources();
        if (resources == null) {
            return null;
        }

        return resources.getString(stringId);
    }

    /**
     * Loads the {@link Config} for it to be available when calling {@link
     * #getConfig()}.
     *
     * <p>This call must complete on one thread for other threads to be able to observe the value;
     * i.e. this is meant to be called as an initialization mechanism, prior to interacting with
     * this class on other threads.
     */
    void loadConfig() {
        SafetyCenterConfig safetyCenterConfig = readSafetyCenterConfig();
        if (safetyCenterConfig == null) {
            return;
        }
        mConfig = Config.from(safetyCenterConfig);
    }

    @Nullable
    private SafetyCenterConfig readSafetyCenterConfig() {
        XmlResourceParser parser = mSafetyCenterResourcesContext.getSafetyCenterConfig();
        if (parser == null) {
            Log.e(TAG, "Cannot get safety center config file");
            return null;
        }

        try {
            SafetyCenterConfig safetyCenterConfig = Parser.parseXmlResource(parser);
            Log.i(TAG, "SafetyCenterConfig read successfully");
            return safetyCenterConfig;
        } catch (Parser.ParseException e) {
            Log.e(TAG, "Cannot read SafetyCenterConfig", e);
            return null;
        }
    }

    /** A wrapper class around the parsed XML config. */
    static final class Config {

        @NonNull
        private final List<SafetySourcesGroup> mSafetySourcesGroups;
        @NonNull
        private final ArraySet<SourceId> mExternalSafetySources;
        @NonNull
        private final List<Broadcast> mBroadcasts;

        private Config(
                @NonNull List<SafetySourcesGroup> safetySourcesGroups,
                @NonNull ArraySet<SourceId> externalSafetySources,
                @NonNull List<Broadcast> broadcasts) {
            mSafetySourcesGroups = safetySourcesGroups;
            mExternalSafetySources = externalSafetySources;
            mBroadcasts = broadcasts;
        }

        /**
         * Returns the groups of safety sources, in the order defined in XML and expected by the
         * UI.
         */
        List<SafetySourcesGroup> getSafetySourcesGroups() {
            return mSafetySourcesGroups;
        }

        /** Returns the set of safety source IDs that can provide data externally. */
        ArraySet<SourceId> getExternalSafetySources() {
            return mExternalSafetySources;
        }

        /**
         * Returns the broadcasts defined in the XML config, with all the sources that they should
         * handle and the profile on which they should be dispatched.
         */
        // TODO(b/221018937): Should we move this logic to `SafetyCenterRefreshManager`?
        List<Broadcast> getBroadcasts() {
            return mBroadcasts;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Config)) return false;
            Config config = (Config) o;
            return mSafetySourcesGroups.equals(config.mSafetySourcesGroups)
                    && mExternalSafetySources.equals(
                    config.mExternalSafetySources) && mBroadcasts.equals(
                    config.mBroadcasts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSafetySourcesGroups, mExternalSafetySources, mBroadcasts);
        }

        @Override
        public String toString() {
            return "Config{"
                    + "mSafetySourcesGroups="
                    + mSafetySourcesGroups
                    + ", mExternalSafetySources="
                    + mExternalSafetySources
                    + ", mBroadcastReceivers="
                    + mBroadcasts
                    + '}';
        }

        @NonNull
        private static Config from(@NonNull SafetyCenterConfig safetyCenterConfig) {
            return new Config(
                    safetyCenterConfig.getSafetySourcesGroups(),
                    extractExternalSafetySources(safetyCenterConfig),
                    extractBroadcasts(safetyCenterConfig));
        }

        @NonNull
        private static ArraySet<SourceId> extractExternalSafetySources(
                @NonNull SafetyCenterConfig safetyCenterConfig) {
            ArraySet<SourceId> externalSafetySources = new ArraySet<>();
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

                    externalSafetySources.add(
                            SourceId.of(safetySource.getId(), safetySource.getPackageName()));
                }
            }

            return externalSafetySources;
        }

        @NonNull
        private static List<Broadcast> extractBroadcasts(
                @NonNull SafetyCenterConfig safetyCenterConfig) {
            ArrayMap<ComponentName, List<String>> broadcastReceivers = new ArrayMap<>();
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

                    String broadcastReceiver = safetySource.getBroadcastReceiverClassName();
                    if (broadcastReceiver == null) {
                        continue;
                    }

                    ComponentName componentName = new ComponentName(safetySource.getPackageName(),
                            broadcastReceiver);

                    List<String> sourceIds = broadcastReceivers.get(componentName);
                    if (sourceIds == null) {
                        sourceIds = new ArrayList<>();
                        broadcastReceivers.put(componentName, sourceIds);
                    }
                    sourceIds.add(safetySource.getId());
                }
            }

            List<Broadcast> broadcasts = new ArrayList<>();
            for (int i = 0; i < broadcastReceivers.size(); i++) {
                ComponentName componentName = broadcastReceivers.keyAt(i);
                List<String> sourceIds = broadcastReceivers.valueAt(i);
                // TODO(b/215144069): Handle work profile broadcasts.
                broadcasts.add(new Broadcast(componentName, sourceIds, UserHandle.CURRENT));
            }

            return broadcasts;
        }
    }

    /** A class that represents a unique source id for a given package name. */
    static final class SourceId {

        @NonNull
        private final String mId;
        @NonNull
        private final String mPackageName;

        private SourceId(@NonNull String id, @NonNull String packageName) {
            mId = id;
            mPackageName = packageName;
        }

        /** Creates a {@link SourceId} for the given {@code id} and {@code packageName}. */
        @NonNull
        static SourceId of(@NonNull String id, @NonNull String packageName) {
            return new SourceId(id, packageName);
        }

        /**
         * Returns the safety source id.
         *
         * <p>This is already a unique ID as defined by the XML config, but this class still keeps
         * track of the package name to ensure callers cannot impersonate the source.
         */
        String getId() {
            return mId;
        }

        /** Returns the package name that owns the safety source id. */
        String getPackageName() {
            return mPackageName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SourceId)) return false;
            SourceId sourceId = (SourceId) o;
            return mId.equals(sourceId.mId) && mPackageName.equals(sourceId.mPackageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mId, mPackageName);
        }

        @Override
        public String toString() {
            return "SourceId{"
                    + "mId='"
                    + mId
                    + '\''
                    + ", mPackageName='"
                    + mPackageName
                    + '\''
                    + '}';
        }
    }

    /** A class that represents a broadcast to be sent to safety sources. */
    static final class Broadcast {

        @NonNull
        private final ComponentName mComponentName;

        @NonNull
        private final List<String> mSourceIds;

        private final UserHandle mUserHandle;


        private Broadcast(
                @NonNull ComponentName componentName,
                @NonNull List<String> sourceIds,
                @NonNull UserHandle userHandle) {
            mComponentName = componentName;
            mSourceIds = sourceIds;
            mUserHandle = userHandle;
        }

        /** Creates a {@link Broadcast} for the given {@link ComponentName}. */
        @NonNull
        static Broadcast from(@NonNull ComponentName componentName) {
            // TODO(b/215144069): Handle work profile broadcasts.
            return new Broadcast(componentName, new ArrayList<>(), UserHandle.CURRENT);
        }

        /** Returns the {@link ComponentName} to dispatch the broadcast to. */
        public ComponentName getComponentName() {
            return mComponentName;
        }

        /** Returns the safety source ids associated with this broadcast. */
        public List<String> getSourceIds() {
            return mSourceIds;
        }

        /** Returns the {@link UserHandle} on which this broadcast should be dispatched. */
        public UserHandle getUserHandle() {
            return mUserHandle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Broadcast)) return false;
            Broadcast broadcast = (Broadcast) o;
            return mComponentName.equals(broadcast.mComponentName) && mSourceIds.equals(
                    broadcast.mSourceIds) && mUserHandle.equals(broadcast.mUserHandle);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mComponentName, mSourceIds, mUserHandle);
        }

        @Override
        public String toString() {
            return "Broadcast{"
                    + "mComponentName="
                    + mComponentName
                    + ", mSourceIds="
                    + mSourceIds
                    + ", mUserHandle="
                    + mUserHandle
                    + '}';
        }
    }
}
