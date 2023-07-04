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

package com.android.safetycenter.annotations;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.annotation.meta.TypeQualifierDefault;

/**
 * Specifies that all types are {@code RequiresApi(TIRAMISU)} within the annotated package, unless
 * tagged another {@code @RequiresApi} annotation.
 */
@Retention(CLASS)
@Target(PACKAGE)
@TypeQualifierDefault({TYPE})
@RequiresApi(TIRAMISU)
public @interface RequiresTiramisuByDefault {}
