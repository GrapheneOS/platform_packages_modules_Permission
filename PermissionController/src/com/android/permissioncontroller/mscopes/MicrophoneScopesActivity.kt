package com.android.permissioncontroller.mscopes

import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.PackageExtraConfigActivity

class MicrophoneScopesActivity : PackageExtraConfigActivity() {
    override fun getNavGraphStart() = R.id.microphone_scopes
}
