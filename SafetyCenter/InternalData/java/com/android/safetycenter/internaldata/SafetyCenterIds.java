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

package com.android.safetycenter.internaldata;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.annotation.NonNull;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

/** A class to facilitate working with Safety Center IDs. */
@RequiresApi(TIRAMISU)
public final class SafetyCenterIds {

    private static final int ENCODING_FLAGS = Base64.NO_WRAP | Base64.URL_SAFE;

    private SafetyCenterIds() {}

    /**
     * Converts a String to a {@link SafetyCenterEntryGroupId}.
     *
     * <p>Throws an {@link IllegalArgumentException} if the String couldn't be converted to a {@link
     * SafetyCenterEntryGroupId}.
     */
    @NonNull
    public static SafetyCenterEntryGroupId entryGroupIdFromString(@NonNull String encoded) {
        return decodeToProto(SafetyCenterEntryGroupId.parser(), encoded);
    }

    /**
     * Converts a String to a {@link SafetyCenterEntryId}.
     *
     * <p>Throws an {@link IllegalArgumentException} if the String couldn't be converted to a {@link
     * SafetyCenterEntryId}.
     */
    @NonNull
    public static SafetyCenterEntryId entryIdFromString(@NonNull String encoded) {
        return decodeToProto(SafetyCenterEntryId.parser(), encoded);
    }

    /**
     * Converts a String to a {@link SafetyCenterIssueId}.
     *
     * <p>Throws an {@link IllegalArgumentException} if the String couldn't be converted to a {@link
     * SafetyCenterIssueId}.
     */
    @NonNull
    public static SafetyCenterIssueId issueIdFromString(@NonNull String encoded) {
        return decodeToProto(SafetyCenterIssueId.parser(), encoded);
    }

    /**
     * Converts a String to a {@link SafetyCenterIssueActionId}.
     *
     * <p>Throws an {@link IllegalArgumentException} if the String couldn't be converted to a {@link
     * SafetyCenterIssueActionId}.
     */
    @NonNull
    public static SafetyCenterIssueActionId issueActionIdFromString(@NonNull String encoded) {
        return decodeToProto(SafetyCenterIssueActionId.parser(), encoded);
    }

    /** Encodes a Safety Center id to a String. */
    @NonNull
    public static String encodeToString(@NonNull MessageLite message) {
        return Base64.encodeToString(message.toByteArray(), ENCODING_FLAGS);
    }

    @NonNull
    private static <T extends MessageLite> T decodeToProto(@NonNull Parser<T> parser,
            @NonNull String encoded) {
        try {
            return parser.parseFrom(Base64.decode(encoded, ENCODING_FLAGS));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid ID: %s couldn't be parsed with %s", encoded,
                            parser.getClass().getSimpleName()));
        }
    }
}
