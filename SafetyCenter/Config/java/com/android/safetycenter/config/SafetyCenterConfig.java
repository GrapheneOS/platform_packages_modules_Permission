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

    private SafetyCenterConfig(@NonNull List<SafetySourcesGroup> safetySourcesGroups) {
        mSafetySourcesGroups = safetySourcesGroups;
    }

    /** Returns the list of safety sources groups in the configuration. */
    @NonNull
    public List<SafetySourcesGroup> getSafetySourcesGroups() {
        return mSafetySourcesGroups;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetyCenterConfig)) return false;
        SafetyCenterConfig that = (SafetyCenterConfig) o;
        return Objects.equals(mSafetySourcesGroups, that.mSafetySourcesGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSafetySourcesGroups);
    }

    @Override
    public String toString() {
        return "SafetyCenterConfig{"
                + "mSafetySourcesGroups=" + mSafetySourcesGroups
                + '}';
    }

    /** Builder class for {@link SafetyCenterConfig}. */
    public static final class Builder {
        @NonNull
        private final List<SafetySourcesGroup> mSafetySourcesGroups = new ArrayList<>();

        /** Creates a {@link Builder} for a {@link SafetyCenterConfig}. */
        public Builder() {
        }

        /** Adds a safety source group to the configuration. */
        @NonNull
        public Builder addSafetySourcesGroup(@NonNull SafetySourcesGroup safetySourcesGroup) {
            mSafetySourcesGroups.add(requireNonNull(safetySourcesGroup));
            return this;
        }

        /** Creates the {@link SafetyCenterConfig} defined by this {@link Builder}. */
        @NonNull
        public SafetyCenterConfig build() {
            if (mSafetySourcesGroups.isEmpty()) {
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
                List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
                int safetySourcesSize = safetySources.size();
                for (int j = 0; j < safetySourcesSize; j++) {
                    SafetySource staticSafetySource = safetySources.get(j);
                    String sourceId = staticSafetySource.getId();
                    if (safetySourceIds.contains(sourceId)) {
                        throw new IllegalStateException(
                                String.format("Duplicate id %s among safety sources", sourceId));
                    }
                    safetySourceIds.add(sourceId);
                }
            }
            return new SafetyCenterConfig(Collections.unmodifiableList(mSafetySourcesGroups));
        }
    }

}
