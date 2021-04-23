/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Configured to draw a set of contiguous partial circles via {@link PartialCircleView}, which
 * are generated from the relative weight of values and corresponding colors given to
 * {@link #configure(float[], int[], int)}.
 */
public class CompositeCircleView extends FrameLayout {

    /**
     * Angles toward the middle of each colored partial circle, calculated in
     * {@link #configure(float[], int[], int)}. Can be used to position text relative to the
     * partial circles, by index.
     */
    private float[] mPartialCircleCenterAngles;

    public CompositeCircleView(@NonNull Context context) {
        super(context);
    }

    public CompositeCircleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CompositeCircleView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CompositeCircleView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Configures the {@link CompositeCircleView} to draw a set of contiguous partial circles that
     * are generated from the relative weight of the given values and corresponding colors. The
     * first segment starts at the top, and drawing proceeds clockwise from there.
     *
     * @param values relative weights, used to size the partial circles
     * @param colors colors corresponding to relative weights
     * @param strokeWidth stroke width to apply to all contained partial circles
     */
    public void configure(float[] values, int[] colors, int strokeWidth) {
        removeAllViews();

        float total = 0;
        for (int i = 0; i < values.length; i++) {
            total += values[i];
        }

        // Start from vertical top, which is angle = 270.
        float startAngle = 270;
        mPartialCircleCenterAngles = new float[values.length];

        for (int i = 0; i < values.length; i++) {
            PartialCircleView pcv = new PartialCircleView(getContext());
            addView(pcv);
            pcv.setStartAngle(startAngle);
            pcv.setColor(colors[i]);
            pcv.setStrokeWidth(strokeWidth);

            // Calculate sweep, which is (value / total) * 360, keep track of segment center
            // angles for later reference.
            float sweepAngle = (values[i] / total) * 360;
            pcv.setSweepAngle(sweepAngle);
            mPartialCircleCenterAngles[i] = (startAngle + (sweepAngle * 0.5f)) % 360;

            // Move to next segment.
            startAngle += sweepAngle;
            startAngle %= 360;
        }
    }

    /** Returns the center angle for the given partial circle index. */
    public float getPartialCircleCenterAngle(int index) {
        return mPartialCircleCenterAngles[index];
    }
}
