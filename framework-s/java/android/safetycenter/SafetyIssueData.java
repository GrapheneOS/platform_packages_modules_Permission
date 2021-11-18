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
 * Data for a safety issue in the Safety Center page. A safety issue represents an actionable issue
 *  relating to a particular safety source.
 *
 * @hide
 */
// @SystemApi -- Add this line back when ready for API council review.
// TODO(b/205551986): Move this class into `framework-s`, add NonNull annotations, replace usages of
//  `androidx.annotation.IntDef` with `android.annotation.IntDef` and add prefixes to IntDefs.
@RequiresApi(TIRAMISU)
public final class SafetyIssueData implements Parcelable {

    /**
     * All possible severity levels for the safety issue.
     *
     * @hide
     */
    @IntDef(prefix = { "SEVERITY_LEVEL_" }, value = {
            SEVERITY_LEVEL_INFORMATION,
            SEVERITY_LEVEL_RECOMMENDATION,
            SEVERITY_LEVEL_CRITICAL_WARNING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SeverityLevel {}

    /**
     * Indicates an informational message. This severity will be reflected in the UI through a
     * green icon.
     */
    public static final int SEVERITY_LEVEL_INFORMATION = 200;

    /**
     * Indicates a medium-severity issue which the user is encouraged to act on. This severity will
     * be reflected in the UI through a yellow icon.
     */
    public static final int SEVERITY_LEVEL_RECOMMENDATION = 300;

    /**
     * Indicates a critical or urgent security issue that should be addressed by the user. This
     * severity will be reflected in the UI through a red icon.
     */
    public static final int SEVERITY_LEVEL_CRITICAL_WARNING = 400;

    @NonNull
    public static final Parcelable.Creator<SafetyIssueData> CREATOR =
            new Parcelable.Creator<SafetyIssueData>() {
                @Override
                public SafetyIssueData createFromParcel(Parcel in) {
                    CharSequence title =
                            requireNonNull(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                    CharSequence summary =
                            requireNonNull(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                    int severityLevel = in.readInt();
                    return new SafetyIssueData(title, summary, severityLevel);
                }

                @Override
                public SafetyIssueData[] newArray(int size) {
                    return new SafetyIssueData[size];
                }
            };

    @NonNull
    private final CharSequence mTitle;

    @NonNull
    private final CharSequence mSummary;

    private final @SeverityLevel int mSeverityLevel;

    private SafetyIssueData(@NonNull CharSequence title, @NonNull CharSequence summary,
            @SeverityLevel int severityLevel) {
        this.mTitle = title;
        this.mSummary = summary;
        this.mSeverityLevel = severityLevel;
    }

    /** Returns the localized title of the safety issue to be displayed in the UI. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the localized summary of the safety issue to be displayed in the UI. */
    @NonNull
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the {@link SeverityLevel} of the safety issue. */
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
        if (!(o instanceof SafetyIssueData)) return false;
        SafetyIssueData that = (SafetyIssueData) o;
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
        return "SafetyIssueData{"
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

    /** Builder class for {@link SafetyIssueData}. */
    public static final class Builder {
        @NonNull
        private final CharSequence mTitle;

        @NonNull
        private final CharSequence mSummary;

        private @SeverityLevel final int mSeverityLevel;

        /** Creates a {@link Builder} for a {@link SafetyIssueData}. */
        public Builder(@NonNull CharSequence title, @NonNull CharSequence summary,
                @SeverityLevel int severityLevel) {
            this.mTitle = requireNonNull(title);
            this.mSummary = requireNonNull(summary);
            this.mSeverityLevel = severityLevel;
        }

        /** Creates the {@link SafetyIssueData} defined by this {@link Builder}. */
        @NonNull
        public SafetyIssueData build() {
            return new SafetyIssueData(mTitle, mSummary, mSeverityLevel);
        }
    }
}
