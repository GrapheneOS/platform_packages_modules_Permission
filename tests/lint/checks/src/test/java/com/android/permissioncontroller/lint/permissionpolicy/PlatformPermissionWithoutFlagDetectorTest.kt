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

package com.android.permissioncontroller.lint.permissionpolicy

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class PlatformPermissionWithoutFlagDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = PlatformPermissionWithoutFlagDetector()

    override fun getIssues(): List<Issue> = listOf(PlatformPermissionWithoutFlagDetector.ISSUE)

    @Test
    fun testManifestPermissionsMustHaveFeatureFlag() {
        lint()
            .files(
                xml(
                        "AndroidManifest.xml",
                        """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="android" coreApp="true" android:sharedUserId="android.uid.system"
        android:sharedUserLabel="@string/android_system_label">

    <permission android:name="android.permission.EXAMPLE"
            android:protectionLevel="normal" />

    <permission android:name="com.android.permission.EXAMPLE2"
            android:protectionLevel="normal" />

    <permission android:name="android.permission.EXAMPLE3"
            android:protectionLevel="normal"
            android:featureFlag="this.is.a.feature.flag"/>

    <attribution android:tag="ExampleTag" android:label="@string/example_label"/>

    <application android:process="system"
            android:persistent="true"
            android:hasCode="false"
            android:label="@string/android_system_label"
            android:allowClearUserData="false"
            android:backupAgent="com.android.server.backup.SystemBackupAgent"
            android:killAfterRestore="false"
            android:icon="@drawable/ic_launcher_android"
            android:supportsRtl="true"
            android:theme="@style/Theme.DeviceDefault.Light.DarkActionBar"
            android:defaultToDeviceProtectedStorage="true"
            android:forceQueryable="true"
            android:directBootAware="true">
        <activity android:name="com.android.internal.ExampleActivity"
                  android:exported="false"
                  android:theme="@style/Theme.DeviceDefault.Dialog.Alert.DayNight"
                  android:finishOnCloseSystemDialogs="true"
                  android:excludeFromRecents="true"
                  android:documentLaunchMode="never"
                  android:relinquishTaskIdentity="true"
                  android:process=":ui"
                  android:visibleToInstantApps="true">
            <intent-filter>
                <action android:name="com.android.internal.intent.action.EXAMPLE_ACTION" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

    </application>

    <permission android:name="android.permission.EXAMPLE4"
            android:protectionLevel="normal" />
</manifest>
"""
                    )
                    .indented()
            )
            .allowMissingSdk()
            .run()
            .expect(
                """
AndroidManifest.xml:5: Error: android.permission.EXAMPLE isn't guarded by a feature flag. New <permission> elements must have the android:featureFlag attribute. [PlatformPermissionWithoutFlag]
    <permission android:name="android.permission.EXAMPLE"
    ^
AndroidManifest.xml:47: Error: android.permission.EXAMPLE4 isn't guarded by a feature flag. New <permission> elements must have the android:featureFlag attribute. [PlatformPermissionWithoutFlag]
    <permission android:name="android.permission.EXAMPLE4"
    ^
2 errors, 0 warnings
"""
                    .trimIndent()
            )
    }
}
