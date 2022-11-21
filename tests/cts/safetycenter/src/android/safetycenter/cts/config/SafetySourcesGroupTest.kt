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

package android.safetycenter.cts.config

import android.content.res.Resources
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.config.SafetySourcesGroup
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.android.modules.utils.build.SdkLevel
import com.android.permission.testing.EqualsHashCodeToStringTester
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourcesGroup]. */
@RunWith(AndroidJUnit4::class)
class SafetySourcesGroupTest {

    @Test
    fun getType_returnsType() {
        assertThat(COLLAPSIBLE_INFERRED_WITH_SUMMARY.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE)
        assertThat(COLLAPSIBLE_INFERRED_WITH_ICON.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE)
        assertThat(COLLAPSIBLE_INFERRED_WITH_BOTH.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE)
        assertThat(RIGID_INFERRED.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_RIGID)
        assertThat(HIDDEN_INFERRED.type)
            .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
        if (SdkLevel.isAtLeastU()) {
            assertThat(COLLAPSIBLE_BAREBONE.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE)
            assertThat(COLLAPSIBLE_ALL_OPTIONAL.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE)
            assertThat(RIGID_BAREBONE.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_RIGID)
            assertThat(RIGID_ALL_OPTIONAL.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_RIGID)
            assertThat(HIDDEN_BAREBONE.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
            assertThat(HIDDEN_ALL_OPTIONAL.type)
                .isEqualTo(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
        }
    }

    @Test
    fun getId_returnsId() {
        assertThat(COLLAPSIBLE_INFERRED_WITH_SUMMARY.id)
            .isEqualTo(COLLAPSIBLE_INFERRED_WITH_SUMMARY_ID)
        assertThat(COLLAPSIBLE_INFERRED_WITH_ICON.id).isEqualTo(COLLAPSIBLE_INFERRED_WITH_ICON_ID)
        assertThat(COLLAPSIBLE_INFERRED_WITH_BOTH.id).isEqualTo(COLLAPSIBLE_INFERRED_WITH_BOTH_ID)
        assertThat(RIGID_INFERRED.id).isEqualTo(RIGID_INFERRED_ID)
        assertThat(HIDDEN_INFERRED.id).isEqualTo(HIDDEN_INFERRED_ID)
        if (SdkLevel.isAtLeastU()) {
            assertThat(COLLAPSIBLE_BAREBONE.id).isEqualTo(COLLAPSIBLE_BAREBONE_ID)
            assertThat(COLLAPSIBLE_ALL_OPTIONAL.id).isEqualTo(COLLAPSIBLE_ALL_OPTIONAL_ID)
            assertThat(RIGID_BAREBONE.id).isEqualTo(RIGID_BAREBONE_ID)
            assertThat(RIGID_ALL_OPTIONAL.id).isEqualTo(RIGID_ALL_OPTIONAL_ID)
            assertThat(HIDDEN_BAREBONE.id).isEqualTo(HIDDEN_BAREBONE_ID)
            assertThat(HIDDEN_ALL_OPTIONAL.id).isEqualTo(HIDDEN_ALL_OPTIONAL_ID)
        }
    }

    @Test
    fun getTitleResId_returnsTitleResId() {
        assertThat(COLLAPSIBLE_INFERRED_WITH_SUMMARY.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(COLLAPSIBLE_INFERRED_WITH_ICON.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(COLLAPSIBLE_INFERRED_WITH_BOTH.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(RIGID_INFERRED.titleResId).isEqualTo(REFERENCE_RES_ID)
        // This is not an enforced invariant, titleResId should just be ignored for hidden groups
        assertThat(HIDDEN_INFERRED.titleResId).isEqualTo(Resources.ID_NULL)
        if (SdkLevel.isAtLeastU()) {
            assertThat(COLLAPSIBLE_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(COLLAPSIBLE_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(RIGID_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(RIGID_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(HIDDEN_BAREBONE.titleResId).isEqualTo(Resources.ID_NULL)
            assertThat(HIDDEN_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
        }
    }

    @Test
    fun getSummaryResId_returnsSummaryResId() {
        assertThat(COLLAPSIBLE_INFERRED_WITH_SUMMARY.summaryResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(COLLAPSIBLE_INFERRED_WITH_ICON.summaryResId).isEqualTo(Resources.ID_NULL)
        assertThat(COLLAPSIBLE_INFERRED_WITH_BOTH.summaryResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(RIGID_INFERRED.summaryResId).isEqualTo(Resources.ID_NULL)
        // This is not an enforced invariant, summaryResId should just be ignored for hidden groups
        assertThat(HIDDEN_INFERRED.summaryResId).isEqualTo(Resources.ID_NULL)
        if (SdkLevel.isAtLeastU()) {
            assertThat(COLLAPSIBLE_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(COLLAPSIBLE_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(RIGID_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(RIGID_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
            assertThat(HIDDEN_BAREBONE.titleResId).isEqualTo(Resources.ID_NULL)
            assertThat(HIDDEN_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
        }
    }

    @Test
    fun getStatelessIconType_returnsStatelessIconType() {
        assertThat(COLLAPSIBLE_INFERRED_WITH_SUMMARY.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
        assertThat(COLLAPSIBLE_INFERRED_WITH_ICON.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
        assertThat(COLLAPSIBLE_INFERRED_WITH_BOTH.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
        assertThat(RIGID_INFERRED.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
        // This is not an enforced invariant
        // statelessIconType should just be ignored for hidden groups
        assertThat(HIDDEN_INFERRED.statelessIconType)
            .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
        if (SdkLevel.isAtLeastU()) {
            assertThat(COLLAPSIBLE_BAREBONE.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
            assertThat(COLLAPSIBLE_ALL_OPTIONAL.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
            assertThat(RIGID_BAREBONE.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
            assertThat(RIGID_ALL_OPTIONAL.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
            assertThat(HIDDEN_BAREBONE.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
            assertThat(HIDDEN_ALL_OPTIONAL.statelessIconType)
                .isEqualTo(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
        }
    }

    @Test
    fun getSafetySources_returnsSafetySources() {
        assertThat(COLLAPSIBLE_INFERRED_WITH_SUMMARY.safetySources)
            .containsExactly(SafetySourceTest.DYNAMIC_BAREBONE)
        assertThat(COLLAPSIBLE_INFERRED_WITH_ICON.safetySources)
            .containsExactly(SafetySourceTest.STATIC_BAREBONE)
        assertThat(COLLAPSIBLE_INFERRED_WITH_BOTH.safetySources)
            .containsExactly(
                SafetySourceTest.DYNAMIC_BAREBONE,
                SafetySourceTest.STATIC_BAREBONE,
                SafetySourceTest.ISSUE_ONLY_BAREBONE)
            .inOrder()
        assertThat(RIGID_INFERRED.safetySources).containsExactly(SafetySourceTest.STATIC_BAREBONE)
        assertThat(HIDDEN_INFERRED.safetySources)
            .containsExactly(SafetySourceTest.ISSUE_ONLY_BAREBONE)
        if (SdkLevel.isAtLeastU()) {
            assertThat(COLLAPSIBLE_BAREBONE.safetySources)
                .containsExactly(SafetySourceTest.DYNAMIC_BAREBONE)
            assertThat(COLLAPSIBLE_ALL_OPTIONAL.safetySources)
                .containsExactly(
                    SafetySourceTest.DYNAMIC_BAREBONE,
                    SafetySourceTest.STATIC_BAREBONE,
                    SafetySourceTest.ISSUE_ONLY_BAREBONE)
            assertThat(RIGID_BAREBONE.safetySources)
                .containsExactly(SafetySourceTest.STATIC_BAREBONE)
            assertThat(RIGID_ALL_OPTIONAL.safetySources)
                .containsExactly(
                    SafetySourceTest.DYNAMIC_BAREBONE,
                    SafetySourceTest.STATIC_BAREBONE,
                    SafetySourceTest.ISSUE_ONLY_BAREBONE)
            assertThat(HIDDEN_BAREBONE.safetySources)
                .containsExactly(SafetySourceTest.ISSUE_ONLY_BAREBONE)
            assertThat(HIDDEN_ALL_OPTIONAL.safetySources)
                .containsExactly(SafetySourceTest.ISSUE_ONLY_BAREBONE)
        }
    }

    @Test
    fun getSafetySources_mutationsAreNotAllowed() {
        val sources = COLLAPSIBLE_INFERRED_WITH_SUMMARY.safetySources

        assertFailsWith(UnsupportedOperationException::class) {
            sources.add(SafetySourceTest.DYNAMIC_BAREBONE)
        }
    }

    @Test
    fun builder_addSafetySource_doesNotMutatePreviouslyBuiltInstance() {
        val safetySourcesGroupBuilder =
            SafetySourcesGroup.Builder()
                .setId(COLLAPSIBLE_INFERRED_WITH_SUMMARY_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
        val sources = safetySourcesGroupBuilder.build().safetySources

        safetySourcesGroupBuilder.addSafetySource(SafetySourceTest.STATIC_BAREBONE)

        assertThat(sources).containsExactly(SafetySourceTest.DYNAMIC_BAREBONE)
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun build_hiddenGroupWithDynamicSource_throwsIllegalStateException() {
        val builder =
            SafetySourcesGroup.Builder()
                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                .setId(HIDDEN_BAREBONE_ID)
                .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)

        val exception = assertFailsWith(IllegalStateException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Safety sources groups of type hidden can only contain sources of type issue-only")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun build_hiddenGroupWithStaticSource_throwsIllegalStateException() {
        val builder =
            SafetySourcesGroup.Builder()
                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                .setId(HIDDEN_BAREBONE_ID)
                .addSafetySource(SafetySourceTest.STATIC_BAREBONE)

        val exception = assertFailsWith(IllegalStateException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Safety sources groups of type hidden can only contain sources of type issue-only")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun build_collapsibleGroupWithIssueOnlySource_throwsIllegalStateException() {
        val builder =
            SafetySourcesGroup.Builder()
                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE)
                .setId(COLLAPSIBLE_BAREBONE_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)

        val exception = assertFailsWith(IllegalStateException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Safety sources groups containing only sources of type issue-only must be of " +
                    "type hidden")
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun build_rigidGroupWithIssueOnlySource_throwsIllegalStateException() {
        val builder =
            SafetySourcesGroup.Builder()
                .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_RIGID)
                .setId(RIGID_BAREBONE_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)

        val exception = assertFailsWith(IllegalStateException::class) { builder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo(
                "Safety sources groups containing only sources of type issue-only must be of " +
                    "type hidden")
    }

    @Test
    fun describeContents_returns0() {
        assertThat(COLLAPSIBLE_INFERRED_WITH_SUMMARY.describeContents()).isEqualTo(0)
        assertThat(COLLAPSIBLE_INFERRED_WITH_ICON.describeContents()).isEqualTo(0)
        assertThat(COLLAPSIBLE_INFERRED_WITH_BOTH.describeContents()).isEqualTo(0)
        assertThat(RIGID_INFERRED.describeContents()).isEqualTo(0)
        assertThat(HIDDEN_INFERRED.describeContents()).isEqualTo(0)
        if (SdkLevel.isAtLeastU()) {
            assertThat(COLLAPSIBLE_BAREBONE.describeContents()).isEqualTo(0)
            assertThat(COLLAPSIBLE_ALL_OPTIONAL.describeContents()).isEqualTo(0)
            assertThat(RIGID_BAREBONE.describeContents()).isEqualTo(0)
            assertThat(RIGID_ALL_OPTIONAL.describeContents()).isEqualTo(0)
            assertThat(HIDDEN_BAREBONE.describeContents()).isEqualTo(0)
            assertThat(HIDDEN_ALL_OPTIONAL.describeContents()).isEqualTo(0)
        }
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(COLLAPSIBLE_INFERRED_WITH_SUMMARY).recreatesEqual(SafetySourcesGroup.CREATOR)
        assertThat(COLLAPSIBLE_INFERRED_WITH_ICON).recreatesEqual(SafetySourcesGroup.CREATOR)
        assertThat(COLLAPSIBLE_INFERRED_WITH_BOTH).recreatesEqual(SafetySourcesGroup.CREATOR)
        assertThat(RIGID_INFERRED).recreatesEqual(SafetySourcesGroup.CREATOR)
        assertThat(HIDDEN_INFERRED).recreatesEqual(SafetySourcesGroup.CREATOR)
        if (SdkLevel.isAtLeastU()) {
            assertThat(COLLAPSIBLE_BAREBONE).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(COLLAPSIBLE_ALL_OPTIONAL).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(RIGID_BAREBONE).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(RIGID_ALL_OPTIONAL).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(HIDDEN_BAREBONE).recreatesEqual(SafetySourcesGroup.CREATOR)
            assertThat(HIDDEN_ALL_OPTIONAL).recreatesEqual(SafetySourcesGroup.CREATOR)
        }
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(COLLAPSIBLE_INFERRED_WITH_SUMMARY)
            .addEqualityGroup(COLLAPSIBLE_INFERRED_WITH_ICON)
            .addEqualityGroup(
                *mutableListOf(
                        COLLAPSIBLE_INFERRED_WITH_BOTH,
                        SafetySourcesGroup.Builder()
                            .setId(COLLAPSIBLE_INFERRED_WITH_BOTH_ID)
                            .setTitleResId(REFERENCE_RES_ID)
                            .setSummaryResId(REFERENCE_RES_ID)
                            .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                            .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                            .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                            .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                            .build())
                    .apply { if (SdkLevel.isAtLeastU()) add(COLLAPSIBLE_ALL_OPTIONAL) }
                    .toTypedArray())
            .addEqualityGroup(
                *mutableListOf(RIGID_INFERRED)
                    .apply { if (SdkLevel.isAtLeastU()) add(RIGID_BAREBONE) }
                    .toTypedArray())
            .addEqualityGroup(
                *mutableListOf(HIDDEN_INFERRED)
                    .apply { if (SdkLevel.isAtLeastU()) add(HIDDEN_BAREBONE) }
                    .toTypedArray())
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId("other")
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build())
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId(COLLAPSIBLE_INFERRED_WITH_BOTH_ID)
                    .setTitleResId(-1)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build())
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId(COLLAPSIBLE_INFERRED_WITH_BOTH_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(-1)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build())
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId(COLLAPSIBLE_INFERRED_WITH_BOTH_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_NONE)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build())
            .addEqualityGroup(
                SafetySourcesGroup.Builder()
                    .setId(COLLAPSIBLE_INFERRED_WITH_BOTH_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                    .build())
            .apply {
                if (SdkLevel.isAtLeastU()) {
                    addEqualityGroup(COLLAPSIBLE_BAREBONE)
                    addEqualityGroup(RIGID_ALL_OPTIONAL)
                    addEqualityGroup(HIDDEN_ALL_OPTIONAL)
                }
            }
            .test()
    }

    companion object {
        private const val REFERENCE_RES_ID = 9999

        private const val COLLAPSIBLE_BAREBONE_ID = "collapsible_barebone"
        private const val COLLAPSIBLE_ALL_OPTIONAL_ID = "collapsible_all_optional"
        private const val RIGID_BAREBONE_ID = "rigid_barebone"
        private const val RIGID_ALL_OPTIONAL_ID = "rigid_all_optional"
        private const val HIDDEN_BAREBONE_ID = "hidden_barebone"
        private const val HIDDEN_ALL_OPTIONAL_ID = "hidden_all_optional"
        private const val COLLAPSIBLE_INFERRED_WITH_SUMMARY_ID = "collapsible_inferred_with_summary"
        private const val COLLAPSIBLE_INFERRED_WITH_ICON_ID = "collapsible_inferred_with_icon"
        private const val COLLAPSIBLE_INFERRED_WITH_BOTH_ID = COLLAPSIBLE_ALL_OPTIONAL_ID
        private const val RIGID_INFERRED_ID = RIGID_BAREBONE_ID
        private const val HIDDEN_INFERRED_ID = HIDDEN_BAREBONE_ID

        // TODO(b/230078826): Consider extracting shared constants to a separate file.
        internal val COLLAPSIBLE_INFERRED_WITH_SUMMARY =
            SafetySourcesGroup.Builder()
                .setId(COLLAPSIBLE_INFERRED_WITH_SUMMARY_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                .build()

        private val COLLAPSIBLE_INFERRED_WITH_ICON =
            SafetySourcesGroup.Builder()
                .setId(COLLAPSIBLE_INFERRED_WITH_ICON_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                .build()

        private val COLLAPSIBLE_INFERRED_WITH_BOTH =
            SafetySourcesGroup.Builder()
                .setId(COLLAPSIBLE_INFERRED_WITH_BOTH_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                .build()

        private val COLLAPSIBLE_BAREBONE: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE)
                    .setId(COLLAPSIBLE_BAREBONE_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .build()

        private val COLLAPSIBLE_ALL_OPTIONAL: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE)
                    .setId(COLLAPSIBLE_ALL_OPTIONAL_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                    .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                    .build()

        internal val RIGID_INFERRED =
            SafetySourcesGroup.Builder()
                .setId(RIGID_INFERRED_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                .build()

        private val RIGID_BAREBONE: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_RIGID)
                    .setId(RIGID_BAREBONE_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                    .build()

        private val RIGID_ALL_OPTIONAL: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_RIGID)
                    .setId(RIGID_ALL_OPTIONAL_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.DYNAMIC_BAREBONE)
                    .addSafetySource(SafetySourceTest.STATIC_BAREBONE)
                    .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                    .build()

        internal val HIDDEN_INFERRED =
            SafetySourcesGroup.Builder()
                .setId(HIDDEN_INFERRED_ID)
                .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                .build()

        private val HIDDEN_BAREBONE: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                    .setId(HIDDEN_BAREBONE_ID)
                    .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                    .build()

        private val HIDDEN_ALL_OPTIONAL: SafetySourcesGroup
            @RequiresApi(UPSIDE_DOWN_CAKE)
            get() =
                SafetySourcesGroup.Builder()
                    .setType(SafetySourcesGroup.SAFETY_SOURCES_GROUP_TYPE_HIDDEN)
                    .setId(HIDDEN_ALL_OPTIONAL_ID)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setStatelessIconType(SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY)
                    .addSafetySource(SafetySourceTest.ISSUE_ONLY_BAREBONE)
                    .build()
    }
}
