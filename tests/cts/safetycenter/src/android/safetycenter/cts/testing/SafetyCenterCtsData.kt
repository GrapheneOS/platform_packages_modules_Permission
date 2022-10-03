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
import android.icu.text.MessageFormat
import android.os.UserHandle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.INFORMATION_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.INFORMATION_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.ISSUE_TYPE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.RECOMMENDATION_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.RECOMMENDATION_ISSUE_ID
import android.util.ArrayMap
import com.android.safetycenter.internaldata.SafetyCenterEntryGroupId
import com.android.safetycenter.internaldata.SafetyCenterEntryId
import com.android.safetycenter.internaldata.SafetyCenterIds
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId
import com.android.safetycenter.internaldata.SafetyCenterIssueId
import com.android.safetycenter.internaldata.SafetyCenterIssueKey
import com.android.safetycenter.resources.SafetyCenterResourcesContext
import java.util.Locale

/**
 * A class that provides [SafetyCenterData] objects and associated constants to facilitate asserting
 * on specific Safety Center states in SafetyCenter for testing.
 */
class SafetyCenterCtsData(context: Context) {

    private val safetyCenterResourcesContext = SafetyCenterResourcesContext.forTests(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)

    /**
     * The [SafetyCenterStatus] used when the overall status is unknown and no scan is in progress.
     */
    val safetyCenterStatusUnknown =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_title"),
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_ok_review_summary"))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
            .build()

    /**
     * Returns the [SafetyCenterStatus] used when the overall status is critical and no scan is in
     * progress for the given number of alerts.
     */
    fun safetyCenterStatusCritical(numAlerts: Int) =
        SafetyCenterStatus.Builder(
                safetyCenterResourcesContext.getStringByName(
                    "overall_severity_level_critical_safety_warning_title"),
                getAlertString(numAlerts))
            .setSeverityLevel(OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
            .build()

    /**
     * Returns a [SafetyCenterEntry] builder with a grey icon (for unknown severity), the summary
     * generally used for sources of the [SafetyCenterCtsConfigs], and a pending intent that
     * redirects to [TestActivity] for the given source, user id, and title.
     */
    fun safetyCenterEntryDefaultBuilder(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "OK",
        pendingIntent: PendingIntent? = safetySourceCtsData.testActivityRedirectPendingIntent
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId, userId), title)
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
            .setSummary("OK")
            .setPendingIntent(pendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)

    /**
     * Returns a [SafetyCenterEntry] with a grey icon (for unknown severity), the summary generally
     * used for sources of the [SafetyCenterCtsConfigs], and a pending intent that redirects to
     * Safety Center for the given source, user id, and title.
     */
    fun safetyCenterEntryDefault(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "OK",
        pendingIntent: PendingIntent? = safetySourceCtsData.testActivityRedirectPendingIntent
    ) = safetyCenterEntryDefaultBuilder(sourceId, userId, title, pendingIntent).build()

    /**
     * Returns a [SafetyCenterEntry] builder with no icon, the summary generally used for sources of
     * the [SafetyCenterCtsConfigs], and a pending intent that redirects to [TestActivity] for the
     * given source, user id, and title.
     */
    fun safetyCenterEntryDefaultStaticBuilder(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "OK"
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId, userId), title)
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
            .setSummary("OK")
            .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)

    /**
     * Returns a [SafetyCenterEntry] with a grey icon (for unknown severity), a refresh error
     * summary, and a pending intent that redirects to [TestActivity] for the given source, user id,
     * and title.
     */
    fun safetyCenterEntryError(sourceId: String) =
        safetyCenterEntryDefaultBuilder(sourceId).setSummary(getRefreshErrorString(1)).build()

    /**
     * Returns a disabled [SafetyCenterEntry] with a grey icon (for unspecified severity), a
     * standard summary, and a standard title for the given source and pending intent.
     */
    fun safetyCenterEntryUnspecified(
        sourceId: String,
        pendingIntent: PendingIntent? = safetySourceCtsData.testActivityRedirectPendingIntent
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId), "Unspecified title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
            .setSummary("Unspecified summary")
            .setPendingIntent(pendingIntent)
            .setEnabled(false)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    /**
     * Returns a [SafetyCenterEntry] builder with a green icon (for ok severity), a standard
     * summary, and a pending intent that redirects to [TestActivity] for the given source, user id,
     * and title.
     */
    fun safetyCenterEntryOkBuilder(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "Ok title"
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId, userId), title)
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_OK)
            .setSummary("Ok summary")
            .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)

    /**
     * Returns a [SafetyCenterEntry] with a green icon (for ok severity), a standard summary, and a
     * pending intent that redirects to [TestActivity] for the given source, user id, and title.
     */
    fun safetyCenterEntryOk(
        sourceId: String,
        userId: Int = UserHandle.myUserId(),
        title: CharSequence = "Ok title"
    ) = safetyCenterEntryOkBuilder(sourceId, userId, title).build()

    /**
     * Returns a [SafetyCenterEntry] with a yellow icon (for recommendation severity), a standard
     * title, and a pending intent that redirects to [TestActivity] for the given source and
     * summary.
     */
    fun safetyCenterEntryRecommendation(
        sourceId: String,
        summary: String = "Recommendation summary"
    ) =
        SafetyCenterEntry.Builder(entryId(sourceId), "Recommendation title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setSummary(summary)
            .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    /**
     * Returns a [SafetyCenterEntry] with a red icon (for critical severity), a standard title, a
     * standard summary, and a pending intent that redirects to [TestActivity] for the given source.
     */
    fun safetyCenterEntryCritical(sourceId: String) =
        SafetyCenterEntry.Builder(entryId(sourceId), "Critical title")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
            .setSummary("Critical summary")
            .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
            .build()

    /**
     * Returns an information [SafetyCenterIssue] for the given source and user id that is
     * consistent with information [SafetySourceIssue]s used in [SafetySourceCtsData].
     */
    fun safetyCenterIssueInformation(sourceId: String, userId: Int = UserHandle.myUserId()) =
        SafetyCenterIssue.Builder(
                issueId(sourceId, INFORMATION_ISSUE_ID, userId = userId),
                "Information issue title",
                "Information issue summary")
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_OK)
            .setShouldConfirmDismissal(false)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            issueActionId(
                                sourceId,
                                INFORMATION_ISSUE_ID,
                                INFORMATION_ISSUE_ACTION_ID,
                                userId),
                            "Review",
                            safetySourceCtsData.testActivityRedirectPendingIntent)
                        .build()))
            .build()

    /**
     * Returns a recommendation [SafetyCenterIssue] for the given source and user id that is
     * consistent with recommendation [SafetySourceIssue]s used in [SafetySourceCtsData].
     */
    fun safetyCenterIssueRecommendation(sourceId: String, userId: Int = UserHandle.myUserId()) =
        SafetyCenterIssue.Builder(
                issueId(sourceId, RECOMMENDATION_ISSUE_ID, userId = userId),
                "Recommendation issue title",
                "Recommendation issue summary")
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            issueActionId(
                                sourceId,
                                RECOMMENDATION_ISSUE_ID,
                                RECOMMENDATION_ISSUE_ACTION_ID,
                                userId),
                            "See issue",
                            safetySourceCtsData.testActivityRedirectPendingIntent)
                        .build()))
            .build()

    /**
     * Returns a critical [SafetyCenterIssue] for the given source and user id that is consistent
     * with critical [SafetySourceIssue]s used in [SafetySourceCtsData].
     */
    fun safetyCenterIssueCritical(
        sourceId: String,
        isActionInFlight: Boolean = false,
        userId: Int = UserHandle.myUserId()
    ) =
        SafetyCenterIssue.Builder(
                issueId(sourceId, CRITICAL_ISSUE_ID, userId = userId),
                "Critical issue title",
                "Critical issue summary")
            .setSeverityLevel(ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
            .setActions(
                listOf(
                    SafetyCenterIssue.Action.Builder(
                            issueActionId(
                                sourceId, CRITICAL_ISSUE_ID, CRITICAL_ISSUE_ACTION_ID, userId),
                            "Solve issue",
                            safetySourceCtsData.criticalIssueActionPendingIntent)
                        .setWillResolve(true)
                        .setIsInFlight(isActionInFlight)
                        .build()))
            .build()

    /**
     * Returns the [overall_severity_n_alerts_summary] string formatted for the given number of
     * alerts.
     */
    fun getAlertString(numberOfAlerts: Int): String =
        getIcuPluralsString("overall_severity_n_alerts_summary", numberOfAlerts)

    /** Returns the [refresh_error] string formatted for the given number of error entries. */
    fun getRefreshErrorString(numberOfErrorEntries: Int): String =
        getIcuPluralsString("refresh_error", numberOfErrorEntries)

    private fun getIcuPluralsString(name: String, count: Int, vararg formatArgs: Any): String {
        val messageFormat =
            MessageFormat(
                safetyCenterResourcesContext.getStringByName(name, formatArgs), Locale.getDefault())
        val arguments = ArrayMap<String, Any>()
        arguments["count"] = count
        return messageFormat.format(arguments)
    }

    companion object {
        /** The default [SafetyCenterData] returned by the Safety Center APIs. */
        val DEFAULT: SafetyCenterData =
            SafetyCenterData(
                SafetyCenterStatus.Builder("", "")
                    .setSeverityLevel(OVERALL_SEVERITY_LEVEL_UNKNOWN)
                    .build(),
                emptyList(),
                emptyList(),
                emptyList())

        /** Creates an ID for a Safety Center entry group. */
        fun entryGroupId(sourcesGroupId: String) =
            SafetyCenterIds.encodeToString(
                SafetyCenterEntryGroupId.newBuilder()
                    .setSafetySourcesGroupId(sourcesGroupId)
                    .build())

        /** Creates an ID for a Safety Center entry. */
        fun entryId(sourceId: String, userId: Int = UserHandle.myUserId()) =
            SafetyCenterIds.encodeToString(
                SafetyCenterEntryId.newBuilder()
                    .setSafetySourceId(sourceId)
                    .setUserId(userId)
                    .build())

        /** Creates an ID for a Safety Center issue. */
        fun issueId(
            sourceId: String,
            sourceIssueId: String,
            issueTypeId: String = ISSUE_TYPE_ID,
            userId: Int = UserHandle.myUserId()
        ) =
            SafetyCenterIds.encodeToString(
                SafetyCenterIssueId.newBuilder()
                    .setSafetyCenterIssueKey(
                        SafetyCenterIssueKey.newBuilder()
                            .setSafetySourceId(sourceId)
                            .setSafetySourceIssueId(sourceIssueId)
                            .setUserId(userId)
                            .build())
                    .setIssueTypeId(issueTypeId)
                    .build())

        /** Creates an ID for a Safety Center issue action. */
        fun issueActionId(
            sourceId: String,
            sourceIssueId: String,
            sourceIssueActionId: String,
            userId: Int = UserHandle.myUserId()
        ) =
            SafetyCenterIds.encodeToString(
                SafetyCenterIssueActionId.newBuilder()
                    .setSafetyCenterIssueKey(
                        SafetyCenterIssueKey.newBuilder()
                            .setSafetySourceId(sourceId)
                            .setSafetySourceIssueId(sourceIssueId)
                            .setUserId(userId)
                            .build())
                    .setSafetySourceIssueActionId(sourceIssueActionId)
                    .build())
    }
}
