// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.debloat;

final class DebloaterFilter {
    static boolean matchesPair(int flags, int positive, int negative, boolean value, boolean oppositeValue) {
        int selected = flags & (positive | negative);
        return selected == 0 || selected == (positive | negative)
                || ((selected & positive) != 0 && value)
                || ((selected & negative) != 0 && oppositeValue);
    }
}
