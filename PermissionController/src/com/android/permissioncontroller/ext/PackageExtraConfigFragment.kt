package com.android.permissioncontroller.ext

import android.content.Intent
import android.content.pm.GosPackageState
import android.os.Bundle
import android.text.TextUtils
import com.android.permissioncontroller.permission.ui.handheld.pressBack
import com.android.permissioncontroller.permission.utils.KotlinUtils.getBadgedPackageIcon
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPackageLabel

abstract class PackageExtraConfigFragment : BaseSettingsWithLargeHeaderFragment() {
    protected lateinit var pkgName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkgName = requireArguments().getString(Intent.EXTRA_PACKAGE_NAME)!!

        val application = requireActivity().application
        val user = requireContext().user
        val label = getPackageLabel(application, pkgName, user)
        // null if the package isn't installed for this user, getPackageLabel() falls back to
        // pkgName in that case
        val icon = getBadgedPackageIcon(application, pkgName, user)

        if (icon == null || TextUtils.isEmpty(label)) {
            this.pressBack()
            return
        }
        setHeader(icon, label, null, user, false)
    }

    fun getGosPackageState(): GosPackageState {
        return GosPackageState.get(pkgName, requireContext().user)
    }

    fun GosPackageState.Editor.applyOrPressBack() {
        if (apply()) {
            update()
        } else {
            // apply() fails only if the package is uninstalled
            pressBack()
        }
    }
}
