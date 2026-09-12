// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

// Copyright 2017 Zheng Li
public class FLog {

    public static volatile boolean writeLog = false;
    private static FileOutputStream fos;
    private static final AtomicInteger sBufferSize = new AtomicInteger();
    private static final AtomicInteger sErrorCount = new AtomicInteger();

    private static void openFile() {
        FileDescriptor descriptor = null;
        try {
            if (writeLog && fos == null && sErrorCount.get() < 5) {
                File file = new File("/data/local/tmp/am.txt");
                // A shell-writable pathname must never redirect a root logger through a link.
                int flags = OsConstants.O_WRONLY | OsConstants.O_CREAT
                        | OsConstants.O_NOFOLLOW | OsConstants.O_NONBLOCK;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) flags |= OsConstants.O_CLOEXEC;
                descriptor = Os.open(file.getAbsolutePath(), flags, 0600);
                // O_CLOEXEC is not exposed by the public API before Android 8.1.
                Os.fcntlInt(descriptor, OsConstants.F_SETFD, OsConstants.FD_CLOEXEC);
                StructStat stat = Os.fstat(descriptor);
                if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_nlink != 1) {
                    throw new IOException("Log destination is not a single regular file.");
                }
                Os.fchmod(descriptor, 0600);
                try {
                    Os.fchown(descriptor, 2000, 2000);
                } catch (ErrnoException e) {
                    e.printStackTrace();
                }
                Os.ftruncate(descriptor, 0);
                fos = new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor));
                Os.fcntlInt(fos.getFD(), OsConstants.F_SETFD, OsConstants.FD_CLOEXEC);

                fos.write("\n\n\n--------------------".getBytes());
                fos.write(new Date().toString().getBytes());
                fos.write("\n\n".getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
            sErrorCount.incrementAndGet();
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
            fos = null;
        } finally {
            if (descriptor != null) {
                try {
                    Os.close(descriptor);
                } catch (ErrnoException ignored) {
                }
            }
        }
    }

    public static synchronized void log(String log) {
        if (writeLog) {
            System.out.println(log);
        } else {
            Log.e("am", "Flog --> " + log);
        }

        try {
            if (writeLog) {
                openFile();
                if (fos != null) {
                    fos.write(log.getBytes());
                    fos.write("\n".getBytes());

                    if (sBufferSize.incrementAndGet() > 10) {
                        fos.getFD().sync();
                        fos.flush();
                        sBufferSize.set(0);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void log(Throwable e) {
        log(Log.getStackTraceString(e));
    }

    public static synchronized void close() {
        try {
            if (fos != null) {
                fos.getFD().sync();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                fos = null;
            }
            sBufferSize.set(0);
        }
    }

}
