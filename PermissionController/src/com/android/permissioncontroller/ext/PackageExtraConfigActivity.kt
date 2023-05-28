package com.android.permissioncontroller.ext

import android.content.Intent
import android.os.Bundle
import android.os.UserHandle

abstract class PackageExtraConfigActivity : BaseSettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
        check(user == null || user == this.user)
    }

    override fun getFragmentArgs(): Bundle {
        val packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        return Bundle().apply {
            putString(Intent.EXTRA_PACKAGE_NAME, packageName)
        }
    }
}
