package com.android.permissioncontroller.micspoofing

import androidx.preference.PreferenceFragmentCompat
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment

class MicSpoofingWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = MicSpoofingFragment()
}
