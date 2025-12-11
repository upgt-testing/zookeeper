# ZooKeeper Restart Testing Adapter

This module provides restart testing adapters for Apache ZooKeeper test clusters, enabling systematic restart testing using the [Restart Testing Framework](https://github.com/anthropics/restart-testing-framework).

## Overview

This adapter module supports three types of ZooKeeper test clusters:

1. **ClientBase** - Standalone ZooKeeper server for unit tests
2. **MainThread** - Single QuorumPeer managed by QuorumPeerTestBase.MainThread
3. **QuorumBase** - Full 5-server quorum cluster for distributed tests

## Modified ZooKeeper Test-JAR

This module includes a **modified ZooKeeper 3.9.4 test-jar** (`lib/zookeeper-3.9.4-tests.jar`) with public field access modifications. This allows the module to **build standalone without requiring you to build zookeeper-server first**.

### What Was Modified

The bundled test-jar contains the following changes to enable adapter field access:

**`org.apache.zookeeper.test.ClientBase`** - Changed from `protected` to `public`:
- `hostPort`, `tmpDir`, `maxCnxns`, `serverFactory` fields
- `shutdownServerInstance()` method

**`org.apache.zookeeper.test.QuorumBase`** - Changed from package-private to `public`:
- `s1, s2, s3, s4, s5` (QuorumPeer instances)
- `s1dir - s5dir` (data directories)
- `portClient1 - portClient5` (client ports)

### How Standalone Build Works

The `pom.xml` includes a `maven-install-plugin` execution that automatically installs the bundled modified test-jar to your local Maven repository during the `validate` phase:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-install-plugin</artifactId>
    <executions>
        <execution>
            <id>install-bundled-zookeeper-tests</id>
            <phase>validate</phase>
            <goals><goal>install-file</goal></goals>
        </execution>
    </executions>
</plugin>
```

This means you can build on a fresh machine without building zookeeper-server first.

## Installation

### Building from Source (Standalone)

```bash
# No need to build zookeeper-server first!
cd zookeeper-restart-adapter
mvn clean install -DskipTests
```

Requirements:
- Java 8 (1.8)
- Maven 3.x
- RestartTestingFramework `restart-core` installed in local Maven repo

### Maven Dependency

Add this module as a test dependency in your ZooKeeper project:

```xml
<dependency>
    <groupId>org.restarttest.adapter</groupId>
    <artifactId>zookeeper-restart-adapter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### Updating the Bundled Test-JAR

If you make additional modifications to ZooKeeper test classes:

```bash
# 1. Build zookeeper-server with your modifications
cd zookeeper-server
mvn clean test-compile -DskipTests
mvn jar:test-jar

# 2. Install to local repo
mvn install:install-file \
  -Dfile=target/zookeeper-3.9.4-tests.jar \
  -DgroupId=org.apache.zookeeper \
  -DartifactId=zookeeper \
  -Dversion=3.9.4 \
  -Dclassifier=tests \
  -Dpackaging=jar

# 3. Update bundled JAR
cp ~/.m2/repository/org/apache/zookeeper/zookeeper/3.9.4/zookeeper-3.9.4-tests.jar \
   ../zookeeper-restart-adapter/lib/

# 4. Rebuild adapter
cd ../zookeeper-restart-adapter
mvn clean install
```

## Usage

### ClientBase Adapter (Standalone Server)

```java
import org.apache.zookeeper.test.ClientBase;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

@Test
public void testStandaloneRestart() throws Exception {
    ClientBase base = new ClientBase();
    base.setUp();

    // Create some data
    ZooKeeper zk = base.createClient();
    zk.create("/test", "data".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    // Restart the server
    RestartFramework.at("after_create")
        .on(base)
        .restart("server")
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Verify data persisted
    assertTrue(zk.exists("/test", false) != null);

    base.tearDown();
}
```

**Supported Node Roles:**
- `"server"` - The standalone server
- `"all"` - Same as "server"

### MainThread Adapter (Single Quorum Peer)

```java
import org.apache.zookeeper.server.quorum.QuorumPeerTestBase.MainThread;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

@Test
public void testQuorumPeerRestart() throws Exception {
    MainThread mt = new MainThread(1, "server.1=127.0.0.1:2888:3888:participant;127.0.0.1:2181");
    mt.start();

    // Restart the peer
    RestartFramework.at("mid_test")
        .on(mt)
        .restart("server")
        .withMode(RestartMode.CRASH)
        .execute();

    assertTrue(mt.isQuorumPeerRunning());
    mt.shutdown();
}
```

**Supported Node Roles:**
- `"server"` - The QuorumPeer server
- `"all"` - Same as "server"

### QuorumBase Adapter (Full Cluster)

```java
import org.apache.zookeeper.test.QuorumBase;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

@Test
public void testLeaderRestart() throws Exception {
    QuorumBase qb = new QuorumBase();
    qb.setUp();

    // Create data
    ZooKeeper zk = qb.createClient();
    zk.create("/test", "data".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    // Restart the leader
    RestartFramework.at("after_create")
        .on(qb)
        .restart("leader")
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Verify new leader elected and data persisted
    assertNotNull(qb.getLeaderQuorumPeer());
    assertTrue(zk.exists("/test", false) != null);

    qb.tearDown();
}
```

**Supported Node Roles:**
- `"leader"` / `"master"` - Restart current leader
- `"follower"` / `"worker"` - Restart a follower
- `"observer"` - Restart an observer
- `"server"` - Restart by index (0-4 for s1-s5)
- `"all"` - Restart all 5 servers

**Index Mapping:**
- `withIndex(0)` → s1
- `withIndex(1)` → s2
- `withIndex(2)` → s3
- `withIndex(3)` → s4
- `withIndex(4)` → s5

### Restart Modes

All adapters support three restart modes:

- **GRACEFUL** - Clean shutdown with proper cleanup, then restart
- **CRASH** - Abrupt shutdown simulating a crash, then immediate restart
- **DELAYED_CRASH** - Crash, wait (default 500ms), then restart

### System Properties Activation

Restart points can be activated via system properties:

```bash
mvn test -Dtest=MyTest \
  -Drestart.position=after_create \
  -Drestart.target=leader \
  -Drestart.mode=CRASH
```

## Architecture

### Module Structure

```
zookeeper-restart-adapter/
├── pom.xml
├── README.md
└── src/main/
    ├── java/org/restarttest/adapter/zookeeper/
    │   ├── ClientBaseAdapter.java
    │   ├── ClientBaseStateCapture.java
    │   ├── MainThreadAdapter.java
    │   ├── MainThreadStateCapture.java
    │   ├── QuorumBaseAdapter.java
    │   └── QuorumBaseStateCapture.java
    └── resources/META-INF/services/
        └── org.restarttest.core.ClusterAdapter
```

### Adapters

Each adapter implements `ClusterAdapter<T>` interface:

- **ClientBaseAdapter** - Cluster type: `ClientBase`
- **MainThreadAdapter** - Cluster type: `QuorumPeerTestBase.MainThread`
- **QuorumBaseAdapter** - Cluster type: `QuorumBase`

### State Capture

Each adapter has a corresponding `StateCapture` implementation that:

1. **Captures** cluster state before restart (data, topology, metadata)
2. **Verifies** invariants after restart (data preserved, leader elected, no data loss)

### Health Checks

All adapters use `NoOpHealthCheck` and rely on the framework's built-in `waitActive()` method, which:

- Waits for servers to accept connections
- Verifies cluster is responsive
- Confirms leader election (for quorum clusters)

## Examples

### Test All Restart Scenarios

```java
@Test
public void testMultipleRestartScenarios() throws Exception {
    QuorumBase qb = new QuorumBase();
    qb.setUp();

    ZooKeeper zk = qb.createClient();
    zk.create("/test", "data".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

    // Test 1: Restart leader gracefully
    RestartFramework.at("scenario1")
        .on(qb)
        .restart("leader")
        .withMode(RestartMode.GRACEFUL)
        .execute();

    // Test 2: Crash restart a follower
    RestartFramework.at("scenario2")
        .on(qb)
        .restart("follower")
        .withMode(RestartMode.CRASH)
        .execute();

    // Test 3: Restart specific server
    RestartFramework.at("scenario3")
        .on(qb)
        .restart("server")
        .withIndex(2)
        .withMode(RestartMode.DELAYED_CRASH)
        .execute();

    assertTrue(zk.exists("/test", false) != null);
    qb.tearDown();
}
```

### Disable State Capture/Health Checks

```java
RestartFramework.at("fast_restart")
    .on(cluster)
    .restart("server")
    .captureState(false)   // Skip state verification
    .healthChecks(false)   // Skip health checks
    .execute();
```

## Dependencies

- **restart-core** - Restart Testing Framework core library
- **zookeeper** (3.9.4) - Apache ZooKeeper server
- **zookeeper** (3.9.4, tests classifier) - ZooKeeper test classes

## License

Licensed under the Apache License 2.0, consistent with Apache ZooKeeper.

## Contributing

This adapter is part of the ZooKeeper restart testing infrastructure. For issues or improvements, please refer to the main ZooKeeper project.
