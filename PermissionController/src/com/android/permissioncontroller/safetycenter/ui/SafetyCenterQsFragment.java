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

package com.android.permissioncontroller.permission.ui.handheld.v33;

import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MICROPHONE;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.permission.PermissionGroupUsage;
import android.permission.PermissionManager;
import android.util.ArrayMap;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.model.v33.SafetyCenterQsViewModel;
import com.android.permissioncontroller.permission.ui.model.v33.SafetyCenterQsViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.safetycenter.ui.SafetyCenterDashboardFragment;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The Quick Settings fragment for the safety center. Displays information to the user about the
 * current safety and privacy status of their device, including showing mic/camera usage, and having
 * mic/camera/location toggles.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class SafetyCenterQsFragment extends Fragment {
    private static final ArrayMap<String, Integer> sToggleButtons = new ArrayMap<>();

    private Context mContext;
    private long mSessionId;
    private List<PermissionGroupUsage> mPermGroupUsages;
    private SafetyCenterQsViewModel mViewModel;
    private View mRootView;

    static {
        sToggleButtons.put(CAMERA, R.id.camera_toggle);
        sToggleButtons.put(MICROPHONE, R.id.mic_toggle);
        sToggleButtons.put(LOCATION, R.id.location_toggle);
    }

    /**
     * Create instance of SafetyCenterDashboardFragment with the arguments set
     *
     * @param sessionId The current session Id
     * @param usages ArrayList of PermissionGroupUsage
     * @return SafetyCenterQsFragment with the arguments set
     */
    public static SafetyCenterQsFragment newInstance(
            long sessionId, ArrayList<PermissionGroupUsage> usages) {
        Bundle args = new Bundle();
        args.putLong(EXTRA_SESSION_ID, sessionId);
        args.putParcelableArrayList(PermissionManager.EXTRA_PERMISSION_USAGES, usages);
        SafetyCenterQsFragment frag = new SafetyCenterQsFragment();
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSessionId = INVALID_SESSION_ID;
        if (getArguments() != null) {
            mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        }
        mContext = getContext();

        mPermGroupUsages =
                getArguments().getParcelableArrayList(PermissionManager.EXTRA_PERMISSION_USAGES);
        if (mPermGroupUsages == null) {
            mPermGroupUsages = new ArrayList<>();
        }

        getActivity().setTheme(R.style.Theme_SafetyCenterQs);

        SafetyCenterQsViewModelFactory factory =
                new SafetyCenterQsViewModelFactory(
                        getActivity().getApplication(), mSessionId, mPermGroupUsages);
        mViewModel =
                new ViewModelProvider(requireActivity(), factory)
                        .get(SafetyCenterQsViewModel.class);
        mViewModel
                .getSensorPrivacyLiveData()
                .observe(this, (v) -> setSensorToggleState(v, getView()));
        // LightAppPermGroupLiveDatas are kept track of in the view model,
        // we need to start observing them here
        if (!mPermGroupUsages.isEmpty()) {
            mViewModel.getPermDataLoadedLiveData().observe(this, this::onPermissionGroupsLoaded);
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.safety_center_qs, container, false);
        mRootView = root;
        if (mPermGroupUsages.isEmpty()) {
            mRootView.setVisibility(View.VISIBLE);
            setSensorToggleState(new ArrayMap<>(), mRootView);
        } else {
            mRootView.setVisibility(View.GONE);
        }
        root.setBackgroundColor(android.R.color.background_dark);
        root.findViewById(R.id.close_button).setOnClickListener((v) -> requireActivity().finish());

        View securitySettings = root.findViewById(R.id.security_settings_button);
        securitySettings.setOnClickListener((v) -> mViewModel.navigateToSecuritySettings(this));
        ((TextView) securitySettings.findViewById(R.id.toggle_sensor_name))
                .setText(R.string.settings);
        securitySettings.findViewById(R.id.toggle_sensor_status).setVisibility(View.GONE);
        ((ImageView) securitySettings.findViewById(R.id.toggle_sensor_icon))
                .setImageDrawable(mContext.getDrawable(R.drawable.settings_gear));
        securitySettings.findViewById(R.id.arrow_icon).setVisibility(View.VISIBLE);
        ((ImageView) securitySettings.findViewById(R.id.arrow_icon))
                .setImageDrawable(mContext.getDrawable(R.drawable.forward_arrow));

        getChildFragmentManager()
                .beginTransaction()
                .add(
                        R.id.safety_center_prefs,
                        SafetyCenterDashboardFragment.newInstance(
                                /* isQuickSettingsFragment= */ true))
                .commitNow();
        return root;
    }

    private void onPermissionGroupsLoaded(boolean initialized) {
        if (initialized) {
            mRootView.setVisibility(View.VISIBLE);
            setSensorToggleState(new ArrayMap<>(), mRootView);
            addPermissionUsageInformation(mRootView);
        }
    }

    private void addPermissionUsageInformation(View rootView) {
        if (rootView == null) {
            return;
        }
        View permissionSectionTitleView = rootView.findViewById(R.id.permission_section_title);
        if (mPermGroupUsages == null || mPermGroupUsages.isEmpty()) {
            permissionSectionTitleView.setVisibility(View.GONE);
            return;
        }
        permissionSectionTitleView.setVisibility(View.VISIBLE);
        LinearLayout usageLayout = rootView.findViewById(R.id.permission_usage);
        Collections.sort(
                mPermGroupUsages, (pguA, pguB) -> getAppLabel(pguA).toString().compareTo(
                        getAppLabel(pguB).toString()));

        for (PermissionGroupUsage usage : mPermGroupUsages) {
            View cardView = View.inflate(mContext, R.layout.indicator_card, usageLayout);
            cardView.setId(View.generateViewId());
            ConstraintLayout parentIndicatorLayout = cardView.findViewById(R.id.indicator_layout);
            parentIndicatorLayout.setId(View.generateViewId());
            ImageView expandView = parentIndicatorLayout.findViewById(R.id.expand_view);

            // Update UI for the parent indicator card
            updateIndicatorParentUi(
                    parentIndicatorLayout,
                    usage.getPermissionGroupName(),
                    generateUsageLabel(usage),
                    usage.isActive());

            // If sensor usage is due to an active phone call, don't allow any actions
            if (usage.isPhoneCall()) {
                expandView.setVisibility(View.GONE);
                continue;
            }

            ConstraintLayout expandedLayout = cardView.findViewById(R.id.expanded_layout);
            expandedLayout.setId(View.generateViewId());

            // Handle redraw on orientation changes if permission has been revoked
            if (mViewModel.getRevokedUsages().contains(usage)) {
                disableIndicatorCardUi(parentIndicatorLayout, expandView);
                continue;
            }

            setIndicatorExpansionBehavior(parentIndicatorLayout, expandedLayout, expandView);

            // Configure the indicator action buttons
            configureIndicatorActionButtons(
                    usage, parentIndicatorLayout, expandedLayout, expandView);
        }
    }

    private void configureIndicatorActionButtons(
            PermissionGroupUsage usage,
            ConstraintLayout parentIndicatorLayout,
            ConstraintLayout expandedLayout,
            ImageView expandView) {
        configurePrimaryActionButton(usage, parentIndicatorLayout, expandedLayout, expandView);
        configureSeeUsageButton(usage, expandedLayout);
    }

    private void configurePrimaryActionButton(
            PermissionGroupUsage usage,
            ConstraintLayout parentIndicatorLayout,
            ConstraintLayout expandedLayout,
            ImageView expandView) {
        boolean shouldAllowRevoke = mViewModel.shouldAllowRevoke(usage);
        Intent manageServiceIntent = null;

        if (isSubAttributionUsage(usage.getAttributionLabel())) {
            manageServiceIntent = mViewModel.getStartViewPermissionUsageIntent(mContext, usage);
        }

        int primaryActionButtonLabel =
                getPrimaryActionButtonLabel(
                        manageServiceIntent != null,
                        shouldAllowRevoke,
                        usage.getPermissionGroupName());
        MaterialButton primaryActionButton = expandedLayout.findViewById(R.id.primary_button);
        primaryActionButton.setText(primaryActionButtonLabel);
        primaryActionButton.setStrokeColorResource(
                Utils.getColorResId(mContext, android.R.attr.colorAccent));

        if (shouldAllowRevoke && manageServiceIntent == null) {
            primaryActionButton.setOnClickListener(
                    l -> {
                        parentIndicatorLayout.callOnClick();
                        disableIndicatorCardUi(parentIndicatorLayout, expandView);
                        revokePermission(usage);
                    });
        } else {
            setPrimaryActionClickListener(primaryActionButton, usage, manageServiceIntent);
        }
    }

    private void configureSeeUsageButton(
            PermissionGroupUsage usage, ConstraintLayout expandedLayout) {
        MaterialButton seeUsageButton = expandedLayout.findViewById(R.id.secondary_button);
        seeUsageButton.setText(getSeeUsageText(usage.getPermissionGroupName()));
        seeUsageButton.setStrokeColorResource(
                Utils.getColorResId(mContext, android.R.attr.colorAccent));
        seeUsageButton.setOnClickListener(
                l -> {
                    mViewModel.navigateToSeeUsage(this, usage.getPermissionGroupName());
                });
    }

    private void setPrimaryActionClickListener(
            Button primaryActionButton, PermissionGroupUsage usage, Intent manageServiceIntent) {
        if (manageServiceIntent != null) {
            primaryActionButton.setOnClickListener(
                    l -> {
                        mViewModel.navigateToManageService(this, manageServiceIntent);
                    });
        } else {
            primaryActionButton.setOnClickListener(
                    l -> {
                        mViewModel.navigateToManageAppPermissions(this, usage);
                    });
        }
    }

    private int getPrimaryActionButtonLabel(
            boolean canHandleIntent, boolean shouldAllowRevoke, String permissionGroupName) {
        if (canHandleIntent) {
            return R.string.manage_service_qs;
        }
        if (!shouldAllowRevoke) {
            return R.string.manage_permissions_qs;
        }
        return getRemovePermissionText(permissionGroupName);
    }

    private boolean isSubAttributionUsage(@Nullable CharSequence attributionLabel) {
        if (attributionLabel == null || attributionLabel.length() == 0) {
            return false;
        }
        return true;
    }

    private void revokePermission(PermissionGroupUsage usage) {
        mViewModel.revokePermission(usage);
    }

    private void disableIndicatorCardUi(
            ConstraintLayout parentIndicatorLayout, ImageView expandView) {
        // Disable the parent indicator and the expand view
        parentIndicatorLayout.setEnabled(false);
        expandView.setEnabled(false);
        expandView.setVisibility(View.GONE);

        // Construct new icon for revoked permission
        ImageView iconView = parentIndicatorLayout.findViewById(R.id.indicator_icon);
        Drawable background = mContext.getDrawable(R.drawable.indicator_background_circle).mutate();
        background.setTint(mContext.getColor(R.color.sc_surface_variant_dark));
        Drawable icon = mContext.getDrawable(R.drawable.ic_check);
        Utils.applyTint(mContext, icon, android.R.attr.textColorPrimary);
        int bgSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_circle_size);
        int iconSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_icon_size);
        iconView.setImageDrawable(constructIcon(icon, background, bgSize, iconSize));

        // Set label to show on permission revoke
        TextView labelView = parentIndicatorLayout.findViewById(R.id.indicator_label);
        labelView.setText(R.string.permissions_removed_qs);
    }

    private void setIndicatorExpansionBehavior(
            ConstraintLayout parentIndicatorLayout,
            ConstraintLayout expandedLayout,
            ImageView expandView) {
        parentIndicatorLayout.setOnClickListener(
                createExpansionListener(expandedLayout, expandView));
        expandView.setOnClickListener(createExpansionListener(expandedLayout, expandView));
    }

    private View.OnClickListener createExpansionListener(
            ConstraintLayout expandedLayout, ImageView expandView) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (expandedLayout.getVisibility() == View.VISIBLE) {
                    expandedLayout.setVisibility(View.GONE);
                    expandView.setImageDrawable(
                            constructExpandButton(mContext.getDrawable(R.drawable.ic_expand_more)));
                } else {
                    expandedLayout.setVisibility(View.VISIBLE);
                    expandView.setImageDrawable(
                            constructExpandButton(mContext.getDrawable(R.drawable.ic_expand_less)));
                }
            }
        };
    }

    private String generateUsageLabel(PermissionGroupUsage usage) {
        if (usage.isPhoneCall() && usage.isActive()) {
            return mContext.getString(R.string.active_call_usage_qs);
        } else if (usage.isPhoneCall()) {
            return mContext.getString(R.string.recent_call_usage_qs);
        }
        return generateAttributionUsageLabel(usage);
    }

    private String generateAttributionUsageLabel(PermissionGroupUsage usage) {
        CharSequence appLabel = getAppLabel(usage);

        final int usageResId =
                usage.isActive() ? R.string.active_app_usage_qs : R.string.recent_app_usage_qs;
        final int singleUsageResId =
                usage.isActive() ? R.string.active_app_usage_1_qs : R.string.recent_app_usage_1_qs;
        final int doubleUsageResId =
                usage.isActive() ? R.string.active_app_usage_2_qs : R.string.recent_app_usage_2_qs;

        CharSequence attributionLabel = usage.getAttributionLabel();
        CharSequence proxyLabel = usage.getProxyLabel();

        if (attributionLabel == null && proxyLabel == null) {
            return mContext.getString(usageResId, appLabel);
        } else if (attributionLabel != null && proxyLabel != null) {
            return mContext.getString(doubleUsageResId, appLabel, attributionLabel, proxyLabel);
        } else {
            return mContext.getString(
                    singleUsageResId,
                    appLabel,
                    attributionLabel == null ? proxyLabel : attributionLabel);
        }
    }

    private CharSequence getAppLabel(PermissionGroupUsage usage) {
        return KotlinUtils.INSTANCE.getPackageLabel(
                getActivity().getApplication(),
                usage.getPackageName(),
                UserHandle.getUserHandleForUid(usage.getUid()));
    }

    private void updateIndicatorParentUi(
            ConstraintLayout indicatorParentLayout,
            String permGroupName,
            String usageText,
            boolean isActiveUsage) {
        CharSequence permGroupLabel =
                KotlinUtils.INSTANCE.getPermGroupLabel(mContext, permGroupName);
        ImageView iconView = indicatorParentLayout.findViewById(R.id.indicator_icon);

        Drawable background = mContext.getDrawable(R.drawable.indicator_background_circle);
        int indicatorColor =
                Utils.getColorResId(
                        mContext,
                        isActiveUsage
                                ? android.R.attr.textColorPrimaryInverse
                                : android.R.attr.textColorPrimary);
        Drawable indicatorIcon =
                KotlinUtils.INSTANCE.getPermGroupIcon(
                        mContext, permGroupName, mContext.getColor(indicatorColor));
        if (isActiveUsage) {
            Utils.applyTint(mContext, background, android.R.attr.colorAccent);
        } else {
            background.setTint(mContext.getColor(R.color.sc_surface_variant_dark));
        }
        int bgSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_circle_size);
        int iconSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_icon_size);
        iconView.setImageDrawable(constructIcon(indicatorIcon, background, bgSize, iconSize));
        iconView.setContentDescription(permGroupLabel);

        TextView titleText = indicatorParentLayout.findViewById(R.id.indicator_title);
        titleText.setText(permGroupLabel);
        titleText.setTextColor(
                mContext.getColor(Utils.getColorResId(mContext, android.R.attr.textColorPrimary)));
        titleText.setContentDescription(permGroupLabel);

        TextView labelText = indicatorParentLayout.findViewById(R.id.indicator_label);
        labelText.setText(usageText);
        labelText.setContentDescription(usageText);

        ImageView expandView = indicatorParentLayout.findViewById(R.id.expand_view);
        expandView.setImageDrawable(
                constructExpandButton(mContext.getDrawable(R.drawable.ic_expand_more)));
    }

    private Drawable constructExpandButton(Drawable expandButtonIcon) {
        Utils.applyTint(mContext, expandButtonIcon, android.R.attr.textColorPrimary);
        Drawable expandButtonBackground =
                mContext.getDrawable(R.drawable.indicator_background_circle).mutate();
        expandButtonBackground.setTint(mContext.getColor(R.color.sc_surface_variant_dark));
        int size =
                (int) getResources().getDimension(
                        R.dimen.safety_center_indicator_expand_button_background);
        return constructIcon(expandButtonIcon, expandButtonBackground, size, size);
    }

    private Drawable constructIcon(Drawable icon, Drawable background, int bgSize, int iconSize) {
        LayerDrawable layered = new LayerDrawable(new Drawable[] {background, icon});
        final int bgLayerIndex = 0;
        final int iconLayerIndex = 1;
        layered.setLayerSize(bgLayerIndex, bgSize, bgSize);
        layered.setLayerSize(iconLayerIndex, iconSize, iconSize);
        layered.setLayerGravity(iconLayerIndex, Gravity.CENTER);
        return layered;
    }

    private void setSensorToggleState(Map<String, Boolean> sensorState, View rootView) {
        if (rootView == null) {
            if (getView() == null) {
                return;
            }
            rootView = getView();
            if (rootView == null) {
                return;
            }
        }

        for (int i = 0; i < sToggleButtons.size(); i++) {
            View toggle = rootView.findViewById(sToggleButtons.valueAt(i));
            String groupName = sToggleButtons.keyAt(i);
            if (!toggle.hasOnClickListeners()) {
                toggle.setOnClickListener((v) -> mViewModel.toggleSensor(groupName));
            }

            TextView groupLabel = toggle.findViewById(R.id.toggle_sensor_name);
            groupLabel.setText(KotlinUtils.INSTANCE.getPermGroupLabel(mContext, groupName));
            TextView blockedStatus = toggle.findViewById(R.id.toggle_sensor_status);
            ImageView iconView = toggle.findViewById(R.id.toggle_sensor_icon);
            boolean sensorEnabled =
                    !sensorState.containsKey(groupName) || sensorState.get(groupName);

            Drawable icon;
            int colorPrimary = getTextColor(true, sensorEnabled);
            int colorSecondary = getTextColor(false, sensorEnabled);
            if (sensorEnabled) {
                blockedStatus.setText(R.string.available);
                toggle.setBackgroundResource(R.drawable.safety_center_button_background);
                icon = KotlinUtils.INSTANCE.getPermGroupIcon(mContext, groupName, colorPrimary);
            } else {
                blockedStatus.setText(R.string.blocked);
                toggle.setBackgroundResource(R.drawable.safety_center_button_background_dark);
                icon = mContext.getDrawable(getBlockedIconResId(groupName));
                icon.setTint(colorPrimary);
            }
            blockedStatus.setTextColor(colorSecondary);
            groupLabel.setTextColor(colorPrimary);
            iconView.setImageDrawable(icon);
        }
    }

    @ColorInt
    private Integer getTextColor(boolean primary, boolean inverse) {
        int primaryAttribute =
                inverse ? android.R.attr.textColorPrimaryInverse : android.R.attr.textColorPrimary;
        int secondaryAttribute =
                inverse
                        ? android.R.attr.textColorSecondaryInverse
                        : android.R.attr.textColorSecondary;
        int attribute = primary ? primaryAttribute : secondaryAttribute;
        TypedValue value = new TypedValue();
        mContext.getTheme().resolveAttribute(attribute, value, true);
        int colorRes = value.resourceId != 0 ? value.resourceId : value.data;
        return mContext.getColor(colorRes);
    }

    private static int getRemovePermissionText(String permissionGroup) {
        return CAMERA.equals(permissionGroup)
                ? R.string.remove_camera_qs
                : R.string.remove_microphone_qs;
    }

    private static int getSeeUsageText(String permissionGroup) {
        return CAMERA.equals(permissionGroup)
                ? R.string.camera_usage_qs
                : R.string.microphone_usage_qs;
    }

    private static int getBlockedIconResId(String permissionGroup) {
        switch (permissionGroup) {
            case MICROPHONE:
                return R.drawable.ic_mic_blocked;
            case CAMERA:
                return R.drawable.ic_camera_blocked;
            case LOCATION:
                return R.drawable.ic_location_blocked;
        }
        return -1;
    }
}