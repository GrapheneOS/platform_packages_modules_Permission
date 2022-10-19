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

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.permission.util.UserUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A class that represent all the enabled profiles (profile parent and managed profile(s))
 * associated with a user id.
 */
@RequiresApi(TIRAMISU)
final class UserProfileGroup {

    @UserIdInt private final int mProfileParentUserId;
    @NonNull private final int[] mManagedProfilesUserIds;
    @NonNull private final int[] mManagedRunningProfilesUserIds;

    private UserProfileGroup(
            @UserIdInt int profileParentUserId,
            @NonNull int[] managedProfilesUserIds,
            int[] managedRunningProfilesUserIds) {
        mProfileParentUserId = profileParentUserId;
        mManagedProfilesUserIds = managedProfilesUserIds;
        mManagedRunningProfilesUserIds = managedRunningProfilesUserIds;
    }

    /** Returns all the alive {@link UserProfileGroup}s. */
    static List<UserProfileGroup> getAllUserProfileGroups(@NonNull Context context) {
        List<UserProfileGroup> userProfileGroups = new ArrayList<>();
        List<UserHandle> userHandles = UserUtils.getUserHandles(context);
        for (int i = 0; i < userHandles.size(); i++) {
            UserHandle userHandle = userHandles.get(i);
            int userId = userHandle.getIdentifier();

            if (userProfileGroupsContain(userProfileGroups, userId)) {
                continue;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.from(context, userId);
            userProfileGroups.add(userProfileGroup);
        }
        return userProfileGroups;
    }

    private static boolean userProfileGroupsContain(
            @NonNull List<UserProfileGroup> userProfileGroups, @UserIdInt int userId) {
        for (int i = 0; i < userProfileGroups.size(); i++) {
            UserProfileGroup userProfileGroup = userProfileGroups.get(i);

            if (userProfileGroup.contains(userId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the {@link UserProfileGroup} associated with the given {@code userId}.
     *
     * <p>The given {@code userId} could be related to the profile parent or any of its associated
     * managed profile(s).
     */
    static UserProfileGroup from(@NonNull Context context, @UserIdInt int userId) {
        UserManager userManager = getUserManagerForUser(userId, context);
        List<UserHandle> userProfiles = getEnabledUserProfiles(userManager);
        UserHandle profileParent = getProfileParent(userManager, userId);
        int profileParentUserId = userId;
        if (profileParent != null) {
            profileParentUserId = profileParent.getIdentifier();
        }

        int[] managedProfilesUserIds = new int[userProfiles.size()];
        int[] managedRunningProfilesUserIds = new int[userProfiles.size()];
        int managedProfilesUserIdsLen = 0;
        int managedRunningProfilesUserIdsLen = 0;
        for (int i = 0; i < userProfiles.size(); i++) {
            UserHandle userProfileHandle = userProfiles.get(i);
            int userProfileId = userProfileHandle.getIdentifier();

            if (UserUtils.isManagedProfile(userProfileId, context)) {
                managedProfilesUserIds[managedProfilesUserIdsLen++] = userProfileId;
                if (UserUtils.isProfileRunning(userProfileId, context)) {
                    managedRunningProfilesUserIds[managedRunningProfilesUserIdsLen++] =
                            userProfileId;
                }
            }
        }

        return new UserProfileGroup(
                profileParentUserId,
                Arrays.copyOf(managedProfilesUserIds, managedProfilesUserIdsLen),
                Arrays.copyOf(managedRunningProfilesUserIds, managedRunningProfilesUserIdsLen));
    }

    @NonNull
    private static UserManager getUserManagerForUser(
            @UserIdInt int userId, @NonNull Context context) {
        Context userContext = getUserContext(context, UserHandle.of(userId));
        return requireNonNull(userContext.getSystemService(UserManager.class));
    }

    @NonNull
    private static Context getUserContext(
            @NonNull Context context, @NonNull UserHandle userHandle) {
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
    private static List<UserHandle> getEnabledUserProfiles(@NonNull UserManager userManager) {
        // This call requires the QUERY_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return userManager.getUserProfiles();
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Nullable
    private static UserHandle getProfileParent(
            @NonNull UserManager userManager, @UserIdInt int userId) {
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return userManager.getProfileParent(UserHandle.of(userId));
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /** Returns the profile parent user id of the {@link UserProfileGroup}. */
    int getProfileParentUserId() {
        return mProfileParentUserId;
    }

    /** Returns the managed profile user ids of the {@link UserProfileGroup}. */
    int[] getManagedProfilesUserIds() {
        return mManagedProfilesUserIds;
    }

    /** Returns the running managed profile user ids of the {@link UserProfileGroup}. */
    int[] getManagedRunningProfilesUserIds() {
        return mManagedRunningProfilesUserIds;
    }

    /** Returns whether the {@link UserProfileGroup} contains the given {@code userId}. */
    boolean contains(@UserIdInt int userId) {
        if (userId == mProfileParentUserId) {
            return true;
        }

        for (int i = 0; i < mManagedProfilesUserIds.length; i++) {
            if (userId == mManagedProfilesUserIds[i]) {
                return true;
            }
        }

        return false;
    }

    /** Returns whether the given {@code userId} is associated with a running managed profile. */
    boolean isManagedUserRunning(@UserIdInt int userId) {
        for (int i = 0; i < mManagedRunningProfilesUserIds.length; i++) {
            if (userId == mManagedRunningProfilesUserIds[i]) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfileGroup)) return false;
        UserProfileGroup that = (UserProfileGroup) o;
        return mProfileParentUserId == that.mProfileParentUserId
                && Arrays.equals(mManagedProfilesUserIds, that.mManagedProfilesUserIds)
                && Arrays.equals(
                        mManagedRunningProfilesUserIds, that.mManagedRunningProfilesUserIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mProfileParentUserId,
                Arrays.hashCode(mManagedProfilesUserIds),
                Arrays.hashCode(mManagedRunningProfilesUserIds));
    }

    @Override
    public String toString() {
        return "UserProfileGroup{"
                + "mProfileParentUserId="
                + mProfileParentUserId
                + ", mManagedProfilesUserIds="
                + Arrays.toString(mManagedProfilesUserIds)
                + ", mManagedRunningProfilesUserIds="
                + Arrays.toString(mManagedRunningProfilesUserIds)
                + '}';
    }
}
