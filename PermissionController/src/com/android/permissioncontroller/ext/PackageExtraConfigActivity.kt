package com.android.permissioncontroller.ext

import android.content.Intent
import android.os.Bundle

abstract class PackageExtraConfigActivity : BaseSettingsActivity() {
    override fun getFragmentArgs(): Bundle {
        val packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        return Bundle().apply {
            putString(Intent.EXTRA_PACKAGE_NAME, packageName)
        }
    }
}
