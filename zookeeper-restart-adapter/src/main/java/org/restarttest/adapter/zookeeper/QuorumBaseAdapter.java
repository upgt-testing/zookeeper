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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumPeer.LearnerType;
import org.apache.zookeeper.server.quorum.QuorumPeer.ServerState;
import org.apache.zookeeper.test.ClientBase;
import org.apache.zookeeper.test.QuorumBase;
import org.restarttest.core.ClusterAdapter;
import org.restarttest.core.RestartMode;
import org.restarttest.health.HealthCheck;
import org.restarttest.state.StateCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for ZooKeeper quorum cluster tests using QuorumBase.
 * Supports restarting servers by role (leader/follower/observer) or by index (0-4 for s1-s5).
 */
public class QuorumBaseAdapter implements ClusterAdapter<QuorumBase> {

    private static final Logger LOG = LoggerFactory.getLogger(QuorumBaseAdapter.class);
    private final QuorumBaseStateCapture stateCapture;
    private final Random random = new Random();

    public QuorumBaseAdapter() {
        this.stateCapture = new QuorumBaseStateCapture();
    }

    @Override
    public Class<QuorumBase> getClusterType() {
        return QuorumBase.class;
    }

    @Override
    public void restartNode(QuorumBase cluster, String nodeRole, int nodeIndex, RestartMode mode) throws Exception {
        String normalizedRole = normalizeRole(nodeRole);

        if ("leader".equals(normalizedRole) || "master".equals(normalizedRole)) {
            restartLeader(cluster, mode);
        } else if ("follower".equals(normalizedRole) || "worker".equals(normalizedRole)) {
            restartFollower(cluster, nodeIndex, mode);
        } else if ("observer".equals(normalizedRole)) {
            restartObserver(cluster, nodeIndex, mode);
        } else if ("server".equals(normalizedRole)) {
            restartByIndex(cluster, nodeIndex, mode);
        } else if ("all".equals(normalizedRole)) {
            restartAllServers(cluster, mode);
        } else {
            throw new IllegalArgumentException(
                "Unknown node role: " + nodeRole + ". Supported: leader, follower, observer, server, all");
        }
    }

    @Override
    public void restartAllNodes(QuorumBase cluster, String nodeRole, RestartMode mode) throws Exception {
        String normalizedRole = normalizeRole(nodeRole);

        if ("all".equals(normalizedRole) || "server".equals(normalizedRole)) {
            restartAllServers(cluster, mode);
        } else if ("leader".equals(normalizedRole) || "master".equals(normalizedRole)) {
            restartLeader(cluster, mode);
        } else if ("follower".equals(normalizedRole) || "worker".equals(normalizedRole)) {
            // Restart all followers
            for (int i = 0; i < 5; i++) {
                QuorumPeer peer = getServerByIndex(cluster, i);
                if (peer != null && peer.getPeerState() == ServerState.FOLLOWING) {
                    restartByIndex(cluster, i, mode);
                }
            }
        } else if ("observer".equals(normalizedRole)) {
            // Restart all observers
            for (int i = 0; i < 5; i++) {
                QuorumPeer peer = getServerByIndex(cluster, i);
                if (peer != null && peer.getLearnerType() == LearnerType.OBSERVER) {
                    restartByIndex(cluster, i, mode);
                }
            }
        } else {
            throw new IllegalArgumentException("Unknown node role: " + nodeRole);
        }
    }

    @Override
    public void waitActive(QuorumBase cluster) throws Exception {
        // Wait for all servers to be up and responsive
        String hostPort = cluster.hostPort;
        String[] hostPorts = hostPort.split(",");
        for (String hp : hostPorts) {
            boolean serverUp = ClientBase.waitForServerUp(hp, ClientBase.CONNECTION_TIMEOUT);
            if (!serverUp) {
                throw new Exception("Server failed to start at " + hp);
            }
        }

        // Wait for leader election to complete
        int retries = 30;
        while (cluster.getLeaderQuorumPeer() == null && retries > 0) {
            Thread.sleep(1000);
            retries--;
        }

        if (cluster.getLeaderQuorumPeer() == null) {
            throw new Exception("No leader elected after restart");
        }

        LOG.info("Cluster is active with leader at port {}", cluster.getLeaderClientPort());
    }

    @Override
    public StateCapture<QuorumBase> getStateCapture() {
        return stateCapture;
    }

    @Override
    public HealthCheck<QuorumBase> getHealthCheck() {
        // No custom health checks - rely on waitActive()
        return null;
    }

    @Override
    public int getNodeCount(QuorumBase cluster, String nodeRole) throws Exception {
        String normalizedRole = normalizeRole(nodeRole);

        if ("all".equals(normalizedRole) || "server".equals(normalizedRole)) {
            return 5; // QuorumBase always has 5 servers
        } else if ("leader".equals(normalizedRole) || "master".equals(normalizedRole)) {
            return cluster.getLeaderQuorumPeer() != null ? 1 : 0;
        } else if ("follower".equals(normalizedRole) || "worker".equals(normalizedRole)) {
            int count = 0;
            for (QuorumPeer peer : cluster.getPeerList()) {
                if (peer != null && peer.getPeerState() == ServerState.FOLLOWING) {
                    count++;
                }
            }
            return count;
        } else if ("observer".equals(normalizedRole)) {
            int count = 0;
            for (QuorumPeer peer : cluster.getPeerList()) {
                if (peer != null && peer.getLearnerType() == LearnerType.OBSERVER) {
                    count++;
                }
            }
            return count;
        } else {
            throw new IllegalArgumentException("Unknown node role: " + nodeRole);
        }
    }

    private String normalizeRole(String role) {
        String lower = role.toLowerCase();
        // Map generic names to ZooKeeper-specific names
        if ("master".equals(lower)) {
            return "leader";
        }
        if ("worker".equals(lower)) {
            return "follower";
        }
        return lower;
    }

    private void restartLeader(QuorumBase cluster, RestartMode mode) throws Exception {
        int leaderIndex = cluster.getLeaderIndex();
        if (leaderIndex < 0) {
            throw new Exception("No leader found in cluster");
        }
        LOG.info("Restarting leader at index {}", leaderIndex);
        restartByIndex(cluster, leaderIndex, mode);
    }

    private void restartFollower(QuorumBase cluster, int followerIndex, RestartMode mode) throws Exception {
        // Find a follower (either specific index or first follower found)
        List<Integer> followerIndices = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            QuorumPeer peer = getServerByIndex(cluster, i);
            if (peer != null && peer.getPeerState() == ServerState.FOLLOWING) {
                followerIndices.add(i);
            }
        }

        if (followerIndices.isEmpty()) {
            throw new Exception("No followers found in cluster");
        }

        int indexToRestart;
        if (followerIndex >= 0 && followerIndex < followerIndices.size()) {
            indexToRestart = followerIndices.get(followerIndex);
        } else {
            indexToRestart = followerIndices.get(0); // First follower
        }

        LOG.info("Restarting follower at index {}", indexToRestart);
        restartByIndex(cluster, indexToRestart, mode);
    }

    private void restartObserver(QuorumBase cluster, int observerIndex, RestartMode mode) throws Exception {
        // Find an observer
        List<Integer> observerIndices = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            QuorumPeer peer = getServerByIndex(cluster, i);
            if (peer != null && peer.getLearnerType() == LearnerType.OBSERVER) {
                observerIndices.add(i);
            }
        }

        if (observerIndices.isEmpty()) {
            throw new Exception("No observers found in cluster");
        }

        int indexToRestart;
        if (observerIndex >= 0 && observerIndex < observerIndices.size()) {
            indexToRestart = observerIndices.get(observerIndex);
        } else {
            indexToRestart = observerIndices.get(0);
        }

        LOG.info("Restarting observer at index {}", indexToRestart);
        restartByIndex(cluster, indexToRestart, mode);
    }

    private void restartByIndex(QuorumBase cluster, int index, RestartMode mode) throws Exception {
        if (index < 0 || index > 4) {
            throw new IllegalArgumentException("Index must be 0-4, got: " + index);
        }

        QuorumPeer server = getServerByIndex(cluster, index);
        if (server == null) {
            throw new Exception("Server at index " + index + " is null");
        }

        int clientPort = getClientPortByIndex(cluster, index);
        String hostPort = "127.0.0.1:" + clientPort;

        LOG.info("Restarting server {} (index {}) at {} with mode {}", server.getMyId(), index, hostPort, mode);

        switch (mode) {
            case GRACEFUL:
                // Graceful shutdown
                QuorumBase.shutdown(server);
                ClientBase.waitForServerDown(hostPort, ClientBase.CONNECTION_TIMEOUT);

                // Recreate and restart
                cluster.setupServer(index + 1); // setupServer uses 1-5
                QuorumPeer newServer = getServerByIndex(cluster, index);
                newServer.start();

                // Wait for server to be up
                ClientBase.waitForServerUp(hostPort, ClientBase.CONNECTION_TIMEOUT);
                break;

            case CRASH:
                // Abrupt shutdown - skip election shutdown
                server.shutdown();

                // Immediate restart
                cluster.setupServer(index + 1);
                QuorumPeer crashServer = getServerByIndex(cluster, index);
                crashServer.start();
                break;

            case DELAYED_CRASH:
                // Crash and wait
                server.shutdown();
                Thread.sleep(500); // Default delay

                cluster.setupServer(index + 1);
                QuorumPeer delayedServer = getServerByIndex(cluster, index);
                delayedServer.start();
                break;

            default:
                throw new IllegalArgumentException("Unknown restart mode: " + mode);
        }

        LOG.info("Server {} restarted successfully", index);
    }

    private void restartAllServers(QuorumBase cluster, RestartMode mode) throws Exception {
        LOG.info("Restarting all servers");
        for (int i = 0; i < 5; i++) {
            restartByIndex(cluster, i, mode);
        }
    }

    // Helper methods to access QuorumBase fields

    private QuorumPeer getServerByIndex(QuorumBase cluster, int index) {
        switch (index) {
            case 0:
                return cluster.s1;
            case 1:
                return cluster.s2;
            case 2:
                return cluster.s3;
            case 3:
                return cluster.s4;
            case 4:
                return cluster.s5;
            default:
                throw new IllegalArgumentException("Index must be 0-4");
        }
    }

    private int getClientPortByIndex(QuorumBase cluster, int index) {
        switch (index) {
            case 0:
                return cluster.portClient1;
            case 1:
                return cluster.portClient2;
            case 2:
                return cluster.portClient3;
            case 3:
                return cluster.portClient4;
            case 4:
                return cluster.portClient5;
            default:
                throw new IllegalArgumentException("Index must be 0-4");
        }
    }
}
