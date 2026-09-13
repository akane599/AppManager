// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.debloat.UadListParser;
import io.github.muntashirakon.AppManager.filters.FilterItem;
import io.github.muntashirakon.AppManager.filters.options.ComponentsOption;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowPermissions;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, shadows = ShadowPermissions.class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class MainListOptionsTest {
    private static int nextPackageId;
    @Test
    public void everyRatingCombinationUnionsRatingsAndIntersectsRunningAndSystemApps() {
        String[] ratings = {"Recommended", "Advanced", "Expert", "Unsafe"};
        int[] flags = {MainListOptions.FILTER_UAD_RECOMMENDED, MainListOptions.FILTER_UAD_ADVANCED,
                MainListOptions.FILTER_UAD_EXPERT, MainListOptions.FILTER_UAD_UNSAFE};
        List<ApplicationItem> apps = new ArrayList<>();
        for (String rating : ratings) {
            apps.add(app(rating, true, true));
            apps.add(app(rating, false, true));
            apps.add(app(rating, true, false));
        }
        apps.add(app(null, true, true));
        for (int mask = 1; mask < 16; ++mask) {
            int selected = MainListOptions.FILTER_RUNNING_APPS | MainListOptions.FILTER_SYSTEM_APPS;
            List<ApplicationItem> expected = new ArrayList<>();
            for (int i = 0; i < flags.length; ++i) {
                if ((mask & (1 << i)) != 0) {
                    selected |= flags[i];
                    expected.add(apps.get(i * 3));
                }
            }
            FilterItem filter = MainListOptions.getFilterItemFromFlags(selected);
            assertEquals(1, filter.getTimesRunningOptionUsed());
            assertEquals(expected, filter.getFilteredAppInfoList(apps));
        }
    }

    @Test
    public void allDebloatableIncludesUnsafeAndUnknownButExcludesUnlistedApps() {
        List<ApplicationItem> apps = Arrays.asList(app("Recommended", false, true),
                app("Unsafe", false, true), app("Future rating", false, true), app(null, false, true));
        assertEquals(apps, MainListOptions.getFilterItemFromFlags(0).getFilteredAppInfoList(apps));
        assertEquals(apps.subList(0, 3), MainListOptions.getFilterItemFromFlags(
                MainListOptions.FILTER_DEBLOATABLE_APPS).getFilteredAppInfoList(apps));
        assertEquals(apps.subList(1, 3), MainListOptions.getFilterItemFromFlags(
                MainListOptions.FILTER_UAD_UNSAFE).getFilteredAppInfoList(apps));
        assertEquals(apps.subList(0, 1), MainListOptions.getFilterItemFromFlags(
                MainListOptions.FILTER_DEBLOATABLE_APPS | MainListOptions.FILTER_UAD_RECOMMENDED)
                .getFilteredAppInfoList(apps));
    }

    @Test
    public void trackerFiltersUseCachedCountsAndCombineWithUad() {
        ApplicationItem tracked = app("Recommended", true, true);
        tracked.trackerCount = 3;
        ApplicationItem clean = app("Recommended", true, true);
        ApplicationItem unlisted = app(null, true, true);
        unlisted.trackerCount = 2;
        List<ApplicationItem> apps = Arrays.asList(tracked, clean, unlisted);
        assertEquals(Collections.singletonList(tracked), MainListOptions.getFilterItemFromFlags(
                MainListOptions.FILTER_UAD_RECOMMENDED | MainListOptions.FILTER_APPS_WITH_TRACKERS)
                .getFilteredAppInfoList(apps));
        assertEquals(Collections.singletonList(clean), MainListOptions.getFilterItemFromFlags(
                MainListOptions.FILTER_APPS_WITHOUT_TRACKERS).getFilteredAppInfoList(apps));
    }

    @Test
    public void extraAppTypeFiltersRequireEverySelectedProperty() {
        ApplicationItem match = app("Recommended", true, true);
        match.flags |= ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        match.debuggable = true;
        match.isPersistent = true;
        ApplicationItem ordinary = app("Recommended", true, true);
        int flags = MainListOptions.FILTER_UPDATED_SYSTEM_APPS | MainListOptions.FILTER_DEBUGGABLE_APPS
                | MainListOptions.FILTER_PERSISTENT_APPS | MainListOptions.FILTER_UAD_RECOMMENDED;
        List<ApplicationItem> apps = Arrays.asList(match, ordinary);
        assertEquals(Collections.singletonList(match), MainListOptions.getFilterItemFromFlags(flags)
                .getFilteredAppInfoList(apps));
        match.isPersistent = false;
        assertEquals(Collections.emptyList(), MainListOptions.getFilterItemFromFlags(flags)
                .getFilteredAppInfoList(apps));
    }

    @Test
    public void withoutActivitiesIncludesAppsWithNoComponents() {
        Fixture background = app("Recommended", true, true);
        Fixture ui = app("Recommended", true, true);
        ui.components = Collections.singletonMap(new ActivityInfo(), ComponentsOption.COMPONENT_TYPE_ACTIVITY);
        List<ApplicationItem> apps = Arrays.asList(background, ui);
        assertEquals(Collections.singletonList(background), MainListOptions.getFilterItemFromFlags(
                MainListOptions.FILTER_APPS_WITHOUT_ACTIVITIES).getFilteredAppInfoList(apps));
        assertEquals(Collections.singletonList(ui), MainListOptions.getFilterItemFromFlags(
                MainListOptions.FILTER_APPS_WITH_ACTIVITIES).getFilteredAppInfoList(apps));
    }

    @Test
    public void savedFlagsAndSerializedFiltersKeepRatingsAndRunningTogether() throws Exception {
        int flags = MainListOptions.FILTER_RUNNING_APPS | MainListOptions.FILTER_UAD_ADVANCED
                | MainListOptions.FILTER_UAD_EXPERT | MainListOptions.FILTER_PERSISTENT_APPS;
        int originalFlags = Prefs.MainPage.getFilters();
        int originalSort = Prefs.MainPage.getSortOrder();
        try {
            Prefs.MainPage.setFilters(flags);
            Prefs.MainPage.setSortOrder(MainListOptions.SORT_BY_DEBLOAT_RATING);
            assertEquals(flags, Prefs.MainPage.getFilters());
            assertEquals(MainListOptions.SORT_BY_DEBLOAT_RATING, Prefs.MainPage.getSortOrder());
            FilterItem filter = FilterItem.DESERIALIZER.deserialize(
                    MainListOptions.getFilterItemFromFlags(Prefs.MainPage.getFilters()).serializeToJson());
            ApplicationItem match = app("Advanced", true, true);
            match.isPersistent = true;
            List<ApplicationItem> apps = Arrays.asList(match, app("Expert", false, true),
                    app("Recommended", true, true));
            assertEquals(Collections.singletonList(match), filter.getFilteredAppInfoList(apps));
            assertEquals(1, filter.getTimesRunningOptionUsed());
        } finally {
            Prefs.MainPage.setFilters(originalFlags);
            Prefs.MainPage.setSortOrder(originalSort);
        }
    }

    @Test
    public void packageIndexReturnsBundledMetadataAndCachesMissingPackages() {
        List<DebloatObject> objects = StaticDataset.getDebloatObjects();
        assertEquals(5372, objects.size());
        for (DebloatObject object : objects) {
            assertSame(object, StaticDataset.getDebloatObject(object.packageName));
        }
        assertNull(StaticDataset.getDebloatObject("nonexistent.test.package"));
        assertNull(StaticDataset.getDebloatObject("nonexistent.test.package"));
        ApplicationItem item = new ApplicationItem();
        item.packageName = objects.get(0).packageName;
        assertSame(objects.get(0), item.getBloatwareInfo());
    }

    private static Fixture app(String rating, boolean running, boolean system) {
        Fixture item = new Fixture();
        item.packageName = "test.package" + nextPackageId++;
        item.label = item.packageName;
        item.isSystem = system;
        item.setRunning(running);
        if (rating != null) {
            item.bloatware = UadListParser.parse("{\"pkg\":{\"removal\":\"" + rating + "\"}}").get(0);
        }
        return item;
    }

    private static class Fixture extends ApplicationItem {
        DebloatObject bloatware;
        Map<ComponentInfo, Integer> components = Collections.emptyMap();

        @Override
        public DebloatObject getBloatwareInfo() {
            return bloatware;
        }

        @Override
        public Map<ComponentInfo, Integer> getAllComponents() {
            return components;
        }

        @Override
        public Map<ComponentInfo, Integer> getTrackerComponents() {
            throw new AssertionError("Main-list tracker filters must use the cached count");
        }
    }
}
