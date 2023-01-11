/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.Context
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.addOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.clearSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterConfigWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetyCenterDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.getSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.isSafetyCenterEnabledWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.removeOnSafetyCenterDataChangedListenerWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.reportSafetySourceErrorWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetyCenterConfigForTestsWithPermission
import android.safetycenter.cts.testing.SafetyCenterApisWithShellPermissions.setSafetySourceDataWithPermission
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_CONFIG
import android.safetycenter.cts.testing.SafetyCenterCtsConfigs.SINGLE_SOURCE_ID
import android.safetycenter.cts.testing.SafetyCenterCtsData
import android.safetycenter.cts.testing.SafetyCenterCtsHelper
import android.safetycenter.cts.testing.SafetyCenterCtsListener
import android.safetycenter.cts.testing.SafetyCenterEnabledChangedReceiver
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SafetySourceCtsData
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ACTION_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.CRITICAL_ISSUE_ID
import android.safetycenter.cts.testing.SafetySourceCtsData.Companion.EVENT_SOURCE_STATE_CHANGED
import android.safetycenter.cts.testing.SafetySourceReceiver
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.executeSafetyCenterIssueActionWithPermissionAndWait
import android.safetycenter.cts.testing.SafetySourceReceiver.Companion.refreshSafetySourcesWithReceiverPermissionAndWait
import android.safetycenter.cts.testing.SettingsPackage.getSettingsPackageName
import android.safetycenter.cts.testing.UiTestHelper.waitDisplayed
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.launchSafetyCenterActivity
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for our APIs and UI on devices that do not support Safety Center. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterUnsupportedTest {

    @get:Rule val disableAnimationRule = DisableAnimationRule()

    @get:Rule val freezeRotationRule = FreezeRotationRule()

    private val context: Context = getApplicationContext()
    private val safetyCenterCtsHelper = SafetyCenterCtsHelper(context)
    private val safetySourceCtsData = SafetySourceCtsData(context)
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = !context.deviceSupportsSafetyCenter()

    @Before
    fun assumeDeviceDoesntSupportSafetyCenterToRunTests() {
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
    }

    @Test
    fun launchActivity_opensSettings() {
        context.launchSafetyCenterActivity {
            waitDisplayed(By.pkg(context.getSettingsPackageName()))
        }
    }

    @Test
    fun isSafetyCenterEnabled_withFlagEnabled_returnsFalse() {
        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_withFlagDisabled_returnsFalse() {
        safetyCenterCtsHelper.setEnabled(false)

        val isSafetyCenterEnabled = safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.isSafetyCenterEnabled }
    }

    @Test
    fun setAndGetSafetySourceData_doesntSetData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue,
            EVENT_SOURCE_STATE_CHANGED
        )

        val apiSafetySourceData =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        assertThat(apiSafetySourceData).isNull()
    }

    @Test
    fun setSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetySourceData(
                SINGLE_SOURCE_ID,
                safetySourceCtsData.unspecified,
                EVENT_SOURCE_STATE_CHANGED
            )
        }
    }

    @Test
    fun getSafetySourceData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.getSafetySourceData(SINGLE_SOURCE_ID)
        }
    }

    @Test
    fun reportSafetySourceError_doesntCallListener() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)
        val listener = SafetyCenterCtsListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        safetyCenterManager.reportSafetySourceErrorWithPermission(
            SINGLE_SOURCE_ID,
            SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun reportSafetySourceError_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.reportSafetySourceError(
                SINGLE_SOURCE_ID,
                SafetySourceErrorDetails(EVENT_SOURCE_STATE_CHANGED)
            )
        }
    }

    @Test
    fun safetyCenterEnabledChanged_withImplicitReceiver_doesntCallReceiver() {
        val enabledChangedReceiver = SafetyCenterEnabledChangedReceiver(context)

        assertFailsWith(TimeoutCancellationException::class) {
            enabledChangedReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false,
                TIMEOUT_SHORT
            )
        }
        enabledChangedReceiver.unregister()
    }

    @Test
    fun safetyCenterEnabledChanged_withSourceReceiver_doesntCallReceiver() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)

        assertFailsWith(TimeoutCancellationException::class) {
            SafetySourceReceiver.setSafetyCenterEnabledWithReceiverPermissionAndWait(
                false,
                TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun refreshSafetySources_doesntRefreshSources() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithReceiverPermissionAndWait(
                REFRESH_REASON_PAGE_OPEN,
                TIMEOUT_SHORT
            )
        }
    }

    @Test
    fun refreshSafetySources_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_RESCAN_BUTTON_CLICK)
        }
    }

    @Test
    fun getSafetyCenterConfig_isNull() {
        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()

        assertThat(config).isNull()
    }

    @Test
    fun getSafetyCenterConfig_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterConfig }
    }

    @Test
    fun getSafetyCenterData_returnsDefaultData() {
        val apiSafetyCenterData = safetyCenterManager.getSafetyCenterDataWithPermission()

        assertThat(apiSafetyCenterData).isEqualTo(SafetyCenterCtsData.DEFAULT)
    }

    @Test
    fun getSafetyCenterData_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) { safetyCenterManager.safetyCenterData }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_listenerNotCalled() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)
        val listener = SafetyCenterCtsListener()

        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = SafetyCenterCtsListener()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(directExecutor(), listener)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_listenerNotCalled() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)
        val listener = SafetyCenterCtsListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        safetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(listener)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_withoutPermission_throwsSecurityException() {
        val listener = SafetyCenterCtsListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(listener)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_doesntCallListenerOrDismiss() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue,
            EVENT_SOURCE_STATE_CHANGED
        )
        val listener = SafetyCenterCtsListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID)
        )

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun dismissSafetyCenterIssue_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.dismissSafetyCenterIssue("bleh")
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_doesntCallListenerOrExecute() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue,
            EVENT_SOURCE_STATE_CHANGED
        )
        val listener = SafetyCenterCtsListener()
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
            directExecutor(),
            listener
        )

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.executeSafetyCenterIssueActionWithPermissionAndWait(
                SafetyCenterCtsData.issueId(SINGLE_SOURCE_ID, CRITICAL_ISSUE_ID),
                SafetyCenterCtsData.issueActionId(
                    SINGLE_SOURCE_ID,
                    CRITICAL_ISSUE_ID,
                    CRITICAL_ISSUE_ACTION_ID
                ),
                TIMEOUT_SHORT
            )
        }
        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun executeSafetyCenterIssueAction_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.executeSafetyCenterIssueAction("bleh", "blah")
        }
    }

    @Test
    fun clearAllSafetySourceDataForTests_doesntClearData() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)
        safetyCenterManager.setSafetySourceDataWithPermission(
            SINGLE_SOURCE_ID,
            safetySourceCtsData.criticalWithResolvingGeneralIssue,
            EVENT_SOURCE_STATE_CHANGED
        )
        val apiSafetySourceDataBeforeClearing =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        val apiSafetySourceDataAfterClearing =
            safetyCenterManager.getSafetySourceDataWithPermission(SINGLE_SOURCE_ID)
        assertThat(apiSafetySourceDataAfterClearing).isEqualTo(apiSafetySourceDataBeforeClearing)
    }

    @Test
    fun clearAllSafetySourceDataForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearAllSafetySourceDataForTests()
        }
    }

    @Test
    fun setSafetyCenterConfigForTests_doesntSetConfig() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)

        val config = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(config).isNull()
    }

    @Test
    fun setSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.setSafetyCenterConfigForTests(SINGLE_SOURCE_CONFIG)
        }
    }

    @Test
    fun clearSafetyCenterConfigForTests_doesntClearConfig() {
        safetyCenterManager.setSafetyCenterConfigForTestsWithPermission(SINGLE_SOURCE_CONFIG)
        val configBeforeClearing = safetyCenterManager.getSafetyCenterConfigWithPermission()

        safetyCenterManager.clearSafetyCenterConfigForTestsWithPermission()

        val configAfterClearing = safetyCenterManager.getSafetyCenterConfigWithPermission()
        assertThat(configAfterClearing).isEqualTo(configBeforeClearing)
    }

    @Test
    fun clearSafetyCenterConfigForTests_withoutPermission_throwsSecurityException() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.clearSafetyCenterConfigForTests()
        }
    }
}
