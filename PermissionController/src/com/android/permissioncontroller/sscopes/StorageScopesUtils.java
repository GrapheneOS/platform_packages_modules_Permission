/*
 * Copyright (C) 2022 GrapheneOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.sscopes;

import android.Manifest;
import android.app.Application;
import android.app.StorageScope;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateFlag;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.permissioncontroller.ext.ScopesUtils;
import com.android.permissioncontroller.ext.StoragePathUtils;
import com.android.permissioncontroller.permission.utils.KotlinUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class StorageScopesUtils {
    private static final String TAG = "StorageScopesUtils";

    private StorageScopesUtils() {}

    private static boolean validateScopePath(@Nullable String path, Context context, @Nullable List<StorageVolume> cachedVolumes) {
        if (!StoragePathUtils.validatePathBasics(path)) {
            return false;
        }

        return StoragePathUtils.storageVolumeForPath(context, new File(path), cachedVolumes) != null;
    }

    static String pathToUiLabel(Context ctx, List<StorageVolume> volumes, File path) {
        StorageVolume volume = StoragePathUtils.storageVolumeForPath(ctx, path, volumes);

        if (volume != null) {
            String volumeName = volume.isPrimary() ?
                    ctx.getString(com.android.permissioncontroller.R.string.sscopes_main_storage) :
                    volume.getDescription(ctx);

            File volumePath = volume.getDirectory();

            if (volumePath.equals(path)) {
                return volumeName;
            }
            return volumeName + path.getAbsolutePath().substring(volumePath.getAbsolutePath().length());
        }

        return path.getAbsolutePath();
    }

    static String dirUriToPath(Context ctx, Uri uri) {
        final String authority = DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY;

        if (!authority.equals(uri.getAuthority())) {
            Log.e(TAG, "unknown uri " + uri);
            return null;
        }

        Bundle res = null;
        try {
            res = ctx.getContentResolver().call(authority,
                    StorageScope.EXTERNAL_STORAGE_PROVIDER_METHOD_CONVERT_DOC_ID_TO_PATH,
                    DocumentsContract.getTreeDocumentId(uri), null);
        } catch (Exception e) {
            Log.d(TAG, "unable to convert uri " + uri + " to path", e);
        }

        if (res == null) {
            return null;
        }

        final String EXTRA_RESULT = "result"; // DocumentsContract.EXTRA_RESULT is {@hide}
        String path = res.getString(EXTRA_RESULT);

        if (validateScopePath(path, ctx, null)) {
            return path;
        }

        return null;
    }

    static String fileUriToPath(Context ctx, Uri uri, List<StorageVolume> cachedVolumes) {
        String unverifiedPath = null;

        if (isLocalPhotoPickerUri(uri)) {
            ContentResolver cr = ctx.getContentResolver();
            String id = uri.getLastPathSegment();
            var res = cr.call(MediaStore.AUTHORITY, StorageScope.MEDIA_PROVIDER_METHOD_MEDIA_ID_TO_FILE_PATH, id, null);
            if (res != null) {
                unverifiedPath = res.getString(id);
            }
        } else {
            unverifiedPath = StoragePathUtils.resolveFileUriToPath(ctx, uri);
        }

        if (validateScopePath(unverifiedPath, ctx, cachedVolumes)) {
            String path = unverifiedPath;
            return path;
        }

        Log.d(TAG, "invalid path " + unverifiedPath);
        return null;
    }

    private static boolean isLocalPhotoPickerUri(Uri uri) {
        if (!MediaStore.AUTHORITY.equals(uri.getAuthority())) {
            return false;
        }

        List<String> pathSegments = uri.getPathSegments();

        if (pathSegments.size() != 5) {
            return false;
        }

        return "picker".equals(pathSegments.get(0))
                && "com.android.providers.media.photopicker".equals(pathSegments.get(2))
                && "media".equals(pathSegments.get(3));
    }

    static <T> ArrayList<T> arrayListOf(T[] array) {
        return new ArrayList<>(Arrays.asList(array));
    }

    public static boolean isStorageScopesEnabled(Context ctx, String pkgName) {
        return isStorageScopesEnabled(GosPackageState.get(pkgName, ctx.getUser()));
    }

    public static boolean isStorageScopesEnabled(GosPackageState ps) {
        return ps.hasFlag(GosPackageStateFlag.STORAGE_SCOPES_ENABLED);
    }

    @Nullable
    static StorageScope[] getStorageScopes(Context ctx, String pkgName) {
        GosPackageState s = GosPackageState.get(pkgName, ctx.getUser());
        if (!isStorageScopesEnabled(s)) {
            return null;
        }
        return StorageScope.deserializeArray(s);
    }

    static String getFullLabelForPackage(Application app, String[] uidPkgs, UserHandle user) {
        StringBuilder res = new StringBuilder();

        for (int i = 0; i < uidPkgs.length; ++i) {
            if (i != 0) {
                res.append('\n');
            }

            res.append("• ");
            res.append(KotlinUtils.INSTANCE.getPackageLabel(app, uidPkgs[i], user));
        }

        return res.toString();
    }

    private static final Set<String> STORAGE_PERMISSION_GROUPS = Set.of(new String[] {
            Manifest.permission_group.STORAGE,
            Manifest.permission_group.READ_MEDIA_AURAL,
            Manifest.permission_group.READ_MEDIA_VISUAL,
    });

    public static boolean isStoragePermissionGroup(String name) {
        return STORAGE_PERMISSION_GROUPS.contains(name);
    }

    // returns whether at least one permission was revoked
    static boolean revokeStoragePermissions(Context ctx, String pkgName) {
        List<String> perms = ScopesUtils.INSTANCE.getGroupPerms(STORAGE_PERMISSION_GROUPS);

        String[] opPerms = {
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_MEDIA,
        };

        return ScopesUtils.INSTANCE.revokePermissions(ctx, pkgName, perms, opPerms);
    }
}
