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

package android.safetycenter.config;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.res.XmlResourceParser;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Data class used to represent the initial configuration of the Safety Center.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterConfig implements Parcelable {

    @NonNull
    public static final Creator<SafetyCenterConfig> CREATOR =
            new Creator<SafetyCenterConfig>() {
                @Override
                public SafetyCenterConfig createFromParcel(Parcel in) {
                    List<SafetySourcesGroup> safetySourcesGroups =
                            requireNonNull(in.createTypedArrayList(SafetySourcesGroup.CREATOR));
                    Builder builder = new Builder();
                    // TODO(b/224513050): Consider simplifying by adding a new API to the builder.
                    for (int i = 0; i < safetySourcesGroups.size(); i++) {
                        builder.addSafetySourcesGroup(safetySourcesGroups.get(i));
                    }
                    return builder.build();
                }

                @Override
                public SafetyCenterConfig[] newArray(int size) {
                    return new SafetyCenterConfig[size];
                }
            };

    @NonNull
    private final List<SafetySourcesGroup> mSafetySourcesGroups;

    private SafetyCenterConfig(@NonNull List<SafetySourcesGroup> safetySourcesGroups) {
        mSafetySourcesGroups = safetySourcesGroups;
    }

    /**
     * Parses and validates the given XML resource into a {@link SafetyCenterConfig} object.
     *
     * <p>It throws a {@link ParseException} if the given XML resource does not comply with the
     * safety_center_config.xsd schema.
     *
     * @param parser the XML resource parsing interface
     */
    @NonNull
    public static SafetyCenterConfig fromXml(@NonNull XmlResourceParser parser)
            throws ParseException {
        return SafetyCenterConfigParser.parseXmlResource(parser);
    }

    /** Returns the list of {@link SafetySourcesGroup}s in the configuration. */
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
                + "mSafetySourcesGroups="
                + mSafetySourcesGroups
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedList(mSafetySourcesGroups);
    }

    /** Builder class for {@link SafetyCenterConfig}. */
    public static final class Builder {

        private final List<SafetySourcesGroup> mSafetySourcesGroups = new ArrayList<>();

        /** Creates a {@link Builder} for a {@link SafetyCenterConfig}. */
        public Builder() {
        }

        /** Adds a {@link SafetySourcesGroup} to the configuration. */
        @NonNull
        public Builder addSafetySourcesGroup(@NonNull SafetySourcesGroup safetySourcesGroup) {
            mSafetySourcesGroups.add(requireNonNull(safetySourcesGroup));
            return this;
        }

        /** Creates the {@link SafetyCenterConfig} defined by this {@link Builder}. */
        @NonNull
        public SafetyCenterConfig build() {
            List<SafetySourcesGroup> safetySourcesGroups = unmodifiableList(
                    new ArrayList<>(mSafetySourcesGroups));
            if (safetySourcesGroups.isEmpty()) {
                throw new IllegalStateException("No safety sources groups present");
            }
            Set<String> safetySourceIds = new HashSet<>();
            Set<String> safetySourcesGroupsIds = new HashSet<>();
            int safetySourcesGroupsSize = safetySourcesGroups.size();
            for (int i = 0; i < safetySourcesGroupsSize; i++) {
                SafetySourcesGroup safetySourcesGroup = safetySourcesGroups.get(i);
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
            return new SafetyCenterConfig(safetySourcesGroups);
        }
    }
}
