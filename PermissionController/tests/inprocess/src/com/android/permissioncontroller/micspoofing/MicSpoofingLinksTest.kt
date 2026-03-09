package com.android.permissioncontroller.micspoofing

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.GosPackageState
import android.content.pm.GosPackageStateFlag
import android.os.Parcel
import android.os.UserHandle
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicSpoofingLinksTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun isVisible_forMicrophoneGroup_returnsTrue() {
        val visible = MicSpoofingLinks.isVisible(
            targetContext,
            Manifest.permission_group.MICROPHONE,
            "com.example.app",
            UserHandle.SYSTEM,
        )

        assertThat(visible).isTrue()
    }

    @Test
    fun isVisible_forNonMicrophoneGroup_returnsFalse() {
        val visible = MicSpoofingLinks.isVisible(
            targetContext,
            Manifest.permission_group.CAMERA,
            "com.example.app",
            UserHandle.SYSTEM,
        )

        assertThat(visible).isFalse()
    }

    @Test
    fun setupDialogButton_setsMicrophoneSpoofingText() {
        val button = Button(targetContext)

        MicSpoofingLinks.setupDialogButton(button)

        assertThat(button.text.toString())
            .isEqualTo(targetContext.getString(R.string.setup_microphone_spoofing))
    }

    @Test
    @Suppress("DEPRECATION")
    fun onDialogButtonClick_startsMicSpoofingActivityForResult() {
        val packageName = "com.example.app"
        var activity: RecordingGrantPermissionsActivity? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val testActivity = RecordingGrantPermissionsActivity()
            MicSpoofingLinks.onDialogButtonClick(testActivity, packageName)
            activity = testActivity
        }

        val recordedActivity = requireNotNull(activity)

        assertIntentTargetsMicSpoofing(recordedActivity.startedIntent, packageName)
        assertThat(recordedActivity.startedRequestCode)
            .isEqualTo(GrantPermissionsActivity.REQ_CODE_SETUP_MICROPHONE_SPOOFING)
    }

    @Test
    fun getSettingsDeniedRadioButtonSuffix_enabled_returnsExpectedSuffix() {
        val packageState = createPackageState(1L shl GosPackageStateFlag.MIC_SPOOFING_ENABLED)

        val suffix = MicSpoofingLinks
            .getSettingsDeniedRadioButtonSuffix(targetContext, packageState)

        assertThat(suffix).isEqualTo(" (+ ${targetContext.getString(R.string.microphone_spoofing)})")
    }

    @Test
    fun getSettingsDeniedRadioButtonSuffix_disabled_returnsNull() {
        val packageState = createPackageState(0L)

        val suffix = MicSpoofingLinks
            .getSettingsDeniedRadioButtonSuffix(targetContext, packageState)

        assertThat(suffix).isNull()
    }

    @Test
    fun getSettingsLinkText_returnsMicrophoneSpoofingText() {
        assertThat(MicSpoofingLinks.getSettingsLinkText(targetContext).toString())
            .isEqualTo(targetContext.getString(R.string.setup_microphone_spoofing))
    }

    @Test
    fun onSettingsLinkClick_startsMicSpoofingActivity() {
        val context = RecordingContext(targetContext)
        val user = UserHandle.of(10)
        val packageName = "com.example.app"

        MicSpoofingLinks.onSettingsLinkClick(context, packageName, user)

        assertIntentTargetsMicSpoofing(context.startedIntent, packageName)
        assertThat(context.startedUser).isEqualTo(user)
    }

    private fun createPackageState(flagStorage1: Long): GosPackageState {
        return Parcel.obtain().useParcel { parcel ->
            parcel.writeInt(REGULAR_GOS_PACKAGE_STATE_TYPE)
            parcel.writeLong(flagStorage1)
            parcel.writeLong(0L)
            parcel.writeByteArray(null)
            parcel.writeByteArray(null)
            parcel.writeByteArray(null)
            parcel.writeInt(0)
            parcel.setDataPosition(0)
            GosPackageState.CREATOR.createFromParcel(parcel)
        }
    }

    private fun assertIntentTargetsMicSpoofing(intent: Intent?, packageName: String) {
        requireNotNull(intent)
        assertThat(intent.component?.packageName).isEqualTo("com.android.permissioncontroller")
        assertThat(intent.component?.className)
            .isEqualTo("com.android.permissioncontroller.micspoofing.MicSpoofingActivity")
        assertThat(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)).isEqualTo(packageName)
    }

    private class RecordingGrantPermissionsActivity : GrantPermissionsActivity() {
        var startedIntent: Intent? = null
        var startedRequestCode = -1

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun startActivityForResult(intent: Intent, requestCode: Int) {
            startedIntent = intent
            startedRequestCode = requestCode
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null
        var startedUser: UserHandle? = null

        override fun startActivityAsUser(intent: Intent, user: UserHandle) {
            startedIntent = intent
            startedUser = user
        }
    }

    private fun Parcel.useParcel(block: (Parcel) -> GosPackageState): GosPackageState {
        return try {
            block(this)
        } finally {
            recycle()
        }
    }

    companion object {
        // Hidden TYPE_REGULAR value used by GosPackageState parcel format.
        private const val REGULAR_GOS_PACKAGE_STATE_TYPE = 2
    }
}
