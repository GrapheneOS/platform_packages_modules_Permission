package com.android.permissioncontroller.sscopes;

import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment;

public class StorageScopesWrapperFragment extends PermissionsCollapsingToolbarBaseFragment {

    @Override
    public PreferenceFragmentCompat createPreferenceFragment() {
        return new StorageScopesFragment();
    }
}
