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

import android.annotation.NonNull;
import android.safetycenter.SafetyCenterIssue;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

/** Wrapper that contains a {@link SafetyCenterIssue} and some extra information about it. */
@RequiresApi(TIRAMISU)
final class SafetyCenterIssueExtended {
    @NonNull private final SafetyCenterIssue mSafetyCenterIssue;
    @NonNull private final SafetyCenterIssueKey mSafetyCenterIssueKey;
    @SafetySourceIssue.IssueCategory private final int mSafetySourceIssueCategory;
    @SafetySourceData.SeverityLevel private final int mSafetySourceIssueSeverityLevel;

    /** Constructs a new {@link SafetyCenterIssueExtended}. */
    SafetyCenterIssueExtended(
            @NonNull SafetyCenterIssue safetyCenterIssue,
            @SafetySourceIssue.IssueCategory int safetySourceIssueCategory,
            int safetySourceIssueSeverityLevel) {
        this.mSafetyCenterIssue = safetyCenterIssue;
        this.mSafetyCenterIssueKey =
                SafetyCenterIds.issueIdFromString(mSafetyCenterIssue.getId())
                        .getSafetyCenterIssueKey();
        this.mSafetySourceIssueCategory = safetySourceIssueCategory;
        this.mSafetySourceIssueSeverityLevel = safetySourceIssueSeverityLevel;
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
}
