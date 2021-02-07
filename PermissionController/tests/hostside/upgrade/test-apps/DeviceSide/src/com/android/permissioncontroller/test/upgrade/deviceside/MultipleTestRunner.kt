/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.test.upgrade.deviceside

import java.io.PrintWriter
import java.io.StringWriter

class MultipleTestRunner {

    private val errors: MutableList<Pair<String, Throwable>> = ArrayList()

    fun runTest(tag: String = "", r: () -> Unit) {
        try {
            r.invoke()
        } catch (t: Throwable) {
            errors.add(tag to t)
        }
    }

    fun finish() {
        if (errors.isEmpty()) {
            return
        }
        val builder = StringBuilder("There were ${errors.size} failures.\n")
        for (i in errors.indices) {
            val stringWriter = StringWriter()
            errors[i].second.printStackTrace(PrintWriter(stringWriter))
            with(builder) {
                append(i + 1)
                append(". ")
                append(errors[i].first)
                append("\n")
                append(errors[i].second.javaClass.name)
                append("\n")
                append(stringWriter.toString())
                append("\n\n")
            }
        }
        errors.clear()
        throw AssertionError(builder.toString())
    }
}