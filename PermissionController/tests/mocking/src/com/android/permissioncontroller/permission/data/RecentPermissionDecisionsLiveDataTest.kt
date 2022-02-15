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

package com.android.permissioncontroller.permission.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permissioncontroller.permission.service.RecentPermissionDecisionsStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class RecentPermissionDecisionsLiveDataTest {

    @Mock
    lateinit var job: Job

    @Mock
    lateinit var recentDecision: PermissionDecision

    private val recentPermissionDecisionStorage = FakeRecentDecisionsStorage()

    private lateinit var recentPermissionDecisionsLiveData: RecentPermissionDecisionsLiveData

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        runBlocking {
            recentPermissionDecisionStorage.storePermissionDecision(recentDecision)
        }
        recentPermissionDecisionsLiveData = spy(RecentPermissionDecisionsLiveData(
            recentPermissionDecisionStorage))
    }

    @Test
    fun loadDataAndPostValue_jobCanceled_doesNothing() {
        `when`(job.isCancelled).thenReturn(true)

        runBlocking {
            recentPermissionDecisionsLiveData.loadDataAndPostValue(job)
            verify(recentPermissionDecisionsLiveData, Mockito.never()).postValue(Mockito.any())
        }
    }

    @Test
    fun loadDataAndPostValue_postsData() {
        `when`(job.isCancelled).thenReturn(false)

        runBlocking {
            recentPermissionDecisionsLiveData.loadDataAndPostValue(job)
            verify(recentPermissionDecisionsLiveData).postValue(eq(listOf(recentDecision)))
        }
    }

    private class FakeRecentDecisionsStorage : RecentPermissionDecisionsStorage {
        val recentDecisions: MutableList<PermissionDecision> = mutableListOf()

        override suspend fun storePermissionDecision(decision: PermissionDecision): Boolean {
            recentDecisions.add(decision)
            return true
        }

        override suspend fun loadPermissionDecisions(): List<PermissionDecision> {
            return recentDecisions
        }

        override suspend fun clearPermissionDecisions() {
            recentDecisions.clear()
        }

        override suspend fun removeOldData(): Boolean {
            // not implemented
            return true
        }

        override suspend fun removePermissionDecisionsForPackage(packageName: String): Boolean {
            // not implemented
            return true
        }

        override suspend fun updateDecisionsBySystemTimeDelta(diffSystemTimeMillis: Long): Boolean {
            // not implemented
            return true
        }
    }
}