// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.util.Pair;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.debloat.UadListParser;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class ApplicationItemSortTest {
    @Test
    public void uadRatingsSortInRiskOrderAndReverse() {
        ApplicationItem recommended = item("z", "Recommended");
        ApplicationItem advanced = item("y", "Advanced");
        ApplicationItem expert = item("x", "Expert");
        ApplicationItem unsafe = item("w", "Unsafe");
        ApplicationItem unlisted = item("a", null);
        List<ApplicationItem> apps = new ArrayList<>(Arrays.asList(unlisted, expert, advanced, unsafe, recommended));
        apps.sort(ApplicationItemSort.comparator(MainListOptions.SORT_BY_DEBLOAT_RATING, false));
        assertEquals(Arrays.asList(recommended, advanced, expert, unsafe, unlisted), apps);
        apps.sort(ApplicationItemSort.comparator(MainListOptions.SORT_BY_DEBLOAT_RATING, true));
        assertEquals(Arrays.asList(unlisted, unsafe, expert, advanced, recommended), apps);
    }

    @Test
    public void addedNumericSortsUseFullWidthValuesAndReverseWithoutOverflow() {
        ApplicationItem small = item("a", null);
        ApplicationItem big = item("z", null);
        small.appSize = small.appDataSize = small.versionCode = 1;
        big.appSize = big.appDataSize = big.versionCode = Long.MAX_VALUE;
        small.backup = new Backup();
        small.backup.backupTime = 1;
        big.backup = new Backup();
        big.backup.backupTime = Long.MAX_VALUE;
        int[] sorts = {MainListOptions.SORT_BY_APP_SIZE, MainListOptions.SORT_BY_APP_DATA_SIZE,
                MainListOptions.SORT_BY_VERSION_CODE, MainListOptions.SORT_BY_BACKUP_TIME};
        for (int sort : sorts) {
            Comparator<ApplicationItem> forward = ApplicationItemSort.comparator(sort, false);
            Comparator<ApplicationItem> reverse = ApplicationItemSort.comparator(sort, true);
            assertTrue(forward.compare(big, small) < 0);
            assertTrue(reverse.compare(big, small) > 0);
        }
        ApplicationItem noBackup = item("0", null);
        assertTrue(ApplicationItemSort.comparator(MainListOptions.SORT_BY_BACKUP_TIME, false)
                .compare(small, noBackup) < 0);
    }

    @Test
    public void missingSdkAndSignatureValuesObeyComparatorContract() {
        List<ApplicationItem> apps = new ArrayList<>();
        for (int i = 0; i < 6; ++i) apps.add(item("pkg" + i, null));
        apps.get(2).sha = new Pair<>(null, null);
        apps.get(3).sha = new Pair<>("issuer", null);
        apps.get(4).sha = new Pair<>("issuer", "SHA256");
        apps.get(5).sha = new Pair<>("Issuer", "sha256");
        apps.get(3).targetSdk = 28;
        apps.get(4).targetSdk = 36;
        apps.get(5).targetSdk = 36;
        for (int sort : new int[]{MainListOptions.SORT_BY_TARGET_SDK, MainListOptions.SORT_BY_SHA}) {
            for (boolean reverse : new boolean[]{false, true}) {
                Comparator<ApplicationItem> comparator = ApplicationItemSort.comparator(sort, reverse);
                for (ApplicationItem a : apps) {
                    assertEquals(0, comparator.compare(a, a));
                    for (ApplicationItem b : apps) {
                        assertEquals(-Integer.signum(comparator.compare(a, b)),
                                Integer.signum(comparator.compare(b, a)));
                        for (ApplicationItem c : apps) {
                            if (comparator.compare(a, b) <= 0 && comparator.compare(b, c) <= 0) {
                                assertTrue(comparator.compare(a, c) <= 0);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void tiesRemainAlphabeticalAndIdenticalLabelsUsePackageNames() {
        ApplicationItem alpha = item("a", "Recommended");
        ApplicationItem beta = item("b", "Recommended");
        assertTrue(ApplicationItemSort.comparator(MainListOptions.SORT_BY_DEBLOAT_RATING, true)
                .compare(alpha, beta) < 0);
        beta.label = alpha.label;
        assertTrue(ApplicationItemSort.comparator(MainListOptions.SORT_BY_APP_LABEL, true)
                .compare(alpha, beta) < 0);
    }

    @Test
    public void existingSortDirectionsAndMissingSdkAccessRemainUsable() {
        ApplicationItem a = item("a", null);
        ApplicationItem b = item("b", null);
        a.lastUpdateTime = 10L;
        b.lastUpdateTime = 20L;
        assertTrue(ApplicationItemSort.comparator(MainListOptions.SORT_BY_LAST_UPDATE, false).compare(b, a) < 0);
        assertTrue(ApplicationItemSort.comparator(MainListOptions.SORT_BY_APP_LABEL, true).compare(b, a) < 0);
        assertEquals(0, a.getTargetSdk());
        assertEquals(0, a.getCompileSdk());
        assertTrue(ApplicationItemSort.comparator(-1, false).compare(a, b) < 0);
    }

    private static ApplicationItem item(String name, String rating) {
        DebloatObject object = rating == null ? null
                : UadListParser.parse("{\"pkg\":{\"removal\":\"" + rating + "\"}}").get(0);
        ApplicationItem item = new ApplicationItem() {
            @Override
            public DebloatObject getBloatwareInfo() {
                return object;
            }
        };
        item.label = name;
        item.packageName = name;
        return item;
    }
}
