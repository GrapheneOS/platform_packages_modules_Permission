package com.android.permissioncontroller.ext

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader
import com.android.permissioncontroller.permission.ui.handheld.pressBack

abstract class BaseSettingsWithLargeHeaderFragment : SettingsWithLargeHeader() {
    val context_: Context
        get() = requireContext()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true) // needed for the "back arrow" button even when there's no menu
        preferenceScreen = preferenceManager.createPreferenceScreen(context)
    }

    abstract fun getTitle(): CharSequence

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            this.pressBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    override fun onStart() {
        super.onStart()
        requireActivity().title = getTitle()
    }

    abstract fun update()

    val toastManager = ToastManager(this)
}
