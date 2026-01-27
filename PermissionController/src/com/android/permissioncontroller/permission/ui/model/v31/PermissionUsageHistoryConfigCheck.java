package com.android.permissioncontroller.permission.ui.model.v31;

import android.app.AppOpsManager;
import android.content.Context;
import android.util.ArraySet;

import com.android.permissioncontroller.PermissionControllerApplication;
import com.android.permissioncontroller.permission.utils.PermissionMapping;

public class PermissionUsageHistoryConfigCheck {
    static final String TAG = "PermissionUsageHistoryConfigCheck";

    public static void run() {
        if (!android.os.Flags.isDevBuild()) {
            return;
        }

        Context context = PermissionControllerApplication.get();
        if (!context.getUser().isSystem()) {
            return;
        }

        var permSet = new ArraySet<String>();

        for (String permGroup : PermissionUsageControlPreferenceUtils.PERMISSIONS_WITH_USAGE_HISTORY) {
            for (String perm : PermissionMapping.getPlatformPermissionNamesOfGroup(permGroup)) {
                if (!permSet.add(perm)) {
                    throw new IllegalStateException(perm);
                }
            }
        }
        String[] perms = permSet.toArray(new String[0]);
        context.getSystemService(AppOpsManager.class).checkHistoricalRegistryConfig(perms);
    }
}
