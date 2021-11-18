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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Data for a safety preference in the Safety Center page. A safety preference represents the
 * overall safety state of a safety source.
 *
 * @hide
 */
// @SystemApi -- Add this line back when ready for API council review.
// TODO(b/205551986): Move this class into `framework-s`, add NonNull annotations, replace usages of
//  `androidx.annotation.IntDef` with `android.annotation.IntDef` and add prefixes to IntDefs.
@RequiresApi(TIRAMISU)
public final class SafetyPreferenceData implements Parcelable {

    /**
     * All possible severity levels for the safety source's safety state.
     *
     * @hide
     */
    // TODO(b/205806500): Determine full list of severity levels. We may add a new one to signify
    //  that there was an error retrieving data.
    @IntDef(prefix = { "SEVERITY_LEVEL_" }, value = {
            SEVERITY_LEVEL_NONE,
            SEVERITY_LEVEL_NO_ISSUES,
            SEVERITY_LEVEL_RECOMMENDATION,
            SEVERITY_LEVEL_CRITICAL_WARNING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SeverityLevel {}

    /**
     * Indicates that no severity is associated with the message. This severity will be reflected in
     * the UI through the absence of an icon.
     */
    public static final int SEVERITY_LEVEL_NONE = 100;

    /**
     * Indicates that no issues were detected. This severity will be reflected in the UI through a
     * green icon.
     */
    public static final int SEVERITY_LEVEL_NO_ISSUES = 200;

    /**
     * Indicates the presence of a medium-severity issue which the user is encouraged to act on.
     * This severity will be reflected in the UI through a yellow icon.
     */
    public static final int SEVERITY_LEVEL_RECOMMENDATION = 300;

    /**
     * Indicates the presence of a critical or urgent security issue that should be addressed by the
     * user. This severity will be reflected in the UI through a red icon.
     */
    public static final int SEVERITY_LEVEL_CRITICAL_WARNING = 400;

    @NonNull
    public static final Parcelable.Creator<SafetyPreferenceData> CREATOR =
            new Parcelable.Creator<SafetyPreferenceData>() {
                @Override
                public SafetyPreferenceData createFromParcel(Parcel in) {
                    CharSequence title =
                            requireNonNull(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                    CharSequence summary =
                            requireNonNull(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                    @SeverityLevel int severityLevel = in.readInt();
                    return new SafetyPreferenceData(title, summary, severityLevel);
                }

                @Override
                public SafetyPreferenceData[] newArray(int size) {
                    return new SafetyPreferenceData[size];
                }
            };

    @NonNull
    private final CharSequence mTitle;

    @NonNull
    private final CharSequence mSummary;

    private final @SeverityLevel int mSeverityLevel;

    private SafetyPreferenceData(@NonNull CharSequence title, @NonNull CharSequence summary,
            @SeverityLevel int severityLevel) {
        this.mTitle = title;
        this.mSummary = summary;
        this.mSeverityLevel = severityLevel;
    }

    /** Returns the localized title of the safety preference to be displayed in the UI. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the localized summary of the safety preference to be displayed in the UI. */
    @NonNull
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the {@link SeverityLevel} of the safety preference. */
    @SeverityLevel
    public int getSeverityLevel() {
        return mSeverityLevel;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetyPreferenceData)) return false;
        SafetyPreferenceData that = (SafetyPreferenceData) o;
        return mSeverityLevel == that.mSeverityLevel && mTitle.equals(that.mTitle)
                && mSummary.equals(
                that.mSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTitle, mSummary, mSeverityLevel);
    }

    @Override
    public String toString() {
        return "SafetyPreferenceData{"
                + "mTitle='"
                + mTitle
                + '\''
                + ", mSummary='"
                + mSummary
                + '\''
                + ", mSeverityLevel="
                + mSeverityLevel
                + '}';
    }

    /** Builder class for {@link SafetyPreferenceData}. */
    public static final class Builder {
        @NonNull
        private final CharSequence mTitle;

        @NonNull
        private final CharSequence mSummary;

        private @SeverityLevel final int mSeverityLevel;

        /** Creates a {@link Builder} for a {@link SafetyPreferenceData}. */
        public Builder(@NonNull CharSequence title, @NonNull CharSequence summary,
                @SeverityLevel int severityLevel) {
            this.mTitle = requireNonNull(title);
            this.mSummary = requireNonNull(summary);
            this.mSeverityLevel = severityLevel;
        }

        /** Creates the {@link SafetyPreferenceData} defined by this {@link Builder}. */
        @NonNull
        public SafetyPreferenceData build() {
            return new SafetyPreferenceData(mTitle, mSummary, mSeverityLevel);
        }
    }
}
