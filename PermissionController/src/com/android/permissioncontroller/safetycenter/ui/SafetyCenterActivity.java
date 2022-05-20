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
package com.android.permissioncontroller.safetycenter.ui;

import static android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.safetycenter.SafetyCenterManager;
import android.util.Log;

import androidx.annotation.Keep;

import com.android.permissioncontroller.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/** Entry-point activity for SafetyCenter. */
@Keep
public final class SafetyCenterActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG = SafetyCenterActivity.class.getSimpleName();
    private SafetyCenterManager mSafetyCenterManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSafetyCenterManager = getSystemService(SafetyCenterManager.class);

        if (maybeRedirectIfDisabled()) return;

        setTitle(getString(R.string.safety_center_dashboard_page_title));
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.content_frame, new SafetyCenterDashboardFragment())
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
}
