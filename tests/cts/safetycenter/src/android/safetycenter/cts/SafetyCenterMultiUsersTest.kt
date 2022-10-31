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
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNKNOWN
import android.safetycenter.SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetyCenterEntry.SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetySourceData
import android.safetycenter.cts.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ACTION_TEST_ACTIVITY
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.COMPLEX_ALL_PROFILE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_DISABLED_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_GROUP_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_HIDDEN_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.DYNAMIC_IN_RIGID_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_ALL_OPTIONAL_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_ALL_PROFILE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_IN_RIGID_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_SOURCE_ALL_PROFILE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.ISSUE_ONLY_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ALL_PROFILE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ALL_PROFILE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_GROUP_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_ALL_OPTIONAL_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_BAREBONE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.STATIC_GROUP_ID
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.getWorkPolicyInfoConfig
import android.safetycenter.cts.testing.SafetyCenterCtsData
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import android.safetycenter.cts.testing.ShellPermissions.callWithShellPermissionIdentity
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextDisplayed
import android.safetycenter.cts.testing.UiTestHelper.waitAllTextNotDisplayed
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.OptionalBoolean.TRUE
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner
import com.android.safetycenter.resources.SafetyCenterResourcesContext
import com.google.common.base.Preconditions.checkState
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

/**
 * CTS tests for our APIs and UI on a device with multiple users. e.g. with a managed or secondary
 * user(s).
 */
@Ignore // Tests are causing flakiness in other tests.
@RunWith(BedsteadJUnit4::class)
// TODO(b/234108780): Add these to presubmits when we figure a way to make sure they don't fail due
// to timeouts with Bedstead.
class SafetyCenterMultiUsersTest {

    companion object {
        @JvmField @ClassRule @Rule val deviceState: DeviceState = DeviceState()
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val safetyCenterResourcesContext = SafetyCenterResourcesContext.forTests(context)
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)
    private val safetyCenterCtsData = SafetyCenterCtsData(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!

    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = context.deviceSupportsSafetyCenter()
    private var inQuietMode = false

    private val primaryProfileOnlyIssues =
        listOf(
            safetyCenterCtsData.safetyCenterIssueCritical(DYNAMIC_BAREBONE_ID),
            safetyCenterCtsData.safetyCenterIssueCritical(ISSUE_ONLY_BAREBONE_ID),
            safetyCenterCtsData.safetyCenterIssueRecommendation(DYNAMIC_DISABLED_ID),
            safetyCenterCtsData.safetyCenterIssueRecommendation(ISSUE_ONLY_ALL_OPTIONAL_ID),
            safetyCenterCtsData.safetyCenterIssueInformation(DYNAMIC_IN_RIGID_ID),
            safetyCenterCtsData.safetyCenterIssueInformation(ISSUE_ONLY_IN_RIGID_ID))

    private val dynamicBareboneDefault =
        safetyCenterCtsData.safetyCenterEntryDefault(DYNAMIC_BAREBONE_ID)

    private val dynamicBareboneUpdated =
        safetyCenterCtsData.safetyCenterEntryCritical(DYNAMIC_BAREBONE_ID)

    private val dynamicDisabledDefault =
        safetyCenterCtsData
            .safetyCenterEntryDefaultBuilder(DYNAMIC_DISABLED_ID)
            .setPendingIntent(null)
            .setEnabled(false)
            .build()

    private val dynamicDisabledUpdated =
        safetyCenterCtsData.safetyCenterEntryRecommendation(DYNAMIC_DISABLED_ID)

    private val dynamicDisabledForWorkDefaultBuilder
        get() =
            safetyCenterCtsData
                .safetyCenterEntryDefaultBuilder(
                    DYNAMIC_DISABLED_ID, userId = deviceState.workProfile().id(), title = "Paste")
                .setPendingIntent(null)
                .setEnabled(false)

    private val dynamicDisabledForWorkDefault
        get() = dynamicDisabledForWorkDefaultBuilder.build()

    private val dynamicDisabledForWorkPaused
        get() =
            dynamicDisabledForWorkDefaultBuilder
                // TODO(b/233188021): This needs to use the Enterprise API to override the "work"
                //  keyword.
                .setSummary(safetyCenterResourcesContext.getStringByName("work_profile_paused"))
                .build()

    private val dynamicDisabledForWorkUpdated
        get() = safetyCenterEntryOkForWork(DYNAMIC_DISABLED_ID, deviceState.workProfile().id())

    private val dynamicHiddenUpdated =
        safetyCenterCtsData.safetyCenterEntryUnspecified(DYNAMIC_HIDDEN_ID, pendingIntent = null)

    private val dynamicHiddenForWorkUpdated
        get() = safetyCenterEntryOkForWork(DYNAMIC_HIDDEN_ID, deviceState.workProfile().id())

    private val staticGroupBuilder =
        SafetyCenterEntryGroup.Builder(SafetyCenterCtsData.entryGroupId(STATIC_GROUP_ID), "OK")
            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNSPECIFIED)
            .setSeverityUnspecifiedIconType(SEVERITY_UNSPECIFIED_ICON_TYPE_PRIVACY)
            .setSummary("OK")

    private val staticBarebone =
        safetyCenterCtsData
            .safetyCenterEntryDefaultStaticBuilder(STATIC_BAREBONE_ID)
            .setSummary(null)
            .build()

    private val staticAllOptional =
        safetyCenterCtsData.safetyCenterEntryDefaultStaticBuilder(STATIC_ALL_OPTIONAL_ID).build()

    private val staticAllOptionalForWorkBuilder
        get() =
            safetyCenterCtsData
                .safetyCenterEntryDefaultStaticBuilder(
                    STATIC_ALL_OPTIONAL_ID,
                    userId = deviceState.workProfile().id(),
                    title = "Paste")
                .setPendingIntent(
                    createTestActivityRedirectPendingIntentForUser(
                        deviceState.workProfile().userHandle()))

    private val staticAllOptionalForWork
        get() = staticAllOptionalForWorkBuilder.build()

    private val staticAllOptionalForWorkPaused
        get() =
            staticAllOptionalForWorkBuilder
                // TODO(b/233188021): This needs to use the Enterprise API to override the "work"
                //  keyword.
                .setSummary(safetyCenterResourcesContext.getStringByName("work_profile_paused"))
                .setEnabled(false)
                .build()

    private val rigidEntry =
        SafetyCenterStaticEntry.Builder("OK")
            .setSummary("OK")
            .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
            .build()

    private val rigidEntryUpdated =
        SafetyCenterStaticEntry.Builder("Unspecified title")
            .setSummary("Unspecified summary")
            .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
            .build()

    private val rigidEntryForWorkBuilder
        get() =
            SafetyCenterStaticEntry.Builder("Paste")
                .setSummary("OK")
                .setPendingIntent(
                    createTestActivityRedirectPendingIntentForUser(
                        deviceState.workProfile().userHandle()))

    private val rigidEntryForWork
        get() = rigidEntryForWorkBuilder.build()

    private val rigidEntryForWorkPaused
        get() =
            rigidEntryForWorkBuilder
                // TODO(b/233188021): This needs to use the Enterprise API to override the "work"
                //  keyword.
                .setSummary(safetyCenterResourcesContext.getStringByName("work_profile_paused"))
                .build()

    private val rigidEntryForWorkUpdated =
        SafetyCenterStaticEntry.Builder("Unspecified title for Work")
            .setSummary("Unspecified summary")
            .setPendingIntent(safetySourceCtsData.testActivityRedirectPendingIntent)
            .build()

    private val safetyCenterDataForSecondaryUser
        get() =
            SafetyCenterData(
                safetyCenterCtsData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterCtsData.safetyCenterEntryDefault(
                            SINGLE_SOURCE_ALL_PROFILE_ID,
                            deviceState.secondaryUser().id(),
                            pendingIntent =
                                createTestActivityRedirectPendingIntentForUser(
                                    deviceState.secondaryUser().userHandle())))),
                emptyList())

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
    @Ignore
    // Tests that check the UI takes a lot of time and they might get timeout in the postsubmits.
    // TODO(b/242999951): Write this test using the APIs instead of checking the UI.
    fun launchActivity_withProfileOwner_displaysWorkPolicyInfo() {
        safetyCenterCtsHelper.setConfig(context.getWorkPolicyInfoConfig())

        findWorkPolicyInfo()
    }

    @Test
    @EnsureHasDeviceOwner
    @Ignore
    // Tests that check the UI takes a lot of time and they might get timeout in the postsubmits.
    // TODO(b/242999951): Write this test using the APIs instead of checking the UI.
    fun launchActivity_withDeviceOwner_displaysWorkPolicyInfo() {
        safetyCenterCtsHelper.setConfig(context.getWorkPolicyInfoConfig())

        findWorkPolicyInfo()
    }

    @Test
    @EnsureHasWorkProfile
    @Ignore
    // Tests that check the UI takes a lot of time and they might get timeout in the postsubmits.
    // TODO(b/242999951): Write this test using the APIs instead of checking the UI.
    fun launchActivity_withQuietModeEnabled_shouldNotDisplayWorkPolicyInfo() {
        safetyCenterCtsHelper.setConfig(context.getWorkPolicyInfoConfig())

        findWorkPolicyInfo()
        setQuietMode(true)
        context.launchSafetyCenterActivity { waitAllTextNotDisplayed("Your work policy info") }
    }

    @Test
    @Ignore
    // Test involving toggling of quiet mode are flaky.
    // TODO(b/237365018): Re-enable them back once we figure out a way to make them stable.
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetySourceData_withQuietModeEnabled_dataIsNotCleared() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        val dataForWork = safetySourceCtsData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID, dataForWork)

        setQuietMode(true)
        val apiSafetySourceDataForWork =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID)

        assertThat(apiSafetySourceDataForWork).isEqualTo(dataForWork)
    }

    @Test
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "Test takes too much time to setup")
    fun getSafetySourceData_afterSecondaryUserRemoved_returnsNull() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        val secondaryUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.secondaryUser().userHandle())
        val dataForSecondaryUser = safetySourceCtsData.information
        secondaryUserSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID, dataForSecondaryUser)
        checkState(
            secondaryUserSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID) == dataForSecondaryUser)

        deviceState.secondaryUser().remove()

        assertThat(
                secondaryUserSafetyCenterManager
                    .getSafetySourceDataWithInteractAcrossUsersPermission(
                        SINGLE_SOURCE_ALL_PROFILE_ID))
            .isNull()
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetySourceData_withoutInteractAcrossUserPermission_shouldThrowError() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)

        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        assertFailsWith(SecurityException::class) {
            managedSafetyCenterManager.getSafetySourceData(SINGLE_SOURCE_ALL_PROFILE_ID)
        }
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasNoWorkProfile
    fun getSafetyCenterData_withComplexConfigWithoutWorkProfile_returnsPrimaryDataFromConfig() {
        safetyCenterCtsHelper.setConfig(COMPLEX_ALL_PROFILE_CONFIG)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterCtsData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(
                                SafetyCenterCtsData.entryGroupId(DYNAMIC_GROUP_ID), "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                            .setSummary(
                                safetyCenterResourcesContext.getStringByName(
                                    "group_unknown_summary"))
                            .setEntries(listOf(dynamicBareboneDefault, dynamicDisabledDefault))
                            .build()),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(listOf(staticBarebone, staticAllOptional))
                            .build())),
                listOf(SafetyCenterStaticEntryGroup("OK", listOf(rigidEntry, rigidEntry))))
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withComplexConfigWithoutDataProvided_returnsDataFromConfig() {
        safetyCenterCtsHelper.setConfig(COMPLEX_ALL_PROFILE_CONFIG)

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterCtsData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(
                                SafetyCenterCtsData.entryGroupId(DYNAMIC_GROUP_ID), "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                            .setSummary(
                                safetyCenterResourcesContext.getStringByName(
                                    "group_unknown_summary"))
                            .setEntries(
                                listOf(
                                    dynamicBareboneDefault,
                                    dynamicDisabledDefault,
                                    dynamicDisabledForWorkDefault))
                            .build()),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(staticBarebone, staticAllOptional, staticAllOptionalForWork))
                            .build())),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(rigidEntry, rigidEntryForWork, rigidEntry, rigidEntryForWork))))
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withComplexConfigWithPrimaryDataProvided_returnsPrimaryDataProvided() {
        safetyCenterCtsHelper.setConfig(COMPLEX_ALL_PROFILE_CONFIG)
        updatePrimaryProfileSources()

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterCtsData.safetyCenterStatusCritical(6),
                primaryProfileOnlyIssues,
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(
                                SafetyCenterCtsData.entryGroupId(DYNAMIC_GROUP_ID), "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                            .setSummary("Critical summary")
                            .setEntries(
                                listOf(
                                    dynamicBareboneUpdated,
                                    dynamicDisabledUpdated,
                                    dynamicDisabledForWorkDefault,
                                    dynamicHiddenUpdated))
                            .build()),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(staticBarebone, staticAllOptional, staticAllOptionalForWork))
                            .build())),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(
                            rigidEntryUpdated, rigidEntryForWork, rigidEntry, rigidEntryForWork))))
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_withComplexConfigWithAllDataProvided_returnsAllDataProvided() {
        safetyCenterCtsHelper.setConfig(COMPLEX_ALL_PROFILE_CONFIG)
        updatePrimaryProfileSources()
        updateWorkProfileSources()

        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val managedUserId = deviceState.workProfile().id()
        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterCtsData.safetyCenterStatusCritical(11),
                listOf(
                    safetyCenterCtsData.safetyCenterIssueCritical(DYNAMIC_BAREBONE_ID),
                    safetyCenterCtsData.safetyCenterIssueCritical(ISSUE_ONLY_BAREBONE_ID),
                    safetyCenterCtsData.safetyCenterIssueRecommendation(DYNAMIC_DISABLED_ID),
                    safetyCenterCtsData.safetyCenterIssueRecommendation(ISSUE_ONLY_ALL_OPTIONAL_ID),
                    safetyCenterCtsData.safetyCenterIssueInformation(
                        DYNAMIC_DISABLED_ID, managedUserId),
                    safetyCenterCtsData.safetyCenterIssueInformation(
                        DYNAMIC_HIDDEN_ID, managedUserId),
                    safetyCenterCtsData.safetyCenterIssueInformation(
                        ISSUE_ONLY_ALL_OPTIONAL_ID, managedUserId),
                    safetyCenterCtsData.safetyCenterIssueInformation(DYNAMIC_IN_RIGID_ID),
                    safetyCenterCtsData.safetyCenterIssueInformation(
                        DYNAMIC_IN_RIGID_ID, managedUserId),
                    safetyCenterCtsData.safetyCenterIssueInformation(ISSUE_ONLY_IN_RIGID_ID),
                    safetyCenterCtsData.safetyCenterIssueInformation(
                        ISSUE_ONLY_IN_RIGID_ID, managedUserId)),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(
                                SafetyCenterCtsData.entryGroupId(DYNAMIC_GROUP_ID), "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                            .setSummary("Critical summary")
                            .setEntries(
                                listOf(
                                    dynamicBareboneUpdated,
                                    dynamicDisabledUpdated,
                                    dynamicDisabledForWorkUpdated,
                                    dynamicHiddenUpdated,
                                    dynamicHiddenForWorkUpdated))
                            .build()),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(staticBarebone, staticAllOptional, staticAllOptionalForWork))
                            .build())),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(
                            rigidEntryUpdated,
                            rigidEntryForWorkUpdated,
                            rigidEntry,
                            rigidEntryForWork))))
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Ignore
    // Test involving toggling of quiet mode are flaky.
    // TODO(b/237365018): Re-enable them back once we figure out a way to make them stable.
    fun getSafetyCenterData_withQuietMode_shouldHaveWorkProfilePausedSummaryAndNoWorkIssues() {
        safetyCenterCtsHelper.setConfig(COMPLEX_ALL_PROFILE_CONFIG)
        updatePrimaryProfileSources()
        updateWorkProfileSources()

        setQuietMode(true)
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        val safetyCenterDataFromComplexConfig =
            SafetyCenterData(
                safetyCenterCtsData.safetyCenterStatusCritical(6),
                primaryProfileOnlyIssues,
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(
                                SafetyCenterCtsData.entryGroupId(DYNAMIC_GROUP_ID), "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING)
                            .setSummary("Critical summary")
                            .setEntries(
                                listOf(
                                    dynamicBareboneUpdated,
                                    dynamicDisabledUpdated,
                                    dynamicDisabledForWorkPaused,
                                    dynamicHiddenUpdated))
                            .build()),
                    SafetyCenterEntryOrGroup(
                        staticGroupBuilder
                            .setEntries(
                                listOf(
                                    staticBarebone,
                                    staticAllOptional,
                                    staticAllOptionalForWorkPaused))
                            .build())),
                listOf(
                    SafetyCenterStaticEntryGroup(
                        "OK",
                        listOf(
                            rigidEntryUpdated,
                            rigidEntryForWorkPaused,
                            rigidEntry,
                            rigidEntryForWorkPaused))))
        assertThat(apiSafetyCenterData).isEqualTo(safetyCenterDataFromComplexConfig)
    }

    @Test
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "Test takes too much time to setup")
    fun getSafetyCenterData_withDataForDifferentUserProfileGroup_shouldBeUnaffected() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        val dataForPrimaryUser = safetySourceCtsData.information
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ALL_PROFILE_ID, dataForPrimaryUser)
        val dataForPrimaryUserWorkProfile = safetySourceCtsData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID, dataForPrimaryUserWorkProfile)

        val secondaryUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.secondaryUser().userHandle())
        val apiSafetyCenterDataForSecondaryUser =
            secondaryUserSafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission()

        assertThat(apiSafetyCenterDataForSecondaryUser).isEqualTo(safetyCenterDataForSecondaryUser)
    }

    @Test
    @Ignore // Removing a managed profile causes a refresh, which makes some tests flaky.
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun getSafetyCenterData_afterManagedProfileRemoved_returnsDefaultData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val safetyCenterDataWithWorkProfile =
            SafetyCenterData(
                safetyCenterCtsData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        SafetyCenterEntryGroup.Builder(
                                SafetyCenterCtsData.entryGroupId(SINGLE_SOURCE_GROUP_ID), "OK")
                            .setSeverityLevel(ENTRY_SEVERITY_LEVEL_UNKNOWN)
                            .setSummary(
                                safetyCenterResourcesContext.getStringByName(
                                    "group_unknown_summary"))
                            .setEntries(
                                listOf(
                                    safetyCenterCtsData.safetyCenterEntryDefault(
                                        SINGLE_SOURCE_ALL_PROFILE_ID),
                                    safetyCenterCtsData.safetyCenterEntryDefault(
                                        SINGLE_SOURCE_ALL_PROFILE_ID,
                                        deviceState.workProfile().id(),
                                        title = "Paste",
                                        pendingIntent =
                                            createTestActivityRedirectPendingIntentForUser(
                                                deviceState.workProfile().userHandle()))))
                            .build())),
                emptyList())
        checkState(
            safetyCenterManager.getSafetyCenterDataWithPermission() ==
                safetyCenterDataWithWorkProfile)
        checkState(
            managedSafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission() ==
                safetyCenterDataWithWorkProfile)

        deviceState.workProfile().remove()

        val safetyCenterDataForPrimaryUser =
            SafetyCenterData(
                safetyCenterCtsData.safetyCenterStatusUnknown,
                emptyList(),
                listOf(
                    SafetyCenterEntryOrGroup(
                        safetyCenterCtsData.safetyCenterEntryDefault(
                            SINGLE_SOURCE_ALL_PROFILE_ID))),
                emptyList())
        assertThat(safetyCenterManager.getSafetyCenterDataWithPermission())
            .isEqualTo(safetyCenterDataForPrimaryUser)
        assertThat(
                managedSafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission())
            .isEqualTo(SafetyCenterCtsData.DEFAULT)
    }

    @Test
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "Test takes too much time to setup")
    fun getSafetyCenterData_afterSecondaryUserRemoved_returnsDefaultData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        val secondaryUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.secondaryUser().userHandle())
        checkState(
            secondaryUserSafetyCenterManager
                .getSafetyCenterDataWithInteractAcrossUsersPermission() ==
                safetyCenterDataForSecondaryUser)

        deviceState.secondaryUser().remove()

        assertThat(
                secondaryUserSafetyCenterManager
                    .getSafetyCenterDataWithInteractAcrossUsersPermission())
            .isEqualTo(SafetyCenterCtsData.DEFAULT)
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_primaryProfileIssueOnlySource_shouldNotBeAbleToSetDataToWorkProfile() {
        safetyCenterCtsHelper.setConfig(ISSUE_ONLY_SOURCE_CONFIG)

        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val dataForWork =
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.criticalResolvingGeneralIssue)
        assertFailsWith(IllegalArgumentException::class) {
            managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
                ISSUE_ONLY_ALL_OPTIONAL_ID, dataForWork)
        }
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_withoutInteractAcrossUserPermission_shouldThrowError() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)
        val dataForWork = safetySourceCtsData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID, dataForWork)

        assertFailsWith(SecurityException::class) {
            managedSafetyCenterManager.setSafetySourceData(
                SINGLE_SOURCE_ALL_PROFILE_ID, dataForWork, EVENT_SOURCE_STATE_CHANGED)
        }
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_issuesOnlySourceWithWorkProfile_shouldBeAbleToSetData() {
        safetyCenterCtsHelper.setConfig(ISSUE_ONLY_SOURCE_ALL_PROFILE_CONFIG)

        val dataForPrimaryUser =
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.recommendationGeneralIssue)
        safetyCenterCtsHelper.setData(ISSUE_ONLY_ALL_PROFILE_SOURCE_ID, dataForPrimaryUser)
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val dataForWorkProfile =
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.criticalResolvingGeneralIssue)
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            ISSUE_ONLY_ALL_PROFILE_SOURCE_ID, dataForWorkProfile)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(ISSUE_ONLY_ALL_PROFILE_SOURCE_ID)
        val apiSafetySourceDataForWork =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                ISSUE_ONLY_ALL_PROFILE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataForPrimaryUser)
        assertThat(apiSafetySourceDataForWork).isEqualTo(dataForWorkProfile)
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_primaryProfileSource_shouldNotBeAbleToSetDataToWorkProfile() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        val dataForWork = safetySourceCtsData.informationWithIssueForWork
        assertFailsWith(IllegalArgumentException::class) {
            managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ID, dataForWork)
        }
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    fun setSafetySourceData_sourceWithWorkProfile_shouldBeAbleToSetData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_ALL_PROFILE_CONFIG)

        val dataForPrimaryUser = safetySourceCtsData.information
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ALL_PROFILE_ID, dataForPrimaryUser)
        val dataForWork = safetySourceCtsData.informationWithIssueForWork
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ALL_PROFILE_ID, dataForWork)

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ALL_PROFILE_ID)
        val apiSafetySourceDataForWork =
            managedSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ALL_PROFILE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataForPrimaryUser)
        assertThat(apiSafetySourceDataForWork).isEqualTo(dataForWork)
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    fun setSafetySourceData_forStoppedUser_shouldSetData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)
        deviceState.secondaryUser().stop()

        val dataForPrimaryUser = safetySourceCtsData.unspecified
        val secondaryUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.secondaryUser().userHandle())
        secondaryUserSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ID, dataForPrimaryUser)

        val apiSafetySourceData =
            secondaryUserSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceData).isEqualTo(dataForPrimaryUser)
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    fun setSafetySourceData_forBothPrimarySecondaryUser_shouldSetData() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val dataForPrimaryUser = safetySourceCtsData.information
        safetyCenterCtsHelper.setData(SINGLE_SOURCE_ID, dataForPrimaryUser)
        val dataForSecondaryUser = safetySourceCtsData.unspecified
        val secondaryUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.secondaryUser().userHandle())
        secondaryUserSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ID, dataForSecondaryUser)

        val apiSafetySourceDataForPrimaryUser =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        val apiSafetySourceDataForSecondaryUser =
            secondaryUserSafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
                SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceDataForPrimaryUser).isEqualTo(dataForPrimaryUser)
        assertThat(apiSafetySourceDataForSecondaryUser).isEqualTo(dataForSecondaryUser)
    }

    @Test
    @Postsubmit(reason = "Test takes too much time to setup")
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    fun setSafetySourceData_forSecondaryUser_shouldNotAffectDataForPrimaryUser() {
        safetyCenterCtsHelper.setConfig(SINGLE_SOURCE_CONFIG)

        val dataForSecondaryUser = safetySourceCtsData.unspecified
        val secondaryUserSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.secondaryUser().userHandle())
        secondaryUserSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            SINGLE_SOURCE_ID, dataForSecondaryUser)

        val apiSafetySourceDataForPrimaryUser =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceDataForPrimaryUser).isEqualTo(null)
    }

    private fun findWorkPolicyInfo() {
        context.launchSafetyCenterActivity {
            // TODO(b/233188021): This needs to use the Enterprise API to override the "work"
            //  keyword.
            waitAllTextDisplayed("Your work policy info", "Settings managed by your IT admin")
        }
    }

    private fun getSafetyCenterManagerForUser(userHandle: UserHandle): SafetyCenterManager {
        val contextForUser = getContextForUser(userHandle)
        return contextForUser.getSystemService(SafetyCenterManager::class.java)!!
    }

    private fun getContextForUser(userHandle: UserHandle): Context {
        return callWithShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL) {
            context.createContextAsUser(userHandle, 0)
        }
    }

    private fun createTestActivityRedirectPendingIntentForUser(user: UserHandle): PendingIntent {
        return callWithShellPermissionIdentity(INTERACT_ACROSS_USERS) {
            SafetySourceCtsData.createRedirectPendingIntent(
                getContextForUser(user), Intent(ACTION_TEST_ACTIVITY))
        }
    }

    private fun SafetyCenterManager.getSafetySourceDataWithInteractAcrossUsersPermission(
        id: String
    ): SafetySourceData? =
        callWithShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL) {
            getSafetySourceDataWithPermission(id)
        }

    private fun SafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
        id: String,
        dataToSet: SafetySourceData
    ) =
        callWithShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL) {
            setSafetySourceDataWithPermission(id, dataToSet, EVENT_SOURCE_STATE_CHANGED)
        }

    private fun SafetyCenterManager.getSafetyCenterDataWithInteractAcrossUsersPermission():
        SafetyCenterData =
        callWithShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL) {
            getSafetyCenterDataWithPermission()
        }

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

    private fun safetyCenterEntryOkForWork(sourceId: String, managedUserId: Int) =
        safetyCenterCtsData
            .safetyCenterEntryOkBuilder(sourceId, managedUserId, title = "Ok title for Work")
            .build()

    private fun updatePrimaryProfileSources() {
        safetyCenterCtsHelper.setData(
            DYNAMIC_BAREBONE_ID, safetySourceCtsData.criticalWithResolvingGeneralIssue)
        safetyCenterCtsHelper.setData(
            DYNAMIC_DISABLED_ID, safetySourceCtsData.recommendationWithGeneralIssue)
        safetyCenterCtsHelper.setData(DYNAMIC_HIDDEN_ID, safetySourceCtsData.unspecified)
        safetyCenterCtsHelper.setData(
            ISSUE_ONLY_BAREBONE_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.criticalResolvingGeneralIssue))
        safetyCenterCtsHelper.setData(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.recommendationGeneralIssue))
        safetyCenterCtsHelper.setData(DYNAMIC_IN_RIGID_ID, safetySourceCtsData.unspecifiedWithIssue)
        safetyCenterCtsHelper.setData(
            ISSUE_ONLY_IN_RIGID_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.informationIssue))
    }

    private fun updateWorkProfileSources() {
        val managedSafetyCenterManager =
            getSafetyCenterManagerForUser(deviceState.workProfile().userHandle())
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            DYNAMIC_DISABLED_ID, safetySourceCtsData.informationWithIssueForWork)
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            DYNAMIC_HIDDEN_ID, safetySourceCtsData.informationWithIssueForWork)
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            ISSUE_ONLY_ALL_OPTIONAL_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.informationIssue))
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            DYNAMIC_IN_RIGID_ID, safetySourceCtsData.unspecifiedWithIssueForWork)
        managedSafetyCenterManager.setSafetySourceDataWithInteractAcrossUsersPermission(
            ISSUE_ONLY_IN_RIGID_ID,
            SafetySourceCtsData.issuesOnly(safetySourceCtsData.informationIssue))
    }
}
