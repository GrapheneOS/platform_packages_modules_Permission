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

package com.android.safetycenter.config;

import android.annotation.IdRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;

final class BuilderUtils {
    private BuilderUtils() {
    }

    static void validateAttribute(@Nullable Object attribute, @NonNull String name,
            boolean required, boolean prohibited) {
        if (attribute == null && required) {
            throw new IllegalStateException(String.format("Required attribute %s missing", name));
        }
        if (attribute != null && prohibited) {
            throw new IllegalStateException(String.format("Prohibited attribute %s present", name));
        }
    }

    @IdRes
    static int validateResId(@Nullable @IdRes Integer value, @NonNull String name, boolean required,
            boolean prohibited) {
        validateAttribute(value, name, required, prohibited);
        if (value == null) {
            return Resources.ID_NULL;
        }
        return value;
    }

    static int validateIntDef(@Nullable Integer value, @NonNull String name,
            boolean required, boolean prohibited, int defaultValue, int... validValues) {
        validateAttribute(value, name, required, prohibited);
        if (value == null) {
            return defaultValue;
        }

        boolean found = false;
        for (int i = 0; i < validValues.length; i++) {
            found |= (value == validValues[i]);
        }
        if (!found) {
            throw new IllegalStateException(String.format("Attribute %s invalid", name));
        }
        return value;
    }

    static int validateInteger(@Nullable Integer value, @NonNull String name,
            boolean required, boolean prohibited, int defaultValue) {
        validateAttribute(value, name, required, prohibited);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    static boolean validateBoolean(@Nullable Boolean value, @NonNull String name,
            boolean required, boolean prohibited, boolean defaultValue) {
        validateAttribute(value, name, required, prohibited);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

}
