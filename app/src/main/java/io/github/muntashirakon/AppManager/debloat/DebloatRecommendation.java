// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.debloat;

/** One current-user snapshot. Missing measurements stay unknown, not zero. */
final class DebloatRecommendation implements Comparable<DebloatRecommendation> {
    final String packageName;
    final String label;
    boolean running;
    boolean background;
    long memoryBytes = -1;
    double cpuPercent = -1;

    DebloatRecommendation(String packageName, String label) {
        this.packageName = packageName;
        this.label = label;
    }

    static boolean isCandidate(boolean recommended, boolean installed, boolean frozen,
                               boolean hasWarning, boolean hasDependents) {
        return recommended && installed && !frozen && !hasWarning && !hasDependents;
    }

    static String packageNameOf(String commandLine) {
        if (commandLine == null) return "";
        int end = commandLine.indexOf('\0');
        String name = end < 0 ? commandLine : commandLine.substring(0, end);
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(0, colon);
    }

    void addProcess(boolean isBackground, long residentPages, long pageSize,
                    long cpuSeconds, long elapsedSeconds) {
        running = true;
        background |= isBackground;
        if (residentPages >= 0 && pageSize > 0 && residentPages <= Long.MAX_VALUE / pageSize) {
            long bytes = residentPages * pageSize;
            long previous = Math.max(0, memoryBytes);
            memoryBytes = previous > Long.MAX_VALUE - bytes ? Long.MAX_VALUE : previous + bytes;
        }
        if (cpuSeconds >= 0 && elapsedSeconds > 0) {
            cpuPercent = Math.max(0, cpuPercent) + cpuSeconds * 100.0 / elapsedSeconds;
        }
    }

    @Override
    public int compareTo(DebloatRecommendation other) {
        int result = Boolean.compare(other.background, background);
        if (result == 0) result = Boolean.compare(other.running, running);
        if (result == 0) result = Double.compare(other.cpuPercent, cpuPercent);
        if (result == 0) result = Long.compare(other.memoryBytes, memoryBytes);
        if (result == 0) result = label.compareToIgnoreCase(other.label);
        return result != 0 ? result : packageName.compareTo(other.packageName);
    }
}
