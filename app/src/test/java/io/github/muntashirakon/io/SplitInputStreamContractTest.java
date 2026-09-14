// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

public class SplitInputStreamContractTest {
    @Test
    public void singleByteReadsAreUnsignedAndDistinguishEof() throws Exception {
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; ++i) bytes[i] = (byte) i;
        try (SplitInputStream stream = openedStream(bytes)) {
            for (int i = 0; i < bytes.length; ++i) {
                assertEquals("Byte " + i, i, stream.read());
            }
            assertEquals(-1, stream.read());
        }
    }

    @SuppressWarnings("unchecked")
    private static SplitInputStream openedStream(byte[] bytes) throws Exception {
        // Inject an already-open part to exercise buffering without an Android file provider.
        SplitInputStream stream = new SplitInputStream(Collections.singletonList(null));
        Field streams = SplitInputStream.class.getDeclaredField("mInputStreams");
        streams.setAccessible(true);
        ((List<InputStream>) streams.get(stream)).add(new ByteArrayInputStream(bytes));
        Field index = SplitInputStream.class.getDeclaredField("mCurrentIndex");
        index.setAccessible(true);
        index.setInt(stream, 0);
        return stream;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void closeContinuesWhenPartsThrowTheSameException() throws Exception {
        IOException shared = new IOException("shared transport failure");
        int[] closes = new int[3];
        SplitInputStream stream = new SplitInputStream(Collections.emptyList());
        Field field = SplitInputStream.class.getDeclaredField("mInputStreams");
        field.setAccessible(true);
        List<InputStream> parts = (List<InputStream>) field.get(stream);
        for (int i = 0; i < closes.length; ++i) {
            final int part = i;
            parts.add(new ByteArrayInputStream(new byte[0]) {
                @Override
                public void close() throws IOException {
                    ++closes[part];
                    if (part < 2) throw shared;
                }
            });
        }

        assertSame(shared, assertThrows(IOException.class, stream::close));
        assertArrayEquals(new int[]{1, 1, 1}, closes);
        assertEquals(0, shared.getSuppressed().length);
    }

    @Test
    public void largeSkipRequestsConsumeSmallStreamsWithoutOverflow() throws Exception {
        long[] requests = {1L << 31, 1L << 32, Long.MAX_VALUE};
        for (long request : requests) {
            try (SplitInputStream stream = openedStream(new byte[]{1, 2, 3})) {
                assertEquals(3, stream.skip(request));
                assertEquals(-1, stream.read());
            }
        }
    }

    @Test
    public void markedStreamSupportsRepeatedEofAndReset() throws Exception {
        try (SplitInputStream stream = openedStream(new byte[]{1, 2, 3})) {
            stream.mark(8192);
            byte[] bytes = new byte[3];
            assertEquals(3, stream.read(bytes));
            assertEquals(-1, stream.read());
            assertEquals(-1, stream.read());
            stream.reset();
            assertEquals(3, stream.available());
            assertEquals(3, stream.read(bytes));
            assertArrayEquals(new byte[]{1, 2, 3}, bytes);
            assertEquals(-1, stream.read());
        }
    }
}
