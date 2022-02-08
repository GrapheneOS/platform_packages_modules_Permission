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

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

import android.annotation.IdRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;

import com.android.safetycenter.config.SafetySource.InitialDisplayState;
import com.android.safetycenter.config.SafetySource.Profile;
import com.android.safetycenter.config.SafetySource.SafetySourceType;
import com.android.safetycenter.config.SafetySourcesGroup.StatelessIconType;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;

/** Utility class to parse and validate a Safety Center Config */
public final class Parser {
    private Parser() {
    }

    private static final String TAG_SAFETY_CENTER_CONFIG = "safety-center-config";
    private static final String TAG_SAFETY_SOURCES_CONFIG = "safety-sources-config";
    private static final String TAG_SAFETY_SOURCES_GROUP = "safety-sources-group";
    private static final String TAG_STATIC_SAFETY_SOURCE = "static-safety-source";
    private static final String TAG_DYNAMIC_SAFETY_SOURCE = "dynamic-safety-source";
    private static final String TAG_ISSUE_ONLY_SAFETY_SOURCE = "issue-only-safety-source";

    private static final String ATTR_SAFETY_SOURCES_GROUP_ID = "id";
    private static final String ATTR_SAFETY_SOURCES_GROUP_TITLE = "title";
    private static final String ATTR_SAFETY_SOURCES_GROUP_SUMMARY = "summary";
    private static final String ATTR_SAFETY_SOURCES_GROUP_STATELESS_ICON_TYPE = "statelessIconType";

    private static final String ATTR_SAFETY_SOURCE_ID = "id";
    private static final String ATTR_SAFETY_SOURCE_PACKAGE_NAME = "packageName";
    private static final String ATTR_SAFETY_SOURCE_TITLE = "title";
    private static final String ATTR_SAFETY_SOURCE_TITLE_FOR_WORK = "titleForWork";
    private static final String ATTR_SAFETY_SOURCE_SUMMARY = "summary";
    private static final String ATTR_SAFETY_SOURCE_INTENT_ACTION = "intentAction";
    private static final String ATTR_SAFETY_SOURCE_PROFILE = "profile";
    private static final String ATTR_SAFETY_SOURCE_INITIAL_DISPLAY_STATE = "initialDisplayState";
    private static final String ATTR_SAFETY_SOURCE_MAX_SEVERITY_LEVEL = "maxSeverityLevel";
    private static final String ATTR_SAFETY_SOURCE_SEARCH_TERMS = "searchTerms";
    private static final String ATTR_SAFETY_SOURCE_BROADCAST_RECEIVER_CLASS_NAME =
            "broadcastReceiverClassName";
    private static final String ATTR_SAFETY_SOURCE_ALLOW_LOGGING = "allowLogging";
    private static final String ATTR_SAFETY_SOURCE_ALLOW_REFRESH_ON_PAGE_OPEN =
            "allowRefreshOnPageOpen";

    private static final String ENUM_STATELESS_ICON_TYPE_NONE = "none";
    private static final String ENUM_STATELESS_ICON_TYPE_PRIVACY = "privacy";

    private static final String ENUM_PROFILE_PRIMARY = "primary_profile_only";
    private static final String ENUM_PROFILE_ALL = "all_profiles";

    private static final String ENUM_INITIAL_DISPLAY_STATE_ENABLED = "enabled";
    private static final String ENUM_INITIAL_DISPLAY_STATE_DISABLED = "disabled";
    private static final String ENUM_INITIAL_DISPLAY_STATE_HIDDEN = "hidden";

    /** Thrown when there is an error parsing the Safety Center Config */
    public static final class ParseException extends Exception {
        public ParseException(@NonNull String message) {
            super(message);
        }

        public ParseException(@NonNull String message, @NonNull Throwable ex) {
            super(message, ex);
        }
    }

    /**
     * Parses and validates the given raw XML resource into a {@link SafetyCenterConfig} object.
     *
     * @param in              the raw XML resource representing the Safety Center configuration
     * @param resourcePkgName the name of the package that contains the Safety Center configuration
     * @param resources       the {@link Resources} retrieved from the package that contains the
     *                        Safety Center configuration
     */
    @Nullable
    public static SafetyCenterConfig parse(@NonNull InputStream in, @NonNull String resourcePkgName,
            @NonNull Resources resources) throws ParseException {
        requireNonNull(in);
        requireNonNull(resourcePkgName);
        requireNonNull(resources);
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(in, null);
            parser.nextTag();
            validateElementStart(parser, TAG_SAFETY_CENTER_CONFIG);
            SafetyCenterConfig safetyCenterConfig =
                    parseSafetyCenterConfig(parser, resourcePkgName, resources);
            if (parser.getEventType() == TEXT && parser.isWhitespace()) {
                parser.next();
            }
            if (parser.getEventType() != END_DOCUMENT) {
                throw new ParseException("Unexpected extra root element");
            }
            return safetyCenterConfig;
        } catch (XmlPullParserException | IOException e) {
            throw new ParseException("Exception while reading XML", e);
        }
    }

    @NonNull
    private static SafetyCenterConfig parseSafetyCenterConfig(@NonNull XmlPullParser parser,
            @NonNull String resourcePkgName, @NonNull Resources resources)
            throws XmlPullParserException, IOException, ParseException {
        validateElementHasNoAttribute(parser, TAG_SAFETY_CENTER_CONFIG);
        parser.nextTag();
        validateElementStart(parser, TAG_SAFETY_SOURCES_CONFIG);
        validateElementHasNoAttribute(parser, TAG_SAFETY_SOURCES_CONFIG);
        SafetyCenterConfig.Builder builder = new SafetyCenterConfig.Builder();
        parser.nextTag();
        while (parser.getEventType() == START_TAG
                && parser.getName().equals(TAG_SAFETY_SOURCES_GROUP)) {
            builder.addSafetySourcesGroup(
                    parseSafetySourcesGroup(parser, resourcePkgName, resources));
        }
        validateElementEnd(parser, TAG_SAFETY_SOURCES_CONFIG);
        parser.nextTag();
        validateElementEnd(parser, TAG_SAFETY_CENTER_CONFIG);
        parser.next();
        try {
            return builder.build();
        } catch (IllegalStateException e) {
            throwElementInvalid(TAG_SAFETY_SOURCES_CONFIG, e);
        }
        return null; // Unreachable
    }

    @NonNull
    private static SafetySourcesGroup parseSafetySourcesGroup(@NonNull XmlPullParser parser,
            @NonNull String resourcePkgName, @NonNull Resources resources)
            throws XmlPullParserException, IOException, ParseException {
        String name = TAG_SAFETY_SOURCES_GROUP;
        SafetySourcesGroup.Builder builder = new SafetySourcesGroup.Builder();
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case ATTR_SAFETY_SOURCES_GROUP_ID:
                    builder.setId(parser.getAttributeValue(i));
                    break;
                case ATTR_SAFETY_SOURCES_GROUP_TITLE:
                    builder.setTitleResId(
                            parseReference(parser.getAttributeValue(i), resourcePkgName, resources,
                                    name, parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCES_GROUP_SUMMARY:
                    builder.setSummaryResId(
                            parseReference(parser.getAttributeValue(i), resourcePkgName, resources,
                                    name, parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCES_GROUP_STATELESS_ICON_TYPE:
                    builder.setStatelessIconType(
                            parseStatelessIconType(parser.getAttributeValue(i), name,
                                    parser.getAttributeName(i)));
                    break;
                default:
                    throwAttributeUnexpected(name, parser.getAttributeName(i));
            }
        }
        parser.nextTag();
        loop:
        while (parser.getEventType() == START_TAG) {
            int type;
            switch (parser.getName()) {
                case TAG_STATIC_SAFETY_SOURCE:
                    type = SafetySource.SAFETY_SOURCE_TYPE_STATIC;
                    break;
                case TAG_DYNAMIC_SAFETY_SOURCE:
                    type = SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC;
                    break;
                case TAG_ISSUE_ONLY_SAFETY_SOURCE:
                    type = SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY;
                    break;
                default:
                    break loop;
            }
            builder.addSafetySource(
                    parseSafetySource(parser, resourcePkgName, resources, type, parser.getName()));
        }
        validateElementEnd(parser, name);
        parser.nextTag();
        try {
            return builder.build();
        } catch (IllegalStateException e) {
            throwElementInvalid(name, e);
        }
        return null; // Unreachable
    }

    @NonNull
    private static SafetySource parseSafetySource(@NonNull XmlPullParser parser,
            @NonNull String resourcePkgName, @NonNull Resources resources,
            @SafetySourceType int type, @NonNull String name)
            throws XmlPullParserException, IOException, ParseException {
        SafetySource.Builder builder = new SafetySource.Builder(type);
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case ATTR_SAFETY_SOURCE_ID:
                    builder.setId(parser.getAttributeValue(i));
                    break;
                case ATTR_SAFETY_SOURCE_PACKAGE_NAME:
                    builder.setPackageName(parser.getAttributeValue(i));
                    break;
                case ATTR_SAFETY_SOURCE_TITLE:
                    builder.setTitleResId(
                            parseReference(parser.getAttributeValue(i), resourcePkgName,
                                    resources, name, parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCE_TITLE_FOR_WORK:
                    builder.setTitleForWorkResId(
                            parseReference(parser.getAttributeValue(i), resourcePkgName,
                                    resources, name, parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCE_SUMMARY:
                    builder.setSummaryResId(
                            parseReference(parser.getAttributeValue(i), resourcePkgName,
                                    resources, name, parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCE_INTENT_ACTION:
                    builder.setIntentAction(parser.getAttributeValue(i));
                    break;
                case ATTR_SAFETY_SOURCE_PROFILE:
                    builder.setProfile(parseProfile(parser.getAttributeValue(i), name,
                            parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCE_INITIAL_DISPLAY_STATE:
                    builder.setInitialDisplayState(
                            parseInitialDisplayState(parser.getAttributeValue(i), name,
                                    parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCE_MAX_SEVERITY_LEVEL:
                    builder.setMaxSeverityLevel(parseInteger(parser.getAttributeValue(i), name,
                            parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCE_SEARCH_TERMS:
                    builder.setSearchTermsResId(
                            parseReference(parser.getAttributeValue(i), resourcePkgName,
                                    resources, name, parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCE_BROADCAST_RECEIVER_CLASS_NAME:
                    builder.setBroadcastReceiverClassName(parser.getAttributeValue(i));
                    break;
                case ATTR_SAFETY_SOURCE_ALLOW_LOGGING:
                    builder.setAllowLogging(parseBoolean(parser.getAttributeValue(i), name,
                            parser.getAttributeName(i)));
                    break;
                case ATTR_SAFETY_SOURCE_ALLOW_REFRESH_ON_PAGE_OPEN:
                    builder.setAllowRefreshOnPageOpen(
                            parseBoolean(parser.getAttributeValue(i), name,
                                    parser.getAttributeName(i)));
                    break;
                default:
                    throwAttributeUnexpected(name, parser.getAttributeName(i));
            }
        }
        parser.nextTag();
        validateElementEnd(parser, name);
        parser.nextTag();
        try {
            return builder.build();
        } catch (IllegalStateException e) {
            throwElementInvalid(name, e);
        }
        return null; // Unreachable
    }

    private static void validateElementStart(@NonNull XmlPullParser parser, @NonNull String name)
            throws XmlPullParserException, ParseException {
        if (parser.getEventType() != START_TAG || !parser.getName().equals(name)) {
            throwElementMissing(name);
        }
    }

    private static void validateElementEnd(@NonNull XmlPullParser parser, @NonNull String name)
            throws XmlPullParserException, ParseException {
        if (parser.getEventType() != END_TAG || !parser.getName().equals(name)) {
            throwElementNotClosed(name);
        }
    }

    private static void validateElementHasNoAttribute(@NonNull XmlPullParser parser,
            @NonNull String name) throws ParseException {
        if (parser.getAttributeCount() != 0) {
            throwElementInvalid(name);
        }
    }

    private static void throwElementMissing(@NonNull String name) throws ParseException {
        throw new ParseException(String.format("Element %s missing", name));
    }

    private static void throwElementNotClosed(@NonNull String name) throws ParseException {
        throw new ParseException(String.format("Element %s not closed", name));
    }

    private static void throwElementInvalid(@NonNull String name) throws ParseException {
        throw new ParseException(String.format("Element %s invalid", name));
    }

    private static void throwElementInvalid(@NonNull String name, @NonNull Throwable e)
            throws ParseException {
        throw new ParseException(String.format("Element %s invalid", name), e);
    }

    private static void throwAttributeUnexpected(@NonNull String parent, @NonNull String name)
            throws ParseException {
        throw new ParseException(String.format("Unexpected attribute %s.%s", parent, name));
    }

    private static void throwAttributeInvalid(@NonNull String parent, @NonNull String name)
            throws ParseException {
        throw new ParseException(String.format("Attribute %s.%s invalid", parent, name));
    }

    private static int parseInteger(@NonNull String valueString, @NonNull String parent,
            @NonNull String name) throws ParseException {
        try {
            return Integer.parseInt(valueString);
        } catch (NumberFormatException e) {
            throw new ParseException(
                    String.format("Attribute %s.%s invalid", parent, name), e);
        }
    }

    private static boolean parseBoolean(@NonNull String valueString, @NonNull String parent,
            @NonNull String name) throws ParseException {
        String valueLowerString = valueString.toLowerCase(ROOT);
        if (valueLowerString.equals("true")) {
            return true;
        } else if (!valueLowerString.equals("false")) {
            throw new ParseException(
                    String.format("Attribute %s.%s invalid", parent, name));
        }
        return false;
    }

    @IdRes
    private static int parseReference(@NonNull String reference, @NonNull String resourcePkgName,
            @NonNull Resources resources, @NonNull String parent, @NonNull String name)
            throws ParseException {
        if (!reference.startsWith("@string/")) {
            throw new ParseException(
                    String.format("String %s in %s.%s is not a reference", reference, parent,
                            name));
        }
        int id = resources.getIdentifier(reference.substring(1), null, resourcePkgName);
        if (id == Resources.ID_NULL) {
            throw new ParseException(
                    String.format("Reference %s in %s.%s missing", reference, parent, name));
        }
        return id;
    }

    @StatelessIconType
    private static int parseStatelessIconType(@NonNull String valueString, @NonNull String parent,
            @NonNull String name) throws ParseException {
        switch (valueString) {
            case ENUM_STATELESS_ICON_TYPE_NONE:
                return SafetySourcesGroup.STATELESS_ICON_TYPE_NONE;
            case ENUM_STATELESS_ICON_TYPE_PRIVACY:
                return SafetySourcesGroup.STATELESS_ICON_TYPE_PRIVACY;
            default:
                throwAttributeInvalid(parent, name);
        }
        return 0; // Unreachable
    }

    @Profile
    private static int parseProfile(@NonNull String valueString, @NonNull String parent,
            @NonNull String name) throws ParseException {
        switch (valueString) {
            case ENUM_PROFILE_PRIMARY:
                return SafetySource.PROFILE_PRIMARY;
            case ENUM_PROFILE_ALL:
                return SafetySource.PROFILE_ALL;
            default:
                throwAttributeInvalid(parent, name);
        }
        return 0; // Unreachable
    }

    @InitialDisplayState
    private static int parseInitialDisplayState(@NonNull String valueString, @NonNull String parent,
            @NonNull String name) throws ParseException {
        switch (valueString) {
            case ENUM_INITIAL_DISPLAY_STATE_ENABLED:
                return SafetySource.INITIAL_DISPLAY_STATE_ENABLED;
            case ENUM_INITIAL_DISPLAY_STATE_DISABLED:
                return SafetySource.INITIAL_DISPLAY_STATE_DISABLED;
            case ENUM_INITIAL_DISPLAY_STATE_HIDDEN:
                return SafetySource.INITIAL_DISPLAY_STATE_HIDDEN;
            default:
                throwAttributeInvalid(parent, name);
        }
        return 0; // Unreachable
    }
}
