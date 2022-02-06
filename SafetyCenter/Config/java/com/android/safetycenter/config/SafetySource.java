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

package com.android.safetycenter.config;

import android.annotation.IdRes;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/** Data class used to represent a generic safety source */
public final class SafetySource {
    /** Invalid safety source. We never expect this value to be set. */
    private static final int SAFETY_SOURCE_TYPE_INVALID = 0;

    /** Static safety source. */
    public static final int SAFETY_SOURCE_TYPE_STATIC = 1;

    /** Dynamic safety source. */
    public static final int SAFETY_SOURCE_TYPE_DYNAMIC = 2;

    /** Internal safety source. */
    public static final int SAFETY_SOURCE_TYPE_INTERNAL = 3;

    /**
     * All possible safety source types.
     *
     * @hide
     */
    @IntDef(prefix = {"SAFETY_SOURCE_TYPE_"}, value = {
            SAFETY_SOURCE_TYPE_STATIC,
            SAFETY_SOURCE_TYPE_DYNAMIC,
            SAFETY_SOURCE_TYPE_INTERNAL
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

    @SafetySourceType
    private final int mType;
    @NonNull
    private final String mId;
    @Nullable
    private final String mPackageName;
    @IdRes
    private final int mTitleResId;
    @IdRes
    private final int mSummaryResId;
    @Nullable
    private final String mIntentAction;
    @Profile
    private final int mProfile;
    @IdRes
    private final int mSearchTermsResId;
    @Nullable
    private final String mBroadcastReceiverClassName;
    private final boolean mDisallowLogging;
    private final boolean mAllowRefreshOnPageOpen;

    /** Returns the id of this safety source. */
    private SafetySource(
            @SafetySourceType int type,
            @NonNull String id,
            @Nullable String packageName,
            @IdRes int titleResId,
            @IdRes int summaryResId,
            @Nullable String intentAction,
            @Profile int profile,
            @IdRes int searchTermsResId,
            @Nullable String broadcastReceiverClassName,
            boolean disallowLogging,
            boolean allowRefreshOnPageOpen) {
        mType = type;
        mId = id;
        mPackageName = packageName;
        mTitleResId = titleResId;
        mSummaryResId = summaryResId;
        mIntentAction = intentAction;
        mProfile = profile;
        mSearchTermsResId = searchTermsResId;
        mBroadcastReceiverClassName = broadcastReceiverClassName;
        mDisallowLogging = disallowLogging;
        mAllowRefreshOnPageOpen = allowRefreshOnPageOpen;
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
        if (mType == SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new UnsupportedOperationException(
                    "getPackageName unsupported for internal safety source");
        }
        return mPackageName;
    }

    /** Returns the resource id of the title of this safety source. */
    @IdRes
    public int getTitleResId() {
        if (mType == SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new UnsupportedOperationException(
                    "getTitleResId unsupported for internal safety source");
        }
        return mTitleResId;
    }

    /** Returns the resource id of the summary of this safety source. */
    @IdRes
    public int getSummaryResId() {
        if (mType == SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new UnsupportedOperationException(
                    "getSummaryResId unsupported for internal safety source");
        }
        return mSummaryResId;
    }

    /** Returns the intent action of this safety source. */
    @NonNull
    public String getIntentAction() {
        if (mType == SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new UnsupportedOperationException(
                    "getIntentAction unsupported for internal safety source");
        }
        return mIntentAction;
    }

    /** Returns the profile property of this safety source. */
    @Profile
    public int getProfile() {
        if (mType == SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new UnsupportedOperationException(
                    "getProfile unsupported for internal safety source");
        }
        return mProfile;
    }

    /**
     * Returns the resource id of the search terms of this safety source if set; otherwise
     * {@link Resources#ID_NULL}.
     */
    @IdRes
    public int getSearchTermsResId() {
        if (mType == SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new UnsupportedOperationException(
                    "getSearchTermsResId unsupported for internal safety source");
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
        if (mType == SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new UnsupportedOperationException(
                    "getBroadcastReceiverClassName unsupported for internal safety source");
        }
        return mBroadcastReceiverClassName;
    }

    /** Returns the disallow logging property of this safety source. */
    public boolean isDisallowLogging() {
        if (mType == SAFETY_SOURCE_TYPE_STATIC) {
            throw new UnsupportedOperationException(
                    "isDisallowLogging unsupported for static safety source");
        }
        if (mType == SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new UnsupportedOperationException(
                    "isDisallowLogging unsupported for internal safety source");
        }
        return mDisallowLogging;
    }

    /** Returns the allow refresh on page open property of this safety source. */
    public boolean isAllowRefreshOnPageOpen() {
        if (mType == SAFETY_SOURCE_TYPE_STATIC) {
            throw new UnsupportedOperationException(
                    "isAllowRefreshOnPageOpen unsupported for static safety source");
        }
        if (mType == SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new UnsupportedOperationException(
                    "isAllowRefreshOnPageOpen unsupported for internal safety source");
        }
        return mAllowRefreshOnPageOpen;
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
                && mSummaryResId == that.mSummaryResId
                && Objects.equals(mIntentAction, that.mIntentAction)
                && mProfile == that.mProfile
                && mSearchTermsResId == that.mSearchTermsResId
                && Objects.equals(mBroadcastReceiverClassName, that.mBroadcastReceiverClassName)
                && mDisallowLogging == that.mDisallowLogging
                && mAllowRefreshOnPageOpen == that.mAllowRefreshOnPageOpen;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mId, mPackageName, mTitleResId, mSummaryResId, mIntentAction,
                mProfile, mSearchTermsResId, mBroadcastReceiverClassName, mDisallowLogging,
                mAllowRefreshOnPageOpen);
    }

    @Override
    public String toString() {
        return "SafetySource{"
                + "mType=" + mType
                + ", mId='" + mId + '\''
                + ", mPackageName='" + mPackageName + '\''
                + ", mTitleResId=" + mTitleResId
                + ", mSummaryResId=" + mSummaryResId
                + ", mIntentAction='" + mIntentAction + '\''
                + ", mProfile=" + mProfile
                + ", mSearchTermsResId=" + mSearchTermsResId
                + ", mBroadcastReceiverClassName='" + mBroadcastReceiverClassName + '\''
                + ", mDisallowLogging=" + mDisallowLogging
                + ", mAllowRefreshOnPageOpen=" + mAllowRefreshOnPageOpen
                + '}';
    }

    /** Builder class for {@link SafetySource}. */
    public static final class Builder {
        @Nullable
        @SafetySourceType
        private Integer mType;
        @Nullable
        private String mId;
        @Nullable
        private String mPackageName;
        @Nullable
        @IdRes
        private Integer mTitleResId;
        @Nullable
        @IdRes
        private Integer mSummaryResId;
        @Nullable
        private String mIntentAction;
        @Nullable
        @Profile
        private Integer mProfile;
        @Nullable
        @IdRes
        private Integer mSearchTermsResId;
        @Nullable
        private String mBroadcastReceiverClassName;
        @Nullable
        private Boolean mDisallowLogging;
        @Nullable
        private Boolean mAllowRefreshOnPageOpen;

        /** Creates a {@link Builder} for a {@link SafetySource}. */
        public Builder() {
        }

        /** Sets the type of this safety source. */
        @NonNull
        public Builder setType(@SafetySourceType int type) {
            mType = type;
            return this;
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
        public Builder setTitleResId(@IdRes int titleResId) {
            mTitleResId = titleResId;
            return this;
        }

        /** Sets the resource id of the summary of this safety source. */
        @NonNull
        public Builder setSummaryResId(@IdRes int summaryResId) {
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

        /** Sets the resource id of the search terms of this safety source. */
        @NonNull
        public Builder setSearchTermsResId(@IdRes int searchTermsResId) {
            mSearchTermsResId = searchTermsResId;
            return this;
        }

        /** Sets the broadcast receiver class name of this safety source. */
        @NonNull
        public Builder setBroadcastReceiverClassName(@Nullable String broadcastReceiverClassName) {
            mBroadcastReceiverClassName = broadcastReceiverClassName;
            return this;
        }

        /** Sets the disallow logging property of this safety source. */
        @NonNull
        public Builder setDisallowLogging(boolean disallowLogging) {
            mDisallowLogging = disallowLogging;
            return this;
        }

        /** Sets the allow refresh on page open property of this safety source. */
        @NonNull
        public Builder setAllowRefreshOnPageOpen(boolean allowRefreshOnPageOpen) {
            mAllowRefreshOnPageOpen = allowRefreshOnPageOpen;
            return this;
        }

        /** Creates the {@link SafetySource} defined by this {@link Builder}. */
        @NonNull
        public SafetySource build() {
            int type = BuilderUtils.validateIntDef(mType, "type", true, false,
                    SAFETY_SOURCE_TYPE_INVALID, SAFETY_SOURCE_TYPE_STATIC,
                    SAFETY_SOURCE_TYPE_DYNAMIC, SAFETY_SOURCE_TYPE_INTERNAL);
            boolean isStatic = type == SAFETY_SOURCE_TYPE_STATIC;
            boolean isDynamic = type == SAFETY_SOURCE_TYPE_DYNAMIC;
            boolean isInternal = type == SAFETY_SOURCE_TYPE_INTERNAL;
            BuilderUtils.validateAttribute(mId, "id", true, false);
            BuilderUtils.validateAttribute(mPackageName, "packageName", isDynamic,
                    isStatic || isInternal);
            int titleResId = BuilderUtils.validateResId(mTitleResId, "title", isDynamic || isStatic,
                    isInternal);
            int summaryResId = BuilderUtils.validateResId(mSummaryResId, "summary",
                    isDynamic || isStatic, isInternal);
            BuilderUtils.validateAttribute(mIntentAction, "intentAction", isDynamic || isStatic,
                    isInternal);
            int profile = BuilderUtils.validateIntDef(mProfile, "profile", isDynamic || isStatic,
                    isInternal, PROFILE_NONE, PROFILE_PRIMARY, PROFILE_ALL);
            int searchTermsResId = BuilderUtils.validateResId(mSearchTermsResId, "searchTerms",
                    false, isInternal);
            BuilderUtils.validateAttribute(mBroadcastReceiverClassName,
                    "broadcastReceiverClassName", false, isStatic || isInternal);
            boolean disallowLogging = BuilderUtils.validateBoolean(mDisallowLogging,
                    "disallowLogging", false, isStatic || isInternal, false);
            boolean allowRefreshOnPageOpen = BuilderUtils.validateBoolean(mAllowRefreshOnPageOpen,
                    "allowRefreshOnPageOpen", false, isStatic || isInternal, false);
            return new SafetySource(type, mId, mPackageName, titleResId, summaryResId,
                    mIntentAction, profile, searchTermsResId, mBroadcastReceiverClassName,
                    disallowLogging, allowRefreshOnPageOpen);
        }
    }

}
