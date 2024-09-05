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

package com.android.permissioncontroller.permission.ui.wear.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.permissioncontroller.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WearEnhancedConfirmationViewModel : ViewModel() {
    enum class ScreenState {
        SHOW_RESTRICTION_DIALOG,
        SHOW_CONNECTION_IN_PROGRESS,
        SHOW_CONNECTION_ERROR,
        SHOW_CONNECTION_SUCCESS
    }

    private val mutableScreenState: MutableState<ScreenState> =
        mutableStateOf(ScreenState.SHOW_RESTRICTION_DIALOG)
    val screenState: State<ScreenState> = mutableScreenState

    private fun getLearnMoreUri(context: Context) =
        Uri.parse(context.getString(R.string.help_url_action_disabled_by_restricted_settings))

    fun openUriOnPhone(context: Context) =
        viewModelScope.launch {
            val uri = getLearnMoreUri(context)
            mutableScreenState.value = ScreenState.SHOW_CONNECTION_IN_PROGRESS
            val target =
                Intent(Intent.ACTION_VIEW).setData(uri).addCategory(Intent.CATEGORY_BROWSABLE)
            val isSuccessful =
                withContext(Dispatchers.IO) {
                    RemoteActivityHelper(context).startRemoteActivity(target)
                }
            mutableScreenState.value =
                if (isSuccessful) {
                    ScreenState.SHOW_CONNECTION_SUCCESS
                } else {
                    ScreenState.SHOW_CONNECTION_ERROR
                }
        }

    fun stripLearnMoreLinkFrom(message: CharSequence): CharSequence {
        val text = SpannableStringBuilder.valueOf(message)
        text.getSpans(0, message.length, URLSpan::class.java).map {
            val spanStart = text.getSpanStart(it)
            text.removeSpan(it)
            text.delete(spanStart, message.length)
        }
        return text
    }
}
