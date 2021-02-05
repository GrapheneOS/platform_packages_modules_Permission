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

package com.android.permissioncontroller.hibernation.utils;

/**
 * Utils and constants for hibernation policy.
 */
public final class PolicyUtils {

    /** The timeout for auto-revoke permissions */
    public static final String PROPERTY_AUTO_REVOKE_UNUSED_THRESHOLD_MILLIS =
            "auto_revoke_unused_threshold_millis2";

    /** The frequency of running the job for auto-revoke permissions */
    public static final String PROPERTY_AUTO_REVOKE_CHECK_FREQUENCY_MILLIS =
            "auto_revoke_check_frequency_millis";
}
