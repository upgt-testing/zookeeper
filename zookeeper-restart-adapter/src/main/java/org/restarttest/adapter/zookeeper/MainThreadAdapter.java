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

import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumPeerTestBase;
import org.apache.zookeeper.test.ClientBase;
import org.restarttest.core.ClusterAdapter;
import org.restarttest.core.RestartMode;
import org.restarttest.health.HealthCheck;
import org.restarttest.state.StateCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        switch (mode) {
            case GRACEFUL:
                // Graceful shutdown
                mainThread.shutdown();
                mainThread.join(5000);

                // Wait for server to be down
                ClientBase.waitForServerDown(hostPort, ClientBase.CONNECTION_TIMEOUT);

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

                mainThread.start();
                break;

            default:
                throw new IllegalArgumentException("Unknown restart mode: " + mode);
        }

        LOG.info("MainThread restarted successfully at {}", hostPort);
    }
}
