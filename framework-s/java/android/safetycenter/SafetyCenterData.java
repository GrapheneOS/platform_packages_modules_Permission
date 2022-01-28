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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

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
    private final SafetyCenterStatus mStatus;
    @NonNull
    private final List<SafetyCenterIssue> mIssues;
    @NonNull
    private final List<SafetyCenterEntryOrGroup> mEntriesOrGroups;
    @NonNull
    private final List<SafetyCenterStaticEntryGroup> mStaticEntryGroups;

    //TODO(b/208415162): Add support for static/advanced entries.

    /** Creates a {@link SafetyCenterData}. */
    public SafetyCenterData(
            @NonNull SafetyCenterStatus status,
            @NonNull List<SafetyCenterIssue> issues,
            @NonNull List<SafetyCenterEntryOrGroup> entriesOrGroups,
            @NonNull List<SafetyCenterStaticEntryGroup> staticEntryGroups) {
        mStatus = requireNonNull(status);
        mIssues = new ArrayList<>(issues);
        mEntriesOrGroups = new ArrayList<>(entriesOrGroups);
        mStaticEntryGroups = new ArrayList<>(staticEntryGroups);
    }

    /** Returns the overall {@link SafetyCenterStatus} of the Safety Center. */
    @NonNull
    public SafetyCenterStatus getStatus() {
        return mStatus;
    }

    /** Returns the list of active {@link SafetyCenterIssue} objects in the Safety Center. */
    @NonNull
    public List<SafetyCenterIssue> getIssues() {
        return new ArrayList<>(mIssues);
    }

    /**
     * Returns the structured list of {@link SafetyCenterEntry} and {@link SafetyCenterEntryGroup}
     * objects, wrapped in {@link SafetyCenterEntryOrGroup}.
     */
    @NonNull
    public List<SafetyCenterEntryOrGroup> getEntriesOrGroups() {
        return new ArrayList<>(mEntriesOrGroups);
    }

    /**
     * Returns the list of {@link SafetyCenterStaticEntryGroup} objects in the Safety Center.
     */
    @NonNull
    public List<SafetyCenterStaticEntryGroup> getStaticEntryGroups() {
        return new ArrayList<>(mStaticEntryGroups);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetyCenterData that = (SafetyCenterData) o;
        return Objects.equals(mStatus, that.mStatus)
                && Objects.equals(mIssues, that.mIssues)
                && Objects.equals(mEntriesOrGroups, that.mEntriesOrGroups)
                && Objects.equals(mStaticEntryGroups, that.mStaticEntryGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStatus, mIssues, mEntriesOrGroups, mStaticEntryGroups);
    }

    @Override
    public String toString() {
        return "SafetyCenterData{"
                + "mStatus=" + mStatus
                + ", mIssues=" + mIssues
                + ", mEntriesOrGroups=" + mEntriesOrGroups
                + ", mStaticEntryGroups=" + mStaticEntryGroups
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mStatus, flags);
        dest.writeTypedList(mIssues);
        dest.writeTypedList(mEntriesOrGroups);
        dest.writeTypedList(mStaticEntryGroups);
    }

    @NonNull
    public static final Creator<SafetyCenterData> CREATOR = new Creator<SafetyCenterData>() {
        @Override
        public SafetyCenterData createFromParcel(Parcel in) {
            return new SafetyCenterData(
                    in.readParcelable(
                            SafetyCenterStatus.class.getClassLoader(), SafetyCenterStatus.class),
                    in.createTypedArrayList(SafetyCenterIssue.CREATOR),
                    in.createTypedArrayList(SafetyCenterEntryOrGroup.CREATOR),
                    in.createTypedArrayList(SafetyCenterStaticEntryGroup.CREATOR));
        }

        @Override
        public SafetyCenterData[] newArray(int size) {
            return new SafetyCenterData[size];
        }
    };
}
