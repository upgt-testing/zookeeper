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
package org.apache.zookeeper.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.DeleteContainerRequest;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.test.ClientBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class CreateContainerTest_RestartInjected_Randomized_42 extends ClientBase {

    private ZooKeeper zk;

    private Semaphore completedContainerDeletions;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        zk = createClient();
        completedContainerDeletions = new Semaphore(0);
        ZKDatabase testDatabase = new ZKDatabase(serverFactory.zkServer.getZKDatabase().snapLog) {

            @Override
            public void addCommittedProposal(Request request) {
                super.addCommittedProposal(request);
                if (request.type == ZooDefs.OpCode.deleteContainer) {
                    completedContainerDeletions.release();
                }
            }
        };
        serverFactory.zkServer.setZKDatabase(testDatabase);
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        zk.close();
    }

    @Test
    public void testCreate() throws KeeperException, InterruptedException {
        RestartFramework.at("after_create_foo").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        createNoStatVerifyResult("/foo");
        RestartFramework.at("after_create_foo_child").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        createNoStatVerifyResult("/foo/child");
    }

    @Test
    public void testCreateWithStat() throws KeeperException, InterruptedException {
        Stat stat = createWithStatVerifyResult("/foo");
        Stat childStat = createWithStatVerifyResult("/foo/child");
        RestartFramework.at("after_create_child_with_stat").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_create_foo_with_stat").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        // Don't expect to get the same stats for different creates.
        assertNotEquals(stat, childStat);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testCreateWithNullStat() throws KeeperException, InterruptedException {
        final String name = "/foo";
        assertNull(zk.exists(name, false));
        Stat stat = null;
        // If a null Stat object is passed the create should still
        // succeed, but no Stat info will be returned.
        zk.create(name, name.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER, stat);
        RestartFramework.at("after_create_with_null_stat").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertNull(stat);
        assertNotNull(zk.exists(name, false));
    }

    @Test
    public void testSimpleDeletion() throws KeeperException, InterruptedException {
        zk.create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        zk.create("/foo/bar", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        // should cause "/foo" to get deleted when checkContainers() is called
        zk.delete("/foo/bar", -1);
        RestartFramework.at("after_delete_child").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_check_containers").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ContainerManager containerManager = new ContainerManager(serverFactory.getZooKeeperServer().getZKDatabase(), serverFactory.getZooKeeperServer().firstProcessor, 1, 100);
        containerManager.checkContainers();
        RestartFramework.at("after_create_container_and_child").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertTrue(completedContainerDeletions.tryAcquire(1, TimeUnit.SECONDS));
        assertNull(zk.exists("/foo", false), "Container should have been deleted");
    }

    @Test
    public void testMultiWithContainerSimple() throws KeeperException, InterruptedException {
        Op createContainer = Op.create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        zk.multi(Collections.singletonList(createContainer));
        DataTree dataTree = serverFactory.getZooKeeperServer().getZKDatabase().getDataTree();
        RestartFramework.at("after_multi_create_container").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(dataTree.getContainers().size(), 1);
    }

    @Test
    public void testMultiWithContainer() throws KeeperException, InterruptedException {
        RestartFramework.at("after_multi_create_container_and_child").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_multi_create_and_delete").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Op createContainer = Op.create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        RestartFramework.at("after_delete_bar").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Op createChild = Op.create("/foo/bar", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zk.multi(Arrays.asList(createContainer, createChild));
        DataTree dataTree = serverFactory.getZooKeeperServer().getZKDatabase().getDataTree();
        assertEquals(dataTree.getContainers().size(), 1);
        // should cause "/foo" to get deleted when checkContainers() is called
        zk.delete("/foo/bar", -1);
        ContainerManager containerManager = new ContainerManager(serverFactory.getZooKeeperServer().getZKDatabase(), serverFactory.getZooKeeperServer().firstProcessor, 1, 100);
        containerManager.checkContainers();
        assertTrue(completedContainerDeletions.tryAcquire(1, TimeUnit.SECONDS));
        assertNull(zk.exists("/foo", false), "Container should have been deleted");
        createContainer = Op.create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        createChild = Op.create("/foo/bar", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        Op deleteChild = Op.delete("/foo/bar", -1);
        zk.multi(Arrays.asList(createContainer, createChild, deleteChild));
        RestartFramework.at("after_first_check_containers").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_second_check_containers").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        containerManager.checkContainers();
        assertTrue(completedContainerDeletions.tryAcquire(1, TimeUnit.SECONDS));
        assertNull(zk.exists("/foo", false), "Container should have been deleted");
    }

    @Test
    public void testSimpleDeletionAsync() throws KeeperException, InterruptedException {
        RestartFramework.at("after_create_bar").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        final CountDownLatch latch = new CountDownLatch(1);
        AsyncCallback.Create2Callback cb = (rc, path, ctx, name, stat) -> {
            assertEquals(ctx, "context");
            latch.countDown();
        };
        zk.create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER, cb, "context");
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        zk.create("/foo/bar", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        // should cause "/foo" to get deleted when checkContainers() is called
        zk.delete("/foo/bar", -1);
        RestartFramework.at("after_check_containers_async").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_delete_bar_async").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ContainerManager containerManager = new ContainerManager(serverFactory.getZooKeeperServer().getZKDatabase(), serverFactory.getZooKeeperServer().firstProcessor, 1, 100);
        containerManager.checkContainers();
        RestartFramework.at("after_async_create_container").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertTrue(completedContainerDeletions.tryAcquire(1, TimeUnit.SECONDS));
        assertNull(zk.exists("/foo", false), "Container should have been deleted");
    }

    @Test
    public void testCascadingDeletion() throws KeeperException, InterruptedException {
        zk.create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        zk.create("/foo/bar", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        zk.create("/foo/bar/one", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        // should cause "/foo/bar" and "/foo" to get deleted when checkContainers() is called
        zk.delete("/foo/bar/one", -1);
        RestartFramework.at("after_delete_one").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ContainerManager containerManager = new ContainerManager(serverFactory.getZooKeeperServer().getZKDatabase(), serverFactory.getZooKeeperServer().firstProcessor, 1, 100);
        containerManager.checkContainers();
        assertTrue(completedContainerDeletions.tryAcquire(1, TimeUnit.SECONDS));
        containerManager.checkContainers();
        assertTrue(completedContainerDeletions.tryAcquire(1, TimeUnit.SECONDS));
        RestartFramework.at("after_second_cascading_check").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertNull(zk.exists("/foo/bar", false), "Container should have been deleted");
        RestartFramework.at("after_create_nested_containers").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_first_cascading_check").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertNull(zk.exists("/foo", false), "Container should have been deleted");
    }

    @Test
    public void testFalseEmpty() throws KeeperException, InterruptedException {
        zk.create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        zk.create("/foo/bar", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        RestartFramework.at("after_create_non_empty_container").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_check_false_empty").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ContainerManager containerManager = new ContainerManager(serverFactory.getZooKeeperServer().getZKDatabase(), serverFactory.getZooKeeperServer().firstProcessor, 1, 100) {

            @Override
            protected Collection<String> getCandidates() {
                return Collections.singletonList("/foo");
            }
        };
        containerManager.checkContainers();
        assertTrue(completedContainerDeletions.tryAcquire(1, TimeUnit.SECONDS));
        assertNotNull(zk.exists("/foo", false), "Container should have not been deleted");
    }

    @Test
    public void testMaxPerMinute() throws InterruptedException {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        RequestProcessor processor = new RequestProcessor() {

            @Override
            public void processRequest(Request request) {
                try {
                    queue.add(request.readRequestRecord(DeleteContainerRequest::new).getPath());
                } catch (IOException e) {
                    fail(e);
                }
            }

            @Override
            public void shutdown() {
            }
        };
        final ContainerManager containerManager = new ContainerManager(serverFactory.getZooKeeperServer().getZKDatabase(), processor, 1, 2) {

            @Override
            protected long getMinIntervalMs() {
                return 1000;
            }

            @Override
            protected Collection<String> getCandidates() {
                return Arrays.asList("/one", "/two", "/three", "/four");
            }
        };
        Executors.newSingleThreadExecutor().submit(() -> {
            containerManager.checkContainers();
            return null;
        });
        assertEquals("/one", queue.poll(5, TimeUnit.SECONDS));
        assertEquals("/two", queue.poll(5, TimeUnit.SECONDS));
        assertEquals(0, queue.size());
        Thread.sleep(500);
        assertEquals(0, queue.size());
        assertEquals("/three", queue.poll(5, TimeUnit.SECONDS));
        RestartFramework.at("after_start_check_containers").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals("/four", queue.poll(5, TimeUnit.SECONDS));
    }

    @Test
    public void testMaxNeverUsedInterval() throws KeeperException, InterruptedException {
        RestartFramework.at("after_first_check_elapsed_zero").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        zk.create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        AtomicLong elapsed = new AtomicLong(0);
        AtomicInteger deletesQty = new AtomicInteger(0);
        ContainerManager containerManager = new ContainerManager(serverFactory.getZooKeeperServer().getZKDatabase(), serverFactory.getZooKeeperServer().firstProcessor, 1, 100, 1000) {

            @Override
            protected void postDeleteRequest(Request request) throws RequestProcessor.RequestProcessorException {
                deletesQty.incrementAndGet();
                super.postDeleteRequest(request);
            }

            @Override
            protected long getElapsed(DataNode node) {
                return elapsed.get();
            }
        };
        // elapsed time will appear to be 0 - container will not get deleted
        containerManager.checkContainers();
        assertEquals(deletesQty.get(), 0);
        assertNotNull(zk.exists("/foo", false), "Container should not have been deleted");
        RestartFramework.at("after_second_check_elapsed_10000").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_create_for_max_interval_test").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        elapsed.set(10000);
        // elapsed time will appear to be 10000 - container should get deleted
        containerManager.checkContainers();
        assertTrue(completedContainerDeletions.tryAcquire(1, TimeUnit.SECONDS));
        assertNull(zk.exists("/foo", false), "Container should have been deleted");
    }

    @Test
    public void testZeroMaxNeverUsedInterval() throws KeeperException, InterruptedException {
        zk.create("/foo", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        RestartFramework.at("after_create_for_zero_interval_test").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_check_zero_interval").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        AtomicInteger deletesQty = new AtomicInteger(0);
        ContainerManager containerManager = new ContainerManager(serverFactory.getZooKeeperServer().getZKDatabase(), serverFactory.getZooKeeperServer().firstProcessor, 1, 100, 0) {

            @Override
            protected void postDeleteRequest(Request request) throws RequestProcessor.RequestProcessorException {
                deletesQty.incrementAndGet();
                super.postDeleteRequest(request);
            }

            @Override
            protected long getElapsed(DataNode node) {
                // some number greater than 0
                return 10000;
            }
        };
        // elapsed time will appear to be 0 - container will not get deleted
        containerManager.checkContainers();
        assertEquals(deletesQty.get(), 0);
        assertNotNull(zk.exists("/foo", false), "Container should not have been deleted");
    }

    private void createNoStatVerifyResult(String newName) throws KeeperException, InterruptedException {
        assertNull(zk.exists(newName, false), "Node existed before created");
        zk.create(newName, newName.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER);
        assertNotNull(zk.exists(newName, false), "Node was not created as expected");
    }

    private Stat createWithStatVerifyResult(String newName) throws KeeperException, InterruptedException {
        assertNull(zk.exists(newName, false), "Node existed before created");
        Stat stat = new Stat();
        zk.create(newName, newName.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.CONTAINER, stat);
        validateCreateStat(stat, newName);
        Stat referenceStat = zk.exists(newName, false);
        assertNotNull(referenceStat, "Node was not created as expected");
        assertEquals(referenceStat, stat);
        return stat;
    }

    private void validateCreateStat(Stat stat, String name) {
        assertEquals(stat.getCzxid(), stat.getMzxid());
        assertEquals(stat.getCzxid(), stat.getPzxid());
        assertEquals(stat.getCtime(), stat.getMtime());
        assertEquals(0, stat.getCversion());
        assertEquals(0, stat.getVersion());
        assertEquals(0, stat.getAversion());
        assertEquals(0, stat.getEphemeralOwner());
        assertEquals(name.length(), stat.getDataLength());
        assertEquals(0, stat.getNumChildren());
    }
}
