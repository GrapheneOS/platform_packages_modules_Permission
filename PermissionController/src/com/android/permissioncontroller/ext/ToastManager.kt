package com.android.permissioncontroller.ext

import android.widget.Toast
import androidx.fragment.app.Fragment

class ToastManager(val fragment: Fragment) {
    private var toast: Toast? = null

    fun showToast(text: Int) {
        showToast(fragment.getText(text))
    }

    fun showToast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        toast?.cancel()

        val toast = Toast.makeText(fragment.requireContext(), text, duration)
        toast.show()
        this.toast = toast
    }
}
