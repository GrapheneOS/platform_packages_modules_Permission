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

import android.cts.statsdatom.lib.AtomTestUtils
import android.cts.statsdatom.lib.ConfigUtils
import android.cts.statsdatom.lib.DeviceUtils
import android.cts.statsdatom.lib.ReportUtils
import com.android.os.AtomsProto.Atom
import com.android.os.AtomsProto.SafetyCenterInteractionReported
import com.android.tradefed.device.ITestDevice
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.android.tradefed.util.CommandStatus
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Host-side tests for Safety Center statsd logging. */
@RunWith(DeviceJUnit4ClassRunner::class)
class SafetyCenterInteractionLoggingHostTest : BaseHostJUnit4Test() {

    private val shouldRunTests: Boolean by lazy {
        // Device is not available when the test is first constructed
        // TODO(b/239682646): These tests should enable safety center instead of only running when
        // it's already enabled.
        device.supportsSafetyCenter() && device.isSafetyCenterEnabled()
    }

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(shouldRunTests)
    }

    @Before
    fun setUp() {
        if (!shouldRunTests) return

        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        ConfigUtils.uploadConfigForPushedAtom(
            device,
            getSafetyCenterPackageName(),
            Atom.SAFETY_CENTER_INTERACTION_REPORTED_FIELD_NUMBER
        )
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG.toLong())

        DeviceUtils.installTestApp(device, HELPER_APK_NAME, HELPER_PACKAGE, build)

        // TODO(b/239682646): Consider adding a target preparer that unlocks the device (like CTS)
    }

    @After
    fun tearDown() {
        if (!shouldRunTests) return

        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        DeviceUtils.uninstallTestApp(device, HELPER_PACKAGE)
    }

    @Test
    fun openSafetyCenter_recordsSafetyCenterViewedEvent() {
        executeDeviceTest(testMethodName = "openSafetyCenter")

        val safetyCenterViewedEvents =
            filterEventsByAction(SafetyCenterInteractionReported.Action.SAFETY_CENTER_VIEWED)

        assertThat(safetyCenterViewedEvents).isNotEmpty()
    }

    @Test
    fun sendNotification_recordsNotificationPostedEvent() {
        executeDeviceTest(
            testClassName = ".NotificationLoggingHelperTests",
            testMethodName = "sendNotification"
        )

        val notificationPostedEvents =
            filterEventsByAction(SafetyCenterInteractionReported.Action.NOTIFICATION_POSTED)

        assertThat(notificationPostedEvents).hasSize(1)
        val atom = notificationPostedEvents.first().atom.safetyCenterInteractionReported
        assertThat(atom.viewType)
            .isEqualTo(SafetyCenterInteractionReported.ViewType.VIEW_TYPE_NOTIFICATION)
    }

    @Test
    fun openSubpageFromIntentExtra_recordsEventWithUnknownNavigationSource() {
        executeDeviceTest(testMethodName = "openSubpageFromIntentExtra")

        val safetyCenterViewedEvents =
            filterEventsByAction(SafetyCenterInteractionReported.Action.SAFETY_CENTER_VIEWED)

        assertThat(safetyCenterViewedEvents).hasSize(1)
        val atom = safetyCenterViewedEvents.first().atom.safetyCenterInteractionReported
        assertThat(atom.viewType).isEqualTo(SafetyCenterInteractionReported.ViewType.SUBPAGE)
        assertThat(atom.navigationSource)
            .isEqualTo(SafetyCenterInteractionReported.NavigationSource.SOURCE_UNKNOWN)
        assertThat(atom.sessionId).isNotNull()
    }

    @Test
    fun openSubpageFromHomepage_recordsEventWithSafetyCenterNavigationSource() {
        executeDeviceTest(testMethodName = "openSubpageFromHomepage")

        val safetyCenterViewedEvents =
            filterEventsByAction(SafetyCenterInteractionReported.Action.SAFETY_CENTER_VIEWED)

        assertThat(safetyCenterViewedEvents).hasSize(3)
        val firstAtom = safetyCenterViewedEvents[0].atom.safetyCenterInteractionReported
        assertThat(firstAtom.viewType).isEqualTo(SafetyCenterInteractionReported.ViewType.FULL)

        val secondAtom = safetyCenterViewedEvents[1].atom.safetyCenterInteractionReported
        assertThat(secondAtom.viewType).isEqualTo(SafetyCenterInteractionReported.ViewType.SUBPAGE)
        assertThat(secondAtom.navigationSource)
            .isEqualTo(SafetyCenterInteractionReported.NavigationSource.SAFETY_CENTER)

        val thirdAtom = safetyCenterViewedEvents[2].atom.safetyCenterInteractionReported
        assertThat(thirdAtom.viewType).isEqualTo(SafetyCenterInteractionReported.ViewType.FULL)

        assertThat(firstAtom.sessionId).isEqualTo(secondAtom.sessionId)
        assertThat(secondAtom.sessionId).isEqualTo(thirdAtom.sessionId)
    }

    @Test
    fun openSubpageFromSettingsSearch_recordsEventWithSettingsNavigationSource() {
        executeDeviceTest(testMethodName = "openSubpageFromSettingsSearch")

        val safetyCenterViewedEvents =
            filterEventsByAction(SafetyCenterInteractionReported.Action.SAFETY_CENTER_VIEWED)

        assertThat(safetyCenterViewedEvents).hasSize(1)
        val atom = safetyCenterViewedEvents.first().atom.safetyCenterInteractionReported
        assertThat(atom.viewType).isEqualTo(SafetyCenterInteractionReported.ViewType.SUBPAGE)
        assertThat(atom.navigationSource)
            .isEqualTo(SafetyCenterInteractionReported.NavigationSource.SETTINGS)
        assertThat(atom.sessionId).isNotNull()
    }

    // TODO(b/239682646): Add more tests

    private fun ITestDevice.supportsSafetyCenter(): Boolean {
        val commandResult = executeShellV2Command("cmd safety_center supported")

        if (commandResult.status != CommandStatus.SUCCESS) {
            throw AssertionError("Unable to check if Safety Center is supported: $commandResult")
        }

        return commandResult.stdout.trim().toBoolean()
    }

    private fun ITestDevice.isSafetyCenterEnabled(): Boolean {
        val commandResult = executeShellV2Command("cmd safety_center enabled")

        if (commandResult.status != CommandStatus.SUCCESS) {
            throw AssertionError("Unable to check if Safety Center is enabled: $commandResult")
        }

        return commandResult.stdout.trim().toBoolean()
    }

    private fun getSafetyCenterPackageName(): String =
        device.executeShellCommand("cmd safety_center package-name").trim()

    private fun executeDeviceTest(
        testPkgName: String = HELPER_PACKAGE,
        testClassName: String = HELPER_TEST_CLASS_NAME,
        testMethodName: String = "openSafetyCenter"
    ) {
        DeviceUtils.runDeviceTests(device, testPkgName, testClassName, testMethodName)
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG.toLong()) // Wait for report to be updated
    }

    private fun filterEventsByAction(action: SafetyCenterInteractionReported.Action) =
        ReportUtils.getEventMetricDataList(device).filter {
            it.atom.safetyCenterInteractionReported.action == action
        }

    private companion object {
        const val HELPER_APK_NAME = "SafetyCenterHostSideTestsHelper.apk"
        const val HELPER_PACKAGE = "android.safetycenter.hostside.device"
        const val HELPER_TEST_CLASS_NAME = ".SafetyCenterInteractionLoggingHelperTests"
    }
}
