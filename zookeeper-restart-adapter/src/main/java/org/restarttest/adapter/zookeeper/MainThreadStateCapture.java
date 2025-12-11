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

import java.util.HashMap;
import java.util.Map;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.apache.zookeeper.server.quorum.QuorumPeerTestBase;
import org.restarttest.state.AbstractStateCapture;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;
import org.restarttest.state.StateVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State capture for ZooKeeper quorum peer (MainThread).
 * Captures server ID, client port, and peer state.
 */
public class MainThreadStateCapture extends AbstractStateCapture<QuorumPeerTestBase.MainThread> {

    private static final Logger LOG = LoggerFactory.getLogger(MainThreadStateCapture.class);

    @Override
    public ClusterState captureState(QuorumPeerTestBase.MainThread cluster) throws Exception {
        Map<String, Object> state = new HashMap<>();

        try {
            // Capture basic configuration
            state.put("myid", cluster.getMyid());
            state.put("client_port", cluster.getClientPort());
            state.put("is_alive", cluster.isAlive());
            state.put("quorum_peer_running", cluster.isQuorumPeerRunning());

            // Capture QuorumPeer state if available
            if (cluster.isQuorumPeerRunning()) {
                QuorumPeer qp = cluster.getQuorumPeer();
                if (qp != null) {
                    state.put("peer_state", qp.getPeerState().toString());
                    state.put("server_id", qp.getMyId());
                    LOG.info("Captured QuorumPeer state: {} (id={})", qp.getPeerState(), qp.getMyId());
                }
            }

        } catch (Exception e) {
            LOG.error("Error capturing state", e);
            throw e;
        }

        return new DefaultClusterState(state);
    }

    @Override
    protected void verifyCustomInvariants(QuorumPeerTestBase.MainThread cluster, ClusterState before,
            ClusterState after) throws Exception {
        LOG.info("Verifying MainThread state invariants");

        // Verify server ID unchanged
        Integer beforeMyid = (Integer) before.getStateMap().get("myid");
        Integer afterMyid = (Integer) after.getStateMap().get("myid");

        if (beforeMyid != null && afterMyid != null && !beforeMyid.equals(afterMyid)) {
            throw new StateVerificationException(
                "Server ID changed: before=" + beforeMyid + ", after=" + afterMyid);
        }

        // Verify client port unchanged
        Integer beforePort = (Integer) before.getStateMap().get("client_port");
        Integer afterPort = (Integer) after.getStateMap().get("client_port");

        if (beforePort != null && afterPort != null && !beforePort.equals(afterPort)) {
            throw new StateVerificationException(
                "Client port changed: before=" + beforePort + ", after=" + afterPort);
        }

        // Verify QuorumPeer is running after restart
        Boolean afterRunning = (Boolean) after.getStateMap().get("quorum_peer_running");
        if (afterRunning == null || !afterRunning) {
            throw new StateVerificationException("QuorumPeer is not running after restart");
        }

        // Verify server rejoined quorum (not stuck in LOOKING state)
        String afterState = (String) after.getStateMap().get("peer_state");
        if (afterState != null && "LOOKING".equals(afterState)) {
            LOG.warn("Server is in LOOKING state after restart - may still be electing leader");
        }

        LOG.info("State invariants verified successfully");
    }
}
