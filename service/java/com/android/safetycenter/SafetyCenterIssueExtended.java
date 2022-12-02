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

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.safetycenter.SafetyCenterIssue;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import javax.annotation.concurrent.NotThreadSafe;

/** Wrapper that contains a {@link SafetyCenterIssue} and some extra information about it. */
@RequiresApi(TIRAMISU)
final class SafetyCenterIssueExtended {
    @NonNull private final SafetyCenterIssue mSafetyCenterIssue;
    @NonNull private final SafetyCenterIssueKey mSafetyCenterIssueKey;
    @SafetySourceIssue.IssueCategory private final int mSafetySourceIssueCategory;
    @SafetySourceData.SeverityLevel private final int mSafetySourceIssueSeverityLevel;

    // Deduplication info, only available on Android U+.
    @Nullable private final String mDeduplicationGroup;
    @Nullable private final String mDeduplicationId;

    private SafetyCenterIssueExtended(
            @NonNull SafetyCenterIssue safetyCenterIssue,
            @NonNull SafetyCenterIssueKey safetyCenterIssueKey,
            @SafetySourceIssue.IssueCategory int safetySourceIssueCategory,
            int safetySourceIssueSeverityLevel,
            @Nullable String deduplicationGroup,
            @Nullable String deduplicationId) {
        this.mSafetyCenterIssue = safetyCenterIssue;
        this.mSafetyCenterIssueKey = safetyCenterIssueKey;
        this.mSafetySourceIssueCategory = safetySourceIssueCategory;
        this.mSafetySourceIssueSeverityLevel = safetySourceIssueSeverityLevel;
        this.mDeduplicationGroup = deduplicationGroup;
        this.mDeduplicationId = deduplicationId;
    }

    /** Returns the {@link SafetyCenterIssue} it contains. */
    @NonNull
    SafetyCenterIssue getSafetyCenterIssue() {
        return mSafetyCenterIssue;
    }

    /** Returns the {@link SafetyCenterIssueKey} related to this issue. */
    @NonNull
    SafetyCenterIssueKey getSafetyCenterIssueKey() {
        return mSafetyCenterIssueKey;
    }

    /** Returns the {@link SafetySourceIssue.IssueCategory} related to this issue. */
    @SafetySourceIssue.IssueCategory
    int getSafetySourceIssueCategory() {
        return mSafetySourceIssueCategory;
    }

    /** Returns the {@link SafetySourceData.SeverityLevel} related to this issue. */
    @SafetySourceData.SeverityLevel
    int getSafetySourceIssueSeverityLevel() {
        return mSafetySourceIssueSeverityLevel;
    }

    /** Returns the deduplication group related to this issue. */
    @Nullable
    String getDeduplicationGroup() {
        return mDeduplicationGroup;
    }

    /** Returns the deduplication id related to this issue. */
    @Nullable
    String getDeduplicationId() {
        return mDeduplicationId;
    }

    /** Builder for the {@link SafetyCenterIssueExtended}. */
    @NotThreadSafe
    static final class Builder {
        @NonNull private final SafetyCenterIssue mSafetyCenterIssue;
        @SafetySourceIssue.IssueCategory private final int mSafetySourceIssueCategory;
        @SafetySourceData.SeverityLevel private final int mSafetySourceIssueSeverityLevel;

        @Nullable private String mDeduplicationGroup;
        @Nullable private String mDeduplicationId;

        /** Constructs a new instance of the builder. */
        Builder(
                @NonNull SafetyCenterIssue safetyCenterIssue,
                @SafetySourceIssue.IssueCategory int safetySourceIssueCategory,
                @SafetySourceData.SeverityLevel int safetySourceIssueSeverityLevel) {
            this.mSafetyCenterIssue = safetyCenterIssue;
            this.mSafetySourceIssueCategory = safetySourceIssueCategory;
            this.mSafetySourceIssueSeverityLevel = safetySourceIssueSeverityLevel;
        }

        /** Sets the deduplication group for this issue. */
        @RequiresApi(UPSIDE_DOWN_CAKE)
        Builder setDeduplicationGroup(@Nullable String deduplicationGroup) {
            this.mDeduplicationGroup = deduplicationGroup;
            return this;
        }

        /** Sets the deduplication id for this issue. */
        @RequiresApi(UPSIDE_DOWN_CAKE)
        Builder setDeduplicationId(@Nullable String deduplicationId) {
            this.mDeduplicationId = deduplicationId;
            return this;
        }

        /** Returns a new {@link SafetyCenterIssueExtended} based on previously given data. */
        SafetyCenterIssueExtended build() {
            return new SafetyCenterIssueExtended(
                    mSafetyCenterIssue,
                    SafetyCenterIds.issueIdFromString(mSafetyCenterIssue.getId())
                            .getSafetyCenterIssueKey(),
                    mSafetySourceIssueCategory,
                    mSafetySourceIssueSeverityLevel,
                    mDeduplicationGroup,
                    mDeduplicationId);
        }
    }
}
