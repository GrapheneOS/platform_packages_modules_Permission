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

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.DeviceConfig
import android.util.AtomicFile
import android.util.Log
import android.util.Xml
import androidx.annotation.VisibleForTesting
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.permission.data.PermissionDecision
import com.android.permissioncontroller.permission.service.RecentPermissionDecisionsStorage.Companion.getMaxDataAgeMs
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Thread-safe implementation of [RecentPermissionDecisionsStorage] using an XML file as the
 * database.
 */
class RecentPermissionDecisionsStorageImpl(
    private val context: Context,
    private val jobScheduler: JobScheduler
) : RecentPermissionDecisionsStorage {

    private val dbFile: AtomicFile = AtomicFile(File(context.filesDir, STORE_FILE_NAME))
    private val fileLock = Object()

    // We don't use namespaces
    private val ns: String? = null

    /**
     * The format for how dates are stored.
     */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val LOG_TAG = "RecentPermissionDecisionsStorageImpl"
        val DEFAULT_CLEAR_OLD_DECISIONS_CHECK_FREQUENCY = TimeUnit.DAYS.toMillis(1)
        const val DB_VERSION = 1

        /**
         * Config store file name for general shared store file.
         */
        const val STORE_FILE_NAME = "recent_permission_decisions.xml"

        const val TAG_RECENT_PERMISSION_DECISIONS = "recent-permission-decisions"
        const val TAG_PERMISSION_DECISION = "permission-decision"
        const val ATTR_VERSION = "version"
        const val ATTR_PACKAGE_NAME = "package-name"
        const val ATTR_PERMISSION_GROUP = "permission-group-name"
        const val ATTR_DECISION_TIME = "decision-time"
        const val ATTR_IS_GRANTED = "is-granted"

        fun getClearOldDecisionsCheckFrequencyMs() =
            DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
                Utils.PROPERTY_PERMISSION_DECISIONS_CHECK_OLD_FREQUENCY_MILLIS,
                DEFAULT_CLEAR_OLD_DECISIONS_CHECK_FREQUENCY)
    }

    init {
        scheduleOldDataCleanupIfNecessary()
    }

    override suspend fun storePermissionDecision(decision: PermissionDecision): Boolean {
        synchronized(fileLock) {
            val existingDecisions = readData()

            val newDecisions = mutableListOf<PermissionDecision>()
            // add new decision first to keep the list ordered
            newDecisions.add(decision)
            for (existingDecision in existingDecisions) {
                // ignore any old decisions that violate the (package, permission_group) uniqueness
                // with the database
                if (existingDecision.packageName == decision.packageName &&
                        existingDecision.permissionGroupName == decision.permissionGroupName) {
                    continue
                }
                newDecisions.add(existingDecision)
            }

            return writeData(newDecisions)
        }
    }

    override suspend fun loadPermissionDecisions(): List<PermissionDecision> {
        synchronized(fileLock) {
            return readData()
        }
    }

    override suspend fun clearPermissionDecisions() {
        synchronized(fileLock) {
            dbFile.delete()
        }
    }

    override suspend fun removeOldData(): Boolean {
        synchronized(fileLock) {
            val existingDecisions = readData()

            val originalCount = existingDecisions.size
            val newDecisions = existingDecisions.filter {
                return (System.currentTimeMillis() - it.decisionTime) <= getMaxDataAgeMs()
            }

            DumpableLog.d(LOG_TAG,
                "${originalCount - newDecisions.size} old permission decisions removed")

            return writeData(newDecisions)
        }
    }

    private fun scheduleOldDataCleanupIfNecessary() {
        if (isNewJobScheduleRequired()) {
            val jobInfo = JobInfo.Builder(
                Constants.OLD_PERMISSION_DECISION_CLEANUP_JOB_ID,
                ComponentName(context, DecisionCleanupJobService::class.java))
                .setPeriodic(getClearOldDecisionsCheckFrequencyMs())
                // persist this job across boots
                .setPersisted(true)
                .build()
            val status = jobScheduler.schedule(jobInfo)
            if (status != JobScheduler.RESULT_SUCCESS) {
                DumpableLog.e(LOG_TAG, "Could not schedule " +
                    "${DecisionCleanupJobService::class.java.simpleName}: $status")
            }
        }
    }

    /**
     * Returns whether a new job needs to be scheduled. A persisted job is used to keep the schedule
     * across boots, but that job needs to be scheduled a first time and whenever the check
     * frequency changes.
     */
    private fun isNewJobScheduleRequired(): Boolean {
        var scheduleNewJob = false
        val existingJob: JobInfo? = jobScheduler
            .getPendingJob(Constants.OLD_PERMISSION_DECISION_CLEANUP_JOB_ID)
        when {
            existingJob == null -> {
                DumpableLog.i(LOG_TAG, "No existing job, scheduling a new one")
                scheduleNewJob = true
            }
            existingJob.intervalMillis != getClearOldDecisionsCheckFrequencyMs() -> {
                DumpableLog.i(LOG_TAG, "Interval frequency has changed, updating job")
                scheduleNewJob = true
            }
            else -> {
                DumpableLog.i(LOG_TAG, "Job already scheduled.")
            }
        }
        return scheduleNewJob
    }

    override suspend fun removePermissionDecisionsForPackage(packageName: String): Boolean {
        synchronized(fileLock) {
            val existingDecisions = readData()

            val newDecisions = existingDecisions.filter { it.packageName != packageName }
            return writeData(newDecisions)
        }
    }

    override suspend fun updateDecisionsBySystemTimeDelta(diffSystemTimeMillis: Long): Boolean {
        if (abs(diffSystemTimeMillis) < TimeUnit.DAYS.toMillis(1)) {
            DumpableLog.i(LOG_TAG, "Ignoring time change - less than one day")
            return true
        }

        synchronized(fileLock) {
            val existingDecisions = readData()

            val newDecisions = existingDecisions.map {
                // delta will be rounded down to the nearest day in writeData
                it.copy(decisionTime = it.decisionTime + diffSystemTimeMillis)
            }
            return writeData(newDecisions)
        }
    }

    private fun writeData(decisions: List<PermissionDecision>): Boolean {
        val stream: FileOutputStream = try {
            dbFile.startWrite()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Failed to save db file", e)
            return false
        }
        try {
            serializeData(stream, decisions)
            dbFile.finishWrite(stream)
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Failed to save db file, restoring backup", e)
            dbFile.failWrite(stream)
            return false
        }

        return true
    }

    private fun serializeData(stream: OutputStream, decisions: List<PermissionDecision>) {
        val out = Xml.newSerializer()
        out.setOutput(stream, StandardCharsets.UTF_8.name())
        out.startDocument(/* encoding= */ null, /* standalone= */ true)
        out.startTag(ns, TAG_RECENT_PERMISSION_DECISIONS)
        out.attribute(null, ATTR_VERSION, DB_VERSION.toString())
        for (decision in decisions) {
            out.startTag(ns, TAG_PERMISSION_DECISION)
            out.attribute(ns, ATTR_PACKAGE_NAME, decision.packageName)
            out.attribute(ns, ATTR_PERMISSION_GROUP, decision.permissionGroupName)
            val date = dateFormat.format(Date(decision.decisionTime))
            out.attribute(ns, ATTR_DECISION_TIME, date)
            out.attribute(ns, ATTR_IS_GRANTED, decision.isGranted.toString())
            out.endTag(ns, TAG_PERMISSION_DECISION)
        }
        out.endTag(null, TAG_RECENT_PERMISSION_DECISIONS)
        out.endDocument()
    }

    private fun readData(): List<PermissionDecision> {
        if (!dbFile.baseFile.exists()) {
            return emptyList()
        }
        return try {
            parse(dbFile.openRead())
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Failed to read db file", e)
            emptyList()
        } catch (e: XmlPullParserException) {
            Log.e(LOG_TAG, "Failed to read db file", e)
            emptyList()
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(inputStream: InputStream): List<PermissionDecision> {
        inputStream.use {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            parser.nextTag()
            return readRecentDecisions(parser)
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readRecentDecisions(parser: XmlPullParser): List<PermissionDecision> {
        val entries = mutableListOf<PermissionDecision>()

        parser.require(XmlPullParser.START_TAG, ns, TAG_RECENT_PERMISSION_DECISIONS)
        while (parser.next() != XmlPullParser.END_TAG) {
            entries.add(readPermissionDecision(parser))
        }
        return entries
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPermissionDecision(parser: XmlPullParser): PermissionDecision {
        parser.require(XmlPullParser.START_TAG, ns, TAG_PERMISSION_DECISION)
        val packageName = parser.getAttributeValue(ns, ATTR_PACKAGE_NAME)
        val permissionGroup = parser.getAttributeValue(ns, ATTR_PERMISSION_GROUP)
        val decisionDate = parser.getAttributeValue(ns, ATTR_DECISION_TIME)
        val decisionTime = dateFormat.parse(decisionDate).time
        val isGranted = parser.getAttributeValue(ns, ATTR_IS_GRANTED).toBoolean()
        parser.nextTag()
        parser.require(XmlPullParser.END_TAG, ns, TAG_PERMISSION_DECISION)
        return PermissionDecision(packageName, permissionGroup, decisionTime, isGranted)
    }
}

/**
 * A job to clean up old permission decisions.
 */
class DecisionCleanupJobService(
    @VisibleForTesting
    val storage: RecentPermissionDecisionsStorage = RecentPermissionDecisionsStorage.getInstance()
) : JobService() {

    companion object {
        const val LOG_TAG = "DecisionCleanupJobService"
    }

    var job: Job? = null
    var jobStartTime: Long = -1L

    override fun onStartJob(params: JobParameters?): Boolean {
        DumpableLog.i(LOG_TAG, "onStartJob")
        jobStartTime = System.currentTimeMillis()
        job = GlobalScope.launch(Dispatchers.IO) {
            val success = storage.removeOldData()
            if (!success) {
                DumpableLog.e(LOG_TAG, "Failed to remove old permission decisions")
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        DumpableLog.w(LOG_TAG, "onStopJob after ${System.currentTimeMillis() - jobStartTime}ms")
        job?.cancel()
        return true
    }
}
