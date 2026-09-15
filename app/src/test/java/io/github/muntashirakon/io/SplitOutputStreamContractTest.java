// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

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

    @Test
    public void closeAttemptsEveryPartAndPreservesFailures() throws Exception {
        IOException first = new IOException("first");
        IOException second = new IOException("second");
        CloseTrackingStream one = new CloseTrackingStream(first);
        CloseTrackingStream two = new CloseTrackingStream(second);
        CloseTrackingStream three = new CloseTrackingStream(null);
        SplitOutputStream stream = withOpenedParts(one, two, three);

        assertSame(first, assertThrows(IOException.class, stream::close));
        assertEquals(1, one.closeCount);
        assertEquals(1, two.closeCount);
        assertEquals(1, three.closeCount);
        assertArrayEquals(new Throwable[]{second}, first.getSuppressed());
    }

    @Test
    public void closeContinuesWhenPartsThrowTheSameException() throws Exception {
        IOException shared = new IOException("shared transport failure");
        CloseTrackingStream one = new CloseTrackingStream(shared);
        CloseTrackingStream two = new CloseTrackingStream(shared);
        CloseTrackingStream three = new CloseTrackingStream(null);
        SplitOutputStream stream = withOpenedParts(one, two, three);

        assertSame(shared, assertThrows(IOException.class, stream::close));
        assertEquals(1, two.closeCount);
        assertEquals(1, three.closeCount);
        assertEquals(0, shared.getSuppressed().length);
    }

    @SuppressWarnings("unchecked")
    private static SplitOutputStream withOpenedParts(OutputStream... parts) throws Exception {
        // Inject open handles so cleanup failures do not depend on an Android file provider.
        SplitOutputStream stream = new SplitOutputStream(null, "unused");
        Field streams = SplitOutputStream.class.getDeclaredField("mOutputStreams");
        streams.setAccessible(true);
        Collections.addAll((List<OutputStream>) streams.get(stream), parts);
        return stream;
    }

    private static class CloseTrackingStream extends OutputStream {
        private final IOException failure;
        private int closeCount;

        CloseTrackingStream(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void write(int b) {
        }

        @Override
        public void close() throws IOException {
            ++closeCount;
            if (failure != null) throw failure;
        }
    }
}
