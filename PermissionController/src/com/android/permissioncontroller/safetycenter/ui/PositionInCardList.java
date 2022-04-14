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

package com.android.permissioncontroller.safetycenter.ui;

import android.content.Context;

import com.android.permissioncontroller.R;

/**
 * Determines the correct background drawable for a given element in the Safety Center card list.
 *
 * <p>This class helps transform a flat list of elements (which are typically Preferences in the
 * PreferenceScreen's recycler view) into a visually grouped list of cards by providing the correct
 * background drawable for each element based on both its position in the flat list of elements and
 * its conceptual position in the card list. While Preferences have a nested XML structure, with
 * Preferences nested inside of PreferenceGroups, they are displayed as a flat list of views, with
 * the PreferenceGroup's view inserted as a header above its constituent preference's views.
 *
 * <p>A list is a group conceptually-related of cards. The top card in the list has large top
 * corners and the bottom card in the list has large bottom corners. Corners between cards in the
 * list are small. In the Safety Center, the entry list is a single list.
 *
 * <p>A card is a group of one or more elements that appear as a single visual card, without corners
 * between its constituent elements. In the Safety Center, a single expanded entry list group is a
 * single card composed of a list of separate preferences (one for the header and one for each entry
 * in the group).
 */
enum PositionInCardList {
    LIST_START_END,
    LIST_START,
    LIST_START_CARD_END,
    CARD_START,
    CARD_START_END,
    CARD_START_LIST_END,
    CARD_ELEMENT,
    CARD_END,
    LIST_END;

    int toBackgroundDrawableResId() {
        switch (this) {
            case LIST_START_END:
                return R.drawable.safety_entity_top_large_bottom_large_background;
            case LIST_START:
                return R.drawable.safety_entity_top_large_bottom_flat_background;
            case LIST_START_CARD_END:
                return R.drawable.safety_entity_top_large_bottom_small_background;
            case CARD_START:
                return R.drawable.safety_entity_top_small_bottom_flat_background;
            case CARD_START_END:
                return R.drawable.safety_entity_top_small_bottom_small_background;
            case CARD_START_LIST_END:
                return R.drawable.safety_entity_top_small_bottom_large_background;
            case CARD_ELEMENT:
                return R.drawable.safety_entity_top_flat_bottom_flat_background;
            case CARD_END:
                return R.drawable.safety_entity_top_flat_bottom_small_background;
            case LIST_END:
                return R.drawable.safety_entity_top_flat_bottom_large_background;
        }
        throw new IllegalArgumentException(String.format("Unexpected PositionInList: %s", name()));
    }

    int toTopMargin(Context context) {
        switch (this) {
            case CARD_START:
            case CARD_START_END:
                return context.getResources()
                        .getDimensionPixelSize(R.dimen.safety_center_card_margin);
            case LIST_START:
            case LIST_START_CARD_END:
                return context.getResources()
                        .getDimensionPixelSize(R.dimen.safety_center_list_margin);
            default:
                return 0;
        }
    }

    static PositionInCardList calculate(boolean isListStart, boolean isListEnd) {
        return calculate(isListStart, isListEnd, !isListStart, !isListEnd);
    }

    static PositionInCardList calculate(
            boolean isListStart, boolean isListEnd, boolean isCardStart, boolean isCardEnd) {
        if (isListStart && isListEnd) {
            return PositionInCardList.LIST_START_END;
        } else if (isListStart && isCardEnd) {
            return PositionInCardList.LIST_START_CARD_END;
        } else if (isListEnd && isCardStart) {
            return PositionInCardList.CARD_START_LIST_END;
        } else if (isCardStart && isCardEnd) {
            return PositionInCardList.CARD_START_END;
        } else if (isListStart) {
            return PositionInCardList.LIST_START;
        } else if (isListEnd) {
            return PositionInCardList.LIST_END;
        } else if (isCardStart) {
            return PositionInCardList.CARD_START;
        } else if (isCardEnd) {
            return PositionInCardList.CARD_END;
        } else {
            return PositionInCardList.CARD_ELEMENT;
        }
    }
}
