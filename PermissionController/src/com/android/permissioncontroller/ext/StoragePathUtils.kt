@file:JvmName("StoragePathUtils")

package com.android.permissioncontroller.ext

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.system.Os
import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets

private const val TAG = "StoragePathUtils"

private const val MAX_PATH_LENGTH_IN_UTF8_BYTES = 4096

fun storageVolumeForPath(
    context: Context,
    file: File,
    cachedVolumes: List<StorageVolume>?,
): StorageVolume? {
    val volumes = cachedVolumes
        ?: context.getSystemService(StorageManager::class.java)?.storageVolumes
        ?: run {
            Log.w(TAG, "No storage volumes found")
            return null
        }

    volumes.forEach { volume ->
        volume.directory?.let { volumeRoot ->
            if (dirContainsOrEquals(volumeRoot, file)) {
                return volume
            }
        }
    }

    return null
}

fun validatePathBasics(path: String?): Boolean {
    if (path == null) {
        return false
    }

    if (!StringUtils.isUtf16(path)) {
        return false
    }

    if (path.indexOf('\u0000') >= 0) {
        return false
    }

    val components = path.split("/")
    if (components.size < 2) {
        return false
    }

    if (components[0].isNotEmpty()) {
        return false
    }

    for (i in 1 until components.size) {
        if (components[i].isEmpty()) {
            return false
        }
    }

    if (path.toByteArray(StandardCharsets.UTF_8).size > MAX_PATH_LENGTH_IN_UTF8_BYTES) {
        return false
    }

    return true
}

fun resolveFileUriToPath(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
            val fdPath = "/proc/self/fd/${parcelFileDescriptor.fd}"
            var realPath = Os.readlink(fdPath)
            val userId = context.user.identifier
            val mntUserPrefix = "/mnt/user/$userId/"

            if (realPath.startsWith(mntUserPrefix)) {
                realPath = realPath.replaceFirst(mntUserPrefix, "/storage/")
            }

            if (File(realPath).isFile) {
                realPath
            } else {
                Log.d(TAG, "$realPath is not a file")
                null
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Unable to convert uri $uri to path", e)
        null
    }
}

private fun dirContainsOrEquals(dir: File, file: File): Boolean {
    var current: File? = file

    while (current != null) {
        if (current == dir) {
            return true
        }

        current = current.parentFile
    }

    return false
}
