// it's not clear how to resolve deprecation warnings for onOptionsItemSelected, they are suppressed
// and not resolved in upstream fragments too
@file:Suppress("DEPRECATION")
package com.android.permissioncontroller.ext.aauto

import android.Manifest
import android.app.compat.gms.AndroidAuto
import android.app.compat.gms.GmsUtils
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.GosPackageState
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.ServiceInfo
import android.ext.PackageId
import android.net.Uri
import android.os.Bundle
import android.os.PatternMatcher
import android.permission.PermissionManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreference
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.BaseSettingsActivity
import com.android.permissioncontroller.ext.addCategory
import com.android.permissioncontroller.ext.addPref
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment
import com.android.permissioncontroller.permission.ui.handheld.pressBack
import getAppInfoOrNull

class AndroidAutoConfigActivity : BaseSettingsActivity() {
    override fun getNavGraphStart() = R.id.android_auto_config
}

class AndroidAutoConfigWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = AndroidAutoConfigFragment()
}

private val PKG_NAME = PackageId.ANDROID_AUTO_NAME

class AndroidAutoConfigFragment : PreferenceFragmentCompat() {
    lateinit var aautoSettingsPref: Preference
    val pkgFlagPrefs = mutableMapOf<Long, SwitchPreference>()
    val packagePrefs = mutableMapOf<String, Preference>()

    lateinit var potentialIssues: PreferenceGroup
    lateinit var aautoVoiceCommandIssues: Preference

    val pkgChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            update()
        }
    }

    lateinit var pkgManager: PackageManager

    override fun onCreatePreferences(savedState: Bundle?, rootKey: String?) {
        setHasOptionsMenu(true)

        val ctx = requireContext()
        pkgManager = ctx.packageManager

        val screen = preferenceManager.createPreferenceScreen(ctx)

        aautoSettingsPref = screen.addPref(getText(R.string.aauto_settings)).apply {
            intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES).apply {
                `package` = PKG_NAME
            }
        }

        screen.addCategory(R.string.permissions_category).apply {
            addPkgFlagPerm(this, AndroidAuto.PKG_FLAG_GRANT_PERMS_FOR_WIRED_ANDROID_AUTO,
                R.string.aauto_wired_perms_title,
                R.string.aauto_wired_perms_confirm,
            )
            addPkgFlagPerm(this, AndroidAuto.PKG_FLAG_GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO,
                R.string.aauto_wireless_perms_title,
                R.string.aauto_wireless_perms_confirm,
            )
            addPkgFlagPerm(this, AndroidAuto.PKG_FLAG_GRANT_AUDIO_ROUTING_PERM,
                R.string.audio_routing_perm_title,
                R.string.audio_routing_perm_confirm,
            )
            addPkgFlagPerm(this, AndroidAuto.PKG_FLAG_GRANT_PERMS_FOR_ANDROID_AUTO_PHONE_CALLS,
                R.string.aauto_phone_perm_title,
                R.string.aauto_phone_perm_confirm,
            )

            addPref(getText(R.string.aauto_app_info_title)).apply {
                setSummary(R.string.aauto_app_info_summary)
                intent = createAppInfoIntent(PKG_NAME)
            }

            addPref(getText(R.string.notif_listener_settings_title)).apply {
                setSummary(R.string.notif_listener_settings_summary)
                intent = getNotifListenerSettingsIntent()
            }
        }

        potentialIssues = screen.addCategory(R.string.potential_issues_category).apply {
            addPref(getText(R.string.aauto_issue_voice_commands)).let {
                aautoVoiceCommandIssues = it
            }
        }

        screen.addCategory(R.string.optional_deps_category).apply {
            addAppPref("com.google.android.apps.maps", getText(R.string.google_maps_app))
            addAppPref("com.google.android.tts", getText(R.string.speech_services_app))
            addAppPref(PackageId.G_SEARCH_APP_NAME, getText(R.string.google_search_app))
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
        requireActivity().setTitle(R.string.android_auto)
        update()
    }

    fun addPkgFlagPerm(dst: PreferenceGroup, flag: Long, title: Int, confirmationText: Int, summary: Int = 0): SwitchPreference {
        val pref = SwitchPreference(context)
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

    private fun createAppInfoIntent(pkgName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkgName, null)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private fun PreferenceGroup.addAppPref(pkgName: String, title: CharSequence): Preference {
        return addPref(title).apply {
            packagePrefs.put(pkgName, this)
        }
    }

    private fun updatePackageFlag(ctx: Context, flag: Long, flagValue: Boolean) {
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

            if (flagValue) {
                when (flag) {
                    AndroidAuto.PKG_FLAG_GRANT_PERMS_FOR_WIRED_ANDROID_AUTO,
                    AndroidAuto.PKG_FLAG_GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO,
                    -> {
                        // this is needed to complete Android Auto initialization
                        pkgManager.sendBootCompletedBroadcastToPackage(PKG_NAME, true, userId)
                    }
                }
            }
        }
    }

    fun update() {
        val aautoAppInfo = pkgManager.getAppInfoOrNull(PKG_NAME)

        if (aautoAppInfo == null) {
            pressBack()
            return
        }

        aautoSettingsPref.apply {
            isEnabled = aautoAppInfo.enabled
            if (aautoAppInfo.enabled) {
                summary = null
            } else {
                setSummary(R.string.aauto_settings_summary_disabled)
            }
        }

        aautoVoiceCommandIssues.apply {
            val text = getVoiceCommandIssuesText()
            isVisible = text != null

            if (text != null) {
                onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
                    AlertDialog.Builder(requireContext()).run {
                        setMessage(text)
                        show()
                    }
                    true
                }
            }
        }

        potentialIssues.isVisible = aautoVoiceCommandIssues.isVisible

        val ps = GosPackageState.getOrDefault(PKG_NAME)

        pkgFlagPrefs.entries.forEach {
            it.value.isChecked = ps.hasPackageFlags(it.key)
        }

        packagePrefs.entries.forEach { e ->
            val pkgName = e.key
            val pref = e.value

            val appInfo = pkgManager.getAppInfoOrNull(pkgName)

            if (appInfo == null) {
                if (pkgManager.getAppInfoOrNull(PackageId.PLAY_STORE_NAME)?.ext()?.packageId == PackageId.PLAY_STORE) {
                    pref.intent = GmsUtils.createAppPlayStoreIntent(pkgName)
                    pref.setSummary(R.string.app_dep_missing_summary)
                } else {
                    pref.intent = null
                    pref.setSummary(R.string.app_dep_missing_summary_no_play_store)
                }
            } else {
                pref.intent = createAppInfoIntent(pkgName)
                pref.setSummary(if (appInfo.enabled) R.string.app_dep_installed
                        else R.string.app_dep_disabled)
            }
        }
    }

    private fun getVoiceCommandIssuesText(): CharSequence? {
        val list = getVoiceCommandIssues()
        if (list.isEmpty()) {
            return null
        }
        return getString(R.string.aauto_issue_voice_commands_header) + "\n\n" +
                list.map {"• " + getString(it) }.joinToString("\n")
    }

    private fun getVoiceCommandIssues(): List<Int> {
        val list = arrayListOf<Int>()

        if (pkgManager.checkPermission(Manifest.permission.RECORD_AUDIO, PackageId.ANDROID_AUTO_NAME) != PERMISSION_GRANTED) {
            list += R.string.aauto_issue_aauto_no_microphone_perm
        }

        val gsaName = PackageId.G_SEARCH_APP_NAME
        val gsaAppInfo = pkgManager.getAppInfoOrNull(gsaName)

        var gsaInstalled = false

        if (gsaAppInfo != null && gsaAppInfo.ext().packageId == PackageId.G_SEARCH_APP) {
            val src = pkgManager.getInstallSourceInfo(gsaName)
            gsaInstalled = src.initiatingPackageName == PackageId.PLAY_STORE_NAME
        }

        if (!gsaInstalled) {
            list += R.string.aauto_issue_gsa_not_installed
            return list
        }

        if (!gsaAppInfo!!.enabled) {
            list += R.string.aauto_issue_gsa_disabled
        }

        if (pkgManager.checkPermission(Manifest.permission.INTERNET, gsaName) != PERMISSION_GRANTED) {
            list += R.string.aauto_issue_gsa_no_network_perm
        }

        if (pkgManager.checkPermission(Manifest.permission.RECORD_AUDIO, gsaName) != PERMISSION_GRANTED) {
            list += R.string.aauto_issue_gsa_no_microphone_perm
        }

        return list
    }

    private fun getNotifListenerSettingsIntent(): Intent? {
        val intent = Intent(NotificationListenerService.SERVICE_INTERFACE).apply {
            `package` = PackageId.ANDROID_AUTO_NAME
        }

        val notifListenerServices = pkgManager.queryIntentServices(intent, 0)
        if (notifListenerServices.size != 1) {
            return null
        }

        val nls: ServiceInfo = notifListenerServices.first().serviceInfo

        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            val nlsComponent = ComponentName(nls.packageName, nls.name)
            putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, nlsComponent.flattenToString())
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            pressBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
