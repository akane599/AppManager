// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.debloat;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserHandleHidden;
import android.system.Os;
import android.system.OsConstants;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry;
import io.github.muntashirakon.AppManager.ipc.ps.Ps;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

/** Collects a fresh, read-only snapshot for the current Android user. */
final class DebloatRecommendations {
    @SuppressWarnings("unchecked")
    static List<DebloatRecommendation> load(Context context) {
        Map<String, DebloatRecommendation> candidates = new HashMap<>();
        Map<String, Integer> uids = new HashMap<>();
        for (DebloatObject app : StaticDataset.getDebloatObjects()) {
            if (Thread.currentThread().isInterrupted()) return Collections.emptyList();
            if (app.packageName.equals(context.getPackageName())
                    || app.getRemoval() != DebloatObject.REMOVAL_SAFE
                    || !TextUtils.isEmpty(app.getWarning()) || app.getRequiredBy().length > 0) continue;
            try {
                ApplicationInfo info = PackageManagerCompat.getApplicationInfo(app.packageName, 0,
                        UserHandleHidden.myUserId());
                if (!DebloatRecommendation.isCandidate(app.getRemoval() == DebloatObject.REMOVAL_SAFE,
                        (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0, FreezeUtils.isFrozen(info),
                        !TextUtils.isEmpty(app.getWarning()), app.getRequiredBy().length > 0)) continue;
                candidates.put(app.packageName, new DebloatRecommendation(app.packageName,
                        info.loadLabel(context.getPackageManager()).toString()));
                uids.put(app.packageName, info.uid);
            } catch (Exception ignored) {
                // Missing or inaccessible current-user packages must not become recommendations.
            }
        }
        Map<Integer, ActivityManager.RunningAppProcessInfo> running = new HashMap<>();
        try {
            for (ActivityManager.RunningAppProcessInfo info : ActivityManagerCompat.getRunningAppProcesses()) {
                running.put(info.pid, info);
                if (info.pkgList == null) continue;
                for (String packageName : info.pkgList) {
                    DebloatRecommendation item = candidates.get(packageName);
                    if (item != null && uids.get(packageName) == info.uid) {
                        item.running = true;
                        item.background |= info.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("DebloatRecommendations", e);
        }
        try {
            List<ProcessEntry> processes;
            if (LocalServices.alive()) {
                processes = (List<ProcessEntry>) LocalServices.getAmService().getRunningProcesses().getList();
            } else {
                Ps ps = new Ps();
                ps.loadProcesses();
                processes = ps.getProcesses();
            }
            long pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
            for (ProcessEntry process : processes) {
                if (process.name == null || process.users == null) continue;
                String packageName = DebloatRecommendation.packageNameOf(process.name);
                DebloatRecommendation item = candidates.get(packageName);
                // Match both name and UID: never attribute another user's or a shared-UID-only process.
                if (item == null || uids.get(packageName) != process.users.fsUid) continue;
                ActivityManager.RunningAppProcessInfo info = running.get(process.pid);
                boolean background = info != null && info.uid == process.users.fsUid
                        && info.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE;
                item.addProcess(background, process.residentSetSize, pageSize,
                        process.cpuTimeConsumed, process.elapsedTime);
            }
        } catch (Exception e) {
            // UAD candidates remain useful when process access is restricted or the service disconnects.
            Log.e("DebloatRecommendations", e);
        }
        List<DebloatRecommendation> result = new ArrayList<>(candidates.values());
        Collections.sort(result);
        return result;
    }
}
