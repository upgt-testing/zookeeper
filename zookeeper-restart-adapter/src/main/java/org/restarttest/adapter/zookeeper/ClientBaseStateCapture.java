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
import org.apache.zookeeper.test.ClientBase;
import org.restarttest.state.AbstractStateCapture;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;

/**
 * State capture for ZooKeeper standalone server (ClientBase).
 * No-op implementation - methods are kept for restart-core dependency compatibility.
 */
public class ClientBaseStateCapture extends AbstractStateCapture<ClientBase> {

    @Override
    public ClusterState captureState(ClientBase cluster) throws Exception {
        // No-op: return empty state
        return new DefaultClusterState(new HashMap<>());
    }

    @Override
    protected void verifyCustomInvariants(ClientBase cluster, ClusterState before, ClusterState after)
            throws Exception {
        // No-op
    }
}
