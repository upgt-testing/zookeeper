# FP-GROUP-4: NullPointerException in FileTxnSnapLog.save

## Summary

**Verdict**: FALSE POSITIVE - Stale Reference Issue Due to Improper Restart Position

**Test Class**: `org.apache.zookeeper.server.SnapshotDigestTest_RestartInjected`
**Test Method**: `testBackwardCompatible`
**Restart Position**: `after_create_compatible_node`
**Restart Target**: `server`
**Restart Mode**: `GRACEFUL`

## Stack Trace

```
java.lang.NullPointerException
    at org.apache.zookeeper.server.persistence.FileTxnSnapLog.save(FileTxnSnapLog.java:482)
    at org.apache.zookeeper.server.ZooKeeperServer.takeSnapshot(ZooKeeperServer.java:575)
    at org.apache.zookeeper.server.ZooKeeperServer.takeSnapshot(ZooKeeperServer.java:559)
    at org.apache.zookeeper.server.ZooKeeperServer.takeSnapshot(ZooKeeperServer.java:555)
    at org.apache.zookeeper.server.SnapshotDigestTest_RestartInjected.testCompatibleHelper(SnapshotDigestTest_RestartInjected.java:223)
    at org.apache.zookeeper.server.SnapshotDigestTest_RestartInjected.testBackwardCompatible(SnapshotDigestTest_RestartInjected.java:198)
```

## Root Cause Analysis

### The NPE Location

In `FileTxnSnapLog.java` at line 482:
```java
public File save(DataTree dataTree, ConcurrentHashMap<Long, Integer> sessionsWithTimeouts, boolean syncSnap) throws IOException {
    long lastZxid = dataTree.lastProcessedZxid;
    File snapshotFile = new File(snapDir, Util.makeSnapshotName(lastZxid));
    LOG.info("Snapshotting: 0x{} to {}", Long.toHexString(lastZxid), snapshotFile);
    try {
        snapLog.serialize(dataTree, sessionsWithTimeouts, snapshotFile, syncSnap);  // LINE 482: snapLog is NULL
        return snapshotFile;
    }
    // ...
}
```

The `snapLog` field is `null` at line 482.

### Why snapLog is NULL

In `FileTxnSnapLog.close()` at lines 629-633:
```java
public void close() throws IOException {
    // ...
    SnapShot snapSlogToClose = snapLog;
    if (snapSlogToClose != null) {
        snapSlogToClose.close();
    }
    snapLog = null;  // <-- Set to NULL on close
}
```

When a ZooKeeperServer is shut down, its `FileTxnSnapLog.close()` is called, which sets `snapLog = null`.

### The Stale Reference Problem

**Original Test Code Flow** (`SnapshotDigestTest.java`):
```java
private void testCompatibleHelper(...) throws Exception {
    ZooKeeperServer.setDigestEnabled(enabledBefore);
    ZooKeeperServer.setSerializeLastProcessedZxidEnabled(enabledBefore);

    reloadSnapshotAndCheckDigest();  // REFRESHES: server = serverFactory.getZooKeeperServer()

    zk.create(path, ...);

    server.takeSnapshot();  // WORKS: server is fresh
    // ...
}

private void reloadSnapshotAndCheckDigest() throws Exception {
    stopServer();
    // ...
    startServer();
    // ...
    server = serverFactory.getZooKeeperServer();  // <-- REFRESH SERVER REFERENCE
    // ...
}
```

**Injected Test Code Flow** (`SnapshotDigestTest_RestartInjected.java`):
```java
private void testCompatibleHelper(...) throws Exception {
    ZooKeeperServer.setDigestEnabled(enabledBefore);
    ZooKeeperServer.setSerializeLastProcessedZxidEnabled(enabledBefore);

    reloadSnapshotAndCheckDigest();  // REFRESHES: server = serverFactory.getZooKeeperServer()

    zk.create(path, ...);

    RestartFramework.at("after_create_compatible_node")  // <-- RESTART INJECTED HERE
        .on(this)
        .restart("server")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();
    // After restart:
    //   - A NEW ZooKeeperServer is created with a NEW FileTxnSnapLog
    //   - But 'server' still points to the OLD ZooKeeperServer
    //   - The OLD ZooKeeperServer's FileTxnSnapLog.close() was called, setting snapLog = null

    server.takeSnapshot();  // FAILS: server is STALE, its snapLog is NULL
    // ...
}
```

### Timeline Diagram

```
Time →

Original Test:
[reloadSnapshotAndCheckDigest] → [server=fresh] → [create] → [takeSnapshot on fresh server] ✓

Injected Test:
[reloadSnapshotAndCheckDigest] → [server=fresh] → [create] → [RESTART] → [takeSnapshot on STALE server] ✗
                                                               ↓
                                                    OLD server closed (snapLog=null)
                                                    NEW server created
                                                    'server' variable NOT updated
```

## Why This Is a False Positive

1. **Not a ZooKeeper Source Code Bug**: The `FileTxnSnapLog.save()` method correctly assumes it will only be called on an active (non-closed) instance. The NPE happens because the method is called on a closed instance via a stale reference.

2. **Not an Original Test Bug**: The original `SnapshotDigestTest.java` works correctly. It properly refreshes the `server` reference in `reloadSnapshotAndCheckDigest()` before any restart-like operations.

3. **Improper Restart Position**: The restart injection at `after_create_compatible_node` is placed between:
   - The last `server` reference refresh (`reloadSnapshotAndCheckDigest()`)
   - The use of that reference (`server.takeSnapshot()`)

   This invalidates the `server` reference that the test code assumes is still valid.

4. **Restart Framework Limitation**: The restart framework cannot automatically detect and update stale object references held by test code. When a restart is injected, any object references obtained before the restart become stale.

## Recommendation

This restart position is **not suitable** for this test because:
- The test holds a reference (`server`) to an internal object that becomes invalid after restart
- The test code does not have logic to refresh this reference after an arbitrary restart

To make this test work with restart injection at this position, the test would need to be modified to refresh the `server` reference after every potential restart point:
```java
RestartFramework.at("after_create_compatible_node")
    .on(this)
    .restart("server")
    .withIndex(0)
    .withMode(RestartMode.GRACEFUL)
    .execute();

// Refresh stale reference
server = serverFactory.getZooKeeperServer();

server.takeSnapshot();
```

However, this would require modifying the injected test, which defeats the purpose of automatic restart injection testing.

## Why the Framework Didn't Refresh the Reference

The `restart-tracking-agent` uses bytecode instrumentation to track object references and refresh them after restart. The tracking works by:

1. **Marking cluster roots**: Objects passed to `RestartFramework.on(cluster)` are marked as tracked roots
2. **Method call propagation**: When methods are called on tracked objects, results become tracked
3. **Field assignment registration**: When tracked values are stored to fields, they're registered for refresh

### The Critical Gap: Missing GETFIELD Instrumentation

Looking at `TrackingClassTransformer.java`, the agent instruments:
- `PUTFIELD` / `PUTSTATIC` (field stores) - lines 593-663
- `INVOKEVIRTUAL` / `INVOKEINTERFACE` (method calls) - lines 219-271
- `ALOAD` / `ASTORE` (local variable operations) - lines 532-590

**But it does NOT instrument `GETFIELD` (field reads)!**

This is why `server` wasn't tracked in the ZooKeeper test:

```java
// In SnapshotDigestTest_RestartInjected.setUp()
server = serverFactory.getZooKeeperServer();
//        ^^^^^^^^^^^^^ GETFIELD - NOT instrumented!
```

**Execution trace**:
1. `serverFactory` is loaded from field via `GETFIELD` → **NOT marked as tracked** (no instrumentation)
2. `getZooKeeperServer()` is called on `serverFactory`
3. The tracking agent checks `ObjectTracker.isTracked(serverFactory)` → returns `false`
4. Since receiver isn't tracked, the result (`server`) isn't tracked either
5. `server` is never registered for refresh after restart

### Contrast with HBase (where tracking works)

```java
// In HBase tests - works because of method chain from cluster root
master = cluster.getMiniHBaseCluster().getMaster();
//       ^^^^^^^ 'cluster' is the tracked root (marked via RestartFramework.on(cluster))
//               ^^^^^^^^^^^^^^^^ INVOKEVIRTUAL on tracked object → result is tracked
//                                ^^^^^^^^^^^ INVOKEVIRTUAL on tracked object → result is tracked
```

In HBase tests:
- `cluster` is the tracked root
- `getMiniHBaseCluster()` is called ON the tracked cluster → result is tracked
- `getMaster()` is called ON the tracked result → result is tracked
- The entire chain flows from method calls, no field access in between

### Why ZooKeeper is Different

In ZooKeeper `ClientBase` tests:
- `this` (the test instance) is passed to `RestartFramework.on(this)` → `this` is tracked
- But `serverFactory` is a **field** of `this`, accessed via `GETFIELD`
- Since `GETFIELD` isn't instrumented, `serverFactory` doesn't inherit tracking from `this`
- The method chain is broken at the field access

### Recommended Fix for Framework

The tracking agent needs to instrument `GETFIELD` to propagate tracking from objects to their fields:

```java
// In TrackingClassTransformer.visitFieldInsn():
if (opcode == Opcodes.GETFIELD) {
    // After GETFIELD, check if the owner object was tracked
    // If so, mark the loaded field value as tracked too
    // This would allow: this.serverFactory -> tracked if 'this' is tracked
}
```

Alternatively, `ClientBase.serverFactory` could be explicitly marked as a cluster-derived field in the ZooKeeper adapter.

## Conclusion

**This is a FALSE POSITIVE** caused by:
1. The inherent limitation of injecting restarts at arbitrary positions in test code that holds references to server objects
2. The `restart-tracking-agent` didn't properly track ZooKeeper's field access patterns

The failure does not indicate any bug in ZooKeeper's production code or the original test logic.

---

## Fix Applied

The `restart-tracking-agent` was enhanced to handle ZooKeeper's test patterns:

### Changes Made

1. **ZooKeeperFieldConfig.java** (NEW): Defines which fields are cluster-derived (`serverFactory`, `s1-s5`)

2. **ClusterRootDetector.java**: Enhanced `markFromRestartFramework()` to:
   - Proactively scan for ZooKeeper cluster fields when cluster root is marked
   - Track `serverFactory` and its derived `server` via `serverFactory.getZooKeeperServer()`
   - Register fields for refresh with proper expression chains

3. **ExpressionChain.java**: Added `FieldAccess` class and `extendWithFieldAccess()` method to support field-based expression chains

4. **ObjectTracker.java**: Added `markAsTracked()` and `setChain()` helper methods

5. **TrackingClassTransformer.java**: Added targeted GETFIELD instrumentation for known cluster fields

### How It Works Now

When `RestartFramework.on(this)` is called:
1. `this` (test instance) is marked as cluster root
2. `scanAndTrackZooKeeperFields()` scans for `serverFactory` field
3. `serverFactory` is tracked with chain: `cluster.serverFactory`
4. `trackServerFromFactory()` calls `serverFactory.getZooKeeperServer()` and tracks result
5. `server` is registered with chain: `cluster.serverFactory.getZooKeeperServer()`
6. After restart, `ReferenceRegistry.refreshAllReferences()` replays the chain to update `server`

### Test Verification

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The test now passes because the `server` field is properly refreshed after restart.

## Files Analyzed

- `/home/shuai/xlab/restart_testing/zookeeper/zookeeper-server/src/main/java/org/apache/zookeeper/server/persistence/FileTxnSnapLog.java` (lines 61, 170, 482, 629-633)
- `/home/shuai/xlab/restart_testing/zookeeper/zookeeper-server/src/test/java/org/apache/zookeeper/server/SnapshotDigestTest.java` (lines 159-205)
- `/home/shuai/xlab/restart_testing/zookeeper/zookeeper-server/src/test/java/org/apache/zookeeper/server/SnapshotDigestTest_RestartInjected.java` (lines 196-231)
- `/home/shuai/xlab/restart_testing/zookeeper/zookeeper-restart-adapter/src/main/java/org/restarttest/adapter/zookeeper/ClientBaseAdapter.java` (lines 119-204)
- `/home/shuai/xlab/restart_testing/zookeeper/zookeeper-server/src/test/java/org/apache/zookeeper/test/ClientBase.java` (line 73: serverFactory field)
- `/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/src/main/java/org/restarttest/tracking/TrackingClassTransformer.java` (visitFieldInsn only handles PUTFIELD/PUTSTATIC, not GETFIELD)
- `/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/src/main/java/org/restarttest/tracking/ObjectTracker.java` (tracking propagation logic)
- `/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/src/main/java/org/restarttest/tracking/ReferenceRegistry.java` (reference refresh logic)
