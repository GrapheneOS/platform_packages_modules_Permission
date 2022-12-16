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

package android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A representation of the safety state of the device.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterData implements Parcelable {

    @NonNull
    public static final Creator<SafetyCenterData> CREATOR =
            new Creator<SafetyCenterData>() {
                @Override
                public SafetyCenterData createFromParcel(Parcel in) {
                    SafetyCenterStatus status = in.readTypedObject(SafetyCenterStatus.CREATOR);
                    List<SafetyCenterIssue> issues =
                            in.createTypedArrayList(SafetyCenterIssue.CREATOR);
                    List<SafetyCenterEntryOrGroup> entryOrGroups =
                            in.createTypedArrayList(SafetyCenterEntryOrGroup.CREATOR);
                    List<SafetyCenterStaticEntryGroup> staticEntryGroups =
                            in.createTypedArrayList(SafetyCenterStaticEntryGroup.CREATOR);

                    if (SdkLevel.isAtLeastU()) {
                        List<SafetyCenterIssue> dismissedIssues =
                                in.createTypedArrayList(SafetyCenterIssue.CREATOR);
                        Bundle extras = in.readBundle(getClass().getClassLoader());
                        SafetyCenterData.Builder builder = new SafetyCenterData.Builder(status);
                        for (int i = 0; i < issues.size(); i++) {
                            builder.addIssue(issues.get(i));
                        }
                        for (int i = 0; i < entryOrGroups.size(); i++) {
                            builder.addEntryOrGroup(entryOrGroups.get(i));
                        }
                        for (int i = 0; i < staticEntryGroups.size(); i++) {
                            builder.addStaticEntryGroup(staticEntryGroups.get(i));
                        }
                        for (int i = 0; i < dismissedIssues.size(); i++) {
                            builder.addDismissedIssue(dismissedIssues.get(i));
                        }
                        if (extras != null) {
                            builder.setExtras(extras);
                        }
                        return builder.build();
                    } else {
                        return new SafetyCenterData(
                                status, issues, entryOrGroups, staticEntryGroups);
                    }
                }

                @Override
                public SafetyCenterData[] newArray(int size) {
                    return new SafetyCenterData[size];
                }
            };

    @NonNull private final SafetyCenterStatus mStatus;
    @NonNull private final List<SafetyCenterIssue> mIssues;
    @NonNull private final List<SafetyCenterEntryOrGroup> mEntriesOrGroups;
    @NonNull private final List<SafetyCenterStaticEntryGroup> mStaticEntryGroups;
    @NonNull private final List<SafetyCenterIssue> mDismissedIssues;
    @NonNull private final Bundle mExtras;

    /** Creates a {@link SafetyCenterData}. */
    public SafetyCenterData(
            @NonNull SafetyCenterStatus status,
            @NonNull List<SafetyCenterIssue> issues,
            @NonNull List<SafetyCenterEntryOrGroup> entriesOrGroups,
            @NonNull List<SafetyCenterStaticEntryGroup> staticEntryGroups) {
        mStatus = requireNonNull(status);
        mIssues = unmodifiableList(new ArrayList<>(requireNonNull(issues)));
        mEntriesOrGroups = unmodifiableList(new ArrayList<>(requireNonNull(entriesOrGroups)));
        mStaticEntryGroups = unmodifiableList(new ArrayList<>(requireNonNull(staticEntryGroups)));
        mDismissedIssues = unmodifiableList(new ArrayList<>());
        mExtras = Bundle.EMPTY;
    }

    private SafetyCenterData(
            @NonNull SafetyCenterStatus status,
            @NonNull List<SafetyCenterIssue> issues,
            @NonNull List<SafetyCenterEntryOrGroup> entriesOrGroups,
            @NonNull List<SafetyCenterStaticEntryGroup> staticEntryGroups,
            @NonNull List<SafetyCenterIssue> dismissedIssues,
            @NonNull Bundle extras) {
        mStatus = status;
        mIssues = issues;
        mEntriesOrGroups = entriesOrGroups;
        mStaticEntryGroups = staticEntryGroups;
        mDismissedIssues = dismissedIssues;
        mExtras = extras;
    }

    /** Returns the overall {@link SafetyCenterStatus} of the Safety Center. */
    @NonNull
    public SafetyCenterStatus getStatus() {
        return mStatus;
    }

    /** Returns the list of active {@link SafetyCenterIssue} objects in the Safety Center. */
    @NonNull
    public List<SafetyCenterIssue> getIssues() {
        return mIssues;
    }

    /**
     * Returns the structured list of {@link SafetyCenterEntry} and {@link SafetyCenterEntryGroup}
     * objects, wrapped in {@link SafetyCenterEntryOrGroup}.
     */
    @NonNull
    public List<SafetyCenterEntryOrGroup> getEntriesOrGroups() {
        return mEntriesOrGroups;
    }

    /** Returns the list of {@link SafetyCenterStaticEntryGroup} objects in the Safety Center. */
    @NonNull
    public List<SafetyCenterStaticEntryGroup> getStaticEntryGroups() {
        return mStaticEntryGroups;
    }

    /** Returns the list of dismissed {@link SafetyCenterIssue} objects in the Safety Center. */
    @NonNull
    @RequiresApi(UPSIDE_DOWN_CAKE)
    public List<SafetyCenterIssue> getDismissedIssues() {
        if (!SdkLevel.isAtLeastU()) {
            throw new UnsupportedOperationException();
        }
        return mDismissedIssues;
    }

    /**
     * Returns a {@link Bundle} containing additional information, {@link Bundle#EMPTY} by default.
     *
     * <p>Note: internal state of this {@link Bundle} is not used for {@link Object#equals} and
     * {@link Object#hashCode} implementation of {@link SafetyCenterData}.
     */
    @NonNull
    @RequiresApi(UPSIDE_DOWN_CAKE)
    public Bundle getExtras() {
        if (!SdkLevel.isAtLeastU()) {
            throw new UnsupportedOperationException();
        }
        return mExtras;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetyCenterData)) return false;
        SafetyCenterData that = (SafetyCenterData) o;
        return Objects.equals(mStatus, that.mStatus)
                && Objects.equals(mIssues, that.mIssues)
                && Objects.equals(mEntriesOrGroups, that.mEntriesOrGroups)
                && Objects.equals(mStaticEntryGroups, that.mStaticEntryGroups)
                && Objects.equals(mDismissedIssues, that.mDismissedIssues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mStatus, mIssues, mEntriesOrGroups, mStaticEntryGroups, mDismissedIssues);
    }

    @Override
    public String toString() {
        return "SafetyCenterData{"
                + "mStatus="
                + mStatus
                + ", mIssues="
                + mIssues
                + ", mEntriesOrGroups="
                + mEntriesOrGroups
                + ", mStaticEntryGroups="
                + mStaticEntryGroups
                + ", mDismissedIssues="
                + mDismissedIssues
                + (!mExtras.isEmpty() ? ", (has extras)" : "")
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(mStatus, flags);
        dest.writeTypedList(mIssues);
        dest.writeTypedList(mEntriesOrGroups);
        dest.writeTypedList(mStaticEntryGroups);
        if (SdkLevel.isAtLeastU()) {
            dest.writeTypedList(mDismissedIssues);
            dest.writeBundle(mExtras);
        }
    }

    /** Builder class for {@link SafetyCenterData}. */
    @RequiresApi(UPSIDE_DOWN_CAKE)
    public static final class Builder {

        @NonNull private final SafetyCenterStatus mStatus;
        @NonNull private final List<SafetyCenterIssue> mIssues = new ArrayList<>();
        @NonNull private final List<SafetyCenterEntryOrGroup> mEntriesOrGroups = new ArrayList<>();

        @NonNull
        private final List<SafetyCenterStaticEntryGroup> mStaticEntryGroups = new ArrayList<>();

        @NonNull private final List<SafetyCenterIssue> mDismissedIssues = new ArrayList<>();
        @NonNull private Bundle mExtras = Bundle.EMPTY;

        public Builder(@NonNull SafetyCenterStatus status) {
            if (!SdkLevel.isAtLeastU()) {
                throw new UnsupportedOperationException();
            }
            mStatus = requireNonNull(status);
        }

        /** Creates a {@link Builder} with the values from the given {@link SafetyCenterData}. */
        public Builder(@NonNull SafetyCenterData safetyCenterData) {
            if (!SdkLevel.isAtLeastU()) {
                throw new UnsupportedOperationException();
            }
            requireNonNull(safetyCenterData);
            mStatus = safetyCenterData.mStatus;
            mIssues.addAll(safetyCenterData.mIssues);
            mEntriesOrGroups.addAll(safetyCenterData.mEntriesOrGroups);
            mStaticEntryGroups.addAll(safetyCenterData.mStaticEntryGroups);
            mDismissedIssues.addAll(safetyCenterData.mDismissedIssues);
            mExtras = safetyCenterData.mExtras.deepCopy();
        }

        /** Adds data for a {@link SafetyCenterIssue} to be shown in UI. */
        @NonNull
        public SafetyCenterData.Builder addIssue(@NonNull SafetyCenterIssue safetyCenterIssue) {
            mIssues.add(requireNonNull(safetyCenterIssue));
            return this;
        }

        /** Adds data for a {@link SafetyCenterEntryOrGroup} to be shown in UI. */
        @NonNull
        @SuppressWarnings("MissingGetterMatchingBuilder") // incorrectly expects "getEntryOrGroups"
        public SafetyCenterData.Builder addEntryOrGroup(
                @NonNull SafetyCenterEntryOrGroup safetyCenterEntryOrGroup) {
            mEntriesOrGroups.add(requireNonNull(safetyCenterEntryOrGroup));
            return this;
        }

        /** Adds data for a {@link SafetyCenterStaticEntryGroup} to be shown in UI. */
        @NonNull
        public SafetyCenterData.Builder addStaticEntryGroup(
                @NonNull SafetyCenterStaticEntryGroup safetyCenterStaticEntryGroup) {
            mStaticEntryGroups.add(requireNonNull(safetyCenterStaticEntryGroup));
            return this;
        }

        /** Adds data for a dismissed {@link SafetyCenterIssue} to be shown in UI. */
        @NonNull
        public SafetyCenterData.Builder addDismissedIssue(
                @NonNull SafetyCenterIssue dismissedSafetyCenterIssue) {
            mDismissedIssues.add(requireNonNull(dismissedSafetyCenterIssue));
            return this;
        }

        /**
         * Sets additional information for the {@link SafetyCenterData}.
         *
         * If not set, the default value is {@link Bundle#EMPTY}.
         */
        @NonNull
        public SafetyCenterData.Builder setExtras(@NonNull Bundle extras) {
            mExtras = requireNonNull(extras);
            return this;
        }

        /**
         * Resets additional information for the {@link SafetyCenterData} to the default value of
         * {@link Bundle#EMPTY}.
         */
        @NonNull
        public SafetyCenterData.Builder clearExtras() {
            mExtras = Bundle.EMPTY;
            return this;
        }

        /**
         * Clears data for all the {@link SafetyCenterIssue}s that were added to this {@link
         * SafetyCenterData.Builder}.
         */
        @NonNull
        public SafetyCenterData.Builder clearIssues() {
            mIssues.clear();
            return this;
        }

        /**
         * Clears data for all the {@link SafetyCenterEntryOrGroup}s that were added to this {@link
         * SafetyCenterData.Builder}.
         */
        @NonNull
        public SafetyCenterData.Builder clearEntriesOrGroups() {
            mEntriesOrGroups.clear();
            return this;
        }

        /**
         * Clears data for all the {@link SafetyCenterStaticEntryGroup}s that were added to this
         * {@link SafetyCenterData.Builder}.
         */
        @NonNull
        public SafetyCenterData.Builder clearStaticEntryGroups() {
            mStaticEntryGroups.clear();
            return this;
        }

        /**
         * Clears data for all the dismissed {@link SafetyCenterIssue}s that were added to this
         * {@link SafetyCenterData.Builder}.
         */
        @NonNull
        public SafetyCenterData.Builder clearDismissedIssues() {
            mDismissedIssues.clear();
            return this;
        }

        /**
         * Creates the {@link SafetyCenterData} defined by this {@link SafetyCenterData.Builder}.
         */
        @NonNull
        public SafetyCenterData build() {
            List<SafetyCenterIssue> issues = unmodifiableList(new ArrayList<>(mIssues));
            List<SafetyCenterEntryOrGroup> entriesOrGroups =
                    unmodifiableList(new ArrayList<>(mEntriesOrGroups));
            List<SafetyCenterStaticEntryGroup> staticEntryGroups =
                    unmodifiableList(new ArrayList<>(mStaticEntryGroups));
            List<SafetyCenterIssue> dismissedIssues =
                    unmodifiableList(new ArrayList<>(mDismissedIssues));

            return new SafetyCenterData(
                    mStatus, issues, entriesOrGroups, staticEntryGroups, dismissedIssues, mExtras);
        }
    }
}
