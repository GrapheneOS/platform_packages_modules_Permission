/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.permission.cts;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.OnPermissionsChangedListener;
import android.platform.test.annotations.AppModeFull;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "Instant apps cannot access properties of other apps")
@RunWith(AndroidJUnit4ClassRunner.class)
public class PermissionUpdateListenerTest {
    private static final String LOG_TAG = PermissionUpdateListenerTest.class.getSimpleName();
    private static final String APK =
            "/data/local/tmp/cts-permission/"
                    + "CtsAppThatRequestsCalendarContactsBodySensorCustomPermission.apk";
    private static final String PACKAGE_NAME =
            "android.permission.cts.appthatrequestcustompermission";
    private static final String PERMISSION_NAME = "android.permission.READ_CONTACTS";
    private static final int TIMEOUT = 10000;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private final PackageManager mPackageManager = mContext.getPackageManager();

    private int mTestAppUid;

    @Before
    public void setup() throws PackageManager.NameNotFoundException, InterruptedException {
        runShellCommandOrThrow("pm install " + APK);
        // permission change events are generated for a package install, the wait helps prevent
        // those permission change events interfere with the test.
        SystemUtil.waitForBroadcasts();
        Thread.sleep(1000);
        mTestAppUid = mPackageManager.getPackageUid(PACKAGE_NAME, 0);
    }

    @After
    public void cleanup() {
        runShellCommand("pm uninstall " + PACKAGE_NAME);
    }

    @Test
    public void testGrantPermissionInvokesOldCallback() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        OnPermissionsChangedListener permissionsChangedListener =
                uid -> {
                    if (mTestAppUid == uid) {
                        countDownLatch.countDown();
                    }
                };

        runWithShellPermissionIdentity(() -> {
            mPackageManager.addOnPermissionsChangeListener(permissionsChangedListener);
            mPackageManager.grantRuntimePermission(PACKAGE_NAME, PERMISSION_NAME,
                    mContext.getUser());
        });
        countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
        runWithShellPermissionIdentity(() -> {
            mPackageManager.removeOnPermissionsChangeListener(permissionsChangedListener);
        });

        assertThat(countDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    public void testGrantPermissionNotifyListener() throws InterruptedException {
        TestOnPermissionsChangedListener permissionsChangedListener =
                new TestOnPermissionsChangedListener(1);
        runWithShellPermissionIdentity(() -> {
            mPackageManager.addOnPermissionsChangeListener(permissionsChangedListener);
            mPackageManager.grantRuntimePermission(PACKAGE_NAME, PERMISSION_NAME,
                    mContext.getUser());
        });

        permissionsChangedListener.waitForPermissionChangedCallbacks();
        runWithShellPermissionIdentity(() -> {
            mPackageManager.removeOnPermissionsChangeListener(permissionsChangedListener);
        });

        String deviceId = permissionsChangedListener.getNotifiedDeviceId(mTestAppUid);
        assertThat(deviceId).isEqualTo(VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT);
    }

    @Test
    public void testRevokePermissionNotifyListener() throws InterruptedException {
        TestOnPermissionsChangedListener permissionsChangedListener =
                new TestOnPermissionsChangedListener(1);
        runWithShellPermissionIdentity(() -> {
            mPackageManager.grantRuntimePermission(PACKAGE_NAME, PERMISSION_NAME,
                    mContext.getUser());
            mPackageManager.addOnPermissionsChangeListener(permissionsChangedListener);
            mPackageManager.revokeRuntimePermission(PACKAGE_NAME, PERMISSION_NAME,
                    mContext.getUser());
        });
        permissionsChangedListener.waitForPermissionChangedCallbacks();
        runWithShellPermissionIdentity(() -> {
            mPackageManager.removeOnPermissionsChangeListener(permissionsChangedListener);
        });

        String deviceId = permissionsChangedListener.getNotifiedDeviceId(mTestAppUid);
        assertThat(deviceId).isEqualTo(VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT);
    }

    @Test
    public void testUpdatePermissionFlagsNotifyListener() throws InterruptedException {
        TestOnPermissionsChangedListener permissionsChangedListener =
                new TestOnPermissionsChangedListener(1);
        runWithShellPermissionIdentity(() -> {
            mPackageManager.addOnPermissionsChangeListener(permissionsChangedListener);
            int flag = PackageManager.FLAG_PERMISSION_USER_SET;
            mPackageManager.updatePermissionFlags(PERMISSION_NAME, PACKAGE_NAME, flag, flag,
                    mContext.getUser());
        });
        permissionsChangedListener.waitForPermissionChangedCallbacks();
        runWithShellPermissionIdentity(() -> {
            mPackageManager.removeOnPermissionsChangeListener(permissionsChangedListener);
        });

        String deviceId = permissionsChangedListener.getNotifiedDeviceId(mTestAppUid);
        assertThat(deviceId).isEqualTo(VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT);
    }

    private class TestOnPermissionsChangedListener
            implements OnPermissionsChangedListener {
        // map of uid and persistentDeviceID
        private final Map<Integer, String> mUidDeviceIdsMap = new ConcurrentHashMap<>();
        private final CountDownLatch mCountDownLatch;

        TestOnPermissionsChangedListener(int expectedCallbackCount) {
            mCountDownLatch = new CountDownLatch(expectedCallbackCount);
        }

        @Override
        public void onPermissionsChanged(int uid) {
            // ignored when we implement the new callback.
        }

        @Override
        public void onPermissionsChanged(int uid, String deviceId) {
            if (uid == mTestAppUid) {
                mCountDownLatch.countDown();
                mUidDeviceIdsMap.put(uid, deviceId);
            }
        }

        String getNotifiedDeviceId(int uid) {
            return mUidDeviceIdsMap.get(uid);
        }

        void waitForPermissionChangedCallbacks() throws InterruptedException {
            mCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
            assertThat(mCountDownLatch.getCount()).isEqualTo(0);
        }
    }
}
