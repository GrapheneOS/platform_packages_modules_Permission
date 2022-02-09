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

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.R;

import java.util.ArrayList;
import java.util.List;

/** Dashboard fragment for the Safety Center **/
public final class SafetyCenterDashboardFragment extends PreferenceFragmentCompat {

    private SafetyCenterContentManager mSafetyCenterContentManager;
    private List<Preference> mSafetyEntryPreferences = new ArrayList<Preference>();
    private SafetyStatusPreference mSafetyStatusPreference;
    private PreferenceGroup mIssuesGroup;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.safety_center_dashboard, rootKey);
        mSafetyCenterContentManager = SafetyCenterContentManager.getInstance();
        updateSafetyEntries();
        mSafetyStatusPreference = getPreferenceScreen().findPreference("safety_status_wheel");

        // TODO(b/206775474): Replace this with fetching real data from SafetyCenterContentManager.
        mSafetyStatusPreference.setSafetyStatus(
                new OverallSafetyStatus(OverallSafetyStatus.Level.RECOMMENDATION,
                        "Something's wrong", "Can you please fix it?"));

        mIssuesGroup = getPreferenceScreen().findPreference("issues_group");
    }

    // TODO(b/208212820): Add groups and move to separate controller
    private void updateSafetyEntries() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();

        // TODO(b/208212820): Only update entries that have changed since last update, rather
        // than deleting and re-adding all.
        mSafetyEntryPreferences.forEach(preferenceScreen::removePreference);
        mSafetyEntryPreferences.clear();

        List<SafetyEntry> entries = mSafetyCenterContentManager.getSafetyEntries();
        entries.forEach(entry -> {
            Preference pref = new Preference(getContext());
            pref.setTitle(entry.getTitle());
            pref.setSummary(entry.getSummary());
            pref.setIcon(entry.getSeverityLevel().getEntryIconResId());
            pref.setOrder(entry.getOrder());
            mSafetyEntryPreferences.add(pref);
            preferenceScreen.addPreference(pref);
        });
    }
}
