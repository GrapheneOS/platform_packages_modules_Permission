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

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import android.util.Xml
import com.android.permissioncontroller.permission.data.PermissionDecision
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

/**
 * Thread-safe implementation of [RecentPermissionDecisionsStorage] using an XML file as the
 * database.
 */
class RecentPermissionDecisionsStorageImpl(
    private val context: Context
) : RecentPermissionDecisionsStorage {

    private val dbFile: AtomicFile
    private val fileLock = Object()

    // We don't use namespaces
    private val ns: String? = null

    /**
     * The format for how dates are stored.
     */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val LOG_TAG = "RecentPermissionDecisionsStorageImpl"

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
    }

    init {
        dbFile = AtomicFile(File(context.filesDir, STORE_FILE_NAME))
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

    override suspend fun removePermissionDecisionsForPackage(packageName: String): Boolean {
        synchronized(fileLock) {
            val existingDecisions = readData()

            val newDecisions = existingDecisions.filter { it.packageName != packageName }
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