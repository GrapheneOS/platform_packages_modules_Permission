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

import android.content.Context;
import android.safetycenter.SafetyCenterEntry;
import android.safetycenter.SafetyCenterEntryGroup;
import android.text.TextUtils;
import android.view.ViewGroup.MarginLayoutParams;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/** A preference that displays a visual representation of a {@link SafetyCenterEntry}. */
public class SafetyGroupHeaderEntryPreference extends Preference {

    private static final String TAG = SafetyGroupHeaderEntryPreference.class.getSimpleName();

    private final String mId;
    private final PositionInCardList mPosition;

    public SafetyGroupHeaderEntryPreference(
            Context context, SafetyCenterEntryGroup group, PositionInCardList position) {
        super(context);
        mId = group.getId();
        mPosition = position;
        setLayoutResource(R.layout.preference_expanded_group_entry);
        setWidgetLayoutResource(R.layout.preference_expanded_group_widget);
        setTitle(group.getTitle());
        setOnPreferenceClickListener(
                unused -> {
                    // TODO(b/222126985): implement collapsing UX
                    return true;
                });
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setBackgroundResource(mPosition.toBackgroundDrawableResId());
        final int topMargin = mPosition.toTopMargin(getContext());

        final MarginLayoutParams params = (MarginLayoutParams) holder.itemView.getLayoutParams();
        if (params.topMargin != topMargin) {
            params.topMargin = topMargin;
            holder.itemView.setLayoutParams(params);
        }
    }

    boolean isSameItem(Preference other) {
        return mId != null
                && other instanceof SafetyGroupHeaderEntryPreference
                && TextUtils.equals(mId, ((SafetyGroupHeaderEntryPreference) other).mId);
    }

    boolean hasSameContents(Preference other) {
        if (other instanceof SafetyGroupHeaderEntryPreference) {
            SafetyGroupHeaderEntryPreference o = (SafetyGroupHeaderEntryPreference) other;
            return TextUtils.equals(getTitle(), o.getTitle()) && mPosition == o.mPosition;
        }
        return false;
    }
}
