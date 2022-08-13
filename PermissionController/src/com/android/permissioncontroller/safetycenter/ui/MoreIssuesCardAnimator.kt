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

package com.android.permissioncontroller.safetycenter.ui

import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.safetycenter.SafetyCenterIssue
import android.transition.AutoTransition
import android.transition.Transition
import android.transition.TransitionListenerAdapter
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.android.permissioncontroller.R
import java.time.Duration

class MoreIssuesCardAnimator {
    fun animateStatusIconsChange(
        statusIcon: ImageView,
        startSeverityLevel: Int,
        endSeverityLevel: Int,
        @DrawableRes endSeverityLevelResId: Int
    ) {
        val severityLevelAnimationResId =
            selectIconAnimationResId(startSeverityLevel, endSeverityLevel)
        statusIcon.setImageResource(severityLevelAnimationResId)
        val setStatusIconDrawable = statusIcon.drawable
        if (setStatusIconDrawable is AnimatedVectorDrawable) {
            setStatusIconDrawable.registerAnimationCallback(
                object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable) {
                        super.onAnimationEnd(drawable)
                        statusIcon.setImageResource(endSeverityLevelResId)
                    }
                })
            setStatusIconDrawable.start()
        }
    }

    fun cancelStatusAnimation(statusIcon: ImageView) {
        val statusDrawable: Drawable? = statusIcon.drawable
        if (statusDrawable != null &&
            statusDrawable is AnimatedVectorDrawable &&
            statusDrawable.isRunning) {
            statusDrawable.clearAnimationCallbacks()
            statusDrawable.stop()
        }
    }

    fun animateChangeText(textView: TextView, text: String) {
        val fadeOutTransition =
            textChangeTransition
                .clone()
                .addListener(
                    object : TransitionListenerAdapter() {
                        override fun onTransitionEnd(transition: Transition?) {
                            super.onTransitionEnd(transition)
                            fadeTextIn(textView, text)
                        }
                    })
        textView.post {
            TransitionManager.beginDelayedTransition(
                textView.parent as ViewGroup, fadeOutTransition)
            textView.visibility = View.INVISIBLE
        }
    }

    fun fadeTextIn(textView: TextView, text: String) {
        val fadeInTransition = textChangeTransition.clone()
        textView.post {
            TransitionManager.beginDelayedTransition(textView.parent as ViewGroup, fadeInTransition)
            textView.text = text
            textView.visibility = View.VISIBLE
        }
    }

    fun cancelTextChangeAnimation(textView: TextView) {
        TransitionManager.endTransitions(textView.parent as ViewGroup)
    }

    @DrawableRes
    private fun selectIconAnimationResId(startSeverityLevel: Int, endSeverityLevel: Int): Int {
        return when (endSeverityLevel) {
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK ->
                when (startSeverityLevel) {
                    SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK ->
                        R.drawable.safety_status_small_info_to_info_anim
                    SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION ->
                        R.drawable.safety_status_small_recommendation_to_info_anim
                    SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING ->
                        R.drawable.safety_status_small_warn_to_info_anim
                    else -> R.drawable.ic_safety_info
                }
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION ->
                when (startSeverityLevel) {
                    SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK ->
                        R.drawable.safety_status_small_info_to_recommendation_anim
                    SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION ->
                        R.drawable.safety_status_small_recommendation_to_recommendation_anim
                    SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING ->
                        R.drawable.safety_status_small_warn_to_recommendation_anim
                    else -> R.drawable.ic_safety_recommendation
                }
            SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING ->
                when (startSeverityLevel) {
                    SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK ->
                        R.drawable.safety_status_small_info_to_warn_anim
                    SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION ->
                        R.drawable.safety_status_small_recommendation_to_warn_anim
                    SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING ->
                        R.drawable.safety_status_small_warn_to_warn_anim
                    else -> R.drawable.ic_safety_warn
                }
            else -> {
                Log.e(
                    MoreIssuesCardPreference.TAG,
                    String.format(
                        "Unexpected SafetyCenterIssue.IssueSeverityLevel: %d", endSeverityLevel))
                R.drawable.ic_safety_null_state
            }
        }
    }

    companion object {
        // Duration is for fade-out & fade-in individually, not combined
        private val TEXT_CHANGE_DURATION = Duration.ofMillis(167)
        private val linearInterpolator = LinearInterpolator()

        private val textChangeTransition =
            AutoTransition()
                .setInterpolator(linearInterpolator)
                .setDuration(TEXT_CHANGE_DURATION.toMillis())
                .addTarget(R.id.widget_title)
    }
}
