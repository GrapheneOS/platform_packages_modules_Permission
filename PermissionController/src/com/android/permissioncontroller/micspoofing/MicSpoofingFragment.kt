package com.android.permissioncontroller.micspoofing

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.GosPackageState
import android.content.pm.GosPackageStateFlag
import android.ext.micspoofing.MicSpoofingApi
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.PackageExtraConfigFragment
import com.android.permissioncontroller.ext.ScopesUtils
import com.android.permissioncontroller.ext.addOrRemove
import com.android.permissioncontroller.ext.createFooterPreference
import com.android.permissioncontroller.permission.ui.handheld.PermissionPreference
import com.android.permissioncontroller.permission.ui.handheld.PermissionPreferenceCategory
import com.android.permissioncontroller.permission.ui.handheld.pressBack
import com.android.permissioncontroller.permission.ui.handheld.v36.PermissionSelectorWithWidgetPreference
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.MainSwitchPreference
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MicSpoofingFragment : PackageExtraConfigFragment() {

    private lateinit var mainSwitch: MainSwitchPreference
    private lateinit var sourceCategory: PermissionPreferenceCategory
    private lateinit var footer: FooterPreference

    private var chooseWavJob: Job? = null

    private val chooseWavLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let(::onChooseWavResult)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainSwitch = MainSwitchPreference(context_).apply {
            setTitle(R.string.mic_spoofing_enable)
            setOnPreferenceChangeListener { _, newValue ->
                setMicSpoofingEnabled(newValue == true)
                false
            }
        }

        sourceCategory = PermissionPreferenceCategory(context_).apply {
            title = getText(R.string.mic_spoofing_source)
        }

        footer = createFooterPreference()

        update()
    }

    override fun update() {
        val packageState = getGosPackageState()
        val enabled = packageState.hasFlag(GosPackageStateFlag.MIC_SPOOFING_ENABLED)

        mainSwitch.isChecked = enabled

        addOrRemove(mainSwitch, true)
        addOrRemove(sourceCategory, enabled)
        if (enabled) {
            updateSourceCategory(packageState)
        }

        footer.summary = getFooterSummary(packageState, enabled)
        addOrRemove(footer, true)
    }

    override fun onDestroy() {
        chooseWavJob?.cancel()
        chooseWavJob = null

        super.onDestroy()
    }

    private fun onSourceSelected(selectedValue: String) {
        when (selectedValue) {
            SOURCE_VALUE_DEFAULT -> useDefaultWav()
            SOURCE_VALUE_CHOOSE_CUSTOM_FILE -> launchWavPicker()
        }
    }

    private fun updateSourceCategory(packageState: GosPackageState) {
        val currentPath = MicSpoofingApi.getPath(packageState.micSpoofingConfig)
        val entries = mutableListOf<SourceEntry>()

        entries.add(
            SourceEntry(
                value = SOURCE_VALUE_DEFAULT,
                label = getString(R.string.mic_spoofing_source_default),
            ),
        )

        if (currentPath != null) {
            entries.add(
                SourceEntry(
                    value = currentPath,
                    label = getCustomSourceDisplayName(currentPath),
                ),
            )
        }

        entries.add(
            SourceEntry(
                value = SOURCE_VALUE_CHOOSE_CUSTOM_FILE,
                label = getString(R.string.mic_spoofing_source_choose_custom_file),
            ),
        )

        val selectedValue = currentPath ?: SOURCE_VALUE_DEFAULT
        renderSourceEntries(entries, selectedValue)
    }

    private fun createSourceOptionPreference(
        entry: SourceEntry,
        checked: Boolean,
    ): PermissionSelectorWithWidgetPreference {
        return PermissionSelectorWithWidgetPreference(context_).apply {
            title = entry.label
            isChecked = checked
            setOnClickListener {
                if (!checked) {
                    onSourceSelected(entry.value)
                }
            }
        }
    }

    private fun createChooseCustomFilePreference(entry: SourceEntry): PermissionPreference {
        return PermissionPreference(context_).apply {
            title = entry.label
            setIcon(R.drawable.ic_settings_open)
            setOnPreferenceClickListener {
                launchWavPicker()
                true
            }
        }
    }

    private fun renderSourceEntries(entries: List<SourceEntry>, selectedValue: String) {
        sourceCategory.removeAll()
        for (entry in entries) {
            if (entry.value == SOURCE_VALUE_CHOOSE_CUSTOM_FILE) {
                sourceCategory.addPreference(createChooseCustomFilePreference(entry))
            } else {
                sourceCategory.addPreference(
                    createSourceOptionPreference(entry, selectedValue == entry.value),
                )
            }
        }
    }

    private fun setMicSpoofingEnabled(enabled: Boolean) {
        if (enabled) {
            ScopesUtils.revokePermissions(
                context_,
                pkgName,
                listOf(Manifest.permission.RECORD_AUDIO),
            )
        }

        GosPackageState.edit(pkgName, context_.user).run {
            setFlagState(GosPackageStateFlag.MIC_SPOOFING_ENABLED, enabled)
            setNotifyUidAfterApply(true)
            applyOrPressBack()
        }
    }

    private fun launchWavPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = MIME_AUDIO_WAV
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_AUDIO_WAV, MIME_AUDIO_X_WAV))
                putStringArrayListExtra(
                    Intent.EXTRA_RESTRICTIONS_LIST,
                    arrayListOf(
                        DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
                        "com.android.providers.media.documents",
                        "com.android.providers.downloads.documents",
                    ),
                )
            }
            chooseWavLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Unable to launch document picker for WAV source selection", e)
            toastManager.showToast(R.string.mic_spoofing_toast_picker_not_found)
        }
    }

    private fun onChooseWavResult(uri: Uri) {
        chooseWavJob?.cancel()
        chooseWavJob = lifecycleScope.launch {
            if (!validateSelectedWav(uri)) {
                return@launch
            }

            val path = withContext(Dispatchers.IO) {
                convertUriToPath(uri)
            }
            if (path == null) {
                toastManager.showToast(R.string.mic_spoofing_toast_invalid_location)
                return@launch
            }

            val oldState = getGosPackageState()
            val newConfig = MicSpoofingApi.buildCustomPathConfig(path)

            val applied = oldState.createEditor(pkgName, context_.user).run {
                setMicSpoofingConfig(newConfig)
                setNotifyUidAfterApply(true)
                apply()
            }

            if (!applied) {
                Log.w(TAG, "Failed to apply custom mic spoofing config")
                pressBack()
                return@launch
            }

            update()
        }
    }

    private fun useDefaultWav() {
        val packageState = getGosPackageState()
        val applied = applyMicSpoofingConfig(packageState, null)

        if (!applied) {
            Log.w(TAG, "Failed to apply default mic spoofing configuration")
            pressBack()
            return
        }

        update()
    }

    private suspend fun validateSelectedWav(uri: Uri): Boolean {
        val validationError = withContext(Dispatchers.IO) {
            validateWavUri(uri)
        }
        if (validationError == 0) {
            return true
        }
        Log.w(TAG, "Selected WAV source failed validation: errorResId=$validationError")
        toastManager.showToast(validationError)
        return false
    }

    private fun convertUriToPath(uri: Uri): String? {
        return fileUriToPath(
            context = context_,
            uri = uri,
            cachedVolumes = context_
                .getSystemService(StorageManager::class.java)
                ?.storageVolumes,
        )
    }

    private fun validateWavUri(uri: Uri): Int {
        val fileSize = getFileSize(uri) ?: return R.string.mic_spoofing_toast_invalid_wav
        if (fileSize > MAX_WAV_FILE_SIZE_BYTES) {
            return R.string.mic_spoofing_toast_wav_too_large
        }
        if (!hasWavHeader(uri)) {
            return R.string.mic_spoofing_toast_invalid_wav
        }
        return 0
    }

    private fun getFileSize(uri: Uri): Long? {
        try {
            context_.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length >= 0) {
                    return afd.length
                }
            }

            context_.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) {
                        break
                    }
                    total += count
                    if (total > MAX_WAV_FILE_SIZE_BYTES) {
                        break
                    }
                }
                return total
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read selected file size", e)
            return null
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read selected file size", e)
            return null
        }

        return null
    }

    private fun hasWavHeader(uri: Uri): Boolean {
        val header = ByteArray(12)
        try {
            context_.contentResolver.openInputStream(uri)?.use { input ->
                var read = 0
                while (read < header.size) {
                    val count = input.read(header, read, header.size - read)
                    if (count < 0) {
                        Log.w(TAG, "Selected WAV source is too short to contain full header")
                        return false
                    }
                    read += count
                }
            } ?: return false
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read WAV header", e)
            return false
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read WAV header", e)
            return false
        }

        return checkMagicBytes(header, 0, RIFF_MAGIC) &&
                checkMagicBytes(header, 8, WAVE_MAGIC)
    }

    private fun applyMicSpoofingConfig(
        packageState: GosPackageState,
        config: ByteArray?,
    ): Boolean {
        return packageState.createEditor(pkgName, context_.user).run {
            setMicSpoofingConfig(config)
            setNotifyUidAfterApply(true)
            apply()
        }
    }

    private fun checkMagicBytes(
        header: ByteArray,
        startIndex: Int,
        magic: String,
    ): Boolean {
        if (header.size < startIndex + magic.length) {
            return false
        }

        for (i in magic.indices) {
            if (header[startIndex + i] != magic[i].code.toByte()) {
                return false
            }
        }

        return true
    }

    private fun getFooterSummary(packageState: GosPackageState, enabled: Boolean): CharSequence {
        if (!enabled) {
            return getText(R.string.mic_spoofing_footer_disabled)
        }

        val path = MicSpoofingApi.getPath(packageState.micSpoofingConfig)
        if (path != null) {
            return getString(
                R.string.mic_spoofing_footer_enabled_custom,
                getCustomSourceDisplayName(path),
            )
        }

        return getText(R.string.mic_spoofing_footer_enabled_default)
    }

    private fun getCustomSourceDisplayName(path: String): String {
        return File(path).name.ifEmpty { fallbackSourceName() }
    }

    private fun fallbackSourceName(): String {
        return getString(R.string.mic_spoofing_source_custom_fallback)
    }

    override fun getTitle(): CharSequence {
        return getText(R.string.microphone_spoofing)
    }

    private data class SourceEntry(
        val value: String,
        val label: String,
    )

    private companion object {
        private const val TAG = "MicSpoofingFragment"

        private const val MAX_WAV_FILE_SIZE_BYTES = 100L * 1024 * 1024
        private const val MIME_AUDIO_WAV = "audio/wav"
        private const val MIME_AUDIO_X_WAV = "audio/x-wav"

        private const val SOURCE_VALUE_DEFAULT = "__default__"
        private const val SOURCE_VALUE_CHOOSE_CUSTOM_FILE = "__choose_custom_file__"

        private const val RIFF_MAGIC = "RIFF"
        private const val WAVE_MAGIC = "WAVE"
    }
}
