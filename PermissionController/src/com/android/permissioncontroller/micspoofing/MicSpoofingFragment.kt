package com.android.permissioncontroller.micspoofing

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.GosPackageState
import android.content.pm.GosPackageStateFlag
import android.ext.micspoofing.MicSpoofingApi
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
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

    private var chooseAudioFileJob: Job? = null

    private val chooseAudioFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let(::onChooseAudioFileResult)
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
        chooseAudioFileJob?.cancel()
        chooseAudioFileJob = null

        super.onDestroy()
    }

    private fun onSourceSelected(selectedValue: String) {
        when (selectedValue) {
            SOURCE_VALUE_DEFAULT -> useDefaultAudioSource()
            SOURCE_VALUE_CHOOSE_CUSTOM_FILE -> launchAudioPicker()
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
                launchAudioPicker()
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

    private fun launchAudioPicker() {
        try {
            chooseAudioFileLauncher.launch(createPickerIntent())
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Unable to launch document picker for audio source selection", e)
            toastManager.showToast(R.string.mic_spoofing_toast_picker_not_found)
        }
    }

    private fun onChooseAudioFileResult(uri: Uri) {
        chooseAudioFileJob?.cancel()
        chooseAudioFileJob = lifecycleScope.launch {
            val path = resolveValidatedAudioPath(uri) ?: return@launch

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

    private fun useDefaultAudioSource() {
        val packageState = getGosPackageState()
        val applied = applyMicSpoofingConfig(packageState, null)

        if (!applied) {
            Log.w(TAG, "Failed to apply default mic spoofing configuration")
            pressBack()
            return
        }

        update()
    }

    private suspend fun resolveValidatedAudioPath(uri: Uri): String? {
        val validationResult = withContext(Dispatchers.IO) {
            validateAudioUri(uri)
        }
        if (validationResult.errorResId == 0) {
            return validationResult.path
        }
        Log.w(
            TAG,
            "Selected audio source failed validation: errorResId=${validationResult.errorResId}",
        )
        toastManager.showToast(validationResult.errorResId)
        return null
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

    private fun validateAudioUri(uri: Uri): SelectedAudioValidationResult {
        val path = convertUriToPath(uri) ?: return SelectedAudioValidationResult(
            errorResId = R.string.mic_spoofing_toast_invalid_location,
        )

        val hasReadableContent = isReadableAndNonEmpty(uri) ?: return SelectedAudioValidationResult(
            errorResId = R.string.mic_spoofing_toast_invalid_audio,
        )
        if (!hasReadableContent) {
            return SelectedAudioValidationResult(
                errorResId = R.string.mic_spoofing_toast_invalid_audio,
            )
        }
        val mimeType = getMimeType(uri)
        if (!isPlausibleAudioFile(mimeType, path)) {
            return SelectedAudioValidationResult(
                errorResId = R.string.mic_spoofing_toast_invalid_audio,
            )
        }

        return SelectedAudioValidationResult(path = path)
    }

    private fun isReadableAndNonEmpty(uri: Uri): Boolean? {
        try {
            val assetLength = context_.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.takeIf { it >= 0 }
            }
            return probeReadableAndNonEmptyContent(
                assetLength = assetLength,
                openInputStream = { context_.contentResolver.openInputStream(uri) },
            )
        } catch (e: IOException) {
            Log.w(TAG, "Failed to verify selected audio readability", e)
            return null
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to verify selected audio readability", e)
            return null
        }
    }

    private fun getMimeType(uri: Uri): String? {
        try {
            return context_.contentResolver.getType(uri)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to resolve selected audio MIME type", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to resolve selected audio MIME type", e)
        }
        return null
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

    private data class SelectedAudioValidationResult(
        val path: String? = null,
        val errorResId: Int = 0,
    )

    private companion object {
        private const val TAG = "MicSpoofingFragment"

        private const val SOURCE_VALUE_DEFAULT = "__default__"
        private const val SOURCE_VALUE_CHOOSE_CUSTOM_FILE = "__choose_custom_file__"
    }
}
