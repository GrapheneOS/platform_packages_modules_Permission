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

package android.safetycenter.hostside

import android.cts.statsdatom.lib.ConfigUtils
import android.cts.statsdatom.lib.ReportUtils
import android.safetycenter.hostside.rules.HelperAppRule
import android.safetycenter.hostside.rules.RequireSafetyCenterRule
import com.android.compatibility.common.util.ApiLevelUtil
import com.android.os.AtomsProto.Atom
import com.android.os.AtomsProto.SafetyCenterInteractionReported
import com.android.os.AtomsProto.SafetyCenterInteractionReported.Action
import com.android.os.AtomsProto.SafetyCenterInteractionReported.ViewType
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Host-side tests for Safety Center statsd logging. */
@RunWith(DeviceJUnit4ClassRunner::class)
class SafetyCenterInteractionLoggingHostTest : BaseHostJUnit4Test() {

    @get:Rule(order = 1) val safetyCenterRule = RequireSafetyCenterRule(this)
    @get:Rule(order = 2)
    val helperAppRule = HelperAppRule(this, HelperApp.APK_NAME, HelperApp.PACKAGE_NAME)

    @Before
    fun setUp() {
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        ConfigUtils.uploadConfigForPushedAtom(
            device,
            safetyCenterRule.getSafetyCenterPackageName(),
            Atom.SAFETY_CENTER_INTERACTION_REPORTED_FIELD_NUMBER
        )
        // TODO(b/239682646): Consider adding a target preparer that unlocks the device (like CTS)
    }

    @After
    fun tearDown() {
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
    }

    @Test
    fun openSafetyCenter_recordsSafetyCenterViewedEvent() {
        helperAppRule.runTest(TEST_CLASS_NAME, testMethodName = "openSafetyCenter")

        val safetyCenterViewedAtoms = getInteractionReportedAtoms(Action.SAFETY_CENTER_VIEWED)

        assertThat(safetyCenterViewedAtoms).isNotEmpty()
    }

    @Test
    fun sendNotification_recordsNotificationPostedEvent() {
        assumeAtLeastUpsideDownCake("Safety Center notification APIs require Android U+")

        helperAppRule.runTest(
            testClassName = ".SafetyCenterNotificationLoggingHelperTests",
            testMethodName = "sendNotification"
        )

        val notificationPostedAtoms = getInteractionReportedAtoms(Action.NOTIFICATION_POSTED)

        assertThat(notificationPostedAtoms).hasSize(1)
        assertThat(notificationPostedAtoms.first().viewType)
            .isEqualTo(ViewType.VIEW_TYPE_NOTIFICATION)
    }

    @Test
    fun openSubpageFromIntentExtra_recordsEventWithUnknownNavigationSource() {
        assumeAtLeastUpsideDownCake("Safety Center subpages require Android U+")

        helperAppRule.runTest(TEST_CLASS_NAME, testMethodName = "openSubpageFromIntentExtra")

        val safetyCenterViewedAtoms = getInteractionReportedAtoms(Action.SAFETY_CENTER_VIEWED)

        assertThat(safetyCenterViewedAtoms).hasSize(1)
        with(safetyCenterViewedAtoms.first()) {
            assertThat(viewType).isEqualTo(ViewType.SUBPAGE)
            assertThat(navigationSource)
                .isEqualTo(SafetyCenterInteractionReported.NavigationSource.SOURCE_UNKNOWN)
            assertThat(sessionId).isNotNull()
        }
    }

    @Test
    fun openSubpageFromHomepage_recordsEventWithSafetyCenterNavigationSource() {
        assumeAtLeastUpsideDownCake("Safety Center subpages require Android U+")

        helperAppRule.runTest(TEST_CLASS_NAME, testMethodName = "openSubpageFromHomepage")

        val safetyCenterViewedAtoms = getInteractionReportedAtoms(Action.SAFETY_CENTER_VIEWED)
        val subpageViewedEvent = safetyCenterViewedAtoms.find { it.viewType == ViewType.SUBPAGE }

        assertThat(subpageViewedEvent).isNotNull()
        assertThat(subpageViewedEvent!!.navigationSource)
            .isEqualTo(SafetyCenterInteractionReported.NavigationSource.SAFETY_CENTER)
        assertThat(safetyCenterViewedAtoms.map { it.sessionId }.distinct()).hasSize(1)
    }

    @Test
    fun openSubpageFromSettingsSearch_recordsEventWithSettingsNavigationSource() {
        assumeAtLeastUpsideDownCake("Safety Center subpages require Android U+")

        helperAppRule.runTest(TEST_CLASS_NAME, testMethodName = "openSubpageFromSettingsSearch")

        val safetyCenterViewedAtoms = getInteractionReportedAtoms(Action.SAFETY_CENTER_VIEWED)

        assertThat(safetyCenterViewedAtoms).hasSize(1)
        with(safetyCenterViewedAtoms.first()) {
            assertThat(viewType).isEqualTo(ViewType.SUBPAGE)
            assertThat(navigationSource)
                .isEqualTo(SafetyCenterInteractionReported.NavigationSource.SETTINGS)
            assertThat(sessionId).isNotNull()
        }
    }

    // TODO(b/239682646): Add more tests

    private fun getInteractionReportedAtoms(action: SafetyCenterInteractionReported.Action) =
        ReportUtils.getEventMetricDataList(device)
            .mapNotNull { it.atom.safetyCenterInteractionReported }
            .filter { it.action == action }

    private fun assumeAtLeastUpsideDownCake(message: String) {
        assumeTrue(message, ApiLevelUtil.isAtLeast(device, 34))
    }

    private companion object {
        const val TEST_CLASS_NAME = ".SafetyCenterInteractionLoggingHelperTests"
    }
}
