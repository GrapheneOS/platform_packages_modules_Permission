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

import android.safetycenter.SafetySourceData;

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
     * Called by a safety source to send a SafetySourceData update to the safety center.
     */
    void sendSafetyCenterUpdate(in String packageName, int userId,
            in SafetySourceData safetySourceData);

    /**
     * Returns the last SafetySourceData update received by the safety center for the given safety
     * source id.
     */
    SafetySourceData getLastSafetyCenterUpdate(in String packageName, int userId,
            in String safetySourceId);
}
