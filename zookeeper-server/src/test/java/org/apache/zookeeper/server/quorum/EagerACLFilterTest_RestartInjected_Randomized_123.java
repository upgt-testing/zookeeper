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
package org.apache.zookeeper.server.quorum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.stream.Stream;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.TestableZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.quorum.QuorumPeer.ServerState;
import org.apache.zookeeper.test.QuorumBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class EagerACLFilterTest_RestartInjected_Randomized_123 extends QuorumBase {

    protected boolean complete = false;

    protected static final String PARENT_PATH = "/foo";

    protected static final String CHILD_PATH = "/foo/bar";

    protected static final String AUTH_PROVIDER = "digest";

    protected static final byte[] AUTH = "hello".getBytes();

    protected static final byte[] AUTHB = "goodbye".getBytes();

    protected static final byte[] DATA = "Hint Water".getBytes();

    protected TestableZooKeeper zkClient;

    protected TestableZooKeeper zkClientB;

    protected TestableZooKeeper zkLeaderClient;

    protected QuorumPeer zkLeader;

    protected QuorumPeer zkConnected;

    protected ZooKeeperServer connectedServer;

    public static Stream<Arguments> data() {
        return Stream.of(Arguments.of(ServerState.LEADING, true), Arguments.of(ServerState.LEADING, false), Arguments.of(ServerState.FOLLOWING, true), Arguments.of(ServerState.FOLLOWING, false), Arguments.of(ServerState.OBSERVING, true), Arguments.of(ServerState.OBSERVING, false));
    }

    @BeforeEach
    @Override
    public void setUp() {
        //since parameterized test methods need a parameterized setUp method
        //the inherited method has to be overridden with an empty function body
    }

    public void setUp(ServerState serverState, boolean checkEnabled) throws Exception {
        ensureCheck(checkEnabled);
        CountdownWatcher leaderWatch = new CountdownWatcher();
        CountdownWatcher clientWatch = new CountdownWatcher();
        CountdownWatcher clientWatchB = new CountdownWatcher();
        super.setUp(true, true);
        String hostPort = getPeersMatching(serverState).split(",")[0];
        int clientPort = Integer.parseInt(hostPort.split(":")[1]);
        zkLeader = getPeerList().get(getLeaderIndex());
        zkConnected = getPeerByClientPort(clientPort);
        connectedServer = zkConnected.getActiveServer();
        zkLeaderClient = createClient(leaderWatch, getPeersMatching(ServerState.LEADING));
        zkClient = createClient(clientWatch, hostPort);
        zkClientB = createClient(clientWatchB, hostPort);
        zkClient.addAuthInfo(AUTH_PROVIDER, AUTH);
        zkClientB.addAuthInfo(AUTH_PROVIDER, AUTHB);
        leaderWatch.waitForConnected(CONNECTION_TIMEOUT);
        clientWatch.waitForConnected(CONNECTION_TIMEOUT);
        clientWatchB.waitForConnected(CONNECTION_TIMEOUT);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (zkClient != null) {
            zkClient.close();
        }
        if (zkClientB != null) {
            zkClientB.close();
        }
        super.tearDown();
    }

    private void ensureCheck(boolean enabled) {
        ZooKeeperServer.setEnableEagerACLCheck(enabled);
    }

    private void assertTransactionState(String operation, QuorumPeer peer, long lastxid) throws Exception {
        if (peer == zkLeader && peer != zkConnected) {
            // The operation is performed on no leader, but we are asserting on leader.
            // There is no happen-before between `zkLeader.getLastLoggedZxid()` and
            // successful response from other server. The commit and response are routed
            // to different servers and performed asynchronous in each server. So we have
            // to sync leader client to go through commit and response path in leader to
            // build happen-before between `zkLeader.getLastLoggedZxid()` and side effect
            // of previous operation.
            syncClient(zkLeaderClient, false);
        }
        assertTrue(peer == zkLeader || peer == zkConnected);
        boolean eagerACL = ZooKeeperServer.isEnableEagerACLCheck();
        String assertion = String.format("Connecting: %s Checking: %s EagerACL: %s Operation: %s", zkConnected.getPeerState(), peer.getPeerState(), eagerACL, operation);
        if (eagerACL) {
            assertEquals(lastxid, peer.getLastLoggedZxid(), assertion);
        } else {
            assertNotEquals(lastxid, peer.getLastLoggedZxid(), assertion);
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testCreateOK(ServerState serverState, boolean checkEnabled) throws Exception {
        setUp(serverState, checkEnabled);
        RestartFramework.at("after_create_parent").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ensureCheck(true);
        zkClient.create(PARENT_PATH, DATA, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        RestartFramework.at("after_create_child").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        zkClientB.create(CHILD_PATH, DATA, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testCreate2OK(ServerState serverState, boolean checkEnabled) throws Exception {
        RestartFramework.at("after_create_child").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        setUp(serverState, checkEnabled);
        zkClient.create(PARENT_PATH, DATA, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, null);
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_create_parent").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        zkClientB.create(CHILD_PATH, DATA, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, null);
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testCreateFail(ServerState serverState, boolean checkEnabled) throws Exception {
        setUp(serverState, checkEnabled);
        zkClient.create(PARENT_PATH, DATA, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
        RestartFramework.at("after_failed_create").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        long lastxid = zkConnected.getLastLoggedZxid();
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try {
            zkClientB.create(CHILD_PATH, DATA, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            fail("expect no auth");
        } catch (KeeperException.NoAuthException e) {
        }
        RestartFramework.at("after_create_parent_with_acl").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
        assertTransactionState("failed create", zkConnected, lastxid);
        assertTransactionState("failed create", zkLeader, lastxid);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testCreate2Fail(ServerState serverState, boolean checkEnabled) throws Exception {
        setUp(serverState, checkEnabled);
        zkClient.create(PARENT_PATH, DATA, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT, null);
        RestartFramework.at("after_failed_create2").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        long lastxid = zkConnected.getLastLoggedZxid();
        try {
            zkClientB.create(CHILD_PATH, DATA, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, null);
            fail("expect no auth");
        } catch (KeeperException.NoAuthException e) {
        }
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
        assertTransactionState("failed create2", zkConnected, lastxid);
        RestartFramework.at("after_create_parent_with_acl").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertTransactionState("failed create2", zkLeader, lastxid);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testDeleteOK(ServerState serverState, boolean checkEnabled) throws Exception {
        setUp(serverState, checkEnabled);
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        zkClient.create(PARENT_PATH, DATA, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zkClientB.delete(PARENT_PATH, -1);
        RestartFramework.at("after_create_parent").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_delete").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testDeleteFail(ServerState serverState, boolean checkEnabled) throws Exception {
        setUp(serverState, checkEnabled);
        zkClient.create(PARENT_PATH, DATA, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT, null);
        zkClient.create(CHILD_PATH, DATA, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT, null);
        RestartFramework.at("after_create_parent_with_acl").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        long lastxid = zkConnected.getLastLoggedZxid();
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        try {
            zkClientB.delete(CHILD_PATH, -1);
            fail("expect no auth");
        } catch (KeeperException.NoAuthException e) {
        }
        RestartFramework.at("after_create_child_with_acl").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_failed_delete").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
        assertTransactionState("failed delete", zkConnected, lastxid);
        assertTransactionState("failed delete", zkLeader, lastxid);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSetDataOK(ServerState serverState, boolean checkEnabled) throws Exception {
        RestartFramework.at("after_set_data").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        setUp(serverState, checkEnabled);
        RestartFramework.at("after_create_parent").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        zkClient.create(PARENT_PATH, null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, null);
        zkClientB.setData(PARENT_PATH, DATA, -1);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSetDataFail(ServerState serverState, boolean checkEnabled) throws Exception {
        RestartFramework.at("after_create_parent_with_acl").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        setUp(serverState, checkEnabled);
        zkClient.create(PARENT_PATH, null, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT, null);
        long lastxid = zkConnected.getLastLoggedZxid();
        try {
            zkClientB.setData(PARENT_PATH, DATA, -1);
            fail("expect no auth");
        } catch (KeeperException.NoAuthException e) {
        }
        RestartFramework.at("after_failed_set_data").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertTransactionState("failed setData", zkConnected, lastxid);
        assertTransactionState("failed setData", zkLeader, lastxid);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSetACLOK(ServerState serverState, boolean checkEnabled) throws Exception {
        setUp(serverState, checkEnabled);
        zkClient.create(PARENT_PATH, null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, null);
        RestartFramework.at("after_create_parent").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        zkClientB.setACL(PARENT_PATH, Ids.READ_ACL_UNSAFE, -1);
        RestartFramework.at("after_set_acl").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSetACLFail(ServerState serverState, boolean checkEnabled) throws Exception {
        setUp(serverState, checkEnabled);
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_failed_set_acl").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        zkClient.create(PARENT_PATH, null, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT, null);
        RestartFramework.at("after_create_parent_with_acl").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        long lastxid = zkConnected.getLastLoggedZxid();
        try {
            zkClientB.setACL(PARENT_PATH, Ids.READ_ACL_UNSAFE, -1);
            fail("expect no auth");
        } catch (KeeperException.NoAuthException ignored) {
        }
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
        assertTransactionState("failed setACL", zkConnected, lastxid);
        assertTransactionState("failed setACL", zkLeader, lastxid);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testBadACL(ServerState serverState, boolean checkEnabled) throws Exception {
        setUp(serverState, checkEnabled);
        CountdownWatcher cw = new CountdownWatcher();
        String addr = String.format("%s:%d", LOCALADDR, zkConnected.getClientPort());
        RestartFramework.at("after_client_connected").on(this).restart("follower").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        TestableZooKeeper zk = createClient(cw, addr);
        cw.waitForConnected(CONNECTION_TIMEOUT);
        long lastxid = zkConnected.getLastLoggedZxid();
        try {
            zk.create("/acltest", new byte[0], Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
            fail("Should have received an invalid acl error");
        } catch (KeeperException.InvalidACLException e) {
        }
        RestartFramework.at("after_invalid_acl_error").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, connectedServer.getInProcess(), "OutstandingRequests not decremented");
        RestartFramework.at("after_setup").on(this).restart("leader").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertTransactionState("invalid ACL", zkConnected, lastxid);
        assertTransactionState("invalid ACL", zkLeader, lastxid);
    }
}
