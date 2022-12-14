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

package com.android.permission.testing

import android.os.Bundle
import com.google.common.base.Equivalence
import com.google.common.testing.EqualsTester
import com.google.common.testing.EquivalenceTester

/**
 * A class similar to [EqualsTester] that also checks that the [Object.hashCode] and
 * [Object.toString] implementations are consistent with equality groups.
 *
 * Note: this class assumes that [Object.hashCode] does not create a collision for equality groups,
 * however this can be disabled by setting [ignoreHashCode] to `true`.
 *
 * Note: this class assumes that [Object.toString] only represents the state that is used in
 * [Object.equals] and [Object.hashCode] implementation. Objects with [Bundle] fields may break it.
 * This can be disabled by setting [ignoreToString] to `true`.
 *
 * @param createCopy optionally provides a custom method to create the equal copy that will be
 *     applied to all the items in provided equality groups.
 */
class EqualsHashCodeToStringTester<T>(
    private val ignoreHashCode: Boolean = false,
    private val ignoreToString: Boolean = false,
    private var createCopy: ((T) -> T)? = null
) {

    private val equalityGroups = mutableListOf<List<T>>()

    fun addEqualityGroup(vararg equalItems: T): EqualsHashCodeToStringTester<T> {
        equalityGroups.add(equalItems.toList())
        return this
    }

    fun setCreateCopy(createCopy: (T) -> T): EqualsHashCodeToStringTester<T> {
        this.createCopy = createCopy
        return this
    }

    fun test() {
        val equalsTester = EqualsTester()
        val toStringTester =
                EquivalenceTester.of<T>(toStringEquivalence()).takeIf { !ignoreToString }
        val hashCodeTester =
                EquivalenceTester.of<T>(hashCodeEquivalence()).takeIf { !ignoreHashCode }

        for (equalityGroup in equalityGroups) {
            val equalItemsWithCopiesIfNeeded = equalityGroup.withCopiesIfNeeded()
            equalsTester.addEqualityGroup(*equalItemsWithCopiesIfNeeded.toArray())
            toStringTester?.addEquivalenceGroup(equalItemsWithCopiesIfNeeded)
            hashCodeTester?.addEquivalenceGroup(equalItemsWithCopiesIfNeeded)
        }

        equalsTester.testEquals()
        toStringTester?.test()
        hashCodeTester?.test()
    }

    private fun List<T>.toArray(): Array<*> = Array(size) { this[it] as Any }

    private fun List<T>.withCopiesIfNeeded(): List<T> =
        createCopy?.let { builder -> this + this.map(builder) } ?: this

    companion object {

        /**
         * An [Equivalence] that considers two instances of a class equivalent iff [Object.toString]
         * return the same value.
         */
        private fun <T> toStringEquivalence() =
            object : Equivalence<T>() {

                override fun doEquivalent(a: T, b: T): Boolean {
                    return a.toString() == b.toString()
                }

                override fun doHash(o: T): Int {
                    return o.toString().hashCode()
                }
            }

        /**
         * An [Equivalence] that considers two instances of a class equivalent iff [Object.hashCode]
         * return the same value.
         */
        private fun <T> hashCodeEquivalence() =
            object : Equivalence<T>() {

                override fun doEquivalent(a: T, b: T): Boolean {
                    return a.hashCode() == b.hashCode()
                }

                override fun doHash(o: T): Int {
                    return o.hashCode()
                }
            }
    }
}
