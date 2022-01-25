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

package com.android.safetycenter.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceInternalSafetySourceWithBroadcast : ResourceBaseTest(
    """
<safety-center-config>
    <safety-sources-config>
        <safety-sources-group
            id="id"
            title="@string/reference"
            summary="@string/reference">
            <safety-source
                type="3"
                id="id"
                broadcastReceiverClassName="broadcast"/>
        </safety-sources-group>
    </safety-sources-config>
</safety-center-config>
    """,
    "Prohibited attribute safety-source.broadcastReceiverClassName present"
)
