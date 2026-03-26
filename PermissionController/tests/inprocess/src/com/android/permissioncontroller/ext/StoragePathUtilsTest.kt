package com.android.permissioncontroller.ext

import android.content.ContextWrapper
import android.net.Uri
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoragePathUtilsTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun validatePathBasics_validAbsolutePath_returnsTrue() {
        assertThat(validatePathBasics("/storage/emulated/0/Music/test.wav")).isTrue()
    }

    @Test
    fun validatePathBasics_null_returnsFalse() {
        assertThat(validatePathBasics(null)).isFalse()
    }

    @Test
    fun validatePathBasics_relativePath_returnsFalse() {
        assertThat(validatePathBasics("storage/emulated/0/Music/test.wav")).isFalse()
    }

    @Test
    fun validatePathBasics_emptyComponent_returnsFalse() {
        assertThat(validatePathBasics("/storage//emulated/0/test.wav")).isFalse()
    }

    @Test
    fun validatePathBasics_containsNul_returnsFalse() {
        assertThat(validatePathBasics("/storage/emulated/0/test\u0000.wav")).isFalse()
    }

    @Test
    fun validatePathBasics_overMaxUtf8Length_returnsFalse() {
        val tooLong = "/" + "a".repeat(4097)

        assertThat(validatePathBasics(tooLong)).isFalse()
    }

    @Test
    fun storageVolumeForPath_withMatchingCachedVolumes_returnsVolume() {
        val file = createTempExternalFile("storage-volume-match")
        try {
            val volume = storageVolumeForPath(targetContext, file, storageVolumes())

            assertThat(volume).isNotNull()
        } finally {
            file.delete()
        }
    }

    @Test
    fun storageVolumeForPath_withEmptyCachedVolumes_returnsNull() {
        val file = createTempExternalFile("storage-volume-empty")
        try {
            val volume = storageVolumeForPath(targetContext, file, emptyList())

            assertThat(volume).isNull()
        } finally {
            file.delete()
        }
    }

    @Test
    fun storageVolumeForPath_withoutStorageManager_returnsNull() {
        val contextWithoutStorageManager = object : ContextWrapper(targetContext) {
            override fun getSystemService(name: String): Any? {
                if (name == android.content.Context.STORAGE_SERVICE) {
                    return null
                }
                return super.getSystemService(name)
            }
        }

        val volume = storageVolumeForPath(
            contextWithoutStorageManager,
            File("/storage/emulated/0"),
            null,
        )

        assertThat(volume).isNull()
    }

    @Test
    fun resolveFileUriToPath_existingFileUri_returnsFilePath() {
        val file = createTempExternalFile("resolve-file-uri")
        try {
            val resolvedPath = resolveFileUriToPath(targetContext, Uri.fromFile(file))

            assertThat(resolvedPath).isNotNull()
            assertThat(File(requireNotNull(resolvedPath)).canonicalFile)
                .isEqualTo(file.canonicalFile)
        } finally {
            file.delete()
        }
    }

    @Test
    fun resolveFileUriToPath_invalidUri_returnsNull() {
        val resolvedPath = resolveFileUriToPath(
            targetContext,
            Uri.parse("content://does.not.exist/invalid")
        )

        assertThat(resolvedPath).isNull()
    }

    private fun createTempExternalFile(prefix: String): File {
        val externalDir = checkNotNull(targetContext.getExternalFilesDir(null)) {
            "External files directory must be available"
        }
        return File
            .createTempFile(prefix, ".txt", externalDir)
            .apply { writeText("test") }
    }

    private fun storageVolumes(): List<StorageVolume> {
        val storageManager = checkNotNull(
            targetContext.getSystemService(StorageManager::class.java)
        )
        return storageManager.storageVolumes
    }
}
