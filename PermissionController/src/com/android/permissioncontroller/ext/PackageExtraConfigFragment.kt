package com.android.permissioncontroller.ext

import android.content.Intent
import android.content.pm.GosPackageState
import android.os.Bundle
import android.os.Process
import android.text.TextUtils
import com.android.permissioncontroller.permission.ui.handheld.pressBack
import com.android.permissioncontroller.permission.utils.KotlinUtils.getBadgedPackageIcon
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPackageLabel

abstract class PackageExtraConfigFragment : BaseSettingsWithLargeHeaderFragment() {
    protected lateinit var pkgName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkgName = requireArguments().getString(Intent.EXTRA_PACKAGE_NAME)!!

        val user = Process.myUserHandle()
        val application = requireActivity().application
        val label = getPackageLabel(application, pkgName, user)

        if (TextUtils.isEmpty(label)) {
            this.pressBack()
            return
        }
        val icon = getBadgedPackageIcon(application, pkgName, user)
        setHeader(icon!!, label, null, user, false)
    }

    fun getGosPackageStateOrDefault(): GosPackageState {
        return GosPackageState.getOrDefault(pkgName)
    }

    fun getGosPackageStateOrPressBack(): GosPackageState? {
        val ps = GosPackageState.get(pkgName)
        if (ps == null) {
            // should happen if the package was racily uninstalled
            pressBack()
        }
        return ps
    }

    fun GosPackageState.Editor.applyOrPressBack() {
        if (apply()) {
            update()
        } else {
            // apply() fails only if the package is uninstalled
            pressBack()
        }
    }

    companion object {
        fun createArgs(packageName: String) = Bundle().apply {
            putString(Intent.EXTRA_PACKAGE_NAME, packageName)
        }
    }
}
