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

import static org.junit.jupiter.api.Assertions.fail;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class ACLRootTest_RestartInjected_Randomized_42 extends ClientBase {

    @Test
    public void testRootAcl() throws Exception {
        ZooKeeper zk = createClient();
        try {
            // set auth using digest
            zk.addAuthInfo("digest", "pat:test".getBytes());
            zk.setACL("/", Ids.CREATOR_ALL_ACL, -1);
            RestartFramework.at("after_set_root_acl").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            zk.getData("/", false, null);
            RestartFramework.at("after_get_root_with_acl").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            zk.close();
            // verify no access
            zk = createClient();
            try {
                zk.getData("/", false, null);
                fail("validate auth");
            } catch (KeeperException.NoAuthException e) {
                // expected
            }
            try {
                zk.create("/apps", null, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
                fail("validate auth");
            } catch (KeeperException.InvalidACLException e) {
                // expected
            }
            zk.addAuthInfo("digest", "world:anyone".getBytes());
            try {
                zk.create("/apps", null, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
                fail("validate auth");
            } catch (KeeperException.NoAuthException e) {
                // expected
            }
            RestartFramework.at("after_verify_no_auth").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            zk.close();
            // verify access using original auth
            zk = createClient();
            zk.addAuthInfo("digest", "pat:test".getBytes());
            zk.getData("/", false, null);
            RestartFramework.at("after_auth_access_verified").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            zk.create("/apps", null, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
            zk.delete("/apps", -1);
            RestartFramework.at("after_create_delete_apps").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            // reset acl (back to open) and verify accessible again
            zk.setACL("/", Ids.OPEN_ACL_UNSAFE, -1);
            RestartFramework.at("after_reset_acl_to_open").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            zk.close();
            zk = createClient();
            zk.getData("/", false, null);
            zk.create("/apps", null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            try {
                zk.create("/apps", null, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
                fail("validate auth");
            } catch (KeeperException.InvalidACLException e) {
                // expected
            }
            zk.delete("/apps", -1);
            RestartFramework.at("after_verify_open_acl").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            zk.addAuthInfo("digest", "world:anyone".getBytes());
            zk.create("/apps", null, Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
            RestartFramework.at("after_create_with_world_auth").on(this).restart("server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            zk.close();
            zk = createClient();
            zk.delete("/apps", -1);
        } finally {
            zk.close();
        }
    }
}
