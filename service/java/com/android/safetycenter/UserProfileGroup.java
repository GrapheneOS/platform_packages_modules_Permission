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

import com.android.permission.util.UserUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A class that represent all the enabled profiles (profile owner and managed profile(s)) associated
 * with a user id.
 */
@RequiresApi(TIRAMISU)
final class UserProfileGroup {

    private static final String TAG = "UserProfileGroup";

    @UserIdInt
    // TODO(b/223126212): Don't fork this — is there another value we could use?
    private static final int USER_NULL = -10000;

    @UserIdInt
    private final int mProfileOwnerUserId;

    @NonNull
    private final int[] mManagedProfilesUserIds;

    private UserProfileGroup(
            @UserIdInt int profileOwnerUserId,
            @NonNull int[] managedProfilesUserIds) {
        mProfileOwnerUserId = profileOwnerUserId;
        mManagedProfilesUserIds = managedProfilesUserIds;
    }

    /**
     * Returns the {@link UserProfileGroup} associated with the given {@code userId}.
     *
     * <p>The given {@code userId} could be related to the profile owner or any of its associated
     * managed profile(s).
     */
    static UserProfileGroup from(@NonNull Context context, @UserIdInt int userId) {
        UserManager userManager = getUserManagerForUser(userId, context);
        List<UserHandle> userProfiles = getEnabledUserProfiles(userManager);
        int profileOwnerUserId = USER_NULL;
        int[] managedProfileUserIds = new int[userProfiles.size()];
        int managedProfileUserIdsLen = 0;
        for (int i = 0; i < userProfiles.size(); i++) {
            UserHandle userProfileHandle = userProfiles.get(i);
            int userProfileId = userProfileHandle.getIdentifier();

            // TODO(b/223132917): Check if user running and/or if quiet mode is enabled?
            if (UserUtils.isManagedProfile(userProfileId, context)) {
                managedProfileUserIds[managedProfileUserIdsLen++] = userProfileId;
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

        return new UserProfileGroup(profileOwnerUserId,
                Arrays.copyOf(managedProfileUserIds, managedProfileUserIdsLen));
    }

    @NonNull
    private static UserManager getUserManagerForUser(
            @UserIdInt int userId,
            @NonNull Context context) {
        Context userContext = getUserContext(context, UserHandle.of(userId));
        return requireNonNull(userContext.getSystemService(UserManager.class));
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

    @NonNull
    private static List<UserHandle> getEnabledUserProfiles(
            @NonNull UserManager userManager) {
        // This call requires the QUERY_USERS permission.
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            return userManager.getUserProfiles();
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    /** Returns the profile owner user id of the {@link UserProfileGroup}. */
    int getProfileOwnerUserId() {
        return mProfileOwnerUserId;
    }

    /** Returns the managed profile user ids of the {@link UserProfileGroup}. */
    int[] getManagedProfilesUserIds() {
        return mManagedProfilesUserIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfileGroup)) return false;
        UserProfileGroup that = (UserProfileGroup) o;
        return mProfileOwnerUserId == that.mProfileOwnerUserId && Arrays.equals(
                mManagedProfilesUserIds, that.mManagedProfilesUserIds);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mProfileOwnerUserId);
        result = 31 * result + Arrays.hashCode(mManagedProfilesUserIds);
        return result;
    }

    @Override
    public String toString() {
        return "UserProfiles{"
                + "mProfileOwnerUserId="
                + mProfileOwnerUserId
                + ", mManagedProfilesUserIds="
                + Arrays.toString(mManagedProfilesUserIds)
                + '}';
    }
}
