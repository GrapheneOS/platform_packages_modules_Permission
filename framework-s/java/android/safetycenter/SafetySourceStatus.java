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
import android.annotation.SystemApi;
import android.app.PendingIntent;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Data for a safety source status in the Safety Center page, which conveys the overall state of
 * the safety source and allows a user to navigate to the source.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetySourceStatus implements Parcelable {

    /**
     * Indicates that no status is associated with the information. This status will be reflected in
     * the UI through the absence of an icon.
     */
    public static final int STATUS_LEVEL_NONE = 100;

    /**
     * Indicates that no issues were detected. This status will be reflected in the UI through a
     * green icon.
     */
    public static final int STATUS_LEVEL_NO_ISSUES = 200;

    /**
     * Indicates the presence of a medium-status issue which the user is encouraged to act on.
     * This status will be reflected in the UI through a yellow icon.
     */
    public static final int STATUS_LEVEL_RECOMMENDATION = 300;

    /**
     * Indicates the presence of a critical or urgent safety issue that should be addressed by the
     * user. This status will be reflected in the UI through a red icon.
     */
    public static final int STATUS_LEVEL_CRITICAL_WARNING = 400;

    @NonNull
    public static final Parcelable.Creator<SafetySourceStatus> CREATOR =
            new Parcelable.Creator<SafetySourceStatus>() {
                @Override
                public SafetySourceStatus createFromParcel(Parcel in) {
                    CharSequence title =
                            requireNonNull(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                    CharSequence summary =
                            requireNonNull(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                    int statusLevel = in.readInt();
                    PendingIntent pendingIntent =
                            requireNonNull(PendingIntent.readPendingIntentOrNullFromParcel(in));
                    return new SafetySourceStatus(title, summary, statusLevel, pendingIntent);
                }

                @Override
                public SafetySourceStatus[] newArray(int size) {
                    return new SafetySourceStatus[size];
                }
            };

    @NonNull
    private final CharSequence mTitle;
    @NonNull
    private final CharSequence mSummary;
    @StatusLevel
    private final int mStatusLevel;
    @NonNull
    private final PendingIntent mPendingIntent;

    private SafetySourceStatus(@NonNull CharSequence title, @NonNull CharSequence summary,
            @StatusLevel int statusLevel, @NonNull PendingIntent pendingIntent) {
        this.mTitle = title;
        this.mSummary = summary;
        this.mStatusLevel = statusLevel;
        this.mPendingIntent = pendingIntent;
    }

    /** Returns the localized title of the safety source status to be displayed in the UI. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the localized summary of the safety source status to be displayed in the UI. */
    @NonNull
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the {@link StatusLevel} of the status. */
    @StatusLevel
    public int getStatusLevel() {
        return mStatusLevel;
    }

    /**
     * Returns the {@link PendingIntent} that will be invoked when the safety source status UI is
     * clicked on.
     */
    @NonNull
    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        TextUtils.writeToParcel(mTitle, dest, flags);
        TextUtils.writeToParcel(mSummary, dest, flags);
        dest.writeInt(mStatusLevel);
        mPendingIntent.writeToParcel(dest, flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourceStatus)) return false;
        SafetySourceStatus that = (SafetySourceStatus) o;
        return mStatusLevel == that.mStatusLevel
                && TextUtils.equals(mTitle, that.mTitle)
                && TextUtils.equals(mSummary, that.mSummary)
                && mPendingIntent.equals(that.mPendingIntent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTitle, mSummary, mStatusLevel, mPendingIntent);
    }

    @Override
    public String toString() {
        return "SafetySourceStatus{"
                + "mTitle="
                + mTitle
                + ", mSummary="
                + mSummary
                + ", mStatusLevel="
                + mStatusLevel
                + ", mPendingIntent="
                + mPendingIntent
                + '}';
    }

    /**
     * All possible status levels for the safety source status.
     *
     * <p>The status level is meant to convey the overall state of the safety source and contributes
     * to the top-level safety status of the user. Choose the status level to represent the most
     * severe of all the safety source's issues.
     *
     * <p>The numerical values of the levels are not used directly, rather they are used to build
     * a continuum of levels which support relative comparison.
     *
     * <p>The higher the status level, the worse the safety level of the source and the higher
     * the threat to the user.
     *
     * @hide
     */
    // TODO(b/205806500): Determine full list of status levels. We may add a new one to signify
    //  that there was an error retrieving data.
    @IntDef(prefix = {"STATUS_LEVEL_"}, value = {
            STATUS_LEVEL_NONE,
            STATUS_LEVEL_NO_ISSUES,
            STATUS_LEVEL_RECOMMENDATION,
            STATUS_LEVEL_CRITICAL_WARNING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StatusLevel {
    }

    /** Builder class for {@link SafetySourceStatus}. */
    public static final class Builder {
        @NonNull
        private final CharSequence mTitle;
        @NonNull
        private final CharSequence mSummary;
        @StatusLevel
        private final int mStatusLevel;
        @NonNull
        private final PendingIntent mPendingIntent;

        /** Creates a {@link Builder} for a {@link SafetySourceStatus}. */
        public Builder(@NonNull CharSequence title, @NonNull CharSequence summary,
                @StatusLevel int statusLevel, @NonNull PendingIntent pendingIntent) {
            this.mTitle = requireNonNull(title);
            this.mSummary = requireNonNull(summary);
            this.mStatusLevel = statusLevel;
            this.mPendingIntent = requireNonNull(pendingIntent);
        }

        /** Creates the {@link SafetySourceStatus} defined by this {@link Builder}. */
        @NonNull
        public SafetySourceStatus build() {
            return new SafetySourceStatus(mTitle, mSummary, mStatusLevel, mPendingIntent);
        }
    }
}
