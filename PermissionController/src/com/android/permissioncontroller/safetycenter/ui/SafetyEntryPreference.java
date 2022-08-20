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

import android.app.PendingIntent;
import android.content.Context;
import android.safetycenter.SafetyCenterEntry;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;

/** A preference that displays a visual representation of a {@link SafetyCenterEntry}. */
@RequiresApi(TIRAMISU)
public final class SafetyEntryPreference extends Preference implements ComparablePreference {

    private static final String TAG = SafetyEntryPreference.class.getSimpleName();

    private final PositionInCardList mPosition;
    private final SafetyCenterEntry mEntry;
    private final SafetyCenterViewModel mViewModel;
    private final CharSequence mGroupId;
    @Nullable private final Integer mLaunchTaskId;

    public SafetyEntryPreference(
            Context context,
            @Nullable Integer launchTaskId,
            SafetyCenterEntry entry,
            CharSequence groupId,
            PositionInCardList position,
            SafetyCenterViewModel viewModel) {
        super(context);

        mEntry = entry;
        mPosition = position;
        mViewModel = viewModel;
        mGroupId = groupId;
        mLaunchTaskId = launchTaskId;

        setLayoutResource(R.layout.preference_entry);
        setTitle(entry.getTitle());
        setSummary(entry.getSummary());

        setIcon(
                SeverityIconPicker.selectIconResId(
                        mEntry.getSeverityLevel(), mEntry.getSeverityUnspecifiedIconType()));

        PendingIntent pendingIntent = entry.getPendingIntent();
        if (pendingIntent != null) {
            setOnPreferenceClickListener(
                    unused -> {
                        try {
                            PendingIntentSender.send(entry.getPendingIntent(), mLaunchTaskId);
                            mViewModel
                                    .getInteractionLogger()
                                    .recordForEntry(Action.ENTRY_CLICKED, mEntry);
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

        SafetyCenterEntry.IconAction iconAction = entry.getIconAction();
        if (iconAction != null) {
            setWidgetLayoutResource(
                    iconAction.getType() == SafetyCenterEntry.IconAction.ICON_ACTION_TYPE_GEAR
                            ? R.layout.preference_entry_icon_action_gear_widget
                            : R.layout.preference_entry_icon_action_info_widget);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setBackgroundResource(mPosition.getBackgroundDrawableResId());
        final int topMargin = mPosition.getTopMargin(holder.itemView.getContext());

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
        holder.findViewById(R.id.icon_frame).setVisibility(hideIcon ? View.GONE : View.VISIBLE);
        holder.findViewById(R.id.empty_space).setVisibility(hideIcon ? View.VISIBLE : View.GONE);
        enableOrDisableEntry(holder);
        SafetyCenterEntry.IconAction iconAction = mEntry.getIconAction();
        if (iconAction != null) {
            holder.findViewById(R.id.icon_action_button)
                    .setOnClickListener(
                            view -> {
                                sendIconActionIntent(iconAction);
                                mViewModel
                                        .getInteractionLogger()
                                        .recordForEntry(Action.ENTRY_ICON_ACTION_CLICKED, mEntry);
                            });
            holder.itemView.setPaddingRelative(
                    holder.itemView.getPaddingStart(),
                    holder.itemView.getPaddingTop(),
                    /* end= */ 0,
                    holder.itemView.getPaddingBottom());
        } else {
            holder.itemView.setPaddingRelative(
                    holder.itemView.getPaddingStart(),
                    holder.itemView.getPaddingTop(),
                    /* end= */ getContext()
                            .getResources()
                            .getDimensionPixelSize(R.dimen.safety_center_entry_padding_end),
                    holder.itemView.getPaddingBottom());
        }

        // Setting a customized description for entries that are part of an expandable group.
        // Whereas for non-expandable entries, the default description of title and summary is used.
        int resourceId =
                mGroupId != null
                        ? R.string.safety_center_entry_group_item_content_description
                        : R.string.safety_center_entry_content_description;
        holder.itemView.setContentDescription(
                getContext().getString(resourceId, getTitle(), getSummary()));
    }

    private void sendIconActionIntent(SafetyCenterEntry.IconAction iconAction) {
        try {
            PendingIntentSender.send(iconAction.getPendingIntent(), mLaunchTaskId);
        } catch (Exception ex) {
            Log.e(
                    TAG,
                    String.format("Failed to execute icon action intent for entry: %s", mEntry),
                    ex);
        }
    }

    /** We are doing this because we need some entries to look disabled but still be clickable. */
    private void enableOrDisableEntry(@NonNull PreferenceViewHolder holder) {
        holder.itemView.setEnabled(mEntry.getPendingIntent() != null);
        if (mEntry.isEnabled()) {
            holder.findViewById(android.R.id.title).setAlpha(1F);
            holder.findViewById(android.R.id.summary).setAlpha(1F);
        } else {
            holder.findViewById(android.R.id.title).setAlpha(0.4F);
            holder.findViewById(android.R.id.summary).setAlpha(0.4F);
        }
    }

    public CharSequence getGroupId() {
        return mGroupId;
    }

    @Override
    public boolean isSameItem(@NonNull Preference other) {
        return other instanceof SafetyEntryPreference
                && TextUtils.equals(mEntry.getId(), ((SafetyEntryPreference) other).mEntry.getId())
                && TextUtils.equals(mGroupId, ((SafetyEntryPreference) other).mGroupId);
    }

    @Override
    public boolean hasSameContents(@NonNull Preference other) {
        if (other instanceof SafetyEntryPreference) {
            SafetyEntryPreference o = (SafetyEntryPreference) other;
            return mEntry.equals(o.mEntry) && mPosition == o.mPosition;
        }
        return false;
    }
}
