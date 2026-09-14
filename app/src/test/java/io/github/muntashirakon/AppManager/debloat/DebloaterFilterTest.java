// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.debloat;

import org.junit.Test;
import static org.junit.Assert.*;

public class DebloaterFilterTest {
    @Test
    public void oppositeSelectionsIncludeBothStatesAndGroupsStayIndependent() {
        assertFalse(DebloaterFilter.matchesPair(1, 1, 2, false, false));
        assertFalse(DebloaterFilter.matchesPair(2, 1, 2, false, false));
        for (boolean value : new boolean[]{false, true}) {
            assertTrue(DebloaterFilter.matchesPair(0, 1, 2, value, !value));
            assertTrue(DebloaterFilter.matchesPair(3, 1, 2, value, !value));
            assertTrue(DebloaterFilter.matchesPair(4, 1, 2, value, !value));
            assertEquals(value, DebloaterFilter.matchesPair(1, 1, 2, value, !value));
            assertEquals(!value, DebloaterFilter.matchesPair(2, 1, 2, value, !value));
        }
    }
}
