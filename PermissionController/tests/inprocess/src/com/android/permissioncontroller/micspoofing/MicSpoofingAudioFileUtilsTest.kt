package com.android.permissioncontroller.micspoofing

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.permissioncontroller.R
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicSpoofingAudioFileUtilsTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createPickerIntent_includesGenericAudioMimeTypes() {
        val intent = createPickerIntent()

        assertThat(intent.action).isEqualTo(Intent.ACTION_OPEN_DOCUMENT)
        assertThat(intent.type).isEqualTo(AUDIO_PICKER_MIME_TYPE)
        assertThat(requireNotNull(intent.categories)).contains(Intent.CATEGORY_OPENABLE)
        assertThat(requireNotNull(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)).asList())
            .containsAtLeast(
                "audio/wav",
                "audio/x-wav",
                "audio/mpeg",
                "audio/ogg",
                "audio/flac",
                "audio/mp4",
                "audio/aac",
                "audio/x-m4a",
            )
        assertThat(
            requireNotNull(
                intent.getStringArrayListExtra(Intent.EXTRA_RESTRICTIONS_LIST),
            ),
        ).containsAtLeast(
            "com.android.externalstorage.documents",
            "com.android.providers.media.documents",
            "com.android.providers.downloads.documents",
        )
    }

    @Test
    fun isPlausibleAudioFile_acceptsSupportedAudioMimeWithoutWavSpecificChecks() {
        val plausible = isPlausibleAudioFile(
            mimeType = "audio/flac",
            path = "/storage/emulated/0/Download/not-a-wav.bin",
        )

        assertThat(plausible).isTrue()
    }

    @Test
    fun isPlausibleAudioFile_acceptsSupportedAudioExtensionWhenMimeIsGeneric() {
        val plausible = isPlausibleAudioFile(
            mimeType = "application/octet-stream",
            path = "/storage/emulated/0/Music/test-tone.m4a",
        )

        assertThat(plausible).isTrue()
    }

    @Test
    fun isPlausibleAudioFile_rejectsUnknownMimeAndExtension() {
        val plausible = isPlausibleAudioFile(
            mimeType = "application/pdf",
            path = "/storage/emulated/0/Download/document.pdf",
        )

        assertThat(plausible).isFalse()
    }

    @Test
    fun probeReadableAndNonEmptyContent_readsUnknownLengthStream() {
        var openInputStreamCalls = 0

        val readable = probeReadableAndNonEmptyContent(assetLength = -1L) {
            openInputStreamCalls += 1
            ByteArrayInputStream(byteArrayOf(0x2A))
        }

        assertThat(readable).isTrue()
        assertThat(openInputStreamCalls).isEqualTo(1)
    }

    @Test
    fun probeReadableAndNonEmptyContent_rejectsEmptyUnknownLengthStream() {
        val readable = probeReadableAndNonEmptyContent(assetLength = -1L) {
            ByteArrayInputStream(byteArrayOf())
        }

        assertThat(readable).isFalse()
    }

    @Test
    fun probeReadableAndNonEmptyContent_usesKnownLengthWithoutOpeningStream() {
        var openInputStreamCalls = 0

        val readable = probeReadableAndNonEmptyContent(assetLength = 4L) {
            openInputStreamCalls += 1
            ByteArrayInputStream(byteArrayOf(0x2A))
        }

        assertThat(readable).isTrue()
        assertThat(openInputStreamCalls).isEqualTo(0)
    }

    @Test
    fun micSpoofingStrings_doNotMentionWav() {
        assertThat(targetContext.getString(R.string.mic_spoofing_source_choose_custom_file))
            .isEqualTo("Choose custom audio file")
        assertThat(targetContext.getString(R.string.mic_spoofing_source_custom_fallback))
            .isEqualTo("Custom audio file")
        assertThat(targetContext.getString(R.string.mic_spoofing_footer_disabled))
            .isEqualTo(
                "When enabled, Microphone Spoofing replaces app microphone input with " +
                    "silence or audio from a custom audio file.",
            )
        assertThat(targetContext.getString(R.string.mic_spoofing_toast_invalid_audio))
            .isEqualTo("Selected file is not a supported audio file")
    }
}
