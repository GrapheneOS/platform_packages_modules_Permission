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

import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.config.SafetyCenterConfig
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.resources.SafetyCenterResourcesContext
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class XmlConfigTest {
    private val safetyCenterContext = SafetyCenterResourcesContext(getApplicationContext())

    @Test
    fun safetyCenterConfigResource_validConfig() {
        // Assert that the parser validates the Safety Center config without throwing any exception
        assertThat(SafetyCenterConfig.fromXml(safetyCenterContext.safetyCenterConfig!!)).isNotNull()
    }
}
