// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shizuku;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.io.IoUtils;
import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Helpers around <a href="https://shizuku.rikka.app">Shizuku</a> and its Magisk variant, Sui.
 * <p>
 * Shizuku runs a privileged process (as {@code shell} normally, or as {@code root} for Sui) and lets the apps
 * that the user authorised borrow it. App Manager only needs one thing from it: the ability to run a shell
 * command as that user, which is enough to launch the usual App Manager server. Everything above that layer,
 * i.e. the local server, {@code AMService} and the binder proxying, is shared with the ADB and root modes.
 */
public final class ShizukuUtils {
    public static final String TAG = ShizukuUtils.class.getSimpleName();

    public static final String PACKAGE_NAME_SHIZUKU = "moe.shizuku.privileged.api";

    /**
     * An arbitrary but stable request code. Shizuku echoes it back to the permission listener.
     */
    private static final int PERMISSION_REQUEST_CODE = 0x414d; // “AM”

    private ShizukuUtils() {
    }

    /**
     * Whether this device can talk to Shizuku at all. The Shizuku libraries declare a minimum of API 24, which
     * is also the point below which {@code ShizukuProvider} — and hence the binder delivery — is disabled.
     */
    @AnyThread
    @NoOps
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    /**
     * Whether a usable Shizuku service is currently bound to this process.
     */
    @AnyThread
    @NoOps
    public static boolean isServiceRunning() {
        if (!isSupported()) {
            return false;
        }
        try {
            return Shizuku.pingBinder();
        } catch (Throwable th) {
            // The library throws when it is used on a device it does not support
            Log.w(TAG, "Could not reach Shizuku.", th);
            return false;
        }
    }

    /**
     * Shizuku delivers its binder asynchronously after the process has started, so a check right at startup can
     * fail even though Shizuku is running. Wait for the binder before giving up.
     */
    @WorkerThread
    @NoOps
    public static boolean awaitService(long timeout, @NonNull TimeUnit unit) {
        if (!isSupported()) {
            return false;
        }
        if (isServiceRunning()) {
            return true;
        }
        CountDownLatch binderReceived = new CountDownLatch(1);
        Shizuku.OnBinderReceivedListener listener = binderReceived::countDown;
        try {
            Shizuku.addBinderReceivedListenerSticky(listener);
        } catch (Throwable th) {
            Log.w(TAG, "Could not listen for the Shizuku binder.", th);
            return false;
        }
        try {
            if (!binderReceived.await(timeout, unit)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            Shizuku.removeBinderReceivedListener(listener);
        }
        return isServiceRunning();
    }

    /**
     * Whether Shizuku is too old to support the permission model used here. Such versions are not supported:
     * they predate Shizuku 11 and are no longer distributed.
     */
    @AnyThread
    @NoOps
    public static boolean isUnsupportedVersion() {
        try {
            return Shizuku.isPreV11();
        } catch (Throwable th) {
            return true;
        }
    }

    @AnyThread
    @NoOps
    public static boolean hasPermission() {
        if (!isServiceRunning() || isUnsupportedVersion()) {
            return false;
        }
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable th) {
            Log.w(TAG, "Could not check the Shizuku permission.", th);
            return false;
        }
    }

    /**
     * Ask Shizuku for permission and wait for the user to answer. Shizuku displays the confirmation itself, so
     * no activity is required here, but the answer can only arrive while the user is looking at the screen.
     *
     * @return {@code true} iff the permission is granted by the time this method returns.
     */
    @WorkerThread
    @NoOps
    public static boolean requestPermission(long timeout, @NonNull TimeUnit unit) {
        if (hasPermission()) {
            return true;
        }
        if (!isServiceRunning() || isUnsupportedVersion()) {
            return false;
        }
        CountDownLatch resultReceived = new CountDownLatch(1);
        AtomicInteger grantResult = new AtomicInteger(PackageManager.PERMISSION_DENIED);
        Shizuku.OnRequestPermissionResultListener listener = (requestCode, result) -> {
            if (requestCode == PERMISSION_REQUEST_CODE) {
                grantResult.set(result);
                resultReceived.countDown();
            }
        };
        try {
            Shizuku.addRequestPermissionResultListener(listener);
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
            if (!resultReceived.await(timeout, unit)) {
                Log.w(TAG, "Timed out while waiting for the Shizuku permission.");
                // The user may have granted it through another route in the meantime
                return hasPermission();
            }
            return grantResult.get() == PackageManager.PERMISSION_GRANTED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Throwable th) {
            Log.e(TAG, "Could not request the Shizuku permission.", th);
            return false;
        } finally {
            Shizuku.removeRequestPermissionResultListener(listener);
        }
    }

    /**
     * Open a shell running as the Shizuku user. The caller owns the returned object and must close it.
     * <p>
     * Only call this once {@link #isSupported()} and {@link #hasPermission()} hold.
     */
    @WorkerThread
    @NoOps
    @NonNull
    public static ShizukuShell openShell() throws IOException {
        IBinder binder = Shizuku.getBinder();
        if (binder == null || !binder.pingBinder()) {
            throw new IOException("Shizuku is not running.");
        }
        IShizukuService service = IShizukuService.Stub.asInterface(binder);
        if (service == null) {
            throw new IOException("Could not obtain the Shizuku service.");
        }
        try {
            IRemoteProcess process = service.newProcess(new String[]{"sh"}, null, null);
            if (process == null) {
                throw new IOException("Shizuku did not return a process.");
            }
            return new ShizukuShell(process);
        } catch (RemoteException e) {
            throw new IOException("Could not start a shell through Shizuku.", e);
        }
    }

    /**
     * A shell running in the Shizuku process. Commands are written to {@link #getOutputStream()} and their
     * output is read back from {@link #getInputStream()}, mirroring an interactive ADB shell.
     */
    public static final class ShizukuShell implements AutoCloseable {
        @NonNull
        private final IRemoteProcess mProcess;
        @Nullable
        private InputStream mInputStream;
        @Nullable
        private OutputStream mOutputStream;

        ShizukuShell(@NonNull IRemoteProcess process) {
            mProcess = process;
        }

        @NonNull
        public InputStream getInputStream() throws IOException {
            if (mInputStream == null) {
                mInputStream = new ParcelFileDescriptor.AutoCloseInputStream(getDescriptor(true));
            }
            return mInputStream;
        }

        @NonNull
        public OutputStream getOutputStream() throws IOException {
            if (mOutputStream == null) {
                mOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(getDescriptor(false));
            }
            return mOutputStream;
        }

        @NonNull
        private ParcelFileDescriptor getDescriptor(boolean input) throws IOException {
            try {
                ParcelFileDescriptor descriptor = input ? mProcess.getInputStream() : mProcess.getOutputStream();
                if (descriptor == null) {
                    throw new IOException("Shizuku did not return a file descriptor.");
                }
                return descriptor;
            } catch (RemoteException e) {
                throw new IOException("Could not access the Shizuku process streams.", e);
            }
        }

        @Override
        public void close() {
            IoUtils.closeQuietly(mInputStream);
            IoUtils.closeQuietly(mOutputStream);
            try {
                mProcess.destroy();
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "Could not destroy the Shizuku process.", e);
            }
        }
    }
}
