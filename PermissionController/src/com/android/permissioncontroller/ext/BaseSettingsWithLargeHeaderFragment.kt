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

        @Suppress("DEPRECATION") // see onOptionsItemSelected
        setHasOptionsMenu(true) // needed for the "back arrow" button even when there's no menu
        preferenceScreen = preferenceManager.createPreferenceScreen(context_)
    }

    abstract fun getTitle(): CharSequence

    // it's not clear how to resolve deprecation warnings for setHasOptionsMenu and onOptionsItemSelected,
    // they are suppressed in upstream fragments that use android.R.id.home too
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            this.pressBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private var callUpdateOnResume = false

    override fun onResume() {
        super.onResume()
        if (callUpdateOnResume) {
            update()
        } else {
            // update() has to be called by subclasses from onCreate() to avoid state loss after
            // configuration change. onCreate() is called right before the first onResume()
            callUpdateOnResume = true
        }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().title = getTitle()
    }

    abstract fun update()

    val toastManager = ToastManager(this)
}
