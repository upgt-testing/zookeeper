# BUG-GROUP-6: FileTxnSnapLog throws uninformative NullPointerException when accessed via stale reference after server restart

## JIRA Summary
**Title:** FileTxnSnapLog should throw informative exception instead of NPE when accessed via stale reference

**Component:** server

**Affects Version:** 3.9.4 (and likely all versions)

**Priority:** Minor

## Description

In scenarios involving server restarts or failovers, application code may inadvertently hold a stale reference to a `ZooKeeperServer` instance whose `FileTxnSnapLog` has been closed. When methods like `save()` or `restore()` are called through such stale references, the current implementation throws a raw `NullPointerException` with no message, making it extremely difficult to diagnose the root cause.

While calling methods on a closed resource is indeed a programming error, such errors can occur in complex distributed systems during restart/failover scenarios. The current behavior forces developers to trace through the code to understand that the NPE is caused by the resource being closed, rather than immediately indicating the problem.

### Real-World Scenario

This issue was discovered during server restart testing. The scenario:

1. Application code obtains a reference to `ZooKeeperServer` (e.g., `server = factory.getZooKeeperServer()`)
2. Server undergoes a restart (graceful shutdown + new instance creation)
3. The old server's `FileTxnSnapLog.close()` is called during shutdown, setting internal fields to `null`
4. Application code still holds the stale reference to the old server
5. When `server.takeSnapshot()` is called, it invokes `FileTxnSnapLog.save()` on the closed instance
6. **Result:** Uninformative `NullPointerException` with no indication of the actual problem

```
java.lang.NullPointerException
    at org.apache.zookeeper.server.persistence.FileTxnSnapLog.save(FileTxnSnapLog.java:482)
    at org.apache.zookeeper.server.ZooKeeperServer.takeSnapshot(ZooKeeperServer.java:575)
    ...
```

This stack trace gives no hint that the issue is a stale reference to a closed resource. A developer seeing this must manually trace through the code to discover that `snapLog` is `null` because `close()` was called.

### Expected Behavior

When a closed `FileTxnSnapLog` is accessed (regardless of the reason), it should throw an `IllegalStateException` with a clear message:

```
java.lang.IllegalStateException: FileTxnSnapLog has been closed
    at org.apache.zookeeper.server.persistence.FileTxnSnapLog.save(FileTxnSnapLog.java:XXX)
    ...
```

This immediately tells the developer:
- The resource was closed
- They likely have a stale reference
- They need to obtain a fresh reference to the current server instance

### Current Code Behavior

When `close()` is called, it sets internal fields to `null` without any tracking:

```java
// FileTxnSnapLog.java:623-634
public void close() throws IOException {
    TxnLog txnLogToClose = txnLog;
    if (txnLogToClose != null) {
        txnLogToClose.close();
    }
    txnLog = null;  // Set to null, no closed flag
    SnapShot snapSlogToClose = snapLog;
    if (snapSlogToClose != null) {
        snapSlogToClose.close();
    }
    snapLog = null;  // Set to null, no closed flag
}
```

Subsequently, methods like `save()` access these fields without null checks:

```java
// FileTxnSnapLog.java:482
snapLog.serialize(dataTree, sessionsWithTimeouts, snapshotFile, syncSnap);
// NPE thrown here if snapLog is null
```

## Affected Methods

The following methods will throw uninformative NPE if accessed via a stale reference:

| Method | Line | Field Used |
|--------|------|------------|
| `save()` | 482 | `snapLog.serialize()` |
| `restore()` | 254 | `snapLog.deserialize()` |
| `getLastSnapshotInfo()` | 229 | `snapLog.getLastSnapshotInfo()` |
| `commit()` | 600 | `txnLog.commit()` |
| `rollLog()` | 616 | `txnLog.rollLog()` |
| `getTxnLogElapsedSyncTime()` | 608 | `txnLog.getTxnLogSyncElapsedTime()` |
| `setTotalLogSize()` | 667 | `txnLog.setTotalLogSize()` |

## Proposed Fix

Add a `closed` flag and a helper method to provide informative error messages:

```java
public class FileTxnSnapLog {
    // ... existing fields ...
    private volatile boolean closed = false;

    /**
     * Throws IllegalStateException if this FileTxnSnapLog has been closed.
     * This helps diagnose issues with stale references after server restarts.
     */
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException(
                "FileTxnSnapLog has been closed. " +
                "This may indicate a stale reference to a stopped server instance.");
        }
    }

    public void close() throws IOException {
        if (closed) {
            return;  // Already closed, make idempotent
        }
        closed = true;

        TxnLog txnLogToClose = txnLog;
        if (txnLogToClose != null) {
            txnLogToClose.close();
        }
        txnLog = null;
        SnapShot snapSlogToClose = snapLog;
        if (snapSlogToClose != null) {
            snapSlogToClose.close();
        }
        snapLog = null;
    }

    public File save(
        DataTree dataTree,
        ConcurrentHashMap<Long, Integer> sessionsWithTimeouts,
        boolean syncSnap) throws IOException {
        checkNotClosed();  // Add this check
        long lastZxid = dataTree.lastProcessedZxid;
        // ... rest of method unchanged
    }

    // Similar checkNotClosed() calls added to other public methods
}
```

## Alternative Fix (Minimal Change)

If adding a `closed` flag is considered too invasive, a simpler fix is to add null checks with meaningful exceptions:

```java
public File save(
    DataTree dataTree,
    ConcurrentHashMap<Long, Integer> sessionsWithTimeouts,
    boolean syncSnap) throws IOException {
    if (snapLog == null) {
        throw new IllegalStateException(
            "FileTxnSnapLog has been closed. " +
            "This may indicate a stale reference to a stopped server instance.");
    }
    long lastZxid = dataTree.lastProcessedZxid;
    // ... rest of method unchanged
}
```

## Rationale

1. **Defensive Programming:** Even though calling methods after `close()` is a programming error, defensive checks with meaningful messages significantly reduce debugging time. This is a well-established pattern in Java (e.g., `InputStream` throws `IOException("Stream closed")`, JDBC connections throw `SQLException("Connection is closed")`).

2. **Restart/Failover Scenarios:** In distributed systems, stale references can occur during restart and failover scenarios. These are exactly the scenarios where quick diagnosis is most critical.

3. **Minimal Runtime Cost:** A single boolean check adds negligible overhead compared to the I/O operations these methods perform.

4. **No Behavior Change for Correct Code:** Code that correctly uses `FileTxnSnapLog` sees no change in behavior. Only erroneous code gets a better error message.

## Test Case

```java
@Test
public void testSaveAfterCloseThrowsMeaningfulException() throws Exception {
    File tmpDir = ClientBase.createTmpDir();
    FileTxnSnapLog txnSnapLog = new FileTxnSnapLog(tmpDir, tmpDir);
    txnSnapLog.close();

    DataTree dataTree = new DataTree();
    ConcurrentHashMap<Long, Integer> sessions = new ConcurrentHashMap<>();

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> txnSnapLog.save(dataTree, sessions, false)
    );
    assertTrue(exception.getMessage().contains("closed"));
}
```

## Impact

- **User Impact:** Developers debugging stale reference issues will immediately understand the problem instead of tracing through code to find why a field is `null`.
- **Risk:** Very low - this is purely defensive programming that doesn't change normal operation.
- **Backward Compatibility:** Fully backward compatible. The exception type changes from `NullPointerException` to `IllegalStateException`, but both are unchecked exceptions and the scenario only occurs in erroneous code paths.

## References

- Java's `InputStream.read()` throws `IOException` with message "Stream closed" after `close()`
- JDBC `Connection` throws `SQLException` with message "Connection is closed" after `close()`
- Similar defensive patterns exist throughout ZooKeeper (e.g., checking connection state)
