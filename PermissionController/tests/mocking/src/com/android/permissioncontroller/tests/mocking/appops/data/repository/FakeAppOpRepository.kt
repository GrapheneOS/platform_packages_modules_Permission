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

package com.android.permissioncontroller.tests.mocking.appops.data.repository

import com.android.permissioncontroller.appops.data.model.v31.DiscretePackageOpsModel
import com.android.permissioncontroller.appops.data.model.v31.PackageAppOpUsageModel
import com.android.permissioncontroller.appops.data.repository.v31.AppOpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeAppOpRepository(
    override val packageAppOpsUsages: Flow<List<PackageAppOpUsageModel>>,
    private val discreteOps: Flow<List<DiscretePackageOpsModel>> = flowOf(),
) : AppOpRepository {
    override fun getDiscreteOps(
        opNames: List<String>,
        coroutineScope: CoroutineScope
    ): Flow<List<DiscretePackageOpsModel>> {
        return discreteOps
    }
}
