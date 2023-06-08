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

package com.android.safetycenter.resources;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Wrapper for a base context to expose Safety Center resources that need to be fetched from a
 * dedicated APK.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
public final class SafetyCenterResourcesContext extends ContextWrapper {
    private static final String TAG = "SafetyCenterResContext";

    /** Intent action that is used to identify the Safety Center resources APK */
    private static final String RESOURCES_APK_ACTION =
            "com.android.safetycenter.intent.action.SAFETY_CENTER_RESOURCES_APK";

    /** Permission APEX name */
    private static final String APEX_MODULE_NAME = "com.android.permission";

    /**
     * The path where the Permission apex is mounted. Current value = "/apex/com.android.permission"
     */
    private static final String APEX_MODULE_PATH =
            new File("/apex", APEX_MODULE_NAME).getAbsolutePath();

    /** Raw XML config resource name */
    private static final String CONFIG_NAME = "safety_center_config";

    /** Intent action that is used to identify the Safety Center resources APK */
    private final String mResourcesApkAction;

    /** The path where the Safety Center resources APK is expected to be installed */
    private final String mResourcesApkPath;

    /** Raw XML config resource name */
    private final String mConfigName;

    /** Specific flags used for retrieving resolve info. */
    private final int mFlags;

    /**
     * Whether we should fallback with an empty string / null values when calling the methods of
     * this class for a resource that does not exist.
     */
    private final boolean mShouldFallbackIfNamedResourceNotFound;

    // Cached context from the resources APK
    @Nullable private Context mResourcesApkContext;

    public SafetyCenterResourcesContext(Context contextBase) {
        this(contextBase, /* shouldFallbackIfNamedResourceNotFound */ true);
    }

    private SafetyCenterResourcesContext(
            Context contextBase, boolean shouldFallbackIfNamedResourceNotFound) {
        this(
                contextBase,
                RESOURCES_APK_ACTION,
                APEX_MODULE_PATH,
                CONFIG_NAME,
                PackageManager.MATCH_SYSTEM_ONLY,
                shouldFallbackIfNamedResourceNotFound);
    }

    @VisibleForTesting
    SafetyCenterResourcesContext(
            Context contextBase,
            String resourcesApkAction,
            String resourcesApkPath,
            String configName,
            int flags,
            boolean shouldFallbackIfNamedResourceNotFound) {
        super(contextBase);
        mResourcesApkAction = requireNonNull(resourcesApkAction);
        mResourcesApkPath = requireNonNull(resourcesApkPath);
        mConfigName = requireNonNull(configName);
        mFlags = flags;
        mShouldFallbackIfNamedResourceNotFound = shouldFallbackIfNamedResourceNotFound;
    }

    /** Creates a new {@link SafetyCenterResourcesContext} for testing. */
    @VisibleForTesting
    public static SafetyCenterResourcesContext forTests(Context contextBase) {
        return new SafetyCenterResourcesContext(
                contextBase, /* shouldFallbackIfNamedResourceNotFound */ false);
    }

    /**
     * Initializes the resources APK {@link Context}, and returns whether this was successful.
     *
     * <p>This call is optional as this can also be lazily instantiated. It can be used to ensure
     * that the resources APK context is loaded prior to interacting with this class. This
     * initialization code needs to run in the same user as the provided base {@link Context}. This
     * may not be the case with a binder call, which is why it can be more appropriate to do this
     * explicitly.
     */
    public boolean init() {
        return getResourcesApkContext() != null;
    }

    /** Gets the {@link Context} of the Safety Center resources APK. */
    @VisibleForTesting
    @Nullable
    Context getResourcesApkContext() {
        if (mResourcesApkContext != null) {
            return mResourcesApkContext;
        }

        mResourcesApkContext = loadResourcesApkContext();
        return mResourcesApkContext;
    }

    @Nullable
    private Context loadResourcesApkContext() {
        List<ResolveInfo> resolveInfos =
                getPackageManager().queryIntentActivities(new Intent(mResourcesApkAction), mFlags);

        if (resolveInfos.size() > 1) {
            // multiple apps found, log a warning, but continue
            Log.w(TAG, "Found > 1 APK that can resolve Safety Center resources APK intent:");
            final int resolveInfosSize = resolveInfos.size();
            for (int i = 0; i < resolveInfosSize; i++) {
                ResolveInfo resolveInfo = resolveInfos.get(i);
                Log.w(
                        TAG,
                        String.format(
                                "- pkg:%s at:%s",
                                resolveInfo.activityInfo.applicationInfo.packageName,
                                resolveInfo.activityInfo.applicationInfo.sourceDir));
            }
        }

        ResolveInfo info = null;
        // Assume the first good ResolveInfo is the one we're looking for
        final int resolveInfosSize = resolveInfos.size();
        for (int i = 0; i < resolveInfosSize; i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            if (!resolveInfo.activityInfo.applicationInfo.sourceDir.startsWith(mResourcesApkPath)) {
                // skip apps that don't live in the Permission apex
                continue;
            }
            info = resolveInfo;
            break;
        }

        if (info == null) {
            // Resource APK not loaded yet, print a stack trace to see where this is called from
            Log.e(
                    TAG,
                    "Attempted to fetch resources before Safety Center resources APK is loaded!",
                    new IllegalStateException());
            return null;
        }

        String resourcesApkPkgName = info.activityInfo.applicationInfo.packageName;
        Log.i(TAG, "Found Safety Center resources APK at: " + resourcesApkPkgName);
        return getPackageContext(resourcesApkPkgName);
    }

    @Nullable
    private Context getPackageContext(String packageName) {
        try {
            return createPackageContext(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to load package context for: " + packageName, e);
        }
        return null;
    }

    /**
     * Gets the raw XML resource representing the Safety Center configuration from the Safety Center
     * resources APK.
     */
    @Nullable
    public InputStream getSafetyCenterConfig() {
        int id = getResId(mConfigName, "raw");
        if (id == Resources.ID_NULL) {
            return null;
        }
        return getResources().openRawResource(id);
    }

    /**
     * Returns an optional {@link String} resource from the given {@code stringId}.
     *
     * <p>Returns {@code null} if {@code stringId} is equal to {@link Resources#ID_NULL}. Otherwise,
     * throws a {@link Resources.NotFoundException} if the resource cannot be accessed.
     */
    @Nullable
    public String getOptionalString(@StringRes int stringId) {
        if (stringId == Resources.ID_NULL) {
            return null;
        }
        return getString(stringId);
    }

    /** Same as {@link #getOptionalString(int)} but with the given {@code formatArgs}. */
    @Nullable
    public String getOptionalString(@StringRes int stringId, Object... formatArgs) {
        if (stringId == Resources.ID_NULL) {
            return null;
        }
        return getString(stringId, formatArgs);
    }

    /** Same as {@link #getOptionalString(int)} but using the string name rather than ID. */
    @Nullable
    public String getOptionalStringByName(String name) {
        return getOptionalString(getResId(name, "string"));
    }

    /**
     * Gets a string resource by name from the Safety Center resources APK, and returns an empty
     * string if the resource does not exist (or throws a {@link Resources.NotFoundException} if
     * {@link #mShouldFallbackIfNamedResourceNotFound} is {@code false}).
     */
    public String getStringByName(String name) {
        int id = getResId(name, "string");
        return maybeFallbackIfNamedResourceIsNull(name, getOptionalString(id));
    }

    /** Same as {@link #getStringByName(String)} but with the given {@code formatArgs}. */
    public String getStringByName(String name, Object... formatArgs) {
        int id = getResId(name, "string");
        return maybeFallbackIfNamedResourceIsNull(name, getOptionalString(id, formatArgs));
    }

    /** Retrieve assets held in the Safety Center resources APK. */
    @Override
    @Nullable
    public AssetManager getAssets() {
        Context resourcesApkContext = getResourcesApkContext();
        if (resourcesApkContext == null) {
            return null;
        }
        return resourcesApkContext.getAssets();
    }

    /** Retrieve resources held in the Safety Center resources APK. */
    @Override
    @Nullable
    public Resources getResources() {
        Context resourcesApkContext = getResourcesApkContext();
        if (resourcesApkContext == null) {
            return null;
        }
        return resourcesApkContext.getResources();
    }

    /** Retrieve theme held in the Safety Center resources APK. */
    @Override
    @Nullable
    public Resources.Theme getTheme() {
        Context resourcesApkContext = getResourcesApkContext();
        if (resourcesApkContext == null) {
            return null;
        }
        return resourcesApkContext.getTheme();
    }

    /**
     * Gets a drawable resource by name from the Safety Center resources APK. Returns a null
     * drawable if the resource does not exist (or throws a {@link Resources.NotFoundException} if
     * {@link #mShouldFallbackIfNamedResourceNotFound} is {@code false}).
     *
     * @param name the identifier for this drawable resource
     * @param theme the theme used to style the drawable attributes, may be {@code null}
     */
    @Nullable
    public Drawable getDrawableByName(String name, @Nullable Resources.Theme theme) {
        int resId = getResId(name, "drawable");
        if (resId != Resources.ID_NULL) {
            return getResources().getDrawable(resId, theme);
        }

        if (!mShouldFallbackIfNamedResourceNotFound) {
            throw new Resources.NotFoundException();
        }

        Log.w(TAG, "Drawable resource " + name + " not found");
        return null;
    }

    /**
     * Returns an {@link Icon} instance containing a drawable with the given name. If no such
     * drawable exists, returns {@code null} or throws {@link Resources.NotFoundException}.
     */
    @Nullable
    public Icon getIconByDrawableName(String name) {
        int resId = getResId(name, "drawable");
        if (resId != Resources.ID_NULL) {
            return Icon.createWithResource(getResourcesApkContext().getPackageName(), resId);
        }

        if (!mShouldFallbackIfNamedResourceNotFound) {
            throw new Resources.NotFoundException();
        }

        Log.w(TAG, "Drawable resource " + name + " not found");
        return null;
    }

    /** Gets a color by resource name */
    @ColorInt
    @Nullable
    public Integer getColorByName(String name) {
        int resId = getResId(name, "color");
        if (resId != Resources.ID_NULL) {
            return getResources().getColor(resId, getTheme());
        }

        if (!mShouldFallbackIfNamedResourceNotFound) {
            throw new Resources.NotFoundException();
        }

        Log.w(TAG, "Color resource " + name + " not found");
        return null;
    }

    private String maybeFallbackIfNamedResourceIsNull(String name, @Nullable String value) {
        if (value != null) {
            return value;
        }
        if (!mShouldFallbackIfNamedResourceNotFound) {
            throw new Resources.NotFoundException();
        }
        Log.w(TAG, "String resource " + name + " not found");
        return "";
    }

    private int getResId(String name, String type) {
        Context resourcesApkContext = getResourcesApkContext();
        if (resourcesApkContext == null) {
            return Resources.ID_NULL;
        }
        // TODO(b/227738283): profile the performance of this operation and consider adding caching
        //  or finding some alternative solution.
        return resourcesApkContext
                .getResources()
                .getIdentifier(name, type, resourcesApkContext.getPackageName());
    }
}
