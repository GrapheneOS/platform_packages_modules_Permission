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

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModel;
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModel.Pref;
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModel.PrefState;
import com.android.permissioncontroller.safetycenter.ui.model.PrivacyControlsViewModelFactory;

import java.util.Map;

/**
 * Fragment that shows several privacy toggle controls, alongside a link to location settings
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public final class PrivacyControlsFragment extends PreferenceFragmentCompat {

    /** Create a new instance of this fragment */
    public static PrivacyControlsFragment newInstance() {
        return new PrivacyControlsFragment();
    }

    private PrivacyControlsViewModel mViewModel;

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.layout.privacy_controls, rootKey);

        PrivacyControlsViewModelFactory factory = new PrivacyControlsViewModelFactory(
                getActivity().getApplication());
        mViewModel = new ViewModelProvider(this, factory).get(PrivacyControlsViewModel.class);
        mViewModel.getControlStateLiveData().observe(this, this::setPreferences);
    }

    private void setPreferences(Map<Pref, PrefState> prefStates) {
        setSwitchPreference(prefStates.get(Pref.MIC), Pref.MIC);
        setSwitchPreference(prefStates.get(Pref.CAMERA), Pref.CAMERA);
        setSwitchPreference(prefStates.get(Pref.CLIPBOARD), Pref.CLIPBOARD);
        findPreference(Pref.LOCATION.getKey()).setOnPreferenceClickListener((v) -> {
            mViewModel.handlePrefClick(this, Pref.LOCATION, null);
            return true;
        });
    }

    private void setSwitchPreference(PrefState prefState, Pref prefType) {
        ClickableDisabledSwitchPreference preference = findPreference(prefType.getKey());

        preference.setVisible(prefState.getVisible());
        preference.setChecked(prefState.getChecked());
        preference.setAppearDisabled(prefState.getAdmin() != null);
        preference.setOnPreferenceClickListener((v) -> {
            mViewModel.handlePrefClick(this, prefType, prefState.getAdmin());
            return true;
        });
        if (prefState.getAdmin() != null && prefState.getChecked()) {
            preference.setSummary(R.string.enabled_by_admin);
        } else if (prefState.getAdmin() != null) {
            preference.setSummary(R.string.disabled_by_admin);
        } else if (prefType.equals(Pref.MIC)) {
            preference.setSummary(R.string.mic_toggle_description);
        } else if (prefType.equals(Pref.CAMERA)) {
            preference.setSummary(R.string.perm_toggle_description);
        }
    }
}
