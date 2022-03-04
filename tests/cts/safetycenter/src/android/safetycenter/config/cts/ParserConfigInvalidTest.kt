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

package android.safetycenter.config.cts

import android.content.Context
import android.safetycenter.config.ParseException
import android.safetycenter.config.SafetyCenterConfig
import android.safetycenter.cts.R
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runners.Parameterized
import org.junit.runner.RunWith

@RunWith(Parameterized::class)
class ParserConfigInvalidTest {
    private val context: Context = getApplicationContext()

    data class Params(
        private val testName: String,
        val configResourceId: Int,
        val errorMessage: String,
        val causeErrorMessage: String?
    ) {
        override fun toString() = testName
    }

    @Parameterized.Parameter
    lateinit var params: Params

    @Test
    fun invalidConfig_throws() {
        val parser = context.resources.getXml(params.configResourceId)
        val thrown = assertThrows(ParseException::class.java) {
            SafetyCenterConfig.fromXml(parser)
        }
        assertThat(thrown).hasMessageThat().isEqualTo(params.errorMessage)
        if (params.causeErrorMessage != null) {
            assertThat(thrown.cause).hasMessageThat().isEqualTo(params.causeErrorMessage)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = arrayOf(
            Params(
                "ConfigDynamicSafetySourceAllDisabledNoWork",
                R.xml.config_dynamic_safety_source_all_disabled_no_work,
                "Element dynamic-safety-source invalid",
                "Required attribute titleForWork missing"
            ),
            Params(
                "ConfigDynamicSafetySourceAllNoWork",
                R.xml.config_dynamic_safety_source_all_no_work,
                "Element dynamic-safety-source invalid",
                "Required attribute titleForWork missing"
            ),
            Params(
                "ConfigDynamicSafetySourceDisabledNoSummary",
                R.xml.config_dynamic_safety_source_disabled_no_summary,
                "Element dynamic-safety-source invalid",
                "Required attribute summary missing"
            ),
            Params(
                "ConfigDynamicSafetySourceDisabledNoTitle",
                R.xml.config_dynamic_safety_source_disabled_no_title,
                "Element dynamic-safety-source invalid",
                "Required attribute title missing"
            ),
            Params(
                "ConfigDynamicSafetySourceDuplicateKey",
                R.xml.config_dynamic_safety_source_duplicate_key,
                "Element safety-sources-config invalid",
                "Duplicate id id among safety sources"
            ),
            Params(
                "ConfigDynamicSafetySourceHiddenWithIntent",
                R.xml.config_dynamic_safety_source_hidden_with_intent,
                "Element dynamic-safety-source invalid",
                "Prohibited attribute intentAction present"
            ),
            Params(
                "ConfigDynamicSafetySourceHiddenWithSummary",
                R.xml.config_dynamic_safety_source_hidden_with_summary,
                "Element dynamic-safety-source invalid",
                "Prohibited attribute summary present"
            ),
            Params(
                "ConfigDynamicSafetySourceHiddenWithTitle",
                R.xml.config_dynamic_safety_source_hidden_with_title,
                "Element dynamic-safety-source invalid",
                "Prohibited attribute title present"
            ),
            Params(
                "ConfigDynamicSafetySourceInvalidDisplay",
                R.xml.config_dynamic_safety_source_invalid_display,
                "Attribute dynamic-safety-source.initialDisplayState invalid",
                null
            ),
            Params(
                "ConfigDynamicSafetySourceInvalidProfile",
                R.xml.config_dynamic_safety_source_invalid_profile,
                "Attribute dynamic-safety-source.profile invalid",
                null
            ),
            Params(
                "ConfigDynamicSafetySourceNoId",
                R.xml.config_dynamic_safety_source_no_id,
                "Element dynamic-safety-source invalid",
                "Required attribute id missing"
            ),
            Params(
                "ConfigDynamicSafetySourceNoIntent",
                R.xml.config_dynamic_safety_source_no_intent,
                "Element dynamic-safety-source invalid",
                "Required attribute intentAction missing"
            ),
            Params(
                "ConfigDynamicSafetySourceNoPackage",
                R.xml.config_dynamic_safety_source_no_package,
                "Element dynamic-safety-source invalid",
                "Required attribute packageName missing"
            ),
            Params(
                "ConfigDynamicSafetySourceNoProfile",
                R.xml.config_dynamic_safety_source_no_profile,
                "Element dynamic-safety-source invalid",
                "Required attribute profile missing"
            ),
            Params(
                "ConfigDynamicSafetySourceNoSummary",
                R.xml.config_dynamic_safety_source_no_summary,
                "Element dynamic-safety-source invalid",
                "Required attribute summary missing"
            ),
            Params(
                "ConfigDynamicSafetySourceNoTitle",
                R.xml.config_dynamic_safety_source_no_title,
                "Element dynamic-safety-source invalid",
                "Required attribute title missing"
            ),
            Params(
                "ConfigDynamicSafetySourcePrimaryHiddenWithWork",
                R.xml.config_dynamic_safety_source_primary_hidden_with_work,
                "Element dynamic-safety-source invalid",
                "Prohibited attribute titleForWork present"
            ),
            Params(
                "ConfigDynamicSafetySourcePrimaryWithWork",
                R.xml.config_dynamic_safety_source_primary_with_work,
                "Element dynamic-safety-source invalid",
                "Prohibited attribute titleForWork present"
            ),
            Params(
                "ConfigIssueOnlySafetySourceDuplicateKey",
                R.xml.config_issue_only_safety_source_duplicate_key,
                "Element safety-sources-config invalid",
                "Duplicate id id among safety sources"
            ),
            Params(
                "ConfigIssueOnlySafetySourceInvalidProfile",
                R.xml.config_issue_only_safety_source_invalid_profile,
                "Attribute issue-only-safety-source.profile invalid",
                null
            ),
            Params(
                "ConfigIssueOnlySafetySourceNoId",
                R.xml.config_issue_only_safety_source_no_id,
                "Element issue-only-safety-source invalid",
                "Required attribute id missing"
            ),
            Params(
                "ConfigIssueOnlySafetySourceNoPackage",
                R.xml.config_issue_only_safety_source_no_package,
                "Element issue-only-safety-source invalid",
                "Required attribute packageName missing"
            ),
            Params(
                "ConfigIssueOnlySafetySourceNoProfile",
                R.xml.config_issue_only_safety_source_no_profile,
                "Element issue-only-safety-source invalid",
                "Required attribute profile missing"
            ),
            Params(
                "ConfigIssueOnlySafetySourceWithDisplay",
                R.xml.config_issue_only_safety_source_with_display,
                "Element issue-only-safety-source invalid",
                "Prohibited attribute initialDisplayState present"
            ),
            Params(
                "ConfigIssueOnlySafetySourceWithIntent",
                R.xml.config_issue_only_safety_source_with_intent,
                "Element issue-only-safety-source invalid",
                "Prohibited attribute intentAction present"
            ),
            Params(
                "ConfigIssueOnlySafetySourceWithSearch",
                R.xml.config_issue_only_safety_source_with_search,
                "Element issue-only-safety-source invalid",
                "Prohibited attribute searchTerms present"
            ),
            Params(
                "ConfigIssueOnlySafetySourceWithSummary",
                R.xml.config_issue_only_safety_source_with_summary,
                "Element issue-only-safety-source invalid",
                "Prohibited attribute summary present"
            ),
            Params(
                "ConfigIssueOnlySafetySourceWithTitle",
                R.xml.config_issue_only_safety_source_with_title,
                "Element issue-only-safety-source invalid",
                "Prohibited attribute title present"
            ),
            Params(
                "ConfigIssueOnlySafetySourceWithWork",
                R.xml.config_issue_only_safety_source_with_work,
                "Element issue-only-safety-source invalid",
                "Prohibited attribute titleForWork present"
            ),
            Params(
                "ConfigMixedSafetySourceDuplicateKey",
                R.xml.config_mixed_safety_source_duplicate_key,
                "Element safety-sources-config invalid",
                "Duplicate id id among safety sources"
            ),
            Params(
                "ConfigReferenceInvalid",
                R.xml.config_reference_invalid,
                "Reference title in safety-sources-group.title missing or invalid",
                null
            ),
            Params(
                "ConfigSafetyCenterConfigMissing",
                R.xml.config_safety_center_config_missing,
                "Element safety-center-config missing",
                null
            ),
            Params(
                "ConfigSafetySourcesConfigEmpty",
                R.xml.config_safety_sources_config_empty,
                "Element safety-sources-config invalid",
                "No safety sources groups present"
            ),
            Params(
                "ConfigSafetySourcesConfigMissing",
                R.xml.config_safety_sources_config_missing,
                "Element safety-sources-config missing",
                null
            ),
            Params(
                "ConfigSafetySourcesGroupDuplicateId",
                R.xml.config_safety_sources_group_duplicate_id,
                "Element safety-sources-config invalid",
                "Duplicate id id among safety sources groups"
            ),
            Params(
                "ConfigSafetySourcesGroupEmpty",
                R.xml.config_safety_sources_group_empty,
                "Element safety-sources-group invalid",
                "Safety sources group empty"
            ),
            Params(
                "ConfigSafetySourcesGroupInvalidIcon",
                R.xml.config_safety_sources_group_invalid_icon,
                "Attribute safety-sources-group.statelessIconType invalid",
                null
            ),
            Params(
                "ConfigSafetySourcesGroupNoId",
                R.xml.config_safety_sources_group_no_id,
                "Element safety-sources-group invalid",
                "Required attribute id missing"
            ),
            Params(
                "ConfigSafetySourcesGroupNoTitle",
                R.xml.config_safety_sources_group_no_title,
                "Element safety-sources-group invalid",
                "Required attribute title missing"
            ),
            Params(
                "ConfigStaticSafetySourceDuplicateKey",
                R.xml.config_static_safety_source_duplicate_key,
                "Element safety-sources-config invalid",
                "Duplicate id id among safety sources"
            ),
            Params(
                "ConfigStaticSafetySourceInvalidProfile",
                R.xml.config_static_safety_source_invalid_profile,
                "Attribute static-safety-source.profile invalid",
                null
            ),
            Params(
                "ConfigStaticSafetySourceNoId",
                R.xml.config_static_safety_source_no_id,
                "Element static-safety-source invalid",
                "Required attribute id missing"
            ),
            Params(
                "ConfigStaticSafetySourceNoIntent",
                R.xml.config_static_safety_source_no_intent,
                "Element static-safety-source invalid",
                "Required attribute intentAction missing"
            ),
            Params(
                "ConfigStaticSafetySourceNoProfile",
                R.xml.config_static_safety_source_no_profile,
                "Element static-safety-source invalid",
                "Required attribute profile missing"
            ),
            Params(
                "ConfigStaticSafetySourceNoSummary",
                R.xml.config_static_safety_source_no_summary,
                "Element static-safety-source invalid",
                "Required attribute summary missing"
            ),
            Params(
                "ConfigStaticSafetySourceNoTitle",
                R.xml.config_static_safety_source_no_title,
                "Element static-safety-source invalid",
                "Required attribute title missing"
            ),
            Params(
                "ConfigStaticSafetySourceWithBroadcast",
                R.xml.config_static_safety_source_with_broadcast,
                "Element static-safety-source invalid",
                "Prohibited attribute broadcastReceiverClassName present"
            ),
            Params(
                "ConfigStaticSafetySourceWithDisplay",
                R.xml.config_static_safety_source_with_display,
                "Element static-safety-source invalid",
                "Prohibited attribute initialDisplayState present"
            ),
            Params(
                "ConfigStaticSafetySourceWithLogging",
                R.xml.config_static_safety_source_with_logging,
                "Element static-safety-source invalid",
                "Prohibited attribute loggingAllowed present"
            ),
            Params(
                "ConfigStaticSafetySourceWithPackage",
                R.xml.config_static_safety_source_with_package,
                "Element static-safety-source invalid",
                "Prohibited attribute packageName present"
            ),
            Params(
                "ConfigStaticSafetySourceWithPrimaryAndWork",
                R.xml.config_static_safety_source_with_primary_and_work,
                "Element static-safety-source invalid",
                "Prohibited attribute titleForWork present"
            ),
            Params(
                "ConfigStaticSafetySourceWithRefresh",
                R.xml.config_static_safety_source_with_refresh,
                "Element static-safety-source invalid",
                "Prohibited attribute refreshOnPageOpenAllowed present"
            ),
            Params(
                "ConfigStaticSafetySourceWithSeverity",
                R.xml.config_static_safety_source_with_severity,
                "Element static-safety-source invalid",
                "Prohibited attribute maxSeverityLevel present"
            )
        )
    }
}
