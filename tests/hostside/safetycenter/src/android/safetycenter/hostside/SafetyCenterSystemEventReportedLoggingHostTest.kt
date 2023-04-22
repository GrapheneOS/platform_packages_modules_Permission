/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.os.AtomsProto.Atom
import com.android.os.AtomsProto.SafetyCenterSystemEventReported.EventType
import com.android.os.AtomsProto.SafetyCenterSystemEventReported.Result
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
class SafetyCenterSystemEventReportedLoggingHostTest : BaseHostJUnit4Test() {

    private val safetyCenterRule = RequireSafetyCenterRule(this)
    private val helperAppRule = HelperAppRule(this, HelperApp.APK_NAME, HelperApp.PACKAGE_NAME)

    @Rule
    @JvmField
    val rules: RuleChain = RuleChain.outerRule(safetyCenterRule).around(helperAppRule)

    @Before
    fun setUp() {
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        ConfigUtils.uploadConfigForPushedAtom(
            device,
            safetyCenterRule.getSafetyCenterPackageName(),
            Atom.SAFETY_CENTER_SYSTEM_EVENT_REPORTED_FIELD_NUMBER
        )
        // TODO(b/239682646): Consider adding a target preparer that unlocks the device (like CTS)
    }

    @After
    fun tearDown() {
        ReportUtils.clearReports(device)
        ConfigUtils.removeConfig(device)
    }

    @Test
    fun refreshAllSources_allSourcesSuccessful_successAtomsForEachSourceAndOverallCompletion() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "refreshAllSources_reasonPageOpen_allSuccessful"
        )

        val systemEventAtoms =
            ReportUtils.getEventMetricDataList(device).mapNotNull {
                it.atom.safetyCenterSystemEventReported
            }

        assertThat(systemEventAtoms.groupingBy { it.eventType }.eachCount())
            .containsExactly(
                EventType.COMPLETE_GET_NEW_DATA,
                1, // Overall refresh complete
                EventType.SINGLE_SOURCE_GET_NEW_DATA,
                3 // For the three sources in the multiple sources test config
            )
        assertThat(systemEventAtoms.groupingBy { it.result }.eachCount())
            .containsExactly(Result.SUCCESS, 4)
    }

    @Test
    fun refreshAllSources_oneSourceError_errorAtomForThatSourceAndOverall() {
        helperAppRule.runTest(
            ".SafetySourceStateCollectedLoggingHelperTests",
            "refreshAllSources_reasonPageOpen_oneError"
        )

        val systemEventAtoms =
            ReportUtils.getEventMetricDataList(device).mapNotNull {
                it.atom.safetyCenterSystemEventReported
            }

        assertThat(systemEventAtoms.groupingBy { Pair(it.eventType, it.result) }.eachCount())
            .containsExactly(
                Pair(EventType.COMPLETE_GET_NEW_DATA, Result.ERROR),
                1, // Overall refresh is an error
                Pair(EventType.SINGLE_SOURCE_GET_NEW_DATA, Result.ERROR),
                1, // For the source which had an error
                Pair(EventType.SINGLE_SOURCE_GET_NEW_DATA, Result.SUCCESS),
                2 // The remaining two sources
            )
    }
}
