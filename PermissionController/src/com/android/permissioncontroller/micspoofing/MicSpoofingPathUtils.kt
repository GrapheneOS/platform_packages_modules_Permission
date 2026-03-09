@file:JvmName("MicSpoofingPathUtils")

package com.android.permissioncontroller.micspoofing

import android.content.Context
import android.net.Uri
import android.os.storage.StorageVolume
import android.util.Log
import com.android.permissioncontroller.ext.resolveFileUriToPath
import com.android.permissioncontroller.ext.storageVolumeForPath
import com.android.permissioncontroller.ext.validatePathBasics
import java.io.File

private const val TAG = "MicSpoofingPathUtils"

fun fileUriToPath(
    context: Context,
    uri: Uri,
    cachedVolumes: List<StorageVolume>?,
): String? {
    val unverifiedPath = resolveFileUriToPath(context, uri)

    if (!validateSourcePath(unverifiedPath, context, cachedVolumes)) {
        Log.w(TAG, "Invalid mic spoofing path $unverifiedPath")
        return null
    }

    return unverifiedPath
}

private fun validateSourcePath(
    path: String?,
    context: Context,
    cachedVolumes: List<StorageVolume>?,
): Boolean {
    if (path == null) {
        return false
    }

    if (!validatePathBasics(path)) {
        return false
    }

    val userId = context.user.identifier
    val expectedPrefix = "/storage/emulated/$userId/"
    if (!path.startsWith(expectedPrefix)) {
        return false
    }

    val volume = storageVolumeForPath(context, File(path), cachedVolumes) ?: return false

    return volume.isPrimary
}
