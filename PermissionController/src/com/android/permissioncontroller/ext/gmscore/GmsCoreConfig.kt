package com.android.permissioncontroller.ext.gmscore

import android.app.compat.gms.GmsCorePackageFlag
import android.content.pm.ApplicationInfo
import android.ext.PackageId
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.BaseGosPkgStateConfigFragment
import com.android.permissioncontroller.ext.BaseSettingsActivity
import com.android.permissioncontroller.ext.addCategory
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment

class GmsCoreConfigActivity : BaseSettingsActivity() {
    override fun getNavGraphStart() = R.id.gmscore_config
}

class GmsCoreConfigWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = GmsCoreConfigFragment()
}

class GmsCoreConfigFragment : BaseGosPkgStateConfigFragment(
    packageName = PackageId.GMS_CORE_NAME,
    titleStringRes = R.string.gmscore_settings
) {
    override fun configurePreferenceScreen(screen: PreferenceScreen) {
        screen.addCategory(R.string.rcs_activation_category).apply {
            addPkgFlagPerm(
                this, GmsCorePackageFlag.GRANT_PERMS_FOR_ICC_AUTHENTICATION,
                R.string.gmscore_icc_auth_perms_title,
                R.string.gmscore_icc_auth_perms_confirm,
            )
        }
    }

    override fun updateNonPkgStateUi(applicationInfo: ApplicationInfo) {}
}
