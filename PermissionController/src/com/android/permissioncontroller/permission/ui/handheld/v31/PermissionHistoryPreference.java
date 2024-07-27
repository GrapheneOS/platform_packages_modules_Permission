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

package com.android.permissioncontroller.permission.ui.handheld.v31;

import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_DETAILS_INTERACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_DETAILS_INTERACTION__ACTION__INFO_ICON_CLICKED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.write;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.compat.IntentCompat;
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.List;
import java.util.Objects;

/**
 * Preference for the permission history page
 */
public class PermissionHistoryPreference extends Preference {

    private static final String LOG_TAG = "PermissionHistoryPref";

    private final Context mContext;
    private final UserHandle mUserHandle;
    private final String mPackageName;
    private final String mPermissionGroup;
    private final long mAccessStartTime;
    private final long mAccessEndTime;
    private final Drawable mAppIcon;
    private final List<String> mAttributionTags;
    private final boolean mIsLastUsage;
    private Intent mIntent = null;
    private boolean mIntentLoaded = false;
    private final boolean mShowingAttribution;
    private final PackageManager mUserPackageManager;
    private final boolean mIsEmergencyLocationAccess;

    private final long mSessionId;

    public PermissionHistoryPreference(@NonNull Context context, @NonNull UserHandle userHandle,
            @NonNull String pkgName, @Nullable Drawable packageIcon, @NonNull String packageLabel,
            @NonNull String permissionGroup, @NonNull long accessStartTime,
            @NonNull long accessEndTime, @Nullable CharSequence summaryText,
            boolean showingAttribution, @NonNull List<String> attributionTags,
            boolean isLastUsage, long sessionId, boolean isEmergencyLocationAccess) {
        super(context);
        mContext = context;
        Context userContext = Utils.getUserContext(context, userHandle);
        mUserPackageManager = userContext.getPackageManager();
        mUserHandle = userHandle;
        mPackageName = pkgName;
        mPermissionGroup = permissionGroup;
        mAccessStartTime = accessStartTime;
        mAccessEndTime = accessEndTime;
        mAppIcon = packageIcon;
        mAttributionTags = attributionTags;
        mIsLastUsage = isLastUsage;
        mSessionId = sessionId;
        mShowingAttribution = showingAttribution;
        mIsEmergencyLocationAccess = isEmergencyLocationAccess;

        setTitle(packageLabel);
        if (summaryText != null) {
            setSummary(summaryText);
        }
        setWidgetLayoutResource(R.layout.image_view_with_divider);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ViewGroup widgetFrame = (ViewGroup) holder.findViewById(android.R.id.widget_frame);
        LinearLayout widgetFrameParent = (LinearLayout) widgetFrame.getParent();

        View iconFrame = holder.findViewById(R.id.icon_frame);
        widgetFrameParent.removeView(iconFrame);

        ViewGroup widget = (ViewGroup) holder.findViewById(R.id.permission_history_layout);
        if (widget == null) {
            LayoutInflater inflater = mContext.getSystemService(LayoutInflater.class);
            widget = (ViewGroup) inflater.inflate(R.layout.permission_history_widget,
                    widgetFrameParent, false);

            widgetFrameParent.addView(widget, 0);
        }

        widgetFrameParent.setGravity(Gravity.TOP);

        TextView permissionHistoryTime = widget.findViewById(R.id.permission_history_time);
        permissionHistoryTime.setText(DateFormat.getTimeFormat(mContext).format(mAccessEndTime));

        ImageView appIcon = widget.findViewById(R.id.permission_history_icon);
        appIcon.setImageDrawable(mAppIcon);

        ImageView widgetView = widgetFrame.findViewById(R.id.icon);
        View dividerVerticalBar = widgetFrame.findViewById(R.id.divider);
        setInfoIcon(holder, widgetView, dividerVerticalBar);

        View dashLine = widget.findViewById(R.id.permission_history_dash_line);
        dashLine.setVisibility(mIsLastUsage ? View.GONE : View.VISIBLE);

        // This Intent should ideally be part of the constructor, passed in from the ViewModel.
        // It's temporarily created via a static method due to ongoing ViewModel refactoring.
        Intent intent =
                PermissionUsageDetailsViewModel.Companion.createHistoryPreferenceClickIntent(
                        mContext,
                        mUserHandle,
                        mPackageName,
                        mPermissionGroup,
                        mAccessStartTime,
                        mAccessEndTime,
                        mShowingAttribution,
                        mAttributionTags);

        if (mIsEmergencyLocationAccess) {
            setOnPreferenceClickListener(preference -> {
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext)
                        .setTitle(R.string.privacy_dashboard_emergency_location_dialog_title)
                        .setMessage(
                                R.string.privacy_dashboard_emergency_location_dialog_description)
                        .setPositiveButton(R.string.app_permissions,  (dialog, which) -> {
                            mContext.startActivityAsUser(intent, mUserHandle);
                        })
                        .setNegativeButton(R.string.dialog_close, null);

                AlertDialog alertDialog = dialogBuilder.create();
                alertDialog.show();
                TextView messageView = alertDialog.findViewById(android.R.id.message);
                if (messageView != null) {
                    messageView.setMovementMethod(LinkMovementMethod.getInstance());
                }
                return true;
            });
        } else {
            setOnPreferenceClickListener((preference) -> {
                mContext.startActivityAsUser(intent, mUserHandle);
                return true;
            });
        }
    }

    private void setInfoIcon(@NonNull PreferenceViewHolder holder, ImageView widgetView,
            View dividerVerticalBar) {
        Intent intent = getViewPermissionUsageForPeriodIntent();
        if (intent != null) {
            dividerVerticalBar.setVisibility(View.VISIBLE);
            widgetView.setImageDrawable(mContext.getDrawable(R.drawable.ic_info_outline));
            widgetView.setOnClickListener(v -> {
                write(PERMISSION_DETAILS_INTERACTION,
                        mSessionId,
                        mPermissionGroup,
                        mPackageName,
                        PERMISSION_DETAILS_INTERACTION__ACTION__INFO_ICON_CLICKED);
                try {
                    mContext.startActivityAsUser(intent, mUserHandle);
                } catch (ActivityNotFoundException e) {
                    Log.e(LOG_TAG, "No activity found for viewing permission usage.");
                }
            });
            View preferenceRootView = holder.itemView;
            preferenceRootView.setPaddingRelative(preferenceRootView.getPaddingStart(),
                    preferenceRootView.getPaddingTop(), 0, preferenceRootView.getPaddingBottom());
        } else {
            dividerVerticalBar.setVisibility(View.GONE);
            widgetView.setImageDrawable(null);
        }
    }

    /**
     * Get a {@link Intent#ACTION_VIEW_PERMISSION_USAGE_FOR_PERIOD} intent, or null if the intent
     * can't be handled.
     */
    @Nullable
    private Intent getViewPermissionUsageForPeriodIntent() {
        if (mIntentLoaded) {
            return mIntent;
        }
        Intent viewUsageIntent = new Intent();
        viewUsageIntent.setAction(Intent.ACTION_VIEW_PERMISSION_USAGE_FOR_PERIOD);
        viewUsageIntent.setPackage(mPackageName);
        viewUsageIntent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mPermissionGroup);
        viewUsageIntent.putExtra(Intent.EXTRA_ATTRIBUTION_TAGS,
                mAttributionTags.toArray(new String[0]));
        viewUsageIntent.putExtra(Intent.EXTRA_START_TIME,
                mAccessStartTime);
        viewUsageIntent.putExtra(Intent.EXTRA_END_TIME, mAccessEndTime);
        viewUsageIntent.putExtra(IntentCompat.EXTRA_SHOWING_ATTRIBUTION, mShowingAttribution);
        viewUsageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ResolveInfo resolveInfo = mUserPackageManager.resolveActivity(viewUsageIntent,
                PackageManager.MATCH_INSTANT);
        if (resolveInfo != null && resolveInfo.activityInfo != null && Objects.equals(
                resolveInfo.activityInfo.permission,
                android.Manifest.permission.START_VIEW_PERMISSION_USAGE)) {
            mIntent = viewUsageIntent;
        }
        mIntentLoaded = true;
        return mIntent;
    }
}