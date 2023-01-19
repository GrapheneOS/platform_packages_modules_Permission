/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.safetycenter.data;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.os.SystemClock;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetySourceIssue;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterStatsdLogger;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Maintains data about in-flight issue actions.
 *
 * @hide
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
public final class SafetyCenterInFlightIssueActionRepository {

    private static final String TAG = "SafetyCenterInFlight";

    @NonNull private final SafetyCenterStatsdLogger mSafetyCenterStatsdLogger;

    private final ArrayMap<SafetyCenterIssueActionId, Long> mSafetyCenterIssueActionsInFlight =
            new ArrayMap<>();

    /** Constructs a new instance of {@link SafetyCenterInFlightIssueActionRepository}. */
    public SafetyCenterInFlightIssueActionRepository(
            @NonNull SafetyCenterStatsdLogger safetyCenterStatsdLogger) {
        mSafetyCenterStatsdLogger = safetyCenterStatsdLogger;
    }

    /** Marks the given {@link SafetyCenterIssueActionId} as in-flight. */
    public void markSafetyCenterIssueActionInFlight(
            @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId) {
        mSafetyCenterIssueActionsInFlight.put(
                safetyCenterIssueActionId, SystemClock.elapsedRealtime());
    }

    /**
     * Unmarks the given {@link SafetyCenterIssueActionId} as in-flight, logs that event to statsd
     * with the given {@code result} value, and returns {@code true} if the underlying {@link
     * SafetyCenterData} changed.
     */
    public boolean unmarkSafetyCenterIssueActionInFlight(
            @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId,
            @NonNull SafetySourceIssue safetySourceIssue,
            @SafetyCenterStatsdLogger.SystemEventResult int result) {
        Long startElapsedMillis =
                mSafetyCenterIssueActionsInFlight.remove(safetyCenterIssueActionId);
        if (startElapsedMillis == null) {
            Log.w(
                    TAG,
                    "Attempt to unmark unknown in-flight action: "
                            + toUserFriendlyString(safetyCenterIssueActionId));
            return false;
        }

        SafetyCenterIssueKey issueKey = safetyCenterIssueActionId.getSafetyCenterIssueKey();
        String issueTypeId = safetySourceIssue == null ? null : safetySourceIssue.getIssueTypeId();
        Duration duration = Duration.ofMillis(SystemClock.elapsedRealtime() - startElapsedMillis);

        mSafetyCenterStatsdLogger.writeInlineActionSystemEvent(
                issueKey.getSafetySourceId(), issueKey.getUserId(), issueTypeId, duration, result);

        if (safetySourceIssue == null
                || getSafetySourceIssueAction(safetyCenterIssueActionId, safetySourceIssue)
                        == null) {
            Log.w(
                    TAG,
                    "Attempt to unmark in-flight action for a non-existent issue or action: "
                            + toUserFriendlyString(safetyCenterIssueActionId));
            return false;
        }

        return true;
    }

    /** Returns {@code true} if the given issue action is in flight. */
    public boolean actionIsInFlight(@NonNull SafetyCenterIssueActionId safetyCenterIssueActionId) {
        return mSafetyCenterIssueActionsInFlight.containsKey(safetyCenterIssueActionId);
    }

    /**
     * Returns {@link SafetySourceIssue.Action} identified by the given {@link
     * SafetyCenterIssueActionId} and {@link SafetySourceIssue}.
     */
    @Nullable
    SafetySourceIssue.Action getSafetySourceIssueAction(
            @NonNull SafetyCenterIssueActionId safetyCenterIssueActionId,
            @NonNull SafetySourceIssue safetySourceIssue) {
        if (actionIsInFlight(safetyCenterIssueActionId)) {
            return null;
        }

        List<SafetySourceIssue.Action> safetySourceIssueActions = safetySourceIssue.getActions();
        for (int i = 0; i < safetySourceIssueActions.size(); i++) {
            SafetySourceIssue.Action safetySourceIssueAction = safetySourceIssueActions.get(i);

            if (safetyCenterIssueActionId
                    .getSafetySourceIssueActionId()
                    .equals(safetySourceIssueAction.getId())) {
                return safetySourceIssueAction;
            }
        }

        return null;
    }

    /** Dumps in-flight action data for debugging purposes. */
    public void dump(@NonNull PrintWriter fout) {
        int actionInFlightCount = mSafetyCenterIssueActionsInFlight.size();
        fout.println("ACTIONS IN FLIGHT (" + actionInFlightCount + ")");
        for (int i = 0; i < actionInFlightCount; i++) {
            String printableId = toUserFriendlyString(mSafetyCenterIssueActionsInFlight.keyAt(i));
            long startElapsedMillis = mSafetyCenterIssueActionsInFlight.valueAt(i);
            long durationMillis = SystemClock.elapsedRealtime() - startElapsedMillis;
            fout.println("\t[" + i + "] " + printableId + "(duration=" + durationMillis + "ms)");
        }
        fout.println();
    }

    /** Clears all in-flight action data. */
    public void clear() {
        mSafetyCenterIssueActionsInFlight.clear();
    }

    /** Clears in-flight action data for given {@code userId}. */
    public void clearForUser(@UserIdInt int userId) {
        // Loop in reverse index order to be able to remove entries while iterating.
        for (int i = mSafetyCenterIssueActionsInFlight.size() - 1; i >= 0; i--) {
            SafetyCenterIssueActionId issueActionId = mSafetyCenterIssueActionsInFlight.keyAt(i);
            if (issueActionId.getSafetyCenterIssueKey().getUserId() == userId) {
                mSafetyCenterIssueActionsInFlight.removeAt(i);
            }
        }
    }
}
