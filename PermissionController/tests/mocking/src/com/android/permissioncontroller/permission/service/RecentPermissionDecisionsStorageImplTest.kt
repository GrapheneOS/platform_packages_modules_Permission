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

package com.android.permissioncontroller.permission.service

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permissioncontroller.permission.data.PermissionDecision
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import java.util.Date

@RunWith(AndroidJUnit4::class)
class RecentPermissionDecisionsStorageImplTest {

    private lateinit var storage: RecentPermissionDecisionsStorage

    private val jan12020 = Date(2020, 0, 1).time
    private val jan22020 = Date(2020, 0, 2).time

    private val mapPackageName = "package.test.map"

    private val musicCalendarGrant = PermissionDecision(
        "package.test.music", "calendar", jan12020, false)
    private val mapLocationGrant = PermissionDecision(
        mapPackageName, "location", jan12020, true)
    private val mapLocationDenied = PermissionDecision(
        mapPackageName, "location", jan12020, false)
    private val mapMicrophoneGrant = PermissionDecision(
        mapPackageName, "microphone", jan12020, true)
    private val parkingLocationGrant = PermissionDecision(
        "package.test.parking", "location", jan22020, true)
    private val podcastMicrophoneGrant = PermissionDecision(
        "package.test.podcast", "microphone", jan22020, true)

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        storage = RecentPermissionDecisionsStorageImpl(ApplicationProvider.getApplicationContext())
    }

    @After
    fun cleanup() = runBlocking {
        storage.clearPermissionDecisions()
    }

    @Test
    fun loadPermissionDecisions_noData_returnsEmptyList() {
        runBlocking {
            assertThat(storage.loadPermissionDecisions()).isEmpty()
        }
    }

    @Test
    fun storePermissionDecision_singleDecision_writeSuccessAndReturnOnLoad() {
        runBlocking {
            assertThat(storage.storePermissionDecision(mapLocationGrant)).isTrue()
            assertThat(storage.loadPermissionDecisions()).containsExactly(mapLocationGrant)
        }
    }

    @Test
    fun storePermissionDecision_roundsTimeDownToDate() {
        runBlocking {
            val laterInTheDayGrant = musicCalendarGrant.copy(
                decisionTime = (musicCalendarGrant.decisionTime + (5 * 60 * 60 * 1000)))
            assertThat(storage.storePermissionDecision(laterInTheDayGrant)).isTrue()
            assertThat(storage.loadPermissionDecisions()).containsExactly(musicCalendarGrant)
        }
    }

    @Test
    fun storePermissionDecision_multipleDecisions_returnedOrderedByMostRecentlyAdded() {
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.storePermissionDecision(musicCalendarGrant)
            storage.storePermissionDecision(parkingLocationGrant)
            storage.storePermissionDecision(podcastMicrophoneGrant)
            assertThat(storage.loadPermissionDecisions())
                .containsExactly(musicCalendarGrant, mapLocationGrant, parkingLocationGrant,
                    podcastMicrophoneGrant)
        }
    }

    @Test
    fun storePermissionDecision_uniqueForPackagePermissionGroup() {
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.storePermissionDecision(mapLocationDenied)
            assertThat(storage.loadPermissionDecisions()).containsExactly(mapLocationDenied)
        }
    }

    @Test
    fun storePermissionDecision_ignoresExactDuplicates() {
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.storePermissionDecision(mapLocationGrant)
            assertThat(storage.loadPermissionDecisions()).containsExactly(mapLocationGrant)
        }
    }

    @Test
    fun clearPermissionDecisions_clearsExistingData() {
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.clearPermissionDecisions()
            assertThat(storage.loadPermissionDecisions()).isEmpty()
        }
    }

    @Test
    fun removePermissionDecisionsForPackage_removesDecisions() {
        runBlocking {
            storage.storePermissionDecision(mapLocationGrant)
            storage.storePermissionDecision(musicCalendarGrant)
            storage.storePermissionDecision(mapMicrophoneGrant)
            storage.removePermissionDecisionsForPackage(mapPackageName)
            assertThat(storage.loadPermissionDecisions()).containsExactly(musicCalendarGrant)
        }
    }
}