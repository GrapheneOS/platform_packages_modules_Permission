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

package com.android.permissioncontroller.safetycenter.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.safetycenter.SafetyCenterEntry;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.Preference;

import com.android.permissioncontroller.R;

/** A preference that displays a visual representation of a {@link SafetyCenterEntry}. */
public final class SafetyTopLevelEntryPreference extends Preference {

    private static final String TAG = SafetyTopLevelEntryPreference.class.getSimpleName();

    private final String mId;
    private final int mIconResId;

    public SafetyTopLevelEntryPreference(Context context, SafetyCenterEntry entry) {
        super(context);
        mId = entry.getId();
        mIconResId = toSeverityLevel(entry.getSeverityLevel()).getEntryIconResId();
        setLayoutResource(R.layout.preference_top_level_entry);
        setTitle(entry.getTitle());
        setSummary(entry.getSummary());
        setIcon(mIconResId);
        PendingIntent pendingIntent = entry.getPendingIntent();
        if (pendingIntent != null) {
            setOnPreferenceClickListener(unused -> {
                try {
                    pendingIntent.send();
                } catch (Exception ex) {
                    Log.e(TAG, String.format("Failed to execute pending intent for entry: %s",
                            entry), ex);
                }
                return true;
            });
        }
    }

    private static SeverityLevel toSeverityLevel(int entrySeverityLevel) {
        switch (entrySeverityLevel) {
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN:
                return SeverityLevel.SEVERITY_LEVEL_UNKNOWN;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED:
                return SeverityLevel.NONE;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK:
                return SeverityLevel.INFORMATION;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION:
                return SeverityLevel.RECOMMENDATION;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING:
                return SeverityLevel.CRITICAL_WARNING;
        }
        throw new IllegalArgumentException(
                String.format("Unexpected SafetyCenterEntry.EntrySeverityLevel: %s",
                        entrySeverityLevel));
    }

    boolean isSameItem(Preference other) {
        return mId != null && other instanceof SafetyTopLevelEntryPreference
            && TextUtils.equals(mId, ((SafetyTopLevelEntryPreference) other).mId);
    }

    boolean hasSameContents(Preference other) {
        if (other instanceof SafetyTopLevelEntryPreference) {
            SafetyTopLevelEntryPreference o = (SafetyTopLevelEntryPreference) other;
            // TODO: check pending intent?
            return TextUtils.equals(getTitle(), o.getTitle()) && TextUtils
                .equals(getSummary(), o.getSummary()) && mIconResId == o.mIconResId;
        }
        return false;
    }
}
