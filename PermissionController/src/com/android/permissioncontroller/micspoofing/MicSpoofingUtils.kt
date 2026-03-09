@file:JvmName("MicSpoofingUtils")

package com.android.permissioncontroller.micspoofing

import android.content.pm.GosPackageState
import android.content.pm.GosPackageStateFlag
import android.os.UserHandle

fun isMicSpoofingEnabled(packageName: String, user: UserHandle): Boolean {
    return isMicSpoofingEnabled(GosPackageState.get(packageName, user))
}

fun isMicSpoofingEnabled(packageState: GosPackageState): Boolean {
    return packageState.hasFlag(GosPackageStateFlag.MIC_SPOOFING_ENABLED)
}
