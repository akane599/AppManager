// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.*;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class TarExtractionSecurityTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void siblingPrefixSymlinkCannotCreateFilesOutsideDestination() throws Exception {
        File dest = temp.newFolder("dest");
        File outside = temp.newFolder("dest-outside");
        TarArchiveEntry link = new TarArchiveEntry("link", TarConstants.LF_SYMLINK);
        link.setLinkName(outside.getAbsolutePath());
        File tar = archive(link, new TarArchiveEntry("link/payload"));
        assertThrows(IOException.class, () -> extract(tar, dest, null));
        assertFalse(new File(outside, "payload").exists());
    }

    @Test
    public void preexistingSymlinkCannotDeleteOutsideFiles() throws Exception {
        File dest = temp.newFolder("dest");
        File outside = temp.newFolder("dest-outside");
        File victim = new File(outside, "payload");
        Files.write(victim.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(new File(dest, "link").toPath(), outside.toPath());
        File tar = archive(new TarArchiveEntry("link/payload"));
        assertThrows(IOException.class, () -> extract(tar, dest, null));
        assertEquals("keep", new String(Files.readAllBytes(victim.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void externalLinkItselfIsPreservedForBackupCompatibility() throws Exception {
        File dest = temp.newFolder("dest");
        File outside = temp.newFolder("outside");
        TarArchiveEntry link = new TarArchiveEntry("lib", TarConstants.LF_SYMLINK);
        link.setLinkName(outside.getAbsolutePath());
        extract(archive(link), dest, null);
        assertEquals(outside.toPath(), Files.readSymbolicLink(new File(dest, "lib").toPath()));
    }

    @Test
    public void excludedExistingFileIsNotDeletedOrTruncated() throws Exception {
        File dest = temp.newFolder("dest");
        File existing = new File(dest, "keep.txt");
        Files.write(existing.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
        extract(archive(new TarArchiveEntry("keep.txt")), dest, new String[]{"keep\\.txt"});
        assertEquals("keep", new String(Files.readAllBytes(existing.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void parentTraversalIsRejectedBeforeCreatingAnything() throws Exception {
        File dest = temp.newFolder("dest");
        File tar = archive(new TarArchiveEntry("../outside"));
        assertThrows(IOException.class, () -> extract(tar, dest, null));
        assertFalse(new File(temp.getRoot(), "outside").exists());
    }

    private File archive(TarArchiveEntry... entries) throws IOException {
        File file = temp.newFile();
        try (OutputStream compressed = TarUtils.createCompressedStream(new FileOutputStream(file), TarUtils.TAR_GZIP);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(compressed)) {
            for (TarArchiveEntry entry : entries) {
                tar.putArchiveEntry(entry);
                tar.closeArchiveEntry();
            }
            tar.finish();
        }
        return file;
    }

    private static void extract(File tar, File dest, String[] exclusions) throws IOException {
        TarUtils.extract(TarUtils.TAR_GZIP, new Path[]{Paths.get(tar)}, Paths.get(dest), null, exclusions, null);
    }
}
