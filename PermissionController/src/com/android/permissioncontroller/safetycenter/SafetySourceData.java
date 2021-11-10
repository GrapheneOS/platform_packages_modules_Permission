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

package com.android.permissioncontroller.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Objects.requireNonNull;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import java.util.Objects;

/**
 * Data class used by safety sources to propagate safety information such as a generic status,
 * potential warnings and other related metadata.
 *
 * @hide
 */
// @SystemApi
@RequiresApi(TIRAMISU)
// TODO(b/205551986): Move this to the right place and add back the NonNull annotations once
// b/205289292 is fixed.
public final class SafetySourceData implements Parcelable {
    public static final Parcelable.Creator<SafetySourceData> CREATOR =
            new Parcelable.Creator<SafetySourceData>() {
                @Override
                public SafetySourceData createFromParcel(Parcel in) {
                    String id = requireNonNull(in.readString());
                    return new SafetySourceData(id);
                }

                @Override
                public SafetySourceData[] newArray(int size) {
                    return new SafetySourceData[size];
                }
            };

    private final String mId;

    /**
     * Creates a new {@link SafetySourceData} based on the id of the associated source.
     */
    public SafetySourceData(String id) {
        this.mId = id;
    }

    /** Returns the id of the associated source. */
    public String getId() {
        return mId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourceData)) return false;
        SafetySourceData that = (SafetySourceData) o;
        return mId.equals(that.mId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId);
    }

    @Override
    public String toString() {
        return "SafetySourceData{"
                + "mId='"
                + mId
                + '\''
                + '}';
    }
}
