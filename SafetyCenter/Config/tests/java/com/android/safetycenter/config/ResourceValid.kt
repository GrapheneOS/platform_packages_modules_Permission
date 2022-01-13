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
class ResourceValid : ResourceBaseTest(
    """
<safety-center-config>
    <safety-sources-config>
        <safety-sources-group
            id="security"
            title="@string/reference"
            summary="@string/reference">
            <safety-source
                type="2"
                id="app_security"
                packageName="package"
                title="@string/reference"
                summary="@string/reference"
                intentAction="intent"
                profile="2"
                searchTerms="@string/reference"
                broadcastReceiverClassName="broadcast"
                disallowLogging="true"/>
            <safety-source
                type="3"
                id="device_security"/>
            <safety-source
                type="1"
                id="lockscreen"
                title="@string/reference"
                summary="@string/reference"
                intentAction="intent"
                profile="1"
                searchTerms="@string/reference"/>
        </safety-sources-group>
        <safety-sources-group
            id="privacy"
            title="@string/reference"
            summary="@string/reference">
            <safety-source
                type="3"
                id="privacy_dashboard"/>
            <safety-source
                type="1"
                id="permissions"
                title="@string/reference"
                summary="@string/reference"
                intentAction="intent"
                profile="2"/>
            <safety-source
                type="2"
                id="privacy_controls"
                packageName="package"
                title="@string/reference"
                summary="@string/reference"
                intentAction="intent"
                profile="2"/>
        </safety-sources-group>
        <static-safety-sources-group
            id="oem"
            title="@string/reference">
            <static-safety-source
                type="1"
                id="oem_setting_1"
                title="@string/reference"
                summary="@string/reference"
                intentAction="intent"
                profile="1"/>
            <static-safety-source
                type="1"
                id="oem_setting_2"
                title="@string/reference"
                summary="@string/reference"
                intentAction="intent"
                profile="1"
                searchTerms="@string/reference"/>
        </static-safety-sources-group>
        <static-safety-sources-group
            id="advanced"
            title="@string/reference">
            <static-safety-source
                type="1"
                id="advanced_security"
                title="@string/reference"
                summary="@string/reference"
                intentAction="intent"
                profile="1"/>
            <static-safety-source
                type="1"
                id="advanced_privacy"
                title="@string/reference"
                summary="@string/reference"
                intentAction="intent"
                profile="1"
                searchTerms="@string/reference"/>
        </static-safety-sources-group>
    </safety-sources-config>
</safety-center-config>
    """
)
