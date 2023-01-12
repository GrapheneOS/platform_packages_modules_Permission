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
import android.safetycenter.cts.testing.Coroutines.runBlockingWithTimeout
import android.safetycenter.cts.testing.Coroutines.runBlockingWithTimeoutOrNull
import android.safetycenter.cts.testing.Coroutines.waitForWithTimeout
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel

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
        if (statusBarNotification.isSafetyCenterNotification()) {
            runBlockingWithTimeout {
                safetyCenterNotificationEvents.send(NotificationPosted(statusBarNotification))
            }
        }
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        super.onNotificationRemoved(statusBarNotification)
        if (statusBarNotification.isSafetyCenterNotification()) {
            runBlockingWithTimeout {
                safetyCenterNotificationEvents.send(NotificationRemoved(statusBarNotification))
            }
        }
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

        @Volatile
        private var safetyCenterNotificationEvents =
            Channel<NotificationEvent>(capacity = Channel.UNLIMITED)

        /**
         * Blocks until there are zero Safety Center notifications, or throw an [AssertionError] if
         * that doesn't happen within [timeout].
         */
        fun waitForZeroNotifications(timeout: Duration = TIMEOUT_LONG) {
            waitForNotificationCount(0, timeout)
        }

        /**
         * Blocks until there is exactly one Safety Center notification and then return it, or throw
         * an [AssertionError] if that doesn't happen within [timeout].
         */
        fun waitForSingleNotification(timeout: Duration = TIMEOUT_LONG): StatusBarNotification {
            return waitForNotificationCount(1, timeout).first()
        }

        /**
         * Blocks until there are exactly [count] Safety Center notifications and then return them,
         * or throw an [AssertionError] if that doesn't happen within [timeout].
         */
        private fun waitForNotificationCount(
            count: Int,
            timeout: Duration = TIMEOUT_LONG
        ): List<StatusBarNotification> {
            return waitForNotificationsToSatisfy(timeout, description = "$count notifications") {
                it.size == count
            }
        }

        /**
         * Blocks until there is a single Safety Center notification matching the given
         * [characteristics] and then return it, or throw an [AssertionError] if that doesn't happen
         * within [timeout].
         */
        fun waitForSingleNotificationMatching(
            characteristics: NotificationCharacteristics,
            timeout: Duration = TIMEOUT_LONG
        ): StatusBarNotification {
            return waitForNotificationsMatching(characteristics, timeout = timeout).first()
        }

        /**
         * Blocks until the set of Safety Center notifications matches the given [characteristics]
         * and then return them, or throw an [AssertionError] if that doesn't happen within
         * [timeout].
         */
        private fun waitForNotificationsMatching(
            vararg characteristics: NotificationCharacteristics,
            timeout: Duration = TIMEOUT_LONG
        ): List<StatusBarNotification> {
            val charsList = characteristics.toList()
            return waitForNotificationsToSatisfy(
                timeout,
                description = "notification(s) matching characteristics $charsList"
            ) { NotificationCharacteristics.areMatching(it, charsList) }
        }

        /**
         * Blocks until [forAtLeast] has elapsed, or throw an [AssertionError] if any notification
         * is posted or removed before then.
         */
        fun waitForZeroNotificationEvents(forAtLeast: Duration = TIMEOUT_SHORT) {
            val event =
                runBlockingWithTimeoutOrNull(forAtLeast) {
                    safetyCenterNotificationEvents.receive()
                }
            assertThat(event).isNull()
        }

        private fun waitForNotificationsToSatisfy(
            timeout: Duration = TIMEOUT_LONG,
            forAtLeast: Duration = TIMEOUT_SHORT,
            description: String,
            predicate: (List<StatusBarNotification>) -> Boolean
        ): List<StatusBarNotification> {
            fun formatError(notifs: List<StatusBarNotification>): String {
                return "Expected: $description, but the actual notifications were: $notifs"
            }

            // First we wait at most timeout for the active notifications to satisfy the given
            // predicate or otherwise we throw:
            val satisfyingNotifications =
                try {
                    runBlockingWithTimeout(timeout) {
                        waitForNotificationsToSatisfyAsync(predicate)
                    }
                } catch (e: TimeoutCancellationException) {
                    throw AssertionError(formatError(getSafetyCenterNotifications()), e)
                }

            // Assuming the predicate was satisfied, now we ensure it is not violated for the
            // forAtLeast duration as well:
            val nonSatisfyingNotifications =
                runBlockingWithTimeoutOrNull(forAtLeast) {
                    waitForNotificationsToSatisfyAsync { !predicate(it) }
                }
            if (nonSatisfyingNotifications != null) {
                // In this case the negated-predicate was satisfied before forAtLeast had elapsed
                throw AssertionError(formatError(nonSatisfyingNotifications))
            }

            return satisfyingNotifications
        }

        private suspend fun waitForNotificationsToSatisfyAsync(
            predicate: (List<StatusBarNotification>) -> Boolean
        ): List<StatusBarNotification> {
            var currentNotifications = getSafetyCenterNotifications()
            while (!predicate(currentNotifications)) {
                val event = safetyCenterNotificationEvents.receive()
                Log.d(TAG, "Received notification event: $event")
                currentNotifications = getSafetyCenterNotifications()
            }
            return currentNotifications
        }

        private fun getSafetyCenterNotifications(): List<StatusBarNotification> =
            instance!!.activeNotifications.filter { it.isSafetyCenterNotification() }

        /**
         * Cancels a specific notification and then waits for it to be removed by the notification
         * manager and marked as dismissed in Safety Center, or throws if it has not been removed
         * within [timeout].
         */
        fun cancelAndWait(key: String, timeout: Duration = TIMEOUT_LONG) {
            instance!!.cancelNotification(key)
            waitForNotificationsToSatisfy(
                timeout,
                description = "no notification with the key $key"
            ) { notifications -> notifications.none { it.key == key } }

            waitForIssueCacheToContainAnyDismissedNotification()
        }

        private fun waitForIssueCacheToContainAnyDismissedNotification() {
            // Here we wait for an issue to be recorded as dismissed according to the dumpsys
            // output. The cancelAndWait helper above first "waits" for the notification to
            // be dismissed, but this additional wait is needed to ensure the notification's delete
            // PendingIntent is handled. Without this wait there is a race condition between
            // SafetyCenterNotificationReceiver#onReceive and subsequent calls that set source data
            // and that race makes tests flaky because the dismissal status of the previous
            // notification is not well defined.
            fun dumpIssueCacheState(): String =
                SystemUtil.runShellCommand("dumpsys safety_center issues")
            try {
                waitForWithTimeout {
                    dumpIssueCacheState().contains(Regex("""mNotificationDismissedAt=\d+"""))
                }
            } catch (e: TimeoutCancellationException) {
                throw IllegalStateException(
                    "Notification dismissal was not recorded in the issue cache: " +
                        dumpIssueCacheState(),
                    e
                )
            }
        }

        /** Runs a shell command to allow or disallow the listener. Use before and after test. */
        private fun toggleListenerAccess(allowed: Boolean) {
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

        /** Prepare the [CtsNotificationListener] for a notification test */
        fun setup() {
            toggleListenerAccess(true)
        }

        /** Clean up the [CtsNotificationListener] after executing a notification test. */
        fun reset() {
            waitForNotificationsToSatisfy(
                forAtLeast = Duration.ZERO,
                description = "all Safety Center notifications removed in tear down"
            ) { it.isEmpty() }
            toggleListenerAccess(false)
            safetyCenterNotificationEvents.cancel()
            safetyCenterNotificationEvents = Channel(capacity = Channel.UNLIMITED)
        }

        // TODO(b/264369469): Tests should account for multiple SC notification channels
        private fun StatusBarNotification.isSafetyCenterNotification(): Boolean =
            packageName == "android" && notification.channelId == "safety_center"
    }
}
