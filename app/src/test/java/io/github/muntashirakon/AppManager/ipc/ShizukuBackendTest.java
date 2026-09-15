// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = ShizukuBackendTest.Api.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ShizukuBackendTest {
    @Implements(Shizuku.class)
    public static class Api {
        static boolean granted;
        static int uid;
        static ServiceConnection connection;
        static int unbinds;

        @Implementation public static boolean pingBinder() { return true; }
        @Implementation public static boolean isPreV11() { return false; }
        @Implementation public static int getVersion() { return 13; }
        @Implementation public static int checkSelfPermission() {
            return granted ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
        }
        @Implementation
        public static void bindUserService(Shizuku.UserServiceArgs args, ServiceConnection callback) {
            connection = callback;
            callback.onServiceConnected(new ComponentName("test", "ShizukuService"), new AMService.IAMServiceImpl() {
                @Override public int getUid() { return uid; }
                @Override public IBinder getFileSystemService() { return new Binder(); }
            });
        }
        @Implementation
        public static void unbindUserService(Shizuku.UserServiceArgs args, ServiceConnection callback, boolean remove) {
            unbinds++;
        }
    }

    @Before
    public void setUp() {
        ShizukuBackend.stop();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Api.granted = true;
        Api.uid = 2000;
        Api.unbinds = 0;
    }

    @After
    public void tearDown() {
        ShizukuBackend.stop();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void bind() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { ShizukuBackend.bind(); return null; });
        Thread thread = new Thread(task);
        thread.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!task.isDone() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            thread.join(10);
        }
        task.get(1, TimeUnit.SECONDS);
    }

    @Test
    public void bindsBothServicesAndReportsTheActualUid() throws Exception {
        bind();
        assertTrue(ShizukuBackend.alive());
        assertEquals(2000, ShizukuBackend.getService().getUid());
        assertNotNull(ShizukuBackend.getFileSystem());
        ShizukuBackend.stop();
        Api.uid = 0;
        bind();
        assertEquals(0, ShizukuBackend.getService().getUid());
    }

    @Test
    public void revokedPermissionImmediatelyDisablesCapability() throws Exception {
        bind();
        Api.granted = false;
        assertFalse(ShizukuBackend.alive());
        assertThrows(android.os.RemoteException.class, ShizukuBackend::getService);
        assertThrows(android.os.RemoteException.class, ShizukuBackend::getFileSystem);
    }

    @Test
    public void oldConnectionCannotInvalidateReplacement() throws Exception {
        bind();
        ServiceConnection old = Api.connection;
        ShizukuBackend.stop();
        bind();
        old.onServiceDisconnected(new ComponentName("test", "ShizukuService"));
        assertTrue(ShizukuBackend.alive());
        assertEquals(1, Api.unbinds);
    }
}
