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

import android.safetycenter.IOnSafetyCenterDataChangedListener;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterError;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceError;

/**
 * AIDL service for the safety center.
 *
 * This is the main entry point for gathering data from various safety sources. Safety sources can
 * call this service to provide new data to the safety center. This data will be aggregated and
 * merged into a single safety status, warning cards and settings preferences visible in the safety
 * center.
 *
 * @hide
 */
interface ISafetyCenterManager {
    /**
     * Returns whether the SafetyCenter page is enabled.
     */
    boolean isSafetyCenterEnabled();

     /**
     * Called by a safety source to send a SafetySourceData update to the safety center.
     */
    void sendSafetyCenterUpdate(
            in SafetySourceData safetySourceData,
            String packageName,
            int userId);

    /**
     * Returns the last SafetySourceData update received by the safety center for the given safety
     * source id.
     */
    SafetySourceData getLastSafetyCenterUpdate(
            String safetySourceId,
            String packageName,
            int userId);

   /**
     * Requests safety sources to send a SafetySourceData update to Safety Center.
    */
    void refreshSafetySources(int refreshReason, int userId);

    /**
     * Notifies the SafetyCenter of an error related to a given safety source.
     *
     * <p>Safety sources should use this API to notify SafetyCenter when SafetyCenter requested or
     * expected them to perform an action or provide data, but they were unable to do so.
     */
    void reportSafetySourceError(String safetySourceId,
            in SafetySourceError error,
            String packageName,
            int userId);

    /**
    * Add a safety source dynamically to be used in addition to the sources in the Safety Center
    * xml configuration.
    *
    * <p>Note: This API serves to facilitate CTS testing and should not be used for other purposes.
    */
    void addAdditionalSafetySource(
            String sourceId,
            String packageName,
            String broadcastReceiverName);

    /**
     * Clears additional safety sources added dynamically to be used in addition to the sources in
     * the Safety Center xml configuration.
     *
     * <p>Note: This API serves to facilitate CTS testing and should not be used for other purposes.
     */
    void clearAdditionalSafetySources();

    /**
     * Returns the current SafetyCenterData, assembled from the SafetySourceData from all sources.
     */
    SafetyCenterData getSafetyCenterData(int userId);

    void addOnSafetyCenterDataChangedListener(
            IOnSafetyCenterDataChangedListener listener,
            int userId);

    void removeOnSafetyCenterDataChangedListener(
            IOnSafetyCenterDataChangedListener listener,
            int userId);

    /** Executes the specified action on the specified issue. */
    void executeAction(String safetyCenterIssueId, String safetyCenterActionId, int userId);

    /**
     * Dismisses the issue corresponding to the given issue ID.
     */
    void dismissSafetyIssue(String issueId, int userId);

    /**
     * Clears all SafetySourceData updates sent to the safety center using sendSafetyCenterUpdate,
     * for all packages and users.
     */
    void clearSafetyCenterData();
}