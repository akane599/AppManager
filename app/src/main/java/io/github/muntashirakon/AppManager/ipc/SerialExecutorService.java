// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static com.topjohnwu.superuser.Shell.EXECUTOR;

// Copyright 2020 John "topjohnwu" Wu
public class SerialExecutorService extends AbstractExecutorService implements Callable<Void> {
    private final Executor mExecutor;
    private final ArrayDeque<Runnable> mTasks = new ArrayDeque<>();
    private boolean mIsShutdown;
    private boolean mScheduled;
    private Thread mWorker;

    public SerialExecutorService() {
        this(EXECUTOR);
    }

    SerialExecutorService(Executor executor) {
        mExecutor = Objects.requireNonNull(executor);
    }

    @Override
    public Void call() {
        synchronized (this) {
            mWorker = Thread.currentThread();
        }
        try {
            for (;;) {
                Runnable task;
                synchronized (this) {
                    task = mTasks.poll();
                    if (task == null) return null;
                }
                task.run();
            }
        } finally {
            synchronized (this) {
                mWorker = null;
                mScheduled = false;
                // A throwing task must not strand the rest of the queue.
                try {
                    if (!mTasks.isEmpty()) scheduleWorker();
                } finally {
                    notifyAll();
                }
            }
        }
    }

    private void scheduleWorker() {
        mScheduled = true;
        try {
            // Retain the original FutureTask containment: an asynchronous task failure must
            // not reach Android's process-wide uncaught-exception handler.
            mExecutor.execute(new FutureTask<>(this));
        } catch (RuntimeException e) {
            mScheduled = false;
            throw e;
        }
    }

    @Override
    public synchronized void execute(Runnable task) {
        Objects.requireNonNull(task);
        if (mIsShutdown) throw new RejectedExecutionException("Executor is shut down");
        mTasks.offer(task);
        if (!mScheduled) {
            try {
                scheduleWorker();
            } catch (RuntimeException e) {
                mTasks.removeLastOccurrence(task);
                throw e;
            }
        }
    }

    @Override
    public synchronized void shutdown() {
        mIsShutdown = true;
        notifyAll();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        mIsShutdown = true;
        List<Runnable> pending = new ArrayList<>(mTasks);
        mTasks.clear();
        if (mWorker != null) mWorker.interrupt();
        notifyAll();
        return pending;
    }

    @Override
    public synchronized boolean isShutdown() {
        return mIsShutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return mIsShutdown && !mScheduled && mTasks.isEmpty();
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long remaining = unit.toNanos(timeout);
        long lastCheck = System.nanoTime();
        while (!isTerminated()) {
            if (remaining <= 0) return false;
            // Waiting on the monitor releases it so the worker can drain and terminate.
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
            long now = System.nanoTime();
            remaining -= now - lastCheck;
            lastCheck = now;
        }
        return true;
    }
}
