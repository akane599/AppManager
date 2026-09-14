// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.debloat;

import org.junit.Test;

import static org.junit.Assert.*;

public class DebloatRecommendationTest {
    @Test
    public void onlyEnabledInstalledRecommendedAppsWithoutWarningsOrDependentsQualify() {
        assertTrue(DebloatRecommendation.isCandidate(true, true, false, false, false));
        assertFalse(DebloatRecommendation.isCandidate(false, true, false, false, false));
        assertFalse(DebloatRecommendation.isCandidate(true, false, false, false, false));
        assertFalse(DebloatRecommendation.isCandidate(true, true, true, false, false));
        assertFalse(DebloatRecommendation.isCandidate(true, true, false, true, false));
        assertFalse(DebloatRecommendation.isCandidate(true, true, false, false, true));
    }

    @Test
    public void missingDataStaysUnknownAndInvalidCpuTimeIsIgnored() {
        DebloatRecommendation item = new DebloatRecommendation("a", "A");
        assertFalse(item.running);
        assertEquals(-1, item.memoryBytes);
        assertEquals(-1, item.cpuPercent, 0);
        item.addProcess(false, -1, 4096, 5, 0);
        assertTrue(item.running);
        assertFalse(item.background);
        assertEquals(-1, item.memoryBytes);
        assertEquals(-1, item.cpuPercent, 0);
    }

    @Test
    public void aggregatesProcessesUsingActualPageSizeAndLifetimeCpu() {
        DebloatRecommendation item = new DebloatRecommendation("a", "A");
        item.addProcess(true, 10, 16384, 2, 10);
        item.addProcess(false, 5, 16384, 1, 10);
        assertEquals(15 * 16384, item.memoryBytes);
        assertEquals(30, item.cpuPercent, 0);
        assertTrue(item.background);
    }

    @Test
    public void ranksObservedBackgroundActivityBeforeUnknownAndBreaksTiesStably() {
        DebloatRecommendation background = new DebloatRecommendation("b", "B");
        DebloatRecommendation active = new DebloatRecommendation("a", "A");
        DebloatRecommendation unknown = new DebloatRecommendation("c", "C");
        background.addProcess(true, 1, 4096, 0, 10);
        active.addProcess(false, 100, 4096, 9, 10);
        assertTrue(background.compareTo(active) < 0);
        assertTrue(active.compareTo(unknown) < 0);
        assertTrue(new DebloatRecommendation("a", "Same")
                .compareTo(new DebloatRecommendation("b", "Same")) < 0);
    }

    @Test
    public void matchesProcessNamesWithoutTreatingArgumentsOrPrefixesAsPackages() {
        assertEquals("com.example", DebloatRecommendation.packageNameOf("com.example:worker\0argument"));
        assertEquals("com.example.other", DebloatRecommendation.packageNameOf("com.example.other"));
        assertEquals("/system/bin/tool", DebloatRecommendation.packageNameOf("/system/bin/tool\0com.example"));
        assertEquals("", DebloatRecommendation.packageNameOf(null));
    }

    @Test
    public void ranksCpuThenRamWithinTheSameActivityGroup() {
        DebloatRecommendation cpu = new DebloatRecommendation("a", "A");
        DebloatRecommendation ram = new DebloatRecommendation("b", "B");
        cpu.addProcess(true, 1, 4096, 5, 10);
        ram.addProcess(true, 100, 4096, 1, 10);
        assertTrue(cpu.compareTo(ram) < 0);
        ram.cpuPercent = cpu.cpuPercent;
        assertTrue(ram.compareTo(cpu) < 0);
    }
}
