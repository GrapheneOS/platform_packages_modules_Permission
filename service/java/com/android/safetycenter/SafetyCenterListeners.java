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
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;

import java.io.PrintWriter;
import java.util.List;
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

    @NonNull private final SafetyCenterDataFactory mSafetyCenterDataFactory;

    private final SparseArray<RemoteCallbackList<IOnSafetyCenterDataChangedListener>>
            mSafetyCenterDataChangedListeners = new SparseArray<>();

    SafetyCenterListeners(@NonNull SafetyCenterDataFactory safetyCenterDataFactory) {
        mSafetyCenterDataFactory = safetyCenterDataFactory;
    }

    /**
     * Delivers a {@link SafetyCenterData} and/or {@link SafetyCenterErrorDetails} update to a
     * single {@link IOnSafetyCenterDataChangedListener}.
     */
    static void deliverUpdateForListener(
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
     * Same as {@link #deliverUpdateForUserProfileGroup} but for all the given {@code
     * userProfileGroups}.
     */
    void deliverUpdateForUserProfileGroups(
            @NonNull List<UserProfileGroup> userProfileGroups,
            boolean updateSafetyCenterData,
            @Nullable SafetyCenterErrorDetails safetyCenterErrorDetails) {
        for (int i = 0; i < userProfileGroups.size(); i++) {
            deliverUpdateForUserProfileGroup(
                    userProfileGroups.get(i), updateSafetyCenterData, safetyCenterErrorDetails);
        }
    }

    /**
     * Delivers a {@link SafetyCenterData} and {@link SafetyCenterErrorDetails} update on all
     * listeners of the given {@link UserProfileGroup}, if applicable.
     *
     * @param userProfileGroup the {@link UserProfileGroup} to deliver this update on
     * @param updateSafetyCenterData whether a new {@link SafetyCenterData} should be computed and
     *     delivered to listeners
     * @param safetyCenterErrorDetails the relevant {@link SafetyCenterErrorDetails} to deliver to
     *     listeners, if any
     */
    void deliverUpdateForUserProfileGroup(
            @NonNull UserProfileGroup userProfileGroup,
            boolean updateSafetyCenterData,
            @Nullable SafetyCenterErrorDetails safetyCenterErrorDetails) {
        boolean needToUpdateListeners = updateSafetyCenterData || safetyCenterErrorDetails != null;
        if (!needToUpdateListeners) {
            return;
        }

        ArrayMap<String, SafetyCenterData> safetyCenterDataCache = new ArrayMap<>();
        deliverUpdateForUser(
                userProfileGroup.getProfileParentUserId(),
                userProfileGroup,
                safetyCenterDataCache,
                updateSafetyCenterData,
                safetyCenterErrorDetails);

        int[] managedRunningProfilesUserIds = userProfileGroup.getManagedRunningProfilesUserIds();
        for (int i = 0; i < managedRunningProfilesUserIds.length; i++) {
            int managedRunningProfileUserId = managedRunningProfilesUserIds[i];

            deliverUpdateForUser(
                    managedRunningProfileUserId,
                    userProfileGroup,
                    safetyCenterDataCache,
                    updateSafetyCenterData,
                    safetyCenterErrorDetails);
        }
    }

    /**
     * Adds a {@link IOnSafetyCenterDataChangedListener} for the given {@code packageName} and
     * {@code userId}.
     *
     * <p>Returns the registered {@link IOnSafetyCenterDataChangedListener} if this operation was
     * successful. Otherwise, returns {@code null}.
     */
    @Nullable
    IOnSafetyCenterDataChangedListener addListener(
            @NonNull IOnSafetyCenterDataChangedListener listener,
            @NonNull String packageName,
            @UserIdInt int userId) {
        RemoteCallbackList<IOnSafetyCenterDataChangedListener> listeners =
                mSafetyCenterDataChangedListeners.get(userId);
        if (listeners == null) {
            listeners = new RemoteCallbackList<>();
        }
        OnSafetyCenterDataChangedListenerWrapper listenerWrapper =
                new OnSafetyCenterDataChangedListenerWrapper(listener, packageName);
        boolean registered = listeners.register(listenerWrapper);
        if (!registered) {
            return null;
        }
        mSafetyCenterDataChangedListeners.put(userId, listeners);
        return listenerWrapper;
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

    /** Clears all {@link IOnSafetyCenterDataChangedListener}s, for the given user. */
    void clearForUser(@UserIdInt int userId) {
        RemoteCallbackList<IOnSafetyCenterDataChangedListener> listeners =
                mSafetyCenterDataChangedListeners.get(userId);
        if (listeners == null) {
            return;
        }
        listeners.kill();
        mSafetyCenterDataChangedListeners.remove(userId);
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

    private void deliverUpdateForUser(
            @UserIdInt int userId,
            @NonNull UserProfileGroup userProfileGroup,
            @NonNull ArrayMap<String, SafetyCenterData> safetyCenterDataCache,
            boolean updateSafetyCenterData,
            @Nullable SafetyCenterErrorDetails safetyCenterErrorDetails) {
        RemoteCallbackList<IOnSafetyCenterDataChangedListener> listenersForUserId =
                mSafetyCenterDataChangedListeners.get(userId);
        if (listenersForUserId == null) {
            return;
        }
        int i = listenersForUserId.beginBroadcast();
        try {
            while (i > 0) {
                i--;
                OnSafetyCenterDataChangedListenerWrapper listenerWrapper =
                        (OnSafetyCenterDataChangedListenerWrapper)
                                listenersForUserId.getBroadcastItem(i);
                SafetyCenterData safetyCenterData = null;
                if (updateSafetyCenterData) {
                    String packageName = listenerWrapper.getPackageName();
                    SafetyCenterData cachedSafetyCenterData =
                            safetyCenterDataCache.get(packageName);
                    if (cachedSafetyCenterData != null) {
                        safetyCenterData = cachedSafetyCenterData;
                    } else {
                        safetyCenterData =
                                mSafetyCenterDataFactory.assembleSafetyCenterData(
                                        packageName, userProfileGroup);
                        safetyCenterDataCache.put(packageName, safetyCenterData);
                    }
                }
                deliverUpdateForListener(
                        listenerWrapper, safetyCenterData, safetyCenterErrorDetails);
            }
        } finally {
            listenersForUserId.finishBroadcast();
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(@NonNull PrintWriter fout) {
        int userIdCount = mSafetyCenterDataChangedListeners.size();
        fout.println("DATA CHANGED LISTENERS (" + userIdCount + " user IDs)");
        for (int i = 0; i < userIdCount; i++) {
            int userId = mSafetyCenterDataChangedListeners.keyAt(i);
            RemoteCallbackList<IOnSafetyCenterDataChangedListener> listeners =
                    mSafetyCenterDataChangedListeners.valueAt(i);
            if (listeners == null) {
                continue;
            }
            int listenerCount = listeners.getRegisteredCallbackCount();
            fout.println("\t[" + i + "] user " + userId + " (" + listenerCount + " listeners)");
            for (int j = 0; j < listenerCount; j++) {
                fout.println("\t\t[" + j + "] " + listeners.getRegisteredCallbackItem(j));
            }
        }
        fout.println();
    }

    /**
     * A wrapper around an {@link IOnSafetyCenterDataChangedListener} to ensure it is only called
     * when the {@link SafetyCenterData} actually changes.
     */
    private static final class OnSafetyCenterDataChangedListenerWrapper
            implements IOnSafetyCenterDataChangedListener {

        @NonNull private final IOnSafetyCenterDataChangedListener mDelegate;
        @NonNull private final String mPackageName;

        private final AtomicReference<SafetyCenterData> mLastSafetyCenterData =
                new AtomicReference<>();

        OnSafetyCenterDataChangedListenerWrapper(
                @NonNull IOnSafetyCenterDataChangedListener delegate, @NonNull String packageName) {
            mDelegate = delegate;
            mPackageName = packageName;
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

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        @Override
        public String toString() {
            return "OnSafetyCenterDataChangedListenerWrapper{"
                    + "mDelegate="
                    + mDelegate
                    + ", mPackageName='"
                    + mPackageName
                    + '\''
                    + ", mLastSafetyCenterData="
                    + mLastSafetyCenterData
                    + '}';
        }
    }
}
