package com.android.permissioncontroller.micspoofing

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
class MicSpoofingPathUtilsTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fileUriToPath_validExternalFile_returnsPath() {
        val file = createTempExternalFile("mic-spoofing-valid")
        try {
            val path = fileUriToPath(targetContext, Uri.fromFile(file), storageVolumes())

            assertThat(path).isNotNull()
            assertThat(path).startsWith("/storage/emulated/${targetContext.user.identifier}/")
            assertThat(File(requireNotNull(path)).canonicalFile).isEqualTo(file.canonicalFile)
        } finally {
            file.delete()
        }
    }

    @Test
    fun fileUriToPath_withEmptyCachedVolumes_returnsNull() {
        val file = createTempExternalFile("mic-spoofing-empty-volumes")
        try {
            val path = fileUriToPath(targetContext, Uri.fromFile(file), emptyList())

            assertThat(path).isNull()
        } finally {
            file.delete()
        }
    }

    @Test
    fun fileUriToPath_nonExternalFile_returnsNull() {
        val file = createTempInternalFile("mic-spoofing-internal")
        try {
            val path = fileUriToPath(targetContext, Uri.fromFile(file), storageVolumes())

            assertThat(path).isNull()
        } finally {
            file.delete()
        }
    }

    @Test
    fun fileUriToPath_invalidUri_returnsNull() {
        val path =
            fileUriToPath(
                targetContext,
                Uri.parse("content://does.not.exist/mic-spoofing-invalid"),
                storageVolumes(),
            )

        assertThat(path).isNull()
    }

    private fun createTempExternalFile(prefix: String): File {
        val externalDir = checkNotNull(targetContext.getExternalFilesDir(null)) {
            "External files directory must be available"
        }
        return File.createTempFile(prefix, ".wav", externalDir).apply { writeText("audio") }
    }

    private fun createTempInternalFile(prefix: String): File {
        return File.createTempFile(prefix, ".wav", targetContext.cacheDir)
            .apply { writeText("audio") }
    }

    private fun storageVolumes(): List<StorageVolume> {
        val storageManager = checkNotNull(
            targetContext.getSystemService(StorageManager::class.java)
        )
        return storageManager.storageVolumes
    }
}
