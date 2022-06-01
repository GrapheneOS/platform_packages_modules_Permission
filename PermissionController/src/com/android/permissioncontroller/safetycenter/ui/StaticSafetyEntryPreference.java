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

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.content.Context;
import android.safetycenter.SafetyCenterStaticEntry;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;

/** A preference which displays a visual representation of a {@link SafetyCenterStaticEntry}. */
@RequiresApi(TIRAMISU)
public class StaticSafetyEntryPreference extends Preference implements ComparablePreference {

    private static final String TAG = StaticSafetyEntryPreference.class.getSimpleName();

    private final SafetyCenterStaticEntry mEntry;

    public StaticSafetyEntryPreference(Context context, SafetyCenterStaticEntry entry) {
        super(context);
        mEntry = entry;
        setTitle(entry.getTitle());
        setSummary(entry.getSummary());
        if (entry.getPendingIntent() != null) {
            setOnPreferenceClickListener(unused -> {
                try {
                    entry.getPendingIntent().send();
                } catch (Exception ex) {
                    Log.e(TAG,
                            String.format(
                                    "Failed to execute pending intent for static entry: %s", entry),
                            ex);
                }
                return true;
            });
        }
    }

    @Override
    public boolean isSameItem(@NonNull Preference preference) {
        return preference instanceof StaticSafetyEntryPreference
                && TextUtils.equals(mEntry.getTitle(),
                ((StaticSafetyEntryPreference) preference).mEntry.getTitle());
    }

    @Override
    public boolean hasSameContents(@NonNull Preference preference) {
        return preference instanceof StaticSafetyEntryPreference
                && mEntry.equals(((StaticSafetyEntryPreference) preference).mEntry);
    }
}
