# Prompt: Transform ZooKeeper Test with Restart Position Injection

## Objective

Transform an existing ZooKeeper test to inject restart positions for distributed system restart testing. The transformation will generate:
1. A new test file with `_RestartInjected` suffix
2. A restart configuration file for the Maven plugin

## Input

- **Test File Path**: Path to the original test file (e.g., `/path/to/TestZooKeeperOperations.java`)
- **Test Class**: Fully-qualified class name (e.g., `org.apache.zookeeper.test.TestZooKeeperOperations`)

## Output

1. **Generated Test File**: `{OriginalFileName}_RestartInjected.java` at the same directory as the input file
2. **Restart Configuration**: `restart-config.json` in the `restarts-config/` directory under the same module directory as the test file, if not exist create it.

## ZooKeeper Cluster Types

ZooKeeper has three cluster base types, each with its own adapter:

### 1. ClientBase - Standalone Server
- **Cluster Type**: Single standalone ZooKeeper server
- **Base Class**: `org.apache.zookeeper.test.ClientBase`
- **Cluster Field**: `serverFactory` (ServerCnxnFactory)
- **Adapter**: `ClientBaseAdapter`
- **Node Roles**: `server`, `all`
- **Use Case**: Tests for single-server operations, basic client-server interactions

### 2. QuorumBase - Quorum Cluster (5 servers)
- **Cluster Type**: ZooKeeper quorum with 5 servers (s1-s5)
- **Base Class**: `org.apache.zookeeper.test.QuorumBase`
- **Cluster Fields**: `s1`, `s2`, `s3`, `s4`, `s5` (QuorumPeer instances)
- **Adapter**: `QuorumBaseAdapter`
- **Node Roles**: `leader`, `follower`, `observer`, `server` (by index 0-4), `all`
- **Use Case**: Tests for leader election, consensus, replication, quorum operations

### 3. QuorumPeerTestBase.MainThread - Individual Quorum Peer
- **Cluster Type**: Single quorum peer managed by MainThread
- **Base Class**: `org.apache.zookeeper.server.quorum.QuorumPeerTestBase`
- **Cluster Field**: `MainThread` instance (often in `Servers.mt[]` array)
- **Adapter**: `MainThreadAdapter`
- **Node Roles**: `server`, `all`
- **Use Case**: Tests for specific peer configurations, dynamic reconfiguration, peer-level operations

## Transformation Instructions

### Step 1: Identify Cluster Type

Read the test file and determine which cluster type it uses:

1. **Check the parent class**:
   - `extends ClientBase` → Standalone server
   - `extends QuorumBase` → Quorum cluster (5 servers)
   - `extends QuorumPeerTestBase` → Individual quorum peer(s)

2. **Check cluster initialization**:
   - Look for `serverFactory` → ClientBase
   - Look for `s1, s2, s3, s4, s5` or `getPeerList()` → QuorumBase
   - Look for `MainThread` or `Servers.mt[]` → QuorumPeerTestBase

3. **Identify the cluster variable name**:
   - ClientBase: Usually `this` (inherited), or explicit `serverFactory`
   - QuorumBase: Usually `this` (inherited), can access `s1-s5` directly
   - QuorumPeerTestBase: Usually `servers.mt[i]` or individual `MainThread` instance

### Step 2: Identify Critical Operations

For each test method, identify operations that involve state transitions:

**ZooKeeper Client Operations**:
- Data operations: `create()`, `setData()`, `delete()`, `exists()`, `getData()`, `getChildren()`
- Watch operations: Setting watches, receiving notifications
- Transaction operations: `multi()` with multiple operations
- Session operations: Session creation, expiration, reconnection
- ACL operations: `setACL()`, `getACL()`
- Ephemeral operations: Creating ephemeral nodes, session expiration

**ZooKeeper Server Operations**:
- Leader election: Quorum formation, leader selection
- Data synchronization: SYNC, snapshot, transaction log replay
- Client request processing: Proposals, commits, acknowledgments
- Configuration changes: Dynamic reconfiguration
- Session tracking: Session expiration, global sessions vs local sessions

**Cluster Operations**:
- Server startup/shutdown
- Leader/follower state transitions
- Quorum changes (observers joining/leaving)
- Data replication and consistency

### Step 3: Identify Restart Points

For each test method, identify potential restart points based on these criteria:

**Good Restart Points** (inject here):
- After creating znodes (data nodes)
- After data updates (`setData()`)
- Before/after transaction commits
- During multi-operation sequences
- After leader election completes
- During session operations (create, reconnect)
- After watch triggers
- During data synchronization
- Before/after ephemeral node creation
- After ACL changes
- During quorum reconfigurations

**Poor Restart Points** (avoid):
- Before cluster setup (no cluster exists yet)
- After cluster teardown (cluster already destroyed)
- During trivial read operations with no state changes
- Operations that are too fast to test meaningful state

**Naming Convention for Restart Positions**:
- Use descriptive, lowercase names with underscores
- Pattern: `{operation}_{context}`
- Examples:
  - `after_create_znode`
  - `after_set_data`
  - `before_delete`
  - `after_transaction`
  - `after_leader_election`
  - `during_sync`
  - `after_session_create`
  - `before_ephemeral_delete`
  - `after_watch_trigger`
  - `after_multi_op`
  - `during_reconfig`
  - `after_snapshot`

### Step 4: Generate the Restart-Injected Test File

Create a new test file with the following transformations:

#### 4.1 Package and Imports

```java
// Keep original package declaration
package org.apache.zookeeper.test;

// Add these imports at the top (if not already present)
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

// Keep all original imports
```

#### 4.2 Class Declaration

```java
// Original class name: TestZooKeeperOps
// New class name: TestZooKeeperOps_RestartInjected

// IMPORTANT: Preserve the same inheritance structure!
// If original: public class TestZooKeeperOps extends ClientBase
// Then use:    public class TestZooKeeperOps_RestartInjected extends ClientBase
public class TestZooKeeperOps_RestartInjected extends ClientBase {
    // Keep all original fields and variables
}
```

**CRITICAL**: Always preserve the base class inheritance. The cluster object is often inherited from the parent class.

#### 4.3 Cluster Setup and Teardown

Keep the `@BeforeEach`/`@Before` and `@AfterEach`/`@After` methods unchanged:

```java
@BeforeEach
@Override
public void setUp() throws Exception {
    super.setUp(); // Keep original setup code
}

@AfterEach
@Override
public void tearDown() throws Exception {
    super.tearDown(); // Keep original teardown code
}
```

**Note**: Setup methods may be inherited from parent class. In that case, don't add setup methods to the generated test.

#### 4.4 Transform Test Methods

For each `@Test` method, apply the following transformations based on cluster type:

---

**Example 1: ClientBase (Standalone Server)**

**Original Test Method**:
```java
@Test
public void testCreateAndRead() throws Exception {
    ZooKeeper zk = createClient();

    String path = "/test";
    zk.create(path, "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    byte[] data = zk.getData(path, false, null);
    assertEquals("data", new String(data));

    zk.close();
}
```

**Transformed Test Method**:
```java
@Test
public void testCreateAndRead() throws Exception {
    ZooKeeper zk = createClient();

    String path = "/test";
    zk.create(path, "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    // RESTART POINT 1: after_create_znode
    RestartFramework.at("after_create_znode")
        .on(this)  // ClientBase instance
        .restart("server")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    byte[] data = zk.getData(path, false, null);
    assertEquals("data", new String(data));

    // RESTART POINT 2: after_get_data
    RestartFramework.at("after_get_data")
        .on(this)
        .restart("server")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    zk.close();
}
```

---

**Example 2: QuorumBase (Quorum Cluster)**

**Original Test Method**:
```java
@Test
public void testLeaderElection() throws Exception {
    // Wait for leader election
    waitForOne(zk[0], States.CONNECTED);

    QuorumPeer leader = getLeaderQuorumPeer();
    assertNotNull(leader);

    // Create data on leader
    zk[0].create("/test", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    // Verify on followers
    for (int i = 1; i < 5; i++) {
        assertNotNull(zk[i].exists("/test", false));
    }
}
```

**Transformed Test Method**:
```java
@Test
public void testLeaderElection() throws Exception {
    // Wait for leader election
    waitForOne(zk[0], States.CONNECTED);

    QuorumPeer leader = getLeaderQuorumPeer();
    assertNotNull(leader);

    // RESTART POINT 1: after_leader_election
    RestartFramework.at("after_leader_election")
        .on(this)  // QuorumBase instance
        .restart("follower")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Create data on leader
    zk[0].create("/test", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    // RESTART POINT 2: after_create_znode
    RestartFramework.at("after_create_znode")
        .on(this)
        .restart("leader")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Verify on followers
    for (int i = 1; i < 5; i++) {
        assertNotNull(zk[i].exists("/test", false));
    }
}
```

---

**Example 3: QuorumPeerTestBase.MainThread**

**Original Test Method**:
```java
@Test
public void testServerRecovery() throws Exception {
    Servers servers = LaunchServers(3);

    ZooKeeper zk = servers.zk[0];
    zk.create("/test", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    // Verify data persists
    byte[] data = zk.getData("/test", false, null);
    assertEquals("data", new String(data));
}
```

**Transformed Test Method**:
```java
@Test
public void testServerRecovery() throws Exception {
    Servers servers = LaunchServers(3);

    ZooKeeper zk = servers.zk[0];
    zk.create("/test", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    // RESTART POINT 1: after_create_znode
    RestartFramework.at("after_create_znode")
        .on(servers.mt[0])  // MainThread instance
        .restart("server")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Verify data persists
    byte[] data = zk.getData("/test", false, null);
    assertEquals("data", new String(data));
}
```

---

**Injection Pattern**:

1. **After Data Operations**:
   ```java
   zk.create("/test", data, acl, mode);

   // Inject restart point
   RestartFramework.at("after_create_znode")
       .on(cluster)  // cluster variable (this, servers.mt[i], etc.)
       .restart("server")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

2. **Before Critical Operations**:
   ```java
   // Inject restart point before delete
   RestartFramework.at("before_delete")
       .on(cluster)
       .restart("leader")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();

   zk.delete("/test", -1);
   ```

3. **During Multi-Operations**:
   ```java
   Op create1 = Op.create("/test1", data1, acl, mode);
   Op create2 = Op.create("/test2", data2, acl, mode);

   // Inject restart point before transaction
   RestartFramework.at("before_multi_op")
       .on(cluster)
       .restart("leader")
       .execute();

   zk.multi(Arrays.asList(create1, create2));

   RestartFramework.at("after_multi_op")
       .on(cluster)
       .restart("follower")
       .execute();
   ```

#### 4.5 Node Role Selection by Cluster Type

**ClientBase (Standalone)**:
- **Node Roles**: `"server"`, `"all"`
- **Node Index**: Always `0` (single server)
- **Use**: `.restart("server").withIndex(0)`

**QuorumBase (Quorum)**:
- **Node Roles**: `"leader"`, `"follower"`, `"observer"`, `"server"` (by index), `"all"`
- **Node Index**:
  - For `"server"`: 0-4 (maps to s1-s5)
  - For `"leader"`: 0 (only one leader)
  - For `"follower"`: 0, 1, 2... (multiple followers)
  - For `"observer"`: 0, 1... (if observers configured)
- **Examples**:
  - Restart leader: `.restart("leader").withIndex(0)`
  - Restart first follower: `.restart("follower").withIndex(0)`
  - Restart server 3 (s3): `.restart("server").withIndex(2)`
  - Restart all: `.restart("all")`

**QuorumPeerTestBase.MainThread**:
- **Node Roles**: `"server"`, `"all"`
- **Node Index**: Always `0` (single MainThread instance)
- **Cluster Variable**: Use `servers.mt[i]` for specific server
- **Use**: `.on(servers.mt[i]).restart("server").withIndex(0)`

#### 4.6 Default Restart Configuration

Use these defaults for all injected restart points:
- **Restart Mode**: `RestartMode.GRACEFUL` (default, safest)
- **Node Index**: `0` (first node of the specified role)

### Step 5: Generate Restart Configuration File

Create `restarts-config/restart-config.json` with the following structure:

```json
{
  "tests": [
    {
      "testClass": "org.apache.zookeeper.test.TestZooKeeperOps_RestartInjected",
      "testMethod": "testCreateAndRead",
      "restartPoints": [
        {
          "position": "after_create_znode",
          "targets": ["server"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "after_get_data",
          "targets": ["server"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```

#### Configuration Generation Rules

For each test method in the transformed test:

1. **Create a test specification** with:
   - `testClass`: The fully-qualified name of the generated test class
   - `testMethod`: The test method name (same as original)
   - `restartPoints`: Array of restart point configurations

2. **For each restart point** injected in the test method:
   - `position`: The position identifier used in `.at("...")`
   - `targets`: Array of node roles to test
   - `modes`: Array of restart modes to test

#### Target Selection Guidelines

**ClientBase (Standalone)**:
- **All operations**: `["server"]`

**QuorumBase (Quorum)**:
- **Data operations** (create, setData, delete): `["leader", "follower"]`
- **Leader-specific operations** (proposals): `["leader"]`
- **Replication testing**: `["follower"]`
- **Observer testing**: `["observer"]` (if configured)
- **Consensus testing**: `["leader", "follower"]`
- **Full cluster**: `["server"]` with different indices or `["all"]`

**QuorumPeerTestBase.MainThread**:
- **Single peer operations**: `["server"]`
- **Multiple peers**: Use different `servers.mt[i]` instances

#### Mode Selection Guidelines

- **`["GRACEFUL"]`**: Basic test, verify restart works
  - Use for: Initial testing, simple state transitions

- **`["GRACEFUL", "CRASH"]`**: Standard test, verify crash recovery
  - Use for: Data operations, leader election, session handling

- **`["GRACEFUL", "CRASH", "DELAYED_CRASH"]`**: Advanced test, verify timing-sensitive operations
  - Use for: Transaction commits, multi-ops, leader election, data synchronization, session expiration

### Step 6: File Placement

1. **Generated Test File**:
   - Location: Same directory as original test file
   - Name: `{OriginalClassName}_RestartInjected.java`
   - Example: `ClientTest.java` → `ClientTest_RestartInjected.java`

2. **Restart Configuration**:
   - Location: `restarts-config/` directory under the same module directory as the test file
   - Name: `restart-config.json`
   - If file exists, append to the `tests` array (avoid duplicates)
   - If file doesn't exist, create new file

## ZooKeeper-Specific Patterns

### Pattern 1: Data Operations Testing (ClientBase)

```java
@Test
public void testDataPersistence() throws Exception {
    ZooKeeper zk = createClient();

    String path = "/persistent";
    zk.create(path, "v1".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    RestartFramework.at("after_create_znode")
        .on(this)
        .restart("server")
        .execute();

    // Update data
    zk.setData(path, "v2".getBytes(), -1);

    RestartFramework.at("after_set_data")
        .on(this)
        .restart("server")
        .execute();

    // Verify persistence
    byte[] data = zk.getData(path, false, null);
    assertEquals("v2", new String(data));

    zk.close();
}
```

### Pattern 2: Leader Election Testing (QuorumBase)

```java
@Test
public void testLeaderElection() throws Exception {
    // Wait for initial leader
    QuorumPeer leader = getLeaderQuorumPeer();
    assertNotNull(leader);

    RestartFramework.at("after_initial_election")
        .on(this)
        .restart("follower")
        .withIndex(0)
        .execute();

    // Restart leader to trigger re-election
    RestartFramework.at("before_leader_restart")
        .on(this)
        .restart("leader")
        .execute();

    // Wait for new leader
    waitForNewLeader();
    QuorumPeer newLeader = getLeaderQuorumPeer();
    assertNotNull(newLeader);
}
```

### Pattern 3: Transaction Testing

```java
@Test
public void testMultiOperation() throws Exception {
    ZooKeeper zk = createClient();

    Op create1 = Op.create("/t1", "d1".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    Op create2 = Op.create("/t2", "d2".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    RestartFramework.at("before_multi_op")
        .on(this)
        .restart("leader")
        .execute();

    List<OpResult> results = zk.multi(Arrays.asList(create1, create2));

    RestartFramework.at("after_multi_op")
        .on(this)
        .restart("follower")
        .execute();

    // Verify both nodes exist
    assertNotNull(zk.exists("/t1", false));
    assertNotNull(zk.exists("/t2", false));

    zk.close();
}
```

### Pattern 4: Session Testing

```java
@Test
public void testSessionRecovery() throws Exception {
    ZooKeeper zk = createClient();
    long sessionId = zk.getSessionId();

    // Create ephemeral node
    zk.create("/ephemeral", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

    RestartFramework.at("after_create_ephemeral")
        .on(this)
        .restart("server")
        .execute();

    // Reconnect and verify session
    ZooKeeper zk2 = createClient();
    long newSessionId = zk2.getSessionId();

    // Ephemeral should still exist if session recovered
    assertNotNull(zk2.exists("/ephemeral", false));

    zk.close();
    zk2.close();
}
```

### Pattern 5: Watch Testing

```java
@Test
public void testWatchTriggering() throws Exception {
    ZooKeeper zk = createClient();
    CountdownWatcher watcher = new CountdownWatcher();

    zk.create("/watched", "v1".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    // Set watch
    zk.getData("/watched", watcher, null);

    RestartFramework.at("after_set_watch")
        .on(this)
        .restart("server")
        .execute();

    // Trigger watch
    zk.setData("/watched", "v2".getBytes(), -1);

    // Wait for watch notification
    watcher.waitForConnected(CONNECTION_TIMEOUT);

    zk.close();
}
```

### Pattern 6: Quorum Testing (QuorumBase)

```java
@Test
public void testQuorumConsistency() throws Exception {
    ZooKeeper zk = createClient(getPeersMatching(ServerState.FOLLOWING));

    // Write to follower
    zk.create("/test", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    RestartFramework.at("after_write_to_follower")
        .on(this)
        .restart("follower")
        .withIndex(0)
        .execute();

    // Verify on all servers
    for (int i = 0; i < 5; i++) {
        ZooKeeper zkServer = createClient("127.0.0.1:" + getClientPortByIndex(i));
        assertNotNull(zkServer.exists("/test", false));
        zkServer.close();
    }

    zk.close();
}
```

### Pattern 7: Dynamic Reconfiguration (QuorumPeerTestBase)

```java
@Test
public void testDynamicReconfig() throws Exception {
    Servers servers = LaunchServers(3);
    ZooKeeper zk = servers.zk[0];

    // Get current config
    byte[] config = zk.getConfig(false, null);

    RestartFramework.at("before_reconfig")
        .on(servers.mt[0])
        .restart("server")
        .execute();

    // Perform reconfiguration
    zk.reconfig(null, null, newMembers, -1, null);

    RestartFramework.at("after_reconfig")
        .on(servers.mt[1])
        .restart("server")
        .execute();

    // Verify new config
    byte[] newConfig = zk.getConfig(false, null);
    assertNotEquals(new String(config), new String(newConfig));

    zk.close();
}
```

## Common ZooKeeper Operations and Suggested Restart Points

| ZooKeeper Operation | Suggested Restart Point Name | Node Role (ClientBase) | Node Role (QuorumBase) | Timing |
|--------------------|------------------------------|------------------------|------------------------|--------|
| `create()` | `after_create_znode` | `server` | `leader`, `follower` | After |
| `setData()` | `after_set_data` | `server` | `leader`, `follower` | After |
| `delete()` | `before_delete`, `after_delete` | `server` | `leader` | Before/After |
| `exists()` | `after_exists_check` | `server` | `follower` | After |
| `getData()` | `after_get_data` | `server` | `follower` | After |
| `getChildren()` | `after_get_children` | `server` | `follower` | After |
| `multi()` | `before_multi_op`, `after_multi_op` | `server` | `leader` | Before/After |
| `sync()` | `after_sync` | `server` | `leader`, `follower` | After |
| Leader election | `after_leader_election` | N/A | `leader`, `follower` | After |
| Session create | `after_session_create` | `server` | `leader` | After |
| Session expire | `after_session_expire` | `server` | `leader` | After |
| Watch trigger | `after_watch_trigger` | `server` | `leader`, `follower` | After |
| Ephemeral create | `after_create_ephemeral` | `server` | `leader` | After |
| ACL operations | `after_set_acl` | `server` | `leader` | After |
| Snapshot | `after_snapshot` | `server` | `leader` | After |
| `reconfig()` | `before_reconfig`, `after_reconfig` | N/A | `leader` | Before/After |

## Validation Checklist

After transformation, verify:

- [ ] Generated test file compiles without errors
- [ ] All original test logic is preserved
- [ ] Class inheritance is maintained (extends same base class)
- [ ] Correct cluster variable is used (this, servers.mt[i], etc.)
- [ ] Restart points are placed at meaningful ZooKeeper operations
- [ ] Restart position names are descriptive and ZooKeeper-specific
- [ ] Node roles are correctly chosen based on cluster type
- [ ] Configuration file has correct fully-qualified class names
- [ ] Configuration file includes all restart points from the test
- [ ] Target arrays match the cluster type and operation
- [ ] Mode arrays are appropriate for timing sensitivity
- [ ] Files are placed in correct locations
- [ ] Original test file is not modified (only new files created)

## Advanced Scenarios

### Multiple Test Methods

If the original test has multiple `@Test` methods:

1. Transform each method independently
2. Inject restart points in each method
3. Create a separate test specification for each method in the configuration

Example configuration:
```json
{
  "tests": [
    {
      "testClass": "org.apache.zookeeper.test.TestZK_RestartInjected",
      "testMethod": "testCreate",
      "restartPoints": [...]
    },
    {
      "testClass": "org.apache.zookeeper.test.TestZK_RestartInjected",
      "testMethod": "testDelete",
      "restartPoints": [...]
    }
  ]
}
```

### Inherited Cluster from Base Class

**CRITICAL**: Most ZooKeeper tests inherit cluster setup from parent classes:

- **ClientBase**: Tests inherit `serverFactory`, `hostPort`, `tmpDir` from ClientBase
- **QuorumBase**: Tests inherit `s1-s5`, `hostPort` from QuorumBase
- **QuorumPeerTestBase**: Tests create `Servers` object with `mt[]` array

**Transformation Strategy**:
1. **Preserve the inheritance**: `TestOps_RestartInjected extends ClientBase`
2. **Do NOT duplicate setup/teardown** methods unless they contain test-specific logic
3. **Use the inherited cluster reference**:
   - ClientBase: `.on(this)`
   - QuorumBase: `.on(this)`
   - QuorumPeerTestBase: `.on(servers.mt[i])`

### Helper Methods

If the test has helper methods:

1. **Do not inject restart points in helper methods**
2. Only inject in `@Test` annotated methods
3. Keep helper methods unchanged

### Tests Without Obvious Restart Points

If a test has no clear state transitions:

1. Inject restart points within the range of (a) after cluster setup and (b) before cluster teardown
2. Evenly distribute restart points to cover the test execution
3. You MUST use percentage-based positions (e.g., `at_25_percent`, `at_50_percent`) to at least cover 4 points during the test execution
4. Find ZooKeeper operations to place restart points around

**IMPORTANT**: You are NOT allowed to skip any test transformation due to lack of restart points. Always inject at least one restart point per test method.

## Notes

- **Non-invasive**: Original test file is never modified
- **Incremental**: Can transform tests one at a time
- **Compatible**: Generated tests can run both with and without restart injection
- **Configurable**: Configuration file allows easy adjustment of test matrix
- **Three cluster types**: ClientBase (standalone), QuorumBase (5-server quorum), QuorumPeerTestBase.MainThread (individual peer)
- **Role-specific**: Node roles vary by cluster type (server for standalone, leader/follower/observer for quorum)
- **Inheritance-aware**: Handles tests that inherit cluster setup from parent classes

## Dependencies

Ensure the following dependencies are included in the test's module to use the Restart Testing Framework:

```xml
<dependencies>
    <!-- Existing dependencies... -->

    <!-- Restart Testing Framework - Core -->
    <dependency>
        <groupId>org.restarttest</groupId>
        <artifactId>restart-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>

    <!-- Restart Testing Framework - ZooKeeper Adapter -->
    <dependency>
        <groupId>org.restarttest</groupId>
        <artifactId>restart-zookeeper-adapter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Cluster Type Quick Reference

| Cluster Type | Base Class | Cluster Variable | Node Roles | Adapter |
|--------------|------------|------------------|------------|---------|
| **Standalone** | `ClientBase` | `this` (or `serverFactory`) | `server`, `all` | `ClientBaseAdapter` |
| **Quorum (5 servers)** | `QuorumBase` | `this` (or `s1-s5`) | `leader`, `follower`, `observer`, `server` (0-4), `all` | `QuorumBaseAdapter` |
| **Individual Peer** | `QuorumPeerTestBase` | `servers.mt[i]` | `server`, `all` | `MainThreadAdapter` |

## Example Transformations

### Example 1: ClientBase Test

**Input**: `ClientTest.java`
```java
package org.apache.zookeeper.test;

import org.apache.zookeeper.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ClientTest extends ClientBase {

    @Test
    public void testBasicOperations() throws Exception {
        ZooKeeper zk = createClient();

        zk.create("/test", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        byte[] data = zk.getData("/test", false, null);
        assertEquals("data", new String(data));

        zk.close();
    }
}
```

**Output**: `ClientTest_RestartInjected.java`
```java
package org.apache.zookeeper.test;

import org.apache.zookeeper.*;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.junit.jupiter.api.Assertions.*;

public class ClientTest_RestartInjected extends ClientBase {

    @Test
    public void testBasicOperations() throws Exception {
        ZooKeeper zk = createClient();

        zk.create("/test", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        // RESTART POINT 1: after_create_znode
        RestartFramework.at("after_create_znode")
            .on(this)
            .restart("server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        byte[] data = zk.getData("/test", false, null);
        assertEquals("data", new String(data));

        zk.close();
    }
}
```

**Configuration**: `restarts-config/restart-config.json`
```json
{
  "tests": [
    {
      "testClass": "org.apache.zookeeper.test.ClientTest_RestartInjected",
      "testMethod": "testBasicOperations",
      "restartPoints": [
        {
          "position": "after_create_znode",
          "targets": ["server"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```

### Example 2: QuorumBase Test

**Input**: `QuorumTest.java`
```java
package org.apache.zookeeper.test;

import org.apache.zookeeper.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QuorumTest extends QuorumBase {

    @Test
    public void testQuorumWrite() throws Exception {
        ZooKeeper zk = createClient();

        zk.create("/test", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        // Verify on all servers
        for (int i = 0; i < 5; i++) {
            assertNotNull(getPeerList().get(i));
        }

        zk.close();
    }
}
```

**Output**: `QuorumTest_RestartInjected.java`
```java
package org.apache.zookeeper.test;

import org.apache.zookeeper.*;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;
import static org.junit.jupiter.api.Assertions.*;

public class QuorumTest_RestartInjected extends QuorumBase {

    @Test
    public void testQuorumWrite() throws Exception {
        ZooKeeper zk = createClient();

        zk.create("/test", "data".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        // RESTART POINT 1: after_create_znode
        RestartFramework.at("after_create_znode")
            .on(this)
            .restart("leader")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // RESTART POINT 2: after_leader_restart
        RestartFramework.at("after_leader_restart")
            .on(this)
            .restart("follower")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        // Verify on all servers
        for (int i = 0; i < 5; i++) {
            assertNotNull(getPeerList().get(i));
        }

        zk.close();
    }
}
```

**Configuration**: `restarts-config/restart-config.json`
```json
{
  "tests": [
    {
      "testClass": "org.apache.zookeeper.test.QuorumTest_RestartInjected",
      "testMethod": "testQuorumWrite",
      "restartPoints": [
        {
          "position": "after_create_znode",
          "targets": ["leader", "follower"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "after_leader_restart",
          "targets": ["follower"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```
