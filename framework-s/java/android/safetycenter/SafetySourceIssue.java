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

import static com.android.internal.util.Preconditions.checkArgument;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Data for a safety source issue in the Safety Center page.
 *
 * <p>An issue represents an actionable matter relating to a particular safety source.
 *
 * <p>The safety issue will contain localized messages to be shown in UI explaining the potential
 * threat or warning and suggested fixes, as well as actions a user is allowed to take from the UI
 * to resolve the issue.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetySourceIssue implements Parcelable {

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
     * Indicates a critical or urgent safety issue that should be addressed by the user. This
     * severity will be reflected in the UI through a red icon.
     */
    public static final int SEVERITY_LEVEL_CRITICAL_WARNING = 400;

    @NonNull
    public static final Parcelable.Creator<SafetySourceIssue> CREATOR =
            new Parcelable.Creator<SafetySourceIssue>() {
                @Override
                public SafetySourceIssue createFromParcel(Parcel in) {
                    CharSequence title =
                            requireNonNull(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                    CharSequence summary =
                            requireNonNull(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                    int severityLevel = in.readInt();
                    List<Action> actions = new ArrayList<>();
                    in.readParcelableList(actions, Action.class.getClassLoader());
                    return new SafetySourceIssue(title, summary, severityLevel, actions);
                }

                @Override
                public SafetySourceIssue[] newArray(int size) {
                    return new SafetySourceIssue[size];
                }
            };

    @NonNull
    private final CharSequence mTitle;
    @NonNull
    private final CharSequence mSummary;
    @SeverityLevel
    private final int mSeverityLevel;
    @NonNull
    private final List<Action> mActions;

    private SafetySourceIssue(@NonNull CharSequence title, @NonNull CharSequence summary,
            @SeverityLevel int severityLevel, @NonNull List<Action> actions) {
        this.mTitle = title;
        this.mSummary = summary;
        this.mSeverityLevel = severityLevel;
        this.mActions = actions;
    }

    /** Returns the localized title of the issue to be displayed in the UI. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the localized summary of the issue to be displayed in the UI. */
    @NonNull
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the {@link SeverityLevel} of the issue. */
    @SeverityLevel
    public int getSeverityLevel() {
        return mSeverityLevel;
    }

    /**
     * Returns a list of {@link Action} instances representing actions supported in the UI for this
     * issue.
     *
     * <p>Each issue must contain at least one action, in order to help the user resolve the issue.
     *
     * <p>In Android {@link android.os.Build.VERSION_CODES#TIRAMISU}, each issue can contain at most
     * two actions supported from the UI.
     */
    @NonNull
    public List<Action> getActions() {
        return new ArrayList<>(mActions);
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
        dest.writeParcelableList(mActions, flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourceIssue)) return false;
        SafetySourceIssue that = (SafetySourceIssue) o;
        return mSeverityLevel == that.mSeverityLevel
                && TextUtils.equals(mTitle, that.mTitle)
                && TextUtils.equals(mSummary, that.mSummary)
                && mActions.equals(that.mActions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTitle, mSummary, mSeverityLevel, mActions);
    }

    @Override
    public String toString() {
        return "SafetySourceIssue{"
                + "mTitle="
                + mTitle
                + ", mSummary="
                + mSummary
                + ", mSeverityLevel="
                + mSeverityLevel
                + ", mActions="
                + mActions
                + '}';
    }

    /**
     * All possible severity levels for the safety source issue.
     *
     * <p>The severity level is meant to convey the severity of the individual issue.
     *
     * <p>The higher the severity level, the worse the safety level of the source and the higher
     * the threat to the user.
     *
     * <p>The numerical values of the levels are not used directly, rather they are used to build
     * a continuum of levels which support relative comparison.
     *
     * @hide
     */
    @IntDef(prefix = {"SEVERITY_LEVEL_"}, value = {
            SEVERITY_LEVEL_INFORMATION,
            SEVERITY_LEVEL_RECOMMENDATION,
            SEVERITY_LEVEL_CRITICAL_WARNING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SeverityLevel {
    }

    /**
     * Data for an action supported from a safety issue {@link SafetySourceIssue} in the Safety
     * Center page.
     *
     * <p>The purpose of the action is to allow the user to address the safety issue, either by
     * performing a fix suggested in the issue, or by navigating the user to the source of the issue
     * where they can be exposed to details about the issue and further suggestions to resolve it.
     *
     * <p>The user will be allowed to invoke the action from the UI by clicking on a UI element and
     * consequently resolve the issue.
     *
     * @hide
     */
    @SystemApi
    public static final class Action implements Parcelable {

        @NonNull
        public static final Parcelable.Creator<Action> CREATOR =
                new Parcelable.Creator<Action>() {
                    @Override
                    public Action createFromParcel(Parcel in) {
                        CharSequence label =
                                requireNonNull(
                                        TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
                        PendingIntent pendingIntent =
                                requireNonNull(PendingIntent.readPendingIntentOrNullFromParcel(in));
                        boolean resolving = in.readBoolean();
                        return new Action(label, pendingIntent, resolving);
                    }

                    @Override
                    public Action[] newArray(int size) {
                        return new Action[size];
                    }
                };

        @NonNull
        private final CharSequence mLabel;
        @NonNull
        private final PendingIntent mPendingIntent;
        private final boolean mResolving;

        private Action(@NonNull CharSequence label, @NonNull PendingIntent pendingIntent,
                boolean resolving) {
            this.mLabel = label;
            this.mPendingIntent = pendingIntent;
            this.mResolving = resolving;
        }

        /**
         * Returns the localized label of the action to be displayed in the UI.
         *
         * <p>The label should indicate what action will be performed if when invoked.
         */
        @NonNull
        public CharSequence getLabel() {
            return mLabel;
        }

        /**
         * Returns a {@link PendingIntent} to be fired when the action is clicked on.
         *
         * <p>The {@link PendingIntent} should perform the action referred to by
         * {@link #getLabel()}.
         */
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

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            TextUtils.writeToParcel(mLabel, dest, flags);
            mPendingIntent.writeToParcel(dest, flags);
            dest.writeBoolean(mResolving);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Action)) return false;
            Action that = (Action) o;
            return mResolving == that.mResolving
                    && TextUtils.equals(mLabel, that.mLabel)
                    && mPendingIntent.equals(that.mPendingIntent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mLabel, mPendingIntent, mResolving);
        }

        @Override
        public String toString() {
            return "Action{"
                    + "mLabel="
                    + mLabel
                    + ", mPendingIntent="
                    + mPendingIntent
                    + ", mResolving="
                    + mResolving
                    + '}';
        }

        /** Builder class for {@link Action}. */
        public static final class Builder {
            @NonNull
            private final CharSequence mLabel;
            @NonNull
            private final PendingIntent mPendingIntent;
            private boolean mResolving = false;

            /** Creates a {@link Builder} for an {@link Action}. */
            public Builder(@NonNull CharSequence label, @NonNull PendingIntent pendingIntent) {
                this.mLabel = requireNonNull(label);
                this.mPendingIntent = requireNonNull(pendingIntent);
            }

            /**
             * Sets whether the action will resolve the safety issue.
             *
             * @see #isResolving()
             */
            @NonNull
            public Builder setResolving(boolean resolving) {
                this.mResolving = resolving;
                return this;
            }

            /** Creates the {@link Action} defined by this {@link Builder}. */
            @NonNull
            public Action build() {
                return new Action(mLabel, mPendingIntent, mResolving);
            }
        }
    }

    /** Builder class for {@link SafetySourceIssue}. */
    public static final class Builder {
        @NonNull
        private final CharSequence mTitle;
        @NonNull
        private final CharSequence mSummary;
        @SeverityLevel
        private final int mSeverityLevel;

        @NonNull
        private final List<Action> mActions = new ArrayList<>();

        /** Creates a {@link Builder} for a {@link SafetySourceIssue}. */
        public Builder(@NonNull CharSequence title, @NonNull CharSequence summary,
                @SeverityLevel int severityLevel) {
            this.mTitle = requireNonNull(title);
            this.mSummary = requireNonNull(summary);
            this.mSeverityLevel = severityLevel;
        }

        /** Adds data for an action to be shown in UI. */
        @NonNull
        public Builder addAction(@NonNull Action actionData) {
            mActions.add(requireNonNull(actionData));
            return this;
        }

        /** Clears data for all the actions that were added to this {@link Builder}. */
        @NonNull
        public Builder clearActions() {
            mActions.clear();
            return this;
        }

        /** Creates the {@link SafetySourceIssue} defined by this {@link Builder}. */
        @NonNull
        public SafetySourceIssue build() {
            // TODO(b/207402324): Check with UX whether issues without any actions are permitted.
            checkArgument(!mActions.isEmpty(),
                    "Safety source issue must contain at least 1 action");
            checkArgument(mActions.size() <= 2,
                    "Safety source issue must not contain more than 2 actions");
            return new SafetySourceIssue(mTitle, mSummary, mSeverityLevel, mActions);
        }
    }
}
