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

package android.safetycenter.config;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.annotation.SystemApi;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Data class used to represent the initial configuration of a safety source
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetySource implements Parcelable {
    /** Static safety source. */
    public static final int SAFETY_SOURCE_TYPE_STATIC = 1;

    /** Dynamic safety source. */
    public static final int SAFETY_SOURCE_TYPE_DYNAMIC = 2;

    /** Issue only safety source. */
    public static final int SAFETY_SOURCE_TYPE_ISSUE_ONLY = 3;

    /**
     * All possible safety source types.
     *
     * @hide
     */
    @IntDef(prefix = {"SAFETY_SOURCE_TYPE_"}, value = {
            SAFETY_SOURCE_TYPE_STATIC,
            SAFETY_SOURCE_TYPE_DYNAMIC,
            SAFETY_SOURCE_TYPE_ISSUE_ONLY
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SafetySourceType {
    }

    /** Profile property unspecified. */
    public static final int PROFILE_NONE = 0;

    /**
     * Even when the active user has managed profiles, the safety source will be displayed as a
     * single entry for the primary profile.
     */
    public static final int PROFILE_PRIMARY = 1;

    /**
     * When the user has managed profiles, the safety source will be displayed as multiple entries
     * one for each profile.
     */
    public static final int PROFILE_ALL = 2;

    /**
     * All possible profile configurations for a safety source.
     *
     * @hide
     */
    @IntDef(prefix = {"PROFILE_"}, value = {
            PROFILE_NONE,
            PROFILE_PRIMARY,
            PROFILE_ALL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Profile {
    }

    /** This dynamic source will create an enabled entry in the UI until an update is received. */
    public static final int INITIAL_DISPLAY_STATE_ENABLED = 0;

    /** This dynamic source will create a disabled entry in the UI until an update is received. */
    public static final int INITIAL_DISPLAY_STATE_DISABLED = 1;

    /** This dynamic source will have no entry in the UI until an update is received. */
    public static final int INITIAL_DISPLAY_STATE_HIDDEN = 2;

    /**
     * All possible initial display states for a dynamic safety source.
     *
     * @hide
     */
    @IntDef(prefix = {"INITIAL_DISPLAY_STATE_"}, value = {
            INITIAL_DISPLAY_STATE_ENABLED,
            INITIAL_DISPLAY_STATE_DISABLED,
            INITIAL_DISPLAY_STATE_HIDDEN
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InitialDisplayState {
    }

    @SafetySourceType
    private final int mType;
    @NonNull
    private final String mId;
    @Nullable
    private final String mPackageName;
    @StringRes
    private final int mTitleResId;
    @StringRes
    private final int mTitleForWorkResId;
    @StringRes
    private final int mSummaryResId;
    @Nullable
    private final String mIntentAction;
    @Profile
    private final int mProfile;
    @InitialDisplayState
    private final int mInitialDisplayState;
    private final int mMaxSeverityLevel;
    @StringRes
    private final int mSearchTermsResId;
    @Nullable
    private final String mBroadcastReceiverClassName;
    private final boolean mLoggingAllowed;
    private final boolean mRefreshOnPageOpenAllowed;

    /** Returns the id of this safety source. */
    private SafetySource(
            @SafetySourceType int type,
            @NonNull String id,
            @Nullable String packageName,
            @StringRes int titleResId,
            @StringRes int titleForWorkResId,
            @StringRes int summaryResId,
            @Nullable String intentAction,
            @Profile int profile,
            @InitialDisplayState int initialDisplayState,
            int maxSeverityLevel,
            @StringRes int searchTermsResId,
            @Nullable String broadcastReceiverClassName,
            boolean loggingAllowed,
            boolean refreshOnPageOpenAllowed) {
        mType = type;
        mId = id;
        mPackageName = packageName;
        mTitleResId = titleResId;
        mTitleForWorkResId = titleForWorkResId;
        mSummaryResId = summaryResId;
        mIntentAction = intentAction;
        mProfile = profile;
        mInitialDisplayState = initialDisplayState;
        mMaxSeverityLevel = maxSeverityLevel;
        mSearchTermsResId = searchTermsResId;
        mBroadcastReceiverClassName = broadcastReceiverClassName;
        mLoggingAllowed = loggingAllowed;
        mRefreshOnPageOpenAllowed = refreshOnPageOpenAllowed;
    }

    /** Returns the type of this safety source. */
    @SafetySourceType
    public int getType() {
        return mType;
    }

    /** Returns the id of this safety source. */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns the package name of this safety source. */
    @NonNull
    public String getPackageName() {
        if (mType == SAFETY_SOURCE_TYPE_STATIC) {
            throw new UnsupportedOperationException(
                    "getPackageName unsupported for static safety source");
        }
        return mPackageName;
    }

    /** Returns the resource id of the title of this safety source. */
    @StringRes
    public int getTitleResId() {
        if (mType == SAFETY_SOURCE_TYPE_ISSUE_ONLY) {
            throw new UnsupportedOperationException(
                    "getTitleResId unsupported for issue only safety source");
        }
        return mTitleResId;
    }

    /** Returns the resource id of the title for work of this safety source. */
    @StringRes
    public int getTitleForWorkResId() {
        if (mType == SAFETY_SOURCE_TYPE_ISSUE_ONLY) {
            throw new UnsupportedOperationException(
                    "getTitleForWorkResId unsupported for issue only safety source");
        }
        if (mProfile == PROFILE_PRIMARY) {
            throw new UnsupportedOperationException(
                    "getTitleForWorkResId unsupported for primary profile safety source");
        }
        return mTitleForWorkResId;
    }

    /** Returns the resource id of the summary of this safety source. */
    @StringRes
    public int getSummaryResId() {
        if (mType == SAFETY_SOURCE_TYPE_ISSUE_ONLY) {
            throw new UnsupportedOperationException(
                    "getSummaryResId unsupported for issue only safety source");
        }
        return mSummaryResId;
    }

    /** Returns the intent action of this safety source. */
    @Nullable
    public String getIntentAction() {
        if (mType == SAFETY_SOURCE_TYPE_ISSUE_ONLY) {
            throw new UnsupportedOperationException(
                    "getIntentAction unsupported for issue only safety source");
        }
        return mIntentAction;
    }

    /** Returns the profile property of this safety source. */
    @Profile
    public int getProfile() {
        return mProfile;
    }

    /** Returns the initial display state of this safety source. */
    @InitialDisplayState
    public int getInitialDisplayState() {
        if (mType == SAFETY_SOURCE_TYPE_STATIC) {
            throw new UnsupportedOperationException(
                    "getInitialDisplayState unsupported for static safety source");
        }
        if (mType == SAFETY_SOURCE_TYPE_ISSUE_ONLY) {
            throw new UnsupportedOperationException(
                    "getInitialDisplayState unsupported for issue only safety source");
        }
        return mInitialDisplayState;
    }

    /** Returns the maximum severity level of this safety source. */
    public int getMaxSeverityLevel() {
        if (mType == SAFETY_SOURCE_TYPE_STATIC) {
            throw new UnsupportedOperationException(
                    "getMaxSeverityLevel unsupported for static safety source");
        }
        return mMaxSeverityLevel;
    }

    /**
     * Returns the resource id of the search terms of this safety source if set; otherwise
     * {@link Resources#ID_NULL}.
     */
    @StringRes
    public int getSearchTermsResId() {
        if (mType == SAFETY_SOURCE_TYPE_ISSUE_ONLY) {
            throw new UnsupportedOperationException(
                    "getSearchTermsResId unsupported for issue only safety source");
        }
        return mSearchTermsResId;
    }

    /** Returns the broadcast receiver class name of this safety source. */
    @Nullable
    public String getBroadcastReceiverClassName() {
        if (mType == SAFETY_SOURCE_TYPE_STATIC) {
            throw new UnsupportedOperationException(
                    "getBroadcastReceiverClassName unsupported for static safety source");
        }
        return mBroadcastReceiverClassName;
    }

    /** Returns the logging allowed property of this safety source. */
    public boolean isLoggingAllowed() {
        if (mType == SAFETY_SOURCE_TYPE_STATIC) {
            throw new UnsupportedOperationException(
                    "isLoggingAllowed unsupported for static safety source");
        }
        return mLoggingAllowed;
    }

    /** Returns the refresh on page open allowed property of this safety source. */
    public boolean isRefreshOnPageOpenAllowed() {
        if (mType == SAFETY_SOURCE_TYPE_STATIC) {
            throw new UnsupportedOperationException(
                    "isRefreshOnPageOpenAllowed unsupported for static safety source");
        }
        return mRefreshOnPageOpenAllowed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySource)) return false;
        SafetySource that = (SafetySource) o;
        return mType == that.mType
                && Objects.equals(mId, that.mId)
                && Objects.equals(mPackageName, that.mPackageName)
                && mTitleResId == that.mTitleResId
                && mTitleForWorkResId == that.mTitleForWorkResId
                && mSummaryResId == that.mSummaryResId
                && Objects.equals(mIntentAction, that.mIntentAction)
                && mProfile == that.mProfile
                && mInitialDisplayState == that.mInitialDisplayState
                && mMaxSeverityLevel == that.mMaxSeverityLevel
                && mSearchTermsResId == that.mSearchTermsResId
                && Objects.equals(mBroadcastReceiverClassName, that.mBroadcastReceiverClassName)
                && mLoggingAllowed == that.mLoggingAllowed
                && mRefreshOnPageOpenAllowed == that.mRefreshOnPageOpenAllowed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mId, mPackageName, mTitleResId, mTitleForWorkResId,
                mSummaryResId, mIntentAction, mProfile, mInitialDisplayState, mMaxSeverityLevel,
                mSearchTermsResId, mBroadcastReceiverClassName, mLoggingAllowed,
                mRefreshOnPageOpenAllowed);
    }

    @Override
    public String toString() {
        return "SafetySource{"
                + "mType=" + mType
                + ", mId='" + mId + '\''
                + ", mPackageName='" + mPackageName + '\''
                + ", mTitleResId=" + mTitleResId
                + ", mTitleForWorkResId=" + mTitleForWorkResId
                + ", mSummaryResId=" + mSummaryResId
                + ", mIntentAction='" + mIntentAction + '\''
                + ", mProfile=" + mProfile
                + ", mInitialDisplayState=" + mInitialDisplayState
                + ", mMaxSeverityLevel=" + mMaxSeverityLevel
                + ", mSearchTermsResId=" + mSearchTermsResId
                + ", mBroadcastReceiverClassName='" + mBroadcastReceiverClassName + '\''
                + ", mLoggingAllowed=" + mLoggingAllowed
                + ", mRefreshOnPageOpenAllowed=" + mRefreshOnPageOpenAllowed
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeString(mId);
        dest.writeString(mPackageName);
        dest.writeInt(mTitleResId);
        dest.writeInt(mTitleForWorkResId);
        dest.writeInt(mSummaryResId);
        dest.writeString(mIntentAction);
        dest.writeInt(mProfile);
        dest.writeInt(mInitialDisplayState);
        dest.writeInt(mMaxSeverityLevel);
        dest.writeInt(mSearchTermsResId);
        dest.writeString(mBroadcastReceiverClassName);
        dest.writeBoolean(mLoggingAllowed);
        dest.writeBoolean(mRefreshOnPageOpenAllowed);
    }

    @NonNull
    public static final Parcelable.Creator<SafetySource> CREATOR =
            new Parcelable.Creator<SafetySource>() {
                @Override
                public SafetySource createFromParcel(Parcel in) {
                    int type = in.readInt();
                    String id = in.readString();
                    String packageName = in.readString();
                    int titleResId = in.readInt();
                    int titleForWorkResId = in.readInt();
                    int summaryResId = in.readInt();
                    String intentAction = in.readString();
                    int profile = in.readInt();
                    int initialDisplayState = in.readInt();
                    int maxSeverityLevel = in.readInt();
                    int searchTermsResId = in.readInt();
                    String broadcastReceiverClassName = in.readString();
                    boolean loggingAllowed = in.readBoolean();
                    boolean refreshOnPageOpenAllowed = in.readBoolean();
                    return new SafetySource(type, id, packageName, titleResId, titleForWorkResId,
                            summaryResId, intentAction, profile, initialDisplayState,
                            maxSeverityLevel, searchTermsResId, broadcastReceiverClassName,
                            loggingAllowed, refreshOnPageOpenAllowed);
                }

                @Override
                public SafetySource[] newArray(int size) {
                    return new SafetySource[size];
                }
            };

    /** Builder class for {@link SafetySource}. */
    public static final class Builder {
        @SafetySourceType
        private final int mType;
        @Nullable
        private String mId;
        @Nullable
        private String mPackageName;
        @Nullable
        @StringRes
        private Integer mTitleResId;
        @Nullable
        @StringRes
        private Integer mTitleForWorkResId;
        @Nullable
        @StringRes
        private Integer mSummaryResId;
        @Nullable
        private String mIntentAction;
        @Nullable
        @Profile
        private Integer mProfile;
        @Nullable
        @InitialDisplayState
        private Integer mInitialDisplayState;
        @Nullable
        private Integer mMaxSeverityLevel;
        @Nullable
        @StringRes
        private Integer mSearchTermsResId;
        @Nullable
        private String mBroadcastReceiverClassName;
        @Nullable
        private Boolean mLoggingAllowed;
        @Nullable
        private Boolean mRefreshOnPageOpenAllowed;

        /** Creates a {@link Builder} for a {@link SafetySource}. */
        public Builder(@SafetySourceType int type) {
            mType = type;
        }

        /** Sets the id of this safety source. */
        @NonNull
        public Builder setId(@Nullable String id) {
            mId = id;
            return this;
        }

        /** Sets the package name of this safety source. */
        @NonNull
        public Builder setPackageName(@Nullable String packageName) {
            mPackageName = packageName;
            return this;
        }

        /** Sets the resource id of the title of this safety source. */
        @NonNull
        public Builder setTitleResId(@StringRes int titleResId) {
            mTitleResId = titleResId;
            return this;
        }

        /** Sets the resource id of the title for work of this safety source. */
        @NonNull
        public Builder setTitleForWorkResId(@StringRes int titleForWorkResId) {
            mTitleForWorkResId = titleForWorkResId;
            return this;
        }

        /** Sets the resource id of the summary of this safety source. */
        @NonNull
        public Builder setSummaryResId(@StringRes int summaryResId) {
            mSummaryResId = summaryResId;
            return this;
        }

        /** Sets the intent action of this safety source. */
        @NonNull
        public Builder setIntentAction(@Nullable String intentAction) {
            mIntentAction = intentAction;
            return this;
        }

        /** Sets the profile property of this safety source. */
        @NonNull
        public Builder setProfile(@Profile int profile) {
            mProfile = profile;
            return this;
        }

        /** Sets the initial display state of this safety source. */
        @NonNull
        public Builder setInitialDisplayState(@InitialDisplayState int initialDisplayState) {
            mInitialDisplayState = initialDisplayState;
            return this;
        }

        /** Sets the maximum severity level of this safety source. */
        @NonNull
        public Builder setMaxSeverityLevel(int maxSeverityLevel) {
            mMaxSeverityLevel = maxSeverityLevel;
            return this;
        }

        /** Sets the resource id of the search terms of this safety source. */
        @NonNull
        public Builder setSearchTermsResId(@StringRes int searchTermsResId) {
            mSearchTermsResId = searchTermsResId;
            return this;
        }

        /** Sets the broadcast receiver class name of this safety source. */
        @NonNull
        public Builder setBroadcastReceiverClassName(@Nullable String broadcastReceiverClassName) {
            mBroadcastReceiverClassName = broadcastReceiverClassName;
            return this;
        }

        /** Sets the logging allowed property of this safety source. */
        @NonNull
        public Builder setLoggingAllowed(boolean loggingAllowed) {
            mLoggingAllowed = loggingAllowed;
            return this;
        }

        /** Sets the refresh on page open allowed property of this safety source. */
        @NonNull
        public Builder setRefreshOnPageOpenAllowed(boolean refreshOnPageOpenAllowed) {
            mRefreshOnPageOpenAllowed = refreshOnPageOpenAllowed;
            return this;
        }

        /** Creates the {@link SafetySource} defined by this {@link Builder}. */
        @NonNull
        public SafetySource build() {
            if (mType != SAFETY_SOURCE_TYPE_STATIC
                    && mType != SAFETY_SOURCE_TYPE_DYNAMIC
                    && mType != SAFETY_SOURCE_TYPE_ISSUE_ONLY) {
                throw new IllegalStateException("Unexpected type");
            }
            boolean isStatic = mType == SAFETY_SOURCE_TYPE_STATIC;
            boolean isDynamic = mType == SAFETY_SOURCE_TYPE_DYNAMIC;
            boolean isIssueOnly = mType == SAFETY_SOURCE_TYPE_ISSUE_ONLY;
            BuilderUtils.validateAttribute(mId, "id", true, false);
            BuilderUtils.validateAttribute(mPackageName, "packageName", isDynamic || isIssueOnly,
                    isStatic);
            int initialDisplayState = BuilderUtils.validateIntDef(mInitialDisplayState,
                    "initialDisplayState", false, isStatic || isIssueOnly,
                    INITIAL_DISPLAY_STATE_ENABLED, INITIAL_DISPLAY_STATE_ENABLED,
                    INITIAL_DISPLAY_STATE_DISABLED, INITIAL_DISPLAY_STATE_HIDDEN);
            boolean isEnabled = initialDisplayState == INITIAL_DISPLAY_STATE_ENABLED;
            boolean isHidden = initialDisplayState == INITIAL_DISPLAY_STATE_HIDDEN;
            int titleResId = BuilderUtils.validateResId(mTitleResId, "title",
                    (isDynamic && !isHidden) || isStatic, isIssueOnly || isHidden);
            int summaryResId = BuilderUtils.validateResId(mSummaryResId, "summary",
                    (isDynamic && !isHidden) || isStatic, isIssueOnly || isHidden);
            BuilderUtils.validateAttribute(mIntentAction, "intentAction",
                    (isDynamic && isEnabled) || isStatic, isIssueOnly || isHidden);
            int profile = BuilderUtils.validateIntDef(mProfile, "profile", true, false,
                    PROFILE_NONE, PROFILE_PRIMARY, PROFILE_ALL);
            int titleForWorkResId = BuilderUtils.validateResId(mTitleForWorkResId, "titleForWork",
                    ((isDynamic && !isHidden) || isStatic) && profile == PROFILE_ALL,
                    isIssueOnly || isHidden || profile == PROFILE_PRIMARY);
            int maxSeverityLevel = BuilderUtils.validateInteger(mMaxSeverityLevel,
                    "maxSeverityLevel", false, isStatic, Integer.MAX_VALUE);
            int searchTermsResId = BuilderUtils.validateResId(mSearchTermsResId, "searchTerms",
                    false, isIssueOnly);
            BuilderUtils.validateAttribute(mBroadcastReceiverClassName,
                    "broadcastReceiverClassName", false, isStatic);
            boolean loggingAllowed = BuilderUtils.validateBoolean(mLoggingAllowed, "loggingAllowed",
                    false, isStatic, true);
            boolean refreshOnPageOpenAllowed = BuilderUtils.validateBoolean(
                    mRefreshOnPageOpenAllowed, "refreshOnPageOpenAllowed", false, isStatic, false);
            return new SafetySource(mType, mId, mPackageName, titleResId, titleForWorkResId,
                    summaryResId, mIntentAction, profile, initialDisplayState, maxSeverityLevel,
                    searchTermsResId, mBroadcastReceiverClassName, loggingAllowed,
                    refreshOnPageOpenAllowed);
        }
    }

}
