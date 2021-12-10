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

package com.android.permissioncontroller.safetycenter.ui;

import com.android.permissioncontroller.R;

/** Indicates the overall safety status of the device and user. */
public class OverallSafetyStatus {

    /** Severity level for the Safety Center's overall status. */
    public enum Level {
        SAFETY_STATUS_LEVEL_UNKNOWN(R.drawable.safety_status_info),
        INFORMATION_NO_ISSUES(R.drawable.safety_status_info),
        INFORMATION_REVIEW_ISSUES(R.drawable.safety_status_info_review),
        RECOMMENDATION(R.drawable.safety_status_recommendation),
        CRITICAL_WARNING(R.drawable.safety_status_warn);

        private final int mImageResId;

        Level(int imageResId) {
            mImageResId = imageResId;
        }

        public int getImageResId() {
            return mImageResId;
        }
    }

    private final Level mLevel;
    private final String mTitle;
    private final String mSummary;

    public OverallSafetyStatus(
            Level level, String title, String summary) {
        mLevel = level;
        mTitle = title;
        mSummary = summary;
    }

    public Level getLevel() {
        return mLevel;
    }

    int getImageResId() {
        return getLevel().getImageResId();
    }

    public String getTitle() {
        return mTitle;
    }

    public String getSummary() {
        return mSummary;
    }
}
