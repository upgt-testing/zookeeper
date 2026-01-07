# FP-GROUP-5: BindException - Restart Adapter Mismatch

## Summary
**Classification:** False Positive (FP)
**Root Cause:** Restart framework adapter mismatch - `ClientBaseAdapter` is incorrectly applied to a quorum-based test

## Failure Details

### Exception
```
java.net.BindException: Address already in use
    at sun.nio.ch.Net.bind0(Native Method)
    at sun.nio.ch.Net.bind(Net.java:461)
    at sun.nio.ch.Net.bind(Net.java:453)
    at sun.nio.ch.ServerSocketChannelImpl.bind(ServerSocketChannelImpl.java:222)
    at org.apache.zookeeper.server.NIOServerCnxnFactory.configure(NIOServerCnxnFactory.java:662)
    at org.apache.zookeeper.test.ClientBase.createNewServerInstance(ClientBase.java:437)
    at org.restarttest.adapter.zookeeper.ClientBaseAdapter.restartServer(ClientBaseAdapter.java:135)
```

### Affected Tests
- `org.apache.zookeeper.test.HierarchicalQuorumTest_RestartInjected#testHierarchicalQuorum`
- `org.apache.zookeeper.test.QuorumOracleMajTest_RestartInjected#testMajQuorums`

## Root Cause Analysis

### Test Structure Mismatch

The test `HierarchicalQuorumTest_RestartInjected` has an unusual structure:

1. **Class Hierarchy:** It `extends ClientBase`
2. **Actual Infrastructure:** It creates 5 `QuorumPeer` instances (s1, s2, s3, s4, s5) instead of using `ClientBase`'s standalone server infrastructure

```java
// In HierarchicalQuorumTest_RestartInjected.setUp():
// Does NOT call super.setUp() which would start a standalone server
// Instead, creates QuorumPeers directly:
s1 = new QuorumPeer(peers, s1dir, s1dir, clientport1, ...);
s2 = new QuorumPeer(peers, s2dir, s2dir, clientport2, ...);
// ... etc
```

### Adapter Selection Issue

When the restart framework encounters `RestartFramework.at("before_hammer_test").on(this).restart("server")`:

1. Framework inspects `this` and sees it's a `ClientBase` instance
2. Framework selects `ClientBaseAdapter` to handle the restart
3. `ClientBaseAdapter` reads `cluster.serverFactory` which is `null` (no standalone server was started)
4. Adapter calls `shutdownServerInstance(null, hostPort)` which does nothing
5. Adapter calls `createNewServerInstance(null, hostPort, maxCnxns)` which tries to bind to the first port
6. But QuorumPeers are still running on those ports - hence `BindException`

### Code Flow in ClientBaseAdapter

```java
// In ClientBaseAdapter.restartServer():
ServerCnxnFactory factory = cluster.serverFactory;  // NULL - no standalone server!
boolean isSecure = factory != null && factory.isSecure();  // false

// GRACEFUL mode:
ClientBase.shutdownServerInstance(factory, hostPort);  // Does nothing when factory is null!
ClientBase.waitForServerDown(hostPort, ...);  // May succeed because checking wrong port

// Then tries to create new server:
factory = ClientBase.createNewServerInstance(null, hostPort, maxCnxns);
// FAILS! Port is still in use by QuorumPeer
```

### Why ClientBase.shutdownServerInstance Does Nothing

```java
// In ClientBase.shutdownServerInstance():
public static void shutdownServerInstance(ServerCnxnFactory factory, String hostPort) {
    if (factory != null) {  // factory is null, so entire method is skipped
        // ... shutdown logic
    }
}
```

## Why This Is a False Positive

1. **Not a ZooKeeper Source Code Bug:** The `BindException` is not caused by any bug in ZooKeeper's production code. `NIOServerCnxnFactory.configure()` correctly throws `BindException` when the port is in use.

2. **Not a Test Code Bug:** The test itself is correctly structured for its original purpose (testing hierarchical quorum). It simply wasn't designed for restart injection via `ClientBaseAdapter`.

3. **Restart Framework Limitation:** The restart framework's automatic adapter selection based on class hierarchy (`instanceof ClientBase`) is insufficient for this test case. The test extends `ClientBase` but uses a completely different server infrastructure (QuorumPeers).

4. **Improper Restart Target:** The restart injection `restart("server")` assumes there's a standalone "server" to restart, but this test only has QuorumPeers. The concept of "server" doesn't map correctly.

## Recommendation

Tests that extend `ClientBase` but use `QuorumPeer` directly should either:
1. Use a different base class that properly reflects their infrastructure
2. Not be candidates for restart injection via `ClientBaseAdapter`
3. Be transformed to use an appropriate adapter (e.g., a custom adapter that can handle QuorumPeer arrays)

The restart framework should detect when `serverFactory` is `null` and either:
- Skip the restart gracefully
- Throw an informative error message indicating adapter mismatch
- Attempt to detect actual server infrastructure and use appropriate adapter
