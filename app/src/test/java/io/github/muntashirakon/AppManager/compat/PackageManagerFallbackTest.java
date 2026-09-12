// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.*;

import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandleHidden;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.List;

import io.github.muntashirakon.AppManager.settings.Ops;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = PackageManagerFallbackTest.DeniedPackageService.class)
public class PackageManagerFallbackTest {
    @Implements(ProxyBinder.class)
    public static class DeniedPackageService {
        @Implementation
        public static IBinder getService(String name) {
            throw new SecurityException("OEM denied the shell package query");
        }
    }

    @Test
    public void coldQueryFallsBackForCurrentUserAndPreservesBackendIdentity() {
        PackageManager pm = RuntimeEnvironment.getApplication().getPackageManager();
        PackageInfo info = new PackageInfo();
        info.packageName = "example.installed";
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = info.packageName;
        info.applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED;
        Shadows.shadowOf(pm).installPackage(info);
        Ops.setWorkingUid(Ops.SHELL_UID);

        List<PackageInfo> packages = PackageManagerCompat.getInstalledPackages(0, UserHandleHidden.myUserId());

        assertTrue(packages.stream().anyMatch(p -> info.packageName.equals(p.packageName)));
        assertEquals(Ops.SHELL_UID, Ops.getWorkingUid());
    }

    @Test
    public void doesNotSubstituteCurrentUserPackagesForDeniedWorkProfile() {
        assertThrows(SecurityException.class, () ->
                PackageManagerCompat.getInstalledPackages(0, UserHandleHidden.myUserId() + 10));
    }
}
