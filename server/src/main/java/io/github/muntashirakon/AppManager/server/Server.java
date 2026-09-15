// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.FLog;

// Copyright 2017 Zheng Li
class Server implements Closeable {
    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;
    @NonNull
    private final LifecycleAgent mLifecycleAgent;
    @NonNull
    private final ServerSocket mServer;
    @NonNull
    private final String mToken;
    @Nullable
    private final DataTransmission.OnReceiveCallback mOnReceiveCallback;

    private volatile Socket mClient;
    private volatile DataTransmission mDataTransmission;
    private volatile boolean mRunning = true;
    boolean mRunInBackground = false;

    /**
     * Constructor for starting a local server
     *
     * @param port              Port number
     * @param token             Token for handshaking
     * @param onReceiveCallback Callback for sending message (received by the calling class)
     * @throws IOException On failing to create a socket connection
     */
    Server(int port, @NonNull String token, @NonNull LifecycleAgent lifecycleAgent,
           @Nullable DataTransmission.OnReceiveCallback onReceiveCallback)
            throws IOException {
        mToken = token;
        mLifecycleAgent = lifecycleAgent;
        mServer = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
        mOnReceiveCallback = onReceiveCallback;
    }

    /**
     * Run the server
     *
     * @throws IOException When server has failed to shake hands or the connection cannot be made
     */
    void run() throws IOException, RuntimeException {
        while (mRunning) {
            boolean authenticated = false;
            try {
                FLog.log("Waiting for a new client...");
                // Allow only one client
                mClient = mServer.accept();
                if (!mRunning) break;
                mClient.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                FLog.log("Connected to 127.0.0.1:" + mClient.getPort());
                // Prepare input and output streams for data interchange
                mDataTransmission = new DataTransmission(mClient.getOutputStream(), mClient.getInputStream(),
                        mOnReceiveCallback);
                // Handshake: check if tokens matched
                mDataTransmission.shakeHands(mToken, DataTransmission.Role.Server);
                authenticated = true;
                mClient.setSoTimeout(0);
                // Send broadcast message to the system that the server has connected
                mLifecycleAgent.onConnected();
                // Handle the data received initially from the client
                mDataTransmission.handleReceive();
            } catch (IOException e) {
                if (!mRunning) break;
                FLog.log(e);
                // Rejected or abandoned handshakes must not stop the privileged service or
                // publish a disconnect for a session that was never authenticated.
                if (!authenticated) continue;
                FLog.log("Run in background: " + mRunInBackground);
                // Send broadcast message to the system that the server has disconnected
                mLifecycleAgent.onDisconnected();
                // Throw exception only when run in background is not requested
                if (!mRunInBackground) {
                    mRunning = false;
                    throw e;
                }
            } catch (RuntimeException e) {
                FLog.log(e);
                // Send broadcast message to the system that the server has disconnected
                if (authenticated) mLifecycleAgent.onDisconnected();
                // Re-throw the exception
                mRunInBackground = false;
                mRunning = false;
                throw e;
            } finally {
                mDataTransmission = null;
                if (mClient != null) {
                    try {
                        mClient.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                mClient = null;
            }
        }
    }

    public void sendResult(byte[] bytes) throws IOException {
        DataTransmission transmission = mDataTransmission;
        if (mRunning && transmission != null) {
            LifecycleAgent.sServerInfo.txBytes += bytes.length;
            transmission.sendMessage(bytes);
        }
    }

    @Override
    public void close() throws IOException {
        mRunning = false;
        try {
            Socket client = mClient;
            if (client != null) client.close();
        } finally {
            mServer.close();
        }
    }
}
