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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * An error that a Safety Source may report to the Safety Center.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetySourceError implements Parcelable {

    /**
     * All possible types for a {@link SafetySourceError}.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "SOURCE_ERROR_TYPE_", value = {
            SOURCE_ERROR_TYPE_UNKNOWN,
            SOURCE_ERROR_TYPE_ACTION_ERROR,
    })
    public @interface SourceErrorType {}

    /** Indicates this error is of an unknown type. */
    public static final int SOURCE_ERROR_TYPE_UNKNOWN = 0;

    /** Indicates this error reports a problem while executing an action on an issue. */
    public static final int SOURCE_ERROR_TYPE_ACTION_ERROR = 10;

    @SourceErrorType
    private final int mType;
    @Nullable
    private final String mIssueId;
    @Nullable
    private final String mActionId;

    private SafetySourceError(
            @SourceErrorType int type, @Nullable String issueId, @Nullable String actionId) {
        mType = type;
        mIssueId = issueId;
        mActionId = actionId;
    }

    /** Returns the {@link SourceErrorType} of this error. */
    @SourceErrorType
    public int getType() {
        return mType;
    }

    /**
     * Returns the id of the {@link SafetySourceIssue} this error is associated with (if any).
     *
     * @see SafetySourceIssue#getId()
     */
    @Nullable
    public String getIssueId() {
        return mIssueId;
    }

    /**
     * Returns the id of the {@link SafetySourceIssue.Action} this error is associated with (if
     * any).
     *
     * @see SafetySourceIssue.Action#getId()
     */
    @Nullable
    public String getActionId() {
        return mActionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetySourceError that = (SafetySourceError) o;
        return mType == that.mType
                && Objects.equals(mIssueId, that.mIssueId)
                && Objects.equals(mActionId, that.mActionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mIssueId, mActionId);
    }

    @Override
    public String toString() {
        return "SafetySourceError{"
                + "mType=" + mType
                + ", mIssueId='" + mIssueId + '\''
                + ", mActionId='" + mActionId + '\''
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeString(mIssueId);
        dest.writeString(mActionId);
    }

    @NonNull
    public static final Creator<SafetySourceError> CREATOR = new Creator<SafetySourceError>() {
        @Override
        public SafetySourceError createFromParcel(Parcel in) {
            return new SafetySourceError(
                    in.readInt(),
                    in.readString(),
                    in.readString());
        }

        @Override
        public SafetySourceError[] newArray(int size) {
            return new SafetySourceError[0];
        }
    };

    /** Builder class for a {@link SafetySourceError}. */
    public static final class Builder {
        @SourceErrorType
        private final int mType;
        @Nullable
        private String mIssueId;
        @Nullable
        private String mActionId;

        /** Creates a {@link Builder} for a {@link SafetySourceError}. */
        public Builder(@SourceErrorType int type) {
            mType = type;
        }

        /**
         * Sets the id of the {@link SafetySourceIssue} this error is associated with (if any).
         *
         * <p>Typically this would only be used for issues containing actions marked {@link
         * SafetySourceIssue.Action#isResolving()}, since Safety Center is waiting for a response
         * and will return an error if none is given in a certain amount of time.
         */
        @NonNull
        public Builder setIssueId(@Nullable String issueId) {
            mIssueId = issueId;
            return this;
        }

        /**
         * Sets the id of the {@link SafetySourceIssue.Action} this error is associated with (if
         * any).
         *
         * <p>Typically this would only be used for actions marked {@link
         * SafetySourceIssue.Action#isResolving()}, since Safety Center is waiting for a response
         * and will return an error if none is given in a certain amount of time.
         */
        @NonNull
        public Builder setActionId(@Nullable String actionId) {
            mActionId = actionId;
            return this;
        }

        /** Creates the {@link SafetySourceError} defined by this {@link Builder}. */
        @NonNull
        public SafetySourceError build() {
            return new SafetySourceError(mType, mIssueId, mActionId);
        }
    }
}
