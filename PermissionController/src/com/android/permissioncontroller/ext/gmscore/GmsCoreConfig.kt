package com.android.permissioncontroller.ext.gmscore

import android.app.compat.gms.GmsCorePackageFlag
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.GosPackageState
import android.content.pm.PackageManager
import android.ext.PackageId
import android.os.Bundle
import android.os.PatternMatcher
import android.permission.PermissionManager
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.BaseSettingsActivity
import com.android.permissioncontroller.ext.addCategory
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment
import com.android.permissioncontroller.permission.ui.handheld.PermissionsFrameFragment
import com.android.permissioncontroller.permission.ui.handheld.pressBack
import getAppInfoOrNull

class GmsCoreConfigActivity : BaseSettingsActivity() {
    override fun getNavGraphStart() = R.id.gmscore_config
}

class GmsCoreConfigWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = GmsCoreConfigFragment()
}

private val PKG_NAME = PackageId.GMS_CORE_NAME

class GmsCoreConfigFragment : PermissionsFrameFragment() {
    val pkgFlagPrefs = mutableMapOf<Int, SwitchPreferenceCompat>()
    val packagePrefs = mutableMapOf<String, Preference>()

    val pkgChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            update()
        }
    }

    lateinit var pkgManager: PackageManager

    override fun onCreatePreferences(savedState: Bundle?, rootKey: String?) {
        @Suppress("DEPRECATION") // see onOptionsItemSelected
        setHasOptionsMenu(true)

        val ctx = requireContext()
        pkgManager = ctx.packageManager

        val screen = preferenceManager.createPreferenceScreen(ctx)

        screen.addCategory(R.string.rcs_activation_category).apply {
            addPkgFlagPerm(
                this, GmsCorePackageFlag.GRANT_PERMS_FOR_ICC_AUTHENTICATION,
                R.string.gmscore_icc_auth_perms_title,
                R.string.gmscore_icc_auth_perms_confirm,
            )
        }

        IntentFilter().run {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
            packagePrefs.keys.forEach {
                addDataSchemeSpecificPart(it, PatternMatcher.PATTERN_LITERAL)
            }
            addDataSchemeSpecificPart(PKG_NAME, PatternMatcher.PATTERN_LITERAL)
            ctx.registerReceiver(pkgChangeReceiver, this)
        }

        preferenceScreen = screen
    }

    override fun onDestroy() {
        super.onDestroy()
        requireContext().unregisterReceiver(pkgChangeReceiver)
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.gmscore_settings)
        update()
    }

    fun addPkgFlagPerm(dst: PreferenceGroup, flag: Int, title: Int, confirmationText: Int, summary: Int = 0): SwitchPreferenceCompat {
        val pref = SwitchPreferenceCompat(dst.context)
        pref.setTitle(title)
        if (summary != 0) {
            pref.setSummary(summary)
        }

        pkgFlagPrefs[flag] = pref

        pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValueB ->
            val newValue = newValueB as Boolean

            val ctx = requireContext()

            if (newValue) {
                AlertDialog.Builder(ctx).run {
                    setMessage(getText(confirmationText))
                    setPositiveButton(R.string.grant_dialog_button_allow) { _, _ ->
                        updatePackageFlag(ctx, flag, true)
                        update()
                    }
                    setNegativeButton(R.string.cancel, null)
                    show()
                }
                false
            } else {
                updatePackageFlag(ctx, flag, false)
                true
            }
        }

        dst.addPreference(pref)
        return pref
    }

    private fun updatePackageFlag(ctx: Context, flag: Int, flagValue: Boolean) {
        val userId = android.os.Process.myUserHandle().identifier
        GosPackageState.edit(PKG_NAME, userId).run {
            setPackageFlagState(flag, flagValue)
            applyOrPressBack()
        }

        val permManager = ctx.getSystemService(PermissionManager::class.java)!!
        permManager.updatePermissionState(PKG_NAME, userId)

        GosPackageState.edit(PKG_NAME, userId).run {
            killUidAfterApply()
            applyOrPressBack()
        }

        val isPkgEnabled = pkgManager.getApplicationInfo(PKG_NAME, 0).enabled
        if (isPkgEnabled) {
            // this is needed to invalidate cached system_server state
            pkgManager.setApplicationEnabledSetting(PKG_NAME, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, userId)
            pkgManager.setApplicationEnabledSetting(PKG_NAME, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, userId)
        }
    }

    fun update() {
        val gmsCoreAppInfo = pkgManager.getAppInfoOrNull(PKG_NAME)

        if (gmsCoreAppInfo == null) {
            pressBack()
            return
        }

        val ps = GosPackageState.get(PKG_NAME, requireContext().user)

        pkgFlagPrefs.entries.forEach {
            it.value.isChecked = ps.hasPackageFlag(it.key)
        }
    }

    fun GosPackageState.Editor.applyOrPressBack() {
        if (apply()) {
            update()
        } else {
            // apply() fails only if the package is uninstalled
            pressBack()
        }
    }

    // it's not clear how to resolve deprecation warnings for setHasOptionsMenu and onOptionsItemSelected,
    // they are suppressed in upstream fragments that use android.R.id.home too
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            pressBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
