package com.android.permissioncontroller.privacy

import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.PackageExtraConfigActivity

class LocationIndicatorActivity : PackageExtraConfigActivity() {
    override fun getNavGraphStart() = R.id.location_indicator
}