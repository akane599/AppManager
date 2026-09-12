// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ServerConfigTokenTest {
    private SharedPreferences prefs() {
        return ContextUtils.getContext().getSharedPreferences("server_config", Context.MODE_PRIVATE);
    }

    @After
    public void cleanUp() {
        prefs().edit().remove("l_token").commit();
    }

    @Test
    public void concurrentStartupUsesOneStrongToken() throws Exception {
        prefs().edit().remove("l_token").commit();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> tokens = new ArrayList<>();
        try {
            for (int i = 0; i < 8; ++i) tokens.add(executor.submit(() -> {
                start.await();
                return ServerConfig.getLocalToken();
            }));
            start.countDown();
            String expected = tokens.get(0).get(2, TimeUnit.SECONDS);
            assertTrue(expected.matches("[0-9a-f]{64}"));
            for (Future<String> token : tokens) assertEquals(expected, token.get(2, TimeUnit.SECONDS));
            assertEquals(expected, prefs().getString("l_token", null));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void existingTokenIsPreservedForRunningServer() {
        prefs().edit().putString("l_token", "existing-compatible-token").commit();
        assertEquals("existing-compatible-token", ServerConfig.getLocalToken());
    }
}
