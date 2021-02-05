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

package com.android.permissioncontroller.hibernation

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.printservice.PrintService
import android.provider.DeviceConfig
import android.service.autofill.AutofillService
import android.service.dreams.DreamService
import android.service.notification.NotificationListenerService
import android.service.voice.VoiceInteractionService
import android.service.wallpaper.WallpaperService
import android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS
import android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS
import android.util.Log
import android.view.inputmethod.InputMethod
import androidx.annotation.MainThread
import androidx.preference.PreferenceManager
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.R
import com.android.permissioncontroller.hibernation.utils.PolicyUtils
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.AppOpLiveData
import com.android.permissioncontroller.permission.data.BroadcastReceiverLiveData
import com.android.permissioncontroller.permission.data.CarrierPrivilegedStatusLiveData
import com.android.permissioncontroller.permission.data.DataRepositoryForPackage
import com.android.permissioncontroller.permission.data.HasIntentAction
import com.android.permissioncontroller.permission.data.ServiceLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.UnusedAutoRevokedPackagesLiveData
import com.android.permissioncontroller.permission.data.UsageStatsLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.service.revokeAppPermissions
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.application
import com.android.permissioncontroller.permission.utils.forEachInParallel
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Random
import java.util.concurrent.TimeUnit

private const val LOG_TAG = "HibernationPolicy"
const val DEBUG_OVERRIDE_THRESHOLDS = false
// TODO eugenesusla: temporarily enabled for extra logs during dogfooding
const val DEBUG_AUTO_REVOKE_POLICY = true || DEBUG_OVERRIDE_THRESHOLDS

private const val AUTO_REVOKE_ENABLED = true

private var SKIP_NEXT_RUN = false

private val DEFAULT_UNUSED_THRESHOLD_MS =
        if (AUTO_REVOKE_ENABLED) TimeUnit.DAYS.toMillis(90) else Long.MAX_VALUE

fun getUnusedThresholdMs(context: Context) = when {
    DEBUG_OVERRIDE_THRESHOLDS -> TimeUnit.SECONDS.toMillis(1)
    else -> DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
            PolicyUtils.PROPERTY_AUTO_REVOKE_UNUSED_THRESHOLD_MILLIS,
            DEFAULT_UNUSED_THRESHOLD_MS)
}

private val DEFAULT_CHECK_FREQUENCY_MS = TimeUnit.DAYS.toMillis(15)
private fun getCheckFrequencyMs(context: Context) = DeviceConfig.getLong(
    DeviceConfig.NAMESPACE_PERMISSIONS,
        PolicyUtils.PROPERTY_AUTO_REVOKE_CHECK_FREQUENCY_MILLIS,
        DEFAULT_CHECK_FREQUENCY_MS)

private val PREF_KEY_FIRST_BOOT_TIME = "first_boot_time"

fun isAutoRevokeEnabled(context: Context): Boolean {
    return getCheckFrequencyMs(context) > 0 &&
            getUnusedThresholdMs(context) > 0 &&
            getUnusedThresholdMs(context) != Long.MAX_VALUE
}

/**
 * Receiver of the onBoot event.
 */
class AutoRevokeOnBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Init firstBootTime
        val firstBootTime = context.firstBootTime

        if (DEBUG_AUTO_REVOKE_POLICY) {
            DumpableLog.i(LOG_TAG, "scheduleAutoRevokePermissions " +
                    "with frequency ${getCheckFrequencyMs(context)}ms " +
                    "and threshold ${getUnusedThresholdMs(context)}ms")
        }

        val userManager = context.getSystemService(UserManager::class.java)!!
        // If this user is a profile, then its auto revoke will be handled by the primary user
        if (userManager.isProfile) {
            if (DEBUG_AUTO_REVOKE_POLICY) {
                DumpableLog.i(LOG_TAG, "user ${Process.myUserHandle().identifier} is a profile. " +
                        "Not running Auto Revoke.")
            }
            return
        } else if (DEBUG_AUTO_REVOKE_POLICY) {
            DumpableLog.i(LOG_TAG, "user ${Process.myUserHandle().identifier} is a profile " +
                    "owner. Running Auto Revoke.")
        }

        SKIP_NEXT_RUN = true

        val jobInfo = JobInfo.Builder(
                Constants.AUTO_REVOKE_JOB_ID,
                ComponentName(context, AutoRevokeService::class.java))
            .setPeriodic(getCheckFrequencyMs(context))
            .build()
        val status = context.getSystemService(JobScheduler::class.java)!!.schedule(jobInfo)
        if (status != JobScheduler.RESULT_SUCCESS) {
            DumpableLog.e(LOG_TAG,
                    "Could not schedule ${AutoRevokeService::class.java.simpleName}: $status")
        }
    }
}

/**
 * Gets apps that are unused and auto-revocable as a map of the user and their revocable apps.
 */
@MainThread
private suspend fun getRevocableApps(
    context: Context
):
    Map<UserHandle, List<String>> {
    if (!isAutoRevokeEnabled(context)) {
        return emptyMap()
    }

    val now = System.currentTimeMillis()
    val firstBootTime = context.firstBootTime

    // TODO ntmyren: remove once b/154796729 is fixed
    Log.i(LOG_TAG, "getting UserPackageInfoLiveData for all users " +
            "in AutoRevokePermissions")
    val allPackagesByUser = AllPackageInfosLiveData.getInitializedValue(forceUpdate = true)
    val allPackagesByUserByUid = allPackagesByUser.mapValues { (_, pkgs) ->
        pkgs.groupBy { pkg -> pkg.uid }
    }
    val unusedApps = allPackagesByUser.toMutableMap()

    val userStats = UsageStatsLiveData[getUnusedThresholdMs(context),
        if (DEBUG_OVERRIDE_THRESHOLDS) UsageStatsManager.INTERVAL_DAILY
        else UsageStatsManager.INTERVAL_MONTHLY].getInitializedValue()
    if (DEBUG_AUTO_REVOKE_POLICY) {
        for ((user, stats) in userStats) {
            DumpableLog.i(LOG_TAG, "Usage stats for user ${user.identifier}: " +
                    stats.map { stat ->
                        stat.packageName to Date(stat.lastTimeVisible)
                    }.toMap())
        }
    }
    for (user in unusedApps.keys.toList()) {
        if (user !in userStats.keys) {
            if (DEBUG_AUTO_REVOKE_POLICY) {
                DumpableLog.i(LOG_TAG, "Ignoring user ${user.identifier}")
            }
            unusedApps.remove(user)
        }
    }

    for ((user, stats) in userStats) {
        var unusedUserApps = unusedApps[user] ?: continue

        unusedUserApps = unusedUserApps.filter { packageInfo ->
            val pkgName = packageInfo.packageName

            val uidPackages = allPackagesByUserByUid[user]!![packageInfo.uid]
                    ?.map { info -> info.packageName } ?: emptyList()
            if (pkgName !in uidPackages) {
                Log.wtf(LOG_TAG, "Package $pkgName not among packages for " +
                        "its uid ${packageInfo.uid}: $uidPackages")
            }
            var lastTimeVisible: Long = stats.lastTimeVisible(uidPackages)

            // Limit by install time
            lastTimeVisible = Math.max(lastTimeVisible, packageInfo.firstInstallTime)

            // Limit by first boot time
            lastTimeVisible = Math.max(lastTimeVisible, firstBootTime)

            // Handle cross-profile apps
            if (context.isPackageCrossProfile(pkgName)) {
                for ((otherUser, otherStats) in userStats) {
                    if (otherUser == user) {
                        continue
                    }
                    lastTimeVisible = Math.max(lastTimeVisible, otherStats.lastTimeVisible(pkgName))
                }
            }

            // Threshold check - whether app is unused
            now - lastTimeVisible > getUnusedThresholdMs(context)
        }

        unusedApps[user] = unusedUserApps
        if (DEBUG_AUTO_REVOKE_POLICY) {
            DumpableLog.i(LOG_TAG, "Unused apps for user ${user.identifier}: " +
                    "${unusedUserApps.map { it.packageName }}")
        }
    }

    val revocableApps = mutableMapOf<UserHandle, List<String>>()
    val userManager = context.getSystemService(UserManager::class.java)
    for ((user, userApps) in unusedApps) {
        if (userManager == null || !userManager.isUserUnlocked(user)) {
            DumpableLog.w(LOG_TAG, "Skipping $user - locked direct boot state")
            continue
        }
        var userRevocableApps = mutableListOf<String>()
        userApps.forEachInParallel(Main) { pkg: LightPackageInfo ->
            if (pkg.grantedPermissions.isEmpty()) {
                return@forEachInParallel
            }

            if (isPackageAutoRevokePermanentlyExempt(pkg, user)) {
                return@forEachInParallel
            }

            if (isPackageAutoRevokeExempt(context, pkg)) {
                return@forEachInParallel
            }

            if (DEBUG_AUTO_REVOKE_POLICY) {
                var packageName = pkg.packageName
                DumpableLog.i(LOG_TAG, "unused app $packageName - lastVisible on " +
                    userStats[user]?.lastTimeVisible(packageName)?.let(::Date))
            }

            synchronized(userRevocableApps) {
                userRevocableApps.add(pkg.packageName)
            }
        }
        revocableApps.put(user, userRevocableApps)
    }
    return revocableApps
}

private fun List<UsageStats>.lastTimeVisible(pkgNames: List<String>): Long {
    var result = 0L
    for (stat in this) {
        if (stat.packageName in pkgNames) {
            result = Math.max(result, stat.lastTimeVisible)
        }
    }
    return result
}

private fun List<UsageStats>.lastTimeVisible(pkgName: String): Long {
    return lastTimeVisible(listOf(pkgName))
}

/**
 * Checks if the given package is exempt from auto revoke in a way that's not user-overridable
 */
suspend fun isPackageAutoRevokePermanentlyExempt(
    pkg: LightPackageInfo,
    user: UserHandle
): Boolean {
    if (!ExemptServicesLiveData[user]
            .getInitializedValue()[pkg.packageName]
            .isNullOrEmpty()) {
        return true
    }
    if (Utils.isUserDisabledOrWorkProfile(user)) {
        if (DEBUG_AUTO_REVOKE_POLICY) {
            DumpableLog.i(LOG_TAG,
                    "Exempted ${pkg.packageName} - $user is disabled or a work profile")
        }
        return true
    }
    val carrierPrivilegedStatus = CarrierPrivilegedStatusLiveData[pkg.packageName]
            .getInitializedValue()
    if (carrierPrivilegedStatus != CARRIER_PRIVILEGE_STATUS_HAS_ACCESS &&
            carrierPrivilegedStatus != CARRIER_PRIVILEGE_STATUS_NO_ACCESS) {
        DumpableLog.w(LOG_TAG, "Error carrier privileged status for ${pkg.packageName}: " +
                carrierPrivilegedStatus)
    }
    if (carrierPrivilegedStatus == CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
        if (DEBUG_AUTO_REVOKE_POLICY) {
            DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - carrier privileged")
        }
        return true
    }

    if (PermissionControllerApplication.get()
            .packageManager
            .checkPermission(
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                    pkg.packageName) == PERMISSION_GRANTED) {
        if (DEBUG_AUTO_REVOKE_POLICY) {
            DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} " +
                    "- holder of READ_PRIVILEGED_PHONE_STATE")
        }
        return true
    }

    return false
}

/**
 * Checks if the given package is exempt from auto revoke in a way that's user-overridable
 */
suspend fun isPackageAutoRevokeExempt(
    context: Context,
    pkg: LightPackageInfo
): Boolean {
    val packageName = pkg.packageName
    val packageUid = pkg.uid

    val allowlistAppOpMode =
        AppOpLiveData[packageName,
            AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, packageUid]
            .getInitializedValue()
    if (allowlistAppOpMode == AppOpsManager.MODE_DEFAULT) {
        // Initial state - allowlist not explicitly overridden by either user or installer
        if (DEBUG_OVERRIDE_THRESHOLDS) {
            // Suppress exemptions to allow debugging
            return false
        }

        // Q- packages exempt by default, except R- on Auto since Auto-Revoke was skipped in R
        val maxTargetSdkVersionForExemptApps =
                if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                    android.os.Build.VERSION_CODES.R
                } else {
                    android.os.Build.VERSION_CODES.Q
                }

        return pkg.targetSdkVersion <= maxTargetSdkVersionForExemptApps
    }
    // Check whether user/installer exempt
    return allowlistAppOpMode != AppOpsManager.MODE_ALLOWED
}

private fun Context.isPackageCrossProfile(pkg: String): Boolean {
    return packageManager.checkPermission(
        Manifest.permission.INTERACT_ACROSS_PROFILES, pkg) == PERMISSION_GRANTED ||
        packageManager.checkPermission(
            Manifest.permission.INTERACT_ACROSS_USERS, pkg) == PERMISSION_GRANTED ||
        packageManager.checkPermission(
            Manifest.permission.INTERACT_ACROSS_USERS_FULL, pkg) == PERMISSION_GRANTED
}

private fun Context.forUser(user: UserHandle): Context {
    return Utils.getUserContext(application, user)
}

private fun Context.forParentUser(): Context {
    return Utils.getParentUserContext(this)
}

private inline fun <reified T> Context.getSystemService() = getSystemService(T::class.java)!!

val Context.sharedPreferences: SharedPreferences
    get() {
    return PreferenceManager.getDefaultSharedPreferences(this)
}

private val Context.firstBootTime: Long get() {
    var time = sharedPreferences.getLong(PREF_KEY_FIRST_BOOT_TIME, -1L)
    if (time > 0) {
        return time
    }
    // This is the first boot
    time = System.currentTimeMillis()
    sharedPreferences.edit().putLong(PREF_KEY_FIRST_BOOT_TIME, time).apply()
    return time
}

/**
 * A job to check for apps unused in the last [getUnusedThresholdMs]ms every
 * [getCheckFrequencyMs]ms and revoke their permissions.
 */
class AutoRevokeService : JobService() {
    var job: Job? = null
    var jobStartTime: Long = -1L

    override fun onStartJob(params: JobParameters?): Boolean {
        if (DEBUG_AUTO_REVOKE_POLICY) {
            DumpableLog.i(LOG_TAG, "onStartJob")
        }

        if (SKIP_NEXT_RUN) {
            SKIP_NEXT_RUN = false
            if (DEBUG_AUTO_REVOKE_POLICY) {
                Log.i(LOG_TAG, "Skipping auto revoke first run when scheduled by system")
            }
            jobFinished(params, false)
            return true
        }

        jobStartTime = System.currentTimeMillis()
        job = GlobalScope.launch(Main) {
            try {
                var sessionId = Constants.INVALID_SESSION_ID
                while (sessionId == Constants.INVALID_SESSION_ID) {
                    sessionId = Random().nextLong()
                }

                val revocableApps = getRevocableApps(this@AutoRevokeService)
                val revokedApps = revokeAppPermissions(
                        revocableApps, this@AutoRevokeService, sessionId)
                if (revokedApps.isNotEmpty()) {
                    showAutoRevokeNotification(sessionId)
                }
            } catch (e: Exception) {
                DumpableLog.e(LOG_TAG, "Failed to auto-revoke permissions", e)
            }
            jobFinished(params, false)
        }
        return true
    }

    private suspend fun showAutoRevokeNotification(sessionId: Long) {
        val notificationManager = getSystemService(NotificationManager::class.java)!!

        val permissionReminderChannel = NotificationChannel(
                Constants.PERMISSION_REMINDER_CHANNEL_ID, getString(R.string.permission_reminders),
                NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(permissionReminderChannel)

        val clickIntent = Intent(this, ManagePermissionsActivity::class.java).apply {
            action = Constants.ACTION_MANAGE_AUTO_REVOKE
            putExtra(Constants.EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, clickIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)

        val b = Notification.Builder(this, Constants.PERMISSION_REMINDER_CHANNEL_ID)
            .setContentTitle(getString(R.string.auto_revoke_permission_notification_title))
            .setContentText(getString(
                R.string.auto_revoke_permission_notification_content))
            .setStyle(Notification.BigTextStyle().bigText(getString(
                R.string.auto_revoke_permission_notification_content)))
            .setSmallIcon(R.drawable.ic_settings_24dp)
            .setColor(getColor(android.R.color.system_notification_accent_color))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .extend(Notification.TvExtender())
        Utils.getSettingsLabelForNotifications(applicationContext.packageManager)?.let {
            settingsLabel ->
            val extras = Bundle()
            extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, settingsLabel.toString())
            b.addExtras(extras)
        }

        notificationManager.notify(AutoRevokeService::class.java.simpleName,
                Constants.AUTO_REVOKE_NOTIFICATION_ID, b.build())
        // Preload the auto revoked packages
        UnusedAutoRevokedPackagesLiveData.getInitializedValue()
    }

    companion object {
        const val SHOW_AUTO_REVOKE = "showAutoRevoke"
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        DumpableLog.w(LOG_TAG, "onStopJob after ${System.currentTimeMillis() - jobStartTime}ms")
        job?.cancel()
        return true
    }
}

/**
 * Packages using exempt services for the current user (package-name -> list<service-interfaces>
 * implemented by the package)
 */
class ExemptServicesLiveData(val user: UserHandle)
    : SmartUpdateMediatorLiveData<Map<String, List<String>>>() {
    private val serviceLiveDatas: List<SmartUpdateMediatorLiveData<Set<String>>> = listOf(
            ServiceLiveData[InputMethod.SERVICE_INTERFACE,
                    Manifest.permission.BIND_INPUT_METHOD,
                    user],
            ServiceLiveData[
                    NotificationListenerService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                    user],
            ServiceLiveData[
                    AccessibilityService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
                    user],
            ServiceLiveData[
                    WallpaperService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_WALLPAPER,
                    user],
            ServiceLiveData[
                    VoiceInteractionService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_VOICE_INTERACTION,
                    user],
            ServiceLiveData[
                    PrintService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_PRINT_SERVICE,
                    user],
            ServiceLiveData[
                    DreamService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_DREAM_SERVICE,
                    user],
            ServiceLiveData[
                    AutofillService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_AUTOFILL_SERVICE,
                    user],
            ServiceLiveData[
                    DevicePolicyManager.ACTION_DEVICE_ADMIN_SERVICE,
                    Manifest.permission.BIND_DEVICE_ADMIN,
                    user],
            BroadcastReceiverLiveData[
                    DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED,
                    Manifest.permission.BIND_DEVICE_ADMIN,
                    user]
    )

    init {
        serviceLiveDatas.forEach { addSource(it) { update() } }
    }

    override fun onUpdate() {
        if (serviceLiveDatas.all { it.isInitialized }) {
            val pksToServices = mutableMapOf<String, MutableList<String>>()

            serviceLiveDatas.forEach { serviceLD ->
                serviceLD.value!!.forEach { packageName ->
                    pksToServices.getOrPut(packageName, { mutableListOf() })
                            .add((serviceLD as? HasIntentAction)?.intentAction ?: "???")
                }
            }

            value = pksToServices
        }
    }

    /**
     * Repository for ExemptServiceLiveData
     *
     * <p> Key value is user
     */
    companion object : DataRepositoryForPackage<UserHandle, ExemptServicesLiveData>() {
        override fun newValue(key: UserHandle): ExemptServicesLiveData {
            return ExemptServicesLiveData(key)
        }
    }
}
