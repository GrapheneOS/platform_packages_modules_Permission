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
import android.cts.statsdatom.lib.DeviceUtils
import android.cts.statsdatom.lib.ReportUtils
import com.android.os.AtomsProto.Atom
import com.android.os.AtomsProto.SafetySourceStateCollected
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Host-side tests for Safety Center statsd logging. */
@RunWith(DeviceJUnit4ClassRunner::class)
class SafetySourceStateCollectedLoggingHostTest : BaseHostJUnit4Test() {

    // Use lazy here because device is not available when the test is first constructed
    private val shouldRunTests: Boolean by lazy {
        // TODO(b/239682646): These tests should enable Safety Center
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

        val config = ConfigUtils.createConfigBuilder(device.getSafetyCenterPackageName())
        ConfigUtils.addGaugeMetric(config, Atom.SAFETY_STATE_FIELD_NUMBER)
        ConfigUtils.addEventMetric(config, Atom.SAFETY_SOURCE_STATE_COLLECTED_FIELD_NUMBER)
        ConfigUtils.uploadConfig(device, config)
        DeviceUtils.installTestApp(device, HelperApp.APK_NAME, HelperApp.PACKAGE_NAME, build)

        // TODO(b/239682646): Consider adding a target preparer that unlocks the device (like CTS)
    }

    @After
    fun tearDown() {
        if (!shouldRunTests) return

        ReportUtils.clearReports(device)
        ConfigUtils.removeConfig(device)
        DeviceUtils.uninstallTestApp(device, HelperApp.PACKAGE_NAME)
    }

    @Test
    fun triggerStatsPull_atomsPushedForAllSources() {
        device.runTest(TEST_CLASS_NAME, "triggerStatsPull")

        val sourceStateAtoms = getSafetySourceStateCollectedAtoms()

        assertThat(sourceStateAtoms.map { it.encodedSafetySourceId })
            .containsExactly(
                SOURCE_1_ENCODED_SOURCE_ID,
                SOURCE_2_ENCODED_SOURCE_ID,
                SOURCE_3_ENCODED_SOURCE_ID
            )
    }

    @Test
    fun triggerStatsPull_atomsHaveCollectionTypeAutomatic() {
        device.runTest(TEST_CLASS_NAME, "triggerStatsPull")

        val sourceStateAtoms = getSafetySourceStateCollectedAtoms()

        assertThat(sourceStateAtoms.map { it.collectionType }.distinct())
            .containsExactly(SafetySourceStateCollected.CollectionType.AUTOMATIC)
    }

    @Test
    fun setSafetySourceData_atomPushedForThatSource() {
        device.runTest(TEST_CLASS_NAME, "setSafetySourceData_source1")

        val sourceStateAtoms = getSafetySourceStateCollectedAtoms()

        assertThat(sourceStateAtoms.map { it.encodedSafetySourceId })
            .containsExactly(SOURCE_1_ENCODED_SOURCE_ID)
    }

    @Test
    fun setSafetySourceData_atomHasCollectionTypeSourceUpdated() {
        device.runTest(TEST_CLASS_NAME, "setSafetySourceData_source1")

        val sourceStateAtoms = getSafetySourceStateCollectedAtoms()

        assertThat(sourceStateAtoms.map { it.collectionType })
            .containsExactly(SafetySourceStateCollected.CollectionType.SOURCE_UPDATED)
    }

    private fun getSafetySourceStateCollectedAtoms() =
        ReportUtils.getEventMetricDataList(device)
            .mapNotNull { it.atom.safetySourceStateCollected }
            .filterNot {
                // Installing/uninstalling the helper app can cause Play Protect to run a scan and
                // push new data to Safety Center which interferes with the test results so we
                // specifically filter the resultant atoms out using the real encoded source ID.
                // Similar failures are also observed on Android Test Hub due to the background
                // location source (b/278782808)
                it.encodedSafetySourceId == PLAY_PROTECT_ENCODED_SOURCE_ID
                        || it.encodedSafetySourceId == BACKGROUND_LOCATION_ENCODED_SOURCE_ID
            }

    private companion object {
        const val TEST_CLASS_NAME = ".SafetySourceStateCollectedLoggingHelperTests"
        const val PLAY_PROTECT_ENCODED_SOURCE_ID = 7711894340233229936L
        const val BACKGROUND_LOCATION_ENCODED_SOURCE_ID = 7355693215512427559L
        const val SOURCE_1_ENCODED_SOURCE_ID = 6446219357586936066L
        const val SOURCE_2_ENCODED_SOURCE_ID = -5887429047684886602L
        const val SOURCE_3_ENCODED_SOURCE_ID = -619470868366498469L
    }
}
