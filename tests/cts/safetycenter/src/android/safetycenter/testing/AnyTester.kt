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

package android.safetycenter.testing

import com.google.common.truth.Truth.assertThat

/** Collection of functions to test generic objects */
object AnyTester {
    /**
     * Asserts that two generic objects are equal and that the values returned by the [hashCode] and
     * [toString] methods for the two generic objects are also equal.
     */
    fun assertThatRepresentationsAreEqual(a: Any, b: Any) {
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isEqualTo(b)
        assertThat(a.toString()).isEqualTo(b.toString())
    }

    /**
     * Asserts that two generic objects are not equal and that the values returned by the [hashCode]
     * and [toString] methods for the two generic objects are also not equal.
     */
    fun assertThatRepresentationsAreNotEqual(a: Any, b: Any) {
        assertThat(a.hashCode()).isNotEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(b)
        assertThat(a.toString()).isNotEqualTo(b.toString())
    }
}