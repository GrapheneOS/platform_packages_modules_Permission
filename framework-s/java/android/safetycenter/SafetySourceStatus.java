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
import android.annotation.Nullable;
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
     * Indicates that no status is currently associated with the information. This may be due to the
     * source not having sufficient information or opinion on the status level.
     *
     * <p>This status will be reflected in the UI through a grey icon.
     */
    public static final int STATUS_LEVEL_NONE = 100;

    /**
     * Indicates that no issues were detected.
     *
     * <p>This status will be reflected in the UI through a green icon.
     */
    public static final int STATUS_LEVEL_OK = 200;

    /**
     * Indicates the presence of a medium-level issue which the user is encouraged to act on.
     *
     * <p>This status will be reflected in the UI through a yellow icon.
     */
    public static final int STATUS_LEVEL_RECOMMENDATION = 300;

    /**
     * Indicates the presence of a critical or urgent safety issue that should be addressed by the
     * user.
     *
     * <p>This status will be reflected in the UI through a red icon.
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
                    IconAction iconAction = in.readTypedObject(IconAction.CREATOR);
                    boolean enabled = in.readBoolean();
                    return new SafetySourceStatus(title, summary, statusLevel, pendingIntent,
                            iconAction, enabled);
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
    @Nullable
    private final IconAction mIconAction;
    private final boolean mEnabled;

    private SafetySourceStatus(@NonNull CharSequence title, @NonNull CharSequence summary,
            @StatusLevel int statusLevel, @NonNull PendingIntent pendingIntent,
            @Nullable IconAction iconAction, boolean enabled) {
        this.mTitle = title;
        this.mSummary = summary;
        this.mStatusLevel = statusLevel;
        this.mPendingIntent = pendingIntent;
        this.mIconAction = iconAction;
        this.mEnabled = enabled;
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

    /**
     * Returns an optional icon action to be displayed in the safety source status UI.
     *
     * <p>The icon action will be a clickable icon which performs an action as indicated by the
     * icon.
     */
    @Nullable
    public IconAction getIconAction() {
        return mIconAction;
    }

    /**
     * Returns whether the safety source status is enabled.
     *
     * <p>A safety source status should be disabled if it is currently unavailable on the device.
     *
     * <p>If disabled, the status will show as grayed out in the UI, and interactions with it may
     * be limited.
     */
    public boolean isEnabled() {
        return mEnabled;
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
        dest.writeTypedObject(mIconAction, flags);
        dest.writeBoolean(mEnabled);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourceStatus)) return false;
        SafetySourceStatus that = (SafetySourceStatus) o;
        return mStatusLevel == that.mStatusLevel
                && mEnabled == that.mEnabled
                && TextUtils.equals(mTitle, that.mTitle)
                && TextUtils.equals(mSummary, that.mSummary)
                && mPendingIntent.equals(that.mPendingIntent)
                && Objects.equals(mIconAction, that.mIconAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTitle, mSummary, mStatusLevel, mPendingIntent, mIconAction,
                mEnabled);
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
                + ", mIconAction="
                + mIconAction
                + ", mEnabled="
                + mEnabled
                + '}';
    }

    /**
     * Data for an action supported from a safety source status {@link SafetySourceStatus} in the
     * Safety Center page.
     *
     * <p>The purpose of the action is to add a surface to allow the user to perform an action
     * relating to the safety source status.
     *
     * <p>The action will be shown as a clickable icon chosen from a predefined set of icons (see
     * {@link IconType}). The icon should indicate to the user what action will be performed on
     * clicking on it.
     *
     * @hide
     */
    @SystemApi
    public static final class IconAction implements Parcelable {

        @NonNull
        public static final Parcelable.Creator<IconAction> CREATOR =
                new Parcelable.Creator<IconAction>() {
                    @Override
                    public IconAction createFromParcel(Parcel in) {
                        int iconType = in.readInt();
                        PendingIntent pendingIntent =
                                requireNonNull(PendingIntent.readPendingIntentOrNullFromParcel(in));
                        return new IconAction(iconType, pendingIntent);
                    }

                    @Override
                    public IconAction[] newArray(int size) {
                        return new IconAction[size];
                    }
                };

        @IconType
        private final int mIconType;
        @NonNull
        private final PendingIntent mPendingIntent;

        public IconAction(@IconType int iconType, @NonNull PendingIntent pendingIntent) {
            this.mIconType = iconType;
            this.mPendingIntent = pendingIntent;
        }

        /**
         * Returns the type of icon to be displayed in the UI.
         *
         * <p>The icon type should indicate what action will be performed if when invoked.
         */
        @IconType
        public int getIconType() {
            return mIconType;
        }

        /** Returns a {@link PendingIntent} to be invoked when the icon action is clicked on. */
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
            dest.writeInt(mIconType);
            mPendingIntent.writeToParcel(dest, flags);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IconAction)) return false;
            IconAction that = (IconAction) o;
            return mIconType == that.mIconType && mPendingIntent.equals(that.mPendingIntent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIconType, mPendingIntent);
        }

        @Override
        public String toString() {
            return "IconAction{"
                    + "mIconType="
                    + mIconType
                    + ", mPendingIntent="
                    + mPendingIntent
                    + '}';
        }

        /** Indicates a gear (cog) icon. */
        public static final int ICON_TYPE_GEAR = 100;

        /** Indicates an information icon. */
        public static final int ICON_TYPE_INFO = 200;

        /**
         * All possible icons which can be displayed in an {@link IconAction}.
         *
         * @hide
         */
        @IntDef(prefix = {"ICON_TYPE_"}, value = {
                ICON_TYPE_GEAR,
                ICON_TYPE_INFO,

        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface IconType {
        }
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
    @IntDef(prefix = {"STATUS_LEVEL_"}, value = {
            STATUS_LEVEL_NONE,
            STATUS_LEVEL_OK,
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
        @Nullable
        private IconAction mIconAction;
        private boolean mEnabled = true;

        /** Creates a {@link Builder} for a {@link SafetySourceStatus}. */
        public Builder(@NonNull CharSequence title, @NonNull CharSequence summary,
                @StatusLevel int statusLevel, @NonNull PendingIntent pendingIntent) {
            this.mTitle = requireNonNull(title);
            this.mSummary = requireNonNull(summary);
            this.mStatusLevel = statusLevel;
            this.mPendingIntent = requireNonNull(pendingIntent);
        }

        /**
         * Sets an optional icon action for the safety source status.
         *
         * @see #getIconAction()
         */
        @NonNull
        public Builder setIconAction(@Nullable IconAction iconAction) {
            this.mIconAction = iconAction;
            return this;
        }

        /**
         * Sets whether the safety source status is enabled.
         *
         * <p>By default, the safety source status will be enabled.
         *
         * @see #isEnabled()
         */
        @NonNull
        public Builder setEnabled(boolean enabled) {
            this.mEnabled = enabled;
            return this;
        }

        /** Creates the {@link SafetySourceStatus} defined by this {@link Builder}. */
        @NonNull
        public SafetySourceStatus build() {
            return new SafetySourceStatus(mTitle, mSummary, mStatusLevel, mPendingIntent,
                    mIconAction, mEnabled);
        }
    }
}
