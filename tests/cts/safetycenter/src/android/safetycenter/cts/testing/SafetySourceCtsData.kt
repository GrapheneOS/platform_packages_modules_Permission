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
import android.content.Intent.ACTION_SAFETY_CENTER
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
import android.safetycenter.SafetySourceStatus.IconAction
import android.safetycenter.SafetySourceStatus.IconAction.ICON_TYPE_GEAR
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.ACTION_HANDLE_DISMISSED_ISSUE
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.ACTION_HANDLE_INLINE_ACTION
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.EXTRA_SOURCE_ID
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.EXTRA_SOURCE_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.EXTRA_SOURCE_ISSUE_ID
import kotlin.math.max

/**
 * A class that provides [SafetySourceData] objects and associated constants to facilitate setting
 * up specific states in SafetyCenter for testing.
 */
class SafetySourceCtsData(private val context: Context) {

    /** A [PendingIntent] that redirects to the SafetyCenter page. */
    val redirectPendingIntent = createRedirectPendingIntent(context)

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
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_INFORMATION] and a redirecting [Action]. With
     * subtitle provided.
     */
    val informationIssueWithSubtitle =
        SafetySourceIssue.Builder(
                INFORMATION_ISSUE_ID,
                "Information issue title",
                "Information issue summary",
                SEVERITY_LEVEL_INFORMATION,
                ISSUE_TYPE_ID)
            .setSubtitle("Information issue subtitle")
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

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * a [SEVERITY_LEVEL_UNSPECIFIED] [SafetySourceStatus], to be used for a managed profile entry.
     */
    val unspecifiedWithIssueForWork =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                    "Unspecified title for Work", "Unspecified summary", SEVERITY_LEVEL_UNSPECIFIED)
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
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceStatus] and null
     * pending intent.
     */
    val informationWithNullIntent =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(null)
                    .build())
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] [SafetySourceStatus] and an
     * [IconAction] defined.
     */
    val informationWithIconAction =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(redirectPendingIntent)
                    .setIconAction(IconAction(ICON_TYPE_GEAR, redirectPendingIntent))
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

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SafetySourceStatus], to be used for a managed profile entry.
     */
    val informationWithIssueForWork =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                    "Ok title for Work", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .addIssue(informationIssue)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SafetySourceStatus].
     */
    val informationWithSubtitleIssue =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Ok title", "Ok summary", SEVERITY_LEVEL_INFORMATION)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .addIssue(informationIssueWithSubtitle)
            .build()

    /**
     * A [SafetySourceIssue.Builder] with a [SEVERITY_LEVEL_RECOMMENDATION] and a redirecting
     * [Action].
     */
    private fun defaultRecommendationIssueBuilder() =
        SafetySourceIssue.Builder(
                RECOMMENDATION_ISSUE_ID,
                "Recommendation issue title",
                "Recommendation issue summary",
                SEVERITY_LEVEL_RECOMMENDATION,
                ISSUE_TYPE_ID)
            .addAction(
                Action.Builder(RECOMMENDATION_ISSUE_ACTION_ID, "See issue", redirectPendingIntent)
                    .build())

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION], general category and a
     * redirecting [Action].
     */
    val recommendationGeneralIssue = defaultRecommendationIssueBuilder().build()

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION], account category and a
     * redirecting [Action].
     */
    val recommendationAccountIssue =
        defaultRecommendationIssueBuilder()
            .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT)
            .build()

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION], device category and a
     * redirecting [Action].
     */
    val recommendationDeviceIssue =
        defaultRecommendationIssueBuilder()
            .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
            .build()

    private val dismissIssuePendingIntent =
        broadcastPendingIntent(
            Intent(ACTION_HANDLE_DISMISSED_ISSUE).putExtra(EXTRA_SOURCE_ID, SINGLE_SOURCE_ID))

    /**
     * A [SafetySourceIssue] with a [SEVERITY_LEVEL_RECOMMENDATION] and a dismiss [PendingIntent].
     */
    val recommendationIssueWithDismissPendingIntent =
        defaultRecommendationIssueBuilder()
            .setOnDismissPendingIntent(dismissIssuePendingIntent)
            .build()

    /** A [SafetySourceData.Builder] with a [SEVERITY_LEVEL_RECOMMENDATION] status. */
    fun defaultRecommendationDataBuilder() =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Recommendation title",
                        "Recommendation summary",
                        SEVERITY_LEVEL_RECOMMENDATION)
                    .setPendingIntent(redirectPendingIntent)
                    .build())

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] redirecting [SafetySourceIssue]
     * and [SafetySourceStatus], only containing a general issue.
     */
    val recommendationWithGeneralIssue =
        defaultRecommendationDataBuilder().addIssue(recommendationGeneralIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] redirecting [SafetySourceIssue]
     * and [SafetySourceStatus], only containing an account issue.
     */
    val recommendationWithAccountIssue =
        defaultRecommendationDataBuilder().addIssue(recommendationAccountIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] redirecting [SafetySourceIssue]
     * and [SafetySourceStatus], only containing a device issue.
     */
    val recommendationWithDeviceIssue =
        defaultRecommendationDataBuilder().addIssue(recommendationDeviceIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_RECOMMENDATION] [SafetySourceIssue] that has a
     * dismiss [PendingIntent], and [SafetySourceStatus].
     */
    val recommendationDismissPendingIntentIssue =
        defaultRecommendationDataBuilder()
            .addIssue(recommendationIssueWithDismissPendingIntent)
            .build()

    /** A [PendingIntent] used by the resolving [Action] in [criticalResolvingGeneralIssue]. */
    val criticalIssueActionPendingIntent =
        broadcastPendingIntent(
            Intent(ACTION_HANDLE_INLINE_ACTION)
                .putExtra(EXTRA_SOURCE_ID, SINGLE_SOURCE_ID)
                .putExtra(EXTRA_SOURCE_ISSUE_ID, CRITICAL_ISSUE_ID)
                .putExtra(EXTRA_SOURCE_ISSUE_ACTION_ID, CRITICAL_ISSUE_ACTION_ID))

    /** A resolving Critical [Action] */
    val criticalResolvingAction =
        Action.Builder(CRITICAL_ISSUE_ACTION_ID, "Solve issue", criticalIssueActionPendingIntent)
            .setWillResolve(true)
            .build()

    /** A resolving Critical [Action] that declares a success message */
    val criticalResolvingActionWithSuccessMessage =
        Action.Builder(CRITICAL_ISSUE_ACTION_ID, "Solve issue", criticalIssueActionPendingIntent)
            .setWillResolve(true)
            .setSuccessMessage("Issue solved")
            .build()

    /** A [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving [Action]. */
    val criticalResolvingIssueWithSuccessMessage =
        SafetySourceIssue.Builder(
                CRITICAL_ISSUE_ID,
                "Critical issue title",
                "Critical issue summary",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                ISSUE_TYPE_ID)
            .addAction(criticalResolvingActionWithSuccessMessage)
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
     * [SafetySourceIssue.Builder] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving [Action]
     * .
     */
    fun defaultCriticalResolvingIssueBuilder(issueId: String = CRITICAL_ISSUE_ID) =
        SafetySourceIssue.Builder(
                issueId,
                "Critical issue title",
                "Critical issue summary",
                SEVERITY_LEVEL_CRITICAL_WARNING,
                ISSUE_TYPE_ID)
            .addAction(criticalResolvingAction)

    /**
     * General [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving [Action]
     * .
     */
    val criticalResolvingGeneralIssue = defaultCriticalResolvingIssueBuilder().build()

    /**
     * Account related [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving
     * [Action].
     */
    val criticalResolvingAccountIssue =
        defaultCriticalResolvingIssueBuilder()
            .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT)
            .build()

    /**
     * Device related [SafetySourceIssue] with a [SEVERITY_LEVEL_CRITICAL_WARNING] and a resolving
     * [Action].
     */
    val criticalResolvingDeviceIssue =
        defaultCriticalResolvingIssueBuilder()
            .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
            .build()

    /** A [SafetySourceData.Builder] with a [SEVERITY_LEVEL_CRITICAL_WARNING] status. */
    fun defaultCriticalDataBuilder() =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title", "Critical summary", SEVERITY_LEVEL_CRITICAL_WARNING)
                    .setPendingIntent(redirectPendingIntent)
                    .build())

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving general
     * [SafetySourceIssue] and [SafetySourceStatus].
     */
    val criticalWithResolvingGeneralIssue =
        defaultCriticalDataBuilder().addIssue(criticalResolvingGeneralIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving account related
     * [SafetySourceIssue] and [SafetySourceStatus].
     */
    val criticalWithResolvingAccountIssue =
        defaultCriticalDataBuilder().addIssue(criticalResolvingAccountIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving device related
     * [SafetySourceIssue] and [SafetySourceStatus].
     */
    val criticalWithResolvingDeviceIssue =
        defaultCriticalDataBuilder().addIssue(criticalResolvingDeviceIssue).build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving device related
     * [SafetySourceIssue] and [SafetySourceStatus] and a recommendation issue.
     */
    val criticalWithResolvingDeviceIssueAndRecommendationIssue =
        defaultCriticalDataBuilder()
            .addIssue(criticalResolvingDeviceIssue)
            .addIssue(recommendationAccountIssue)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_CRITICAL_WARNING] resolving [SafetySourceIssue]
     * and [SafetySourceStatus].
     */
    val criticalWithResolvingIssueWithSuccessMessage =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder(
                        "Critical title", "Critical summary", SEVERITY_LEVEL_CRITICAL_WARNING)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .addIssue(criticalResolvingIssueWithSuccessMessage)
            .build()

    /**
     * A [SafetySourceData] with a [SEVERITY_LEVEL_INFORMATION] redirecting [SafetySourceIssue] and
     * [SEVERITY_LEVEL_CRITICAL_WARNING] [SafetySourceStatus].
     */
    val criticalWithInformationIssue =
        defaultCriticalDataBuilder().addIssue(informationIssue).build()

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

    /**
     * A function to generate simple [SafetySourceData] with the given entry [severityLevel] and
     * [entrySummary], and an optional issue with the same [severityLevel].
     */
    fun buildSafetySourceDataWithSummary(
        severityLevel: Int,
        entrySummary: String,
        withIssue: Boolean = false
    ) =
        SafetySourceData.Builder()
            .setStatus(
                SafetySourceStatus.Builder("Entry title", entrySummary, severityLevel)
                    .setPendingIntent(redirectPendingIntent)
                    .build())
            .apply {
                if (withIssue) {
                    addIssue(
                        SafetySourceIssue.Builder(
                                "issue_id",
                                "Issue title",
                                "Issue summary",
                                max(severityLevel, SEVERITY_LEVEL_INFORMATION),
                                ISSUE_TYPE_ID)
                            .addAction(
                                Action.Builder("action_id", "Action", redirectPendingIntent)
                                    .build())
                            .build())
                }
            }
            .build()

    private fun broadcastPendingIntent(intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            intent.addFlags(FLAG_RECEIVER_FOREGROUND).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE)

    companion object {
        /** Issue ID for [informationIssue]. */
        const val INFORMATION_ISSUE_ID = "information_issue_id"

        /** Action ID for the redirecting action in [informationIssue]. */
        const val INFORMATION_ISSUE_ACTION_ID = "information_issue_action_id"

        /** Issue ID for a recommendation issue */
        const val RECOMMENDATION_ISSUE_ID = "recommendation_issue_id"

        /** Action ID for the redirecting action in recommendation issue. */
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

        /** Returns a [SafetySourceData] object containing only the given [issues]. */
        fun issuesOnly(vararg issues: SafetySourceIssue): SafetySourceData {
            val builder = SafetySourceData.Builder()
            issues.forEach { builder.addIssue(it) }
            return builder.build()
        }

        /** Returns a [PendingIntent] that redirects to the given [intentAction]'s page. */
        fun createRedirectPendingIntent(
            context: Context,
            intentAction: String = ACTION_SAFETY_CENTER
        ): PendingIntent =
            PendingIntent.getActivity(
                context, 0 /* requestCode */, Intent(intentAction), PendingIntent.FLAG_IMMUTABLE)
    }
}
