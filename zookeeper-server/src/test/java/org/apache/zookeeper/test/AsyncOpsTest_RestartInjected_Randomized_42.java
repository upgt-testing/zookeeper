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
package org.apache.zookeeper.test;

import java.util.concurrent.CountDownLatch;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.test.AsyncOps.ACLCB;
import org.apache.zookeeper.test.AsyncOps.Children2CB;
import org.apache.zookeeper.test.AsyncOps.ChildrenCB;
import org.apache.zookeeper.test.AsyncOps.Create2CB;
import org.apache.zookeeper.test.AsyncOps.DataCB;
import org.apache.zookeeper.test.AsyncOps.MultiCB;
import org.apache.zookeeper.test.AsyncOps.StatCB;
import org.apache.zookeeper.test.AsyncOps.StringCB;
import org.apache.zookeeper.test.AsyncOps.VoidCB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncOpsTest_RestartInjected_Randomized_42 extends ClientBase {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncOpsTest_RestartInjected.class);

    private ZooKeeper zk;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        LOG.info("Creating client {}", getTestName());
        zk = createClient();
        zk.addAuthInfo("digest", "ben:passwd".getBytes());
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        zk.close();
        super.tearDown();
        LOG.info("Test clients shutting down");
    }

    @Test
    public void testAsyncCreate() {
        new StringCB(zk).verifyCreate();
        RestartFramework.at("after_async_create").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncCreate2() {
        RestartFramework.at("after_async_create2").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new Create2CB(zk).verifyCreate();
    }

    @Test
    public void testAsyncCreateThree() {
        CountDownLatch latch = new CountDownLatch(3);
        StringCB op1 = new StringCB(zk, latch);
        op1.setPath("/op1");
        RestartFramework.at("after_verify_three").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        StringCB op2 = new StringCB(zk, latch);
        op2.setPath("/op2");
        StringCB op3 = new StringCB(zk, latch);
        op3.setPath("/op3");
        op1.create();
        op2.create();
        op3.create();
        op1.verify();
        op2.verify();
        RestartFramework.at("after_create_three").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        op3.verify();
    }

    @Test
    public void testAsyncCreateFailure_NodeExists() {
        RestartFramework.at("after_create_failure_node_exists").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new StringCB(zk).verifyCreateFailure_NodeExists();
    }

    @Test
    public void testAsyncCreateFailure_NoNode() {
        new StringCB(zk).verifyCreateFailure_NoNode();
        RestartFramework.at("after_create_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncCreateFailure_NoChildForEphemeral() {
        RestartFramework.at("after_create_failure_no_child").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new StringCB(zk).verifyCreateFailure_NoChildForEphemeral();
    }

    @Test
    public void testAsyncCreate2Failure_NodeExists() {
        new Create2CB(zk).verifyCreateFailure_NodeExists();
        RestartFramework.at("after_create2_failure_node_exists").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncCreate2Failure_NoNode() {
        new Create2CB(zk).verifyCreateFailure_NoNode();
        RestartFramework.at("after_create2_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncCreate2Failure_NoChildForEphemeral() {
        RestartFramework.at("after_create2_failure_no_child").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new Create2CB(zk).verifyCreateFailure_NoChildForEphemeral();
    }

    @Test
    public void testAsyncDelete() {
        new VoidCB(zk).verifyDelete();
        RestartFramework.at("after_async_delete").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncDeleteFailure_NoNode() {
        RestartFramework.at("after_delete_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new VoidCB(zk).verifyDeleteFailure_NoNode();
    }

    @Test
    public void testAsyncDeleteFailure_BadVersion() {
        RestartFramework.at("after_delete_failure_bad_version").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new VoidCB(zk).verifyDeleteFailure_BadVersion();
    }

    @Test
    public void testAsyncDeleteFailure_NotEmpty() {
        RestartFramework.at("after_delete_failure_not_empty").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new VoidCB(zk).verifyDeleteFailure_NotEmpty();
    }

    @Test
    public void testAsyncSync() {
        RestartFramework.at("after_async_sync").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new VoidCB(zk).verifySync();
    }

    @Test
    public void testAsyncSetACL() {
        new StatCB(zk).verifySetACL();
        RestartFramework.at("after_async_set_acl").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncSetACLFailure_NoNode() {
        RestartFramework.at("after_set_acl_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new StatCB(zk).verifySetACLFailure_NoNode();
    }

    @Test
    public void testAsyncSetACLFailure_BadVersion() {
        new StatCB(zk).verifySetACLFailure_BadVersion();
        RestartFramework.at("after_set_acl_failure_bad_version").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncSetData() {
        new StatCB(zk).verifySetData();
        RestartFramework.at("after_async_set_data").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncSetDataFailure_NoNode() {
        new StatCB(zk).verifySetDataFailure_NoNode();
        RestartFramework.at("after_set_data_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncSetDataFailure_BadVersion() {
        new StatCB(zk).verifySetDataFailure_BadVersion();
        RestartFramework.at("after_set_data_failure_bad_version").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncExists() {
        RestartFramework.at("after_async_exists").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new StatCB(zk).verifyExists();
    }

    @Test
    public void testAsyncExistsFailure_NoNode() {
        RestartFramework.at("after_exists_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new StatCB(zk).verifyExistsFailure_NoNode();
    }

    @Test
    public void testAsyncGetACL() {
        RestartFramework.at("after_async_get_acl").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new ACLCB(zk).verifyGetACL();
    }

    @Test
    public void testAsyncGetACLFailure_NoNode() {
        new ACLCB(zk).verifyGetACLFailure_NoNode();
        RestartFramework.at("after_get_acl_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncGetChildrenEmpty() {
        new ChildrenCB(zk).verifyGetChildrenEmpty();
        RestartFramework.at("after_get_children_empty").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncGetChildrenSingle() {
        RestartFramework.at("after_get_children_single").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new ChildrenCB(zk).verifyGetChildrenSingle();
    }

    @Test
    public void testAsyncGetChildrenTwo() {
        new ChildrenCB(zk).verifyGetChildrenTwo();
        RestartFramework.at("after_get_children_two").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncGetChildrenFailure_NoNode() {
        RestartFramework.at("after_get_children_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new ChildrenCB(zk).verifyGetChildrenFailure_NoNode();
    }

    @Test
    public void testAsyncGetChildren2Empty() {
        RestartFramework.at("after_get_children2_empty").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new Children2CB(zk).verifyGetChildrenEmpty();
    }

    @Test
    public void testAsyncGetChildren2Single() {
        new Children2CB(zk).verifyGetChildrenSingle();
        RestartFramework.at("after_get_children2_single").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncGetChildren2Two() {
        RestartFramework.at("after_get_children2_two").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new Children2CB(zk).verifyGetChildrenTwo();
    }

    @Test
    public void testAsyncGetChildren2Failure_NoNode() {
        RestartFramework.at("after_get_children2_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new Children2CB(zk).verifyGetChildrenFailure_NoNode();
    }

    @Test
    public void testAsyncGetData() {
        RestartFramework.at("after_async_get_data").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new DataCB(zk).verifyGetData();
    }

    @Test
    public void testAsyncGetDataFailure_NoNode() {
        new DataCB(zk).verifyGetDataFailure_NoNode();
        RestartFramework.at("after_get_data_failure_no_node").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void testAsyncMulti() {
        RestartFramework.at("after_async_multi").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new MultiCB(zk).verifyMulti();
    }

    @Test
    public void testAsyncMultiFailure_AllErrorResult() {
        RestartFramework.at("after_multi_failure_all_error").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new MultiCB(zk).verifyMultiFailure_AllErrorResult();
    }

    @Test
    public void testAsyncMultiFailure_NoSideEffect() throws Exception {
        RestartFramework.at("after_multi_failure_no_side_effect").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        new MultiCB(zk).verifyMultiFailure_NoSideEffect();
    }

    @Test
    public void testAsyncMultiSequential_NoSideEffect() throws Exception {
        new MultiCB(zk).verifyMultiSequential_NoSideEffect();
        RestartFramework.at("after_multi_sequential_no_side_effect").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }
}
