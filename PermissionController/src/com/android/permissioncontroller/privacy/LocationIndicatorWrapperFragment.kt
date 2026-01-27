package com.android.permissioncontroller.privacy

import androidx.preference.PreferenceFragmentCompat
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment

class LocationIndicatorWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = LocationIndicatorFragment()
}