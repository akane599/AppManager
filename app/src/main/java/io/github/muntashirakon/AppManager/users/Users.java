// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.annotation.UserIdInt;
import android.content.Context;
import android.os.Build;
import android.os.IUserManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserHandleHidden;
import android.os.UserManager;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;

public final class Users {
    public static final String TAG = "Users";

    private static final List<UserInfo> sUserInfoList = new ArrayList<>();
    private static boolean sUnprivilegedMode = false;

    private static int sCachedUid = -1;

    @NonNull
    public static synchronized List<UserInfo> getAllUsers() {
        int uid = getSelfOrRemoteUid();
        if (!sUserInfoList.isEmpty() && !sUnprivilegedMode && sCachedUid == uid) {
            return new ArrayList<>(sUserInfoList);
        }
        List<UserInfo> users = new ArrayList<>();
        try {
            IUserManager manager = IUserManager.Stub.asInterface(ProxyBinder.getService(Context.USER_SERVICE));
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)
                    || SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CREATE_USERS)) {
                List<android.content.pm.UserInfo> result;
                try {
                    result = manager.getUsers(true);
                } catch (RemoteException | NoSuchMethodError e) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw e;
                    result = manager.getUsers(true, true, true);
                }
                if (result != null) {
                    for (android.content.pm.UserInfo info : result) {
                        if (uid == Ops.SHELL_UID && manager.hasUserRestriction(
                                UserManager.DISALLOW_DEBUGGING_FEATURES, info.id)) continue;
                        users.add(new UserInfo(info));
                    }
                }
            }
        } catch (RemoteException | RuntimeException | NoSuchMethodError e) {
            Log.w(TAG, "Privileged profile discovery unavailable", e);
        }
        sUnprivilegedMode = users.isEmpty();
        if (users.isEmpty()) {
            // Use the app identity and its actual user, not shell's user 0.
            try {
                IUserManager manager = IUserManager.Stub.asInterface(
                        ProxyBinder.getUnprivilegedService(Context.USER_SERVICE));
                List<android.content.pm.UserInfo> profiles = manager.getProfiles(UserHandleHidden.myUserId(), false);
                if (profiles != null) {
                    for (android.content.pm.UserInfo info : profiles) users.add(new UserInfo(info));
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Profile query unavailable; using current user", e);
            }
            if (users.isEmpty()) users.add(new UserInfo(Process.myUserHandle(), UserHandleHidden.myUserId()));
        }
        sCachedUid = uid;
        sUserInfoList.clear();
        sUserInfoList.addAll(users);
        return new ArrayList<>(users);
    }

    @NonNull
    @UserIdInt
    public static int[] getAllUserIds() {
        List<Integer> users = new ArrayList<>();
        for (UserInfo userInfo : getAllUsers()) {
            users.add(userInfo.id);
        }
        return ArrayUtils.convertToIntArray(users);
    }

    @NonNull
    public static List<UserInfo> getUsers() {
        int[] selectedUserIds = Prefs.Misc.getSelectedUsers();
        List<UserInfo> users = new ArrayList<>();
        for (UserInfo userInfo : getAllUsers()) {
            if (selectedUserIds == null || ArrayUtils.contains(selectedUserIds, userInfo.id)) {
                users.add(userInfo);
            }
        }
        return users;
    }

    @NonNull
    @UserIdInt
    public static int[] getUsersIds() {
        int[] selectedUserIds = Prefs.Misc.getSelectedUsers();
        List<Integer> users = new ArrayList<>();
        for (UserInfo userInfo : getAllUsers()) {
            if (selectedUserIds == null || ArrayUtils.contains(selectedUserIds, userInfo.id)) {
                users.add(userInfo.id);
            }
        }
        return ArrayUtils.convertToIntArray(users);
    }

    @Nullable
    public static UserHandle getUserHandle(@UserIdInt int userId) {
        for (UserInfo userInfo : getAllUsers()) {
            if (userInfo.id == userId) {
                return userInfo.userHandle;
            }
        }
        return null;
    }

    @IntRange(from = 0)
    public static int getSelfOrRemoteUid() {
        try {
            return LocalServices.getAmService().getUid();
        } catch (RemoteException e) {
            return Process.myUid();
        }
    }
}
