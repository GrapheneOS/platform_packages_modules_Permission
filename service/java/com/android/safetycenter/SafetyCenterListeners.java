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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.safetycenter.IOnSafetyCenterDataChangedListener;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterErrorDetails;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class that keeps track of all the registered {@link IOnSafetyCenterDataChangedListener}
 * per-user.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterListeners {

    private static final String TAG = "SafetyCenterListeners";

    private final SparseArray<RemoteCallbackList<IOnSafetyCenterDataChangedListener>>
            mSafetyCenterDataChangedListeners = new SparseArray<>();

    /** Creates a {@link SafetyCenterListeners}. */
    SafetyCenterListeners() {}

    /**
     * Delivers a {@link SafetyCenterData} and/or {@link SafetyCenterErrorDetails} update to a
     * single {@link IOnSafetyCenterDataChangedListener}.
     */
    static void deliverUpdate(
            @NonNull IOnSafetyCenterDataChangedListener listener,
            @Nullable SafetyCenterData safetyCenterData,
            @Nullable SafetyCenterErrorDetails safetyCenterErrorDetails) {
        if (safetyCenterData != null) {
            try {
                listener.onSafetyCenterDataChanged(safetyCenterData);
            } catch (RemoteException e) {
                Log.e(TAG, "Error delivering SafetyCenterData to listener", e);
            }
        }
        if (safetyCenterErrorDetails != null) {
            try {
                listener.onError(safetyCenterErrorDetails);
            } catch (RemoteException e) {
                Log.e(TAG, "Error delivering SafetyCenterErrorDetails to listener", e);
            }
        }
    }

    /**
     * Delivers a {@link SafetyCenterData} and/or {@link SafetyCenterErrorDetails} update to all the
     * listeners in the given {@link UserProfileGroup}.
     */
    void deliverUpdateForUserProfileGroup(
            @NonNull UserProfileGroup userProfileGroup,
            @Nullable SafetyCenterData safetyCenterData,
            @Nullable SafetyCenterErrorDetails safetyCenterErrorDetails) {
        deliverUpdateForUserId(
                userProfileGroup.getProfileOwnerUserId(),
                safetyCenterData,
                safetyCenterErrorDetails);
        int[] managedProfilesUserIds = userProfileGroup.getManagedProfilesUserIds();
        for (int i = 0; i < managedProfilesUserIds.length; i++) {
            deliverUpdateForUserId(
                    managedProfilesUserIds[i], safetyCenterData, safetyCenterErrorDetails);
        }
    }

    private void deliverUpdateForUserId(
            @UserIdInt int userId,
            @Nullable SafetyCenterData safetyCenterData,
            @Nullable SafetyCenterErrorDetails safetyCenterErrorDetails) {
        RemoteCallbackList<IOnSafetyCenterDataChangedListener> listenersForUserId =
                mSafetyCenterDataChangedListeners.get(userId);
        if (listenersForUserId == null) {
            return;
        }
        int i = listenersForUserId.beginBroadcast();
        while (i > 0) {
            i--;
            deliverUpdate(
                    listenersForUserId.getBroadcastItem(i),
                    safetyCenterData,
                    safetyCenterErrorDetails);
        }
        listenersForUserId.finishBroadcast();
    }

    /**
     * Adds a {@link IOnSafetyCenterDataChangedListener} for the given {@code userId}.
     *
     * <p>Returns whether the callback was successfully registered. Returns {@code true} if the
     * callback was already registered.
     */
    boolean addListener(
            @NonNull IOnSafetyCenterDataChangedListener listener, @UserIdInt int userId) {
        RemoteCallbackList<IOnSafetyCenterDataChangedListener> listeners =
                mSafetyCenterDataChangedListeners.get(userId);
        if (listeners == null) {
            listeners = new RemoteCallbackList<>();
            mSafetyCenterDataChangedListeners.put(userId, listeners);
        }
        OnSafetyCenterDataChangedListenerWrapper listenerWrapper =
                new OnSafetyCenterDataChangedListenerWrapper(listener);
        return listeners.register(listenerWrapper);
    }

    /**
     * Removes a {@link IOnSafetyCenterDataChangedListener} for the given {@code userId}.
     *
     * <p>Returns whether the callback was unregistered. Returns {@code false} if the callback was
     * never registered.
     */
    boolean removeListener(
            @NonNull IOnSafetyCenterDataChangedListener listener, @UserIdInt int userId) {
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

    /** Clears all {@link IOnSafetyCenterDataChangedListener}s, for all user ids. */
    void clear() {
        for (int i = 0; i < mSafetyCenterDataChangedListeners.size(); i++) {
            RemoteCallbackList<IOnSafetyCenterDataChangedListener> listeners =
                    mSafetyCenterDataChangedListeners.valueAt(i);
            if (listeners == null) {
                continue;
            }
            listeners.kill();
        }
        mSafetyCenterDataChangedListeners.clear();
    }

    /**
     * A wrapper around an {@link IOnSafetyCenterDataChangedListener} to ensure it is only called
     * when the {@link SafetyCenterData} actually changes.
     */
    private static final class OnSafetyCenterDataChangedListenerWrapper
            implements IOnSafetyCenterDataChangedListener {

        @NonNull private final IOnSafetyCenterDataChangedListener mDelegate;
        private final AtomicReference<SafetyCenterData> mLastSafetyCenterData =
                new AtomicReference<>();

        OnSafetyCenterDataChangedListenerWrapper(
                @NonNull IOnSafetyCenterDataChangedListener delegate) {
            mDelegate = delegate;
        }

        @Override
        public void onSafetyCenterDataChanged(@NonNull SafetyCenterData safetyCenterData)
                throws RemoteException {
            if (safetyCenterData.equals(mLastSafetyCenterData.getAndSet(safetyCenterData))) {
                return;
            }
            mDelegate.onSafetyCenterDataChanged(safetyCenterData);
        }

        @Override
        public void onError(@NonNull SafetyCenterErrorDetails safetyCenterErrorDetails)
                throws RemoteException {
            mDelegate.onError(safetyCenterErrorDetails);
        }

        @Override
        public IBinder asBinder() {
            return mDelegate.asBinder();
        }
    }
}
