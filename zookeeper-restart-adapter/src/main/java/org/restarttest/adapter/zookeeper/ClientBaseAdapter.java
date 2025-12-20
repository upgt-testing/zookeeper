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

import java.io.File;
import java.io.IOException;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZKDatabase;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.test.ClientBase;
import org.restarttest.core.ClusterAdapter;
import org.restarttest.core.RestartMode;
import org.restarttest.health.HealthCheck;
import org.restarttest.state.StateCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for ZooKeeper standalone server tests using ClientBase.
 * Supports restarting the single standalone server.
 */
public class ClientBaseAdapter implements ClusterAdapter<ClientBase> {

    private static final Logger LOG = LoggerFactory.getLogger(ClientBaseAdapter.class);
    private final ClientBaseStateCapture stateCapture;

    public ClientBaseAdapter() {
        this.stateCapture = new ClientBaseStateCapture();
    }

    @Override
    public Class<ClientBase> getClusterType() {
        return ClientBase.class;
    }

    @Override
    public void restartNode(ClientBase cluster, String nodeRole, int nodeIndex, RestartMode mode) throws Exception {
        String normalizedRole = normalizeRole(nodeRole);

        if ("server".equals(normalizedRole) || "all".equals(normalizedRole)) {
            restartServer(cluster, mode);
        } else {
            throw new IllegalArgumentException(
                "Unknown node role: " + nodeRole + ". Supported: server, all");
        }
    }

    @Override
    public void restartAllNodes(ClientBase cluster, String nodeRole, RestartMode mode) throws Exception {
        // For standalone server, restart all is the same as restart one
        restartServer(cluster, mode);
    }

    @Override
    public void waitActive(ClientBase cluster) throws Exception {
        String hostPort = cluster.hostPort;
        ServerCnxnFactory factory = cluster.serverFactory;
        boolean isSecure = factory != null && factory.isSecure();

        boolean serverUp = ClientBase.waitForServerUp(hostPort, ClientBase.CONNECTION_TIMEOUT, isSecure);
        if (!serverUp) {
            throw new Exception("Server failed to start at " + hostPort);
        }
        LOG.info("Server is up at {}", hostPort);

        // Wait for existing ZooKeeper clients to reconnect after server restart
        LOG.info("Waiting for clients to reconnect...");
        cluster.waitForClientReconnection(ClientBase.CONNECTION_TIMEOUT);
    }

    @Override
    public StateCapture<ClientBase> getStateCapture() {
        return stateCapture;
    }

    @Override
    public HealthCheck<ClientBase> getHealthCheck() {
        // No custom health checks - rely on waitActive()
        return null;
    }

    @Override
    public int getNodeCount(ClientBase cluster, String nodeRole) throws Exception {
        String normalizedRole = normalizeRole(nodeRole);
        if ("server".equals(normalizedRole) || "all".equals(normalizedRole)) {
            return 1; // Only one standalone server
        } else {
            throw new IllegalArgumentException("Unknown node role: " + nodeRole);
        }
    }

    private String normalizeRole(String role) {
        String lower = role.toLowerCase();
        // "server" and "all" are both valid for standalone
        return lower;
    }

    private void restartServer(ClientBase cluster, RestartMode mode) throws Exception {
        String hostPort = cluster.hostPort;
        File tmpDir = cluster.tmpDir;
        int maxCnxns = cluster.maxCnxns;
        ServerCnxnFactory factory = cluster.serverFactory;
        boolean isSecure = factory != null && factory.isSecure();

        LOG.info("Restarting server at {} with mode {}", hostPort, mode);

        switch (mode) {
            case GRACEFUL:
                // Graceful shutdown with cleanup
                ClientBase.shutdownServerInstance(factory, hostPort);
                ClientBase.waitForServerDown(hostPort, ClientBase.CONNECTION_TIMEOUT, isSecure);

                // Create and start new instance
                factory = ClientBase.createNewServerInstance(null, hostPort, maxCnxns);
                cluster.serverFactory = factory;
                ClientBase.startServerInstance(tmpDir, factory, hostPort, 1);
                break;

            case CRASH:
                // Abrupt shutdown - close database but don't wait for server down
                if (factory != null) {
                    ZKDatabase zkDb = null;
                    ZooKeeperServer zs = factory.getZooKeeperServer();
                    if (zs != null) {
                        zkDb = zs.getZKDatabase();
                    }
                    factory.shutdown();
                    if (zkDb != null) {
                        try {
                            zkDb.close();
                        } catch (IOException e) {
                            LOG.warn("Error closing database in crash mode", e);
                        }
                    }
                }

                // Immediate restart (no waitForServerDown)
                factory = ClientBase.createNewServerInstance(null, hostPort, maxCnxns);
                cluster.serverFactory = factory;
                ClientBase.startServerInstance(tmpDir, factory, hostPort, 1);
                break;

            case DELAYED_CRASH:
                // Crash and wait before restart
                if (factory != null) {
                    ZKDatabase zkDbDelayed = null;
                    ZooKeeperServer zsDelayed = factory.getZooKeeperServer();
                    if (zsDelayed != null) {
                        zkDbDelayed = zsDelayed.getZKDatabase();
                    }
                    factory.shutdown();
                    if (zkDbDelayed != null) {
                        try {
                            zkDbDelayed.close();
                        } catch (IOException e) {
                            LOG.warn("Error closing database in delayed crash mode", e);
                        }
                    }
                }

                Thread.sleep(500); // Default delay

                factory = ClientBase.createNewServerInstance(null, hostPort, maxCnxns);
                cluster.serverFactory = factory;
                ClientBase.startServerInstance(tmpDir, factory, hostPort, 1);
                break;

            default:
                throw new IllegalArgumentException("Unknown restart mode: " + mode);
        }

        LOG.info("Server restarted successfully at {}", hostPort);
    }
}
