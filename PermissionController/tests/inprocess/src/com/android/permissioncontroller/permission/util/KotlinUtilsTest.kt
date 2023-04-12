/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

/**
 * Unit tests for [KotlinUtils].
 */
@RunWith(AndroidJUnit4::class)
class KotlinUtilsTest {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun convertToBitmap_argb888BitmapDrawable_returnsSameBitmap() {
        val bitmap = Bitmap.createBitmap(/* width= */ 5, /* height= */ 10, Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(targetContext.resources, bitmap)

        assertThat(KotlinUtils.convertToBitmap(drawable).sameAs(bitmap)).isTrue()
    }

    @Test
    fun convertToBitmap_noIntrinsicSize_throws() {
        val drawable = FakeDrawable(intrinsicSize = 0)
        assertFailsWith<IllegalArgumentException> { KotlinUtils.convertToBitmap(drawable) }
    }

    class FakeDrawable(private val intrinsicSize: Int) : Drawable() {
        override fun getIntrinsicWidth() = intrinsicSize
        override fun getIntrinsicHeight() = intrinsicSize

        override fun draw(canvas: Canvas) = Unit // no-op
        override fun getOpacity() = throw UnsupportedOperationException()
        override fun setAlpha(alpha: Int) = throw UnsupportedOperationException()
        override fun setColorFilter(colorFilter: ColorFilter?) =
            throw UnsupportedOperationException()
    }
}