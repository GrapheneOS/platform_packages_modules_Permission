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

/** Lint Detector that finds platform permissions that aren't guarded by a flag. */
package com.android.permissioncontroller.lint.permissionpolicy

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class PlatformPermissionWithoutFlagDetector : Detector(), XmlScanner {

    override fun getApplicableElements() = listOf("permission")

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttribute("android:name")
        if (!permissionName!!.startsWith("android.")) {
            return
        }
        val featureFlag = element.getAttribute("android:featureFlag")
        if (featureFlag.isNullOrBlank()) {
            val incident =
                Incident(context, ISSUE)
                    .at(element)
                    .message(
                        "$permissionName isn't guarded by a feature flag. New <permission> " +
                            "elements must have the `android:featureFlag` attribute."
                    )
            context.report(incident)
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "PlatformPermissionWithoutFlag",
                briefDescription =
                    "Checks for permissions that do not have an associated feature flag.",
                explanation =
                    """
         All new platform permissions need to have the android:featureFlag attribute set.
                    """
                        .trimIndent(),
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        PlatformPermissionWithoutFlagDetector::class.java,
                        Scope.MANIFEST_AND_RESOURCE_SCOPE
                    ),
            )
    }
}
