package com.android.permissioncontroller.permission.utils;

import static android.health.connect.HealthPermissions.HEALTH_PERMISSION_GROUP;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.health.connect.HealthConnectManager;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;

public class HealthPermissionUtils {

    public static boolean isHealthPermissionAndShowing(@NonNull String permGroupName) {
        return Utils.isHealthPermissionUiEnabled() && HEALTH_PERMISSION_GROUP.equals(permGroupName);
    }

    public static boolean hasRationaleIntent(@NonNull Context context, @NonNull String pkgName, @NonNull UserHandle user) {
        // Based on HealthPermissionReader#isRationalIntentDeclared, and HealthPermissionReader#getApplicationRationaleIntent
        List<ResolveInfo> ris = context.getPackageManager().queryIntentActivitiesAsUser(
                new Intent(Intent.ACTION_VIEW_PERMISSION_USAGE)
                        .addCategory(HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS)
                        .setPackage(pkgName),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL),
                user
        );

        if (ris.isEmpty()) {
            Log.e(HealthPermissionUtils.class.getSimpleName(), "Health connect permission granting UI for " + pkgName + "is removed to match the standard behavior. "
                    + "Apps requesting these permissions in Android 14 are required to declare an activity alias "
                    + "with intent filters of action \"android.intent.action.VIEW_PERMISSION_USAGE\" "
                    + "and category \"android.intent.category.HEALTH_PERMISSIONS\", "
                    + "linking its rationale and privacy policy for requesting health connect permissions. "
                    + "Refer to the following documentations for details: "
                    + "https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started#show-privacy-policy, "
                    + "https://developer.android.com/reference/android/health/connect/HealthPermissions");

            return false;
        }

        return true;
    }

}
