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

package android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.annotation.IntDef;
import android.annotation.SystemApi;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Data for a safety source status in the Safety Center page, which conveys the overall state of
 * the safety source and allows a user to navigate to the source.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public class SafetySourceSeverity {
    private SafetySourceSeverity() {}

    /**
     * Indicates that no opinion is currently associated with the information provided.
     *
     * <p>This severity level will be reflected in the UI of a {@link SafetySourceStatus} through a
     * grey icon.
     *
     * <p>For a {@link SafetySourceStatus}, this severity level indicates that the safety source
     * currently does not have sufficient information on the severity level of the
     * {@link SafetySourceStatus}.
     *
     * <p>This severity level cannot be used to indicate the severity level of a
     * {@link SafetySourceIssue}.
     */
    public static final int LEVEL_UNSPECIFIED = 100;

    /**
     * Indicates the presence of an informational message or the absence of any safety issues.
     *
     * <p>This severity level will be reflected in the UI of either a {@link SafetySourceStatus} or
     * a {@link SafetySourceIssue} through a green icon.
     *
     * <p>For a {@link SafetySourceStatus}, this severity level indicates either the absence of any
     * {@link SafetySourceIssue}s or the presence of only {@link SafetySourceIssue}s with the same
     * severity level.
     *
     * <p>For a {@link SafetySourceIssue}, this severity level indicates that the
     * {@link SafetySourceIssue} represents an informational message relating to the safety source.
     * {@link SafetySourceIssue}s of this severity level will be dismissible by the user from the
     * UI, and will not trigger a confirmation dialog upon a user attempting to dismiss the warning.
     */
    public static final int LEVEL_INFORMATION = 200;

    /**
     * Indicates the presence of a medium-severity safety issue which the user is encouraged to act
     * on.
     *
     * <p>This severity level will be reflected in the UI of either a {@link SafetySourceStatus} or
     * a {@link SafetySourceIssue} through a yellow icon.
     *
     * <p>For a {@link SafetySourceStatus}, this severity level indicates the presence of at least
     * one medium-severity {@link SafetySourceIssue} relating to the safety source which the user is
     * encouraged to act on, and no {@link SafetySourceIssue}s with higher severity level.
     *
     * <p>For a {@link SafetySourceIssue}, this severity level indicates that the
     * {@link SafetySourceIssue} represents a medium-severity safety issue relating to the safety
     * source which the user is encouraged to act on. {@link SafetySourceIssue}s of this severity
     * level will be dismissible by the user from the UI, and will trigger a confirmation dialog
     * upon a user attempting to dismiss the warning.
     */
    public static final int LEVEL_RECOMMENDATION = 300;

    /**
     * Indicates the presence of a critical or urgent safety issue that should be addressed by the
     * user.
     *
     * <p>This severity level will be reflected in the UI of either a {@link SafetySourceStatus} or
     * a {@link SafetySourceIssue} through a red icon.
     *
     * <p>For a {@link SafetySourceStatus}, this severity level indicates the presence of at least
     * one critical or urgent {@link SafetySourceIssue} relating to the safety source that should be
     * addressed by the user.
     *
     * <p>For a {@link SafetySourceIssue}, this severity level indicates that the
     * {@link SafetySourceIssue} represents a critical or urgent safety issue relating to the safety
     * source that should be addressed by the user. {@link SafetySourceIssue}s of this severity
     * level will be dismissible by the user from the UI, and will trigger a confirmation dialog
     * upon a user attempting to dismiss the warning.
     */
    public static final int LEVEL_CRITICAL_WARNING = 400;

    /**
     * All possible severity levels for a {@link SafetySourceStatus} or a {@link SafetySourceIssue}.
     *
     * <p>The numerical values of the levels are not used directly, rather they are used to build a
     * continuum of levels which support relative comparison. The higher the severity level the
     * higher the threat to the user.
     *
     * <p>For a {@link SafetySourceStatus}, the severity level is meant to convey the aggregated
     * severity of the safety source, and it contributes to the overall severity level in the Safety
     * Center. If the {@link SafetySourceData} contains {@link SafetySourceIssue}s, the severity
     * level of the s{@link SafetySourceStatus} must match the highest severity level among the
     * {@link SafetySourceIssue}s.
     *
     * <p>For a {@link SafetySourceIssue}, not all severity levels can be used. The severity level
     * also determines how a {@link SafetySourceIssue}s is "dismissible" by the user, i.e. how the
     * user can choose to ignore the issue and remove it from view in the Safety Center.
     *
     * @hide
     */
    @IntDef(prefix = {"LEVEL_"}, value = {
            LEVEL_UNSPECIFIED,
            LEVEL_INFORMATION,
            LEVEL_RECOMMENDATION,
            LEVEL_CRITICAL_WARNING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Level {
    }

    @Level
    static int validateLevelForSource(int value) {
        switch (value) {
            case SafetySourceSeverity.LEVEL_UNSPECIFIED:
            case SafetySourceSeverity.LEVEL_INFORMATION:
            case SafetySourceSeverity.LEVEL_RECOMMENDATION:
            case SafetySourceSeverity.LEVEL_CRITICAL_WARNING:
                return value;
            default:
                throw new IllegalArgumentException(
                        String.format("Unexpected Level for source: %s", value));
        }
    }

    @Level
    static int validateLevelForIssue(int value) {
        switch (value) {
            case SafetySourceSeverity.LEVEL_INFORMATION:
            case SafetySourceSeverity.LEVEL_RECOMMENDATION:
            case SafetySourceSeverity.LEVEL_CRITICAL_WARNING:
                return value;
            case SafetySourceSeverity.LEVEL_UNSPECIFIED:
            default:
                throw new IllegalArgumentException(
                        String.format("Unexpected Level for issue: %s", value));
        }
    }
}
