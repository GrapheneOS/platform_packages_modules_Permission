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

package com.android.permissioncontroller.safetylabel

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import android.util.Xml
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.AppSafetyLabelHistory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataCategory
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.DataLabel
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.PackageInfo
import com.android.permissioncontroller.safetylabel.AppsSafetyLabelHistory.SafetyLabel
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlSerializer

/** Persists safety label history to disk and allows reading from and writing to this storage */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object AppsSafetyLabelHistoryPersistence {
    private const val TAG_DATA_SHARED_MAP = "shared"
    private const val TAG_DATA_SHARED_ENTRY = "entry"
    private const val TAG_PACKAGE_INFO = "pkg-info"
    private const val TAG_DATA_LABEL = "data-lbl"
    private const val TAG_SAFETY_LABEL = "sfty-lbl"
    private const val TAG_APP_SAFETY_LABEL_HISTORY = "app-hstry"
    private const val TAG_APPS_SAFETY_LABEL_HISTORY = "apps-hstry"
    private const val ATTRIBUTE_PACKAGE_NAME = "pkg-name"
    private const val ATTRIBUTE_RECEIVED_AT = "rcvd"
    private const val ATTRIBUTE_CATEGORY = "cat"
    private const val ATTRIBUTE_CONTAINS_ADS = "ads"
    /** The name of the file used to persist Safety Label history. */
    private const val APPS_SAFETY_LABEL_HISTORY_PERSISTENCE_FILE_NAME =
        "apps_safety_label_history_persistence.xml"
    private val LOG_TAG = "AppsSafetyLabelHistoryPersistence".take(23)

    /**
     * Reads the provided file storing safety label history and returns the parsed
     * [AppsSafetyLabelHistory].
     */
    fun read(file: File): AppsSafetyLabelHistory? {
        val parser = Xml.newPullParser()
        try {
            AtomicFile(file).openRead().let { inputStream ->
                parser.setInput(inputStream, StandardCharsets.UTF_8.name())
                return parser.parseHistoryFile()
            }
        } catch (e: FileNotFoundException) {
            Log.e(LOG_TAG, "File not found: $file")
        } catch (e: IOException) {
            Log.e(
                LOG_TAG, "Failed to read file: $file, encountered exception ${e.localizedMessage}")
        } catch (e: XmlPullParserException) {
            Log.e(
                LOG_TAG, "Failed to parse file: $file, encountered exception ${e.localizedMessage}")
        }

        return null
    }

    /** Serializes and writes the provided [AppsSafetyLabelHistory] to the provided file. */
    fun write(file: File, appsSafetyLabelHistory: AppsSafetyLabelHistory) {
        val atomicFile = AtomicFile(file)
        var outputStream: FileOutputStream? = null

        try {
            outputStream = atomicFile.startWrite()
            // TODO(b/263153094): Use BinaryXmlSerializer.
            val serializer = Xml.newSerializer()
            serializer.setOutput(outputStream, StandardCharsets.UTF_8.name())
            serializer.startDocument(null, true)
            serializer.serializeAllAppSafetyLabelHistory(appsSafetyLabelHistory)
            serializer.endDocument()
            atomicFile.finishWrite(outputStream)
        } catch (e: Exception) {
            Log.i(
                LOG_TAG, "Failed to write to $file. Previous version of file will be restored.", e)
            atomicFile.failWrite(outputStream)
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to close $file.", e)
            }
        }
    }

    /** Clears the file. */
    fun clear(file: File) {
        AtomicFile(file).delete()
    }

    /** Returns the file persisting safety label history for installed apps. */
    fun getSafetyLabelHistoryFile(context: Context): File =
        File(context.filesDir, APPS_SAFETY_LABEL_HISTORY_PERSISTENCE_FILE_NAME)

    private fun XmlPullParser.parseHistoryFile(): AppsSafetyLabelHistory {
        if (eventType != XmlPullParser.START_DOCUMENT) {
            throw IllegalArgumentException()
        }
        nextTag()

        val appsSafetyLabelHistory = parseAppsSafetyLabelHistory()

        while (eventType == XmlPullParser.TEXT && isWhitespace) {
            next()
        }
        if (eventType != XmlPullParser.END_DOCUMENT) {
            throw IllegalArgumentException("Unexpected extra element")
        }

        return appsSafetyLabelHistory
    }

    private fun XmlPullParser.parseAppsSafetyLabelHistory(): AppsSafetyLabelHistory {
        // TODO(b/263153093): Add versioning.
        checkTagStart(TAG_APPS_SAFETY_LABEL_HISTORY)
        nextTag()

        val appSafetyLabelHistories = mutableListOf<AppSafetyLabelHistory>()
        while (eventType == XmlPullParser.START_TAG && name == TAG_APP_SAFETY_LABEL_HISTORY) {
            appSafetyLabelHistories.add(parseAppSafetyLabelHistory())
        }

        checkTagEnd(TAG_APPS_SAFETY_LABEL_HISTORY)
        next()

        return AppsSafetyLabelHistory(appSafetyLabelHistories)
    }

    private fun XmlPullParser.parseAppSafetyLabelHistory(): AppSafetyLabelHistory {
        checkTagStart(TAG_APP_SAFETY_LABEL_HISTORY)
        nextTag()

        val packageInfo = parsePackageInfo()

        val safetyLabels = mutableListOf<SafetyLabel>()
        while (eventType == XmlPullParser.START_TAG && name == TAG_SAFETY_LABEL) {
            safetyLabels.add(parseSafetyLabel(packageInfo))
        }

        checkTagEnd(TAG_APP_SAFETY_LABEL_HISTORY)
        nextTag()

        return AppSafetyLabelHistory(packageInfo, safetyLabels)
    }

    private fun XmlPullParser.parseSafetyLabel(packageInfo: PackageInfo): SafetyLabel {
        checkTagStart(TAG_SAFETY_LABEL)

        var receivedAt: Instant? = null
        for (i in 0 until attributeCount) {
            when (getAttributeName(i)) {
                ATTRIBUTE_RECEIVED_AT -> receivedAt = parseInstant(getAttributeValue(i))
                else ->
                    throw IllegalArgumentException(
                        "Unexpected attribute ${getAttributeName(i)} in tag $TAG_SAFETY_LABEL")
            }
        }
        if (receivedAt == null) {
            throw IllegalArgumentException("Missing $ATTRIBUTE_RECEIVED_AT in $TAG_SAFETY_LABEL")
        }
        nextTag()

        val dataLabel = parseDataLabel()

        checkTagEnd(TAG_SAFETY_LABEL)
        nextTag()

        return SafetyLabel(packageInfo, receivedAt, dataLabel)
    }

    private fun XmlPullParser.parseDataLabel(): DataLabel {
        checkTagStart(TAG_DATA_LABEL)
        nextTag()

        val dataSharing = parseDataShared()

        checkTagEnd(TAG_DATA_LABEL)
        nextTag()

        return DataLabel(dataSharing)
    }

    private fun XmlPullParser.parseDataShared(): Map<String, DataCategory> {
        checkTagStart(TAG_DATA_SHARED_MAP)
        nextTag()

        val sharedCategories = mutableListOf<Pair<String, DataCategory>>()
        while (eventType == XmlPullParser.START_TAG && name == TAG_DATA_SHARED_ENTRY) {
            sharedCategories.add(parseDataSharedEntry())
        }

        checkTagEnd(TAG_DATA_SHARED_MAP)
        nextTag()

        return sharedCategories.associate { it.first to it.second }
    }

    private fun XmlPullParser.parseDataSharedEntry(): Pair<String, DataCategory> {
        checkTagStart(TAG_DATA_SHARED_ENTRY)
        var category: String? = null
        var hasAds: Boolean? = null
        for (i in 0 until attributeCount) {
            when (getAttributeName(i)) {
                ATTRIBUTE_CATEGORY -> category = getAttributeValue(i)
                ATTRIBUTE_CONTAINS_ADS -> hasAds = getAttributeValue(i).toBoolean()
                else ->
                    throw IllegalArgumentException(
                        "Unexpected attribute ${getAttributeName(i)} in tag $TAG_DATA_SHARED_ENTRY")
            }
        }
        if (category == null) {
            throw IllegalArgumentException("Missing $ATTRIBUTE_CATEGORY in $TAG_DATA_SHARED_ENTRY")
        }
        if (hasAds == null) {
            throw IllegalArgumentException(
                "Missing $ATTRIBUTE_CONTAINS_ADS in $TAG_DATA_SHARED_ENTRY")
        }
        nextTag()

        checkTagEnd(TAG_DATA_SHARED_ENTRY)
        nextTag()

        return category to DataCategory(hasAds)
    }

    private fun XmlPullParser.parsePackageInfo(): PackageInfo {
        checkTagStart(TAG_PACKAGE_INFO)
        var packageName: String? = null
        for (i in 0 until attributeCount) {
            when (getAttributeName(i)) {
                ATTRIBUTE_PACKAGE_NAME -> packageName = getAttributeValue(i)
                else ->
                    throw IllegalArgumentException(
                        "Unexpected attribute ${getAttributeName(i)} in tag $TAG_PACKAGE_INFO")
            }
        }
        if (packageName == null) {
            throw IllegalArgumentException("Missing $ATTRIBUTE_PACKAGE_NAME in $TAG_PACKAGE_INFO")
        }

        nextTag()
        checkTagEnd(TAG_PACKAGE_INFO)
        nextTag()
        return PackageInfo(packageName)
    }

    private fun XmlPullParser.checkTagStart(tag: String) {
        check(eventType == XmlPullParser.START_TAG && tag == name)
    }

    private fun XmlPullParser.checkTagEnd(tag: String) {
        check(eventType == XmlPullParser.END_TAG && tag == name)
    }

    private fun parseInstant(value: String): Instant {
        return try {
            Instant.ofEpochMilli(value.toLong())
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not parse $value as Instant")
        }
    }

    private fun XmlSerializer.serializeAllAppSafetyLabelHistory(
        appsSafetyLabelHistory: AppsSafetyLabelHistory
    ) {
        startTag(null, TAG_APPS_SAFETY_LABEL_HISTORY)
        appsSafetyLabelHistory.appSafetyLabelHistory.forEach { serializeAppSafetyLabelHistory(it) }
        endTag(null, TAG_APPS_SAFETY_LABEL_HISTORY)
    }

    private fun XmlSerializer.serializeAppSafetyLabelHistory(
        appSafetyLabelHistory: AppSafetyLabelHistory
    ) {
        startTag(null, TAG_APP_SAFETY_LABEL_HISTORY)
        serializePackageInfo(appSafetyLabelHistory.packageInfo)
        appSafetyLabelHistory.safetyLabelHistory.forEach { serializeSafetyLabel(it) }
        endTag(null, TAG_APP_SAFETY_LABEL_HISTORY)
    }

    private fun XmlSerializer.serializePackageInfo(packageInfo: PackageInfo) {
        startTag(null, TAG_PACKAGE_INFO)
        attribute(null, ATTRIBUTE_PACKAGE_NAME, packageInfo.packageName)
        endTag(null, TAG_PACKAGE_INFO)
    }

    private fun XmlSerializer.serializeSafetyLabel(safetyLabel: SafetyLabel) {
        startTag(null, TAG_SAFETY_LABEL)
        attribute(null, ATTRIBUTE_RECEIVED_AT, safetyLabel.receivedAt.toEpochMilli().toString())
        serializeDataLabel(safetyLabel.dataLabel)
        endTag(null, TAG_SAFETY_LABEL)
    }

    private fun XmlSerializer.serializeDataLabel(dataLabel: DataLabel) {
        startTag(null, TAG_DATA_LABEL)
        serializeDataSharedMap(dataLabel.dataShared)
        endTag(null, TAG_DATA_LABEL)
    }

    private fun XmlSerializer.serializeDataSharedMap(dataShared: Map<String, DataCategory>) {
        startTag(null, TAG_DATA_SHARED_MAP)
        dataShared.entries.forEach { serializeDataSharedEntry(it) }
        endTag(null, TAG_DATA_SHARED_MAP)
    }

    private fun XmlSerializer.serializeDataSharedEntry(
        dataSharedEntry: Map.Entry<String, DataCategory>
    ) {
        startTag(null, TAG_DATA_SHARED_ENTRY)
        attribute(null, ATTRIBUTE_CATEGORY, dataSharedEntry.key)
        attribute(
            null,
            ATTRIBUTE_CONTAINS_ADS,
            dataSharedEntry.value.containsAdvertisingPurpose.toString())
        endTag(null, TAG_DATA_SHARED_ENTRY)
    }
}
