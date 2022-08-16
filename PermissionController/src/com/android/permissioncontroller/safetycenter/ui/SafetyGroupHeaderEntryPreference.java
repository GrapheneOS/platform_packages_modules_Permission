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
import android.safetycenter.SafetyCenterEntry;
import android.safetycenter.SafetyCenterEntryGroup;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

import java.util.function.Consumer;

/**
 * A preference that displays a visual representation of a header for
 * {@link SafetyCenterEntryGroup}.
 */
@RequiresApi(TIRAMISU)
public class SafetyGroupHeaderEntryPreference extends Preference implements ComparablePreference {

    private static final String TAG = SafetyGroupHeaderEntryPreference.class.getSimpleName();

    private final SafetyCenterEntryGroup mGroup;
    private final PositionInCardList mPosition;
    private final boolean mIsExpanded;

    public SafetyGroupHeaderEntryPreference(
            Context context,
            SafetyCenterEntryGroup group,
            PositionInCardList position,
            boolean isExpanded,
            Consumer<String> onClick) {
        super(context);
        mGroup = group;
        mPosition = position;
        mIsExpanded = isExpanded;
        setLayoutResource(
                isExpanded
                        ? R.layout.preference_expanded_group_entry
                        : R.layout.preference_collapsed_group_entry);

        setTitle(group.getTitle());

        if (!isExpanded) {
            setSummary(group.getSummary());
            setIcon(
                    SeverityIconPicker.selectIconResId(
                            group.getSeverityLevel(), group.getSeverityUnspecifiedIconType()));
        }

        setOnPreferenceClickListener(
                unused -> {
                    onClick.accept(group.getId());
                    return true;
                });
    }

    public String getGroupId() {
        return mGroup != null ? mGroup.getId() : null;
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setBackgroundResource(mPosition.getBackgroundDrawableResId());
        final int topMargin = mPosition.getTopMargin(getContext());

        final MarginLayoutParams params = (MarginLayoutParams) holder.itemView.getLayoutParams();
        if (params.topMargin != topMargin) {
            params.topMargin = topMargin;
            holder.itemView.setLayoutParams(params);
        }

        if (!mIsExpanded) {
            boolean hideIcon =
                    mGroup.getSeverityLevel() == SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
                            && mGroup.getSeverityUnspecifiedIconType()
                            == SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON;
            holder.findViewById(R.id.icon_frame).setVisibility(hideIcon ? View.GONE : View.VISIBLE);
            holder.findViewById(R.id.empty_space)
                    .setVisibility(hideIcon ? View.VISIBLE : View.GONE);
        }

        ImageView chevronIcon = (ImageView) holder.findViewById(R.id.chevron_icon);
        chevronIcon.setImageResource(
                mIsExpanded
                        ? R.drawable.ic_safety_group_collapse
                        : R.drawable.ic_safety_group_expand);

        // When status is yellow/red, adding an "Actions needed" before the summary is read.
        int resourceId =
                mGroup.getSeverityLevel() >= SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION
                        ? R.string.safety_center_entry_group_with_actions_needed_content_description
                        : R.string.safety_center_entry_group_content_description;
        holder.itemView.setContentDescription(
                mIsExpanded
                        ? getTitle()
                        : getContext().getString(resourceId, getTitle(), getSummary()));

        // Replacing the on-click label to indicate the expand/collapse action. The on-click command
        // is set to null so that it uses the existing expand/collapse behaviour.
        ViewCompat.replaceAccessibilityAction(
                holder.itemView,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getContext()
                        .getString(
                                mIsExpanded
                                        ? R.string.safety_center_entry_group_collapse_action
                                        : R.string.safety_center_entry_group_expand_action),
                null);
    }

    @Override
    public boolean isSameItem(@NonNull Preference other) {
        return mGroup != null
                && other instanceof SafetyGroupHeaderEntryPreference
                && TextUtils.equals(
                getGroupId(), ((SafetyGroupHeaderEntryPreference) other).getGroupId());
    }

    @Override
    public boolean hasSameContents(@NonNull Preference other) {
        if (other instanceof SafetyGroupHeaderEntryPreference) {
            SafetyGroupHeaderEntryPreference o = (SafetyGroupHeaderEntryPreference) other;
            return TextUtils.equals(getTitle(), o.getTitle())
                    && mPosition == o.mPosition
                    && mIsExpanded == o.mIsExpanded;
        }
        return false;
    }
}
