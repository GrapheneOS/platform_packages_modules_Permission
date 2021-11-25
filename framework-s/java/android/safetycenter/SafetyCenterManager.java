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

package android.safetycenter;

import static android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

/**
 * Interface for communicating with the safety center.
 *
 * @hide
 */
@SystemService(Context.SAFETY_CENTER_SERVICE)
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterManager {

    @NonNull
    private final Context mContext;
    @NonNull
    private final ISafetyCenterManager mService;

    /**
     * Creates a new instance of the {@link SafetyCenterManager}.
     *
     * @param context the {@link Context}
     * @param service the {@link ISafetyCenterManager} service
     * @hide
     */
    public SafetyCenterManager(@NonNull Context context, @NonNull ISafetyCenterManager service) {
        this.mContext = context;
        this.mService = service;
    }

    /**
     * Sends a {@link SafetySourceData} update to the safety center.
     *
     * <p>Each {@link SafetySourceData#getId()} uniquely identifies the {@link SafetySourceData} for
     * the current package and user.
     *
     * <p>This call will override any existing {@link SafetySourceData} already present for the
     * given {@link SafetySourceData#getId()} for the current package and user.
     */
    @RequiresPermission(SEND_SAFETY_CENTER_UPDATE)
    public void sendSafetyCenterUpdate(@NonNull SafetySourceData safetySourceData) {
        requireNonNull(safetySourceData, "safetySourceData cannot be null");
        try {
            mService.sendSafetyCenterUpdate(mContext.getPackageName(),
                    mContext.getUser().getIdentifier(), safetySourceData);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the last {@link SafetySourceData} update received through {@link
     * #sendSafetyCenterUpdate(SafetySourceData)} for the given
     * {@code safetySourceId}, package and user.
     *
     * <p>Returns {@code null} if there never was any update for the given {@code safetySourceId},
     * package and user.
     */
    @RequiresPermission(SEND_SAFETY_CENTER_UPDATE)
    @Nullable
    public SafetySourceData getLastSafetyCenterUpdate(@NonNull String safetySourceId) {
        requireNonNull(safetySourceId, "safetySourceId cannot be null");
        try {
            return mService.getLastSafetyCenterUpdate(mContext.getPackageName(),
                    mContext.getUser().getIdentifier(), safetySourceId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
