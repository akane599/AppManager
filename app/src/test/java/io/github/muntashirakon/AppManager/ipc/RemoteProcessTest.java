// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.*;

import android.os.ParcelFileDescriptor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import io.github.muntashirakon.AppManager.IRemoteProcess;

@RunWith(RobolectricTestRunner.class)
public class RemoteProcessTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void repeatedErrorStreamAccessKeepsOneReader() throws Exception {
        FakeProcess remote = new FakeProcess(temp.newFile());
        RemoteProcess process = new RemoteProcess(remote);
        try (InputStream error = process.getErrorStream()) {
            assertSame(error, process.getErrorStream());
            assertEquals(1, remote.errorRequests);
        }
    }

    private static class FakeProcess extends IRemoteProcess.Stub {
        final File file;
        int errorRequests;
        FakeProcess(File file) { this.file = file; }
        @Override public ParcelFileDescriptor getErrorStream() {
            errorRequests++;
            try { return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY); }
            catch (IOException e) { throw new IllegalStateException(e); }
        }
        @Override public ParcelFileDescriptor getOutputStream() { throw new UnsupportedOperationException(); }
        @Override public ParcelFileDescriptor getInputStream() { throw new UnsupportedOperationException(); }
        @Override public void closeOutputStream() {}
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() {}
        @Override public boolean alive() { return false; }
        @Override public boolean waitForTimeout(long timeout, String unit) { return true; }
    }
}
