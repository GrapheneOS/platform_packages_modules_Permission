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
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.PendingIntent;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.util.Objects;

/**
 * A static, stateless entry in the Safety Center.
 *
 * <p>Static entries have no changing severity level or associated issues. They provide simple links
 * or actions for safety-related features via {@link #getPendingIntent()}.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterStaticEntry implements Parcelable {

    @NonNull
    private final CharSequence mTitle;
    @Nullable
    private final CharSequence mSummary;
    @NonNull
    private final PendingIntent mPendingIntent;

    /**
     * Creates a {@link SafetyCenterStaticEntry} with the given title, summary, and pendingIntent
     */
    public SafetyCenterStaticEntry(
            @NonNull CharSequence title,
            @Nullable CharSequence summary,
            @NonNull PendingIntent pendingIntent) {
        mTitle = requireNonNull(title);
        mSummary = summary;
        mPendingIntent = requireNonNull(pendingIntent);
    }

    /** Returns the title that describes this entry. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the summary text that describes this entry if present, or {@code null} otherwise. */
    @Nullable
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the {@link PendingIntent} to execute when this entry is selected. */
    @NonNull
    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetyCenterStaticEntry that = (SafetyCenterStaticEntry) o;
        return TextUtils.equals(mTitle, that.mTitle)
                && TextUtils.equals(mSummary, that.mSummary)
                && Objects.equals(mPendingIntent, that.mPendingIntent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTitle, mSummary, mPendingIntent);
    }

    @Override
    public String toString() {
        return "SafetyCenterStaticEntry{"
                + "mTitle=" + mTitle
                + ", mSummary=" + mSummary
                + ", mPendingIntent=" + mPendingIntent
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
        dest.writeParcelable(mPendingIntent, flags);
    }

    @NonNull
    public static final Creator<SafetyCenterStaticEntry> CREATOR =
            new Creator<SafetyCenterStaticEntry>() {
        @Override
        public SafetyCenterStaticEntry createFromParcel(Parcel source) {
            return new SafetyCenterStaticEntry(
                    /* title= */ TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source),
                    /* summary= */ TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source),
                    source.readParcelable(
                            PendingIntent.class.getClassLoader(), PendingIntent.class));
        }

        @Override
        public SafetyCenterStaticEntry[] newArray(int size) {
            return new SafetyCenterStaticEntry[size];
        }
    };
}
