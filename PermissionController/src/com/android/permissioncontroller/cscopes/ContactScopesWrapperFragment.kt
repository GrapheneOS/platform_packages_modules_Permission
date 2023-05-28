package com.android.permissioncontroller.cscopes

import androidx.preference.PreferenceFragmentCompat
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment

class ContactScopesWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = ContactScopesFragment()
}
