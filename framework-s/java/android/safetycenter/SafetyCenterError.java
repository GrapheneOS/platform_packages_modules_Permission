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
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.util.Objects;

/**
 * An error the Safety Center should display to the user.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterError implements Parcelable {

    @NonNull
    private final CharSequence mErrorMessage;

    /** Creates a {@link SafetyCenterError} with a given error message. */
    public SafetyCenterError(@NonNull CharSequence errorMessage) {
        mErrorMessage = errorMessage;
    }

    /** Returns an error message to display to the user. */
    @NonNull
    public CharSequence getErrorMessage() {
        return mErrorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetyCenterError that = (SafetyCenterError) o;
        return TextUtils.equals(mErrorMessage, that.mErrorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mErrorMessage);
    }

    @Override
    public String toString() {
        return "SafetyCenterError{"
                + "mErrorMessage=" + mErrorMessage
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        TextUtils.writeToParcel(mErrorMessage, dest, flags);
    }

    @NonNull
    public static final Creator<SafetyCenterError> CREATOR = new Creator<SafetyCenterError>() {
        @Override
        public SafetyCenterError createFromParcel(Parcel in) {
            return new SafetyCenterError(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
        }

        @Override
        public SafetyCenterError[] newArray(int size) {
            return new SafetyCenterError[0];
        }
    };
}
