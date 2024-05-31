/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.permissioncontroller.permission.utils;

import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.location.LocationManager.EXTRA_ADAS_GNSS_ENABLED;
import static android.location.LocationManager.EXTRA_LOCATION_ENABLED;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.permission.flags.Flags;
import com.android.permissioncontroller.PermissionControllerApplication;
import com.android.permissioncontroller.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LocationUtils {

    public static final String LOCATION_PERMISSION = Manifest.permission_group.LOCATION;
    public static final String ACTIVITY_RECOGNITION_PERMISSION =
            Manifest.permission_group.ACTIVITY_RECOGNITION;

    private static final String TAG = LocationUtils.class.getSimpleName();
    private static final long LOCATION_UPDATE_DELAY_MS = 1000;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public static void showLocationDialog(final Context context, CharSequence label) {
        new AlertDialog.Builder(context)
                .setIcon(R.drawable.ic_dialog_alert_material)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(context.getString(R.string.location_warning, label))
                .setNegativeButton(R.string.ok, null)
                .setPositiveButton(R.string.location_settings, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        context.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .show();
    }

    /** Start the settings page for the location controller extra package. */
    public static void startLocationControllerExtraPackageSettings(@NonNull Context context,
            @NonNull UserHandle user) {
        try {
            context.startActivityAsUser(new Intent(
                        Settings.ACTION_LOCATION_CONTROLLER_EXTRA_PACKAGE_SETTINGS), user);
        } catch (ActivityNotFoundException e) {
            // In rare cases where location controller extra package is set, but
            // no activity exists to handle the location controller extra package settings
            // intent, log an error instead of crashing permission controller.
            Log.e(TAG, "No activity to handle "
                        + "android.settings.LOCATION_CONTROLLER_EXTRA_PACKAGE_SETTINGS");
        }
    }

    public static boolean isLocationEnabled(Context context) {
        return context.getSystemService(LocationManager.class).isLocationEnabled();
    }

    /** Checks if the automotive location bypass is enabled. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static boolean isAutomotiveLocationBypassEnabled(Context context) {
        return context.getSystemService(LocationManager.class).isAdasGnssLocationEnabled();
    }

    /** Return the automotive location bypass allowlist. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static Collection<String> getAutomotiveLocationBypassAllowlist(Context context) {
        // TODO(b/335763768): Remove reflection once getAdasAllowlist() is a System API
        try {
            LocationManager locationManager = context.getSystemService(LocationManager.class);
            Object packageTagsList =
                    LocationManager.class.getMethod("getAdasAllowlist").invoke(locationManager);
            return (Collection<String>) packageTagsList.getClass().getMethod("getPackages")
                    .invoke(packageTagsList);
        } catch (Exception e) {
            Log.e(TAG, "Cannot get location bypass allowlist: " + e);
            return new ArrayList<String>();
        }
    }

    /** Checks if the provided package is an automotive location bypass allowlisted package. */
    public static boolean isAutomotiveLocationBypassAllowlistedPackage(
            Context context, String packageName) {
        return SdkLevel.isAtLeastV() && Flags.addBannersToPrivacySensitiveAppsForAaos()
                && getAutomotiveLocationBypassAllowlist(context).contains(packageName);
    }

    /** Checks if the provided package is a location provider. */
    public static boolean isLocationProvider(Context context, String packageName) {
        return context.getSystemService(LocationManager.class).isProviderPackage(packageName);
    }

    public static boolean isLocationGroupAndProvider(Context context, String groupName,
            String packageName) {
        return LOCATION_PERMISSION.equals(groupName) && isLocationProvider(context, packageName);
    }

    public static boolean isLocationGroupAndControllerExtraPackage(@NonNull Context context,
            @NonNull String groupName, @NonNull String packageName) {
        return (LOCATION_PERMISSION.equals(groupName)
            || ACTIVITY_RECOGNITION_PERMISSION.equals(groupName))
                && packageName.equals(context.getSystemService(LocationManager.class)
                        .getExtraLocationControllerPackage());
    }

    /** Returns whether the location controller extra package is enabled. */
    public static boolean isExtraLocationControllerPackageEnabled(Context context) {
        try {
            return context.getSystemService(LocationManager.class)
                    .isExtraLocationControllerPackageEnabled();
        } catch (Exception e) {
            return false;
        }

    }

    /**
     * A Listener which responds to enabling or disabling of location on the device
     */
    public interface LocationListener {

        /**
         * A callback run any time we receive a broadcast stating the location enable state has
         * changed.
         * @param enabled Whether or not location is enabled
         */
        void onLocationStateChange(boolean enabled);
    }

    /**
     * Add a location listener, which will be notified if the automotive location bypass state is
     * enabled or disabled.
     * @param listener the listener to add
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static void addAutomotiveLocationBypassListener(LocationListener listener) {
        addLocationListener(listener, sAutomotiveLocationBypassListeners,
                sAutomotiveLocationBypassBroadcastReceiver,
                LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED);
    }

    /**
     * Add a location listener, which will be notified if the main location state is enabled or
     * disabled.
     * @param listener the listener to add
     */
    public static void addLocationListener(LocationListener listener) {
        addLocationListener(listener, sLocationListeners, sLocationBroadcastReceiver,
                LocationManager.MODE_CHANGED_ACTION);
    }

    /**
     * Remove an automotive location bypass listener
     * @param listener The listener to remove
     *
     * @return True if it was successfully removed, false otherwise
     */
    public static boolean removeAutomotiveLocationBypassListener(LocationListener listener) {
        return removeLocationListener(listener, sAutomotiveLocationBypassListeners,
                sAutomotiveLocationBypassBroadcastReceiver);
    }

    /**
     * Remove a main location listener
     * @param listener The listener to remove
     *
     * @return True if it was successfully removed, false otherwise
     */
    public static boolean removeLocationListener(LocationListener listener) {
        return removeLocationListener(listener, sLocationListeners, sLocationBroadcastReceiver);
    }

    private static final List<LocationListener> sAutomotiveLocationBypassListeners =
            new ArrayList<>();
    private static final List<LocationListener> sLocationListeners = new ArrayList<>();

    private static final BroadcastReceiver sAutomotiveLocationBypassBroadcastReceiver =
            getLocationBroadcastReceiver(
                    SdkLevel.isAtLeastT() ? EXTRA_ADAS_GNSS_ENABLED : EXTRA_LOCATION_ENABLED,
                    sAutomotiveLocationBypassListeners);
    private static final BroadcastReceiver sLocationBroadcastReceiver =
            getLocationBroadcastReceiver(EXTRA_LOCATION_ENABLED, sLocationListeners);

    private static BroadcastReceiver getLocationBroadcastReceiver(String locationIntentExtra,
            List<LocationListener> locationListeners) {
        return new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    boolean isEnabled = intent.getBooleanExtra(locationIntentExtra, true);
                    sMainHandler.postDelayed(() -> {
                        synchronized (locationListeners) {
                            for (LocationListener l : locationListeners) {
                                l.onLocationStateChange(isEnabled);
                            }
                        }
                    }, LOCATION_UPDATE_DELAY_MS);
                }
            };
    }

    private static void addLocationListener(LocationListener listener,
            List<LocationListener> locationListeners, BroadcastReceiver locationBroadcastReceiver,
            String intentAction) {
        synchronized (locationListeners) {
            boolean wasEmpty = locationListeners.isEmpty();
            locationListeners.add(listener);
            if (wasEmpty) {
                IntentFilter intentFilter = new IntentFilter(intentAction);
                if (SdkLevel.isAtLeastU()) {
                    PermissionControllerApplication.get().getApplicationContext()
                            .registerReceiverForAllUsers(locationBroadcastReceiver, intentFilter,
                                    null, null, RECEIVER_NOT_EXPORTED);
                } else {
                    PermissionControllerApplication.get().getApplicationContext()
                            .registerReceiverForAllUsers(locationBroadcastReceiver, intentFilter,
                                    null, null);
                }
            }
        }
    }

    private static boolean removeLocationListener(LocationListener listener,
            List<LocationListener> locationListeners, BroadcastReceiver locationBroadcastReceiver) {
        synchronized (locationListeners) {
            boolean success = locationListeners.remove(listener);
            if (success && locationListeners.isEmpty()) {
                PermissionControllerApplication.get().getApplicationContext()
                        .unregisterReceiver(locationBroadcastReceiver);
            }
            return success;
        }
    }
}
