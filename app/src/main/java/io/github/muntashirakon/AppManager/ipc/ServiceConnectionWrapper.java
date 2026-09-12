// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

class ServiceConnectionWrapper {
    private static final String TAG = "ServiceConnectionWrapper";
    private final ComponentName mComponentName;
    @Nullable
    private final Runnable mDeathCallback;
    @Nullable
    private volatile Binding mBinding;

    // Each attempt owns its callback and latch. Late responses cannot satisfy a newer attempt.
    private final class Binding implements ServiceConnection {
        final CountDownLatch ready = new CountDownLatch(1);
        volatile IBinder binder;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mBinding != this) return;
            binder = service;
            try {
                service.linkToDeath(this::lost, 0);
            } catch (RemoteException e) {
                lost();
            }
            ready.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            lost();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            lost();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            lost();
        }

        void lost() {
            binder = null;
            ready.countDown();
            if (mBinding == this && mDeathCallback != null) mDeathCallback.run();
        }
    }

    ServiceConnectionWrapper(@NonNull String pkgName, @NonNull String className) {
        this(new ComponentName(pkgName, className), null);
    }

    ServiceConnectionWrapper(@NonNull String pkgName, @NonNull String className, @Nullable Runnable callback) {
        this(new ComponentName(pkgName, className), callback);
    }

    ServiceConnectionWrapper(@NonNull ComponentName name) {
        this(name, null);
    }

    ServiceConnectionWrapper(@NonNull ComponentName name, @Nullable Runnable callback) {
        mComponentName = name;
        mDeathCallback = callback;
    }

    @NonNull
    public IBinder getService() throws RemoteException {
        Binding binding = mBinding;
        IBinder binder = binding != null ? binding.binder : null;
        if (binder == null || !binder.pingBinder()) throw new RemoteException("Binder not running.");
        return binder;
    }

    @WorkerThread
    @NonNull
    public IBinder bindService() throws RemoteException {
        if (isBinderActive()) return getService();
        Binding binding = new Binding();
        mBinding = binding;
        Intent intent = new Intent().setComponent(mComponentName);
        ThreadUtils.postOnMainThread(() -> {
            if (mBinding != binding) return;
            try {
                RootService.bind(intent, binding);
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not bind " + mComponentName, e);
                binding.lost();
            }
        });
        try {
            if (!binding.ready.await(45, TimeUnit.SECONDS)) {
                throw new RemoteException("Timed out binding " + mComponentName);
            }
            return getService();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Interrupted binding " + mComponentName);
        } finally {
            if (!isBinderActive()) {
                if (mBinding == binding) mBinding = null;
                ThreadUtils.postOnMainThread(() -> RootService.unbind(binding));
            }
        }
    }

    @MainThread
    public void unbindService() {
        Binding binding = mBinding;
        mBinding = null;
        if (binding != null) {
            binding.ready.countDown();
            RootService.unbind(binding);
        }
    }

    @WorkerThread
    public void stopDaemon() {
        Binding binding = mBinding;
        mBinding = null;
        if (binding == null) return;
        binding.ready.countDown();
        Intent intent = new Intent().setComponent(mComponentName);
        ThreadUtils.postOnMainThread(() -> {
            RootService.unbind(binding);
            RootService.stop(intent);
        });
    }

    boolean isBinderActive() {
        Binding binding = mBinding;
        IBinder binder = binding != null ? binding.binder : null;
        return binder != null && binder.pingBinder();
    }
}
