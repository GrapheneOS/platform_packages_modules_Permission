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
import android.safetycenter.cts.testing.Coroutines.waitForWithTimeout
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

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
            events.add(NotificationPosted(statusBarNotification))
        }
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        super.onNotificationRemoved(statusBarNotification)
        events.add(NotificationRemoved(statusBarNotification))
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

        private val events = CopyOnWriteArrayList<NotificationEvent>()

        /**
         * Invokes the given [block] while observing all [NotificationEvent] of type [T] and returns
         * those events.
         *
         * This method blocks until [duration] has elapsed and then returns a possibly-empty list
         * containing those events.
         */
        private inline fun <reified T : NotificationEvent> getNotificationEvents(
            duration: Duration,
            block: () -> Any?
        ): List<T> {
            events.clear()
            block.invoke()
            return runBlocking {
                // TODO(b/262691874): Refactor notification tests (reduce use of delays)
                delay(duration.toMillis())
                events.filterIsInstance<T>()
            }
        }

        /**
         * Invokes the given [block] while observing all [NotificationEvent] of type [T] and returns
         * those events.
         *
         * This method blocks until either [limit] events of type [T] have occurred, or [duration]
         * has elapsed and then returns a possibly-empty list containing those events.
         */
        private inline fun <reified T : NotificationEvent> getNotificationEvents(
            limit: Int,
            duration: Duration,
            block: () -> Any?
        ): List<T> {
            require(limit > 0) { "limit must be greater than zero" }
            events.clear()
            block.invoke()
            return runBlocking {
                try {
                    waitForWithTimeout(duration) { events.count { it is T } >= limit }
                } catch (e: TimeoutCancellationException) {
                    // OK, this means there weren't limit events within duration, continue
                }
                events.filterIsInstance<T>().take(limit)
            }
        }

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
            return getNotificationEvents<NotificationPosted>(limit = 1, duration = timeout, block)
                .firstOrNull()
                ?.statusBarNotification
        }

        /**
         * Invoked the given [block] and then returns a possibly-empty list of all the
         * [StatusBarNotification] instances that were posted within [duration].
         */
        fun getAllNotificationsPosted(
            duration: Duration = TIMEOUT_SHORT,
            block: () -> Any?
        ): List<StatusBarNotification> {
            return getNotificationEvents<NotificationPosted>(duration, block).map {
                it.statusBarNotification
            }
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
                .firstOrNull()
                ?.statusBarNotification
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
