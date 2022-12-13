package com.android.permissioncontroller.permission.ui.handheld.v34

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithHeader

/** Fragment to display data sharing updates for installed apps. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatesFragment : SettingsWithHeader() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // TODO(b/261914980): Update final strings.
        setHeader(
            /* icon= */ null,
            "Data Sharing updates",
            /* infoIntent= */ null,
            /* userHandle= */ null)
        super.onCreate(savedInstanceState)
    }

    /** Companion object for [AppDataSharingUpdatesFragment]. */
    companion object {
        /**
         * Creates a [Bundle] with the arguments needed by this fragment.
         *
         * @param sessionId the current session ID
         * @return a [Bundle] with all of the required args
         */
        fun createArgs(sessionId: Long) =
                Bundle().apply {
                    putLong(EXTRA_SESSION_ID, sessionId)
                }
    }
}
