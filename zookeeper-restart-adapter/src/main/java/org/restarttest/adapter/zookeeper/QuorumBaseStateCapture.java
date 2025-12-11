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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumPeer.LearnerType;
import org.apache.zookeeper.server.quorum.QuorumPeer.ServerState;
import org.apache.zookeeper.test.ClientBase;
import org.apache.zookeeper.test.QuorumBase;
import org.restarttest.state.AbstractStateCapture;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;
import org.restarttest.state.StateVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State capture for ZooKeeper quorum cluster (QuorumBase).
 * Captures cluster topology, server states, and data consistency.
 */
public class QuorumBaseStateCapture extends AbstractStateCapture<QuorumBase> {

    private static final Logger LOG = LoggerFactory.getLogger(QuorumBaseStateCapture.class);

    @Override
    public ClusterState captureState(QuorumBase cluster) throws Exception {
        Map<String, Object> state = new HashMap<>();

        try {
            // Capture cluster topology
            int leaderCount = 0;
            int followerCount = 0;
            int observerCount = 0;
            int lookingCount = 0;
            List<String> serverStates = new ArrayList<>();

            for (QuorumPeer peer : cluster.getPeerList()) {
                if (peer != null) {
                    ServerState peerState = peer.getPeerState();
                    serverStates.add(peer.getMyId() + ":" + peerState);

                    if (peerState == ServerState.LEADING) {
                        leaderCount++;
                    } else if (peerState == ServerState.FOLLOWING) {
                        followerCount++;
                    } else if (peerState == ServerState.OBSERVING) {
                        observerCount++;
                    } else if (peerState == ServerState.LOOKING) {
                        lookingCount++;
                    }
                }
            }

            state.put("leader_count", leaderCount);
            state.put("follower_count", followerCount);
            state.put("observer_count", observerCount);
            state.put("looking_count", lookingCount);
            state.put("server_states", serverStates);

            // Capture leader information
            QuorumPeer leader = cluster.getLeaderQuorumPeer();
            if (leader != null) {
                state.put("leader_id", leader.getMyId());
                try {
                    long lastZxid = leader.getLastLoggedZxid();
                    state.put("leader_zxid", lastZxid);
                } catch (Exception e) {
                    LOG.warn("Could not get leader ZXID: {}", e.getMessage());
                }
            }

            // Capture data consistency - root node children count across all servers
            Map<Long, Integer> rootChildrenPerServer = new HashMap<>();
            for (QuorumPeer peer : cluster.getPeerList()) {
                if (peer != null && peer.getClientAddress() != null) {
                    try {
                        String hp = "127.0.0.1:" + peer.getClientAddress().getPort();
                        ZooKeeper zk = ClientBase.createZKClient(hp, ClientBase.CONNECTION_TIMEOUT);
                        try {
                            List<String> children = zk.getChildren("/", false);
                            rootChildrenPerServer.put(peer.getMyId(), children.size());
                        } finally {
                            zk.close();
                        }
                    } catch (Exception e) {
                        LOG.warn("Could not get children from server {}: {}", peer.getMyId(), e.getMessage());
                    }
                }
            }
            state.put("root_children_per_server", rootChildrenPerServer);

            LOG.info("Captured state: {} leaders, {} followers, {} observers, {} looking",
                leaderCount, followerCount, observerCount, lookingCount);

        } catch (Exception e) {
            LOG.error("Error capturing state", e);
            throw e;
        }

        return new DefaultClusterState(state);
    }

    @Override
    protected void verifyCustomInvariants(QuorumBase cluster, ClusterState before, ClusterState after)
            throws Exception {
        LOG.info("Verifying QuorumBase state invariants");

        // Verify exactly one leader
        Integer afterLeaderCount = (Integer) after.getStateMap().get("leader_count");
        if (afterLeaderCount == null || afterLeaderCount != 1) {
            throw new StateVerificationException(
                "Expected exactly 1 leader, but found " + afterLeaderCount);
        }

        // Verify no servers stuck in LOOKING state
        Integer lookingCount = (Integer) after.getStateMap().get("looking_count");
        if (lookingCount != null && lookingCount > 0) {
            LOG.warn("Warning: {} servers still in LOOKING state after restart", lookingCount);
        }

        // Verify ZXID didn't decrease (data not lost)
        Long beforeZxid = (Long) before.getStateMap().get("leader_zxid");
        Long afterZxid = (Long) after.getStateMap().get("leader_zxid");

        if (beforeZxid != null && afterZxid != null && afterZxid < beforeZxid) {
            throw new StateVerificationException(
                "Leader ZXID decreased (data loss): before=" + beforeZxid + ", after=" + afterZxid);
        }

        // Verify data consistency across servers
        Map<Long, Integer> beforeChildren = (Map<Long, Integer>) before.getStateMap().get("root_children_per_server");
        Map<Long, Integer> afterChildren = (Map<Long, Integer>) after.getStateMap().get("root_children_per_server");

        if (beforeChildren != null && afterChildren != null) {
            // Check if any server lost data
            for (Map.Entry<Long, Integer> entry : beforeChildren.entrySet()) {
                Long serverId = entry.getKey();
                Integer beforeCount = entry.getValue();
                Integer afterCount = afterChildren.get(serverId);

                if (afterCount != null && afterCount < beforeCount) {
                    throw new StateVerificationException(
                        "Server " + serverId + " lost data: before=" + beforeCount + ", after=" + afterCount);
                }
            }

            // Check consistency across servers
            Integer firstCount = null;
            for (Integer count : afterChildren.values()) {
                if (firstCount == null) {
                    firstCount = count;
                } else if (!firstCount.equals(count)) {
                    LOG.warn("Warning: Data inconsistency detected across servers: {}", afterChildren);
                }
            }
        }

        LOG.info("State invariants verified successfully");
    }
}
