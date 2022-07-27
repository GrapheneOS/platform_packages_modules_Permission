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

import static android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_CLICKED;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.safetycenter.SafetyCenterManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/** Entry-point activity for SafetyCenter. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG = SafetyCenterActivity.class.getSimpleName();
    private static final String PRIVACY_CONTROLS_ACTION = "android.settings.PRIVACY_CONTROLS";
    private SafetyCenterManager mSafetyCenterManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSafetyCenterManager = getSystemService(SafetyCenterManager.class);

        if (maybeRedirectIfDisabled()) return;

        PreferenceFragmentCompat frag;
        if (getIntent().getAction().equals(PRIVACY_CONTROLS_ACTION)) {
            setTitle(R.string.privacy_controls_title);
            frag = PrivacyControlsFragment.newInstance();
        } else {
            logPrivacySourceMetric();
            setTitle(getString(R.string.safety_center_dashboard_page_title));
            frag = SafetyCenterDashboardFragment.newInstance(
                    Utils.getOrGenerateSessionId(getIntent()),
                    /* isQuickSettingsFragment= */ false);
        }
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.content_frame, frag)
                    .commitNow();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        maybeRedirectIfDisabled();
    }

    private boolean maybeRedirectIfDisabled() {
        if (mSafetyCenterManager == null || !mSafetyCenterManager.isSafetyCenterEnabled()) {
            Log.w(TAG, "Safety Center disabled, redirecting to settings page");
            startActivity(
                    new Intent(Settings.ACTION_SETTINGS).addFlags(FLAG_ACTIVITY_FORWARD_RESULT));
            finish();
            return true;
        }
        return false;
    }

    private void logPrivacySourceMetric() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(Constants.EXTRA_PRIVACY_SOURCE)) {
            int privacySource = intent.getIntExtra(Constants.EXTRA_PRIVACY_SOURCE, -1);
            int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
            long sessionId =
                    intent.getLongExtra(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID);
            Log.v(TAG, "privacy source notification metric, source " + privacySource + " uid "
                    + uid + " sessionId " + sessionId);
            PermissionControllerStatsLog.write(
                    PRIVACY_SIGNAL_NOTIFICATION_INTERACTION,
                    privacySource,
                    uid,
                    PRIVACY_SIGNAL_NOTIFICATION_INTERACTION__ACTION__NOTIFICATION_CLICKED,
                    sessionId);
        }
    }
}
