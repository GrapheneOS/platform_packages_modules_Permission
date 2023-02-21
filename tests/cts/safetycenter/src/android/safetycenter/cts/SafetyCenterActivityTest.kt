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
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.cts.testing.SafetyCenterFlags
import android.safetycenter.cts.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import android.safetycenter.cts.testing.SettingsPackage.getSettingsPackageName
import android.support.test.uiautomator.By
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
@ApiTest(apis = ["android.content.Intent#ACTION_SAFETY_CENTER"])
class SafetyCenterActivityTest {
    private val context: Context = getApplicationContext()

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(context.deviceSupportsSafetyCenter())
    }

    @Test
    fun launchActivity_withFlagEnabled_showsSecurityAndPrivacyTitle() {
        SafetyCenterFlags.setSafetyCenterEnabled(true)

        startSafetyCenterActivity()

        // CollapsingToolbar title can't be found by text, so using description instead.
        waitFindObject(By.desc(Pattern.compile("Security & [Pp]rivacy")))
    }

    @Test
    fun launchActivity_withFlagDisabled_opensSettings() {
        // TODO(b/269760296) this is to fix test failure caused by incorrect using of U API. Remove
        // in next release.
        assumeFalse(isCodeNameU())
        SafetyCenterFlags.setSafetyCenterEnabled(false)

        startSafetyCenterActivity()

        waitFindObject(By.pkg(context.getSettingsPackageName()))
    }

    private fun startSafetyCenterActivity() {
        context.startActivity(
            Intent(ACTION_SAFETY_CENTER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun isCodeNameU(): Boolean {
        val buildCodeName = Build.VERSION.CODENAME.toUpperCase(Locale.ROOT)
        return buildCodeName.compareTo("UPSIDEDOWNCAKE") >= 0
    }
}
