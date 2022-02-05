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

package com.android.safetycenter.config;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Data class used to represent the initial configuration and current state of the Safety Center */
public final class SafetyCenterConfig {
    @NonNull
    private final List<SafetySourcesGroup> mSafetySourcesGroups;
    @NonNull
    private final List<StaticSafetySourcesGroup> mStaticSafetySourcesGroups;

    private SafetyCenterConfig(@NonNull List<SafetySourcesGroup> safetySourcesGroups,
            @NonNull List<StaticSafetySourcesGroup> staticSafetySourcesGroups) {
        mSafetySourcesGroups = safetySourcesGroups;
        mStaticSafetySourcesGroups = staticSafetySourcesGroups;
    }

    /** Returns the list of safety sources groups in the configuration. */
    @NonNull
    public List<SafetySourcesGroup> getSafetySourcesGroups() {
        return mSafetySourcesGroups;
    }

    /** Returns the list of static safety sources groups in the configuration. */
    @NonNull
    public List<StaticSafetySourcesGroup> getStaticSafetySourcesGroups() {
        return mStaticSafetySourcesGroups;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetyCenterConfig)) return false;
        SafetyCenterConfig that = (SafetyCenterConfig) o;
        return Objects.equals(mSafetySourcesGroups, that.mSafetySourcesGroups)
                && Objects.equals(mStaticSafetySourcesGroups, that.mStaticSafetySourcesGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSafetySourcesGroups, mStaticSafetySourcesGroups);
    }

    @Override
    public String toString() {
        return "SafetyCenterConfig{"
                + "mSafetySourcesGroups=" + mSafetySourcesGroups
                + ", mStaticSafetySourcesGroups=" + mStaticSafetySourcesGroups
                + '}';
    }

    /** Builder class for {@link SafetyCenterConfig}. */
    public static final class Builder {
        @NonNull
        private final List<SafetySourcesGroup> mSafetySourcesGroups = new ArrayList<>();
        @NonNull
        private final List<StaticSafetySourcesGroup> mStaticSafetySourcesGroups = new ArrayList<>();

        /** Creates a {@link Builder} for a {@link SafetyCenterConfig}. */
        public Builder() {
        }

        /** Adds a safety source group to the configuration. */
        @NonNull
        public Builder addSafetySourcesGroup(@NonNull SafetySourcesGroup safetySourcesGroup) {
            mSafetySourcesGroups.add(requireNonNull(safetySourcesGroup));
            return this;
        }

        /** Adds a static safety source group to the configuration. */
        @NonNull
        public Builder addStaticSafetySourcesGroup(
                @NonNull StaticSafetySourcesGroup staticSafetySourcesGroup) {
            mStaticSafetySourcesGroups.add(requireNonNull(staticSafetySourcesGroup));
            return this;
        }

        /** Creates the {@link SafetyCenterConfig} defined by this {@link Builder}. */
        @NonNull
        public SafetyCenterConfig build() {
            if (mSafetySourcesGroups.isEmpty() && mStaticSafetySourcesGroups.isEmpty()) {
                throw new IllegalStateException("No safety sources groups present");
            }
            Set<String> safetySourceIds = new HashSet<>();
            Set<String> safetySourcesGroupsIds = new HashSet<>();
            int safetySourcesGroupsSize = mSafetySourcesGroups.size();
            for (int i = 0; i < safetySourcesGroupsSize; i++) {
                SafetySourcesGroup safetySourcesGroup = mSafetySourcesGroups.get(i);
                String groupId = safetySourcesGroup.getId();
                if (safetySourcesGroupsIds.contains(groupId)) {
                    throw new IllegalStateException(
                            String.format("Duplicate id %s among safety sources groups", groupId));
                }
                safetySourcesGroupsIds.add(groupId);
                checkForDuplicateSourceIds(safetySourcesGroup.getSafetySources(), safetySourceIds);
            }
            int staticSafetySourcesGroupsSize = mStaticSafetySourcesGroups.size();
            for (int i = 0; i < staticSafetySourcesGroupsSize; i++) {
                StaticSafetySourcesGroup staticSafetySourcesGroup = mStaticSafetySourcesGroups.get(
                        i);
                String groupId = staticSafetySourcesGroup.getId();
                if (safetySourcesGroupsIds.contains(groupId)) {
                    throw new IllegalStateException(
                            String.format("Duplicate id %s among safety sources groups", groupId));
                }
                safetySourcesGroupsIds.add(groupId);
                checkForDuplicateSourceIds(staticSafetySourcesGroup.getStaticSafetySources(),
                        safetySourceIds);
            }
            return new SafetyCenterConfig(Collections.unmodifiableList(mSafetySourcesGroups),
                    Collections.unmodifiableList(mStaticSafetySourcesGroups));
        }

        private static void checkForDuplicateSourceIds(List<SafetySource> safetySources,
                Set<String> safetySourceIds) {
            int safetySourcesSize = safetySources.size();
            for (int i = 0; i < safetySourcesSize; i++) {
                SafetySource staticSafetySource = safetySources.get(i);
                String sourceId = staticSafetySource.getId();
                if (safetySourceIds.contains(sourceId)) {
                    throw new IllegalStateException(
                            String.format("Duplicate id %s among safety sources", sourceId));
                }
                safetySourceIds.add(sourceId);
            }
        }
    }

}
