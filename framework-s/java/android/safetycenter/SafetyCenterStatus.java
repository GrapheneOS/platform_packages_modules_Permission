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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * The overall status of the Safety Center.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterStatus implements Parcelable {

    /**
     * All possible overall severity levels for the Safety Center.
     *
     * <p>The overall severity level is calculated from the severity level and statuses of all
     * issues and entries in the Safety Center.
     *
     * @see SafetyCenterStatus#getSeverityLevel()
     * @see Builder#setSeverityLevel(int)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "OVERALL_SEVERITY_LEVEL_", value = {
            OVERALL_SEVERITY_LEVEL_UNKNOWN,
            OVERALL_SEVERITY_LEVEL_OK,
            OVERALL_SEVERITY_LEVEL_RECOMMENDATION,
            OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING,
    })
    @interface OverallSeverityLevel {
    }

    /** Indicates the overall severity level of the Safety Center is not currently known. */
    public static final int OVERALL_SEVERITY_LEVEL_UNKNOWN = 1000;

    /**
     * Indicates the overall safety status of the device is OK and there are no actionable issues.
     */
    public static final int OVERALL_SEVERITY_LEVEL_OK = 1100;

    /** Indicates the presence of safety recommendations which the user is encouraged to act on. */
    public static final int OVERALL_SEVERITY_LEVEL_RECOMMENDATION = 1200;

    /** Indicates the presence of critical safety warnings on the device. */
    public static final int OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING = 1300;

    @NonNull
    private final CharSequence mTitle;
    @NonNull
    private final CharSequence mSummary;
    @OverallSeverityLevel
    private final int mSeverityLevel;

    private SafetyCenterStatus(
            @NonNull CharSequence title,
            @NonNull CharSequence summary,
            @OverallSeverityLevel int severityLevel) {
        mTitle = requireNonNull(title);
        mSummary = requireNonNull(summary);
        mSeverityLevel = severityLevel;
    }

    /** Returns the title which describes the overall safety state of the device. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the summary text which adds detail to the overall safety state of the device. */
    @NonNull
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the current {@link OverallSeverityLevel} of the Safety Center. */
    @OverallSeverityLevel
    public int getSeverityLevel() {
        return mSeverityLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetyCenterStatus that = (SafetyCenterStatus) o;
        return mSeverityLevel == that.mSeverityLevel
                && TextUtils.equals(mTitle, that.mTitle)
                && TextUtils.equals(mSummary, that.mSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTitle, mSummary, mSeverityLevel);
    }

    @Override
    public String toString() {
        return "SafetyCenterStatus{"
                + "mTitle=" + mTitle
                + ", mSummary=" + mSummary
                + ", mSeverityLevel=" + mSeverityLevel
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        TextUtils.writeToParcel(mTitle, dest, flags);
        TextUtils.writeToParcel(mSummary, dest, flags);
        dest.writeInt(mSeverityLevel);
    }

    @NonNull
    public static final Creator<SafetyCenterStatus> CREATOR = new Creator<SafetyCenterStatus>() {
        @Override
        public SafetyCenterStatus createFromParcel(Parcel in) {
            return new Builder()
                    .setTitle(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                    .setSummary(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                    .setSeverityLevel(in.readInt())
                    .build();
        }

        @Override
        public SafetyCenterStatus[] newArray(int size) {
            return new SafetyCenterStatus[size];
        }
    };

    /** Builder class for {@link SafetyCenterStatus}. */
    public static final class Builder {
        private CharSequence mTitle;
        private CharSequence mSummary;
        @OverallSeverityLevel
        private int mSeverityLevel = OVERALL_SEVERITY_LEVEL_UNKNOWN;

        /** Creates an empty {@link Builder} for {@link SafetyCenterStatus}*/
        public Builder() {}

        /**
         * Creates a pre-populated {@link Builder} with the values from the given {@link
         * SafetyCenterStatus}.
         */
        public Builder(@NonNull SafetyCenterStatus safetyCenterStatus) {
            mTitle = safetyCenterStatus.mTitle;
            mSummary = safetyCenterStatus.mSummary;
            mSeverityLevel = safetyCenterStatus.mSeverityLevel;
        }

        /** Sets the title for this status. */
        @NonNull
        public Builder setTitle(@NonNull CharSequence title) {
            mTitle = requireNonNull(title);
            return this;
        }

        /** Sets the summary text for this status. */
        @NonNull
        public Builder setSummary(@NonNull CharSequence summary) {
            mSummary = requireNonNull(summary);
            return this;
        }

        /** Sets the {@link OverallSeverityLevel} of this status. */
        @NonNull
        public Builder setSeverityLevel(@OverallSeverityLevel int severityLevel) {
            mSeverityLevel = severityLevel;
            return this;
        }

        /** Creates the {@link SafetyCenterStatus} defined by this {@link Builder}. */
        @NonNull
        public SafetyCenterStatus build() {
            return new SafetyCenterStatus(mTitle, mSummary, mSeverityLevel);
        }
    }
}
