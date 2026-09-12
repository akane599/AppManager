// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.server;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ServerTest {
    @Test
    public void rejectedClientDoesNotStopServerAndListenerIsLocal() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        ConfigParams config = new ConfigParams();
        config.put(ConfigParams.PARAM_APP, "test.app");
        config.put(ConfigParams.PARAM_TOKEN, "test-token");
        config.put(ConfigParams.PARAM_UID, "2000");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Server server = new Server(0, "test-token", new LifecycleAgent(config), bytes -> received.countDown())) {
            Field field = Server.class.getDeclaredField("mServer");
            field.setAccessible(true);
            ServerSocket listener = (ServerSocket) field.get(server);
            assertTrue(listener.getInetAddress().isLoopbackAddress());
            Future<?> worker = executor.submit(() -> { server.run(); return null; });
            try (Socket rejected = new Socket("127.0.0.1", listener.getLocalPort())) {
                rejected.setSoTimeout(2000);
                DataOutputStream output = new DataOutputStream(rejected.getOutputStream());
                output.writeInt(1);
                output.writeByte('x');
                output.flush();
                assertEquals(-1, rejected.getInputStream().read());
            }
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort())) {
                client.setSoTimeout(2000);
                DataTransmission transfer = new DataTransmission(client.getOutputStream(), client.getInputStream(), false);
                transfer.shakeHands("test-token", DataTransmission.Role.Client);
                transfer.sendMessage(new byte[]{1});
                assertTrue(received.await(2, TimeUnit.SECONDS));
                server.close();
                worker.get(2, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void closeUnblocksAnUnauthenticatedClient() throws Exception {
        ConfigParams config = new ConfigParams();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Server server = new Server(0, "test-token", new LifecycleAgent(config), null)) {
            Field field = Server.class.getDeclaredField("mServer");
            field.setAccessible(true);
            ServerSocket listener = (ServerSocket) field.get(server);
            Future<?> worker = executor.submit(() -> { server.run(); return null; });
            try (Socket client = new Socket("127.0.0.1", listener.getLocalPort())) {
                server.close();
                worker.get(2, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
