// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
}
