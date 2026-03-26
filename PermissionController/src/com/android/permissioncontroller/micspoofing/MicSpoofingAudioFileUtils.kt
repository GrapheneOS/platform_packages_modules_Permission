package com.android.permissioncontroller.micspoofing

import android.content.Intent
import android.provider.DocumentsContract
import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.InputStream
import java.util.Locale

@VisibleForTesting
const val AUDIO_PICKER_MIME_TYPE = "audio/*"

@VisibleForTesting
val AUDIO_PICKER_MIME_TYPES = arrayOf(
    "audio/wav",
    "audio/x-wav",
    "audio/mpeg",
    "audio/ogg",
    "audio/opus",
    "audio/flac",
    "audio/x-flac",
    "audio/mp4",
    "audio/aac",
    "audio/x-m4a",
    "audio/m4a",
    "audio/mp4a-latm",
)

@VisibleForTesting
val PLAUSIBLE_AUDIO_FILE_EXTENSIONS = setOf(
    "wav",
    "mp3",
    "ogg",
    "oga",
    "opus",
    "flac",
    "aac",
    "m4a",
    "mp4",
)

fun createPickerIntent(): Intent {
    return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = AUDIO_PICKER_MIME_TYPE
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_MIME_TYPES, AUDIO_PICKER_MIME_TYPES)
        putStringArrayListExtra(
            Intent.EXTRA_RESTRICTIONS_LIST,
            arrayListOf(
                DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
                "com.android.providers.media.documents",
                "com.android.providers.downloads.documents",
            ),
        )
    }
}

fun isPlausibleAudioFile(mimeType: String?, path: String): Boolean {
    return hasPlausibleAudioMimeType(mimeType) || hasPlausibleAudioFileExtension(path)
}

@VisibleForTesting
fun probeReadableAndNonEmptyContent(
    assetLength: Long?,
    openInputStream: () -> InputStream?,
): Boolean? {
    if (assetLength != null && assetLength >= 0) {
        return assetLength > 0
    }

    openInputStream().use { input ->
        return input?.read()?.let { it >= 0 }
    }
}

private fun hasPlausibleAudioMimeType(mimeType: String?): Boolean {
    val normalizedMimeType = normalizeMimeType(mimeType) ?: return false
    return normalizedMimeType.startsWith("audio/") || normalizedMimeType == "application/ogg"
}

private fun hasPlausibleAudioFileExtension(path: String): Boolean {
    return File(path)
        .extension
        .lowercase(Locale.ROOT)
        .let(PLAUSIBLE_AUDIO_FILE_EXTENSIONS::contains)
}

private fun normalizeMimeType(mimeType: String?): String? {
    return mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
}
