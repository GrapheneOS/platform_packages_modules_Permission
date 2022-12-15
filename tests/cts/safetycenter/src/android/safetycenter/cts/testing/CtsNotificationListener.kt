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

package android.safetycenter.cts.testing

import android.content.ComponentName
import android.os.ConditionVariable
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_LONG
import android.safetycenter.cts.testing.Coroutines.TIMEOUT_SHORT
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Used in CTS tests to check whether expected notifications are present in the status bar. */
class CtsNotificationListener : NotificationListenerService() {

    private sealed class NotificationEvent(val statusBarNotification: StatusBarNotification)

    private class NotificationPosted(statusBarNotification: StatusBarNotification) :
        NotificationEvent(statusBarNotification) {
        override fun toString(): String = "Posted $statusBarNotification"
    }

    private class NotificationRemoved(statusBarNotification: StatusBarNotification) :
        NotificationEvent(statusBarNotification) {
        override fun toString(): String = "Removed $statusBarNotification"
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        super.onNotificationPosted(statusBarNotification)
        if (isSafetyCenterNotification(statusBarNotification)) {
            eventFlow.tryEmit(NotificationPosted(statusBarNotification))
        }
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        super.onNotificationRemoved(statusBarNotification)
        eventFlow.tryEmit(NotificationRemoved(statusBarNotification))
    }

    override fun onListenerConnected() {
        Log.d(TAG, "onListenerConnected")
        super.onListenerConnected()
        instance = this
        connected.open()
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "onListenerDisconnected")
        super.onListenerDisconnected()
        connected.close()
        instance = null
    }

    companion object {
        private const val TAG = "CtsNotificationListener"

        private val id: String =
            "android.safetycenter.cts/" + CtsNotificationListener::class.java.name
        private val componentName =
            ComponentName("android.safetycenter.cts", CtsNotificationListener::class.java.name)

        private val connected = ConditionVariable(false)
        private var instance: CtsNotificationListener? = null

        private val eventFlow =
            MutableSharedFlow<NotificationEvent>(
                replay = 0, extraBufferCapacity = 99, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /**
         * Invokes the given [block] and asserts that no notifications are posted within [duration].
         */
        fun assertNoNotificationsPosted(duration: Duration = TIMEOUT_SHORT, block: () -> Any?) {
            assertThat(getNextNotificationPostedOrNull(duration, block)).isNull()
        }

        /**
         * Invokes the given [block] and asserts that a notification is posted within [duration].
         */
        fun assertAnyNotificationPosted(duration: Duration = TIMEOUT_LONG, block: () -> Any?) {
            assertThat(getNextNotificationPostedOrNull(duration, block)).isNotNull()
        }

        /**
         * Invokes the given [block] and then waits until the next notification is posted and
         * returns it, or returns `null` if no notification is posted within [timeout].
         */
        fun getNextNotificationPostedOrNull(
            timeout: Duration = TIMEOUT_LONG,
            block: () -> Any?
        ): StatusBarNotification? {
            return getNotificationsPosted(limit = 1, duration = timeout, block).firstOrNull()
        }

        /**
         * Invoked the given [block] and then returns a possibly-empty list of all the
         * [StatusBarNotification] instances that were posted within [duration].
         */
        fun getAllNotificationsPosted(
            duration: Duration = TIMEOUT_SHORT,
            block: () -> Any?
        ): List<StatusBarNotification> {
            return getNotificationsPosted(limit = 0, duration, block)
        }

        /**
         * Invokes the given [block] and then waits until either [limit] notifications have been
         * posted, or [duration] has elapsed and then returns a possibly-empty list containing those
         * [StatusBarNotification] instances.
         *
         * [limit] only applies if it's a positive integer, otherwise all notifications posted
         * within the given [duration] are returned.
         */
        private fun getNotificationsPosted(
            limit: Int,
            duration: Duration,
            block: () -> Any?
        ): List<StatusBarNotification> {
            return getNotificationEvents<NotificationPosted>(limit, duration, block).map {
                it.statusBarNotification
            }
        }

        /**
         * Invokes the given [block] while observing all [NotificationEvent] of type [T] and returns
         * those events.
         *
         * This method blocks until either [limit] events of type [T] have occurred, or [duration]
         * has elapsed and then returns a possibly-empty list containing those events.
         *
         * [limit] only applies if it's a positive integer, otherwise all events that occur within
         * the given [duration] are returned.
         */
        private inline fun <reified T : NotificationEvent> getNotificationEvents(
            limit: Int,
            duration: Duration,
            block: () -> Any?
        ): List<T> {
            // We keep this list of events so far because when `toList` gets timed out it returns
            // nothing and not just everything it collected so far i.e. `take(3).toList()` returns
            // either a list of exactly 3 elements or nothing.
            val eventsSoFar = mutableListOf<T>()
            val filteredEventFlow =
                eventFlow.filterIsInstance<T>().onEach {
                    Log.d(TAG, "Event: $it")
                    eventsSoFar.add(it)
                }
            block()
            return runBlocking {
                withTimeoutOrNull(duration.toMillis()) {
                    if (limit > 0) {
                        filteredEventFlow.take(limit).toList()
                    } else {
                        filteredEventFlow.toList()
                    }
                }
            }
                ?: eventsSoFar
        }

        /**
         * Invokes the given [block] and asserts that a notification is removed within [duration].
         */
        fun assertAnyNotificationRemoved(duration: Duration = TIMEOUT_LONG, block: () -> Any?) {
            assertThat(getNextNotificationRemovedOrNull(duration, block)).isNotNull()
        }

        /**
         * Invokes the given [block] and then waits until the next notification is removed and
         * returns it, or returns `null` if no notification is removed within [timeout].
         */
        fun getNextNotificationRemovedOrNull(
            timeout: Duration = TIMEOUT_LONG,
            block: () -> Any?
        ): StatusBarNotification? {
            return getNotificationEvents<NotificationRemoved>(limit = 1, duration = timeout, block)
                .map { it.statusBarNotification }
                .firstOrNull()
        }

        /**
         * Cancels a specific notification and then waits for it to be removed by the notification
         * manager, or throws if it has not been removed within [timeout].
         */
        fun cancelAndWait(key: String, timeout: Duration = TIMEOUT_LONG) {
            val cancelled =
                getNextNotificationRemovedOrNull(timeout) { instance?.cancelNotification(key) }
            assertThat(cancelled).isNotNull()
            assertThat(cancelled!!.key).isEqualTo(key)
        }

        /** Runs a shell command to allow or disallow the listener. Use before and after test. */
        fun toggleListenerAccess(allowed: Boolean) {
            // TODO(b/260335646): Try to do this using the AndroidTest.xml instead of in code
            val verb = if (allowed) "allow" else "disallow"
            SystemUtil.runShellCommand("cmd notification ${verb}_listener $id")
            if (allowed) {
                requestRebind(componentName)
                if (!connected.block(TIMEOUT_LONG.toMillis())) {
                    throw TimeoutException("Notification listener not connected")
                }
            }
        }

        private fun isSafetyCenterNotification(
            statusBarNotification: StatusBarNotification
        ): Boolean =
            statusBarNotification.packageName == "android" &&
                statusBarNotification.notification.channelId == "safety_center"
    }
}
