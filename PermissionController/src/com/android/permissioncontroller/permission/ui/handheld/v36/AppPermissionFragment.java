/*
 * Copyright (C) 2018 The Android Open Source Project
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

// LINT.IfChange

package com.android.permissioncontroller.permission.ui.handheld.v36;

import static android.Manifest.permission_group.STORAGE;
import static android.app.Activity.RESULT_OK;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ASK_EVERY_TIME;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__GRANT_FINE_LOCATION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__PHOTOS_SELECTED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__REVOKE_FINE_LOCATION;
import static com.android.permissioncontroller.permission.compat.AppPermissionFragmentCompat.PERSISTENT_DEVICE_ID;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_CALLER_NAME;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_INTERACTED;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_RESULT;
import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.data.FullStoragePermissionAppsLiveData.FullStoragePackageState;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;
import com.android.permissioncontroller.permission.ui.handheld.AllAppPermissionsFragment;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionGroupsFragment;
import com.android.permissioncontroller.permission.ui.handheld.PermissionAppsFragment;
import com.android.permissioncontroller.permission.ui.handheld.PermissionPreference;
import com.android.permissioncontroller.permission.ui.handheld.PermissionPreferenceCategory;
import com.android.permissioncontroller.permission.ui.handheld.PermissionSelectorWithWidgetPreference;
import com.android.permissioncontroller.permission.ui.handheld.PermissionSwitchPreference;
import com.android.permissioncontroller.permission.ui.handheld.PermissionTwoTargetPreference;
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonState;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ChangeRequest;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModelFactory;
import com.android.permissioncontroller.permission.ui.v33.AdvancedConfirmDialogArgs;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.permission.utils.v35.MultiDeviceUtils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

import kotlin.Pair;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Show and manage a single permission group for an app.
 *
 * <p>Allows the user to control whether the app is granted the permission.
 *
 * <p>Note: This fragment is opt-in on V and enabled on B.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
public class AppPermissionFragment extends SettingsWithLargeHeader
        implements AppPermissionViewModel.ConfirmDialogShowingFragment {
    private static final String LOG_TAG = "AppPermissionFragment";
    private static final long POST_DELAY_MS = 20;
    private static final long EDIT_PHOTOS_BUTTON_ANIMATION_LENGTH_MS = 200L;

    private @NonNull AppPermissionViewModel mViewModel;

    private @NonNull PermissionPreferenceCategory mAppPermissionRationaleContainer;
    private @NonNull PermissionPreference mAppPermissionRationaleContent;
    private @NonNull PermissionPreferenceCategory mButtonCategory;
    private @NonNull PermissionSelectorWithWidgetPreference mAllowButton;
    private @NonNull SelectorWithWidgetPreference mAllowAlwaysButton;
    private @NonNull SelectorWithWidgetPreference mAllowForegroundButton;
    private @NonNull PermissionSelectorWithWidgetPreference mSelectButton;
    private @NonNull SelectorWithWidgetPreference mAskOneTimeButton;
    private @NonNull SelectorWithWidgetPreference mAskButton;
    private @NonNull SelectorWithWidgetPreference mDenyButton;
    private @NonNull SelectorWithWidgetPreference mDenyForegroundButton;
    private @NonNull PermissionSwitchPreference mLocationAccuracySwitch;
    private @NonNull PermissionTwoTargetPreference mDetails;
    private @NonNull PermissionPreference mFooterLink1;
    private @NonNull PermissionPreference mFooterLink2;
    private @NonNull PermissionPreference mFooterStorageSpecialAppAccess;
    private @NonNull PermissionPreference mAdditionalInfo;

    private @NonNull String mPackageName;
    private @NonNull String mPermGroupName;
    private @NonNull UserHandle mUser;
    private boolean mIsStorageGroup;
    private boolean mIsInitialLoad;
    // This prevents the user from clicking the photo picker button multiple times in succession
    private boolean mPhotoPickerTriggered;
    private long mSessionId;
    private String mPersistentDeviceId;

    private @NonNull String mPackageLabel;
    private @NonNull String mPermGroupLabel;
    private Drawable mPackageIcon;
    private @NonNull RoleManager mRoleManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermGroupName == null) {
            mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }
        mIsStorageGroup = Objects.equals(mPermGroupName, STORAGE);
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        mPackageLabel = BidiFormatter.getInstance().unicodeWrap(
                KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(), mPackageName,
                        mUser));
        mPermGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(getContext(),
                mPermGroupName).toString();
        mPackageIcon = KotlinUtils.INSTANCE.getBadgedPackageIcon(getActivity().getApplication(),
                mPackageName, mUser);
        mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        mPersistentDeviceId = getArguments().getString(PERSISTENT_DEVICE_ID,
                MultiDeviceUtils.getDefaultDevicePersistentDeviceId());

        AppPermissionViewModelFactory factory = new AppPermissionViewModelFactory(
                getActivity().getApplication(), mPackageName, mPermGroupName, mUser, mSessionId,
                mPersistentDeviceId);
        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionViewModel.class);
        Handler delayHandler = new Handler(Looper.getMainLooper());
        mViewModel.getButtonStateLiveData().observe(this, buttonState -> {
            if (mIsInitialLoad) {
                setRadioButtonsState(buttonState);
            } else {
                delayHandler.removeCallbacksAndMessages(null);
                delayHandler.postDelayed(() -> setRadioButtonsState(buttonState), POST_DELAY_MS);
            }
        });
        mViewModel.getDetailResIdLiveData().observe(this, this::setDetail);
        mViewModel.getShowAdminSupportLiveData().observe(this, this::setAdminSupportDetail);
        if (mIsStorageGroup) {
            mViewModel.getFullStorageStateLiveData().observe(this, this::setSpecialStorageState);
        }
        mViewModel.getShowPermissionRationaleLiveData().observe(this,
                this::showPermissionRationaleDialog);

        mRoleManager = Utils.getSystemServiceSafe(getContext(), RoleManager.class);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        super.onCreatePreferences(bundle, s);
        addPreferencesFromResource(R.xml.app_permission);

        mAppPermissionRationaleContainer = requirePreference("app_permission_rationale_container");
        mAppPermissionRationaleContent = requirePreference("app_permission_rationale");
        mButtonCategory = requirePreference("app_permission_button_category");
        mAllowButton = requirePreference("app_permission_allow_radio_button");
        mAllowAlwaysButton = requirePreference("app_permission_allow_always_radio_button");
        mAllowForegroundButton =
                requirePreference("app_permission_allow_foreground_only_radio_button");
        mSelectButton = requirePreference("app_permission_select_photos_radio_button");
        mAskOneTimeButton = requirePreference("app_permission_ask_one_time_radio_button");
        mAskButton = requirePreference("app_permission_ask_radio_button");
        mDenyButton = requirePreference("app_permission_deny_radio_button");
        mDenyForegroundButton = requirePreference("app_permission_deny_foreground_radio_button");
        mLocationAccuracySwitch = requirePreference("app_permission_location_accuracy_switch");
        mDetails = requirePreference("app_permission_details");
        mFooterLink1 = requirePreference("app_permission_footer_link_1");
        mFooterLink2 = requirePreference("app_permission_footer_link_2");
        mFooterStorageSpecialAppAccess =
                requirePreference("app_permission_footer_storage_special_app_access");
        mAdditionalInfo = requirePreference("app_permission_additional_info");
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    private <T extends Preference> @NonNull T requirePreference(@NonNull CharSequence key) {
        return Objects.requireNonNull(findPreference(key),
                "Failed to find preference '" + key + "'");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getContext();

        mIsInitialLoad = true;

        setHeader(mPackageIcon, mPackageLabel, null, null, false);

        String text = null;
        if (MultiDeviceUtils.isDefaultDeviceId(mPersistentDeviceId)) {
            text = context.getString(R.string.app_permission_header, mPermGroupLabel);
        } else {
            final String deviceName = MultiDeviceUtils.getDeviceName(context, mPersistentDeviceId);
            text = context.getString(R.string.app_permission_header_with_device_name,
                    mPermGroupLabel, deviceName);
        }
        mButtonCategory.setTitle(text);

        mFooterLink1.setSummary(context.getString(
                R.string.app_permission_footer_app_permissions_link, mPackageLabel));
        String caller = getArguments().getString(EXTRA_CALLER_NAME);
        setBottomLinkState(mFooterLink1, caller, Intent.ACTION_MANAGE_APP_PERMISSIONS);

        setBottomLinkState(mFooterLink2, caller, Intent.ACTION_MANAGE_PERMISSION_APPS);

        Set<String> exemptedPackages = Utils.getExemptedPackages(mRoleManager);
        if (exemptedPackages.contains(mPackageName)) {
            int additional_info_label = Utils.isStatusBarIndicatorPermission(mPermGroupName)
                    ? R.string.exempt_mic_camera_info_label : R.string.exempt_info_label;
            mAdditionalInfo.setSummary(context.getString(additional_info_label, mPackageLabel));
            mAdditionalInfo.setVisible(true);
        } else {
            mAdditionalInfo.setVisible(false);
        }

        if (mViewModel.getButtonStateLiveData().getValue() != null) {
            setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
        } else {
            mAllowButton.setVisible(false);
            mAllowAlwaysButton.setVisible(false);
            mAllowForegroundButton.setVisible(false);
            mAskOneTimeButton.setVisible(false);
            mAskButton.setVisible(false);
            mDenyButton.setVisible(false);
            mDenyForegroundButton.setVisible(false);
            mLocationAccuracySwitch.setVisible(false);
            mSelectButton.setVisible(false);
            mSelectButton.setExtraWidgetOnClickListener(null);
        }

        if (mViewModel.getFullStorageStateLiveData().isInitialized() && mIsStorageGroup) {
            setSpecialStorageState(mViewModel.getFullStorageStateLiveData().getValue());
        } else {
            mFooterStorageSpecialAppAccess.setVisible(false);
        }

        // Avoid an animation by showing this Preference immediately
        showPermissionRationaleDialog(mViewModel.getShowPermissionRationaleLiveData().getValue());

        getActivity().setTitle(
                getPreferenceManager().getContext().getString(R.string.app_permission_title,
                        mPermGroupLabel));

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    public void onResume() {
        super.onResume();
        // If we're returning to the fragment, photo picker hasn't been triggered
        mPhotoPickerTriggered = false;
    }

    private void showPermissionRationaleDialog(Boolean showPermissionRationale) {
        showPermissionRationaleDialog(showPermissionRationale == Boolean.TRUE);
    }

    private void showPermissionRationaleDialog(boolean showPermissionRationale) {
        if (!showPermissionRationale) {
            mAppPermissionRationaleContainer.setVisible(false);
        } else {
            mAppPermissionRationaleContainer.setVisible(true);
            mAppPermissionRationaleContent.setOnPreferenceClickListener((v) -> {
                if (SdkLevel.isAtLeastU()) {
                    mViewModel.showPermissionRationaleActivity(getActivity(), mPermGroupName);
                }
                return true;
            });
        }
    }

    private void setBottomLinkState(Preference preference, String caller, String action) {
        if ((Objects.equals(caller, AppPermissionGroupsFragment.class.getName())
                && action.equals(Intent.ACTION_MANAGE_APP_PERMISSIONS))
                || (Objects.equals(caller, PermissionAppsFragment.class.getName())
                && action.equals(Intent.ACTION_MANAGE_PERMISSION_APPS))) {
            preference.setVisible(false);
        } else {
            preference.setOnPreferenceClickListener((v) -> {
                Bundle args;
                if (action.equals(Intent.ACTION_MANAGE_APP_PERMISSIONS)) {
                    args = AppPermissionGroupsFragment.createArgs(mPackageName, mUser,
                            mSessionId, true);
                } else {
                    args = PermissionAppsFragment.createArgs(mPermGroupName, mSessionId);
                }
                mViewModel.showBottomLinkPage(this, action, args);
                return true;
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            pressBack(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setRadioButtonsState(Map<ButtonType, ButtonState> states) {
        if (states == null && !mViewModel.getButtonStateLiveData().isStale()) {
            pressBack(this);
            Log.w(LOG_TAG, "invalid package " + mPackageName + " or perm group "
                    + mPermGroupName);
            Toast.makeText(
                    getActivity(), R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            return;
        } else if (states == null) {
            return;
        }

        mAllowButton.setOnClickListener((v) -> {
            allowButtonFrameClickListener();
        });
        mAllowButton.setOnDisabledClickListener((v) -> {
            allowButtonFrameClickListener();
        });
        mAllowAlwaysButton.setOnClickListener((v) -> {
            markSingleButtonAsChecked(ButtonType.ALLOW_ALWAYS);
            if (mIsStorageGroup) {
                showConfirmDialog(ChangeRequest.GRANT_ALL_FILE_ACCESS,
                        R.string.special_file_access_dialog, -1, false);
            } else {
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_BOTH,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS);
            }
            setResult(GRANTED_ALWAYS);
        });
        mAllowForegroundButton.setOnClickListener((v) -> {
            markSingleButtonAsChecked(ButtonType.ALLOW_FOREGROUND);
            if (mIsStorageGroup) {
                mViewModel.setAllFilesAccess(false);
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_BOTH,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW);
                setResult(GRANTED_ALWAYS);
            } else {
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_FOREGROUND_ONLY,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND);
                setResult(GRANTED_FOREGROUND_ONLY);
            }
        });
        mAskOneTimeButton.setOnClickListener((v) -> {
            // mAskOneTimeButton only shows if checked hence should do nothing
            markSingleButtonAsChecked(ButtonType.ASK_ONCE);
        });
        mAskButton.setOnClickListener((v) -> {
            markSingleButtonAsChecked(ButtonType.ASK);
            mViewModel.requestChange(true, this, this, ChangeRequest.REVOKE_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ASK_EVERY_TIME);
            setResult(DENIED);
        });
        mSelectButton.setOnClickListener((v) -> {
            markSingleButtonAsChecked(ButtonType.SELECT_PHOTOS);
            int buttonPressed =
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__PHOTOS_SELECTED;
            mViewModel.requestChange(false, this, this, ChangeRequest.PHOTOS_SELECTED,
                    buttonPressed);
        });

        if (isButtonChecked(states, ButtonType.SELECT_PHOTOS)) {
            // Show the clickable extra widget
            // TODO(b/366269715): make the widget show/hide via animation
            mSelectButton.setExtraWidgetOnClickListener((v) -> {
                if (isButtonChecked(states, ButtonType.SELECT_PHOTOS) && !mPhotoPickerTriggered) {
                    mPhotoPickerTriggered = true;
                    mViewModel.openPhotoPicker(this);
                }
            });
        } else {
            // Hide the clickable extra widget
            mSelectButton.setExtraWidgetOnClickListener(null);
        }

        mDenyButton.setOnClickListener((v) -> {
            markSingleButtonAsChecked(ButtonType.DENY);
            if (mViewModel.getFullStorageStateLiveData().getValue() != null
                    && !mViewModel.getFullStorageStateLiveData().getValue().isLegacy()) {
                mViewModel.setAllFilesAccess(false);
            }
            mViewModel.requestChange(false, this, this, ChangeRequest.REVOKE_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY);
            setResult(DENIED_DO_NOT_ASK_AGAIN);
        });
        mDenyForegroundButton.setOnClickListener((v) -> {
            markSingleButtonAsChecked(ButtonType.DENY_FOREGROUND);
            mViewModel.requestChange(false, this, this, ChangeRequest.REVOKE_FOREGROUND,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY_FOREGROUND);
            setResult(DENIED_DO_NOT_ASK_AGAIN);
        });
        // Set long variable names to new variables to bypass linter errors.
        int grantFineLocation =
                APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__GRANT_FINE_LOCATION;
        int revokeFineLocation =
                APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__REVOKE_FINE_LOCATION;

        mLocationAccuracySwitch.setOnPreferenceChangeListener((pref, newValue) -> {
            if ((Boolean) newValue) {
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_FINE_LOCATION,
                        grantFineLocation);
            } else {
                mViewModel.requestChange(false, this, this, ChangeRequest.REVOKE_FINE_LOCATION,
                        revokeFineLocation);
            }
            // Don't actually toggle the switch yet. (Allow livedata observer to do so.)
            return false;
        });

        setButtonState(mAllowButton, states.get(ButtonType.ALLOW));
        setButtonState(mAllowAlwaysButton, states.get(ButtonType.ALLOW_ALWAYS));
        setButtonState(mAllowForegroundButton, states.get(ButtonType.ALLOW_FOREGROUND));
        setButtonState(mAskOneTimeButton, states.get(ButtonType.ASK_ONCE));
        setButtonState(mAskButton, states.get(ButtonType.ASK));
        setButtonState(mDenyButton, states.get(ButtonType.DENY));
        setButtonState(mDenyForegroundButton, states.get(ButtonType.DENY_FOREGROUND));
        setButtonState(mSelectButton, states.get(ButtonType.SELECT_PHOTOS));
        if (mSelectButton.isVisible()) {
            mAllowButton.setTitle(R.string.app_permission_button_always_allow_all);
        } else {
            mAllowButton.setTitle(R.string.app_permission_button_allow);
        }

        setButtonState(mLocationAccuracySwitch, states.get(ButtonType.LOCATION_ACCURACY));

        mIsInitialLoad = false;

        if (mViewModel.getFullStorageStateLiveData().isInitialized()) {
            setSpecialStorageState(mViewModel.getFullStorageStateLiveData().getValue());
        }
    }

    private void allowButtonFrameClickListener() {
        if (!mAllowButton.isEnabled()) {
            mViewModel.handleDisabledAllowButton(this);
        } else {
            markSingleButtonAsChecked(ButtonType.ALLOW);
            mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_FOREGROUND,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW);
            setResult(GRANTED_ALWAYS);
        }
    }

    private void setButtonState(TwoStatePreference button,
            AppPermissionViewModel.ButtonState state) {
        button.setVisible(state.isShown());
        button.setChecked(state.isChecked());
        button.setEnabled(state.isEnabled());
    }

    private boolean isButtonChecked(Map<ButtonType, ButtonState> state, ButtonType button) {
        return state.containsKey(button) && state.get(button).isChecked();
    }

    private void setSpecialStorageState(FullStoragePackageState storageState) {
        if (!mAllowButton.isVisible() || !mIsStorageGroup) {
            mFooterStorageSpecialAppAccess.setVisible(false);
            return;
        }

        mAllowAlwaysButton.setTitle(R.string.app_permission_button_allow_all_files);
        mAllowForegroundButton.setTitle(R.string.app_permission_button_allow_media_only);

        if (storageState == null) {
            mFooterStorageSpecialAppAccess.setVisible(false);
            return;
        }

        if (storageState.isLegacy()) {
            mAllowButton.setTitle(R.string.app_permission_button_allow_all_files);
            mFooterStorageSpecialAppAccess.setVisible(false);
            return;
        }

        mFooterStorageSpecialAppAccess.setVisible(true);
    }

    private void setResult(@GrantPermissionsViewHandler.Result int result) {
        if (!mPackageName.equals(
                getActivity().getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME))) {
            return;
        }
        Intent intent = new Intent()
                .putExtra(EXTRA_RESULT_PERMISSION_INTERACTED, mPermGroupName)
                .putExtra(EXTRA_RESULT_PERMISSION_RESULT, result);
        getActivity().setResult(RESULT_OK, intent);
    }

    private void setDetail(Pair<Integer, Integer> detailResIds) {
        if (detailResIds == null) {
            mDetails.setOnSecondTargetClickListener(null);
            return;
        }
        if (detailResIds.getSecond() != null) {
            // If the permissions are individually controlled, also show a link to the page that
            // lets you control them.
            mDetails.setExtraWidgetIconRes(R.drawable.ic_settings);
            Bundle args = AllAppPermissionsFragment.createArgs(mPackageName, mPermGroupName, mUser);
            mDetails.setOnSecondTargetClickListener((v) ->
                    mViewModel.showAllPermissions(this, args));
            mDetails.setSummary(getPreferenceManager().getContext().getString(
                    detailResIds.getFirst(), detailResIds.getSecond()));
        } else {
            mDetails.setOnSecondTargetClickListener(null);
            mDetails.setSummary(getPreferenceManager().getContext().getString(
                    detailResIds.getFirst()));
        }
        mDetails.setVisible(true);
    }

    private void setAdminSupportDetail(EnforcedAdmin admin) {
        if (admin != null) {
            mDetails.setExtraWidgetIconRes(R.drawable.ic_info_outline);
            mDetails.setOnSecondTargetClickListener((v) ->
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), admin));
        } else {
            mDetails.setOnSecondTargetClickListener(null);
        }
    }

    /**
     * Show a dialog that warns the users that they are about to revoke permissions that were
     * granted by default, or that they are about to grant full file access to an app.
     *
     *
     * The order of operation to revoke a permission granted by default is:
     * 1. `showConfirmDialog`
     * 1. [ConfirmDialog.onCreateDialog]
     * 1. [AppPermissionViewModel.onDenyAnyWay] or [AppPermissionViewModel.onConfirmFileAccess]
     * TODO: Remove once data can be passed between dialogs and fragments with nav component
     *
     * @param changeRequest Whether background or foreground should be changed
     * @param messageId     The Id of the string message to show
     * @param buttonPressed Button which was pressed to initiate the dialog, one of
     *                      AppPermissionFragmentActionReported.button_pressed constants
     * @param oneTime       Whether the one-time (ask) button was clicked rather than the deny
     *                      button
     */
    @Override
    public void showConfirmDialog(ChangeRequest changeRequest, @StringRes int messageId,
            int buttonPressed, boolean oneTime) {
        Bundle args = getArguments().deepCopy();
        args.putInt(ConfirmDialog.MSG, messageId);
        args.putSerializable(ConfirmDialog.CHANGE_REQUEST, changeRequest);
        args.putInt(ConfirmDialog.BUTTON, buttonPressed);
        args.putBoolean(ConfirmDialog.ONE_TIME, oneTime);
        ConfirmDialog defaultDenyDialog = new ConfirmDialog();
        defaultDenyDialog.setCancelable(true);
        defaultDenyDialog.setArguments(args);
        defaultDenyDialog.show(getChildFragmentManager().beginTransaction(),
                ConfirmDialog.class.getName());
    }

    private void markSingleButtonAsChecked(ButtonType buttonToMarkChecked) {
        if (!mViewModel.getButtonStateLiveData().isInitialized()) {
            return;
        }
        Map<ButtonType, ButtonState> currentStates = mViewModel.getButtonStateLiveData().getValue();
        Map<ButtonType, ButtonState> newStates = new ArrayMap<>();
        for (ButtonType button: currentStates.keySet()) {
            ButtonState state = currentStates.get(button);
            boolean isChecked = button == buttonToMarkChecked
                    // Don't uncheck the Location Accuracy switch, if it is checked
                    || (button == ButtonType.LOCATION_ACCURACY && state.isChecked());
            ButtonState newState = new ButtonState(isChecked, state.isEnabled(), state.isShown(),
                    state.getCustomRequest());
            newStates.put(button, newState);
        }
        setRadioButtonsState(newStates);
    }

    /**
     * A dialog warning the user that they are about to deny a permission that was granted by
     * default, or that they are denying a permission on a Pre-M app
     *
     * @see AppPermissionViewModel.ConfirmDialogShowingFragment#showConfirmDialog(ChangeRequest,
     * int, int, boolean)
     * @see #showConfirmDialog(ChangeRequest, int, int)
     */
    public static class ConfirmDialog extends DialogFragment {
        static final String MSG = ConfirmDialog.class.getName() + ".arg.msg";
        static final String CHANGE_REQUEST = ConfirmDialog.class.getName()
                + ".arg.changeRequest";
        private static final String KEY = ConfirmDialog.class.getName() + ".arg.key";
        private static final String BUTTON = ConfirmDialog.class.getName() + ".arg.button";
        private static final String ONE_TIME = ConfirmDialog.class.getName() + ".arg.onetime";
        private static int sCode =  APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW;
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // TODO(b/229024576): This code is duplicated, refactor ConfirmDialog for easier
            // NFF sharing
            AppPermissionFragment fragment = (AppPermissionFragment) getParentFragment();
            boolean isGrantFileAccess = getArguments().getSerializable(CHANGE_REQUEST)
                    == ChangeRequest.GRANT_ALL_FILE_ACCESS;
            int positiveButtonStringResId = R.string.grant_dialog_button_deny_anyway;
            if (isGrantFileAccess) {
                positiveButtonStringResId = R.string.grant_dialog_button_allow;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel,
                            (DialogInterface dialog, int which) -> dialog.cancel())
                    .setPositiveButton(positiveButtonStringResId,
                            (DialogInterface dialog, int which) -> {
                                if (isGrantFileAccess) {
                                    fragment.mViewModel.setAllFilesAccess(true);
                                    fragment.mViewModel.requestChange(false, fragment,
                                            fragment, ChangeRequest.GRANT_BOTH, sCode);
                                } else {
                                    fragment.mViewModel.onDenyAnyWay((ChangeRequest)
                                                    getArguments().getSerializable(CHANGE_REQUEST),
                                            getArguments().getInt(BUTTON),
                                            getArguments().getBoolean(ONE_TIME));
                                }
                            });
            Dialog d = b.create();
            d.setCanceledOnTouchOutside(true);
            return d;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            AppPermissionFragment fragment = (AppPermissionFragment) getParentFragment();
            fragment.setRadioButtonsState(fragment.mViewModel.getButtonStateLiveData().getValue());
        }
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void showAdvancedConfirmDialog(AdvancedConfirmDialogArgs args) {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                .setIcon(args.getIconId())
                .setMessage(args.getMessageId())
                .setOnCancelListener((DialogInterface dialog) -> {
                    setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
                })
                .setNegativeButton(args.getNegativeButtonTextId(),
                        (DialogInterface dialog, int which) -> {
                            setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
                        })
                .setPositiveButton(args.getPositiveButtonTextId(),
                        (DialogInterface dialog, int which) -> {
                            mViewModel.requestChange(args.getSetOneTime(),
                                    AppPermissionFragment.this, AppPermissionFragment.this,
                                    args.getChangeRequest(), args.getButtonClicked());
                        });
        if (args.getTitleId() != 0) {
            b.setTitle(args.getTitleId());
        }
        b.show();
    }
}
// LINT.ThenChange(../max35/LegacyAppPermissionFragment.java)
