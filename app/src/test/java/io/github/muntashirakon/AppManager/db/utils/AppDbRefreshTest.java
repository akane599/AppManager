// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.db.utils;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.room.Room;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.users.Users;

@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = {AppDbRefreshTest.Profiles.class, AppDbRefreshTest.Packages.class,
        AppDbRefreshTest.Permissions.class, AppDbRefreshTest.OptionalMetadata.class})
public class AppDbRefreshTest {
    private AppsDb database;
    private final Map<String, Backup> backups = new HashMap<>();

    @Implements(Users.class)
    public static class Profiles {
        @Implementation
        public static int[] getUsersIds() { return new int[]{0, 10}; }
    }

    @Implements(SelfPermissions.class)
    public static class Permissions {
        @Implementation
        public static boolean checkCrossUserPermission(int user, boolean full) { return true; }
    }

    @Implements(PackageManagerCompat.class)
    public static class Packages {
        @Implementation
        public static List<PackageInfo> getInstalledPackages(int flags, int user) {
            if (user == 10) throw new SecurityException("Profile locked");
            PackageInfo info = new PackageInfo();
            info.packageName = "current.app";
            info.applicationInfo = new ApplicationInfo();
            info.applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED;
            info.applicationInfo.uid = 10001;
            return Collections.singletonList(info);
        }
    }

    @Implements(AppDb.class)
    public static class OptionalMetadata {
        @Implementation
        protected static void updateVariableData(Context context, List<App> apps) {
            // This test exercises snapshot persistence, independently of device usage/SSAID APIs.
        }
    }

    @Before
    public void setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppsDb.class)
                .allowMainThreadQueries().build();
        ReflectionHelpers.setStaticField(AppsDb.class, "sAppsDb", database);
        database.appDao().insert(app("current.app", 0));
        database.appDao().insert(app("removed.app", 0));
        database.appDao().insert(app("work.app", 10));
        database.appDao().insert(app("unselected.app", 11));
    }

    @After
    public void tearDown() {
        database.close();
        ReflectionHelpers.setStaticField(AppsDb.class, "sAppsDb", null);
    }

    @Test
    public void refreshDeletesConfirmedMissingAppsButPreservesUnavailableProfiles() {
        refresh();
        assertEquals(1, database.appDao().getAll("current.app", 0).size());
        assertTrue(database.appDao().getAll("removed.app", 0).isEmpty());
        assertTrue(database.appDao().getAll("work.app", 10).get(0).isInstalled);
        assertTrue(database.appDao().getAll("unselected.app", 11).get(0).isInstalled);
    }

    @Test
    public void backupCannotReplaceInstalledStateOfLockedProfile() {
        Backup backup = new Backup();
        backup.packageName = "work.app";
        backup.userId = 10;
        backups.put(backup.packageName, backup);
        refresh();
        assertTrue(database.appDao().getAll("work.app", 10).get(0).isInstalled);
    }

    private void refresh() {
        new AppDb() {
            @Override
            public Map<String, Backup> getBackups(boolean load) { return new HashMap<>(backups); }
        }.updateApplications(RuntimeEnvironment.getApplication());
    }

    private static App app(String name, int user) {
        App app = new App();
        app.packageName = name;
        app.userId = user;
        app.isInstalled = true;
        app.flags = ApplicationInfo.FLAG_INSTALLED;
        return app;
    }
}
