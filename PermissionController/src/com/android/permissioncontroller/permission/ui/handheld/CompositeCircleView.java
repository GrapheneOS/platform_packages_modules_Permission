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
 * {@link #configure(float, int[], int[], int)}.
 */
public class CompositeCircleView extends FrameLayout {

    /** Spacing between circle segments in degrees. */
    private static final int SEGMENT_ANGLE_SPACING_DEG = 2;

    /** How far apart to bump labels so that they have more space. */
    private static final float LABEL_BUMP_DEGREES = 10;

    /** Values being represented by this circle. */
    private int[] mValues;

    /**
     * Angles toward the middle of each colored partial circle, calculated in
     * {@link #configure(float, int[], int[], int)}. Can be used to position text relative to the
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
     * @param startAngle the angle at which to start segments
     * @param values relative weights, used to size the partial circles
     * @param colors colors corresponding to relative weights
     * @param strokeWidth stroke width to apply to all contained partial circles
     */
    public void configure(float startAngle, int[] values, int[] colors, int strokeWidth) {
        removeAllViews();
        mValues = values;

        // Get total values and number of values over 0.
        float total = 0;
        int numValidValues = 0;
        for (int i = 0; i < values.length; i++) {
            total += values[i];
            if (values[i] > 0) {
                numValidValues++;
            }
        }

        // Add small spacing to the first angle to make the little space between segments.
        startAngle = startAngle + (SEGMENT_ANGLE_SPACING_DEG * 0.5f);
        mPartialCircleCenterAngles = new float[values.length];

        // Number of degrees allocated to drawing circle segments.
        float allocatedDegrees = 360 - (numValidValues * SEGMENT_ANGLE_SPACING_DEG);

        // Number of consecutive times we've bumped the next label further to make space.
        int labelBumps = 0;

        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) {
                continue;
            }

            PartialCircleView pcv = new PartialCircleView(getContext());
            addView(pcv);
            pcv.setStartAngle(startAngle);
            pcv.setColor(colors[i]);
            pcv.setStrokeWidth(strokeWidth);

            // Calculate sweep, which is (value / total) * 360, keep track of segment center
            // angles for later reference.
            float sweepAngle = (values[i] / total) * allocatedDegrees;
            pcv.setSweepAngle(sweepAngle);

            // If the sweep angle is big, don't bump this label out, spread previous label
            // bumps by moving all bumped items back.
            if (sweepAngle > ((labelBumps * LABEL_BUMP_DEGREES) * 2)) {
                spreadPreviousLabelBumps(labelBumps, i);
                labelBumps = 0;
            }

            mPartialCircleCenterAngles[i] =
                    (startAngle + (sweepAngle * 0.5f) + (labelBumps * LABEL_BUMP_DEGREES)) % 360;

            // If the sweep angle is tiny, we have to bump the next label out a bit.
            if (sweepAngle < LABEL_BUMP_DEGREES) {
                labelBumps++;
            }

            // Move to next segment.
            startAngle += sweepAngle;
            startAngle += SEGMENT_ANGLE_SPACING_DEG;
            startAngle %= 360;
        }

        // If any label bumps remaining, spread now.
        spreadPreviousLabelBumps(labelBumps, values.length);
    }

    /**
     * If we've been bumping labels further from previous labels to make space, we use this method
     * to spread the bumps back along the circle, so that labels are as close as possible to their
     * corresponding segments.
     *
     * @param labelBumps total number of previous segments under the size threshold
     * @param behindIndex the index behind which we were bumping labels
     */
    private void spreadPreviousLabelBumps(int labelBumps, int behindIndex) {
        if (labelBumps > 0) {
            float spread = ((labelBumps - 1) * LABEL_BUMP_DEGREES) * 0.5f;
            for (int i = 1; i <= labelBumps; i++) {
                int index = behindIndex - i;
                float angle = mPartialCircleCenterAngles[index];
                angle -= spread;
                angle += 360;
                angle %= 360;
                mPartialCircleCenterAngles[index] = angle;
            }
        }
    }

    /** Returns the value for the given index. */
    public int getValue(int index) {
        return mValues[index];
    }

    /** Returns the center angle for the given partial circle index. */
    public float getPartialCircleCenterAngle(int index) {
        return mPartialCircleCenterAngles[index];
    }
}
