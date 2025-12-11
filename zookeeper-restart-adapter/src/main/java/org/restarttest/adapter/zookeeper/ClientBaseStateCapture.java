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
import java.util.List;
import java.util.Map;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.test.ClientBase;
import org.restarttest.state.AbstractStateCapture;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;
import org.restarttest.state.StateVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State capture for ZooKeeper standalone server (ClientBase).
 * Captures root node children count and server status.
 */
public class ClientBaseStateCapture extends AbstractStateCapture<ClientBase> {

    private static final Logger LOG = LoggerFactory.getLogger(ClientBaseStateCapture.class);

    @Override
    public ClusterState captureState(ClientBase cluster) throws Exception {
        Map<String, Object> state = new HashMap<>();

        try {
            // Get server factory to check if server is running
            ServerCnxnFactory factory = cluster.serverFactory;
            state.put("server_running", factory != null);

            if (factory != null) {
                state.put("is_secure", factory.isSecure());
            }

            // Try to connect and get root node children count
            String hostPort = cluster.hostPort;
            try {
                ZooKeeper zk = ClientBase.createZKClient(hostPort, ClientBase.CONNECTION_TIMEOUT);
                try {
                    List<String> children = zk.getChildren("/", false);
                    state.put("root_children_count", children.size());
                    state.put("root_children", children);
                    LOG.info("Captured state: {} children at root", children.size());
                } finally {
                    zk.close();
                }
            } catch (Exception e) {
                LOG.warn("Could not connect to server to capture state: {}", e.getMessage());
                state.put("root_children_count", -1);
            }

        } catch (Exception e) {
            LOG.error("Error capturing state", e);
            throw e;
        }

        return new DefaultClusterState(state);
    }

    @Override
    protected void verifyCustomInvariants(ClientBase cluster, ClusterState before, ClusterState after)
            throws Exception {
        LOG.info("Verifying state invariants");

        // Verify root children count is preserved
        Integer beforeCount = (Integer) before.getStateMap().get("root_children_count");
        Integer afterCount = (Integer) after.getStateMap().get("root_children_count");

        if (beforeCount != null && afterCount != null && beforeCount >= 0 && afterCount >= 0) {
            if (!beforeCount.equals(afterCount)) {
                throw new StateVerificationException(
                    "Root children count changed: before=" + beforeCount + ", after=" + afterCount);
            }
            LOG.info("Root children count verified: {}", beforeCount);
        }

        // Verify server is still running after restart
        Boolean afterRunning = (Boolean) after.getStateMap().get("server_running");
        if (afterRunning == null || !afterRunning) {
            throw new StateVerificationException("Server is not running after restart");
        }
    }
}
