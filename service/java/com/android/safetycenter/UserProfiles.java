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

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A class that represent all the profiles (profile owner and work profile(s)) associated with a
 * user id.
 */
@RequiresApi(TIRAMISU)
final class UserProfiles {

    private static final String TAG = "UserProfiles";

    @UserIdInt
    private static final int USER_NULL = -10000;

    @UserIdInt
    private final int mProfileOwnerUserId;

    @NonNull
    private final int[] mWorkProfilesUserIds;

    private UserProfiles(@UserIdInt int profileOwnerUserId, @NonNull int[] workProfilesUserIds) {
        mProfileOwnerUserId = profileOwnerUserId;
        mWorkProfilesUserIds = workProfilesUserIds;
    }

    /**
     * Returns the {@link UserProfiles} associated with the given {@code userId}.
     *
     * <p>The given {@code userId} could be related to the profile owner or any of its associated
     * work profile(s).
     */
    static UserProfiles from(@NonNull Context context, @UserIdInt int userId) {
        Context userContext = getUserContext(context, UserHandle.of(userId));
        UserManager userManager = requireNonNull(userContext.getSystemService(UserManager.class));
        List<UserHandle> userProfiles = getUserProfiles(userManager);
        int profileOwnerUserId = USER_NULL;
        int[] workProfileUserIds = new int[userProfiles.size() - 1];
        int j = 0;
        for (int i = 0; i < userProfiles.size(); i++) {
            int userProfileId = userProfiles.get(i).getIdentifier();

            if (isManagedProfile(userManager, userProfileId)) {
                workProfileUserIds[j++] = userProfileId;
            } else if (profileOwnerUserId == USER_NULL) {
                profileOwnerUserId = userProfileId;
            } else {
                Log.w(TAG, String.format("Found multiple profile owner user ids: %s, %s",
                        profileOwnerUserId, userProfileId));
            }
        }

        if (profileOwnerUserId == USER_NULL) {
            throw new IllegalStateException("Could not find profile owner user id");
        }

        return new UserProfiles(profileOwnerUserId, workProfileUserIds);
    }

    @NonNull
    private static List<UserHandle> getUserProfiles(
            @NonNull UserManager userManager) {
        // This call requires the QUERY_USERS permission.
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            return userManager.getUserProfiles();
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    private static boolean isManagedProfile(
            @NonNull UserManager userManager,
            @UserIdInt int userId) {
        // This call requires the QUERY_USERS permission.
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            return userManager.isManagedProfile(userId);
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    @NonNull
    private static Context getUserContext(
            @NonNull Context context,
            @NonNull UserHandle userHandle) {
        if (Process.myUserHandle().equals(userHandle)) {
            return context;
        } else {
            try {
                return context.createPackageContextAsUser(context.getPackageName(), 0, userHandle);
            } catch (PackageManager.NameNotFoundException doesNotHappen) {
                throw new IllegalStateException(doesNotHappen);
            }
        }
    }

    /** Returns the profile owner user id of the {@link UserProfiles}. */
    int getProfileOwnerUserId() {
        return mProfileOwnerUserId;
    }

    /** Returns the work profile user ids of the {@link UserProfiles}. */
    int[] getWorkProfilesUserIds() {
        return mWorkProfilesUserIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfiles)) return false;
        UserProfiles that = (UserProfiles) o;
        return mProfileOwnerUserId == that.mProfileOwnerUserId && Arrays.equals(
                mWorkProfilesUserIds, that.mWorkProfilesUserIds);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mProfileOwnerUserId);
        result = 31 * result + Arrays.hashCode(mWorkProfilesUserIds);
        return result;
    }

    @Override
    public String toString() {
        return "UserProfiles{"
                + "mProfileOwnerUserId="
                + mProfileOwnerUserId
                + ", mWorkProfilesUserIds="
                + Arrays.toString(mWorkProfilesUserIds)
                + '}';
    }
}
