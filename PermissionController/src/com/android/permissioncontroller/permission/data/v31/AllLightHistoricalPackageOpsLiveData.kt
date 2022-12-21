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

package com.android.permissioncontroller.permission.data.v31

import android.app.AppOpsManager
import android.app.AppOpsManager.HISTORY_FLAG_DISCRETE
import android.app.AppOpsManager.HistoricalOps
import android.app.AppOpsManager.HistoricalOpsRequest
import android.app.AppOpsManager.OP_FLAG_SELF
import android.app.AppOpsManager.OP_FLAG_TRUSTED_PROXIED
import android.app.Application
import android.os.UserHandle
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.v31.LightHistoricalPackageOps
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job

/**
 * LiveData class tracking [LightHistoricalPackageOps] for all packages on the device and for the
 * provided app ops.
 *
 * App ops data is retrieved from [AppOpsManager] and is updated whenever app ops data changes are
 * heard.
 */
class AllLightHistoricalPackageOpsLiveData(app: Application, val opNames: Set<String>) :
    SmartAsyncMediatorLiveData<Map<Pair<String, UserHandle>, LightHistoricalPackageOps>>(),
    AppOpsManager.OnOpActiveChangedListener,
    AppOpsManager.OnOpNotedListener,
    AppOpsManager.OnOpChangedListener {

    private val appOpsManager = app.getSystemService(AppOpsManager::class.java)!!

    override fun onActive() {
        super.onActive()

        opNames.forEach { opName ->
            // TODO(b/262035952): We watch each active op individually as startWatchingActive only
            // registers the callback if all ops are valid. Fix this behavior so if one op is
            // invalid it doesn't affect the other ops.
            try {
                appOpsManager.startWatchingActive(arrayOf(opName), { it.run() }, this)
            } catch (ignored: IllegalArgumentException) {
                // Older builds may not support all requested app ops.
            }

            try {
                appOpsManager.startWatchingMode(opName, /* all packages */ null, this)
            } catch (ignored: IllegalArgumentException) {
                // Older builds may not support all requested app ops.
            }
        }
    }

    override fun onInactive() {
        super.onInactive()

        appOpsManager.stopWatchingActive(this)
        appOpsManager.stopWatchingMode(this)
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        val allLightHistoricalPackageOps =
            mutableMapOf<Pair<String, UserHandle>, LightHistoricalPackageOps>()

        val endTimeMillis = System.currentTimeMillis()
        // TODO(b/257317733): Consider setting timeframe according to whether we are displaying 7
        // days data.
        val beginTimeMillis = endTimeMillis - TimeUnit.DAYS.toMillis(7)

        // TODO(b/257317733): The following AppOpsManager call has been copied from legacy code in
        // PermissionUsages.
        // Rewrite it once we have confidence in the rest of the LiveData pattern for the Permission
        // Usage Details page. Can we use a coroutine here?
        val historicalOpsRef = AtomicReference<HistoricalOps>()
        val latch = CountDownLatch(1)

        val request =
            HistoricalOpsRequest.Builder(beginTimeMillis, endTimeMillis)
                .setFlags(OP_FLAG_SELF or OP_FLAG_TRUSTED_PROXIED)
                .setHistoryFlags(HISTORY_FLAG_DISCRETE or HISTORY_FLAG_GET_ATTRIBUTION_CHAINS)
                .build()
        appOpsManager.getHistoricalOps(request, { obj: Runnable -> obj.run() }) { ops: HistoricalOps
            ->
            historicalOpsRef.set(ops)
            latch.countDown()
        }

        try {
            latch.await(5, TimeUnit.DAYS)
        } catch (ignored: InterruptedException) {}
        val historicalOps = historicalOpsRef.get()

        for (i in 0 until historicalOps.uidCount) {
            val historicalUidOps = historicalOps.getUidOpsAt(i)
            val userHandle = UserHandle.getUserHandleForUid(historicalUidOps.uid)
            for (j in 0 until historicalUidOps.packageCount) {
                val historicalPackageOps = historicalUidOps.getPackageOpsAt(j)
                allLightHistoricalPackageOps[Pair(historicalPackageOps.packageName, userHandle)] =
                    LightHistoricalPackageOps(historicalPackageOps, userHandle, opNames)
            }
        }

        postValue(allLightHistoricalPackageOps)
    }

    override fun onOpChanged(op: String?, packageName: String?) {
        update()
    }

    override fun onOpActiveChanged(op: String, uid: Int, packageName: String, active: Boolean) {
        update()
    }

    override fun onOpNoted(
        code: String,
        uid: Int,
        packageName: String,
        attributionTag: String?,
        flags: Int,
        result: Int
    ) {
        update()
    }

    /** Companion object for [AllLightHistoricalPackageOpsLiveData]. */
    companion object {
        // TODO(b/257317733): Add
        // http://cs/android-internal/frameworks/base/core/java/android/app/AppOpsManager.java?rcl=07feded3186ec30d8da280d85fb68933fc628a31&l=4506 to API.
        const val HISTORY_FLAG_GET_ATTRIBUTION_CHAINS = 1 shl 2
    }
}
