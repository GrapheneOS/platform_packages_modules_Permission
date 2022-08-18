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

package com.android.permission.access.util

import android.util.SparseArray

typealias IntMap<T> = SparseArray<T>

inline fun <T> IntMap<T>.allIndexed(predicate: (Int, Int, T) -> Boolean): Boolean {
    for (index in 0 until size) {
        if (!predicate(index, keyAt(index), valueAt(index))) {
            return false
        }
    }
    return true
}

inline fun <T> IntMap<T>.anyIndexed(predicate: (Int, Int, T) -> Boolean): Boolean {
    for (index in 0 until size) {
        if (predicate(index, keyAt(index), valueAt(index))) {
            return true
        }
    }
    return false
}

inline fun <T> IntMap<T>.copy(copyValue: (T) -> T): IntMap<T> =
    this.clone().apply {
        forEachValueIndexed { index, value ->
            setValueAt(index, copyValue(value))
        }
    }

inline fun <T> IntMap<T>.forEachIndexed(action: (Int, Int, T) -> Unit) {
    for (index in 0 until size) {
        action(index, keyAt(index), valueAt(index))
    }
}

inline fun <T> IntMap<T>.forEachKeyIndexed(action: (Int, Int) -> Unit) {
    for (index in 0 until size) {
        action(index, keyAt(index))
    }
}

inline fun <T> IntMap<T>.forEachValueIndexed(action: (Int, T) -> Unit) {
    for (index in 0 until size) {
        action(index, valueAt(index))
    }
}

inline fun <T> IntMap<T>.getOrPut(key: Int, defaultValue: () -> T): T {
    get(key)?.let { return it }
    return defaultValue().also { put(key, it) }
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> IntMap<T>.minusAssign(key: Int) {
    remove(key)
}

inline val <T> IntMap<T>.size: Int
    get() = size()
