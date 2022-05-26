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

    /** A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceStatus]. */
    val information =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .build()

    /** A [SafetySourceIssue] with a [SEVERITY_LEVEL_INFORMATION] and a redirection [Action]. */
    val informationIssue =
        SafetySourceIssue.Builder(
                INFORMATION_ISSUE_ID,
                "Information issue title",
                "Information issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id")
            .addAction(
                Action.Builder(INFORMATION_ISSUE_ACTION_ID, "Review", redirectPendingIntent)
                    .build())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceIssue] and
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

    /** A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION] and a redirection [Action]. */
    val recommendationIssue =
        SafetySourceIssue.Builder(
                RECOMMENDATION_ISSUE_ID,
                "Recommendation issue title",
                "Recommendation issue summary",
                SEVERITY_LEVEL_RECOMMENDATION,
                "issue_type_id")
            .addAction(
                Action.Builder("recommendation_action_id", "See issue", redirectPendingIntent)
                    .build())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] [SafetySourceIssue] and
     * [SafetySourceStatus].
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

    /** A [PendingIntent] used by the resolving [Action] in [criticalIssue]. */
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
    val criticalIssue =
        SafetySourceIssue.Builder(
                CRITICAL_ISSUE_ID,
                "Critical issue title",
                "Critical issue summary",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                "issue_type_id")
            .addAction(
                Action.Builder(
                        CRITICAL_ISSUE_ACTION_ID, "Solve issue", criticalIssueActionPendingIntent)
                    .setWillResolve(true)
                    .build())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] [SafetySourceIssue] and
     * [SafetySourceStatus].
     */
    val criticalWithIssue = criticalBuilder().addIssue(criticalIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] [SafetySourceStatus], but no
     * issue.
     */
    val critical = criticalBuilder().build()

    private fun criticalBuilder() =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title", "Critical summary", SEVERITY_LEVEL_CRITICAL_WARNING)
                    .setPendingIntent(redirectPendingIntent)
                    .build())

    companion object {
        /** Issue ID for [criticalIssue]. */
        const val CRITICAL_ISSUE_ID = "critical_issue_id"

        /** Action ID for the resolving action in [criticalIssue]. */
        const val CRITICAL_ISSUE_ACTION_ID = "critical_issue_action_id"

        /** Issue ID for [recommendationIssue]. */
        const val RECOMMENDATION_ISSUE_ID = "recommendation_issue_id"

        /** Issue ID for [informationIssue]. */
        const val INFORMATION_ISSUE_ID = "information_issue_id"

        /** Action ID for the redirection action in [informationIssue]. */
        const val INFORMATION_ISSUE_ACTION_ID = "information_issue_action_id"

        /** A [SafetyEvent] to push arbitrary changes to SafetyCenter. */
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
