/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.model

import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.modules.utils.build.SdkLevel
import com.android.permission.flags.Flags
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.PermissionUsageDetailsUiState
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModelV2
import com.google.common.truth.Truth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** This is an integration tests for permission timeline page view model. */
@RunWith(AndroidJUnit4::class)
class PermissionUsageDetailsViewModelTest {
    @JvmField @Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @JvmField @Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LIVEDATA_REFACTOR_PERMISSION_TIMELINE_ENABLED)
    fun verifyUiStateIsGeneratedSuccessfully() {
        Assume.assumeTrue(SdkLevel.isAtLeastS())
        lateinit var uiState: PermissionUsageDetailsUiState.Success
        val viewModel =
            PermissionUsageDetailsViewModelV2.create(
                PermissionControllerApplication.get(),
                SavedStateHandle(mapOf("show7Days" to true, "showSystem" to true)),
                LOCATION_PERMISSION_GROUP
            )
        val countDownLatch = CountDownLatch(1)

        viewModel.getPermissionUsagesDetailsInfoUiLiveData().observeForever {
            if (it is PermissionUsageDetailsUiState.Success) {
                uiState = it
                countDownLatch.countDown()
            }
        }
        countDownLatch.await(5L, TimeUnit.SECONDS)
        Truth.assertThat(uiState).isNotNull()
    }

    companion object {
        private val LOCATION_PERMISSION_GROUP = android.Manifest.permission_group.LOCATION
    }
}
