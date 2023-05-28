package com.android.permissioncontroller.ext;

import android.icu.text.BreakIterator;

public class StringUtils {

    public static boolean validateName(String s, int maxLength) {
        if (!isUtf16(s)) {
            return false;
        }

        int cnt = countGraphemeClusters(s);

        return cnt > 0 && cnt <= maxLength;
    }

    public static int countGraphemeClusters(String s) {
        BreakIterator breakIterator = BreakIterator.getCharacterInstance();
        breakIterator.setText(s);

        int cnt = 0;
        while (breakIterator.next() != BreakIterator.DONE) {
            ++ cnt;
        }
        return cnt;
    }

    public static boolean isUtf16(String str) {
        return isUtf16(str, 0, str.length());
    }

    public static boolean isUtf16(String str, int a, int b) {
        while (a != b) {
            int c1 = str.charAt(a++);

            if (c1 < 0xd800 || c1 > 0xdfff) {
                continue;
            }

            // check validity of a surrogate pair

            // d800..dbff
            if (c1 <= 0xdbff && a != b) {
                int c2 = str.charAt(a++);
                if (c2 >= 0xdc00 && c2 <= 0xdfff) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }
}
