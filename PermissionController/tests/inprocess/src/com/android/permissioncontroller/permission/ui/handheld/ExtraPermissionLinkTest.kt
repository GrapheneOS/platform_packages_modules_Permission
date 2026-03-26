package com.android.permissioncontroller.permission.ui.handheld

import android.Manifest
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.permissioncontroller.micspoofing.MicSpoofingLinks
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtraPermissionLinkTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun getExtraPermissionLink_microphoneGroup_returnsMicSpoofingLink() {
        val link = getExtraPermissionLink(
            targetContext,
            "com.example.app",
            UserHandle.SYSTEM,
            Manifest.permission_group.MICROPHONE,
        )

        assertThat(link).isEqualTo(MicSpoofingLinks)
    }

    @Test
    fun getExtraPermissionLink_unrelatedGroup_returnsNull() {
        val link = getExtraPermissionLink(
            targetContext,
            "com.example.app",
            UserHandle.SYSTEM,
            Manifest.permission_group.CAMERA,
        )

        assertThat(link).isNull()
    }
}
