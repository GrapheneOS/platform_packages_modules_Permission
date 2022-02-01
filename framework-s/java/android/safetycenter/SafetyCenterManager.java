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

import static android.Manifest.permission.MANAGE_SAFETY_CENTER;
import static android.Manifest.permission.READ_SAFETY_CENTER_STATUS;
import static android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE;
import static android.annotation.SdkConstant.SdkConstantType.BROADCAST_INTENT_ACTION;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Objects.requireNonNull;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;
import android.util.ArrayMap;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Interface for communicating with the safety center.
 *
 * @hide
 */
@SystemService(Context.SAFETY_CENTER_SERVICE)
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterManager {

    /**
     * Broadcast Action: A broadcast sent by the system to indicate that {@link SafetyCenterManager}
     * is requesting data from safety sources regarding their safety state.
     *
     * <p>This broadcast is sent when a user triggers a data refresh from the Safety Center UI or
     * when Safety Center detects that its stored safety information is stale and needs to be
     * updated.
     *
     * <p>This broadcast is sent explicitly to safety sources by targeting intents to a specified
     * set of components provided by the safety sources in the safety source configuration.
     * The receiving components should be manifest-declared receivers so that safety sources can be
     * requested to send data even if they are not running.
     *
     * <p>On receiving this broadcast, safety sources should determine their safety state according
     * to the parameters specified in the intent extras (see below) and send Safety Center data
     * about their safety state using {@link #sendSafetyCenterUpdate(SafetySourceData)}.
     *
     * <p class="note">This is a protected intent that can only be sent by the system.
     *
     * <p>Includes the following extras:
     * <ul>
     * <li>{@link #EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE}: An int representing the type of data
     * being requested. Possible values are {@link #EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA} and
     * {@link #EXTRA_REFRESH_REQUEST_TYPE_GET_DATA}.
     * <li>{@link #EXTRA_REFRESH_SAFETY_SOURCE_IDS}: A {@code String[]} of ids representing the
     * safety sources being requested for data. This extra exists for disambiguation in the case
     * that a single component is responsible for receiving refresh requests for multiple safety
     * sources.
     * </ul>
     */
    // TODO(b/210805082): Define the term "safety sources" more concretely here once safety sources
    //  are configured in xml config.
    // TODO(b/210979035): Determine recommendation for sources if they are requested for fresh data
    //  but cannot provide it.
    @SdkConstant(BROADCAST_INTENT_ACTION)
    public static final String ACTION_REFRESH_SAFETY_SOURCES =
            "android.safetycenter.action.REFRESH_SAFETY_SOURCES";

    /**
     * Used as a {@code String[]} extra field in {@link #ACTION_REFRESH_SAFETY_SOURCES}
     * intents to specify the safety source ids of the safety sources being requested for data by
     * Safety Center.
     *
     * When this extra field is not specified in the intent, it is assumed that Safety Center is
     * requesting data from all safety sources supported by the component receiving the broadcast.
     */
    public static final String EXTRA_REFRESH_SAFETY_SOURCE_IDS =
            "android.safetycenter.extra.REFRESH_SAFETY_SOURCE_IDS";

    /**
     * Used as an {@code int} extra field in {@link #ACTION_REFRESH_SAFETY_SOURCES} intents to
     * specify the type of data request from Safety Center.
     *
     * Possible values are {@link #EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA} and
     * {@link #EXTRA_REFRESH_REQUEST_TYPE_GET_DATA}
     */
    public static final String EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE =
            "android.safetycenter.extra.REFRESH_SAFETY_SOURCES_REQUEST_TYPE";

    /**
     * All possible types of data refresh requests in broadcasts with intent action
     * {@link #ACTION_REFRESH_SAFETY_SOURCES}.
     *
     * @hide
     */
    @IntDef(prefix = { "EXTRA_REFRESH_REQUEST_TYPE_" }, value = {
            EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA,
            EXTRA_REFRESH_REQUEST_TYPE_GET_DATA,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RefreshRequestType {}

    /**
     * Used as an int value for {@link #EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE} to indicate that
     * the safety source should fetch fresh data relating to their safety state upon receiving a
     * broadcast with intent action {@link #ACTION_REFRESH_SAFETY_SOURCES} and provide it to Safety
     * Center.
     *
     * The term "fresh" here means that the sources should ensure that the safety data is accurate
     * as possible at the time of providing it to Safety Center, even if it involves performing an
     * expensive and/or slow process.
     */
    public static final int EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA = 0;

    /**
     * Used as an int value for
     * {@link #EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE} to indicate that upon receiving a
     * broadcasts with intent action {@link #ACTION_REFRESH_SAFETY_SOURCES}, the safety source
     * should provide data relating to their safety state to Safety Center.
     *
     * If the source already has its safety data cached, it may provide it without triggering a
     * process to fetch state which may be expensive and/or slow.
     */
    public static final int EXTRA_REFRESH_REQUEST_TYPE_GET_DATA = 1;

    /** Indicates that the Safety Center UI has been opened by the user. */
    public static final int REFRESH_REASON_PAGE_OPEN = 100;
    /** Indicates that the rescan button in the Safety Center UI has been clicked on by the user. */
    public static final int REFRESH_REASON_RESCAN_BUTTON_CLICK = 200;

    /**
     * The reason for requesting a refresh of {@link SafetySourceData} from safety sources.
     *
     * @hide
     */
    @IntDef(prefix = {"REFRESH_REASON_"}, value = {
            REFRESH_REASON_PAGE_OPEN,
            REFRESH_REASON_RESCAN_BUTTON_CLICK,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RefreshReason {
    }

    /** Listener for changes to {@link SafetyCenterData}. */
    public interface OnSafetyCenterDataChangedListener {

        /**
         * Called when {@link SafetyCenterData} tracked by the manager changes.
         *
         * @param data the updated data
         */
        void onSafetyCenterDataChanged(@NonNull SafetyCenterData data);
    }

    @NonNull
    private final Context mContext;
    @NonNull
    private final ISafetyCenterManager mService;

    @GuardedBy("mListenersLock")
    private final Map<OnSafetyCenterDataChangedListener, ListenerDelegate>
            mListenersToDelegates = new ArrayMap<>(1); // only one expected listener
    private final Object mListenersLock = new Object();

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
            mService.sendSafetyCenterUpdate(
                    safetySourceData,
                    mContext.getPackageName(),
                    mContext.getUser().getIdentifier());
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
            return mService.getLastSafetyCenterUpdate(
                    safetySourceId,
                    mContext.getPackageName(),
                    mContext.getUser().getIdentifier());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether the SafetyCenter page is enabled.
     */
    @RequiresPermission(anyOf = {
            READ_SAFETY_CENTER_STATUS,
            SEND_SAFETY_CENTER_UPDATE
    })
    public boolean isSafetyCenterEnabled() {
        try {
            return mService.isSafetyCenterEnabled();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Requests safety sources to send a {@link SafetySourceData} update to Safety Center.
     *
     * <p>This API sends a broadcast to all safety sources with action
     * {@link #ACTION_REFRESH_SAFETY_SOURCES}.
     * See {@link #ACTION_REFRESH_SAFETY_SOURCES} for details on how safety sources should respond
     * to receiving these broadcasts.
     *
     * @param refreshReason the reason for the refresh, either {@link #REFRESH_REASON_PAGE_OPEN} or
     * {@link #REFRESH_REASON_RESCAN_BUTTON_CLICK}
     */
    @RequiresPermission(MANAGE_SAFETY_CENTER)
    public void refreshSafetySources(@RefreshReason int refreshReason) {
        try {
            mService.refreshSafetySources(refreshReason, mContext.getUser().getIdentifier());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Clears all {@link SafetySourceData} updates sent to the safety center using {@link
     * #sendSafetyCenterUpdate(SafetySourceData)}, for all packages and users.
     */
    @RequiresPermission(MANAGE_SAFETY_CENTER)
    public void clearSafetyCenterData() {
        try {
            mService.clearSafetyCenterData();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the current {@link SafetyCenterData}, assembled from {@link SafetySourceData} from
     * all sources.
     */
    @RequiresPermission(MANAGE_SAFETY_CENTER)
    @NonNull
    public SafetyCenterData getSafetyCenterData() {
        try {
            return mService.getSafetyCenterData(mContext.getUser().getIdentifier());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Adds a listener for changes to {@link SafetyCenterData}.
     *
     * @see #removeOnSafetyCenterDataChangedListener(OnSafetyCenterDataChangedListener)
     */
    @RequiresPermission(MANAGE_SAFETY_CENTER)
    public void addOnSafetyCenterDataChangedListener(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OnSafetyCenterDataChangedListener listener) {
        requireNonNull(executor, "executor cannot be null");
        requireNonNull(listener, "listener cannot be null");

        synchronized (mListenersLock) {
            if (mListenersToDelegates.containsKey(listener)) return;

            ListenerDelegate delegate = new ListenerDelegate(executor, listener);
            try {
                mService.addOnSafetyCenterDataChangedListener(
                        delegate, mContext.getUser().getIdentifier());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
            mListenersToDelegates.put(listener, delegate);
        }
    }

    /**
     * Removes a listener for changes to {@link SafetyCenterData}.
     *
     * @see #addOnSafetyCenterDataChangedListener(Executor, OnSafetyCenterDataChangedListener)
     */
    @RequiresPermission(MANAGE_SAFETY_CENTER)
    public void removeOnSafetyCenterDataChangedListener(
            @NonNull OnSafetyCenterDataChangedListener listener) {
        requireNonNull(listener, "listener cannot be null");

        synchronized (mListenersLock) {
            ListenerDelegate delegate = mListenersToDelegates.get(listener);
            if (delegate == null) return;

            try {
                mService.removeOnSafetyCenterDataChangedListener(
                        delegate, mContext.getUser().getIdentifier());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
            mListenersToDelegates.remove(listener);
        }
    }

    /**
     * Dismiss an active safety issue and prevent it from appearing in the Safety Center or
     * affecting the overall safety status.
     *
     * @param issueId the target issue ID returned by {@link SafetyCenterIssue#getId()}
     */
    @RequiresPermission(MANAGE_SAFETY_CENTER)
    public void dismissSafetyIssue(@NonNull String issueId) {
        try {
            mService.dismissSafetyIssue(issueId, mContext.getUser().getIdentifier());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private static final class ListenerDelegate
            extends IOnSafetyCenterDataChangedListener.Stub {
        @NonNull
        private final Executor mExecutor;
        @NonNull
        private final OnSafetyCenterDataChangedListener mOriginalListener;

        private ListenerDelegate(
                @NonNull Executor executor,
                @NonNull OnSafetyCenterDataChangedListener originalListener) {
            mExecutor = executor;
            mOriginalListener = originalListener;
        }

        @Override
        public void onSafetyCenterDataChanged(@NonNull SafetyCenterData safetyCenterData)
                throws RemoteException {
            final long token = Binder.clearCallingIdentity();

            try {
                mExecutor.execute(
                        () -> mOriginalListener.onSafetyCenterDataChanged(safetyCenterData));
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }
}
