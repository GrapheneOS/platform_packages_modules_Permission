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

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.telephony.TelephonyManager
import com.android.permissioncontroller.PermissionControllerApplication

/**
 * A LiveData which represents the carrier privileged status for a package
 *
 * @param app The current application
 * @param packageName The name of the package
 */
class CarrierPrivilegedStatusLiveData
private constructor(private val app: Application, private val packageName: String) :
    SmartUpdateMediatorLiveData<Int>() {

    private val telephonyManager = app.getSystemService(TelephonyManager::class.java)!!

    override fun onUpdate() {
        value = telephonyManager.checkCarrierPrivilegesForPackageAnyPhone(packageName)
    }

    override fun onActive() {
        super.onActive()
        update()
    }

    /**
     * Repository for [CarrierPrivilegedStatusLiveData].
     *
     * <p> Key value is a package name, value is its corresponding LiveData of
     * [android.telephony.Annotation.CarrierPrivilegeStatus]
     */
    companion object : DataRepository<String, CarrierPrivilegedStatusLiveData>() {
        override fun newValue(key: String): CarrierPrivilegedStatusLiveData {
            return CarrierPrivilegedStatusLiveData(PermissionControllerApplication.get(), key)
        }
    }
}
