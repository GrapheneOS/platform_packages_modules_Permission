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

package android.safetycenter.cts.testing

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_RECEIVER_FOREGROUND
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.Action
import android.safetycenter.SafetySourceStatus
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.ACTION_HANDLE_INLINE_ACTION
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.EXTRA_INLINE_ACTION_SOURCE_ID
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.EXTRA_INLINE_ACTION_SOURCE_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.EXTRA_INLINE_ACTION_SOURCE_ISSUE_ID

/**
 * A class that provides [SafetySourceData] objects and associated constants to facilitate setting
 * up specific states in SafetyCenter for testing.
 */
class SafetySourceCtsData(private val context: Context) {

    /** A [PendingIntent] that redirects to the SafetyCenter page. */
    val redirectPendingIntent =
        PendingIntent.getActivity(
            context,
            0 /* requestCode */,
            Intent(Intent.ACTION_SAFETY_CENTER),
            PendingIntent.FLAG_IMMUTABLE)

    /** A [SafetySourceData] with a [SEVERITY_LEVEL_UNSPECIFIED] [SafetySourceStatus]. */
    val unspecified =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Unspecified title", "Unspecified summary", SEVERITY_LEVEL_UNSPECIFIED)
                    .setEnabled(false)
                    .build())
            .build()

    /** A [SafetySourceIssue] with a [SEVERITY_LEVEL_INFORMATION] and a redirecting [Action]. */
    val informationIssue =
        SafetySourceIssue.Builder(
                INFORMATION_ISSUE_ID,
                "Information issue title",
                "Information issue summary",
                SEVERITY_LEVEL_INFORMATION,
                ISSUE_TYPE_ID)
            .addAction(
                Action.Builder(INFORMATION_ISSUE_ACTION_ID, "Review", redirectPendingIntent)
                    .build())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * a [SEVERITY_LEVEL_UNSPECIFIED] [SafetySourceStatus].
     */
    val unspecifiedWithIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Unspecified title", "Unspecified summary", SEVERITY_LEVEL_UNSPECIFIED)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .addIssue(informationIssue)
            .build()

    /** A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceStatus]. */
    val information =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceStatus], to be used for
     * a managed profile entry.
     */
    val informationForWork =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Ok title for Work", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SafetySourceStatus].
     */
    val informationWithIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .addIssue(informationIssue)
            .build()

    /** A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION] and a redirecting [Action]. */
    val recommendationIssue =
        SafetySourceIssue.Builder(
                RECOMMENDATION_ISSUE_ID,
                "Recommendation issue title",
                "Recommendation issue summary",
                SEVERITY_LEVEL_RECOMMENDATION,
                ISSUE_TYPE_ID)
            .addAction(
                Action.Builder(RECOMMENDATION_ISSUE_ACTION_ID, "See issue", redirectPendingIntent)
                    .build())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] redirecting [SafetySourceIssue]
     * and [SafetySourceStatus].
     */
    val recommendationWithIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Recommendation title",
                        "Recommendation summary",
                        SEVERITY_LEVEL_RECOMMENDATION)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .addIssue(recommendationIssue)
            .build()

    /** A [PendingIntent] used by the resolving [Action] in [criticalResolvingIssue]. */
    val criticalIssueActionPendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_HANDLE_INLINE_ACTION)
                .setFlags(FLAG_RECEIVER_FOREGROUND)
                .setPackage(context.packageName)
                .putExtra(EXTRA_INLINE_ACTION_SOURCE_ID, SINGLE_SOURCE_ID)
                .putExtra(EXTRA_INLINE_ACTION_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
                .putExtra(EXTRA_INLINE_ACTION_SOURCE_ISSUE_ACTION_ID, CRITICAL_ISSUE_ACTION_ID),
            PendingIntent.FLAG_IMMUTABLE)

    /** A [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving [Action]. */
    val criticalResolvingIssue =
        SafetySourceIssue.Builder(
                CRITICAL_ISSUE_ID,
                "Critical issue title",
                "Critical issue summary",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                ISSUE_TYPE_ID)
            .addAction(
                Action.Builder(
                        CRITICAL_ISSUE_ACTION_ID, "Solve issue", criticalIssueActionPendingIntent)
                    .setWillResolve(true)
                    .build())
            .build()

    /**
     * Another [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a redirecting
     * [Action].
     */
    val criticalRedirectingIssue =
        SafetySourceIssue.Builder(
                CRITICAL_ISSUE_ID,
                "Critical issue title 2",
                "Critical issue summary 2",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                ISSUE_TYPE_ID)
            .addAction(
                Action.Builder(CRITICAL_ISSUE_ACTION_ID, "Go solve issue", redirectPendingIntent)
                    .build())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving [SafetySourceIssue]
     * and [SafetySourceStatus].
     */
    val criticalWithResolvingIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title", "Critical summary", SEVERITY_LEVEL_CRITICAL_WARNING)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .addIssue(criticalResolvingIssue)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SEVERITY_LEVEL_CRITICAL_WARNING] [SafetySourceStatus].
     */
    val criticalWithInformationIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title", "Critical summary", SEVERITY_LEVEL_CRITICAL_WARNING)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .addIssue(informationIssue)
            .build()

    /**
     * Another [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] redirecting
     * [SafetySourceIssue] and [SafetySourceStatus].
     */
    val criticalWithRedirectingIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title 2", "Critical summary 2", SEVERITY_LEVEL_CRITICAL_WARNING)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .addIssue(criticalRedirectingIssue)
            .build()

    companion object {
        /** Issue ID for [informationIssue]. */
        const val INFORMATION_ISSUE_ID = "information_issue_id"

        /** Action ID for the redirecting action in [informationIssue]. */
        const val INFORMATION_ISSUE_ACTION_ID = "information_issue_action_id"

        /** Issue ID for [recommendationIssue]. */
        const val RECOMMENDATION_ISSUE_ID = "recommendation_issue_id"

        /** Action ID for the redirecting action in [recommendationIssue]. */
        const val RECOMMENDATION_ISSUE_ACTION_ID = "recommendation_issue_action_id"

        /** Issue ID for the critical issues in this file. */
        const val CRITICAL_ISSUE_ID = "critical_issue_id"

        /** Action ID for the critical actions in this file. */
        const val CRITICAL_ISSUE_ACTION_ID = "critical_issue_action_id"

        /** Issue type ID for all the issues in this file */
        const val ISSUE_TYPE_ID = "issue_type_id"

        /** A [SafetyEvent] to push arbitrary changes to Safety Center. */
        val EVENT_SOURCE_STATE_CHANGED =
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()

        /** A utility to create a [SafetySourceData] object containing only issues. */
        fun issuesOnly(vararg issues: SafetySourceIssue): SafetySourceData {
            val builder = SafetySourceData.Builder()
            issues.forEach { builder.addIssue(it) }
            return builder.build()
        }
    }
}
