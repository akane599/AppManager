// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Looper;

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
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = ServiceConnectionWrapperTest.ShadowRootService.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ServiceConnectionWrapperTest {
    @Implements(RootService.class)
    public static class ShadowRootService {
        static ServiceConnection lastConnection;

        @Implementation
        public static void bind(Intent intent, ServiceConnection connection) {
            lastConnection = connection;
            connection.onServiceConnected(intent.getComponent(), new Binder());
        }

        @Implementation
        public static void unbind(ServiceConnection connection) {
        }
    }

    private void bind(ServiceConnectionWrapper wrapper) throws Exception {
        FutureTask<?> task = new FutureTask<>(wrapper::bindService);
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
    public void lateCallbacksCannotReplaceOrDisconnectNewBinder() throws Exception {
        AtomicInteger deaths = new AtomicInteger();
        ComponentName name = new ComponentName("test", "Service");
        ServiceConnectionWrapper wrapper = new ServiceConnectionWrapper(name, deaths::incrementAndGet);
        bind(wrapper);
        ServiceConnection old = ShadowRootService.lastConnection;
        wrapper.unbindService();
        assertFalse(wrapper.isBinderActive());
        bind(wrapper);
        Object current = wrapper.getService();
        old.onServiceDisconnected(name);
        old.onServiceConnected(name, new Binder());
        assertSame(current, wrapper.getService());
        assertEquals(0, deaths.get());
        ShadowRootService.lastConnection.onServiceDisconnected(name);
        assertFalse(wrapper.isBinderActive());
        assertEquals(1, deaths.get());
    }
    @Test
    public void capabilityReadDoesNotWaitForTheBindingMonitor() throws Exception {
        Object monitor = org.robolectric.util.ReflectionHelpers.getStaticField(
                LocalServices.class, "sAMServiceConnectionWrapper");
        java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Thread binderThread = new Thread(() -> {
            synchronized (monitor) {
                locked.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        binderThread.start();
        assertTrue(locked.await(1, TimeUnit.SECONDS));
        FutureTask<Boolean> read = new FutureTask<>(() -> {
            try {
                LocalServices.getAmService();
                return false;
            } catch (android.os.RemoteException expected) {
                return true;
            }
        });
        new Thread(read).start();
        try {
            assertTrue(read.get(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            binderThread.join(1000);
        }
    }
}
