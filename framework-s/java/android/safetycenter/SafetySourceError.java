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

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import java.util.Objects;

/**
 * An error that a Safety Source may report to the Safety Center.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetySourceError implements Parcelable {
    @NonNull
    private final SafetyEvent mSafetyEvent;

    public SafetySourceError(@NonNull SafetyEvent safetyEvent) {
        mSafetyEvent = safetyEvent;
    }

    /** Returns the safety event associated with this error. */
    @NonNull
    public SafetyEvent getSafetyEvent() {
        return mSafetyEvent;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetySourceError that = (SafetySourceError) o;
        return mSafetyEvent.equals(that.mSafetyEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSafetyEvent);
    }

    @Override
    public String toString() {
        return "SafetySourceError{"
                + "mSafetyEvent="
                + mSafetyEvent
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mSafetyEvent, flags);
    }

    @NonNull
    public static final Creator<SafetySourceError> CREATOR = new Creator<SafetySourceError>() {
        @Override
        public SafetySourceError createFromParcel(Parcel in) {
            return new SafetySourceError(in.readParcelable(SafetyEvent.class.getClassLoader(),
                    SafetyEvent.class));
        }

        @Override
        public SafetySourceError[] newArray(int size) {
            return new SafetySourceError[0];
        }
    };
}
