// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.algo;

import static org.junit.Assert.*;

import org.junit.Test;

public class AhoCorasickTest {
    @Test
    public void matchesSuffixesInDiscoveryOrderWithoutNativeLibrary() {
        try (AhoCorasick matcher = new AhoCorasick(new String[]{"he", "she", "hers", "his"})) {
            assertArrayEquals(new int[]{1, 0, 2}, matcher.search("ushers"));
            assertArrayEquals(new int[0], matcher.search("xyz"));
        }
    }

    @Test
    public void preservesOverlappingAndRepeatedMatches() {
        try (AhoCorasick matcher = new AhoCorasick(new String[]{"a", "aa"})) {
            assertArrayEquals(new int[]{0, 1, 0, 1, 0}, matcher.search("aaa"));
        }
    }

    @Test
    public void supportsUnicodeAndIdempotentClose() {
        AhoCorasick matcher = new AhoCorasick(new String[]{"é", "éx"});
        assertArrayEquals(new int[]{0, 1}, matcher.search("éx"));
        matcher.close();
        matcher.close();
        assertThrows(IllegalStateException.class, () -> matcher.search("éx"));
    }
}
