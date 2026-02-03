package com.android.permissioncontroller.mscopes

import androidx.preference.PreferenceFragmentCompat
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment

class MicrophoneScopesWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = MicrophoneScopesFragment()
}
