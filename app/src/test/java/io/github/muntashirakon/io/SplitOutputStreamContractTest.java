// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SplitOutputStreamContractTest {
    @Test
    public void initialEmptyArrayWriteDoesNotCreateAPart() throws Exception {
        try (SplitOutputStream stream = new SplitOutputStream(null, "unused")) {
            stream.write(new byte[0]);
            assertTrue(stream.getFiles().isEmpty());
        }
    }

    @Test
    public void initialEmptySliceWriteDoesNotCreateAPart() throws Exception {
        try (SplitOutputStream stream = new SplitOutputStream(null, "unused")) {
            stream.write(new byte[3], 3, 0);
            assertTrue(stream.getFiles().isEmpty());
        }
    }

    @Test
    public void invalidSlicesAreRejectedBeforeCreatingAPart() throws Exception {
        try (SplitOutputStream stream = new SplitOutputStream(null, "unused")) {
            byte[] bytes = new byte[3];
            assertThrows(IndexOutOfBoundsException.class, () -> stream.write(bytes, -1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> stream.write(bytes, 4, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> stream.write(bytes, 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> stream.write(bytes, 2, 2));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> stream.write(bytes, 1, Integer.MAX_VALUE));
            assertThrows(NullPointerException.class, () -> stream.write(null, 0, 0));
            assertTrue(stream.getFiles().isEmpty());
        }
    }
}
