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

package android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Data class used by safety sources to propagate safety information such as a generic status,
 * potential warnings and other related metadata.
 *
 * @hide
 */
// @SystemApi -- Add this line back when ready for API council review.
@RequiresApi(TIRAMISU)
// TODO(b/205551986): Move this to the right place and add back the NonNull annotations once
//  b/205289292 is fixed.
// TODO(b/206089303): Add Builders as more fields are added to this class.
public final class SafetySourceData implements Parcelable {

    @NonNull
    public static final Parcelable.Creator<SafetySourceData> CREATOR =
            new Parcelable.Creator<SafetySourceData>() {
                @Override
                public SafetySourceData createFromParcel(Parcel in) {
                    String safetySourceId = requireNonNull(in.readString());
                    SafetyPreferenceData safetyPreferenceData =
                            requireNonNull(
                                    in.readParcelable(SafetyPreferenceData.class.getClassLoader()));
                    List<SafetyIssueData> safetyIssuesData = new ArrayList<>();
                    in.readParcelableList(safetyIssuesData, SafetyIssueData.class.getClassLoader());
                    return new SafetySourceData(safetySourceId,
                            safetyPreferenceData, safetyIssuesData);
                }
                @Override
                public SafetySourceData[] newArray(int size) {
                    return new SafetySourceData[size];
                }
            };

    @NonNull
    private final String mSafetySourceId;

    @Nullable
    private final SafetyPreferenceData mSafetyPreferenceData;

    @NonNull
    private final List<SafetyIssueData> mSafetyIssuesData;

    private SafetySourceData(@NonNull String safetySourceId,
            @Nullable SafetyPreferenceData safetyPreferenceData,
            @NonNull List<SafetyIssueData> safetyIssuesData) {
        this.mSafetySourceId = safetySourceId;
        this.mSafetyPreferenceData = safetyPreferenceData;
        this.mSafetyIssuesData = new ArrayList<>(safetyIssuesData);
    }

    /** Returns the id of the associated safety source. */
    @NonNull
    public String getSafetySourceId() {
        return mSafetySourceId;
    }

    /** Returns the data for the safety preference to be shown in UI. */
    @Nullable
    public SafetyPreferenceData getSafetyPreferenceData() {
        return mSafetyPreferenceData;
    }

    /** Returns the data for the list of safety issues to be shown in UI. */
    @NonNull
    public List<SafetyIssueData> getSafetyIssuesData() {
        return new ArrayList<>(mSafetyIssuesData);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mSafetySourceId);
        dest.writeParcelable(mSafetyPreferenceData, flags);
        dest.writeParcelableList(mSafetyIssuesData, flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourceData)) return false;
        SafetySourceData that = (SafetySourceData) o;
        return mSafetySourceId.equals(that.mSafetySourceId)
                && mSafetyPreferenceData.equals(that.mSafetyPreferenceData)
                && mSafetyIssuesData.equals(that.mSafetyIssuesData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSafetySourceId, mSafetyPreferenceData, mSafetyIssuesData);
    }

    @Override
    public String toString() {
        return "SafetySourceData{"
                + "mSafetySourceId='"
                + mSafetySourceId
                + '\''
                + ", mSafetyPreferenceData="
                + mSafetyPreferenceData
                + ", mSafetyIssuesData="
                + mSafetyIssuesData
                + '}';
    }

    /** Builder class for {@link SafetySourceData}. */
    public static final class Builder {
        @NonNull
        private final String mId;

        @Nullable
        private SafetyPreferenceData mSafetyPreferenceData;

        @NonNull
        private List<SafetyIssueData> mSafetyIssuesData = new ArrayList<>();

        /**
         * Creates a {@link Builder} for a {@link SafetySourceData} using the id of the associated
         * safety source.
         */
        public Builder(@NonNull String id) {
            this.mId = requireNonNull(id);
        }

        /** Sets data for the safety preference to be shown in UI. */
        @NonNull
        public Builder setSafetyPreferenceData(
                @Nullable SafetyPreferenceData safetyPreferenceData) {
            mSafetyPreferenceData = safetyPreferenceData;
            return this;
        }

        /** Adds data for a safety issue to be shown in UI. */
        @NonNull
        // @SuppressWarnings("MissingGetterMatchingBuilder")
        //  The MissingGetterMatchingBuilder warning has been suppressed as it expects the
        //  corresponding getter to be named `getSafetyIssueDatas()` which would be grammatically
        //  incorrect.
        public Builder addSafetyIssueData(@NonNull SafetyIssueData safetyIssueData) {
            mSafetyIssuesData.add(requireNonNull(safetyIssueData));
            return this;
        }

        /** Clears data for all the safety issues that were added to this {@link Builder}. */
        @NonNull
        public Builder clearSafetyIssuesData() {
            mSafetyIssuesData.clear();
            return this;
        }

        /** Creates the {@link SafetySourceData} defined by this {@link Builder}. */
        @NonNull
        public SafetySourceData build() {
            return new SafetySourceData(mId, mSafetyPreferenceData, mSafetyIssuesData);
        }
    }
}
