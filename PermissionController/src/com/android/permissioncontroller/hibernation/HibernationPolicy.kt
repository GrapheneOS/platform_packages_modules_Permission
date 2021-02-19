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
import android.app.usage.UsageStatsManager.INTERVAL_DAILY
import android.app.usage.UsageStatsManager.INTERVAL_MONTHLY
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
const val DEBUG_HIBERNATION_POLICY = true || DEBUG_OVERRIDE_THRESHOLDS

// TODO(b/175830282): Add SDK check when platform SDK moves up
private val HIBERNATION_ENABLED =
        DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_APP_HIBERNATION,
        Utils.PROPERTY_APP_HIBERNATION_ENABLED, false)
private const val AUTO_REVOKE_ENABLED = true

private var SKIP_NEXT_RUN = false

private val DEFAULT_UNUSED_THRESHOLD_MS =
        if (HIBERNATION_ENABLED || AUTO_REVOKE_ENABLED)
            TimeUnit.DAYS.toMillis(90) else Long.MAX_VALUE

fun getUnusedThresholdMs() = when {
    DEBUG_OVERRIDE_THRESHOLDS -> TimeUnit.SECONDS.toMillis(1)
    else -> DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
            Utils.PROPERTY_HIBERNATION_UNUSED_THRESHOLD_MILLIS,
            DEFAULT_UNUSED_THRESHOLD_MS)
}

private val DEFAULT_CHECK_FREQUENCY_MS = TimeUnit.DAYS.toMillis(15)

private fun getCheckFrequencyMs() = DeviceConfig.getLong(
    DeviceConfig.NAMESPACE_PERMISSIONS,
        Utils.PROPERTY_HIBERNATION_CHECK_FREQUENCY_MILLIS,
        DEFAULT_CHECK_FREQUENCY_MS)

private val PREF_KEY_FIRST_BOOT_TIME = "first_boot_time"

fun isHibernationJobEnabled(): Boolean {
    return getCheckFrequencyMs() > 0 &&
            getUnusedThresholdMs() > 0 &&
            getUnusedThresholdMs() != Long.MAX_VALUE
}

/**
 * Receiver of the onBoot event.
 */
class HibernationOnBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "scheduleHibernationJob " +
                    "with frequency ${getCheckFrequencyMs()}ms " +
                    "and threshold ${getUnusedThresholdMs()}ms")
        }

        val userManager = context.getSystemService(UserManager::class.java)!!
        // If this user is a profile, then its hibernation/auto-revoke will be handled by the
        // primary user
        if (userManager.isProfile) {
            if (DEBUG_HIBERNATION_POLICY) {
                DumpableLog.i(LOG_TAG, "user ${Process.myUserHandle().identifier} is a profile." +
                        " Not running hibernation job.")
            }
            return
        } else if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "user ${Process.myUserHandle().identifier} is a profile" +
                    "owner. Running hibernation job.")
        }

        SKIP_NEXT_RUN = true

        val jobInfo = JobInfo.Builder(
                Constants.HIBERNATION_JOB_ID,
                ComponentName(context, HibernationJobService::class.java))
            .setPeriodic(getCheckFrequencyMs())
            .build()
        val status = context.getSystemService(JobScheduler::class.java)!!.schedule(jobInfo)
        if (status != JobScheduler.RESULT_SUCCESS) {
            DumpableLog.e(LOG_TAG,
                    "Could not schedule ${HibernationJobService::class.java.simpleName}: $status")
        }
    }
}

/**
 * Gets apps that are unused and should hibernate as a map of the user and their hibernateable apps.
 */
@MainThread
private suspend fun getAppsToHibernate(
    context: Context
): Map<UserHandle, List<LightPackageInfo>> {
    if (!isHibernationJobEnabled()) {
        return emptyMap()
    }

    val now = System.currentTimeMillis()
    val firstBootTime = context.firstBootTime

    // TODO ntmyren: remove once b/154796729 is fixed
    Log.i(LOG_TAG, "getting UserPackageInfoLiveData for all users " +
            "in " + HibernationJobService::class.java.simpleName)
    val allPackagesByUser = AllPackageInfosLiveData.getInitializedValue(forceUpdate = true)
    val allPackagesByUserByUid = allPackagesByUser.mapValues { (_, pkgs) ->
        pkgs.groupBy { pkg -> pkg.uid }
    }
    val unusedApps = allPackagesByUser.toMutableMap()

    val userStats = UsageStatsLiveData[getUnusedThresholdMs(),
        if (DEBUG_OVERRIDE_THRESHOLDS) INTERVAL_DAILY else INTERVAL_MONTHLY].getInitializedValue()
    if (DEBUG_HIBERNATION_POLICY) {
        for ((user, stats) in userStats) {
            DumpableLog.i(LOG_TAG, "Usage stats for user ${user.identifier}: " +
                    stats.map { stat ->
                        stat.packageName to Date(stat.lastTimeVisible)
                    }.toMap())
        }
    }
    for (user in unusedApps.keys.toList()) {
        if (user !in userStats.keys) {
            if (DEBUG_HIBERNATION_POLICY) {
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
            now - lastTimeVisible > getUnusedThresholdMs()
        }

        unusedApps[user] = unusedUserApps
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "Unused apps for user ${user.identifier}: " +
                    "${unusedUserApps.map { it.packageName }}")
        }
    }

    val appsToHibernate = mutableMapOf<UserHandle, List<LightPackageInfo>>()
    val userManager = context.getSystemService(UserManager::class.java)
    for ((user, userApps) in unusedApps) {
        if (userManager == null || !userManager.isUserUnlocked(user)) {
            DumpableLog.w(LOG_TAG, "Skipping $user - locked direct boot state")
            continue
        }
        var userAppsToHibernate = mutableListOf<LightPackageInfo>()
        userApps.forEachInParallel(Main) { pkg: LightPackageInfo ->
            if (isPackageHibernationExemptBySystem(pkg, user)) {
                return@forEachInParallel
            }

            if (isPackageHibernationExemptByUser(context, pkg)) {
                return@forEachInParallel
            }

            if (DEBUG_HIBERNATION_POLICY) {
                var packageName = pkg.packageName
                DumpableLog.i(LOG_TAG, "unused app $packageName - lastVisible on " +
                    userStats[user]?.lastTimeVisible(packageName)?.let(::Date))
            }

            synchronized(userAppsToHibernate) {
                userAppsToHibernate.add(pkg)
            }
        }
        appsToHibernate.put(user, userAppsToHibernate)
    }
    return appsToHibernate
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
 * Checks if the given package is exempt from hibernation in a way that's not user-overridable
 */
suspend fun isPackageHibernationExemptBySystem(
    pkg: LightPackageInfo,
    user: UserHandle
): Boolean {
    if (!ExemptServicesLiveData[user]
            .getInitializedValue()[pkg.packageName]
            .isNullOrEmpty()) {
        return true
    }
    if (Utils.isUserDisabledOrWorkProfile(user)) {
        if (DEBUG_HIBERNATION_POLICY) {
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
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - carrier privileged")
        }
        return true
    }

    if (PermissionControllerApplication.get()
            .packageManager
            .checkPermission(
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                    pkg.packageName) == PERMISSION_GRANTED) {
        if (DEBUG_HIBERNATION_POLICY) {
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
suspend fun isPackageHibernationExemptByUser(
    context: Context,
    pkg: LightPackageInfo
): Boolean {
    if (HIBERNATION_ENABLED) {
        // TODO(b/175830282): Hook into hibernation exemption list
        return false
    } else {
        return isPackageAutoRevokeExemptByUser(context, pkg)
    }
}

private suspend fun isPackageAutoRevokeExemptByUser(
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
 * [getCheckFrequencyMs]ms and hibernate the app / revoke their runtime permissions.
 */
class HibernationJobService : JobService() {
    var job: Job? = null
    var jobStartTime: Long = -1L

    override fun onStartJob(params: JobParameters?): Boolean {
        if (DEBUG_HIBERNATION_POLICY) {
            DumpableLog.i(LOG_TAG, "onStartJob")
        }

        if (SKIP_NEXT_RUN) {
            SKIP_NEXT_RUN = false
            if (DEBUG_HIBERNATION_POLICY) {
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

                val appsToHibernate = getAppsToHibernate(this@HibernationJobService)
                // TODO(b/175830282) Call system API to hibernate app here
                val revokedApps = revokeAppPermissions(
                        appsToHibernate, this@HibernationJobService, sessionId)
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

        notificationManager.notify(HibernationJobService::class.java.simpleName,
                Constants.AUTO_REVOKE_NOTIFICATION_ID, b.build())
        // Preload the auto revoked packages
        UnusedAutoRevokedPackagesLiveData.getInitializedValue()
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
