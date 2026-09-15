// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import rikka.shizuku.Shizuku;

/** A single user service supplies both AM operations and the remote filesystem. */
public final class ShizukuBackend {
    private static volatile Connection sConnection;

    private ShizukuBackend() {
    }

    public static boolean isAvailable() {
        try {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Shizuku.pingBinder()
                    && !Shizuku.isPreV11() && Shizuku.getVersion() >= 12;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static final class Connection implements ServiceConnection {
        final CountDownLatch ready = new CountDownLatch(1);
        final Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(BuildConfig.APPLICATION_ID, ShizukuService.class.getName()))
                .tag("app-manager-" + UserHandleHidden.myUserId())
                .processNameSuffix("shizuku")
                .version(BuildConfig.VERSION_CODE)
                .daemon(false)
                .debuggable(BuildConfig.DEBUG);
        volatile IAMService service;
        volatile IBinder fileSystem;

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (sConnection != this) return;
            try {
                IAMService candidate = IAMService.Stub.asInterface(binder);
                IBinder fs = candidate.getFileSystemService();
                if (fs == null || !fs.pingBinder()) throw new RemoteException("Filesystem unavailable");
                binder.linkToDeath(this::lost, 0);
                fileSystem = fs;
                service = candidate;
            } catch (RemoteException | RuntimeException e) {
                Log.e("ShizukuBackend", "Could not connect user service", e);
            } finally {
                ready.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            lost();
        }

        private void lost() {
            service = null;
            fileSystem = null;
            ready.countDown();
            if (sConnection == this) LocalServices.onServiceBinderDied();
        }
    }

    public static boolean isBound() {
        return sConnection != null;
    }

    public static boolean alive() {
        Connection c = sConnection;
        IAMService service = c != null ? c.service : null;
        IBinder fs = c != null ? c.fileSystem : null;
        return service != null && fs != null && service.asBinder().pingBinder() && fs.pingBinder()
                && hasPermission();
    }

    @WorkerThread
    static void bind() throws RemoteException {
        if (!hasPermission()) throw new RemoteException("Shizuku permission unavailable");
        stop();
        Connection c = new Connection();
        sConnection = c;
        ThreadUtils.postOnMainThread(() -> {
            if (sConnection != c) return;
            try {
                Shizuku.bindUserService(c.args, c);
            } catch (RuntimeException e) {
                Log.e("ShizukuBackend", "Could not start user service", e);
                c.ready.countDown();
            }
        });
        try {
            if (!c.ready.await(30, TimeUnit.SECONDS) || !alive()) {
                throw new RemoteException("Shizuku user service did not become ready");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Interrupted connecting to Shizuku");
        } finally {
            if (!alive()) stop();
        }
    }

    @NonNull
    static IAMService getService() throws RemoteException {
        Connection c = sConnection;
        IAMService service = c != null ? c.service : null;
        if (service == null || !service.asBinder().pingBinder() || !hasPermission()) {
            throw new RemoteException("Shizuku service unavailable");
        }
        return service;
    }

    @NonNull
    static IBinder getFileSystem() throws RemoteException {
        Connection c = sConnection;
        IBinder binder = c != null ? c.fileSystem : null;
        if (binder == null || !binder.pingBinder() || !hasPermission()) {
            throw new RemoteException("Shizuku filesystem unavailable");
        }
        return binder;
    }

    static void stop() {
        Connection c = sConnection;
        sConnection = null;
        if (c == null) return;
        c.ready.countDown();
        ThreadUtils.postOnMainThread(() -> {
            try {
                if (Shizuku.pingBinder()) Shizuku.unbindUserService(c.args, c, true);
            } catch (RuntimeException e) {
                Log.w("ShizukuBackend", "Could not release user service", e);
            }
        });
    }
}
