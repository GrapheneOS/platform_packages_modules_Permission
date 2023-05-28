package com.android.permissioncontroller.sscopes;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.ext.PackageExtraConfigActivity;

public class StorageScopesActivity extends PackageExtraConfigActivity {

    @Override
    public int getNavGraphStart() {
        return R.id.storage_scopes;
    }
}
