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

import static com.android.safetycenter.config.SafetySource.SAFETY_SOURCE_TYPE_STATIC;

import static java.util.Objects.requireNonNull;

import android.annotation.IdRes;
import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Data class used to represent a group of static safety sources */
public final class StaticSafetySourcesGroup {
    @NonNull
    private final String mId;
    @IdRes
    private final int mTitleResId;
    @NonNull
    private final List<SafetySource> mStaticSafetySources;

    private StaticSafetySourcesGroup(@NonNull String id, @IdRes int titleResId,
            @NonNull List<SafetySource> staticSafetySources) {
        mId = id;
        mTitleResId = titleResId;
        mStaticSafetySources = staticSafetySources;
    }

    /** Returns the id of this static safety sources group. */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns the resource id of the title of this static safety sources group. */
    @IdRes
    public int getTitleResId() {
        return mTitleResId;
    }

    /** Returns the list of static safety sources in this static safety sources group. */
    @NonNull
    public List<SafetySource> getStaticSafetySources() {
        return mStaticSafetySources;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StaticSafetySourcesGroup)) return false;
        StaticSafetySourcesGroup that = (StaticSafetySourcesGroup) o;
        return Objects.equals(mId, that.mId)
                && mTitleResId == that.mTitleResId
                && Objects.equals(mStaticSafetySources, that.mStaticSafetySources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitleResId, mStaticSafetySources);
    }

    @Override
    public String toString() {
        return "SafetyCenterConfig{"
                + "mId='" + mId + '\''
                + ", mTitleResId=" + mTitleResId
                + ", mStaticSafetySources=" + mStaticSafetySources
                + '}';
    }

    /** Builder class for {@link StaticSafetySourcesGroup}. */
    public static final class Builder {
        @Nullable
        private String mId;
        @Nullable
        @IdRes
        private Integer mTitleResId;
        @NonNull
        private final List<SafetySource> mStaticSafetySources = new ArrayList<>();

        /** Creates a {@link Builder} for a {@link StaticSafetySourcesGroup}. */
        public Builder() {}

        /** Sets the id of this static safety sources group. */
        @NonNull
        public Builder setId(@Nullable String id) {
            mId = id;
            return this;
        }

        /** Sets the resource id of the title of this static safety sources group. */
        @NonNull
        public Builder setTitleResId(@IdRes int titleResId) {
            mTitleResId = titleResId;
            return this;
        }

        /** Adds a safety source to this static safety sources group. */
        @NonNull
        public Builder addStaticSafetySource(@NonNull SafetySource staticSafetySource) {
            mStaticSafetySources.add(requireNonNull(staticSafetySource));
            return this;
        }

        /** Creates the {@link StaticSafetySourcesGroup} defined by this {@link Builder}. */
        @NonNull
        public StaticSafetySourcesGroup build() {
            BuilderUtils.validateAttribute(mId, "id", true, false);
            int titleResId = BuilderUtils.validateResId(mTitleResId, "title", true, false);
            if (mStaticSafetySources.isEmpty()) {
                throw new IllegalStateException("Static safety sources group empty");
            }
            int staticSafetySourcesSize = mStaticSafetySources.size();
            for (int i = 0; i < staticSafetySourcesSize; i++) {
                SafetySource staticSafetySource = mStaticSafetySources.get(i);
                if (staticSafetySource.getType() != SAFETY_SOURCE_TYPE_STATIC) {
                    throw new IllegalStateException(
                            String.format("Invalid safety source type %d in safety sources group",
                                    staticSafetySource.getType()));
                }
            }
            return new StaticSafetySourcesGroup(mId, titleResId,
                    Collections.unmodifiableList(mStaticSafetySources));
        }
    }

}
