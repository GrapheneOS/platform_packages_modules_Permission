/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui;

import android.app.Application;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.role.utils.UserUtils;

import java.util.List;

/**
 * {@link ViewModel} for the list of default apps.
 */
public class DefaultAppListViewModel extends AndroidViewModel {

    @NonNull
    private final UserHandle mUser;
    @NonNull
    private final LiveData<List<RoleItem>> mLiveData;
    @Nullable
    private final UserHandle mWorkProfile;
    @Nullable
    private final LiveData<List<RoleItem>> mWorkLiveData;
    @Nullable
    private final UserHandle mPrivateProfile;
    @Nullable
    private final LiveData<List<RoleItem>> mPrivateLiveData;

    public DefaultAppListViewModel(@NonNull Application application) {
        super(application);

        mUser = Process.myUserHandle();
        RoleListSortFunction sortFunction = new RoleListSortFunction(application);
        mLiveData = Transformations.map(new RoleListLiveData(true, mUser, application),
                sortFunction);
        mWorkProfile = UserUtils.getWorkProfile(application);
        mWorkLiveData = mWorkProfile != null ? Transformations.map(new RoleListLiveData(true,
                mWorkProfile, application), sortFunction) : null;

        UserHandle privateProfile = UserUtils.getPrivateProfile(application);
        if (privateProfile != null && Utils.shouldShowInSettings(
                privateProfile, application.getSystemService(UserManager.class))) {
            mPrivateProfile = privateProfile;
        } else {
            mPrivateProfile = null;
        }
        mPrivateLiveData = mPrivateProfile != null ? Transformations.map(new RoleListLiveData(true,
                mPrivateProfile, application), sortFunction) : null;
    }

    @NonNull
    public UserHandle getUser() {
        return mUser;
    }

    @NonNull
    public LiveData<List<RoleItem>> getLiveData() {
        return mLiveData;
    }

    /**
     * Check whether the user has a work profile.
     *
     * @return whether the user has a work profile.
     */
    public boolean hasWorkProfile() {
        return mWorkProfile != null;
    }

    @Nullable
    public UserHandle getWorkProfile() {
        return mWorkProfile;
    }

    @Nullable
    public LiveData<List<RoleItem>> getWorkLiveData() {
        return mWorkLiveData;
    }

    /**
     * Check whether the user has a private profile.
     *
     * @return whether the user has a private profile.
     */
    public boolean hasPrivateProfile() {
        return mPrivateProfile != null;
    }

    /**
     * Returns the private profile belonging to the user, if any.
     *
     * @return the private profile, if it exists. null otherwise.
     */
    @Nullable
    public UserHandle getPrivateProfile() {
        return mPrivateProfile;
    }

    /**
     * Returns the data corresponding to the private profile, if one exists.
     *
     * @return data corresponding to the private profile, if it exists. null otherwise.
     */
    @Nullable
    public LiveData<List<RoleItem>> getPrivateLiveData() {
        return mPrivateLiveData;
    }
}
