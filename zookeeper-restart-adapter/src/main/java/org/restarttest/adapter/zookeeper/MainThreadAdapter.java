/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.restarttest.adapter.zookeeper;

import org.apache.zookeeper.server.admin.AdminServer;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumPeerTestBase;
import org.apache.zookeeper.test.ClientBase;
import org.restarttest.core.ClusterAdapter;
import org.restarttest.core.RestartMode;
import org.restarttest.health.HealthCheck;
import org.restarttest.state.StateCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * Adapter for ZooKeeper quorum peer tests using QuorumPeerTestBase.MainThread.
 * Supports restarting a single quorum peer managed by MainThread.
 */
public class MainThreadAdapter implements ClusterAdapter<QuorumPeerTestBase.MainThread> {

    private static final Logger LOG = LoggerFactory.getLogger(MainThreadAdapter.class);
    private final MainThreadStateCapture stateCapture;

    public MainThreadAdapter() {
        this.stateCapture = new MainThreadStateCapture();
    }

    @Override
    public Class<QuorumPeerTestBase.MainThread> getClusterType() {
        return QuorumPeerTestBase.MainThread.class;
    }

    @Override
    public void restartNode(QuorumPeerTestBase.MainThread cluster, String nodeRole, int nodeIndex, RestartMode mode)
            throws Exception {
        String normalizedRole = normalizeRole(nodeRole);

        if ("server".equals(normalizedRole) || "all".equals(normalizedRole)) {
            restartMainThread(cluster, mode);
        } else {
            throw new IllegalArgumentException(
                "Unknown node role: " + nodeRole + ". Supported: server, all");
        }
    }

    @Override
    public void restartAllNodes(QuorumPeerTestBase.MainThread cluster, String nodeRole, RestartMode mode)
            throws Exception {
        // For single MainThread, restart all is the same as restart one
        restartMainThread(cluster, mode);
    }

    @Override
    public void waitActive(QuorumPeerTestBase.MainThread cluster) throws Exception {
        int clientPort = cluster.getClientPort();
        String hostPort = "127.0.0.1:" + clientPort;

        // Wait for server to be up
        boolean serverUp = ClientBase.waitForServerUp(hostPort, ClientBase.CONNECTION_TIMEOUT);
        if (!serverUp) {
            throw new Exception("QuorumPeer failed to start at " + hostPort);
        }

        // Wait for QuorumPeer to be running
        int retries = 30;
        while (!cluster.isQuorumPeerRunning() && retries > 0) {
            Thread.sleep(100);
            retries--;
        }

        if (!cluster.isQuorumPeerRunning()) {
            throw new Exception("QuorumPeer not running after restart");
        }

        // Additional wait for admin server and other auxiliary services to be ready
        // The admin server (Jetty HTTP) runs on a separate port and takes extra time to start
        // Note: We can't properly set the admin port since it's only passed via system property
        // which gets overwritten by other servers during startup
        LOG.info("Waiting additional time for admin server and auxiliary services...");
        Thread.sleep(10000);

        LOG.info("QuorumPeer is active at {}", hostPort);
    }

    @Override
    public StateCapture<QuorumPeerTestBase.MainThread> getStateCapture() {
        return stateCapture;
    }

    @Override
    public HealthCheck<QuorumPeerTestBase.MainThread> getHealthCheck() {
        // No custom health checks - rely on waitActive()
        return null;
    }

    @Override
    public int getNodeCount(QuorumPeerTestBase.MainThread cluster, String nodeRole) throws Exception {
        String normalizedRole = normalizeRole(nodeRole);
        if ("server".equals(normalizedRole) || "all".equals(normalizedRole)) {
            return 1; // Only one MainThread server
        } else {
            throw new IllegalArgumentException("Unknown node role: " + nodeRole);
        }
    }

    private String normalizeRole(String role) {
        String lower = role.toLowerCase();
        return lower;
    }

    private void restartMainThread(QuorumPeerTestBase.MainThread mainThread, RestartMode mode) throws Exception {
        int clientPort = mainThread.getClientPort();
        String hostPort = "127.0.0.1:" + clientPort;

        LOG.info("Restarting MainThread at {} with mode {}", hostPort, mode);

        // Get admin port from the running server before shutdown
        String adminPort = getAdminPortFromServer(mainThread);
        LOG.info("Admin server port before restart: {}", adminPort);

        switch (mode) {
            case GRACEFUL:
                // Graceful shutdown
                mainThread.shutdown();
                mainThread.join(5000);

                // Wait for server to be down
                ClientBase.waitForServerDown(hostPort, ClientBase.CONNECTION_TIMEOUT);

                // Set admin port system property before restart
                if (adminPort != null) {
                    LOG.info("Setting admin server port to {} before restart", adminPort);
                    System.setProperty("zookeeper.admin.serverPort", adminPort);
                }

                // Restart
                mainThread.start();
                break;

            case CRASH:
                // Abrupt shutdown - just shutdown QuorumPeer without graceful cleanup
                QuorumPeer qp = mainThread.getQuorumPeer();
                if (qp != null) {
                    qp.shutdown();
                }

                // Force thread interruption if still alive
                if (mainThread.isAlive()) {
                    Thread.sleep(100); // Brief pause
                }

                // Set admin port system property before restart
                if (adminPort != null) {
                    System.setProperty("zookeeper.admin.serverPort", adminPort);
                }

                // Restart immediately
                mainThread.start();
                break;

            case DELAYED_CRASH:
                // Crash and wait before restart
                QuorumPeer qp2 = mainThread.getQuorumPeer();
                if (qp2 != null) {
                    qp2.shutdown();
                }

                Thread.sleep(500); // Default delay

                // Set admin port system property before restart
                if (adminPort != null) {
                    System.setProperty("zookeeper.admin.serverPort", adminPort);
                }

                mainThread.start();
                break;

            default:
                throw new IllegalArgumentException("Unknown restart mode: " + mode);
        }

        LOG.info("MainThread restarted successfully at {}", hostPort);
    }

    /**
     * Get the admin server port from the running QuorumPeer using reflection.
     */
    private String getAdminPortFromServer(QuorumPeerTestBase.MainThread mainThread) {
        try {
            QuorumPeer qp = mainThread.getQuorumPeer();
            if (qp == null) {
                LOG.warn("QuorumPeer is null, cannot get admin port");
                return null;
            }

            // Access adminServer field via reflection
            Field adminServerField = QuorumPeer.class.getDeclaredField("adminServer");
            adminServerField.setAccessible(true);
            Object adminServer = adminServerField.get(qp);

            if (adminServer == null) {
                LOG.warn("AdminServer is null");
                return null;
            }

            // The JettyAdminServer stores the port in its server's connector
            // Try to get port from Jetty Server
            Class<?> jettyAdminClass = adminServer.getClass();
            Field serverField = jettyAdminClass.getDeclaredField("server");
            serverField.setAccessible(true);
            Object jettyServer = serverField.get(adminServer);

            if (jettyServer != null) {
                // Get port from Jetty Server's connectors
                java.lang.reflect.Method getConnectorsMethod = jettyServer.getClass().getMethod("getConnectors");
                Object[] connectors = (Object[]) getConnectorsMethod.invoke(jettyServer);
                if (connectors != null && connectors.length > 0) {
                    // Get port from first connector
                    java.lang.reflect.Method getPortMethod = connectors[0].getClass().getMethod("getLocalPort");
                    int port = (int) getPortMethod.invoke(connectors[0]);
                    LOG.info("Got admin server port {} from Jetty connector", port);
                    return String.valueOf(port);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to get admin port via reflection: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Read the admin server port from the config file.
     */
    private String readAdminPortFromConfig(QuorumPeerTestBase.MainThread mainThread) {
        try {
            File confFile = mainThread.getConfFile();
            if (confFile != null && confFile.exists()) {
                Properties props = new Properties();
                try (FileReader reader = new FileReader(confFile)) {
                    props.load(reader);
                }
                String adminPort = props.getProperty("admin.serverPort");
                if (adminPort != null) {
                    LOG.info("Read admin.serverPort={} from config file {}", adminPort, confFile);
                    return adminPort;
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to read admin port from config file: {}", e.getMessage());
        }
        return null;
    }
}
