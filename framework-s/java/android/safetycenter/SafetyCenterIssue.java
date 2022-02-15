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
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.PendingIntent;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An issue in the Safety Center.
 *
 * <p>An issue represents an actionable matter on the device of elevated importance.
 *
 * <p>It contains localized messages to display to the user, explaining the underlying threat or
 * warning and suggested fixes, and contains actions that a user may take from the UI to resolve the
 * issue.
 *
 * <p>Issues are ephemeral and disappear when resolved by user action or dismissal.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterIssue implements Parcelable {

    /**
     * All possible severity levels for a {@link SafetyCenterIssue}.
     *
     * @see SafetyCenterIssue#getSeverityLevel()
     * @see Builder#setSeverityLevel(int)
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "ISSUE_SEVERITY_LEVEL_", value = {
            ISSUE_SEVERITY_LEVEL_OK,
            ISSUE_SEVERITY_LEVEL_RECOMMENDATION,
            ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING,
    })
    public @interface IssueSeverityLevel {
    }

    /** Indicates that this is low-severity, and informational. */
    public static final int ISSUE_SEVERITY_LEVEL_OK = 2100;

    /** Indicates that this issue describes a safety recommendation. */
    public static final int ISSUE_SEVERITY_LEVEL_RECOMMENDATION = 2200;

    /** Indicates that this issue describes a critical safety warning. */
    public static final int ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING = 2300;

    @NonNull
    private final String mId;
    @NonNull
    private final CharSequence mTitle;
    @Nullable
    private final CharSequence mSubtitle;
    @NonNull
    private final CharSequence mSummary;
    @IssueSeverityLevel
    private final int mSeverityLevel;
    private final boolean mDismissible;
    private final boolean mShouldConfirmDismissal;
    @NonNull
    private final List<Action> mActions;

    private SafetyCenterIssue(
            @NonNull String id,
            @NonNull CharSequence title,
            @Nullable CharSequence subtitle,
            @NonNull CharSequence summary,
            @IssueSeverityLevel int severityLevel,
            boolean isDismissible,
            boolean shouldConfirmDismissal,
            @NonNull List<Action> actions) {
        mId = requireNonNull(id);
        mTitle = requireNonNull(title);
        mSubtitle = subtitle;
        mSummary = requireNonNull(summary);
        mSeverityLevel = severityLevel;
        mDismissible = isDismissible;
        mShouldConfirmDismissal = shouldConfirmDismissal;
        mActions = new ArrayList<>(actions);
    }

    /**
     * Returns the encoded string ID which uniquely identifies this issue within the Safety Center
     * on the device for the current user across all profiles and accounts.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns the title that describes this issue. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the subtitle of this issue, or {@code null} if it has none. */
    @Nullable
    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    /** Returns the summary text that describes this issue. */
    @NonNull
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the {@link IssueSeverityLevel} of this issue. */
    @IssueSeverityLevel
    public int getSeverityLevel() {
        return mSeverityLevel;
    }

    /** Returns {@code true} if this issue can be dismissed. */
    public boolean isDismissible() {
        return mDismissible;
    }

    /** Returns {@code true} if this issue should have its dismissal confirmed. */
    public boolean shouldConfirmDismissal() {
        return mShouldConfirmDismissal;
    }

    /**
     * Returns the ordered list of {@link Action} objects that may be taken to resolve this issue.
     *
     * <p>An issue may have 0-2 actions. The first action will be considered the "Primary" action of
     * the issue.
     */
    @NonNull
    public List<Action> getActions() {
        return new ArrayList<>(mActions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetyCenterIssue that = (SafetyCenterIssue) o;
        return mSeverityLevel == that.mSeverityLevel
                && mDismissible == that.mDismissible
                && mShouldConfirmDismissal == that.mShouldConfirmDismissal
                && Objects.equals(mId, that.mId)
                && TextUtils.equals(mTitle, that.mTitle)
                && TextUtils.equals(mSubtitle, that.mSubtitle)
                && TextUtils.equals(mSummary, that.mSummary)
                && Objects.equals(mActions, that.mActions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitle, mSubtitle, mSummary, mSeverityLevel, mDismissible,
                mShouldConfirmDismissal, mActions);
    }

    @Override
    public String toString() {
        return "SafetyCenterIssue{"
                + "mId='" + mId + '\''
                + ", mTitle=" + mTitle
                + ", mSubtitle=" + mSubtitle
                + ", mSummary=" + mSummary
                + ", mSeverityLevel=" + mSeverityLevel
                + ", mDismissible=" + mDismissible
                + ", mConfirmDismissal=" + mShouldConfirmDismissal
                + ", mActions=" + mActions
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mId);
        TextUtils.writeToParcel(mTitle, dest, flags);
        TextUtils.writeToParcel(mSubtitle, dest, flags);
        TextUtils.writeToParcel(mSummary, dest, flags);
        dest.writeInt(mSeverityLevel);
        dest.writeBoolean(mDismissible);
        dest.writeBoolean(mShouldConfirmDismissal);
        dest.writeTypedList(mActions);
    }

    @NonNull
    public static final Creator<SafetyCenterIssue> CREATOR = new Creator<SafetyCenterIssue>() {
        @Override
        public SafetyCenterIssue createFromParcel(Parcel in) {
            return new Builder(in.readString())
                    .setTitle(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                    .setSubtitle(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                    .setSummary(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                    .setSeverityLevel(in.readInt())
                    .setDismissible(in.readBoolean())
                    .setShouldConfirmDismissal(in.readBoolean())
                    .setActions(in.createTypedArrayList(Action.CREATOR))
                    .build();
        }

        @Override
        public SafetyCenterIssue[] newArray(int size) {
            return new SafetyCenterIssue[size];
        }
    };

    /** Builder class for {@link SafetyCenterIssue}. */
    public static final class Builder {
        @NonNull
        private String mId;
        private CharSequence mTitle;
        private CharSequence mSubtitle;
        private CharSequence mSummary;
        @IssueSeverityLevel
        private int mSeverityLevel = ISSUE_SEVERITY_LEVEL_OK;
        private boolean mDismissible;
        private boolean mShouldConfirmDismissal;
        private List<Action> mActions = new ArrayList<>();

        /**
         * Creates a {@link Builder} for a {@link SafetyCenterIssue}.
         *
         * @param id an encoded string ID to be returned by {@link #getId()}
         */
        public Builder(@NonNull String id) {
            mId = requireNonNull(id);
        }

        public Builder(@NonNull SafetyCenterIssue issue) {
            mId = issue.mId;
            mTitle = issue.mTitle;
            mSubtitle = issue.mSubtitle;
            mSummary = issue.mSummary;
            mSeverityLevel = issue.mSeverityLevel;
            mDismissible = issue.mDismissible;
            mShouldConfirmDismissal = issue.mShouldConfirmDismissal;
            mActions = new ArrayList<>(issue.mActions);
        }

        /** Sets the ID for this issue. */
        @NonNull
        public Builder setId(@NonNull String id) {
            mId = requireNonNull(id);
            return this;
        }

        /** Sets the title for this issue. */
        @NonNull
        public Builder setTitle(@NonNull CharSequence title) {
            mTitle = requireNonNull(title);
            return this;
        }

        /** Sets or clears the optional subtitle for this issue. */
        @NonNull
        public Builder setSubtitle(@Nullable CharSequence subtitle) {
            mSubtitle = subtitle;
            return this;
        }

        /** Sets the summary for this issue. */
        @NonNull
        public Builder setSummary(@NonNull CharSequence summary) {
            mSummary = requireNonNull(summary);
            return this;
        }

        /** Sets {@link IssueSeverityLevel} for this issue. */
        @NonNull
        public Builder setSeverityLevel(@IssueSeverityLevel int severityLevel) {
            mSeverityLevel = severityLevel;
            return this;
        }

        /** Sets whether or not this issue can be dismissed. */
        @NonNull
        public Builder setDismissible(boolean dismissible) {
            mDismissible = dismissible;
            return this;
        }

        /** Sets whether or not this issue should have its dismissal confirmed. */
        @NonNull
        public Builder setShouldConfirmDismissal(boolean confirmDismissal) {
            mShouldConfirmDismissal = confirmDismissal;
            return this;
        }

        /** Sets the list of potential actions to be taken to resolve this issue. */
        @NonNull
        public Builder setActions(@NonNull List<Action> actions) {
            mActions = requireNonNull(actions);
            return this;
        }

        /** Creates the {@link SafetyCenterIssue} defined by this {@link Builder}. */
        @NonNull
        public SafetyCenterIssue build() {
            return new SafetyCenterIssue(
                    mId,
                    mTitle,
                    mSubtitle,
                    mSummary,
                    mSeverityLevel,
                    mDismissible,
                    mShouldConfirmDismissal,
                    mActions);
        }
    }

    /**
     * An action that can be taken to resolve a given issue.
     *
     * <p>When a user initiates an {@link Action}, that action's associated {@link PendingIntent}
     * will be executed, and the {@code successMessage} will be displayed if present.
     *
     * @hide
     */
    @SystemApi
    public static final class Action implements Parcelable {
        @NonNull
        private final CharSequence mLabel;
        @NonNull
        private final PendingIntent mPendingIntent;
        private final boolean mResolving;
        @Nullable
        private final CharSequence mSuccessMessage;

        private Action(
                @NonNull CharSequence label,
                @NonNull PendingIntent pendingIntent,
                boolean resolving,
                @Nullable CharSequence successMessage) {
            mLabel = requireNonNull(label);
            mPendingIntent = requireNonNull(pendingIntent);
            mResolving = resolving;
            mSuccessMessage = successMessage;
        }

        /** Returns a label describing this {@link Action}. */
        @NonNull
        public CharSequence getLabel() {
            return mLabel;
        }

        /** Returns the {@link PendingIntent} to execute when this {@link Action} is taken. */
        @NonNull
        public PendingIntent getPendingIntent() {
            return mPendingIntent;
        }

        /**
         * Returns whether invoking this action will fix or address the issue sufficiently for it
         * to be considered resolved i.e. the issue will no longer need to be conveyed to the user
         * in the UI.
         */
        public boolean isResolving() {
            return mResolving;
        }

        /**
         * Returns the success message to display after successfully completing this {@link Action}
         * or {@code null} if none should be displayed.
         */
        @Nullable
        public CharSequence getSuccessMessage() {
            return mSuccessMessage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Action action = (Action) o;
            return TextUtils.equals(mLabel, action.mLabel)
                    && Objects.equals(mPendingIntent, action.mPendingIntent)
                    && mResolving == action.mResolving
                    && TextUtils.equals(mSuccessMessage, action.mSuccessMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mLabel, mSuccessMessage, mResolving, mPendingIntent);
        }

        @Override
        public String toString() {
            return "Action{"
                    + "mLabel=" + mLabel
                    + ", mPendingIntent=" + mPendingIntent
                    + ", mResolving=" + mResolving
                    + ", mSuccessMessage=" + mSuccessMessage
                    + '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            TextUtils.writeToParcel(mLabel, dest, flags);
            dest.writeParcelable(mPendingIntent, flags);
            dest.writeBoolean(mResolving);
            TextUtils.writeToParcel(mSuccessMessage, dest, flags);
        }

        @NonNull
        public static final Creator<Action> CREATOR = new Creator<Action>() {
            @Override
            public Action createFromParcel(Parcel in) {
                return new Action.Builder()
                        .setLabel(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                        .setPendingIntent(
                                in.readParcelable(
                                        PendingIntent.class.getClassLoader(), PendingIntent.class))
                        .setResolving(in.readBoolean())
                        .setSuccessMessage(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                        .build();
            }

            @Override
            public Action[] newArray(int size) {
                return new Action[size];
            }
        };

        /** Builder class for {@link Action}. */
        public static final class Builder {
            private CharSequence mLabel;
            private PendingIntent mPendingIntent;
            private boolean mResolving;
            private CharSequence mSuccessMessage;

            /** Sets the label of this {@link Action}. */
            @NonNull
            public Builder setLabel(@NonNull CharSequence label) {
                mLabel = requireNonNull(label);
                return this;
            }

            /** Sets the {@link PendingIntent} to be sent when this {@link Action} is taken. */
            @NonNull
            public Builder setPendingIntent(@NonNull PendingIntent pendingIntent) {
                mPendingIntent = requireNonNull(pendingIntent);
                return this;
            }

            /**
             * Sets whether or not this action is resolving. Defaults to false.
             *
             * @see #isResolving()
             */
            @NonNull
            public Builder setResolving(boolean resolving) {
                mResolving = resolving;
                return this;
            }

            /**
             * Sets or clears the success message to be displayed when this {@link Action}
             * completes.
             */
            @NonNull
            public Builder setSuccessMessage(@Nullable CharSequence successMessage) {
                mSuccessMessage = successMessage;
                return this;
            }

            /** Creates the {@link Action} defined by this {@link Builder}. */
            @NonNull
            public Action build() {
                return new Action(mLabel, mPendingIntent, mResolving, mSuccessMessage);
            }
        }
    }
}
