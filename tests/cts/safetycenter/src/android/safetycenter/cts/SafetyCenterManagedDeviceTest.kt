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

package android.safetycenter.cts

import android.Manifest.permission.INTERACT_ACROSS_USERS
import android.Manifest.permission.INTERACT_ACROSS_USERS_FULL
import android.content.Context
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetySourceData
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_ALL_PROFILE_SAFETY_SOURCE
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_ALL_OPTIONAL_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_ALL_PROFILE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_SOURCE_ALL_PROFILE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ALL_PROFILE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ALL_PROFILE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_GROUP_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_ALL_PROFILE_SOURCES_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.getWorkPolicyInfoConfig
import android.safetycenter.cts.testing.SafetyCenterCtsData
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import android.safetycenter.cts.testing.UiTestHelper.findAllText
import android.safetycenter.cts.testing.UiTestHelper.waitTextNotDisplayed
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.OptionalBoolean.TRUE
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner
import com.android.safetycenter.resources.SafetyCenterResourcesContext
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for our APIs and UI on a managed device (e.g. with managed profile(s)). */
@Ignore
@RunWith(BedsteadJUnit4::class)
// TODO(b/234108780): Enable these back when we figure a way to make sure they don't fail due to
// timeouts with Bedstead. Consider marking them as running only in post-submit in the meantime.
class SafetyCenterManagedDeviceTest {

    companion object {
        @JvmField @ClassRule @Rule val deviceState: DeviceState = DeviceState()
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safetyCenterResourcesContext = SafetyCenterResourcesContext.forTests(context)
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = context.deviceSupportsSafetyCenter()
    private var inQuietMode = false

    private val safetyCenterStatusOk =
        SafetyCenterStatus.Builder("Looks good", "This device is protected")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
            .build()
    private val staticEntry =
        SafetyCenterStaticEntry.Builder("OK")
            .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
            .setSummary("OK")
            .build()
    private val staticEntryForWork
        get() =
            SafetyCenterStaticEntry.Builder("Attention")
                .setSummary("OK")
                .setPendingIntent(redirectPendingIntentForWork)
                .build()
    private val staticEntryForWorkQuietMode
        get() =
            SafetyCenterStaticEntry.Builder("Attention")
                // TODO(b/233188021): This needs to use the Entreprise API to override the "work"
                // keyword.
                .setSummary(safetyCenterResourcesContext.getStringByName("work_profile_paused"))
                .setPendingIntent(redirectPendingIntentForWork)
                .build()

    private val redirectPendingIntentForWork
        get() =
            callWithShellPermissionIdentity(
                { SafetySourceCtsData.createRedirectPendingIntent(getManagedContext()) },
                INTERACT_ACROSS_USERS)

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(shouldRunTests)
    }

    @Before
    fun enableSafetyCenterBeforeTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.setup()
    }

    @After
    fun clearDataAfterTest() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterCtsHelper.reset()
        resetQuietMode()
    }

    @Test
    @EnsureHasWorkProfile
    fun launchActivity_withProfileOwner_displaysWorkPolicyInfo() {
        safetyCenterCtsHelper.setConfig(context.getWorkPolicyInfoConfig())

        findWorkPolicyInfo()
    }

    @Test
    @EnsureHasDeviceOwner
    fun launchActivity_withDeviceOwner_displaysWorkPolicyInfo() {
        safetyCenterCtsHelper.setConfig(context.getWorkPolicyInfoConfig())

        findWorkPolicyInfo()
    }

    @Test
    @EnsureHasWorkProfile
    fun launchActivity_withQuietModeEnabled_shouldNotDisplayWorkPolicyInfo() {
        safetyCenterCtsHelper.setConfig(context.getWorkPolicyInfoConfig())

        findWorkPolicyInfo()
        setQuietMode(true)
        context.launchSafetyCenterActivity { waitTextNotDisplayed("Your work policy info") }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun launchActivity_sourceWithWorkProfile_showBothEntriesWithDefaultInformation() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)

        val titleResId = DYNAMIC_ALL_PROFILE_SAFETY_SOURCE.titleResId
        val titleForWorkResId = DYNAMIC_ALL_PROFILE_SAFETY_SOURCE.titleForWorkResId
        context.launchSafetyCenterActivity {
            findAllText(context.getString(titleResId), context.getString(titleForWorkResId))
        }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetySourceData_withoutInteractAcrossUserPermission_shouldThrowError() {
        val managedSafetyCenterManager = getManagedSafetyCenterManager()
        val setData = safetySourceCtsData.information
        val setDataForWork = safetySourceCtsData.informationForWork

        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ALL_PROFILE_ID, setData)
        managedSafetyCenterManager.setSafetySourceDataWithPermissionForManagedUser(
            SINGLE_SOURCE_ALL_PROFILE_ID, setDataForWork)

        assertFailsWith(SecurityException::class) {
            managedSafetyCenterManager.getSafetySourceData(SINGLE_SOURCE_ALL_PROFILE_ID)
        }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_staticSourceWithWorkProfile_shouldBeAbleToGetData() {
        safetyCenterCtsHelper.setConfig(STATIC_ALL_PROFILE_SOURCES_CONFIG)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterStaticData =
            SafetyCenterData(
                safetyCenterStatusOk,
                emptyList(),
                emptyList(),
                listOf(SafetyCenterStaticEntryGroup("OK", listOf(staticEntry, staticEntryForWork))))
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterStaticData)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_primaryProfileIssueOnlySource_shouldNotBeAbleToSetDataToWorkProfile() {
        safetyCenterCtsHelper.setConfig(ISSUE_ONLY_SOURCE_CONFIG)

        val managedSafetyCenterManager = getManagedSafetyCenterManager()
        val setDataForWork = safetySourceCtsData.informationForWork
        assertFailsWith(IllegalArgumentException::class) {
            managedSafetyCenterManager.setSafetySourceDataWithPermissionForManagedUser(
                ISSUE_ONLY_ALL_OPTIONAL_ID, setDataForWork)
        }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_withoutInteractAcrossUserPermission_shouldThrowError() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        val setData = safetySourceCtsData.information
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ALL_PROFILE_ID, setData)
        val managedSafetyCenterManager = getManagedSafetyCenterManager()
        val setDataForWork = safetySourceCtsData.informationForWork
        managedSafetyCenterManager.setSafetySourceDataWithPermissionForManagedUser(
            SINGLE_SOURCE_ALL_PROFILE_ID, setDataForWork)

        assertFailsWith(SecurityException::class) {
            managedSafetyCenterManager.setSafetySourceData(
                SINGLE_SOURCE_ALL_PROFILE_ID, setDataForWork, EVENT_SOURCE_STATE_CHANGED)
        }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_issuesOnlySourceWithWorkProfile_shouldBeAbleToSetData() {
        val managedSafetyCenterManager = getManagedSafetyCenterManager()
        val dataToSet =
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.recommendationGeneralIssue)
        val dataToSetForWork =
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.criticalResolvingGeneralIssue)
        safetyCenterCtsHelper.setConfig(ISSUE_ONLY_SOURCE_ALL_PROFILE_CONFIG)
        safetyCenterCtsHelper.setData(ISSUE_ONLY_ALL_PROFILE_SOURCE_ID, dataToSet)
        managedSafetyCenterManager.setSafetySourceDataWithPermissionForManagedUser(
            ISSUE_ONLY_ALL_PROFILE_SOURCE_ID, dataToSetForWork)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(ISSUE_ONLY_ALL_PROFILE_SOURCE_ID)
        val apiSafetySourceDataForWork =
            managedSafetyCenterManager.getSafetySourceDataWithPermissionForManagedUser(
                ISSUE_ONLY_ALL_PROFILE_SOURCE_ID)

        assertThat(apiSafetySourceData).isEqualTo(dataToSet)
        assertThat(apiSafetySourceDataForWork).isEqualTo(dataToSetForWork)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_primaryProfileSource_shouldNotBeAbleToSetDataToWorkProfile() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val managedSafetyCenterManager = getManagedSafetyCenterManager()
        val setDataForWork = safetySourceCtsData.informationForWork
        assertFailsWith(IllegalArgumentException::class) {
            managedSafetyCenterManager.setSafetySourceDataWithPermissionForManagedUser(
                SINGLE_SOURCE_ID, setDataForWork)
        }
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_sourceWithWorkProfile_bothEntriesShouldShowWhenQuietModeIsEnabled() {
        val managedSafetyCenterManager = getManagedSafetyCenterManager()
        val setData = safetySourceCtsData.information
        val setDataForWork = safetySourceCtsData.informationForWork
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ALL_PROFILE_ID, setData)
        managedSafetyCenterManager.setSafetySourceDataWithPermissionForManagedUser(
            SINGLE_SOURCE_ALL_PROFILE_ID, setDataForWork)

        setQuietMode(true)
        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ALL_PROFILE_ID)
        val apiSafetySourceDataForWork =
            managedSafetyCenterManager.getSafetySourceDataWithPermissionForManagedUser(
                SINGLE_SOURCE_ALL_PROFILE_ID)

        assertThat(apiSafetySourceData).isEqualTo(setData)
        assertThat(apiSafetySourceDataForWork).isEqualTo(setDataForWork)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_staticSourceWithQuietMode_shouldHaveWorkProfilePausedSummary() {
        safetyCenterCtsHelper.setConfig(STATIC_ALL_PROFILE_SOURCES_CONFIG)

        setQuietMode(true)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterStaticData =
            SafetyCenterData(
                safetyCenterStatusOk,
                emptyList(),
                emptyList(),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK", listOf(staticEntry, staticEntryForWorkQuietMode))))
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterStaticData)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_quietModeEnabled_workEntryShouldBeDisabled() {
        val managedSafetyCenterManager = getManagedSafetyCenterManager()
        val setData = safetySourceCtsData.information
        val setDataForWork = safetySourceCtsData.informationForWork
        val managedUserId = deviceState.workProfile().id()
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ALL_PROFILE_ID, setData)
        managedSafetyCenterManager.setSafetySourceDataWithPermissionForManagedUser(
            SINGLE_SOURCE_ALL_PROFILE_ID, setDataForWork)

        setQuietMode(true)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val entry =
            SafetyCenterEntry.Builder(
                    SafetyCenterCtsData.entryId(SINGLE_SOURCE_ALL_PROFILE_ID), "Ok title")
                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_OK)
                .setSummary("Ok summary")
                .setPendingIntent(safetySourceCtsData.redirectPendingIntent)
                .setSeverityUnspecifiedIconType(
                    SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
                .build()
        val entryForWork =
            SafetyCenterEntry.Builder(
                    SafetyCenterCtsData.entryId(SINGLE_SOURCE_ALL_PROFILE_ID, managedUserId),
                    context.getString(DYNAMIC_ALL_PROFILE_SAFETY_SOURCE.titleForWorkResId))
                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                // TODO(b/233188021): This needs to use the Entreprise API to override the "work"
                // keyword.
                .setSummary(safetyCenterResourcesContext.getStringByName("work_profile_paused"))
                .setPendingIntent(redirectPendingIntentForWork)
                .setSeverityUnspecifiedIconType(
                    SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_NO_RECOMMENDATION)
                .setEnabled(false)
                .build()
        val entryGroup =
            SafetyCenterEntryGroup.Builder(
                    SafetyCenterCtsData.entryGroupId(SINGLE_SOURCE_GROUP_ID), "OK")
                .setSeverityLevel(ENTRY_SEVERITY_LEVEL_OK)
                .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_NO_ICON)
                .setSummary("OK")
                .setEntries(listOf(entry, entryForWork))
                .build()
        val safetyCenterData =
            SafetyCenterData(
                safetyCenterStatusOk,
                emptyList(),
                listOf(SafetyCenterEntryOrGroup(entryGroup)),
                emptyList())
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterData)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_sourceWithWorkProfile_shouldBeAbleToSetData() {
        val managedSafetyCenterManager = getManagedSafetyCenterManager()
        val setData = safetySourceCtsData.information
        val setDataForWork = safetySourceCtsData.informationForWork
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ALL_PROFILE_ID, setData)
        managedSafetyCenterManager.setSafetySourceDataWithPermissionForManagedUser(
            SINGLE_SOURCE_ALL_PROFILE_ID, setDataForWork)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ALL_PROFILE_ID)
        val apiSafetySourceDataForWork =
            managedSafetyCenterManager.getSafetySourceDataWithPermissionForManagedUser(
                SINGLE_SOURCE_ALL_PROFILE_ID)

        assertThat(apiSafetySourceData).isEqualTo(setData)
        assertThat(apiSafetySourceDataForWork).isEqualTo(setDataForWork)
    }

    private fun findWorkPolicyInfo() {
        context.launchSafetyCenterActivity {
            // TODO(b/233188021): This test will fail if these strings are overridden by OEMS.
            findAllText("Your work policy info", "Settings managed by your IT admin")
        }
    }

    private fun getManagedSafetyCenterManager(): SafetyCenterManager {
        val managedContext: Context = getManagedContext()
        return managedContext.getSystemService(SafetyCenterManager::class.java)!!
    }

    private fun getManagedContext(): Context {
        return callWithShellPermissionIdentity(
            { context.createContextAsUser(deviceState.workProfile().userHandle(), 0) },
            INTERACT_ACROSS_USERS_FULL)
    }

    private fun SafetyCenterManager.getSafetySourceDataWithPermissionForManagedUser(
        id: String
    ): SafetySourceData? =
        callWithShellPermissionIdentity(
            { getSafetySourceDataWithPermission(id) }, INTERACT_ACROSS_USERS_FULL)

    private fun SafetyCenterManager.setSafetySourceDataWithPermissionForManagedUser(
        id: String,
        dataToSet: SafetySourceData
    ) =
        callWithShellPermissionIdentity(
            { setSafetySourceDataWithPermission(id, dataToSet, EVENT_SOURCE_STATE_CHANGED) },
            INTERACT_ACROSS_USERS_FULL)

    private fun setQuietMode(value: Boolean) {
        deviceState.workProfile().setQuietMode(value)
        inQuietMode = value
    }

    private fun resetQuietMode() {
        if (!inQuietMode) {
            return
        }
        setQuietMode(false)
    }
}
