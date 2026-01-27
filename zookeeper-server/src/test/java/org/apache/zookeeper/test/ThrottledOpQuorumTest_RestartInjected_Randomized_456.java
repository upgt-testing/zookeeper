/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.test;

import java.io.IOException;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class ThrottledOpQuorumTest_RestartInjected_Randomized_456 extends QuorumBase {

    @BeforeAll
    public static void applyMockUps() {
        ThrottledOpHelper.applyMockUps();
    }

    @Test
    public void testThrottledOpLeader() throws IOException, InterruptedException, KeeperException {
        ZooKeeper zk = null;
        try {
            zk = createClient("localhost:" + getLeaderClientPort());
            RestartFramework.at("after_create_client_leader").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            ZooKeeperServer zs = getLeaderQuorumPeer().getActiveServer();
            ThrottledOpHelper test = new ThrottledOpHelper();
            test.testThrottledOp(zk, zs);
            RestartFramework.at("after_throttled_op_leader").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        } finally {
            if (zk != null) {
                zk.close();
            }
        }
    }

    @Test
    public void testThrottledAclLeader() throws Exception {
        ZooKeeper zk = null;
        try {
            zk = createClient("localhost:" + getLeaderClientPort());
            RestartFramework.at("after_create_client_acl_leader").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            ZooKeeperServer zs = getLeaderQuorumPeer().getActiveServer();
            ThrottledOpHelper test = new ThrottledOpHelper();
            test.testThrottledAcl(zk, zs);
            RestartFramework.at("after_throttled_acl_leader").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        } finally {
            if (zk != null) {
                zk.close();
            }
        }
    }

    @Test
    public void testThrottledOpFollower() throws IOException, InterruptedException, KeeperException {
        ZooKeeper zk = null;
        try {
            int clientPort = (getLeaderClientPort() == portClient1) ? portClient2 : portClient1;
            zk = createClient("localhost:" + clientPort);
            RestartFramework.at("after_create_client_follower").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            QuorumPeer qp = (getLeaderClientPort() == portClient1) ? s2 : s1;
            ZooKeeperServer zs = qp.getActiveServer();
            ThrottledOpHelper test = new ThrottledOpHelper();
            test.testThrottledOp(zk, zs);
            RestartFramework.at("after_throttled_op_follower").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        } finally {
            if (zk != null) {
                zk.close();
            }
        }
    }

    @Test
    public void testThrottledAclFollower() throws Exception {
        ZooKeeper zk = null;
        try {
            int clientPort = (getLeaderClientPort() == portClient1) ? portClient2 : portClient1;
            zk = createClient("localhost:" + clientPort);
            RestartFramework.at("after_create_client_acl_follower").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            QuorumPeer qp = (getLeaderClientPort() == portClient1) ? s2 : s1;
            ZooKeeperServer zs = qp.getActiveServer();
            ThrottledOpHelper test = new ThrottledOpHelper();
            test.testThrottledAcl(zk, zs);
            RestartFramework.at("after_throttled_acl_follower").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        } finally {
            if (zk != null) {
                zk.close();
            }
        }
    }
}
