/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld

import android.app.Activity
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.UserHandle
import android.text.method.LinkMovementMethod
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ALWAYS_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_FOREGROUND_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ONE_TIME_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_AND_DONT_ASK_AGAIN_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NEXT_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_BUTTON
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.CANCELED
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ONE_TIME

class GrantPermissionsViewHandlerImpl(
    private val mActivity: Activity,
    mAppPackageName: String,
    mUserHandle: UserHandle
) : GrantPermissionsViewHandler, OnClickListener {

    private var resultListener: GrantPermissionsViewHandler.ResultListener? = null

    // Configuration of the current dialog
    private var groupName: String? = null
    private var groupCount: Int = 0
    private var groupIndex: Int = 0
    private var groupIcon: Icon? = null
    private var groupMessage: CharSequence? = null
    private var detailMessage: CharSequence? = null
    private val buttonVisibilities = BooleanArray(NEXT_BUTTON) { false }

    // Views
    private var iconView: ImageView? = null
    private var messageView: TextView? = null
    private var detailMessageView: TextView? = null
    private var buttons: Array<Button?> = arrayOfNulls(NEXT_BUTTON)
    private var rootView: ViewGroup? = null

    override fun setResultListener(
        listener: GrantPermissionsViewHandler.ResultListener
    ): GrantPermissionsViewHandlerImpl {
        resultListener = listener
        return this
    }

    override fun saveInstanceState(arguments: Bundle) {
        arguments.putString(ARG_GROUP_NAME, groupName)
        arguments.putInt(ARG_GROUP_COUNT, groupCount)
        arguments.putInt(ARG_GROUP_INDEX, groupIndex)
        arguments.putParcelable(ARG_GROUP_ICON, groupIcon)
        arguments.putCharSequence(ARG_GROUP_MESSAGE, groupMessage)
        arguments.putCharSequence(ARG_GROUP_DETAIL_MESSAGE, detailMessage)
        arguments.putBooleanArray(ARG_DIALOG_BUTTON_VISIBILITIES, buttonVisibilities)
    }

    override fun loadInstanceState(savedInstanceState: Bundle) {
        groupName = savedInstanceState.getString(ARG_GROUP_NAME)
        groupMessage = savedInstanceState.getCharSequence(ARG_GROUP_MESSAGE)
        groupIcon = savedInstanceState.getParcelable(ARG_GROUP_ICON)
        groupCount = savedInstanceState.getInt(ARG_GROUP_COUNT)
        groupIndex = savedInstanceState.getInt(ARG_GROUP_INDEX)
        detailMessage = savedInstanceState.getCharSequence(ARG_GROUP_DETAIL_MESSAGE)
        setButtonVisibilities(savedInstanceState.getBooleanArray(ARG_DIALOG_BUTTON_VISIBILITIES))

        updateAll()
    }

    override fun updateUi(
        groupName: String,
        groupCount: Int,
        groupIndex: Int,
        icon: Icon,
        message: CharSequence,
        detailMessage: CharSequence?,
        visibilities: BooleanArray
    ) {

        this.groupName = groupName
        this.groupCount = groupCount
        this.groupIndex = groupIndex
        groupIcon = icon
        groupMessage = message
        this.detailMessage = detailMessage
        setButtonVisibilities(visibilities)

        // If this is a second (or later) permission and the views exist, then animate.
        if (iconView != null) {
            updateAll()
        }
    }

    private fun updateAll() {
        updateDescription()
        updateDetailDescription()
        updateButtons()

        //      Animate change in size
        //      Grow or shrink the content container to size of new content
        val growShrinkToNewContentSize = ChangeBounds()
        growShrinkToNewContentSize.duration = ANIMATION_DURATION_MILLIS
        growShrinkToNewContentSize.interpolator = AnimationUtils.loadInterpolator(mActivity,
            android.R.interpolator.fast_out_slow_in)
        TransitionManager.beginDelayedTransition(rootView, growShrinkToNewContentSize)
    }

    override fun createView(): View {
        // Make this activity be Non-IME target to prevent hiding keyboard flicker when it show up.
        mActivity.window.addFlags(LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        val rootView = LayoutInflater.from(mActivity)
            .inflate(R.layout.grant_permissions, null) as ViewGroup
        this.rootView = rootView

        val h = mActivity.resources.displayMetrics.heightPixels
        rootView.minimumHeight = h
        // Cancel dialog
        rootView.findViewById<View>(R.id.grant_singleton)!!.setOnClickListener(this)
        // Swallow click event
        rootView.findViewById<View>(R.id.grant_dialog)!!.setOnClickListener(this)

        messageView = rootView.findViewById(R.id.permission_message)
        detailMessageView = rootView.findViewById(R.id.detail_message)
        detailMessageView!!.movementMethod = LinkMovementMethod.getInstance()
        iconView = rootView.findViewById(R.id.permission_icon)

        val buttons = arrayOfNulls<Button>(NEXT_BUTTON)

        val numButtons = BUTTON_RES_ID_TO_NUM.size()
        for (i in 0 until numButtons) {
            val button = rootView.findViewById<Button>(BUTTON_RES_ID_TO_NUM.keyAt(i))
            button!!.setOnClickListener(this)
            buttons[BUTTON_RES_ID_TO_NUM.valueAt(i)] = button
        }

        this.buttons = buttons
        if (groupName != null) {
            updateAll()
        }

        return rootView
    }

    override fun updateWindowAttributes(outLayoutParams: LayoutParams) {
        // No-op
    }

    private fun setButtonVisibilities(visibilities: BooleanArray?) {
        for (i in buttonVisibilities.indices) {
            buttonVisibilities[i] = if (visibilities != null && i < visibilities.size) {
                visibilities[i]
            } else {
                false
            }
        }
    }

    private fun updateDescription() {
        if (groupIcon != null) {
            iconView!!.setImageDrawable(groupIcon!!.loadDrawable(mActivity))
        }
        messageView!!.text = groupMessage
    }

    private fun updateDetailDescription() {
        if (detailMessage == null) {
            detailMessageView!!.visibility = View.GONE
        } else {
            detailMessageView!!.text = detailMessage
            detailMessageView!!.visibility = View.VISIBLE
        }
    }

    private fun updateButtons() {
        for (i in 0 until BUTTON_RES_ID_TO_NUM.size()) {
            val pos = BUTTON_RES_ID_TO_NUM.valueAt(i)
            buttons[pos]?.visibility = if (buttonVisibilities[pos]) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.grant_singleton) {
            if (resultListener != null) {
                resultListener!!.onPermissionGrantResult(groupName, CANCELED)
            } else {
                mActivity.finish()
            }
            return
        }

        when (BUTTON_RES_ID_TO_NUM.get(id, -1)) {
            ALLOW_BUTTON -> if (resultListener != null) {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
                resultListener!!.onPermissionGrantResult(groupName, GRANTED_ALWAYS)
            }
            ALLOW_FOREGROUND_BUTTON -> if (resultListener != null) {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
                resultListener!!.onPermissionGrantResult(groupName, GRANTED_FOREGROUND_ONLY)
            }
            ALLOW_ALWAYS_BUTTON -> if (resultListener != null) {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
                resultListener!!.onPermissionGrantResult(groupName, GRANTED_ALWAYS)
            }
            ALLOW_ONE_TIME_BUTTON -> if (resultListener != null) {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
                resultListener!!.onPermissionGrantResult(groupName, GRANTED_ONE_TIME)
            }
            DENY_BUTTON, NO_UPGRADE_BUTTON, NO_UPGRADE_OT_BUTTON -> if (resultListener != null) {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
                resultListener!!.onPermissionGrantResult(groupName, DENIED)
            }
            DENY_AND_DONT_ASK_AGAIN_BUTTON, NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON,
            NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON -> if (resultListener != null) {
                view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
                resultListener!!.onPermissionGrantResult(groupName, DENIED_DO_NOT_ASK_AGAIN)
            }
        }
    }

    override fun onBackPressed() {
        if (resultListener == null) {
            mActivity.finish()
            return
        }
        resultListener?.onPermissionGrantResult(groupName, CANCELED)
    }

    companion object {

        const val ARG_GROUP_NAME = "ARG_GROUP_NAME"
        const val ARG_GROUP_COUNT = "ARG_GROUP_COUNT"
        const val ARG_GROUP_INDEX = "ARG_GROUP_INDEX"
        const val ARG_GROUP_ICON = "ARG_GROUP_ICON"
        const val ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE"
        private const val ARG_GROUP_DETAIL_MESSAGE = "ARG_GROUP_DETAIL_MESSAGE"
        private const val ARG_DIALOG_BUTTON_VISIBILITIES = "ARG_DIALOG_BUTTON_VISIBILITIES"

        // Animation parameters.
        private const val SWITCH_TIME_MILLIS: Long = 75
        private const val ANIMATION_DURATION_MILLIS: Long = 200

        private val BUTTON_RES_ID_TO_NUM = SparseIntArray()

        init {
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_button, ALLOW_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_foreground_only_button,
                ALLOW_FOREGROUND_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_deny_button, DENY_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_deny_and_dont_ask_again_button,
                DENY_AND_DONT_ASK_AGAIN_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_one_time_button,
                ALLOW_ONE_TIME_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_button,
                NO_UPGRADE_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_and_dont_ask_again_button,
                NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_one_time_button,
                NO_UPGRADE_OT_BUTTON)
            BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_one_time_and_dont_ask_again_button,
                NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON)
        }
    }
}
