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

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Collections.unmodifiableList;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.safetycenter.IOnSafetyCenterDataChangedListener;
import android.safetycenter.SafetyCenterData;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that keeps track of all the registered {@link IOnSafetyCenterDataChangedListener}
 * per-user.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
final class SafetyCenterListeners {

    private static final String TAG = "SafetyCenterListeners";

    private final SparseArray<RemoteCallbackList<IOnSafetyCenterDataChangedListener>>
            mSafetyCenterDataChangedListeners = new SparseArray<>();

    /** Creates a {@link SafetyCenterListeners}. */
    SafetyCenterListeners() {
    }

    /**
     * Delivers a {@link SafetyCenterData} update to a single
     * {@link IOnSafetyCenterDataChangedListener}.
     */
    static void deliverUpdate(
            @NonNull IOnSafetyCenterDataChangedListener listener,
            @NonNull SafetyCenterData safetyCenterData) {
        try {
            listener.onSafetyCenterDataChanged(safetyCenterData);
        } catch (RemoteException e) {
            Log.e(TAG, "Error delivering SafetyCenterData update to listener", e);
        }
    }

    /**
     * Delivers a {@link SafetyCenterData} update to all the {@link RemoteCallbackList} of {@link
     * IOnSafetyCenterDataChangedListener}.
     *
     * <p>Registering or unregistering {@link IOnSafetyCenterDataChangedListener} on any of the
     * underlying {@link RemoteCallbackList} on another thread while an update is happening is safe
     * as this is handled by the {@link RemoteCallbackList} already (as well as listeners death).
     */
    static void deliverUpdate(
            @NonNull List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> listeners,
            @NonNull SafetyCenterData safetyCenterData) {
        for (int i = 0; i < listeners.size(); i++) {
            deliverUpdate(listeners.get(i), safetyCenterData);
        }
    }

    private static void deliverUpdate(
            @NonNull RemoteCallbackList<IOnSafetyCenterDataChangedListener> listeners,
            @NonNull SafetyCenterData safetyCenterData) {
        int i = listeners.beginBroadcast();
        while (i > 0) {
            i--;
            deliverUpdate(listeners.getBroadcastItem(i), safetyCenterData);
        }
        listeners.finishBroadcast();
    }

    /**
     * Adds a {@link IOnSafetyCenterDataChangedListener} for the given {@code userId}.
     *
     * <p>Returns whether the callback was successfully registered. Returns {@code true} if
     * the callback was already registered.
     */
    boolean addListener(
            @NonNull IOnSafetyCenterDataChangedListener listener,
            @UserIdInt int userId) {
        RemoteCallbackList<IOnSafetyCenterDataChangedListener> listeners =
                mSafetyCenterDataChangedListeners.get(userId);
        if (listeners == null) {
            listeners = new RemoteCallbackList<>();
            mSafetyCenterDataChangedListeners.put(userId, listeners);
        }
        return listeners.register(listener);
    }

    /**
     * Removes a {@link IOnSafetyCenterDataChangedListener} for the given {@code userId}.
     *
     * <p>Returns whether the callback was unregistered. Returns {@code false} if the callback was
     * never registered.
     */
    boolean removeListener(
            @NonNull IOnSafetyCenterDataChangedListener listener,
            @UserIdInt int userId) {
        RemoteCallbackList<IOnSafetyCenterDataChangedListener> listeners =
                mSafetyCenterDataChangedListeners.get(userId);
        if (listeners == null) {
            return false;
        }
        boolean unregistered = listeners.unregister(listener);
        if (listeners.getRegisteredCallbackCount() == 0) {
            mSafetyCenterDataChangedListeners.remove(userId);
        }
        return unregistered;
    }

    /**
     * Returns all the {@link RemoteCallbackList} of {@link IOnSafetyCenterDataChangedListener} for
     * the given {@link UserProfiles}.
     */
    @NonNull
    List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> getListeners(
            @NonNull UserProfiles userProfiles) {
        List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> listeners = new ArrayList<>();
        addToListIfNotNull(listeners, userProfiles.getProfileOwnerUserId());
        int[] workProfilesUserIds = userProfiles.getWorkProfilesUserIds();
        for (int i = 0; i < workProfilesUserIds.length; i++) {
            addToListIfNotNull(listeners, workProfilesUserIds[i]);
        }
        return unmodifiableList(listeners);
    }

    private void addToListIfNotNull(
            @NonNull List<RemoteCallbackList<IOnSafetyCenterDataChangedListener>> listeners,
            @UserIdInt int userId) {
        RemoteCallbackList<IOnSafetyCenterDataChangedListener> listenersForUserId =
                mSafetyCenterDataChangedListeners.get(userId);
        if (listenersForUserId != null) {
            listeners.add(listenersForUserId);
        }
    }
}
