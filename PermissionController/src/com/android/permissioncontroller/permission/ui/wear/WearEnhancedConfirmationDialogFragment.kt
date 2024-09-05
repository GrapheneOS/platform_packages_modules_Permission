/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.permission.ui.wear

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.android.permissioncontroller.ecm.EnhancedConfirmationDialogActivity
import com.android.permissioncontroller.permission.ui.wear.model.WearEnhancedConfirmationViewModel
import com.android.permissioncontroller.permission.ui.wear.theme.WearPermissionTheme

class WearEnhancedConfirmationDialogFragment : DialogFragment() {
    private lateinit var enhancedConfirmationDialogActivity: EnhancedConfirmationDialogActivity
    private val viewModel: WearEnhancedConfirmationViewModel by viewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        enhancedConfirmationDialogActivity = context as EnhancedConfirmationDialogActivity
        enhancedConfirmationDialogActivity.window.decorView.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val title =
            arguments?.getString(KEY_TITLE) ?: throw RuntimeException("ECM Title can't be null")
        val message =
            arguments?.getCharSequence(KEY_MESSAGE)?.apply {
                viewModel.stripLearnMoreLinkFrom(this)
            } ?: throw RuntimeException("ECM Message can't be null")

        return ComposeView(enhancedConfirmationDialogActivity).apply {
            setContent {
                WearPermissionTheme { WearEnhancedConfirmationScreen(viewModel, title, message) }
            }
        }
    }

    companion object {
        const val TAG = "WearEnhancedConfirmationDialogFragment"
        private const val KEY_TITLE = "KEY_TITLE"
        private const val KEY_MESSAGE = "KEY_MESSAGE"

        fun newInstance(title: String? = null, message: CharSequence? = null) =
            WearEnhancedConfirmationDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(KEY_TITLE, title)
                        putCharSequence(KEY_MESSAGE, message)
                    }
            }
    }
}
