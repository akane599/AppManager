// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.content.pm.ApplicationInfo;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Collator;
import java.util.Comparator;

import io.github.muntashirakon.AppManager.debloat.DebloatObject;

final class ApplicationItemSort {
    private ApplicationItemSort() {
    }

    @NonNull
    static Comparator<ApplicationItem> comparator(@MainListOptions.SortOrder int sortBy, boolean reverse) {
        Collator collator = Collator.getInstance();
        Comparator<ApplicationItem> byLabel = (a, b) -> collator.compare(a.label, b.label);
        Comparator<ApplicationItem> primary;
        switch (sortBy) {
            case MainListOptions.SORT_BY_PACKAGE_NAME:
                primary = Comparator.comparing(item -> item.packageName);
                break;
            case MainListOptions.SORT_BY_DOMAIN:
                primary = Comparator.comparing(item -> (item.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                break;
            case MainListOptions.SORT_BY_LAST_UPDATE:
                primary = (a, b) -> compareNullable(b.lastUpdateTime, a.lastUpdateTime);
                break;
            case MainListOptions.SORT_BY_TOTAL_SIZE:
                primary = (a, b) -> compareNullable(b.totalSize, a.totalSize);
                break;
            case MainListOptions.SORT_BY_APP_SIZE:
                primary = (a, b) -> Long.compare(b.appSize, a.appSize);
                break;
            case MainListOptions.SORT_BY_APP_DATA_SIZE:
                primary = (a, b) -> Long.compare(b.appDataSize, a.appDataSize);
                break;
            case MainListOptions.SORT_BY_DATA_USAGE:
                primary = (a, b) -> compareNullable(b.dataUsage, a.dataUsage);
                break;
            case MainListOptions.SORT_BY_OPEN_COUNT:
                primary = (a, b) -> Integer.compare(b.openCount, a.openCount);
                break;
            case MainListOptions.SORT_BY_INSTALLATION_DATE:
                primary = (a, b) -> Long.compare(b.firstInstallTime, a.firstInstallTime);
                break;
            case MainListOptions.SORT_BY_SCREEN_TIME:
                primary = (a, b) -> compareNullable(b.screenTime, a.screenTime);
                break;
            case MainListOptions.SORT_BY_LAST_USAGE_TIME:
                primary = (a, b) -> compareNullable(b.lastUsageTime, a.lastUsageTime);
                break;
            case MainListOptions.SORT_BY_TARGET_SDK:
                primary = (a, b) -> compareNullable(a.targetSdk, b.targetSdk);
                break;
            case MainListOptions.SORT_BY_SHARED_ID:
                primary = Comparator.comparingInt(item -> item.uid);
                break;
            case MainListOptions.SORT_BY_SHA:
                primary = (a, b) -> compareSignatures(a.sha, b.sha);
                break;
            case MainListOptions.SORT_BY_BLOCKED_COMPONENTS:
                primary = (a, b) -> compareNullable(b.blockedCount, a.blockedCount);
                break;
            case MainListOptions.SORT_BY_FROZEN_APP:
                primary = (a, b) -> Boolean.compare(b.isDisabled, a.isDisabled);
                break;
            case MainListOptions.SORT_BY_BACKUP:
                primary = (a, b) -> Boolean.compare(b.backup != null, a.backup != null);
                break;
            case MainListOptions.SORT_BY_BACKUP_TIME:
                primary = (a, b) -> Long.compare(b.backup != null ? b.backup.backupTime : 0,
                        a.backup != null ? a.backup.backupTime : 0);
                break;
            case MainListOptions.SORT_BY_LAST_ACTION:
                primary = (a, b) -> compareNullable(b.lastActionTime, a.lastActionTime);
                break;
            case MainListOptions.SORT_BY_TRACKERS:
                primary = (a, b) -> Integer.compare(b.getTrackerCount(), a.getTrackerCount());
                break;
            case MainListOptions.SORT_BY_DEBLOAT_RATING:
                primary = Comparator.comparingInt(ApplicationItemSort::debloatRank);
                break;
            case MainListOptions.SORT_BY_VERSION_CODE:
                primary = (a, b) -> Long.compare(b.versionCode, a.versionCode);
                break;
            case MainListOptions.SORT_BY_APP_LABEL:
            default:
                primary = byLabel;
                break;
        }
        if (reverse) primary = primary.reversed();
        // Preserve alphabetical ties in both directions, with deterministic order for identical labels.
        return primary.thenComparing(byLabel).thenComparing(item -> item.packageName);
    }

    private static int debloatRank(@NonNull ApplicationItem item) {
        DebloatObject info = item.getBloatwareInfo();
        // Recommended, Advanced, Expert, Unsafe, then apps absent from UAD.
        return info != null ? info.getRemoval() : Integer.MAX_VALUE;
    }

    private static <T extends Comparable<T>> int compareNullable(@Nullable T a, @Nullable T b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    private static int compareSignatures(@Nullable Pair<String, String> a, @Nullable Pair<String, String> b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        Comparator<String> strings = Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);
        int issuer = strings.compare(a.first, b.first);
        return issuer != 0 ? issuer : strings.compare(a.second, b.second);
    }
}
