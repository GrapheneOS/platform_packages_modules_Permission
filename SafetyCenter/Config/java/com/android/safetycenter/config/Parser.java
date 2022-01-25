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

package com.android.safetycenter.config;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;

import com.android.safetycenter.config.parser.SafetyCenterConfig;
import com.android.safetycenter.config.parser.SafetySource;
import com.android.safetycenter.config.parser.SafetySourcesConfig;
import com.android.safetycenter.config.parser.SafetySourcesGroup;
import com.android.safetycenter.config.parser.StaticSafetySourcesGroup;
import com.android.safetycenter.config.parser.XmlParser;

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Utility class to parse and validate a Safety Center Config */
public final class Parser {
    /** Static safety source */
    public static final int SAFETY_SOURCE_TYPE_STATIC = 1;

    /** Dynamic safety source */
    public static final int SAFETY_SOURCE_TYPE_DYNAMIC = 2;

    /** Internal safety source */
    public static final int SAFETY_SOURCE_TYPE_INTERNAL = 3;

    /** All possible safety source types */
    @IntDef(prefix = {"SAFETY_SOURCE_TYPE_"}, value = {
            SAFETY_SOURCE_TYPE_STATIC,
            SAFETY_SOURCE_TYPE_DYNAMIC,
            SAFETY_SOURCE_TYPE_INTERNAL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SafetySourceType {
    }

    /**
     * Even when the active user has managed profiles, the safety source will be displayed as a
     * single entry for the primary profile.
     */
    public static final int PROFILE_PRIMARY = 1;

    /**
     * When the user has managed profiles, the safety source will be displayed as multiple entries
     * one for each profile.
     */
    public static final int PROFILE_ALL = 2;

    /** All possible profile configurations for a safety source */
    @IntDef(prefix = {"PROFILE_"}, value = {
            PROFILE_PRIMARY,
            PROFILE_ALL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Profile {
    }

    private static final class SourceId {
        @Nullable
        private final String mPackageName;
        @NonNull
        private final String mId;

        private SourceId(@Nullable String packageName, @NonNull String id) {
            this.mPackageName = packageName;
            this.mId = id;
        }

        @NonNull
        private static SourceId of(@Nullable String packageName, @NonNull String id) {
            return new SourceId(packageName, id);
        }

        @Override
        public String toString() {
            return "SourceId{"
                    + "mPackageName='"
                    + (mPackageName == null ? "null" : mPackageName)
                    + '\''
                    + ", mId='"
                    + mId
                    + '\''
                    + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SourceId)) return false;
            SourceId key = (SourceId) o;
            return Objects.equals(this.mPackageName, key.mPackageName)
                    && Objects.equals(this.mId, key.mId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPackageName, mId);
        }
    }

    /** Thrown when there is an error parsing the Safety Center Config */
    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable ex) {
            super(message, ex);
        }
    }

    /**
     * Parses and validates the given raw XML resource into a {@link SafetyCenterConfig} object.
     *
     * @param in              the name of the raw XML resource in the package that contains the
     *                        Safety Center
     *                        Config
     * @param resourcePkgName the name of the package that contains the Safety Center Config
     * @param resources       the {@link Resources} retrieved from the package that contains the
     *                        Safety Center Config
     */
    @Nullable
    public static SafetyCenterConfig parse(@NonNull InputStream in, @NonNull String resourcePkgName,
            @NonNull Resources resources) throws ParseException {
        if (in == null) {
            throw new ParseException("Parameter in must be defined");
        }
        if (resourcePkgName == null || resourcePkgName.isEmpty()) {
            throw new ParseException("Resource package name must be defined");
        }
        if (resources == null) {
            throw new ParseException("Resources must be defined");
        }
        SafetyCenterConfig safetyCenterConfig;
        try {
            safetyCenterConfig = XmlParser.read(in);
        } catch (Exception e) {
            throw new ParseException("Exception while reading XML", e);
        }
        validate(safetyCenterConfig, resourcePkgName, resources);
        return safetyCenterConfig;
    }

    private static void validate(@Nullable SafetyCenterConfig safetyCenterConfig,
            @NonNull String resourcePkgName, @NonNull Resources resources) throws ParseException {
        if (safetyCenterConfig == null) {
            throw new ParseException("Element safety-center-config missing");
        }
        validate(safetyCenterConfig.getSafetySourcesConfig(), resourcePkgName, resources);
    }

    private static void validate(@Nullable SafetySourcesConfig safetySourcesConfig,
            @NonNull String resourcePkgName, @NonNull Resources resources) throws ParseException {
        if (safetySourcesConfig == null) {
            throw new ParseException("Element safety-sources-config missing");
        }
        if ((safetySourcesConfig.getSafetySourcesGroup() == null
                || safetySourcesConfig.getSafetySourcesGroup().isEmpty()) && (
                safetySourcesConfig.getStaticSafetySourcesGroup() == null
                        || safetySourcesConfig.getStaticSafetySourcesGroup().isEmpty())) {
            throw new ParseException("Element safety-sources-config empty");
        }
        Set<String> groupIds = new HashSet<>();
        Set<SourceId> sourceIds = new HashSet<>();
        List<SafetySourcesGroup> safetySourcesGroups = safetySourcesConfig.getSafetySourcesGroup();
        if (safetySourcesGroups != null) {
            for (SafetySourcesGroup safetySourcesGroup : safetySourcesGroups) {
                validate(safetySourcesGroup, groupIds, sourceIds, resourcePkgName, resources);
            }
        }
        List<StaticSafetySourcesGroup> staticSafetySourcesGroups =
                safetySourcesConfig.getStaticSafetySourcesGroup();
        if (staticSafetySourcesGroups != null) {
            for (StaticSafetySourcesGroup staticSafetySourcesGroup : staticSafetySourcesGroups) {
                validate(staticSafetySourcesGroup, groupIds, sourceIds, resourcePkgName, resources);
            }
        }
    }

    private static void validate(@Nullable SafetySourcesGroup safetySourcesGroup,
            @NonNull Set<String> groupIds, @NonNull Set<SourceId> sourceIds,
            @NonNull String resourcePkgName, @NonNull Resources resources) throws ParseException {
        if (safetySourcesGroup == null) {
            throw new ParseException("Element safety-sources-group invalid");
        }
        validateGroupId(safetySourcesGroup.getId(), groupIds, "safety-sources-group");
        validateReference(safetySourcesGroup.getTitle(), "safety-sources-group.title", true, false,
                resourcePkgName, resources);
        validateReference(safetySourcesGroup.getSummary(), "safety-sources-group.summary", true,
                false, resourcePkgName, resources);
        List<SafetySource> safetySources = safetySourcesGroup.getSafetySource();
        if (safetySources == null || safetySources.isEmpty()) {
            throw new ParseException("Element safety-sources-group empty");
        }
        for (SafetySource safetySource : safetySources) {
            validate(safetySource, sourceIds, resourcePkgName, resources, "safety-source");
        }
    }

    private static void validate(@Nullable StaticSafetySourcesGroup staticSafetySourcesGroup,
            @NonNull Set<String> groupIds, @NonNull Set<SourceId> sourceIds,
            @NonNull String resourcePkgName, @NonNull Resources resources) throws ParseException {
        if (staticSafetySourcesGroup == null) {
            throw new ParseException("Element static-safety-sources-group invalid");
        }
        validateGroupId(staticSafetySourcesGroup.getId(), groupIds, "static-safety-sources-group");
        validateReference(staticSafetySourcesGroup.getTitle(), "static-safety-sources-group.title",
                true, false, resourcePkgName, resources);
        List<SafetySource> staticSafetySources = staticSafetySourcesGroup.getStaticSafetySource();
        if (staticSafetySources == null || staticSafetySources.isEmpty()) {
            throw new ParseException("Element static-safety-sources-group empty");
        }
        for (SafetySource staticSafetySource : staticSafetySources) {
            if (staticSafetySource.getType() != SAFETY_SOURCE_TYPE_STATIC) {
                throw new ParseException(
                        String.format("Invalid type %d in static-safety-sources-group",
                                staticSafetySource.getType()));
            }
            validate(staticSafetySource, sourceIds, resourcePkgName, resources,
                    "static-safety-source");
        }
    }

    private static void validate(@Nullable SafetySource safetySource,
            @NonNull Set<SourceId> sourceIds, @NonNull String resourcePkgName,
            @NonNull Resources resources, @NonNull String name) throws ParseException {
        if (safetySource == null) {
            throw new ParseException(String.format("Element %s invalid", name));
        }
        validateType(safetySource.getType(), name);
        boolean isStatic = safetySource.getType() == SAFETY_SOURCE_TYPE_STATIC;
        boolean isDynamic = safetySource.getType() == SAFETY_SOURCE_TYPE_DYNAMIC;
        boolean isInternal = safetySource.getType() == SAFETY_SOURCE_TYPE_INTERNAL;
        validateSourceId(safetySource.getId(), safetySource.getPackageName(), isDynamic,
                isStatic || isInternal, sourceIds, name);
        validateReference(safetySource.getTitle(), name + ".title", isDynamic || isStatic,
                isInternal, resourcePkgName, resources);
        validateReference(safetySource.getSummary(), name + ".summary", isDynamic || isStatic,
                isInternal, resourcePkgName, resources);
        validateString(safetySource.getIntentAction(), name + ".intentAction",
                isDynamic || isStatic, isInternal);
        validateProfile(safetySource.getProfile(), name, isDynamic || isStatic,
                isInternal);
        validateReference(safetySource.getSearchTerms(), name + ".searchTerms", false, isInternal,
                resourcePkgName, resources);
        validateString(safetySource.getBroadcastReceiverClassName(),
                name + ".broadcastReceiverClassName", false, isStatic || isInternal);
        if (safetySource.isDisallowLogging() && (isStatic || isInternal)) {
            throw new ParseException(
                    String.format("Prohibited attribute %s.disallowLogging present", name));
        }
    }

    private static void validateGroupId(@Nullable String id, @NonNull Set<String> groupIds,
            @NonNull String name) throws ParseException {
        validateString(id, name + ".id", true, false);

        if (groupIds.contains(id)) {
            throw new ParseException(String.format(
                    "Duplicate id %s among safety-sources-groups and static-safety-sources-groups",
                    id));
        }
        groupIds.add(id);
    }

    private static void validateType(int type, @NonNull String name) throws ParseException {
        if (type != SAFETY_SOURCE_TYPE_STATIC
                && type != SAFETY_SOURCE_TYPE_DYNAMIC
                && type != SAFETY_SOURCE_TYPE_INTERNAL) {
            throw new ParseException(
                    String.format("Required attribute %s.type missing or invalid", name));
        }
    }

    private static void validateProfile(int profile, @NonNull String name, boolean required,
            boolean prohibited) throws ParseException {
        if (profile != 0
                && profile != PROFILE_PRIMARY
                && profile != PROFILE_ALL) {
            throw new ParseException(String.format("Attribute %s.profile invalid", name));
        }
        if (required && profile == 0) {
            throw new ParseException(
                    String.format("Required attribute %s.profile missing or invalid", name));
        }
        if (prohibited && profile != 0) {
            throw new ParseException(
                    String.format("Prohibited attribute %s.profile present", name));
        }
    }

    private static void validateSourceId(@Nullable String id, @Nullable String packageName,
            boolean packageNameRequired, boolean packageNameProhibited,
            @NonNull Set<SourceId> sourceIds, @NonNull String name) throws ParseException {
        validateString(id, name + ".id", true, false);
        validateString(packageName, name + ".packageName", packageNameRequired,
                packageNameProhibited);
        SourceId key = SourceId.of(packageName, id);
        if (sourceIds.contains(key)) {
            throw new ParseException(
                    String.format("Duplicate id %s %s",
                            id,
                            packageNameRequired
                                    ? "for package " + packageName
                                    : "among static and internal safety sources"));
        }
        sourceIds.add(key);
    }

    private static void validateString(@Nullable String s, @NonNull String name, boolean required,
            boolean prohibited) throws ParseException {
        if (s == null || s.isEmpty()) {
            if (required) {
                throw new ParseException(String.format("Required attribute %s missing", name));
            }
        } else {
            if (prohibited) {
                throw new ParseException(String.format("Prohibited attribute %s present", name));
            }
        }
    }

    private static void validateReference(@Nullable String s, @NonNull String name,
            boolean required, boolean prohibited, @NonNull String resourcePkgName,
            @NonNull Resources resources) throws ParseException {
        validateString(s, name, required, prohibited);
        if (s != null) {
            if (s.startsWith("@string/")) {
                if (resources.getIdentifier(s.substring(1), null, resourcePkgName) == 0) {
                    throw new ParseException(String.format("Reference %s in %s missing", s, name));
                }
            } else {
                throw new ParseException(
                        String.format("String %s in %s is not a reference", s, name));
            }
        }
    }
}
