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

package com.android.permissioncontroller.permission.ui.handheld;

import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MICROPHONE;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.model.SafetyHubViewModel;
import com.android.permissioncontroller.permission.ui.model.SafetyHubViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;

import java.util.Map;

/**
 * The Quick Settings fragment for the safety hub. Displays information to the user about the
 * current safety and privacy status of their device, including showing mic/camera usage, and having
 * mic/camera/location toggles.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class SafetyHubQSFragment extends Fragment {
    private static ArrayMap<String, Integer> sToggleButtons = new ArrayMap<>();

    private long mSessionId;
    private SafetyHubViewModel mViewModel;

    static {
        sToggleButtons.put(CAMERA, R.id.camera_toggle);
        sToggleButtons.put(MICROPHONE, R.id.mic_toggle);
        sToggleButtons.put(LOCATION, R.id.location_toggle);
    }

    /**
     * Create arguments for this package
     * @param sessionId The current session Id
     * @return A bundle with the required arguments
     */
    public static Bundle createArgs(long sessionId) {
        Bundle args = new Bundle();
        args.putLong(EXTRA_SESSION_ID, sessionId);
        return args;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSessionId = INVALID_SESSION_ID;
        if (getArguments() != null) {
            mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        }

        getActivity().setTheme(R.style.SafetyHub);

        SafetyHubViewModelFactory factory = new SafetyHubViewModelFactory(
                getActivity().getApplication(), mSessionId);
        mViewModel = new ViewModelProvider(this, factory).get(SafetyHubViewModel.class);
        mViewModel.getSensorPrivacyLiveData()
                .observe(this, (v) -> setSensorToggleState(v, getView()));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.safety_hub_qs, container, false);
        root.findViewById(R.id.security_settings_button).setOnClickListener(
                (v) -> mViewModel.navigateToSecuritySettings(this));
        setSensorToggleState(new ArrayMap<>(), root);
        root.findViewById(R.id.quit).setOnClickListener(
                (v) -> getActivity().finish());
        return root;
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
            groupLabel.setText(KotlinUtils.INSTANCE.getPermGroupLabel(getContext(), groupName));
            TextView blockedStatus = toggle.findViewById(R.id.toggle_sensor_status);
            ImageView iconView = toggle.findViewById(R.id.toggle_sensor_icon);
            boolean sensorEnabled =
                    !sensorState.containsKey(groupName) || sensorState.get(groupName);

            Drawable icon;
            if (sensorEnabled) {
                blockedStatus.setText(R.string.available);
                toggle.setBackgroundResource(R.drawable.safety_hub_button_background);
                icon = KotlinUtils.INSTANCE.getPermGroupIcon(getContext(), groupName, Color.BLACK);
                groupLabel.setTextColor(Color.BLACK);
            } else {
                blockedStatus.setText(R.string.blocked);
                toggle.setBackgroundResource(R.drawable.safety_hub_button_background_dark);
                icon = getContext().getDrawable(getBlockedIconResId(groupName));
                icon.setTint(Color.LTGRAY);
                groupLabel.setTextColor(Color.LTGRAY);
            }
            iconView.setImageDrawable(icon);
        }
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
