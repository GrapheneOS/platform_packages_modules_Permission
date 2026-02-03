package com.android.permissioncontroller.mscopes

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.GosPackageState
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.os.UserHandle
import android.provider.OpenableColumns
import android.system.Os
import java.io.File
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.preference.Preference
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.PackageExtraConfigFragment
import com.android.permissioncontroller.ext.PreferenceWithImageButton
import com.android.permissioncontroller.ext.addMenuItem
import com.android.permissioncontroller.ext.addOrRemove
import com.android.permissioncontroller.ext.createFooterPreference
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.MainSwitchPreference

class MicrophoneScopesFragment : PackageExtraConfigFragment(), MenuProvider {
    lateinit var mainSwitch: MainSwitchPreference
    lateinit var selectAudioPref: Preference
    lateinit var audioFilePref: PreferenceWithImageButton
    lateinit var footer: FooterPreference

    private val audioFilePicker: ActivityResultLauncher<Unit> = registerForActivityResult(
        AudioFilePickerContract()
    ) { uri ->
        if (uri != null) {
            setAudioFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainSwitch = MainSwitchPreference(context_).apply {
            setTitle(R.string.mscopes_enable)
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue == true) {
                    setMicrophoneScopesEnabled(true)
                }
                true
            }
        }

        selectAudioPref = Preference(context_).apply {
            setTitle(R.string.mscopes_audio_source)
            setOnPreferenceClickListener {
                pickAudioFile()
                true
            }
        }

        audioFilePref = PreferenceWithImageButton(context_).apply {
            isSelectable = false
            setupButton(R.drawable.ic_item_remove, getText(R.string.scopes_btn_remove_scope)) {
                clearAudioFile()
            }
        }

        footer = createFooterPreference()

        requireActivity().addMenuProvider(this, this)

        update()
    }

    override fun update() {
        val gosPackageState = getGosPackageState()
        val enabled = MicrophoneScopesUtils.isMicrophoneScopesEnabled(gosPackageState)

        addOrRemove(mainSwitch, !enabled)
        if (!enabled) {
            mainSwitch.isChecked = false
        }

        addOrRemove(selectAudioPref, enabled)

        if (enabled) {
            val storage = MicrophoneScopesStorage.deserialize(gosPackageState)
            val hasFile = storage.audioFilePath != null
            selectAudioPref.summary = if (hasFile) null else getString(R.string.mscopes_no_file_selected)
            if (hasFile) {
                audioFilePref.title = resolveDisplayName(storage)
            }
            addOrRemove(audioFilePref, hasFile)
        } else {
            addOrRemove(audioFilePref, false)
        }

        if (!enabled) {
            footer.setSummary(R.string.mscopes_disabled_footer)
            footer.setLearnMoreAction {
                val link = "https://github.com/GrapheneOS/os-issue-tracker/issues/7226"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
        }

        addOrRemove(footer, !enabled)

        requireActivity().invalidateMenu()
    }

    private fun resolveDisplayName(storage: MicrophoneScopesStorage): String {
        val resolvedPath = storage.resolvedFilePath
        if (resolvedPath != null) {
            val file = File(resolvedPath)
            if (file.exists()) {
                return pathToUiLabel(file)
            }
            return getString(R.string.mscopes_file_not_found)
        }

        val uri = Uri.parse(storage.audioFilePath)
        try {
            context_.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                var realPath = Os.readlink("/proc/self/fd/${pfd.fd}")
                if (realPath.startsWith("/mnt/user/")) {
                    realPath = realPath.replaceFirst(
                        "/mnt/user/${UserHandle.myUserId()}/", "/storage/"
                    )
                }
                val file = File(realPath)
                if (file.isFile) {
                    return pathToUiLabel(file)
                }
            }
        } catch (_: Exception) {
        }

        try {
            context_.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { return it }
                }
            }
        } catch (_: Exception) {
        }

        return getString(R.string.mscopes_file_not_found)
    }

    private fun pathToUiLabel(file: File): String {
        val volumes = context_.getSystemService(StorageManager::class.java).storageVolumes
        for (volume in volumes) {
            val volumeDir = volume.directory ?: continue
            val volumePath = volumeDir.absolutePath
            if (file.absolutePath.startsWith(volumePath)) {
                val volumeName = if (volume.isPrimary) {
                    getString(R.string.sscopes_main_storage)
                } else {
                    volume.getDescription(context_)
                }
                if (volumeDir == file) {
                    return volumeName
                }
                return volumeName + file.absolutePath.substring(volumePath.length)
            }
        }
        return file.absolutePath
    }

    private fun pickAudioFile() {
        try {
            audioFilePicker.launch(Unit)
        } catch (e: ActivityNotFoundException) {
            toastManager.showToast(R.string.mscopes_toast_file_picker_not_found)
        }
    }

    private fun setAudioFile(uri: Uri) {
        try {
            context_.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }

        val storage = MicrophoneScopesStorage()
        storage.setAudioFilePath(uri.toString())
        storage.setResolvedFilePath(resolveFilePath(uri))

        GosPackageState.edit(pkgName, context_.user).run {
            setMicrophoneScopes(storage.serialize())
            applyOrPressBack()
        }
    }

    private fun resolveFilePath(uri: Uri): String? {
        try {
            context_.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                var realPath = Os.readlink("/proc/self/fd/${pfd.fd}")
                if (realPath.startsWith("/mnt/user/")) {
                    realPath = realPath.replaceFirst(
                        "/mnt/user/${UserHandle.myUserId()}/", "/storage/"
                    )
                }
                if (File(realPath).isFile) {
                    return realPath
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun clearAudioFile() {
        GosPackageState.edit(pkgName, context_.user).run {
            setMicrophoneScopes(null)
            applyOrPressBack()
        }
    }

    fun setMicrophoneScopesEnabled(enabled: Boolean) {
        if (enabled) {
            if (MicrophoneScopesUtils.revokeMicrophonePermissions(context_, pkgName)) {
                toastManager.showToast(R.string.mscopes_toast_microphone_permission_denied)
            }
        }

        GosPackageState.edit(pkgName, context_.user).run {
            setFlagState(MicrophoneScopesUtils.FLAG_MICROPHONE_SCOPES_ENABLED, enabled)
            setMicrophoneScopes(null)
            setKillUidAfterApply(!enabled)
            setNotifyUidAfterApply(true)
            applyOrPressBack()
        }
    }

    override fun getTitle() = getText(R.string.microphone_scopes)

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        if (mainSwitch.parent == null) {
            val item = addMenuItem(R.string.mscopes_turn_off, menu)
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.string.mscopes_turn_off -> {
                AlertDialog.Builder(context_).run {
                    setMessage(R.string.mscopes_turn_off_confirmation)
                    setPositiveButton(R.string.mscopes_turn_off) { _, _ ->
                        setMicrophoneScopesEnabled(false)
                        requireActivity().invalidateOptionsMenu()
                    }
                    show()
                }
            }
            else -> return false
        }
        return true
    }

    class AudioFilePickerContract : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == Activity.RESULT_OK) intent?.data else null
        }
    }
}
