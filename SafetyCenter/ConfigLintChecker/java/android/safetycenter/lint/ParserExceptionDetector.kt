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

package android.safetycenter.lint

import android.content.res.Resources
import com.android.resources.ResourceFolderType
import com.android.safetycenter.config.ParseException
import com.android.safetycenter.config.SafetyCenterConfigParser
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.OtherFileScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

/** Lint check for detecting invalid Safety Center configs */
class ParserExceptionDetector : Detector(), OtherFileScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "InvalidSafetyCenterConfig",
            briefDescription = "The Safety Center config parser detected an error",
            explanation = """The Safety Center config must follow all constraints defined in \
                safety_center_config.xsd. Check the error message to find out the specific \
                constraint not met by the current config.""",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                ParserExceptionDetector::class.java,
                Scope.OTHER_SCOPE
            ),
            androidSpecific = true
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.RAW
    }

    override fun run(context: Context) {
        if (context.file.name != "safety_center_config.xml") {
            return
        }
        try {
            SafetyCenterConfigParser.parseXmlResource(context.file.inputStream(), Resources())
        } catch (e: ParseException) {
            context.report(
                ISSUE,
                Location.create(context.file),
                "Parser exception: \"${e.message}\", cause: \"${e.cause?.message}\""
            )
        }
    }
}