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
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/** A preference that displays a visual representation of a {@link SafetyCenterEntry}. */
public final class SafetyEntryPreference extends Preference {

    private static final String TAG = SafetyEntryPreference.class.getSimpleName();

    private final PositionInCardList mPosition;
    private final SafetyCenterEntry mEntry;

    public SafetyEntryPreference(
            Context context, SafetyCenterEntry entry, PositionInCardList position) {
        super(context);

        mEntry = entry;
        mPosition = position;

        setLayoutResource(R.layout.preference_entry);
        setTitle(entry.getTitle());
        setSummary(entry.getSummary());

        setIcon(selectIconResId(mEntry));

        PendingIntent pendingIntent = entry.getPendingIntent();
        if (pendingIntent != null) {
            setOnPreferenceClickListener(
                    unused -> {
                        try {
                            pendingIntent.send();
                        } catch (Exception ex) {
                            Log.e(
                                    TAG,
                                    String.format(
                                            "Failed to execute pending intent for entry: %s",
                                            entry),
                                    ex);
                        }
                        return true;
                    });
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setBackgroundResource(mPosition.toBackgroundDrawableResId());
        final int topMargin = mPosition.toTopMargin(holder.itemView.getContext());

        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        if (params.topMargin != topMargin) {
            params.topMargin = topMargin;
            holder.itemView.setLayoutParams(params);
        }

        boolean hideIcon =
                mEntry.getSeverityLevel() == SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
                        && mEntry.getSeverityUnspecifiedIconType()
                                == SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON;
        holder.findViewById(R.id.icon_frame)
                .setVisibility(hideIcon ? View.GONE : View.VISIBLE);
    }

    private static int selectIconResId(SafetyCenterEntry entry) {
        switch (entry.getSeverityLevel()) {
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN:
                return R.drawable.ic_safety_null_state;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED:
                return selectSeverityUnspecifiedIconResId(entry);
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK:
                return R.drawable.ic_safety_info;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION:
                return R.drawable.ic_safety_recommendation;
            case SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING:
                return R.drawable.ic_safety_warn;
        }
        Log.e(TAG,
                String.format(
                        "Unexpected SafetyCenterEntry.EntrySeverityLevel: %s", entry));
        return R.drawable.ic_safety_null_state;
    }

    private static int selectSeverityUnspecifiedIconResId(SafetyCenterEntry entry) {
        switch (entry.getSeverityUnspecifiedIconType()) {
            case SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON:
                return R.drawable.ic_safety_empty;
            case SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY:
                return R.drawable.ic_privacy;
            case SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION:
                return R.drawable.ic_safety_null_state;
        }
        Log.e(TAG,
                String.format(
                        "Unexpected SafetyCenterEntry.SeverityNoneIconType: %s", entry));
        return R.drawable.ic_safety_null_state;
    }

    boolean isSameItem(Preference other) {
        return other instanceof SafetyEntryPreference
                && TextUtils.equals(mEntry.getId(), ((SafetyEntryPreference) other).mEntry.getId());
    }

    boolean hasSameContents(Preference other) {
        if (other instanceof SafetyEntryPreference) {
            SafetyEntryPreference o = (SafetyEntryPreference) other;
            return mEntry.equals(o.mEntry) && mPosition == o.mPosition;
        }
        return false;
    }
}
