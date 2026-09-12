// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.db.dao.PermissionOverrideDao;
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride;

public final class PermissionOverrideReconciler {
    public static final int SYNCED = 0;
    public static final int PENDING = 1;
    public static final int FAILED = 2;

    interface Platform {
        int resolveUid(@NonNull String packageName, int userId) throws Exception;

        boolean isEnforced(int uid, @NonNull PermissionOverride override) throws Exception;

        void apply(int uid, @NonNull PermissionOverride override) throws Exception;
    }

    private final PermissionOverrideDao mDao;
    private final Platform mPlatform;
    private final Executor mExecutor;
    private final Object mStateLock = new Object();
    private final Set<UserPackagePair> mQueuedKeys = new java.util.HashSet<>();

    PermissionOverrideReconciler(@NonNull PermissionOverrideDao dao,
                                 @NonNull Platform platform) {
        this(dao, platform, Executors.newSingleThreadExecutor());
    }

    PermissionOverrideReconciler(@NonNull PermissionOverrideDao dao, @NonNull Platform platform,
                                 @NonNull Executor executor) {
        mDao = dao;
        mPlatform = platform;
        mExecutor = executor;
    }

    public void reconcile(@NonNull String packageName, int userId) {
        UserPackagePair key = new UserPackagePair(packageName, userId);
        synchronized (mQueuedKeys) {
            if (!mQueuedKeys.add(key)) return;
        }
        try {
            mExecutor.execute(() -> {
                // Only coalesce waiting work. A change during this run needs a subsequent run.
                synchronized (mQueuedKeys) {
                    mQueuedKeys.remove(key);
                }
                reconcileNow(packageName, userId);
            });
        } catch (RuntimeException e) {
            synchronized (mQueuedKeys) {
                mQueuedKeys.remove(key);
            }
            throw e;
        }
    }

    public void reconcileAll() {
        mExecutor.execute(() -> {
            Set<UserPackagePair> targets = new LinkedHashSet<>();
            for (PermissionOverride override : mDao.getAll()) {
                targets.add(new UserPackagePair(override.packageName, override.userId));
            }
            for (UserPackagePair target : targets) {
                reconcile(target.getPackageName(), target.getUserId());
            }
        });
    }

    public void remove(@NonNull String packageName, int userId) {
        mExecutor.execute(() -> removeNow(packageName, userId));
    }

    void removeNow(@NonNull String packageName, int userId) {
        synchronized (mStateLock) {
            mDao.deleteForPackage(packageName, userId);
        }
    }

    void reconcileNow(@NonNull String packageName, int userId) {
        synchronized (mStateLock) {
            List<PermissionOverride> overrides = mDao.getForPackage(packageName, userId);
            if (overrides.isEmpty()) return;
            int uid;
            try {
                // Resolve UID once per package/user so all overrides use the same current identity.
                uid = mPlatform.resolveUid(packageName, userId);
            } catch (Exception e) {
                for (PermissionOverride override : overrides) {
                    override.syncStatus = FAILED;
                    updateSyncStatus(override);
                }
                return;
            }
            for (PermissionOverride override : overrides) {
                override.syncStatus = PENDING;
                updateSyncStatus(override);
                try {
                    if (!mPlatform.isEnforced(uid, override)) mPlatform.apply(uid, override);
                    override.syncStatus = SYNCED;
                    override.syncTime = System.currentTimeMillis();
                } catch (Exception e) {
                    override.syncStatus = FAILED;
                }
                updateSyncStatus(override);
            }
        }
    }

    private void updateSyncStatus(@NonNull PermissionOverride override) {
        // Updating a stale snapshot must never replace a newer user choice or resurrect a
        // removed override. The DAO checks the desired state atomically with the status write.
        mDao.updateSyncStatus(override.packageName, override.userId, override.permissionName,
                override.desiredGranted, override.controller, override.syncStatus, override.syncTime);
    }
}
