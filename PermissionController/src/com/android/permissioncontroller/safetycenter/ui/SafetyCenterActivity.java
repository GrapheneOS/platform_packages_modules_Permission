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

import static android.content.Intent.ACTION_SAFETY_CENTER;
import static android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.safetycenter.SafetyCenterManager.EXTRA_SAFETY_SOURCES_GROUP_ID;

import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_CLICKED;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.EXTRA_SETTINGS_FRAGMENT_ARGS_KEY;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.PERSONAL_PROFILE_SUFFIX;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.PRIVACY_SOURCES_GROUP_ID;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.PRIVATE_PROFILE_SUFFIX;
import static com.android.permissioncontroller.safetycenter.SafetyCenterConstants.WORK_PROFILE_SUFFIX;

import static java.util.Objects.requireNonNull;

import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.flags.Flags;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModel.Pref;
import com.android.settingslib.activityembedding.ActivityEmbeddingUtils;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.widget.ExpressiveDesignEnabledProvider;
import com.android.settingslib.widget.SettingsThemeHelper;

import java.util.List;
import java.util.Objects;

/** Entry-point activity for SafetyCenter. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterActivity extends CollapsingToolbarBaseActivity
        implements ExpressiveDesignEnabledProvider {

    private static final String TAG = SafetyCenterActivity.class.getSimpleName();
    private static final String PRIVACY_CONTROLS_ACTION = "android.settings.PRIVACY_CONTROLS";
    private static final String MENU_KEY_SAFETY_CENTER = "top_level_safety_center";
    private static final String EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI =
            "android.provider.extra.SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI";
    private static final String EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY =
            "android.provider.extra.SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY";
    private static final String EXTRA_PREVENT_TRAMPOLINE_TO_SETTINGS =
            "com.android.permissioncontroller.safetycenter.extra.PREVENT_TRAMPOLINE_TO_SETTINGS";

    /** The intent action that is triggered when quick settings tile is long-pressed. */
    public static final String ACTION_QS_TILE_PREFERENCES =
            "android.service.quicksettings.action.QS_TILE_PREFERENCES";

    @Nullable private SafetyCenterManager mSafetyCenterManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSafetyCenterManager = getSystemService(SafetyCenterManager.class);

        if (maybeRedirectIfDisabled()) {
            return;
        }

        if (isSettingsUiRedirectionSupported() && maybeRedirectToSettingsUi()) {
            return;
        }

        if (maybeRedirectIntoTwoPaneSettings()) {
            return;
        }

        Fragment frag;
        Intent intent = getIntent();
        final boolean maybeOpenSubpage =
                SafetyCenterUiFlags.getShowSubpages()
                        && Objects.equals(intent.getAction(), ACTION_SAFETY_CENTER);
        if (maybeOpenSubpage && intent.hasExtra(EXTRA_SAFETY_SOURCES_GROUP_ID)) {
            String groupId = intent.getStringExtra(EXTRA_SAFETY_SOURCES_GROUP_ID);
            frag = openRelevantSubpage(groupId);
        } else if (maybeOpenSubpage && intent.hasExtra(EXTRA_SETTINGS_FRAGMENT_ARGS_KEY)) {
            String preferenceKey = intent.getStringExtra(EXTRA_SETTINGS_FRAGMENT_ARGS_KEY);
            String groupId = getParentGroupId(preferenceKey);
            frag = openRelevantSubpage(groupId);
        } else if (Objects.equals(intent.getAction(), PRIVACY_CONTROLS_ACTION)) {
            setTitle(R.string.privacy_controls_title);
            frag = PrivacyControlsFragment.newInstance();
        } else {
            frag = openHomepage();
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(com.android.settingslib.collapsingtoolbar.R.id.content_frame, frag)
                    .commitNow();
        }

        configureHomeButton();

        frag.getLifecycle()
                .addObserver(
                        new LifecycleEventObserver() {
                            @Override
                            public void onStateChanged(
                                    LifecycleOwner unused, Lifecycle.Event event) {
                                if (event != Lifecycle.Event.ON_START) {
                                    return;
                                }
                                View listView = getListView(frag);
                                if (listView == null) {
                                    return;
                                }
                                int paddingBottom = listView.getPaddingBottom();
                                ViewCompat.setOnApplyWindowInsetsListener(
                                        listView,
                                        (v, windowInsets) -> {
                                            Insets insets =
                                                    windowInsets.getInsets(
                                                            WindowInsetsCompat.Type.systemBars());
                                            v.setPadding(
                                                    v.getPaddingLeft(),
                                                    v.getPaddingTop(),
                                                    v.getPaddingRight(),
                                                    paddingBottom + insets.bottom);
                                            return WindowInsetsCompat.CONSUMED;
                                        });
                            }
                        });
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        theme.applyStyle(R.style.ThemeOverlay_SafetyCenterColors_DayNight, /* force= */ true);

        if (SettingsThemeHelper.isExpressiveTheme(this)) {
            theme.applyStyle(R.style.ThemeOverlay_SafetyCenter_Expressive, /* force= */ true);
            theme.applyStyle(
                    R.style.ThemeOverlay_SafetyCenter_ExpressiveButtons, /* force= */ true);
            theme.applyStyle(R.style.ThemeOverlay_SafetyCenter_ExpressiveColors, /* force= */ true);
        }
        return theme;
    }

    @Override
    protected void onStart() {
        super.onStart();
        maybeRedirectIfDisabled();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // We don't set configChanges, but small screen size changes may still be delivered here.
        super.onConfigurationChanged(newConfig);
        configureHomeButton();
    }

    /** Decide whether a home/back button should be shown or not. */
    private void configureHomeButton() {
        ActionBar actionBar = getActionBar();
        Fragment frag =
                getSupportFragmentManager()
                        .findFragmentById(
                                com.android.settingslib.collapsingtoolbar.R.id.content_frame);
        if (actionBar == null || frag == null) {
            return;
        }

        // Only the homepage can be considered a "second layer" page as it's the only one that
        // can be reached from the Settings menu. The other pages are only reachable using
        // a direct intent (e.g. notification, "first layer") and/or by navigating within Safety
        // Center ("third layer").
        // Note that the homepage can also be a "first layer" page, but that would only happen
        // if the activity is not embedded.
        boolean isSecondLayerPage = frag instanceof SafetyCenterScrollWrapperFragment;
        if (ActivityEmbeddingUtils.shouldHideNavigateUpButton(this, isSecondLayerPage)) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setHomeButtonEnabled(false);
        }
    }

    private boolean maybeRedirectIfDisabled() {
        if (mSafetyCenterManager == null || !mSafetyCenterManager.isSafetyCenterEnabled()) {
            Log.w(TAG, "Safety Center disabled, redirecting to settings page");
            startActivity(
                    new Intent(getActionToRedirectWhenDisabled())
                            .addFlags(FLAG_ACTIVITY_FORWARD_RESULT));
            finish();
            return true;
        }
        return false;
    }

    private String getActionToRedirectWhenDisabled() {
        boolean isPrivacyControls =
                TextUtils.equals(getIntent().getAction(), PRIVACY_CONTROLS_ACTION);
        if (isPrivacyControls) {
            return Settings.ACTION_PRIVACY_SETTINGS;
        }
        return Settings.ACTION_SETTINGS;
    }

    private boolean maybeRedirectToSettingsUi() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (action == null) {
            return false;
        }

        switch (action) {
            case ACTION_SAFETY_CENTER:
            case PRIVACY_CONTROLS_ACTION:
                ComponentName resolvedComponent = resolveComponent(action);
                if (resolvedComponent != null && !resolvedComponent.equals(getComponentName())) {
                    Log.i(
                            TAG,
                            "Redirecting away from SafetyCenterActivity in PermissionController"
                                    + " for: "
                                    + action);
                    startActivity(makeRedirectIntent(intent, resolvedComponent));
                    finishAndRemoveTask();
                    return true;
                }
                break;

            case ACTION_QS_TILE_PREFERENCES:
                if (!isHandledByThisActivity(ACTION_SAFETY_CENTER)) {
                    Log.i(
                            TAG,
                            "Redirecting away from SafetyCenterActivity in PermissionController"
                                    + " for: "
                                    + action);
                    startActivity(
                            new Intent(ACTION_SAFETY_CENTER).addFlags(FLAG_ACTIVITY_NEW_TASK));
                    finishAndRemoveTask();
                    return true;
                }
                break;

            default:
                return false;
        }

        return false;
    }

    /**
     * Builds the intent that forwards the request to the resolved component.
     *
     * <p>Only the action and the extras of the incoming intent are carried over, and the target
     * is the explicit component resolved for the action, so the data, clip data, grant flags and
     * selector of the incoming intent are not relaunched with this app's identity.
     */
    private static Intent makeRedirectIntent(Intent intent, ComponentName component) {
        return new Intent(intent.getAction())
                .setComponent(component)
                .putExtras(intent)
                .addFlags(FLAG_ACTIVITY_FORWARD_RESULT | FLAG_ACTIVITY_NEW_TASK);
    }

    private boolean isHandledByThisActivity(String action) {
        return getComponentName().equals(resolveComponent(action));
    }

    /** Resolves the activity that handles the given action. */
    @Nullable
    private ComponentName resolveComponent(String action) {
        return new Intent(action).resolveActivity(getPackageManager());
    }

    /** Whether to redirect to the new Safety Center UI in Settings. */
    private boolean isSettingsUiRedirectionSupported() {
        // This is a mainline flag which effect we want to limit to new SDKs.
        return Flags.supportNewSafetyCenterUiInSettings()
                && VERSION.SDK_INT > VERSION_CODES.BAKLAVA;
    }

    private boolean maybeRedirectIntoTwoPaneSettings() {
        return shouldUseTwoPaneSettings() && tryRedirectTwoPaneSettings();
    }

    private boolean shouldUseTwoPaneSettings() {
        if (!ActivityEmbeddingUtils.isEmbeddingActivityEnabled(this)) {
            return false;
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getBoolean(EXTRA_PREVENT_TRAMPOLINE_TO_SETTINGS, false)) {
            return false;
        }

        return isTaskRoot() && !ActivityEmbeddingUtils.isActivityEmbedded(this);
    }

    /** Return {@code true} if the redirection was attempted. */
    private boolean tryRedirectTwoPaneSettings() {
        Intent twoPaneIntent = getTwoPaneIntent();
        if (twoPaneIntent == null) {
            return false;
        }

        Log.i(TAG, "Safety Center restarting in Settings two-pane layout");
        startActivity(twoPaneIntent);
        finishAndRemoveTask();
        return true;
    }

    @Nullable
    private Intent getTwoPaneIntent() {
        Intent twoPaneIntent = ActivityEmbeddingUtils.buildEmbeddingActivityBaseIntent(this);
        if (twoPaneIntent == null) {
            return null;
        }

        twoPaneIntent.putExtras(getIntent());
        twoPaneIntent.putExtra(
                EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI,
                getIntent().toUri(Intent.URI_INTENT_SCHEME));
        twoPaneIntent.putExtra(
                EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_HIGHLIGHT_MENU_KEY, MENU_KEY_SAFETY_CENTER);
        return twoPaneIntent;
    }

    private void logPrivacySourceMetric() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(Constants.EXTRA_PRIVACY_SOURCE)) {
            int privacySource = intent.getIntExtra(Constants.EXTRA_PRIVACY_SOURCE, -1);
            int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
            long sessionId =
                    intent.getLongExtra(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID);
            Log.i(
                    TAG,
                    "privacy source notification metric, source "
                            + privacySource
                            + " uid "
                            + uid
                            + " sessionId "
                            + sessionId);
            PermissionControllerStatsLog.write(
                    PRIVACY_SIGNAL_NOTIFICATION_INTERACTION,
                    privacySource,
                    uid,
                    PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_CLICKED,
                    sessionId);
        }
    }

    private Fragment openHomepage() {
        logPrivacySourceMetric();
        setTitle(getString(R.string.safety_center_dashboard_page_title));
        return new SafetyCenterScrollWrapperFragment();
    }

    @RequiresApi(UPSIDE_DOWN_CAKE)
    private Fragment openRelevantSubpage(String groupId) {
        if (groupId.isEmpty()) {
            return openHomepage();
        }

        long sessionId = Utils.getOrGenerateSessionId(getIntent());
        if (Objects.equals(groupId, PRIVACY_SOURCES_GROUP_ID)) {
            logPrivacySourceMetric();
            return PrivacySubpageFragment.newInstance(sessionId);
        }

        return SafetyCenterSubpageFragment.newInstance(sessionId, groupId);
    }

    @RequiresApi(UPSIDE_DOWN_CAKE)
    private String getParentGroupId(String preferenceKey) {
        if (Pref.findByKey(preferenceKey) != null) {
            return PRIVACY_SOURCES_GROUP_ID;
        }

        SafetyCenterConfig safetyCenterConfig =
                requireNonNull(mSafetyCenterManager).getSafetyCenterConfig();
        String[] splitKey;
        if (preferenceKey.endsWith(PERSONAL_PROFILE_SUFFIX)) {
            splitKey = preferenceKey.split("_" + PERSONAL_PROFILE_SUFFIX);
        } else if (preferenceKey.endsWith(WORK_PROFILE_SUFFIX)) {
            splitKey = preferenceKey.split("_" + WORK_PROFILE_SUFFIX);
        } else if (preferenceKey.endsWith(PRIVATE_PROFILE_SUFFIX)) {
            splitKey = preferenceKey.split("_" + PRIVATE_PROFILE_SUFFIX);
        } else {
            return "";
        }

        if (safetyCenterConfig == null || splitKey.length == 0) {
            return "";
        }

        List<SafetySourcesGroup> groups = safetyCenterConfig.getSafetySourcesGroups();
        for (SafetySourcesGroup group : groups) {
            if (group.getType() != SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_STATEFUL) {
                // Hidden and static groups are not opened in a subpage.
                continue;
            }
            for (SafetySource source : group.getSafetySources()) {
                if (Objects.equals(source.getId(), splitKey[0])) {
                    return group.getId();
                }
            }
        }
        return "";
    }

    @Nullable
    private View getListView(Fragment fragment) {
        if (fragment instanceof PreferenceFragmentCompat) {
            return ((PreferenceFragmentCompat) fragment).getListView();
        }
        if (fragment instanceof SafetyCenterScrollWrapperFragment) {
            Fragment dashboardFragment =
                    fragment.getChildFragmentManager().findFragmentById(R.id.fragment_container);
            if (dashboardFragment instanceof PreferenceFragmentCompat) {
                return ((PreferenceFragmentCompat) dashboardFragment).getListView();
            }
        }
        return null;
    }

    @Override
    public boolean isExpressiveDesignEnabled() {
        return SettingsThemeHelper.isExpressiveDesignEnabled()
                && getResources().getBoolean(R.bool.config_enableExpressiveDesignInSafetyCenter);
    }
}
