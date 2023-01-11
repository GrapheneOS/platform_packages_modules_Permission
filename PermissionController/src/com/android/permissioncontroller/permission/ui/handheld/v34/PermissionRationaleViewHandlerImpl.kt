/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.permission.ui.handheld.v34

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.v34.PermissionRationaleViewHandler
import com.android.permissioncontroller.permission.ui.v34.PermissionRationaleViewHandler.Result.Companion.CANCELLED

/**
 * Handheld implementation of [PermissionRationaleViewHandler]. Used for managing the presentation
 * and user interaction of the "permission rationale" user interface.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PermissionRationaleViewHandlerImpl(
    private val mActivity: Activity,
    private val resultListener: PermissionRationaleViewHandler.ResultListener
) : PermissionRationaleViewHandler, OnClickListener {

    private var groupName: String? = null
    private var purposeMessage: CharSequence? = null
    private var settingsMessage: CharSequence? = null
    private var learnMoreMessage: CharSequence? = null

    private var rootView: ViewGroup? = null
    private var purposeMessageView: TextView? = null
    private var settingsMessageView: TextView? = null
    private var learnMoreMessageView: TextView? = null
    private var backButton: Button? = null

    override fun saveInstanceState(outState: Bundle) {
        outState.putString(ARG_GROUP_NAME, groupName)
        outState.putCharSequence(ARG_PURPOSE_MESSAGE, purposeMessage)
        outState.putCharSequence(ARG_SETTINGS_MESSAGE, settingsMessage)
        outState.putCharSequence(ARG_LEARN_MORE_MESSAGE, learnMoreMessage)
    }

    override fun loadInstanceState(savedInstanceState: Bundle) {
        groupName = savedInstanceState.getString(ARG_GROUP_NAME)
        purposeMessage = savedInstanceState.getCharSequence(ARG_PURPOSE_MESSAGE)
        settingsMessage = savedInstanceState.getCharSequence(ARG_SETTINGS_MESSAGE)
        learnMoreMessage = savedInstanceState.getCharSequence(ARG_LEARN_MORE_MESSAGE)
    }

    override fun updateUi(
        groupName: String,
        purposeMessage: CharSequence,
        settingsMessage: CharSequence,
        learnMoreMessage: CharSequence
    ) {
        this.groupName = groupName
        this.purposeMessage = purposeMessage
        this.settingsMessage = settingsMessage
        this.learnMoreMessage = learnMoreMessage

        // If view already created, update all children
        if (rootView != null) {
            updateAll()
        }
    }

    private fun updateAll() {
        updatePurposeMessage()
        updateSettingsMessage()
        updateLearnMoreMessage()

        // Animate change in size
        // Grow or shrink the content container to size of new content
        val growShrinkToNewContentSize = ChangeBounds()
        growShrinkToNewContentSize.duration = ANIMATION_DURATION_MILLIS
        growShrinkToNewContentSize.interpolator = AnimationUtils.loadInterpolator(mActivity,
            android.R.interpolator.fast_out_slow_in)
        TransitionManager.beginDelayedTransition(rootView, growShrinkToNewContentSize)
    }

    override fun createView(): View {
        // Make this activity be Non-IME target to prevent hiding keyboard flicker when it show up.
        mActivity.window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)

        val rootView = LayoutInflater.from(mActivity)
            .inflate(R.layout.permission_rationale, null) as ViewGroup

        // Uses the vertical gravity of the PermissionGrantSingleton style to position the window
        val gravity = rootView.requireViewById<LinearLayout>(R.id.grant_singleton).gravity
        val verticalGravity = Gravity.VERTICAL_GRAVITY_MASK and gravity
        mActivity.window.setGravity(Gravity.CENTER_HORIZONTAL or verticalGravity)

        // Cancel dialog
        rootView.findViewById<View>(R.id.grant_singleton)!!.setOnClickListener(this)
        // Swallow click event
        rootView.findViewById<View>(R.id.grant_dialog)!!.setOnClickListener(this)

        purposeMessageView = rootView.findViewById(R.id.purpose_message)
        purposeMessageView!!.movementMethod = LinkMovementMethod.getInstance()

        settingsMessageView = rootView.findViewById(R.id.settings_message)
        settingsMessageView!!.movementMethod = LinkMovementMethod.getInstance()

        learnMoreMessageView = rootView.findViewById(R.id.learn_more_message)
        learnMoreMessageView!!.movementMethod = LinkMovementMethod.getInstance()

        backButton = rootView.findViewById<Button>(R.id.back_button)!!.apply {
            setOnClickListener(this@PermissionRationaleViewHandlerImpl)

            // Load the text color from the activity theme rather than the Material Design theme
            val textColor = getColorStateListForAttr(mActivity, android.R.attr.textColorPrimary)!!
            setTextColor(textColor)
        }

        this.rootView = rootView

        // If ui model present, update all children
        if (groupName != null) {
            updateAll()
        }

        return rootView
    }

    override fun onClick(view: View) {
        val id = view.id

        if (id == R.id.grant_singleton) {
            onCancelled()
            return
        }

        if (id == R.id.back_button) {
            onCancelled()
        }
    }

    override fun onBackPressed() {
        onCancelled()
    }

    override fun onCancelled() {
        resultListener.onPermissionRationaleResult(groupName, CANCELLED)
    }

    private fun updatePurposeMessage() {
        if (purposeMessage == null) {
            purposeMessageView!!.visibility = View.GONE
        } else {
            purposeMessageView!!.text = purposeMessage
            purposeMessageView!!.visibility = View.VISIBLE
        }
    }

    private fun updateSettingsMessage() {
        if (settingsMessage == null) {
            settingsMessageView!!.visibility = View.GONE
        } else {
            settingsMessageView!!.text = settingsMessage
            settingsMessageView!!.visibility = View.VISIBLE
        }
    }

    private fun updateLearnMoreMessage() {
        if (learnMoreMessage == null) {
            learnMoreMessageView!!.visibility = View.GONE
        } else {
            learnMoreMessageView!!.text = learnMoreMessage
            learnMoreMessageView!!.visibility = View.VISIBLE
        }
    }

    companion object {
        private val TAG = PermissionRationaleViewHandlerImpl::class.java.simpleName

        const val ARG_GROUP_NAME = "ARG_GROUP_NAME"
        const val ARG_PURPOSE_MESSAGE = "ARG_PURPOSE_MESSAGE"
        const val ARG_SETTINGS_MESSAGE = "ARG_SETTINGS_MESSAGE"
        const val ARG_LEARN_MORE_MESSAGE = "ARG_LEARN_MORE_MESSAGE"

        // Animation parameters.
        private const val ANIMATION_DURATION_MILLIS: Long = 200

        fun getColorStateListForAttr(context: Context, attr: Int): ColorStateList? {
            val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
            val colorStateList = typedArray.getColorStateList(0)
            typedArray.recycle()
            return colorStateList
        }
    }
}
