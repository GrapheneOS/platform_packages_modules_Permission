/*
 * Copyright (C) 2020 The Android Open Source Project
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

@file:JvmName("AutoRevokePermissions")

package com.android.permissioncontroller.permission.service

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
import android.app.usage.UsageStatsManager.INTERVAL_DAILY
import android.app.usage.UsageStatsManager.INTERVAL_MONTHLY
import android.content.Context
import android.content.pm.PackageManager.FLAG_PERMISSION_AUTO_REVOKED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.MainThread
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED
import com.android.permissioncontroller.hibernation.DEBUG_OVERRIDE_THRESHOLDS
import com.android.permissioncontroller.hibernation.ExemptServicesLiveData
import com.android.permissioncontroller.hibernation.getUnusedThresholdMs
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.UnusedAutoRevokedPackagesLiveData
import com.android.permissioncontroller.permission.data.UsageStatsLiveData
import com.android.permissioncontroller.permission.data.UsersLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.service.AutoRevokePermissionsProto.AutoRevokePermissionsDumpProto
import com.android.permissioncontroller.permission.service.AutoRevokePermissionsProto.PackageProto
import com.android.permissioncontroller.permission.service.AutoRevokePermissionsProto.PerUserProto
import com.android.permissioncontroller.permission.service.AutoRevokePermissionsProto.PermissionGroupProto
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.application
import com.android.permissioncontroller.permission.utils.forEachInParallel
import com.android.permissioncontroller.permission.utils.updatePermissionFlags
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicBoolean

private const val LOG_TAG = "AutoRevokePermissions"
const val DEBUG_AUTO_REVOKE = true

private val EXEMPT_PERMISSIONS = listOf(
        android.Manifest.permission.ACTIVITY_RECOGNITION)

private val SERVER_LOG_ID =
    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED

/**
 * @return dump of auto revoke service as a proto
 */
suspend fun dumpAutoRevokePermissions(context: Context): AutoRevokePermissionsDumpProto {

    val dumpData = GlobalScope.async(IPC) {
        AutoRevokeDumpLiveData(context).getInitializedValue()
    }

    return AutoRevokePermissionsDumpProto.newBuilder()
            .addAllUsers(dumpData.await().dumpUsers())
            .build()
}

/**
 * Revoke granted app permissions for apps that should be auto-revoked
 *
 * @return list of packages that successfully had their permissions revoked
 */
@MainThread
suspend fun revokeAppPermissions(
    apps: Map<UserHandle, List<String>>,
    context: Context,
    sessionId: Long = INVALID_SESSION_ID
): List<Pair<String, UserHandle>> {
    val revokedApps = mutableListOf<Pair<String, UserHandle>>()
    val userManager = context.getSystemService(UserManager::class.java)

    for ((user, userApps) in apps) {
        if (userManager == null || !userManager.isUserUnlocked(user)) {
            DumpableLog.w(LOG_TAG, "Skipping $user - locked direct boot state")
            continue
        }
        userApps.forEachInParallel(Main) { packageName: String ->
            val anyPermsRevoked = AtomicBoolean(false)
            val pkgPermGroups: Map<String, List<String>>? =
                PackagePermissionsLiveData[packageName, user]
                    .getInitializedValue()

            pkgPermGroups?.entries?.forEachInParallel(Main) { (groupName, _) ->
                if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS ||
                    groupName !in Utils.getPlatformPermissionGroups()) {
                    return@forEachInParallel
                }

                val group: LightAppPermGroup =
                    LightAppPermGroupLiveData[packageName, groupName, user]
                        .getInitializedValue()
                        ?: return@forEachInParallel

                val fixed = group.isBackgroundFixed || group.isForegroundFixed
                val granted = group.permissions.any { (_, perm) ->
                    perm.isGrantedIncludingAppOp && perm.name !in EXEMPT_PERMISSIONS
                }
                if (!fixed &&
                    granted &&
                    !group.isGrantedByDefault &&
                    !group.isGrantedByRole &&
                    group.isUserSensitive) {

                    val revocablePermissions = group.permissions.keys.toList()

                    if (revocablePermissions.isEmpty()) {
                        return@forEachInParallel
                    }

                    if (DEBUG_AUTO_REVOKE) {
                        DumpableLog.i(LOG_TAG,
                                "revokeUnused $packageName - $revocablePermissions")
                    }

                    val uid = group.packageInfo.uid
                    for (permName in revocablePermissions) {
                        PermissionControllerStatsLog.write(
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED,
                            sessionId, uid, packageName, permName, false, SERVER_LOG_ID)
                    }

                    val packageImportance = context
                        .getSystemService(ActivityManager::class.java)!!
                        .getPackageImportance(packageName)
                    if (packageImportance > IMPORTANCE_TOP_SLEEPING) {
                        if (DEBUG_AUTO_REVOKE) {
                            DumpableLog.i(LOG_TAG, "revoking $packageName - $revocablePermissions")
                            DumpableLog.i(LOG_TAG, "State pre revocation: ${group.allPermissions}")
                        }
                        anyPermsRevoked.compareAndSet(false, true)

                        val bgRevokedState = KotlinUtils.revokeBackgroundRuntimePermissions(
                                context.application, group,
                                userFixed = false, oneTime = false,
                                filterPermissions = revocablePermissions)
                        if (DEBUG_AUTO_REVOKE) {
                            DumpableLog.i(LOG_TAG,
                                "Bg state post revocation: ${bgRevokedState.allPermissions}")
                        }
                        val fgRevokedState = KotlinUtils.revokeForegroundRuntimePermissions(
                            context.application, group,
                            userFixed = false, oneTime = false,
                            filterPermissions = revocablePermissions)
                        if (DEBUG_AUTO_REVOKE) {
                            DumpableLog.i(LOG_TAG,
                                "Fg state post revocation: ${fgRevokedState.allPermissions}")
                        }

                        for (permission in revocablePermissions) {
                            context.packageManager.updatePermissionFlags(
                                permission, packageName, user,
                                FLAG_PERMISSION_AUTO_REVOKED to true,
                                FLAG_PERMISSION_USER_SET to false)
                        }
                    } else {
                        DumpableLog.i(LOG_TAG,
                            "Skipping auto-revoke - $packageName running with importance " +
                                "$packageImportance")
                    }
                }
            }

            if (anyPermsRevoked.get()) {
                synchronized(revokedApps) {
                    revokedApps.add(packageName to user)
                }
            }
        }
        if (DEBUG_AUTO_REVOKE) {
            synchronized(revokedApps) {
                DumpableLog.i(LOG_TAG,
                        "Done auto-revoke for user ${user.identifier} - revoked $revokedApps")
            }
        }
    }
    return revokedApps
}

/** Data interesting to auto-revoke */
private class AutoRevokeDumpLiveData(context: Context) :
        SmartUpdateMediatorLiveData<AutoRevokeDumpLiveData.AutoRevokeDumpData>() {
    /** All data */
    data class AutoRevokeDumpData(
        val users: List<AutoRevokeDumpUserData>
    ) {
        fun dumpUsers(): List<PerUserProto> {
            return users.map { it.dump() }
        }
    }

    /** Per user data */
    data class AutoRevokeDumpUserData(
        val user: UserHandle,
        val pkgs: List<AutoRevokeDumpPackageData>
    ) {
        fun dump(): PerUserProto {
            val dump = PerUserProto.newBuilder()
                    .setUserId(user.identifier)

            pkgs.forEach { dump.addPackages(it.dump()) }

            return dump.build()
        }
    }

    /** Per package data */
    data class AutoRevokeDumpPackageData(
        val uid: Int,
        val packageName: String,
        val firstInstallTime: Long,
        val lastTimeVisible: Long?,
        val implementedServices: List<String>,
        val groups: List<AutoRevokeDumpGroupData>
    ) {
        fun dump(): PackageProto {
            val dump = PackageProto.newBuilder()
                    .setUid(uid)
                    .setPackageName(packageName)
                    .setFirstInstallTime(firstInstallTime)

            lastTimeVisible?.let { dump.lastTimeVisible = lastTimeVisible }

            implementedServices.forEach { dump.addImplementedServices(it) }

            groups.forEach { dump.addGroups(it.dump()) }

            return dump.build()
        }
    }

    /** Per permission group data */
    data class AutoRevokeDumpGroupData(
        val groupName: String,
        val isFixed: Boolean,
        val isAnyGrantedIncludingAppOp: Boolean,
        val isGrantedByDefault: Boolean,
        val isGrantedByRole: Boolean,
        val isUserSensitive: Boolean,
        val isAutoRevoked: Boolean
    ) {
        fun dump(): PermissionGroupProto {
            return PermissionGroupProto.newBuilder()
                    .setGroupName(groupName)
                    .setIsFixed(isFixed)
                    .setIsAnyGrantedIncludingAppop(isAnyGrantedIncludingAppOp)
                    .setIsGrantedByDefault(isGrantedByDefault)
                    .setIsGrantedByRole(isGrantedByRole)
                    .setIsUserSensitive(isUserSensitive)
                    .setIsAutoRevoked(isAutoRevoked)
                    .build()
        }
    }

    /** All users */
    private val users = UsersLiveData

    /** Exempt services for each user: user -> services */
    private var services: MutableMap<UserHandle, ExemptServicesLiveData>? = null

    /** Usage stats: user -> list<usages> */
    private val usages = UsageStatsLiveData[
        getUnusedThresholdMs(),
        if (DEBUG_OVERRIDE_THRESHOLDS) INTERVAL_DAILY else INTERVAL_MONTHLY
    ]

    /** All package infos: user -> pkg **/
    private val packages = AllPackageInfosLiveData

    /** Group names of revoked permission groups: (user, pkg-name) -> set<group-name> **/
    private val revokedPermGroupNames = UnusedAutoRevokedPackagesLiveData

    /**
     * Group names for packages
     * map<user, pkg-name> -> list<perm-group-name>. {@code null} before step 1
     */
    private var pkgPermGroupNames:
            MutableMap<Pair<UserHandle, String>, PackagePermissionsLiveData>? = null

    /**
     * Group state for packages
     * map<(user, pkg-name) -> map<perm-group-name -> group>>, value {@code null} before step 2
     */
    private val pkgPermGroups =
            mutableMapOf<Pair<UserHandle, String>,
                    MutableMap<String, LightAppPermGroupLiveData>?>()

    /** If this live-data currently inside onUpdate */
    private var isUpdating = false

    init {
        addSource(revokedPermGroupNames) {
            update()
        }

        addSource(users) {
            services?.values?.forEach { removeSource(it) }
            services = null

            update()
        }

        addSource(usages) {
            update()
        }

        addSource(packages) {
            pkgPermGroupNames?.values?.forEach { removeSource(it) }
            pkgPermGroupNames = null
            pkgPermGroups.values.forEach { it?.values?.forEach { removeSource(it) } }

            update()
        }
    }

    override fun onUpdate() {
        // If a source is already ready, the call onUpdate when added. Suppress this
        if (isUpdating) {
            return
        }
        isUpdating = true

        // services/autoRevokeManifestExemptPackages step 1, users is loaded, nothing else
        if (users.isInitialized && services == null) {
            services = mutableMapOf()

            for (user in users.value!!) {
                val newServices = ExemptServicesLiveData[user]
                services!![user] = newServices

                addSource(newServices) {
                    update()
                }
            }
        }

        // pkgPermGroupNames step 1, packages is loaded, nothing else
        if (packages.isInitialized && pkgPermGroupNames == null) {
            pkgPermGroupNames = mutableMapOf()

            for ((user, userPkgs) in packages.value!!) {
                for (pkg in userPkgs) {
                    val newPermGroupNames = PackagePermissionsLiveData[pkg.packageName, user]
                    pkgPermGroupNames!![user to pkg.packageName] = newPermGroupNames

                    addSource(newPermGroupNames) {
                        pkgPermGroups[user to pkg.packageName]?.forEach { removeSource(it.value) }
                        pkgPermGroups.remove(user to pkg.packageName)

                        update()
                    }
                }
            }
        }

        // pkgPermGroupNames step 2, packages and pkgPermGroupNames are loaded, but pkgPermGroups
        // are not loaded yet
        if (packages.isInitialized && pkgPermGroupNames != null) {
            for ((user, userPkgs) in packages.value!!) {
                for (pkg in userPkgs) {
                    if (pkgPermGroupNames!![user to pkg.packageName]?.isInitialized == true &&
                            pkgPermGroups[user to pkg.packageName] == null) {
                        pkgPermGroups[user to pkg.packageName] = mutableMapOf()

                        for (groupName in
                                pkgPermGroupNames!![user to pkg.packageName]!!.value!!.keys) {
                            if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                                continue
                            }

                            val newPkgPermGroup = LightAppPermGroupLiveData[pkg.packageName,
                                    groupName, user]

                            pkgPermGroups[user to pkg.packageName]!![groupName] = newPkgPermGroup

                            addSource(newPkgPermGroup) { update() }
                        }
                    }
                }
            }
        }

        // Final step, everything is loaded, generate data
        if (packages.isInitialized && usages.isInitialized && revokedPermGroupNames.isInitialized &&
                pkgPermGroupNames?.values?.all { it.isInitialized } == true &&
                pkgPermGroupNames?.size == pkgPermGroups.size &&
                pkgPermGroups.values.all { it?.values?.all { it.isInitialized } == true } &&
                services?.values?.all { it.isInitialized } == true) {
            val users = mutableListOf<AutoRevokeDumpUserData>()

            for ((user, userPkgs) in packages.value!!) {
                val pkgs = mutableListOf<AutoRevokeDumpPackageData>()

                for (pkg in userPkgs) {
                    val groups = mutableListOf<AutoRevokeDumpGroupData>()

                    for (groupName in pkgPermGroupNames!![user to pkg.packageName]!!.value!!.keys) {
                        if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                            continue
                        }

                        pkgPermGroups[user to pkg.packageName]?.let {
                            it[groupName]?.value?.apply {
                                groups.add(AutoRevokeDumpGroupData(groupName,
                                        isBackgroundFixed || isForegroundFixed,
                                        permissions.any { (_, p) -> p.isGrantedIncludingAppOp },
                                        isGrantedByDefault,
                                        isGrantedByRole,
                                        isUserSensitive,
                                    revokedPermGroupNames.value?.let {
                                        it[pkg.packageName to user]
                                            ?.contains(groupName)
                                    } == true
                                ))
                            }
                        }
                    }

                    pkgs.add(AutoRevokeDumpPackageData(pkg.uid, pkg.packageName,
                            pkg.firstInstallTime,
                            null,
                            services!![user]?.value!![pkg.packageName] ?: emptyList(),
                            groups))
                }

                users.add(AutoRevokeDumpUserData(user, pkgs))
            }

            value = AutoRevokeDumpData(users)
        }

        isUpdating = false
    }
}
