// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.io.FileSystemManager;

/** Runs inside Shizuku's user-service process, with its shell or root identity. */
@Keep
public final class ShizukuService extends AMService.IAMServiceImpl {
    @Keep
    public ShizukuService() {
    }

    @Override
    public IBinder getFileSystemService() {
        return FileSystemManager.getService();
    }

    @Override
    public boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags)
            throws RemoteException {
        // Shizuku sends this lifecycle transaction when the user service is removed.
        if (code == 16777115) {
            System.exit(0);
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }
}
