package com.android.permissioncontroller.micspoofing

import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.PackageExtraConfigActivity

class MicSpoofingActivity : PackageExtraConfigActivity() {
    override fun getNavGraphStart() = R.id.mic_spoofing
}
